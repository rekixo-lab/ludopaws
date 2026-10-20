import { isFamilyMode } from "./family-safety.js";
const HEX_32 = /^[0-9a-f]{64}$/i;
const REQUEST_ID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MATCH_ALPHABET =
  "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export async function sha256Hex(text) {
  const bytes = new TextEncoder().encode(text);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)]
    .map((value) => value.toString(16).padStart(2, "0"))
    .join("");
}

export function canonicalJson(value) {
  return serialize(value);
}

function serialize(value) {
  if (value === null) return "null";
  if (typeof value === "string") return JSON.stringify(value);
  if (typeof value === "boolean") return value ? "true" : "false";

  if (typeof value === "number") {
    if (!Number.isSafeInteger(value)) {
      throw new TypeError("canonical JSON accepts safe integers only");
    }
    return Object.is(value, -0) ? "0" : String(value);
  }

  if (Array.isArray(value)) {
    return "[" + value.map(serialize).join(",") + "]";
  }

  if (value && typeof value === "object") {
    const keys = Object.keys(value).sort();
    return (
      "{" +
      keys
        .map((key) => {
          const child = value[key];
          if (
            child === undefined ||
            typeof child === "function" ||
            typeof child === "symbol" ||
            typeof child === "bigint"
          ) {
            throw new TypeError("unsupported canonical JSON value");
          }
          return JSON.stringify(key) + ":" + serialize(child);
        })
        .join(",") +
      "}"
    );
  }

  throw new TypeError("unsupported canonical JSON value");
}

export async function deterministicMatchId(
  clientRequestId,
  attempt = 0,
) {
  const requestId =
    requireClientRequestId(
      clientRequestId,
    );
  if (
    !Number.isInteger(attempt) ||
    attempt < 0 ||
    attempt > 16
  ) {
    throw new RangeError(
      "match ID attempt is invalid",
    );
  }
  const bytes =
    await sha256Bytes(
      "ludoproof:match-id:v1:" +
        requestId +
        ":" +
        attempt,
    );
  let value = "LP";
  for (let index = 0; index < 8; index += 1) {
    value +=
      MATCH_ALPHABET[
        bytes[index] & 31
      ];
  }
  return value;
}

export async function derivePlayerIdentity(
  env,
  matchId,
  clientRequestId,
) {
  const requestId =
    requireClientRequestId(
      clientRequestId,
    );
  const sessionKey =
    requireSessionKey(env);
  const playerDigest =
    await sha256Hex(
      "ludoproof:player-id:v1:" +
        matchId +
        ":" +
        requestId,
    );
  const tokenBytes =
    await hmacSha256(
      sessionKey,
      "ludoproof:player-token:v2:" +
        matchId +
        ":" +
        requestId,
    );

  return {
    playerId:
      "p_" +
      playerDigest.slice(0, 32),
    playerToken:
      "lp_" +
      base64Url(tokenBytes),
  };
}

export async function deriveFriendIdentity(
  env,
  clientRequestId,
) {
  const requestId =
    requireClientRequestId(
      clientRequestId,
    );
  const sessionKey =
    requireSessionKey(env);
  const digestBytes =
    await sha256Bytes(
      "ludoproof:friend-id:v1:" +
        requestId,
    );

  let friendId =
    "LPF-";
  for (
    let index = 0;
    index < 12;
    index += 1
  ) {
    if (
      index === 4 ||
      index === 8
    ) {
      friendId +=
        "-";
    }
    friendId +=
      MATCH_ALPHABET[
        digestBytes[index] &
          31
      ];
  }

  const tokenBytes =
    await hmacSha256(
      sessionKey,
      "ludoproof:friend-token:v1:" +
        requestId,
    );

  return {
    friendId,
    friendToken:
      "lf_" +
      base64Url(
        tokenBytes,
      ),
  };
}

export async function deriveLeaderboardIdentity(
  env,
  clientRequestId,
) {
  const requestId =
    requireClientRequestId(
      clientRequestId,
    );
  const sessionKey =
    requireSessionKey(env);
  const profileBytes =
    await hmacSha256(
      sessionKey,
      "ludoproof:leaderboard-profile-id:v2:" +
        requestId,
    );
  const uuidBytes =
    profileBytes.slice(
      0,
      16,
    );
  uuidBytes[6] =
    (uuidBytes[6] & 0x0f) |
    0x40;
  uuidBytes[8] =
    (uuidBytes[8] & 0x3f) |
    0x80;

  const tokenBytes =
    await hmacSha256(
      sessionKey,
      "ludoproof:leaderboard-profile-token:v1:" +
        requestId,
    );

  return {
    profileId:
      uuidV4FromBytes(
        uuidBytes,
      ),
    profileToken:
      "lpp_" +
      base64Url(
        tokenBytes,
      ),
  };
}

export async function issueFriendRoomJoinToken(
  env,
  {
    matchId,
    hostFriendId,
    friendId,
    inviteId,
    expiresAt,
  },
) {
  const claims =
    normalizeFriendJoinClaims({
      matchId,
      hostFriendId,
      friendId,
      inviteId,
      expiresAt,
    });
  const encodedPayload =
    base64Url(
      new TextEncoder()
        .encode(
          canonicalJson({
            v: 1,
            ...claims,
          }),
        ),
    );
  const signature =
    await hmacSha256(
      requireSessionKey(env),
      "ludoproof:friend-room-join:v1:" +
        encodedPayload,
    );

  return (
    "lfj_" +
    encodedPayload +
    "." +
    base64Url(signature)
  );
}

export async function verifyFriendRoomJoinToken(
  env,
  token,
  now = Date.now(),
) {
  if (
    typeof token !== "string" ||
    token.length < 80 ||
    token.length > 2048
  ) {
    throw httpError(
      401,
      "FRIEND_JOIN_TOKEN_INVALID",
      "friend-room join credential is invalid",
    );
  }

  const match =
    token.match(
      /^lfj_([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)$/,
    );
  if (!match) {
    throw httpError(
      401,
      "FRIEND_JOIN_TOKEN_INVALID",
      "friend-room join credential is invalid",
    );
  }

  const encodedPayload =
    match[1];
  let signatureBytes;
  let claims;
  try {
    signatureBytes =
      base64UrlDecode(
        match[2],
      );
    claims =
      JSON.parse(
        new TextDecoder()
          .decode(
            base64UrlDecode(
              encodedPayload,
            ),
          ),
      );
  } catch {
    throw httpError(
      401,
      "FRIEND_JOIN_TOKEN_INVALID",
      "friend-room join credential is invalid",
    );
  }

  const verified =
    await verifyHmacSha256(
      requireSessionKey(env),
      "ludoproof:friend-room-join:v1:" +
        encodedPayload,
      signatureBytes,
    );
  if (!verified) {
    throw httpError(
      401,
      "FRIEND_JOIN_TOKEN_INVALID",
      "friend-room join credential is invalid",
    );
  }

  if (
    claims?.v !== 1
  ) {
    throw httpError(
      401,
      "FRIEND_JOIN_TOKEN_INVALID",
      "friend-room join credential version is invalid",
    );
  }

  const normalized =
    normalizeFriendJoinClaims(
      claims,
    );
  if (
    !Number.isSafeInteger(now)
  ) {
    throw new TypeError(
      "friend-room join verification time must be a safe integer",
    );
  }
  if (
    normalized.expiresAt <= now
  ) {
    throw httpError(
      410,
      "FRIEND_JOIN_TOKEN_EXPIRED",
      "friend-room join credential expired",
    );
  }

  return normalized;
}

export async function issueLeaderboardProfileAssertion(
  env,
  {
    matchId,
    profileId,
    clientRequestId,
    expiresAt,
    playerType,
  },
) {
  const claims =
    normalizeLeaderboardAssertionClaims({
      matchId,
      profileId,
      clientRequestId,
      expiresAt,
      playerType,
    });
  const encodedPayload =
    base64Url(
      new TextEncoder()
        .encode(
          canonicalJson({
            v: 1,
            ...claims,
          }),
        ),
    );
  const signature =
    await hmacSha256(
      requireSessionKey(env),
      "ludoproof:leaderboard-profile-assertion:v1:" +
        encodedPayload,
    );

  return (
    "lpa_" +
    encodedPayload +
    "." +
    base64Url(signature)
  );
}

export async function verifyLeaderboardProfileAssertion(
  env,
  token,
  now = Date.now(),
) {
  if (
    typeof token !== "string" ||
    token.length < 80 ||
    token.length > 2048
  ) {
    throw httpError(
      401,
      "PROFILE_ASSERTION_INVALID",
      "leaderboard profile assertion is invalid",
    );
  }

  const match =
    token.match(
      /^lpa_([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)$/,
    );
  if (!match) {
    throw httpError(
      401,
      "PROFILE_ASSERTION_INVALID",
      "leaderboard profile assertion is invalid",
    );
  }

  const encodedPayload =
    match[1];
  let signatureBytes;
  let claims;
  try {
    signatureBytes =
      base64UrlDecode(
        match[2],
      );
    claims =
      JSON.parse(
        new TextDecoder()
          .decode(
            base64UrlDecode(
              encodedPayload,
            ),
          ),
      );
  } catch {
    throw httpError(
      401,
      "PROFILE_ASSERTION_INVALID",
      "leaderboard profile assertion is invalid",
    );
  }

  const verified =
    await verifyHmacSha256(
      requireSessionKey(env),
      "ludoproof:leaderboard-profile-assertion:v1:" +
        encodedPayload,
      signatureBytes,
    );
  if (
    !verified ||
    claims?.v !== 1
  ) {
    throw httpError(
      401,
      "PROFILE_ASSERTION_INVALID",
      "leaderboard profile assertion is invalid",
    );
  }

  const normalized =
    normalizeLeaderboardAssertionClaims(
      claims,
    );
  if (
    !Number.isSafeInteger(
      now,
    )
  ) {
    throw new TypeError(
      "leaderboard profile assertion time must be a safe integer",
    );
  }
  if (
    normalized.expiresAt <=
    now
  ) {
    throw httpError(
      410,
      "PROFILE_ASSERTION_EXPIRED",
      "leaderboard profile assertion expired",
    );
  }

  return normalized;
}

export function requireClientRequestId(
  value,
) {
  if (
    typeof value !== "string" ||
    !REQUEST_ID.test(value)
  ) {
    throw httpError(
      400,
      "INVALID_CLIENT_REQUEST_ID",
      "clientRequestId must be a UUID v4",
    );
  }
  return value.toLowerCase();
}

export function hasSessionKey(env) {
  return (
    typeof env?.LUDOPROOF_SESSION_HMAC_KEY ===
      "string" &&
    env.LUDOPROOF_SESSION_HMAC_KEY.length >=
      32 &&
    env.LUDOPROOF_SESSION_HMAC_KEY.length <=
      512
  );
}

export function randomToken() {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  const body = base64Url(bytes);
  return "lp_" + body;
}

export function randomMatchId() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  let value = "LP";
  for (const byte of bytes) {
    value += alphabet[byte % alphabet.length];
  }
  return value;
}

export function requireDigest(value, name) {
  if (typeof value !== "string" || !HEX_32.test(value)) {
    throw httpError(400, "INVALID_" + name.toUpperCase(), name + " must be SHA-256 hex");
  }
  return value.toLowerCase();
}

export function normalizeDisplayName(value, env) {
  if (isFamilyMode(env)) return "Player";
  if (typeof value !== "string") {
    throw httpError(400, "INVALID_DISPLAY_NAME", "displayName is required");
  }

  const normalized =
    value.normalize("NFC");

  if (
    /[\u0000-\u001F\u007F-\u009F\u061C\u200B\u200E\u200F\u202A-\u202E\u2060\u2066-\u2069\uFEFF]/u
      .test(normalized)
  ) {
    throw httpError(
      400,
      "INVALID_DISPLAY_NAME",
      "displayName contains invisible or directional control characters",
    );
  }

  const name =
    normalized
      .trim()
      .replace(/\s+/gu, " ");
  const codePoints =
    [...name];

  if (
    codePoints.length < 2 ||
    codePoints.length > 24
  ) {
    throw httpError(
      400,
      "INVALID_DISPLAY_NAME",
      "displayName must contain 2 to 24 characters",
    );
  }

  return name;
}

export function bearerToken(request) {
  const header = request.headers.get("authorization") || "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) throw httpError(401, "AUTH_REQUIRED", "player bearer token is required");
  return match[1];
}

export async function readJsonObject(request, maxBytes = 8 * 1024) {
  const type = request.headers.get("content-type") ?? "";
  if (!type.toLowerCase().startsWith("application/json")) {
    throw httpError(415, "UNSUPPORTED_MEDIA_TYPE", "content-type must be application/json");
  }
  if (!Number.isSafeInteger(maxBytes) || maxBytes < 1 || maxBytes > 1024 * 1024) {
    throw new TypeError("JSON body limit is invalid");
  }
  const declaredLength = request.headers.get("content-length");
  if (declaredLength !== null) {
    if (!/^\d+$/.test(declaredLength)) {
      throw httpError(400, "INVALID_CONTENT_LENGTH", "request content length is invalid");
    }
    if (Number(declaredLength) > maxBytes) {
      throw httpError(413, "REQUEST_TOO_LARGE", "request body is too large");
    }
  }

  const chunks = [];
  let totalBytes = 0;
  const reader = request.body?.getReader();
  if (reader) {
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        totalBytes += value.byteLength;
        if (totalBytes > maxBytes) {
          try { await reader.cancel(); } catch {}
          throw httpError(413, "REQUEST_TOO_LARGE", "request body is too large");
        }
        chunks.push(value);
      }
    } finally {
      try { reader.releaseLock(); } catch {}
    }
  }
  const bytes = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }

  let text;
  try {
    text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    throw httpError(400, "INVALID_JSON", "request body must be valid UTF-8 JSON");
  }
  try {
    const value = text.length ? JSON.parse(text) : {};
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("object required");
    return value;
  } catch {
    throw httpError(400, "INVALID_JSON", "request body must be a JSON object");
  }
}

export function httpError(status, code, message) {
  const error = new Error(message);
  error.status = status;
  error.code = code;
  return error;
}

async function sha256Bytes(text) {
  const bytes =
    new TextEncoder().encode(text);
  const digest =
    await crypto.subtle.digest(
      "SHA-256",
      bytes,
    );
  return new Uint8Array(digest);
}

function normalizeLeaderboardAssertionClaims(value) {
  const matchId = String(value?.matchId ?? "").trim().toUpperCase();
  const rawProfileId = value?.profileId;
  const profileId =
    rawProfileId == null ? null : String(rawProfileId).trim().toLowerCase();
  const clientRequestId = requireClientRequestId(value?.clientRequestId);
  const expiresAt = Number(value?.expiresAt);
  const hasPlayerType = value?.playerType != null;
  const playerType = hasPlayerType
    ? String(value.playerType).trim().toUpperCase()
    : "HUMAN";

  if (
    !/^LP[A-Z2-9]{8}$/.test(matchId) ||
    (profileId !== null &&
      !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(profileId)) ||
    !Number.isSafeInteger(expiresAt) ||
    expiresAt <= 0 ||
    !["HUMAN", "BOT"].includes(playerType) ||
    (playerType === "BOT" && profileId !== null)
  ) {
    throw httpError(
      401,
      "PROFILE_ASSERTION_INVALID",
      "leaderboard profile assertion contains invalid claims",
    );
  }

  return {
    matchId,
    profileId,
    clientRequestId,
    expiresAt,
    ...(hasPlayerType ? { playerType } : {}),
  };
}
function normalizeFriendJoinClaims(
  value,
) {
  const matchId =
    String(
      value?.matchId ??
        "",
    )
      .trim()
      .toUpperCase();
  const hostFriendId =
    String(
      value?.hostFriendId ??
        "",
    )
      .trim()
      .toUpperCase();
  const friendId =
    String(
      value?.friendId ??
        "",
    )
      .trim()
      .toUpperCase();
  const inviteId =
    String(
      value?.inviteId ??
        "",
    )
      .trim()
      .toUpperCase();
  const expiresAt =
    Number(
      value?.expiresAt,
    );

  if (
    !/^LP[A-Z2-9]{8}$/
      .test(matchId) ||
    !/^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/
      .test(hostFriendId) ||
    !/^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/
      .test(friendId) ||
    !/^FIV-[A-F0-9]{20}$/
      .test(inviteId) ||
    !Number.isSafeInteger(
      expiresAt,
    ) ||
    expiresAt <= 0
  ) {
    throw httpError(
      401,
      "FRIEND_JOIN_TOKEN_INVALID",
      "friend-room join credential contains invalid claims",
    );
  }

  return {
    matchId,
    hostFriendId,
    friendId,
    inviteId,
    expiresAt,
  };
}

async function hmacSha256(
  secret,
  message,
) {
  const key =
    await crypto.subtle.importKey(
      "raw",
      new TextEncoder()
        .encode(secret),
      {
        name: "HMAC",
        hash: "SHA-256",
      },
      false,
      ["sign"],
    );
  const signature =
    await crypto.subtle.sign(
      "HMAC",
      key,
      new TextEncoder()
        .encode(message),
    );
  return new Uint8Array(
    signature,
  );
}

async function verifyHmacSha256(
  secret,
  message,
  signature,
) {
  const key =
    await crypto.subtle.importKey(
      "raw",
      new TextEncoder()
        .encode(secret),
      {
        name: "HMAC",
        hash: "SHA-256",
      },
      false,
      ["verify"],
    );

  return crypto.subtle.verify(
    "HMAC",
    key,
    signature,
    new TextEncoder()
      .encode(message),
  );
}

function requireSessionKey(env) {
  if (!hasSessionKey(env)) {
    throw httpError(
      503,
      "SESSION_KEY_NOT_CONFIGURED",
      "LudoProof session-token HMAC key is not configured",
    );
  }
  return env.LUDOPROOF_SESSION_HMAC_KEY;
}

function uuidV4FromBytes(
  bytes,
) {
  if (
    !(bytes instanceof Uint8Array) ||
    bytes.length !== 16
  ) {
    throw new TypeError(
      "UUID requires 16 bytes",
    );
  }

  const hex =
    [...bytes]
      .map(
        (value) =>
          value
            .toString(16)
            .padStart(2, "0"),
      )
      .join("");

  return (
    hex.slice(0, 8) +
    "-" +
    hex.slice(8, 12) +
    "-" +
    hex.slice(12, 16) +
    "-" +
    hex.slice(16, 20) +
    "-" +
    hex.slice(20)
  );
}

function base64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/g, "");
}

function base64UrlDecode(
  value,
) {
  if (
    typeof value !== "string" ||
    !/^[A-Za-z0-9_-]+$/
      .test(value)
  ) {
    throw new TypeError(
      "invalid base64url",
    );
  }

  const padded =
    value
      .replaceAll("-", "+")
      .replaceAll("_", "/")
      .padEnd(
        Math.ceil(
          value.length / 4,
        ) * 4,
        "=",
      );
  const binary =
    atob(padded);
  const bytes =
    new Uint8Array(
      binary.length,
    );
  for (
    let index = 0;
    index < binary.length;
    index += 1
  ) {
    bytes[index] =
      binary.charCodeAt(
        index,
      );
  }
  return bytes;
}
