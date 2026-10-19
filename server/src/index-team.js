import hardenedWorker from "./index-hardening.js";
import { MATCH_LOCATION_HEADER, matchLocationHint } from "./match-location.js";
import {
  httpError,
  sha256Hex,
} from "./crypto.js";

const RATE_LIMIT = 90;
const RATE_WINDOW_MS = 60_000;
const MAX_BODY_BYTES = 8 * 1024;

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);

      if (request.method === "GET" && url.pathname === "/ready") {
        const base = await hardenedWorker.fetch(request, env);
        let body;
        try {
          body = await base.clone().json();
        } catch {
          return base;
        }
        const teamMatchmakerConfigured = Boolean(env?.LUDOPROOF_TEAM_MATCHMAKER);
        const ready = base.ok && body?.ready === true && teamMatchmakerConfigured;
        return json(ready ? 200 : 503, {
          ...body,
          ok: ready,
          ready,
          checks: {
            ...(body?.checks ?? {}),
            teamMatchmakerConfigured,
          },
        });
      }

      const match = url.pathname.match(
        /^\/api\/team-matchmaking\/(search|status|cancel)$/,
      );
      if (!match) {
        return hardenedWorker.fetch(request, env);
      }

      if (request.method !== "POST") {
        const error = httpError(405, "METHOD_NOT_ALLOWED", "method not allowed");
        error.allow = "POST";
        throw error;
      }

      const body = await readJson(request);
      await enforceRateLimit(env, request, match[1]);
      const target = teamMatchmaker(env);
      const headers = new Headers({
        "content-type": "application/json",
      });
      const authorization = request.headers.get("authorization");
      if (authorization) headers.set("authorization", authorization);
      const locationHint = matchLocationHint(env, request);
      if (locationHint) headers.set(MATCH_LOCATION_HEADER, locationHint);

      const response = await target.fetch(
        new Request("https://team-matchmaker/" + match[1], {
          method: "POST",
          headers,
          body: JSON.stringify(body),
        }),
      );
      return withSecurityHeaders(response);
    } catch (error) {
      return errorResponse(error);
    }
  },
};

function teamMatchmaker(env) {
  if (!env?.LUDOPROOF_TEAM_MATCHMAKER) {
    throw httpError(
      503,
      "TEAM_MATCHMAKER_NOT_CONFIGURED",
      "team matchmaking is not configured",
    );
  }
  const id = env.LUDOPROOF_TEAM_MATCHMAKER.idFromName("TEAM_UP:4:V1");
  return env.LUDOPROOF_TEAM_MATCHMAKER.get(id);
}

async function enforceRateLimit(env, request, action) {
  if (!env?.LUDOPROOF_API_GATE) {
    throw httpError(
      503,
      "RATE_LIMITER_NOT_CONFIGURED",
      "API rate limiter is not configured",
    );
  }
  const ip = request.headers.get("cf-connecting-ip") ?? "unknown";
  const key = await sha256Hex(
    "ludoproof:api-rate:v1:team-matchmaking:" + action + ":" + ip,
  );
  const id = env.LUDOPROOF_API_GATE.idFromName(key);
  const target = env.LUDOPROOF_API_GATE.get(id);
  let response;
  try {
    response = await target.fetch(
      new Request("https://gate/check", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          limit: RATE_LIMIT,
          periodMs: RATE_WINDOW_MS,
        }),
      }),
    );
  } catch {
    throw httpError(
      503,
      "RATE_LIMITER_UNAVAILABLE",
      "API rate limiter is unavailable",
    );
  }
  let value;
  try {
    value = await response.json();
  } catch {
    throw httpError(
      503,
      "RATE_LIMITER_BAD_RESPONSE",
      "API rate limiter returned invalid data",
    );
  }
  if (response.status === 429 || value?.allowed === false) {
    const error = httpError(429, "RATE_LIMITED", "too many requests");
    error.retryAfter = response.headers.get("retry-after") ?? "60";
    throw error;
  }
  if (!response.ok || value?.allowed !== true) {
    throw httpError(
      503,
      "RATE_LIMITER_UNAVAILABLE",
      "API rate limiter rejected the check",
    );
  }
}

async function readJson(request) {
  const type = request.headers.get("content-type") ?? "";
  if (!type.toLowerCase().startsWith("application/json")) {
    throw httpError(
      415,
      "UNSUPPORTED_MEDIA_TYPE",
      "content-type must be application/json",
    );
  }
  const text = await request.text();
  if (new TextEncoder().encode(text).byteLength > MAX_BODY_BYTES) {
    throw httpError(413, "REQUEST_TOO_LARGE", "request body is too large");
  }
  try {
    const value = text.length === 0 ? {} : JSON.parse(text);
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      throw new Error("object required");
    }
    return value;
  } catch {
    throw httpError(400, "INVALID_JSON", "request body must be a JSON object");
  }
}

function withSecurityHeaders(response) {
  const headers = new Headers(response.headers);
  for (const [name, value] of securityHeaders()) headers.set(name, value);
  headers.set("cache-control", "no-store");
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

function securityHeaders() {
  return new Headers({
    "content-security-policy": "default-src 'none'; frame-ancestors 'none'; base-uri 'none'",
    "permissions-policy": "camera=(), microphone=(), geolocation=()",
    "referrer-policy": "no-referrer",
    "strict-transport-security": "max-age=31536000; includeSubDomains",
    "x-content-type-options": "nosniff",
    "x-frame-options": "DENY",
    "x-robots-tag": "noindex, nofollow",
  });
}

function errorResponse(error) {
  const status =
    Number.isInteger(error?.status) && error.status >= 400 && error.status <= 599
      ? error.status
      : 500;
  const extra = new Headers();
  if (error?.allow) extra.set("allow", error.allow);
  if (error?.retryAfter) extra.set("retry-after", String(error.retryAfter));
  return json(
    status,
    {
      error: error?.code ?? "INTERNAL_ERROR",
      message:
        status === 500 && !error?.code
          ? "internal server error"
          : String(error?.message ?? "internal server error"),
    },
    extra,
  );
}

function json(status, body, extraHeaders = null) {
  const headers = securityHeaders();
  headers.set("content-type", "application/json; charset=utf-8");
  headers.set("cache-control", "no-store");
  if (extraHeaders) {
    for (const [name, value] of extraHeaders) headers.set(name, value);
  }
  return new Response(JSON.stringify(body), { status, headers });
}
