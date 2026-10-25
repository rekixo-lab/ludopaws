import { httpError } from "./crypto.js";

const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const GOOGLE_ISSUERS = new Set(["accounts.google.com", "https://accounts.google.com"]);
const ACCESS_TOKEN_TTL_SECONDS = 15 * 60;
const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const CLOCK_SKEW_SECONDS = 120;
const jwksCache = new WeakMap();

export { ACCESS_TOKEN_TTL_SECONDS, SESSION_TTL_MS };

export async function verifyGoogleIdToken(env, idToken, expectedNonce, fetchImpl = fetch, nowMs = Date.now()) {
  const clientId = String(env?.GOOGLE_SIGN_IN_WEB_CLIENT_ID ?? "").trim();
  if (!clientId) throw httpError(503, "GOOGLE_SIGN_IN_NOT_CONFIGURED", "Google account sign-in is not configured on the server");
  if (typeof idToken !== "string" || idToken.length < 100 || idToken.length > 16 * 1024 ||
      typeof expectedNonce !== "string" || expectedNonce.length < 16 || expectedNonce.length > 512) {
    throw httpError(400, "GOOGLE_ID_TOKEN_INVALID", "Google sign-in information is invalid");
  }
  const parts = idToken.split(".");
  if (parts.length !== 3) throw httpError(401, "GOOGLE_ID_TOKEN_INVALID", "Google identity token is invalid");

  let header, claims, signature;
  try {
    header = decodeJson(parts[0]);
    claims = decodeJson(parts[1]);
    signature = decodeBase64Url(parts[2]);
  } catch {
    throw httpError(401, "GOOGLE_ID_TOKEN_INVALID", "Google identity token is invalid");
  }
  if (header?.alg !== "RS256" || typeof header?.kid !== "string" || header.kid.length > 256 || header?.crit != null) {
    throw httpError(401, "GOOGLE_ID_TOKEN_INVALID", "Google identity token uses an unsupported signature");
  }

  let keys = await loadGoogleKeys(fetchImpl, nowMs, false);
  let jwk = findGoogleKey(keys, header.kid);
  if (!jwk) {
    keys = await loadGoogleKeys(fetchImpl, nowMs, true);
    jwk = findGoogleKey(keys, header.kid);
  }
  if (!jwk) throw httpError(401, "GOOGLE_ID_TOKEN_INVALID", "Google identity signing key was not recognized");

  let signatureValid = false;
  try {
    const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
    signatureValid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, signature, new TextEncoder().encode(parts[0] + "." + parts[1]));
  } catch { signatureValid = false; }
  if (!signatureValid) throw httpError(401, "GOOGLE_ID_TOKEN_INVALID", "Google identity token signature is invalid");

  const now = Math.floor(nowMs / 1000);
  const audienceMatches = claims?.aud === clientId || (Array.isArray(claims?.aud) && claims.aud.includes(clientId));
  // Android presents a token intended for the Web backend: azp can be its
  // distinct Android OAuth client. Never infer trust from a shared project prefix.
  const presenters = new Set([clientId, ...String(env?.GOOGLE_SIGN_IN_ANDROID_CLIENT_IDS ?? "")
    .split(",").map((value) => value.trim()).filter(Boolean)]);
  const presenterMatches = claims?.azp == null ||
    (typeof claims.azp === "string" && presenters.has(claims.azp));
  if (!GOOGLE_ISSUERS.has(String(claims?.iss ?? "")) || !audienceMatches ||
      !presenterMatches ||
      typeof claims?.sub !== "string" || claims.sub.length < 1 || claims.sub.length > 255 ||
      !Number.isSafeInteger(claims?.exp) || claims.exp <= now - CLOCK_SKEW_SECONDS ||
      !Number.isSafeInteger(claims?.iat) || claims.iat > now + CLOCK_SKEW_SECONDS || claims.iat < now - 7200 ||
      claims.exp <= claims.iat ||
      claims?.nonce !== expectedNonce) {
    throw httpError(401, "GOOGLE_ID_TOKEN_INVALID", "Google identity token claims are invalid or expired");
  }
  return { subject: claims.sub, displayName: normalizeDisplayName(claims.name) };
}
export async function hashGoogleSubject(subject) { return sha256Hex("ludoproof:google-subject:v1:" + subject); }
export async function hashGoogleNonce(nonce) { return sha256Hex("ludoproof:google-nonce:v1:" + nonce); }
export async function billingAccountId(accountId) {
  if (!isAccountId(accountId)) throw httpError(400, "INVALID_ACCOUNT_ID", "account identity is invalid");
  return sha256Hex("ludoproof:play-billing-account:v1:" + accountId);
}
export function isAccountId(value) {
  return typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
export function isSessionId(value) { return isAccountId(value); }
export async function issueAccessToken(env, accountId, sessionId, nowMs = Date.now()) {
  const secret = requireSessionSecret(env);
  if (!isAccountId(accountId) || !isSessionId(sessionId)) throw httpError(500, "ACCOUNT_SESSION_INVALID", "account session could not be created");
  const now = Math.floor(nowMs / 1000);
  const payload = { iss: "https://ludoproof", aud: "ludoproof-android", sub: accountId, sid: sessionId, typ: "access", iat: now, exp: now + ACCESS_TOKEN_TTL_SECONDS };
  const unsigned = encodeBase64UrlBytes(new TextEncoder().encode(JSON.stringify({ alg: "HS256", typ: "JWT" }))) + "." +
    encodeBase64UrlBytes(new TextEncoder().encode(JSON.stringify(payload)));
  return unsigned + "." + encodeBase64UrlBytes(await hmacSha256(secret, unsigned));
}
export async function verifyAccessToken(env, token, nowMs = Date.now()) {
  return verifyAccessTokenClaims(env, token, nowMs, false);
}

// Account deletion may commit server-side while its HTTP response is lost. This
// verifier is only for the deletion endpoint's replay path; its caller must
// confirm the PlayerAccount deletion tombstone before returning success. Never
// use this function to authorize any state-changing or data-reading operation.
export async function verifyAccessTokenForDeletionReplay(env, token, nowMs = Date.now()) {
  return verifyAccessTokenClaims(env, token, nowMs, true);
}

async function verifyAccessTokenClaims(env, token, nowMs, allowExpiredForDeletionReplay) {
  const secret = requireSessionSecret(env);
  if (typeof token !== "string" || token.length > 4096) throw httpError(401, "ACCOUNT_SESSION_REQUIRED", "Please sign in to your Ludo Paws account");
  const parts = token.split(".");
  if (parts.length !== 3) throw httpError(401, "ACCOUNT_SESSION_INVALID", "Account session is invalid or expired");
  let header, claims, supplied;
  try { header = decodeJson(parts[0]); claims = decodeJson(parts[1]); supplied = decodeBase64Url(parts[2]); }
  catch { throw httpError(401, "ACCOUNT_SESSION_INVALID", "Account session is invalid or expired"); }
  if (header?.alg !== "HS256" || header?.typ !== "JWT") throw httpError(401, "ACCOUNT_SESSION_INVALID", "Account session is invalid or expired");
  const expected = await hmacSha256(secret, parts[0] + "." + parts[1]);
  if (!constantTimeBytesEqual(supplied, expected)) throw httpError(401, "ACCOUNT_SESSION_INVALID", "Account session is invalid or expired");
  const now = Math.floor(nowMs / 1000);
  if (claims?.iss !== "https://ludoproof" || claims?.aud !== "ludoproof-android" || claims?.typ !== "access" ||
      !isAccountId(claims?.sub) || !isSessionId(claims?.sid) ||
      !Number.isSafeInteger(claims?.iat) || !Number.isSafeInteger(claims?.exp) ||
      claims.iat > now + CLOCK_SKEW_SECONDS ||
      (!allowExpiredForDeletionReplay && claims.exp <= now) ||
      claims.exp <= claims.iat ||
      claims.exp - claims.iat > ACCESS_TOKEN_TTL_SECONDS) {
    throw httpError(401, "ACCOUNT_SESSION_INVALID", "Account session is invalid or expired");
  }
  return { accountId: claims.sub, sessionId: claims.sid, expiresAt: claims.exp };
}
export async function newRefreshToken() {
  return "lpr_" + encodeBase64UrlBytes(crypto.getRandomValues(new Uint8Array(32)));
}
export async function hashRefreshToken(token) { return sha256Hex("ludoproof:refresh-token:v1:" + token); }
export async function deriveRotatedRefreshToken(env, sessionId, requestId, previousToken) {
  return "lpr_" + encodeBase64UrlBytes(await hmacSha256(
    requireSessionSecret(env),
    "ludoproof:refresh-rotate:v1:" + sessionId + ":" + requestId + ":" + previousToken,
  ));
}
export function validRefreshToken(value) { return typeof value === "string" && /^lpr_[A-Za-z0-9_-]{43}$/.test(value); }
export async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
function requireSessionSecret(env) {
  const value = env?.LUDOPROOF_SESSION_HMAC_KEY;
  if (typeof value !== "string" || new TextEncoder().encode(value).length < 32) throw httpError(503, "ACCOUNT_SESSION_NOT_CONFIGURED", "Secure account sessions are not configured");
  return value;
}
async function hmacSha256(secret, value) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value)));
}
function constantTimeBytesEqual(left, right) {
  if (!(left instanceof Uint8Array) || !(right instanceof Uint8Array) || left.length !== right.length) return false;
  let difference = 0;
  for (let i = 0; i < left.length; i += 1) difference |= left[i] ^ right[i];
  return difference === 0;
}
function findGoogleKey(keys, kid) {
  return keys.find((key) => key?.kid === kid && key?.kty === "RSA" &&
    (key?.alg == null || key.alg === "RS256") && (key?.use == null || key.use === "sig"));
}
async function loadGoogleKeys(fetchImpl, nowMs, forceRefresh) {
  const cached = jwksCache.get(fetchImpl);
  if (!forceRefresh && cached && cached.expiresAt > nowMs) return cached.keys;
  let response;
  try { response = await fetchImpl(GOOGLE_JWKS_URL, { method: "GET", headers: { accept: "application/json" }, signal: AbortSignal.timeout(8000) }); }
  catch { throw httpError(503, "GOOGLE_IDENTITY_UNAVAILABLE", "Google sign-in verification is temporarily unavailable"); }
  if (!response.ok) throw httpError(503, "GOOGLE_IDENTITY_UNAVAILABLE", "Google sign-in verification is temporarily unavailable");
  let data;
  try { data = await response.json(); } catch { throw httpError(503, "GOOGLE_IDENTITY_UNAVAILABLE", "Google sign-in keys could not be loaded"); }
  if (!Array.isArray(data?.keys) || data.keys.length < 1 || data.keys.length > 32) throw httpError(503, "GOOGLE_IDENTITY_UNAVAILABLE", "Google sign-in keys are invalid");
  const maxAgeMatch = (response.headers.get("cache-control") ?? "").match(/(?:^|,)\s*max-age=(\d+)/i);
  const maxAge = maxAgeMatch ? Number(maxAgeMatch[1]) : 3600;
  const ttl = Math.max(60, Math.min(6 * 60 * 60, Number.isFinite(maxAge) ? maxAge : 3600));
  jwksCache.set(fetchImpl, { keys: data.keys, expiresAt: nowMs + ttl * 1000 });
  return data.keys;
}
function normalizeDisplayName(value) {
  return String(value ?? "").replace(/[\u0000-\u001f\u007f]/g, "").trim().replace(/\s+/g, " ").slice(0, 24) || "Guest User";
}
function decodeJson(segment) { return JSON.parse(new TextDecoder().decode(decodeBase64Url(segment))); }
function decodeBase64Url(value) {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new TypeError("invalid base64url");
  const standard = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(standard + "=".repeat((4 - standard.length % 4) % 4));
  return Uint8Array.from(binary, (ch) => ch.charCodeAt(0));
}
function encodeBase64UrlBytes(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
