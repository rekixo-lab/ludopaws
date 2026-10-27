import { httpError } from "./crypto.js";
import { REFERRAL_PACKAGE, signingCertificates } from "./referral-config.js";

const encode = bytes => btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
const jsonPart = value => encode(new TextEncoder().encode(JSON.stringify(value)));
let cachedToken = null;
let pendingToken = null;

const unavailable = () => httpError(503, "INTEGRITY_UNAVAILABLE", "Installation verification is temporarily unavailable.");

async function providerRequest(url, options) {
  try { return await fetch(url, { ...options, signal: AbortSignal.timeout(10_000) }); }
  catch { throw unavailable(); }
}

async function providerJson(response) {
  try { return await response.json(); }
  catch { throw unavailable(); }
}

async function accessToken(env) {
  const credentialKey = env.LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL + env.LUDOPAWS_INTEGRITY_PRIVATE_KEY;
  if (cachedToken?.key === credentialKey && cachedToken.expires > Date.now() + 60_000) return cachedToken.token;
  if (pendingToken?.key === credentialKey) return pendingToken.promise;
  const pending = { key: credentialKey, promise: mintToken(env, credentialKey) };
  pendingToken = pending;
  try { return await pending.promise; }
  finally { if (pendingToken === pending) pendingToken = null; }
}

async function mintToken(env, credentialKey) {
  try {
    const now = Math.floor(Date.now() / 1000);
    const assertion = jsonPart({ alg: "RS256", typ: "JWT" }) + "." + jsonPart({
      iss: env.LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL,
      scope: "https://www.googleapis.com/auth/playintegrity",
      aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
    });
    const pem = String(env.LUDOPAWS_INTEGRITY_PRIVATE_KEY).replaceAll("\\n", "\n")
      .replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
    const key = await crypto.subtle.importKey("pkcs8", Uint8Array.from(atob(pem), char => char.charCodeAt(0)),
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
    const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(assertion));
    const response = await providerRequest("https://oauth2.googleapis.com/token", {
      method: "POST",
      body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: assertion + "." + encode(new Uint8Array(signature)) }),
    });
    if (!response.ok) throw unavailable();
    const token = await providerJson(response);
    const lifetime = Number(token?.expires_in);
    if (typeof token?.access_token !== "string" || !token.access_token.trim() || !Number.isFinite(lifetime) || lifetime <= 0) throw unavailable();
    cachedToken = { key: credentialKey, token: token.access_token, expires: Date.now() + Math.min(lifetime, 3600) * 1000 };
    return cachedToken.token;
  } catch { throw unavailable(); }
}

export async function verifyReferralIntegrity(env, integrityToken, requestHash) {
  if (typeof integrityToken !== "string" || integrityToken.length < 32 || integrityToken.length > 18000) {
    throw httpError(400, "INTEGRITY_REQUIRED", "Play installation verification is required.");
  }
  const minimumVersion = Number(env.LUDOPAWS_REFERRAL_MIN_VERSION_CODE);
  const allowedCertificates = signingCertificates(env);
  if (!/^\d{1,12}$/.test(String(env.LUDOPAWS_REFERRAL_MIN_VERSION_CODE ?? "")) || !Number.isSafeInteger(minimumVersion) || minimumVersion <= 0 || !allowedCertificates.length) throw unavailable();
  const token = await accessToken(env);
  const response = await providerRequest(`https://playintegrity.googleapis.com/v1/${REFERRAL_PACKAGE}:decodeIntegrityToken`, {
    method: "POST",
    headers: { authorization: "Bearer " + token, "content-type": "application/json" },
    body: JSON.stringify({ integrityToken }),
  });
  if (!response.ok) {
    if (response.status === 401 && cachedToken?.token === token) cachedToken = null;
    const transient = response.status === 429 || response.status >= 500 || response.status === 401;
    throw httpError(transient ? 503 : 403, transient ? "INTEGRITY_UNAVAILABLE" : "INSTALL_NOT_VERIFIED", "Google Play could not verify this installation. Please retry later.");
  }
  const payload = (await providerJson(response))?.tokenPayloadExternal;
  const details = payload?.requestDetails;
  const app = payload?.appIntegrity;
  const certificates = allowedCertificates.map(hex => encode(Uint8Array.from(hex.match(/../g), pair => parseInt(pair, 16))));
  const timestamp = Number(details?.timestampMillis);
  if (details?.requestPackageName !== REFERRAL_PACKAGE || details?.requestHash !== requestHash ||
      !Number.isSafeInteger(timestamp) || timestamp < Date.now() - 5 * 60_000 || timestamp > Date.now() + 30_000 ||
      app?.appRecognitionVerdict !== "PLAY_RECOGNIZED" || app?.packageName !== REFERRAL_PACKAGE ||
      !Array.isArray(app?.certificateSha256Digest) || !app.certificateSha256Digest.some(value => certificates.includes(value)) ||
      !/^\d+$/.test(String(app?.versionCode ?? "")) || !Number.isSafeInteger(Number(app.versionCode)) || Number(app.versionCode) < minimumVersion ||
      payload?.accountDetails?.appLicensingVerdict !== "LICENSED" ||
      !Array.isArray(payload?.deviceIntegrity?.deviceRecognitionVerdict) ||
      !payload?.deviceIntegrity?.deviceRecognitionVerdict?.includes("MEETS_DEVICE_INTEGRITY")) {
    throw httpError(403, "INSTALL_NOT_VERIFIED", "A genuine, licensed Play installation is required for this reward.");
  }
}
