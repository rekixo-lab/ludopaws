import { httpError } from "./crypto.js";
import { REFERRAL_PACKAGE, signingCertificates } from "./referral-config.js";

const encode = bytes => btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
const jsonPart = value => encode(new TextEncoder().encode(JSON.stringify(value)));
let cachedToken = null;

export async function verifyReferralIntegrity(env, integrityToken, requestHash) {
  if (typeof integrityToken !== "string" || integrityToken.length < 32 || integrityToken.length > 18000) {
    throw httpError(400, "INTEGRITY_REQUIRED", "Play installation verification is required.");
  }
  const credentialKey = env.LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL + env.LUDOPAWS_INTEGRITY_PRIVATE_KEY;
  if (!cachedToken || cachedToken.key !== credentialKey || cachedToken.expires < Date.now() + 60_000) {
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
    const response = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST", signal: AbortSignal.timeout(10_000),
      body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: assertion + "." + encode(new Uint8Array(signature)) }),
    });
    if (!response.ok) throw httpError(503, "INTEGRITY_UNAVAILABLE", "Installation verification is temporarily unavailable.");
    const token = await response.json();
    if (typeof token.access_token !== "string") throw httpError(503, "INTEGRITY_UNAVAILABLE", "Installation verification is temporarily unavailable.");
    cachedToken = { key: credentialKey, token: token.access_token, expires: Date.now() + Math.min(Number(token.expires_in) || 0, 3600) * 1000 };
  }
  const response = await fetch(`https://playintegrity.googleapis.com/v1/${REFERRAL_PACKAGE}:decodeIntegrityToken`, {
    method: "POST", signal: AbortSignal.timeout(10_000),
    headers: { authorization: "Bearer " + cachedToken.token, "content-type": "application/json" },
    body: JSON.stringify({ integrity_token: integrityToken }),
  });
  if (!response.ok) throw httpError(response.status >= 500 ? 503 : 403, "INSTALL_NOT_VERIFIED", "Google Play could not verify this installation.");
  const payload = (await response.json()).tokenPayloadExternal;
  const details = payload?.requestDetails;
  const app = payload?.appIntegrity;
  const certificates = signingCertificates(env).map(hex => encode(Uint8Array.from(hex.match(/../g), pair => parseInt(pair, 16))));
  const timestamp = Number(details?.timestampMillis);
  if (details?.requestPackageName !== REFERRAL_PACKAGE || details?.requestHash !== requestHash ||
      !Number.isSafeInteger(timestamp) || timestamp < Date.now() - 5 * 60_000 || timestamp > Date.now() + 30_000 ||
      app?.appRecognitionVerdict !== "PLAY_RECOGNIZED" || app?.packageName !== REFERRAL_PACKAGE ||
      !app?.certificateSha256Digest?.some(value => certificates.includes(value)) ||
      payload?.accountDetails?.appLicensingVerdict !== "LICENSED" ||
      !payload?.deviceIntegrity?.deviceRecognitionVerdict?.includes("MEETS_DEVICE_INTEGRITY")) {
    throw httpError(403, "INSTALL_NOT_VERIFIED", "A genuine, licensed Play installation is required for this reward.");
  }
}
