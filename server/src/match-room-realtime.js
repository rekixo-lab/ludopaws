import { familyResponse } from "./family-safety.js";
import { MatchRoom as BotMatchRoom } from "./match-room-bots.js";
import { bearerToken, httpError, sha256Hex } from "./crypto.js";

const STATE_KEY = "match-state";
const TOKEN_HASH_DOMAIN = "ludoproof:player-token:v1:";
const WINDOW_MS = 60_000;
const MAX_COMMAND_BYTES = 8 * 1024;
const COMMAND_ID = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const GAMEPLAY_PATHS = new Set([
  "/state", "/events", "/start", "/roll", "/roll/commit", "/roll/reveal", "/move",
]);
const LIMITS = Object.freeze({ state: 120, mutation: 90, unauthenticated: 30 });

/**
 * Socket commands enter the same authenticated HTTP handlers and mutation queue
 * as fallback requests. A lost acknowledgement can therefore be retried over
 * either transport without applying another roll or move.
 */
export class MatchRoom extends BotMatchRoom {
  constructor(ctx, env) {
    super(ctx, env);
    // Fixed-size room-local buckets: four seats, two policies, one invalid-auth
    // bucket. Keeping these in memory avoids a rate-gate DO hop and a storage
    // write on every gameplay command. Buckets reset when the room is evicted.
    this.gameplayRateBuckets = new Map();
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (GAMEPLAY_PATHS.has(url.pathname)) {
      try {
        await this.#checkGameplayRate(request, url.pathname);
      } catch (error) {
        return errorResponse(error);
      }
    }
    return familyResponse(await super.fetch(request), this.env);
  }

  async #checkGameplayRate(request, path) {
    let player;
    try {
      const token = bearerToken(request);
      if (token.length > 256) {
        throw httpError(403, "AUTH_INVALID", "player token is invalid for this match");
      }
      const state = await this.ctx.storage.get(STATE_KEY);
      if (!state) throw httpError(404, "MATCH_NOT_FOUND", "match does not exist");
      const digest = await sha256Hex(TOKEN_HASH_DOMAIN + token);
      player = state.players?.find((candidate) => candidate.tokenAuthHash === digest);
      if (!player) throw httpError(403, "AUTH_INVALID", "player token is invalid for this match");
    } catch (error) {
      this.#consumeRate("unauthenticated", LIMITS.unauthenticated);
      throw error;
    }
    const policy = path === "/state" || path === "/events" ? "state" : "mutation";
    this.#consumeRate(player.playerId + ":" + policy, LIMITS[policy]);
  }

  #consumeRate(key, limit) {
    const now = Date.now();
    const windowId = Math.floor(now / WINDOW_MS);
    let bucket = this.gameplayRateBuckets.get(key);
    if (bucket?.windowId !== windowId) {
      bucket = { windowId, count: 0 };
      this.gameplayRateBuckets.set(key, bucket);
    }
    if (bucket.count >= limit) {
      const error = httpError(429, "RATE_LIMITED", "too many requests");
      error.retryAfter = Math.max(1, Math.ceil(((windowId + 1) * WINDOW_MS - now) / 1000));
      throw error;
    }
    bucket.count += 1;
  }

  async webSocketMessage(socket, message) {
    if (typeof message !== "string") return super.webSocketMessage(socket, message);
    // Leave heartbeat, quick chat, and voice signaling on their existing paths.
    let payload;
    try {
      if (message.length > MAX_COMMAND_BYTES) {
        if (message.length > 16_000) return;
        payload = JSON.parse(message);
        if (payload?.type !== "command") return super.webSocketMessage(socket, message);
        return this.#sendResult(socket, commandRequestId(payload), 413, {
          ok: false, error: "REQUEST_TOO_LARGE", message: "command body is too large",
        });
      }
      payload = JSON.parse(message);
    } catch {
      return super.webSocketMessage(socket, message);
    }
    if (payload?.type !== "command") return super.webSocketMessage(socket, message);

    const requestId = commandRequestId(payload);
    try {
      if (!requestId) throw httpError(400, "INVALID_REQUEST_ID", "command requestId is required");
      if (new TextEncoder().encode(message).byteLength > MAX_COMMAND_BYTES) {
        throw httpError(413, "REQUEST_TOO_LARGE", "command body is too large");
      }
      if (!payload.body || typeof payload.body !== "object" || Array.isArray(payload.body)) {
        throw httpError(400, "INVALID_COMMAND_BODY", "command body must be an object");
      }
      const path = payload.action === "ROLL" ? "/roll" : payload.action === "MOVE" ? "/move" : null;
      if (!path) throw httpError(400, "INVALID_COMMAND", "command action must be ROLL or MOVE");

      // Tags are assigned only by the authenticated /events handshake. Never
      // trust a playerId or bearer token supplied inside the command payload.
      const tags = typeof this.ctx.getTags === "function" ? this.ctx.getTags(socket) : [];
      const playerTag = Array.isArray(tags)
        ? tags.find((tag) => typeof tag === "string" && tag.startsWith("player:"))
        : null;
      const playerId = playerTag?.slice("player:".length);
      if (!playerId) throw httpError(401, "AUTH_REQUIRED", "authenticated match socket is required");

      const response = await this.realtimeRequestForPlayer(playerId, path, payload.body);
      const body = await response.json();
      this.#sendResult(socket, requestId, response.status, response.ok ? body : { ...body, ok: false });
    } catch (error) {
      const response = errorResponse(error);
      this.#sendResult(socket, requestId, response.status, await response.json());
    }
  }

  #sendResult(socket, requestId, statusCode, response) {
    try {
      socket.send(JSON.stringify({ type: "command_result", requestId, statusCode, response }));
    } catch {
      // The client may retry an acknowledged-or-applied command over HTTP.
    }
  }
}

function commandRequestId(payload) {
  return typeof payload?.requestId === "string" && COMMAND_ID.test(payload.requestId)
    ? payload.requestId : null;
}

function errorResponse(error) {
  const status = Number.isInteger(error?.status) && error.status >= 400 && error.status <= 599
    ? error.status : 500;
  const headers = new Headers({
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
  });
  if (error?.retryAfter) headers.set("retry-after", String(error.retryAfter));
  return new Response(JSON.stringify({
    ok: false,
    error: error?.code ?? "INTERNAL_ERROR",
    message: status >= 500 && !error?.code ? "internal server error" : String(error?.message ?? "internal server error"),
  }), { status, headers });
}
