const KEY_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
const TEST_KEY_URL = "https://www.gstatic.com/admob/reward/verifier-keys-test.json";
const keyCache = new WeakMap();
const MAX_KEY_TTL_MS = 24 * 60 * 60 * 1000;
export const LEGACY_REWARDED_AD_GEMS = 5;
export const UPDATED_REWARDED_AD_GEMS = 2;

export async function verifyAdMobSsvRequest(request, env, fetchImpl = fetch, nowMs = Date.now()) {
  const url = new URL(request.url);
  if (request.method !== "GET" || url.pathname !== "/api/admob/ssv") throw ssvError(404, "SSV_NOT_FOUND", "route not found");
  const query = url.search.startsWith("?") ? url.search.slice(1) : "";
  if (query.length > 8 * 1024) throw ssvError(413, "SSV_REQUEST_TOO_LARGE", "reward callback is too large");
  const marker = "&signature=";
  const signatureIndex = query.indexOf(marker);
  if (signatureIndex < 1) throw ssvError(400, "SSV_SIGNATURE_MISSING", "signed reward callback is invalid");
  // Google's reference verifier uses URI.getQuery(), which decodes percent-escaped
  // query values before verifying the signed bytes. Preserve the raw query separately
  // for parameter parsing, but verify the once-decoded content in its original order.
  let signedContent;
  try {
    signedContent = decodeURIComponent(query.slice(0, signatureIndex));
  } catch {
    throw ssvError(400, "SSV_ENCODING_INVALID", "signed reward callback is invalid");
  }
  const suffix = new URLSearchParams(query.slice(signatureIndex + 1));
  const signature = suffix.get("signature"), keyIdText = suffix.get("key_id"), suffixKeys = [...suffix.keys()];
  if (suffixKeys.length !== 2 || suffixKeys[0] !== "signature" || suffixKeys[1] !== "key_id" || !signature || !/^\d{1,20}$/.test(String(keyIdText ?? "")) || !signedContent) {
    throw ssvError(400, "SSV_SIGNATURE_INVALID", "signed reward callback is invalid");
  }
  const keyId = Number(keyIdText);
  if (!Number.isSafeInteger(keyId)) throw ssvError(400, "SSV_KEY_INVALID", "reward signing key is invalid");

  const params = new URLSearchParams(query.slice(0, signatureIndex));
  const getSingle = (name) => {
    const values = params.getAll(name);
    if (values.length !== 1) throw ssvError(400, "SSV_PARAMETER_INVALID", "reward callback parameters are invalid");
    return values[0];
  };
  const accountId = getSingle("user_id"), adUnit = getSingle("ad_unit");
  const rewardAmount = getSingle("reward_amount"), rewardItem = getSingle("reward_item");
  const timestampRaw = getSingle("timestamp"), transactionId = getSingle("transaction_id");
  const customDataRaw = getSingle("custom_data");
  if (customDataRaw.length > 1024 || signature.length > 4096) {
    throw ssvError(400, "SSV_PARAMETER_TOO_LARGE", "reward callback parameters are too large");
  }
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(accountId)) {
    throw ssvError(400, "SSV_ACCOUNT_INVALID", "reward account is invalid");
  }
  const configuredRewardItem = String(env?.LUDOPROOF_ADMOB_REWARD_ITEM ?? "gems");
  if (configuredRewardItem !== "gems" || !["2", "5"].includes(rewardAmount) || rewardItem !== "gems") {
    // These values are non-sensitive; omit the account identifier and callback payload.
    console.warn("admob-ssv-rejected", {
      code: "SSV_REWARD_INVALID",
      configuredRewardItem,
      receivedRewardAmount: rewardAmount,
      receivedRewardItem: rewardItem,
    });
    throw ssvError(403, "SSV_REWARD_INVALID", "reward callback does not match the configured reward");
  }
  const timestampNumber = Number(timestampRaw);
  if (!Number.isSafeInteger(timestampNumber) || timestampNumber <= 0) throw ssvError(400, "SSV_TIMESTAMP_INVALID", "reward timestamp is invalid");
  const timestampMs = timestampNumber > nowMs * 100 ? Math.floor(timestampNumber / 1000) : timestampNumber;
  if (timestampMs > nowMs + 5 * 60 * 1000 || timestampMs < nowMs - 7 * 24 * 60 * 60 * 1000) {
    throw ssvError(400, "SSV_TIMESTAMP_OUT_OF_RANGE", "reward callback timestamp is outside the allowed window");
  }

  let customData;
  try { customData = JSON.parse(customDataRaw); }
  catch { throw ssvError(400, "SSV_CUSTOM_DATA_INVALID", "reward callback data is invalid"); }
  if (!customData || !["gems", "cosmetic"].includes(customData.kind) ||
      (customData.kind === "cosmetic" && typeof customData.cosmeticId !== "string") ||
      (customData.kind === "gems" && customData.cosmeticId != null)) {
    throw ssvError(400, "SSV_CUSTOM_DATA_INVALID", "reward callback data is invalid");
  }

  // Prefer the production key only when its signature actually verifies.
  // A matching key_id can also occur in Google's test key set, so ID lookup alone
  // must not classify the callback or prevent trying the test signature.
  let isTest = false;
  let keyInfo = null;
  let sawMatchingKeyId = false;
  for (const refresh of [false, true]) {
    const productionKeys = await getKeys(fetchImpl, nowMs, refresh, KEY_URL);
    const productionKey = productionKeys.find((key) => Number(key.keyId) === keyId);
    sawMatchingKeyId = sawMatchingKeyId || Boolean(productionKey);

    if (productionKey && typeof productionKey.base64 === "string" &&
        await verifySignature(productionKey, signature, signedContent)) {
      keyInfo = productionKey;
      isTest = false;
      break;
    }

    const testKeys = await getKeys(fetchImpl, nowMs, refresh, TEST_KEY_URL);
    const testKey = testKeys.find((key) => Number(key.keyId) === keyId);
    sawMatchingKeyId = sawMatchingKeyId || Boolean(testKey);
    if (testKey && typeof testKey.base64 === "string" &&
        await verifySignature(testKey, signature, signedContent)) {
      keyInfo = testKey;
      isTest = true;
      break;
    }
  }
  if (!keyInfo) {
    if (!sawMatchingKeyId) throw ssvError(401, "SSV_KEY_UNKNOWN", "reward signing key was not recognized");
    throw ssvError(401, "SSV_SIGNATURE_INVALID", "reward callback signature is invalid");
  }

  // AdMob Console URL verification may use a production signing key while
  // sending these known placeholder IDs. Signature verification has already
  // succeeded above. Treat this exact fixture as verification-only.
  const isConsoleVerification =
    !isTest &&
    transactionId === "123456789" &&
    adUnit === "1234567890";

  if (isConsoleVerification) {
    isTest = true;
  }

  // Every other production callback must have a real hexadecimal transaction ID.
  if (!isTest && !/^[A-Fa-f0-9]{16,128}$/.test(transactionId)) {
    throw ssvError(400, "SSV_TRANSACTION_INVALID", "reward transaction ID is invalid");
  }

  let gemAmount = LEGACY_REWARDED_AD_GEMS;
  if (!isTest) {
    // Keep the originally published 5-Gem ad unit alive for old installed apps.
    // The updated app uses a different ad unit configured to promise 2 Gems.
    // Signed Google ad_unit and reward_amount must both match; custom_data is
    // never trusted to select a higher payout.
    const legacy = String(env?.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID ?? "").trim();
    const updated = String(env?.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID_V2 ?? "").trim();
    if (updated && legacy === updated) {
      throw ssvError(503, "SSV_AD_UNIT_CONFIGURATION_INVALID", "reward ad units are not configured safely");
    }
    const matches = (unit) => unit && [unit, unit.split("/").at(-1)].includes(adUnit);
    if (matches(legacy)) gemAmount = LEGACY_REWARDED_AD_GEMS;
    else if (matches(updated)) gemAmount = UPDATED_REWARDED_AD_GEMS;
    else {
      throw ssvError(403, "SSV_AD_UNIT_INVALID", "reward callback came from an unexpected ad unit");
    }
    if (Number(rewardAmount) !== gemAmount) {
      throw ssvError(403, "SSV_REWARD_INVALID", "reward amount did not match the signed ad unit");
    }
  }

  return { accountId, transactionId, gemAmount, ...(typeof customData.requestId === "string" && /^[a-f0-9-]{36}$/.test(customData.requestId) ? { requestId: customData.requestId } : {}), kind: customData.kind, cosmeticId: customData.cosmeticId ?? null, timestamp: timestampMs, ...(isTest ? { isTest: true } : {}) };
}

async function getKeys(fetchImpl, nowMs, refresh, keyUrl = KEY_URL) {
  let cache = keyCache.get(fetchImpl);
  if (!cache) { cache = new Map(); keyCache.set(fetchImpl, cache); }
  const cached = cache.get(keyUrl);
  if (!refresh && cached && cached.expiresAt > nowMs) return cached.keys;
  let response;
  try { response = await fetchImpl(keyUrl, { method: "GET", headers: { accept: "application/json" }, signal: AbortSignal.timeout(8000) }); }
  catch { throw ssvError(503, "SSV_KEYS_UNAVAILABLE", "reward verification keys are temporarily unavailable"); }
  if (!response.ok) throw ssvError(503, "SSV_KEYS_UNAVAILABLE", "reward verification keys are temporarily unavailable");
  let data;
  try { data = await response.json(); }
  catch { throw ssvError(503, "SSV_KEYS_INVALID", "reward verification keys are invalid"); }
  if (!Array.isArray(data?.keys) || data.keys.length < 1 || data.keys.length > 32) throw ssvError(503, "SSV_KEYS_INVALID", "reward verification keys are invalid");
  const match = (response.headers.get("cache-control") ?? "").match(/(?:^|,)\s*max-age=(\d+)/i);
  const requested = match ? Number(match[1]) * 1000 : 60 * 60 * 1000;
  cache.set(keyUrl, { keys: data.keys, expiresAt: nowMs + Math.min(MAX_KEY_TTL_MS, Math.max(60_000, requested)) });
  return data.keys;
}

async function verifySignature(keyInfo, signature, signedContent) {
  try {
    const keyBytes = decodeBase64(keyInfo.base64);
    let publicKey, partLength;
    try {
      publicKey = await crypto.subtle.importKey("spki", keyBytes, { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
      partLength = 32;
    } catch {
      publicKey = await crypto.subtle.importKey("spki", keyBytes, { name: "ECDSA", namedCurve: "P-384" }, false, ["verify"]);
      partLength = 48;
    }
    const rawSignature = derEcdsaToRaw(decodeSignature(signature), partLength);
    return await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, publicKey, rawSignature, new TextEncoder().encode(signedContent));
  } catch { return false; }
}

function derEcdsaToRaw(der, partLength) {
  if (!(der instanceof Uint8Array) || der.length < 8 || der[0] !== 0x30 || (der[1] & 0x80) !== 0) throw new TypeError("invalid DER signature");
  let offset = 2;
  if (der[offset++] !== 0x02) throw new TypeError("invalid DER signature");
  const rLength = der[offset++], r = der.slice(offset, offset + rLength);
  offset += rLength;
  if (der[offset++] !== 0x02) throw new TypeError("invalid DER signature");
  const sLength = der[offset++], s = der.slice(offset, offset + sLength);
  offset += sLength;
  if (offset !== der.length || !r.length || !s.length) throw new TypeError("invalid DER signature");
  const trim = (value) => value.length > partLength && value[0] === 0 ? value.slice(1) : value;
  const rr = trim(r), ss = trim(s);
  if (rr.length > partLength || ss.length > partLength) throw new TypeError("invalid ECDSA scalar");
  const result = new Uint8Array(partLength * 2);
  result.set(rr, partLength - rr.length);
  result.set(ss, partLength * 2 - ss.length);
  return result;
}
function decodeSignature(value) {
  const standard = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = standard + "=".repeat((4 - standard.length % 4) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}
function decodeBase64(value) {
  const padded = value + "=".repeat((4 - value.length % 4) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}
function ssvError(status, code, message) {
  const error = new Error(message); error.status = status; error.code = code; return error;
}
