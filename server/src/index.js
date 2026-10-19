import {
  deterministicMatchId,
  hasSessionKey,
  httpError,
  requireClientRequestId,
  sha256Hex,
} from "./crypto.js";
import {
  googlePlayBillingEnabled,
  verifyAndRedeemGooglePlayPurchase,
} from "./google-play-billing.js";
import { MATCH_LOCATION_HEADER, matchLocationHint } from "./match-location.js";

export { MatchRoom } from "./match-room.js";
export { ApiGate } from "./api-gate.js";
export { LeaderboardRoom } from "./leaderboard-room.js";
export { MatchmakerQueue } from "./matchmaker-queue.js";
export { FriendDirectory } from "./friend-directory.js";

const RELEASE_PHASE = "online-realtime-v1";
const DICE_PROTOCOL = "ludopaws-server-dice-v1";
const MAX_BODY_BYTES = 8 * 1024;
const RATE_WINDOW_MS = 60 * 1000;
const RATE_POLICIES = Object.freeze({
  create: 12,
  join: 30,
  state: 120,
  mutation: 90,
  leaderboard: 60,
  matchmaking: 90,
  friends: 120,
  storePurchases: 10,
});

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);

      if (request.method === "GET" && url.pathname === "/") {
        return json(200, {
          ok: true,
          service: "ludoproof-game-api",
          releasePhase: RELEASE_PHASE,
          production: false,
          game: "LudoProof",
          ruleset: "ludoproof-standard-v4",
          diceProtocol: DICE_PROTOCOL,
        });
      }

      if (request.method === "GET" && url.pathname === "/health") {
        const checks =
          configurationChecks(env);
        return json(200, {
          ok: true,
          service: "ludoproof-game-api",
          releasePhase: RELEASE_PHASE,
          diceProtocol: DICE_PROTOCOL,
          checks,
        });
      }

      if (request.method === "GET" && url.pathname === "/ready") {
        const checks =
          configurationChecks(env);
        const configured =
          checks.matchStoreConfigured &&
          checks.rateGateConfigured &&
          checks.leaderboardConfigured &&
          checks.matchmakerConfigured &&
          checks.friendsConfigured;
        const ready =
          configured &&
          checks.sessionKeyConfigured;
        return json(ready ? 200 : 503, {
          ok: ready,
          ready,
          service: "ludoproof-game-api",
          releasePhase: RELEASE_PHASE,
          ruleset: "ludoproof-standard-v4",
          diceProtocol: DICE_PROTOCOL,
          checks,
          productionClaim: false,
        });
      }

      if (url.pathname === "/api/store/status") {
        requireMethod(request, "GET");
        return json(200, {
          ok: true,
          billingAvailable: googlePlayBillingEnabled(env),
        });
      }

      if (url.pathname === "/api/store/purchases/verify") {
        requireMethod(request, "POST");
        const body = await readJsonRequest(request);
        await enforceRateLimit(
          env,
          request,
          "store:purchases:verify",
          RATE_POLICIES.storePurchases,
        );
        const result = await verifyAndRedeemGooglePlayPurchase(env, {
          productId: body.productId,
          purchaseToken: body.purchaseToken,
          accountId: body.accountId,
        });
        return json(200, {
          ok: true,
          ...result,
        });
      }

      if (
        request.method === "POST" &&
        url.pathname ===
          "/api/leaderboard/profile/register"
      ) {
        await enforceRateLimit(
          env,
          request,
          "leaderboard:profile-register",
          RATE_POLICIES.leaderboard,
        );

        const target =
          leaderboard(env);
        const response =
          await target.fetch(
            new Request(
              "https://leaderboard/profile/register",
              {
                method:
                  "POST",
                headers: {
                  "content-type":
                    "application/json",
                },
                body:
                  JSON.stringify(
                    await readJsonRequest(
                      request,
                    ),
                  ),
              },
            ),
          );

        return withSecurityHeaders(
          response,
        );
      }

      if (
        request.method === "GET" &&
        url.pathname === "/api/leaderboard"
      ) {
        await enforceRateLimit(
          env,
          request,
          "leaderboard",
          RATE_POLICIES.leaderboard,
        );

        const target =
          leaderboard(env);
        const upstreamUrl =
          new URL(
            "https://leaderboard/list",
          );
        const limit =
          url.searchParams.get(
            "limit",
          );

        if (limit) {
          upstreamUrl.searchParams.set(
            "limit",
            limit,
          );
        }

        const headers =
          new Headers();
        const authorization =
          request.headers.get(
            "authorization",
          );
        if (
          authorization
        ) {
          headers.set(
            "authorization",
            authorization,
          );
        }

        const response =
          await target.fetch(
            new Request(
              upstreamUrl,
              {
                method: "GET",
                headers,
              },
            ),
          );
        return withSecurityHeaders(
          response,
        );
      }

      const friendRoute =
        url.pathname.match(
          /^\/api\/friends\/(register|snapshot|heartbeat|request|request\/respond|remove|invite|invite\/respond)$/,
        );
      if (friendRoute) {
        const action =
          friendRoute[1];
        const isSnapshot =
          action ===
          "snapshot";
        requireMethod(
          request,
          isSnapshot
            ? "GET"
            : "POST",
        );

        await enforceRateLimit(
          env,
          request,
          "friends:" +
            action,
          RATE_POLICIES.friends,
        );

        const headers =
          new Headers();
        const authorization =
          request.headers.get(
            "authorization",
          );
        if (authorization) {
          headers.set(
            "authorization",
            authorization,
          );
        }
        const roomToken =
          request.headers.get(
            "x-ludoproof-room-token",
          );
        if (
          roomToken &&
          action ===
            "invite"
        ) {
          headers.set(
            "x-ludoproof-room-token",
            roomToken,
          );
        }
        if (!isSnapshot) {
          headers.set(
            "content-type",
            "application/json",
          );
        }

        const target =
          friends(
            env,
          );
        const response =
          await target.fetch(
            new Request(
              "https://friends/" +
                action,
              {
                method:
                  isSnapshot
                    ? "GET"
                    : "POST",
                headers,
                body:
                  isSnapshot
                    ? undefined
                    : JSON.stringify(
                        await readJsonRequest(
                          request,
                        ),
                      ),
              },
            ),
          );

        return withSecurityHeaders(
          response,
        );
      }

      const matchmakingRoute =
        url.pathname.match(
          /^\/api\/matchmaking\/(search|status|cancel)$/,
        );
      if (matchmakingRoute) {
        requireMethod(
          request,
          "POST",
        );
        const body =
          await readJsonRequest(
            request,
          );
        const playerCount =
          requireMatchmakingPlayerCount(
            body.playerCount,
          );

        await enforceRateLimit(
          env,
          request,
          "matchmaking:" +
            matchmakingRoute[1] +
            ":" +
            playerCount,
          RATE_POLICIES.matchmaking,
        );

        const target =
          matchmaker(
            env,
            playerCount,
          );
        const response =
          await target.fetch(
            new Request(
              "https://matchmaker/" +
                matchmakingRoute[1],
              {
                method:
                  "POST",
                headers: (() => {
                  const headers =
                    new Headers({
                      "content-type":
                        "application/json",
                    });
                  const authorization =
                    request.headers.get(
                      "authorization",
                    );
                  if (
                    authorization
                  ) {
                    headers.set(
                      "authorization",
                      authorization,
                    );
                  }
                  const locationHint = matchLocationHint(env, request);
                  if (locationHint) headers.set(MATCH_LOCATION_HEADER, locationHint);
                  return headers;
                })(),
                body:
                  JSON.stringify(
                    body,
                  ),
              },
            ),
          );

        return withSecurityHeaders(
          response,
        );
      }

      if (url.pathname === "/api/matches") {
        requireMethod(request, "POST");
        const body = await readJsonRequest(request);
        await enforceRateLimit(
          env,
          request,
          "create",
          RATE_POLICIES.create,
        );

        const clientRequestId =
          requireClientRequestId(
            body.clientRequestId,
          );

        for (let attempt = 0; attempt < 4; attempt += 1) {
          const matchId =
            await deterministicMatchId(
              clientRequestId,
              attempt,
            );
          const target = room(env, matchId, request);
          const headers =
            new Headers({
              "content-type":
                "application/json",
            });
          const authorization =
            request.headers.get(
              "authorization",
            );
          if (authorization) {
            headers.set(
              "authorization",
              authorization,
            );
          }
          const response = await target.fetch(
            new Request("https://room/create", {
              method: "POST",
              headers,
              body: JSON.stringify({
                ...body,
                matchId,
              }),
            }),
          );
          if (response.status !== 409) {
            return withSecurityHeaders(response);
          }
        }

        throw httpError(
          503,
          "MATCH_ID_EXHAUSTED",
          "could not allocate a match ID",
        );
      }

      const matchRoute = url.pathname.match(
        /^\/api\/matches\/(LP[A-Z2-9]{8})(?:\/(.+))?$/,
      );
      if (!matchRoute) {
        return json(
          404,
          {
            error: "NOT_FOUND",
            message: "route not found",
          },
        );
      }

      const matchId = matchRoute[1];
      const action = matchRoute[2] ?? "state";
      const routeMap = {
        join: {
          path: "/join",
          method: "POST",
        },
        state: {
          path: "/state",
          method: "GET",
        },
        start: {
          path: "/start",
          method: "POST",
        },
        roll: {
          path: "/roll",
          method: "POST",
        },
        "roll/commit": {
          path: "/roll/commit",
          method: "POST",
        },
        "roll/reveal": {
          path: "/roll/reveal",
          method: "POST",
        },
        move: {
          path: "/move",
          method: "POST",
        },
        events: {
          path: "/events",
          method: "GET",
          websocket: true,
        },
      };

      const route = routeMap[action];
      if (!route) {
        return json(
          404,
          {
            error: "NOT_FOUND",
            message: "route not found",
          },
        );
      }

      requireMethod(
        request,
        route.method,
      );

      const target = room(env, matchId);
      const headers = new Headers();
      const authorization =
        request.headers.get("authorization");
      if (authorization) {
        headers.set(
          "authorization",
          authorization,
        );
      }
      const friendJoinToken =
        request.headers.get(
          "x-ludoproof-friend-join-token",
        );
      if (
        friendJoinToken &&
        action === "join"
      ) {
        headers.set(
          "x-ludoproof-friend-join-token",
          friendJoinToken,
        );
      }
      headers.set(
        "content-type",
        "application/json",
      );
      if (
        route.websocket
      ) {
        headers.set(
          "upgrade",
          "websocket",
        );
      }

      let body;
      if (route.method !== "GET") {
        body =
          JSON.stringify(
            await readJsonRequest(request),
          );
      }

      const policyKey =
        action === "join"
          ? "join"
          : (
              action === "state" ||
              action === "events"
            )
            ? "state"
            : "mutation";
      // Authenticated gameplay is rate-limited inside its authoritative room.
      // Joining and unauthenticated requests retain the public edge gate.
      if (action === "join" || !/^Bearer\s+\S{1,256}$/i.test(authorization ?? "")) {
        await enforceRateLimit(
          env,
          request,
          policyKey + ":" + matchId,
          RATE_POLICIES[policyKey],
        );
      }

      const response =
        await target.fetch(
          new Request(
            "https://room" + route.path,
            {
              method: route.method,
              headers,
              body,
            },
          ),
        );

      if (
        route.websocket &&
        response.status === 101
      ) {
        return response;
      }
      return withSecurityHeaders(response);
    } catch (error) {
      return errorResponse(error);
    }
  },
};

async function enforceRateLimit(
  env,
  request,
  scope,
  limit,
) {
  if (!env.LUDOPROOF_API_GATE) {
    throw httpError(
      503,
      "RATE_LIMITER_NOT_CONFIGURED",
      "API rate limiter is not configured",
    );
  }

  const clientIp =
    request.headers.get(
      "cf-connecting-ip",
    ) ?? "unknown";
  const rateKey =
    await sha256Hex(
      "ludoproof:api-rate:v1:" +
        scope +
        ":" +
        clientIp,
    );
  const id =
    env.LUDOPROOF_API_GATE
      .idFromName(rateKey);
  const target =
    env.LUDOPROOF_API_GATE.get(id);

  let response;
  try {
    response =
      await target.fetch(
        new Request(
          "https://gate/check",
          {
            method: "POST",
            headers: {
              "content-type":
                "application/json",
            },
            body: JSON.stringify({
              limit,
              periodMs:
                RATE_WINDOW_MS,
            }),
          },
        ),
      );
  } catch {
    throw httpError(
      503,
      "RATE_LIMITER_UNAVAILABLE",
      "API rate limiter is unavailable",
    );
  }

  let result;
  try {
    result =
      await response.json();
  } catch {
    throw httpError(
      503,
      "RATE_LIMITER_BAD_RESPONSE",
      "API rate limiter returned invalid data",
    );
  }

  if (
    response.status === 429 ||
    result.allowed === false
  ) {
    const error =
      httpError(
        429,
        "RATE_LIMITED",
        "too many requests",
      );
    error.retryAfter =
      response.headers.get(
        "retry-after",
      ) ?? "60";
    throw error;
  }

  if (
    !response.ok ||
    result.allowed !== true
  ) {
    throw httpError(
      503,
      "RATE_LIMITER_UNAVAILABLE",
      "API rate limiter rejected the check",
    );
  }
}

function room(env, matchId, request = null) {
  if (!env.LUDOPROOF_MATCHES) {
    throw httpError(
      503,
      "MATCH_STORE_NOT_CONFIGURED",
      "match storage is not configured",
    );
  }
  const id =
    env.LUDOPROOF_MATCHES.idFromName(matchId);
  const locationHint = matchLocationHint(env, request);
  return locationHint
    ? env.LUDOPROOF_MATCHES.get(id, { locationHint })
    : env.LUDOPROOF_MATCHES.get(id);
}

function friends(env) {
  if (
    !env.LUDOPROOF_FRIENDS
  ) {
    throw httpError(
      503,
      "FRIENDS_NOT_CONFIGURED",
      "friend directory is not configured",
    );
  }

  const id =
    env.LUDOPROOF_FRIENDS
      .idFromName(
        "GLOBAL:V1",
      );
  return env
    .LUDOPROOF_FRIENDS
    .get(id);
}

function matchmaker(
  env,
  playerCount,
) {
  if (
    !env.LUDOPROOF_MATCHMAKER
  ) {
    throw httpError(
      503,
      "MATCHMAKER_NOT_CONFIGURED",
      "public matchmaking is not configured",
    );
  }

  const id =
    env.LUDOPROOF_MATCHMAKER
      .idFromName(
        "ONLINE:" +
          playerCount +
          ":CLASSIC_V1",
      );
  return env
    .LUDOPROOF_MATCHMAKER
    .get(id);
}

function leaderboard(env) {
  if (!env.LUDOPROOF_LEADERBOARD) {
    throw httpError(
      503,
      "LEADERBOARD_NOT_CONFIGURED",
      "leaderboard storage is not configured",
    );
  }
  const id =
    env.LUDOPROOF_LEADERBOARD
      .idFromName("global");
  return env.LUDOPROOF_LEADERBOARD.get(id);
}

function configurationChecks(env) {
  return {
    matchStoreConfigured:
      Boolean(env.LUDOPROOF_MATCHES),
    rateGateConfigured:
      Boolean(env.LUDOPROOF_API_GATE),
    leaderboardConfigured:
      Boolean(env.LUDOPROOF_LEADERBOARD),
    matchmakerConfigured:
      Boolean(env.LUDOPROOF_MATCHMAKER),
    friendsConfigured:
      Boolean(env.LUDOPROOF_FRIENDS),
    sessionKeyConfigured:
      hasSessionKey(env),
  };
}

function requireMatchmakingPlayerCount(
  value,
) {
  const count =
    Number(value);
  if (
    count !== 2 &&
    count !== 4
  ) {
    throw httpError(
      400,
      "INVALID_PLAYER_COUNT",
      "public matchmaking supports 2 or 4 players",
    );
  }
  return count;
}

function requireMethod(request, expected) {
  if (request.method !== expected) {
    const error = httpError(
      405,
      "METHOD_NOT_ALLOWED",
      "method not allowed",
    );
    error.allow = expected;
    throw error;
  }
}

async function readJsonRequest(request) {
  const contentType =
    request.headers.get("content-type") ?? "";
  if (
    !contentType
      .toLowerCase()
      .startsWith("application/json")
  ) {
    throw httpError(
      415,
      "UNSUPPORTED_MEDIA_TYPE",
      "content-type must be application/json",
    );
  }

  const declaredLength =
    Number(
      request.headers.get("content-length"),
    );
  if (
    Number.isFinite(declaredLength) &&
    declaredLength > MAX_BODY_BYTES
  ) {
    throw httpError(
      413,
      "REQUEST_TOO_LARGE",
      "request body is too large",
    );
  }

  const text = await request.text();
  const actualLength =
    new TextEncoder()
      .encode(text)
      .byteLength;
  if (actualLength > MAX_BODY_BYTES) {
    throw httpError(
      413,
      "REQUEST_TOO_LARGE",
      "request body is too large",
    );
  }

  let value;
  try {
    value =
      text.length === 0
        ? {}
        : JSON.parse(text);
  } catch {
    throw httpError(
      400,
      "INVALID_JSON",
      "request body must be valid JSON",
    );
  }

  if (
    !value ||
    typeof value !== "object" ||
    Array.isArray(value)
  ) {
    throw httpError(
      400,
      "INVALID_JSON",
      "request body must be a JSON object",
    );
  }

  return value;
}

function json(status, body, extraHeaders = null) {
  const headers =
    securityHeaders();
  headers.set(
    "content-type",
    "application/json; charset=utf-8",
  );
  headers.set(
    "cache-control",
    "no-store",
  );

  if (extraHeaders) {
    for (const [name, value] of extraHeaders) {
      headers.set(name, value);
    }
  }

  return new Response(
    JSON.stringify(body),
    {
      status,
      headers,
    },
  );
}

function withSecurityHeaders(response) {
  const headers =
    new Headers(response.headers);
  const hardened =
    securityHeaders();

  for (const [name, value] of hardened) {
    headers.set(name, value);
  }
  headers.set(
    "cache-control",
    "no-store",
  );

  return new Response(
    response.body,
    {
      status: response.status,
      statusText: response.statusText,
      headers,
    },
  );
}

function securityHeaders() {
  return new Headers({
    "content-security-policy":
      "default-src 'none'; frame-ancestors 'none'; base-uri 'none'",
    "permissions-policy":
      "camera=(), microphone=(), geolocation=()",
    "referrer-policy":
      "no-referrer",
    "strict-transport-security":
      "max-age=31536000; includeSubDomains",
    "x-content-type-options":
      "nosniff",
    "x-frame-options":
      "DENY",
    "x-robots-tag":
      "noindex, nofollow",
  });
}

function errorResponse(error) {
  const status =
    Number.isInteger(error?.status) &&
    error.status >= 400 &&
    error.status <= 599
      ? error.status
      : 500;

  const extraHeaders = [];
  if (error?.allow) {
    extraHeaders.push(
      ["allow", error.allow],
    );
  }
  if (error?.retryAfter) {
    extraHeaders.push(
      [
        "retry-after",
        String(error.retryAfter),
      ],
    );
  }

  return json(
    status,
    {
      error:
        error?.code ??
        "INTERNAL_ERROR",
      message:
        status === 500 &&
        !error?.code
          ? "internal server error"
          : String(
              error?.message ??
                "internal server error",
            ),
    },
    extraHeaders,
  );
}
