import { httpError, sha256Hex } from "./crypto.js";
import { COSMETIC_PRICING } from "./cosmetic-pricing.js";

const PACKAGE_NAME = "com.rekixo.ludopaws";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const PUBLISHER_URL = "https://androidpublisher.googleapis.com/androidpublisher/v3";
const PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const MAX_TOKEN_LENGTH = 2_048;
const ACCOUNT_ID_REGEX = /^[a-f0-9]{64}$/;
const OWNER_ACCOUNT_ID_REGEX = /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i;

export const GOOGLE_PLAY_PRODUCTS = Object.freeze({
  ...Object.fromEntries(Object.keys(COSMETIC_PRICING).map((id) => [id, Object.freeze({
    gemAmount: 0, removeAds: false, consumable: false, cosmeticId: id,
  })])),
  remove_ads: Object.freeze({
    gemAmount: 5,
    removeAds: true,
    consumable: false,
  }),
  gems_300: Object.freeze({
    gemAmount: 300,
    removeAds: false,
    consumable: true,
  }),
  gems_1500: Object.freeze({
    gemAmount: 1_200,
    removeAds: false,
    consumable: true,
  }),
  gems_3500: Object.freeze({
    gemAmount: 3_000,
    removeAds: false,
    consumable: true,
  }),
  gems_20000: Object.freeze({
    gemAmount: 7_500,
    removeAds: false,
    consumable: true,
  }),
  gems_40000: Object.freeze({
    gemAmount: 16_000,
    removeAds: false,
    consumable: true,
  }),
  gems_200000: Object.freeze({
    gemAmount: 35_000,
    removeAds: false,
    consumable: true,
  }),
});

let cachedAccessToken = null;

export function googlePlayBillingChecks(env) {
  const serviceAccountEmailConfigured =
    typeof env?.GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL === "string" &&
    /^[^@\s]+@[^@\s]+\.iam\.gserviceaccount\.com$/.test(
      env.GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL,
    );
  const privateKey = env?.GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY;
  const normalizedPrivateKey =
    typeof privateKey === "string" ? privateKey.replace(/\\n/g, "\n") : "";
  const privateKeyMatch = normalizedPrivateKey.match(
    /-----BEGIN PRIVATE KEY-----\s*([A-Za-z0-9+/=\s]+?)\s*-----END PRIVATE KEY-----/,
  );
  const privateKeyPemPresent = Boolean(
    privateKeyMatch && privateKeyMatch[1].replace(/\s/g, "").length >= 100,
  );
  const purchaseLedgerBindingConfigured = Boolean(env?.LUDOPROOF_STORE_PURCHASES);
  const featureFlagEnabled = env?.GOOGLE_PLAY_BILLING_ENABLED === "true";
  const credentialsConfigured = serviceAccountEmailConfigured && privateKeyPemPresent;
  return Object.freeze({
    featureFlagEnabled,
    serviceAccountEmailConfigured,
    privateKeyPemPresent,
    purchaseLedgerBindingConfigured,
    credentialsConfigured,
    enabled: featureFlagEnabled && credentialsConfigured && purchaseLedgerBindingConfigured,
  });
}

export function googlePlayBillingConfigured(env) {
  const checks = googlePlayBillingChecks(env);
  return checks.credentialsConfigured;
}

export function googlePlayBillingEnabled(env) {
  return googlePlayBillingChecks(env).enabled;
}

export async function verifyAndRedeemGooglePlayPurchase(
  env,
  { productId, purchaseToken, accountId, ownerAccountId },
  fetchImpl = fetch,
) {
  const product = GOOGLE_PLAY_PRODUCTS[productId];
  if (!product) {
    throw httpError(400, "UNKNOWN_STORE_PRODUCT", "store product is not recognized");
  }
  if (
    typeof purchaseToken !== "string" ||
    purchaseToken.length < 8 ||
    purchaseToken.length > MAX_TOKEN_LENGTH ||
    !/^[a-f0-9]{64}$/.test(String(accountId ?? ""))
  ) {
    throw httpError(400, "INVALID_PURCHASE", "purchase information is invalid");
  }
  if (!googlePlayBillingEnabled(env)) {
    throw httpError(
      503,
      "PLAY_BILLING_NOT_ENABLED",
      "Google Play checkout is not enabled by the server",
    );
  }

  const tokenDigest = await sha256Hex(
    "ludoproof:google-play-purchase:v1:" + purchaseToken,
  );
  const ledger = env.LUDOPROOF_STORE_PURCHASES.get(
    env.LUDOPROOF_STORE_PURCHASES.idFromName(tokenDigest),
  );
  const previous = await ledgerRequest(ledger, "/status", "GET");
  const priorPurchase = previous.purchase;
  if (priorPurchase) {
    assertSamePurchase(priorPurchase, productId, accountId, product);
    if (priorPurchase.state === "REVOKED") throw httpError(409, "PLAY_PURCHASE_REVOKED", "this purchase has been revoked");
    if (OWNER_ACCOUNT_ID_REGEX.test(String(ownerAccountId ?? ""))) {
      await ledgerRequest(ledger, "/bind-owner", "POST", { accountId, ownerAccountId,
        purchaseTokenHash: await sha256Hex("ludoproof:google-play-wallet-token:v1:" + purchaseToken) });
    }
    if (priorPurchase.state === "FULFILLED") {
      return fulfillment(productId, product, true, tokenDigest);
    }
  }

  const purchase = await getPlayPurchase(
    env,
    productId,
    purchaseToken,
    fetchImpl,
  );
  if (
    purchase?.productId !== productId ||
    (
      purchase?.obfuscatedExternalAccountId !== accountId
    ) ||
    purchase?.purchaseState !== 0 ||
    ![0, 1].includes(purchase?.consumptionState) ||
    ![0, 1].includes(purchase?.acknowledgementState) ||
    (
      purchase?.quantity != null &&
      purchase.quantity !== 1
    )
  ) {
    throw httpError(
      400,
      "PLAY_PURCHASE_NOT_VALID",
      "Google Play did not confirm this purchase for this app account",
    );
  }
  if (
    !priorPurchase &&
    product.consumable &&
    purchase.consumptionState === 1
  ) {
    throw httpError(
      409,
      "PLAY_PURCHASE_ALREADY_CONSUMED",
      "this Google Play purchase has already been consumed",
    );
  }

  const reservation = await ledgerRequest(ledger, "/reserve", "POST", {
    productId,
    accountId,
    gemAmount: product.gemAmount,
    removeAds: product.removeAds,
    ...(OWNER_ACCOUNT_ID_REGEX.test(String(ownerAccountId ?? "")) ? { ownerAccountId,
      purchaseTokenHash: await sha256Hex("ludoproof:google-play-wallet-token:v1:" + purchaseToken) } : {}),
  });
  const reservedPurchase = reservation.purchase;
  assertSamePurchase(reservedPurchase, productId, accountId, product);
  if (reservedPurchase.state === "REVOKED") throw httpError(409, "PLAY_PURCHASE_REVOKED", "this purchase has been revoked");
  if (reservedPurchase.state === "FULFILLED") {
    return fulfillment(productId, product, true, tokenDigest);
  }
  if (
    product.consumable &&
    purchase.consumptionState !== 1
  ) {
    await runPublisherAction(
      env,
      productId,
      purchaseToken,
      "consume",
      fetchImpl,
    );
  } else if (
    !product.consumable &&
    purchase.acknowledgementState !== 1
  ) {
    await runPublisherAction(
      env,
      productId,
      purchaseToken,
      "acknowledge",
      fetchImpl,
    );
  }

  await ledgerRequest(ledger, "/complete", "POST", {
    productId,
    accountId,
  });
  return fulfillment(productId, product, reservation.created === false, tokenDigest);
}

async function getPlayPurchase(env, productId, purchaseToken, fetchImpl) {
  const accessToken = await googleAccessToken(env, fetchImpl);
  const applicationPath =
    PUBLISHER_URL +
    "/applications/" +
    encodeURIComponent(PACKAGE_NAME);
  const tokenPath = "/tokens/" + encodeURIComponent(purchaseToken);
  const modernUrl =
    applicationPath + "/purchases/productsv2" + tokenPath;
  let response;
  let legacyResponse = false;
  try {
    response = await fetchImpl(modernUrl, {
      method: "GET",
      headers: { authorization: "Bearer " + accessToken },
      signal: AbortSignal.timeout(10_000),
    });
    // The current One-time products model is verified through purchases.productsv2.
    // Keep a 404-only fallback for legacy in-app products already created under the
    // old catalog model. Never fall back for permission/authentication/API failures.
    if (response.status === 404) {
      const legacyUrl =
        applicationPath +
        "/purchases/products/" +
        encodeURIComponent(productId) +
        tokenPath;
      response = await fetchImpl(legacyUrl, {
        method: "GET",
        headers: { authorization: "Bearer " + accessToken },
        signal: AbortSignal.timeout(10_000),
      });
      legacyResponse = true;
    }
  } catch {
    throw httpError(
      503,
      "PLAY_PUBLISHER_UNAVAILABLE",
      "Google Play purchase verification is temporarily unavailable",
    );
  }
  if (!response.ok) {
    if (response.status === 404) {
      throw httpError(400, "PLAY_PURCHASE_NOT_FOUND", "Google Play purchase was not found");
    }
    throw httpError(
      response.status === 401 || response.status === 403 ? 503 : 502,
      "PLAY_PUBLISHER_REJECTED",
      "Google Play could not verify this purchase",
    );
  }
  let payload;
  try {
    payload = await response.json();
  } catch {
    throw httpError(
      502,
      "PLAY_PUBLISHER_BAD_RESPONSE",
      "Google Play returned an invalid purchase response",
    );
  }
  return legacyResponse ? payload : normalizeProductPurchaseV2(payload);
}

function normalizeProductPurchaseV2(payload) {
  const lineItems = payload?.productLineItem;
  if (!Array.isArray(lineItems) || lineItems.length !== 1) {
    return { productId: null, purchaseState: -1, obfuscatedExternalAccountId: payload?.obfuscatedExternalAccountId };
  }
  const item = lineItems[0];
  const offer = item?.productOfferDetails ?? {};
  const purchaseStates = {
    PURCHASED: 0,
    CANCELLED: 1,
    PENDING: 2,
  };
  const consumptionStates = {
    CONSUMPTION_STATE_YET_TO_BE_CONSUMED: 0,
    CONSUMPTION_STATE_CONSUMED: 1,
  };
  const acknowledgementStates = {
    ACKNOWLEDGEMENT_STATE_PENDING: 0,
    ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED: 1,
  };
  const state = payload?.purchaseStateContext?.purchaseState;
  return {
    productId: item?.productId ?? null,
    purchaseState: Object.prototype.hasOwnProperty.call(purchaseStates, state)
      ? purchaseStates[state]
      : -1,
    quantity: offer.quantity ?? 1,
    consumptionState: Object.prototype.hasOwnProperty.call(consumptionStates, offer.consumptionState)
      ? consumptionStates[offer.consumptionState]
      : -1,
    acknowledgementState: Object.prototype.hasOwnProperty.call(acknowledgementStates, payload?.acknowledgementState)
      ? acknowledgementStates[payload.acknowledgementState]
      : -1,
    obfuscatedExternalAccountId: payload?.obfuscatedExternalAccountId,
  };
}

async function runPublisherAction(
  env,
  productId,
  purchaseToken,
  action,
  fetchImpl,
) {
  const accessToken = await googleAccessToken(env, fetchImpl);
  // Google documents consume/acknowledge under purchases.products. The productsv2
  // resource is used to read purchase state, then the action is performed with the
  // product ID through the documented products action resource.
  const url =
    PUBLISHER_URL +
    "/applications/" +
    encodeURIComponent(PACKAGE_NAME) +
    "/purchases/products/" +
    encodeURIComponent(productId) +
    "/tokens/" +
    encodeURIComponent(purchaseToken) +
    ":" +
    action;
  let response;
  try {
    const headers = { authorization: "Bearer " + accessToken };
    const request = {
      method: "POST",
      headers,
      signal: AbortSignal.timeout(10_000),
    };
    if (action === "acknowledge") {
      headers["content-type"] = "application/json";
      request.body = "{}";
    }
    response = await fetchImpl(url, {
      ...request,
    });
  } catch {
    throw httpError(
      503,
      "PLAY_PUBLISHER_ACTION_UNAVAILABLE",
      "Google Play could not finalize this purchase yet",
    );
  }
  if (!response.ok) {
    throw httpError(
      response.status === 401 || response.status === 403 ? 503 : 502,
      "PLAY_PUBLISHER_ACTION_FAILED",
      "Google Play could not acknowledge or consume this purchase",
    );
  }
}

export async function googleAccessToken(env, fetchImpl) {
  const keyId =
    await sha256Hex(env.GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY);
  if (
    cachedAccessToken &&
    cachedAccessToken.email === env.GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL &&
    cachedAccessToken.keyId === keyId &&
    cachedAccessToken.expiresAt > Date.now() + 60_000
  ) {
    return cachedAccessToken.value;
  }

  let assertion;
  try {
    assertion = await createServiceAccountAssertion(
      env.GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL,
      env.GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY,
    );
  } catch {
    throw httpError(
      503,
      "PLAY_BILLING_CREDENTIALS_INVALID",
      "Google Play service-account credentials are invalid",
    );
  }

  let response;
  try {
    response = await fetchImpl(TOKEN_URL, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion,
      }),
      signal: AbortSignal.timeout(10_000),
    });
  } catch {
    throw httpError(
      503,
      "PLAY_OAUTH_UNAVAILABLE",
      "Google Play authorization is temporarily unavailable",
    );
  }
  if (!response.ok) {
    throw httpError(
      503,
      "PLAY_OAUTH_REJECTED",
      "Google Play rejected the service-account authorization",
    );
  }
  let token;
  try {
    token = await response.json();
  } catch {
    throw httpError(
      502,
      "PLAY_OAUTH_BAD_RESPONSE",
      "Google Play returned an invalid authorization response",
    );
  }
  if (
    typeof token.access_token !== "string" ||
    !Number.isFinite(Number(token.expires_in))
  ) {
    throw httpError(
      502,
      "PLAY_OAUTH_BAD_RESPONSE",
      "Google Play returned an incomplete authorization response",
    );
  }
  cachedAccessToken = {
    email: env.GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL,
    keyId,
    value: token.access_token,
    expiresAt: Date.now() + Number(token.expires_in) * 1000,
  };
  return cachedAccessToken.value;
}

async function createServiceAccountAssertion(email, privateKeyPem) {
  const issuedAt = Math.floor(Date.now() / 1000);
  const header = encodeBase64Url(
    new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })),
  );
  const claims = encodeBase64Url(
    new TextEncoder().encode(
      JSON.stringify({
        iss: email,
        scope: PUBLISHER_SCOPE,
        aud: TOKEN_URL,
        iat: issuedAt,
        exp: issuedAt + 3_600,
      }),
    ),
  );
  const signingInput = header + "." + claims;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    decodePrivateKey(privateKeyPem),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );
  return signingInput + "." + encodeBase64Url(new Uint8Array(signature));
}

function decodePrivateKey(value) {
  const pem = value.replace(/\\n/g, "\n");
  const match = pem.match(
    /-----BEGIN PRIVATE KEY-----([\s\S]+?)-----END PRIVATE KEY-----/,
  );
  if (!match) {
    throw new TypeError("invalid service-account private key");
  }
  const base64 = match[1].replace(/\s/g, "");
  const binary = atob(base64);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function encodeBase64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

async function ledgerRequest(ledger, path, method, body = null) {
  const response = await ledger.fetch(
    new Request("https://store-purchase" + path, {
      method,
      headers:
        body === null
          ? undefined
          : { "content-type": "application/json" },
      body: body === null ? undefined : JSON.stringify(body),
    }),
  );
  let value;
  try {
    value = await response.json();
  } catch {
    throw httpError(
      503,
      "STORE_PURCHASE_LEDGER_BAD_RESPONSE",
      "store purchase ledger returned invalid data",
    );
  }
  if (!response.ok) {
    throw httpError(
      response.status,
      value.error ?? "STORE_PURCHASE_LEDGER_FAILED",
      "store purchase could not be safely redeemed",
    );
  }
  return value;
}

function assertSamePurchase(purchase, productId, accountId, product) {
  if (
    purchase?.productId !== productId ||
    purchase?.accountId !== accountId
  ) {
    throw httpError(
      409,
      "PURCHASE_ALREADY_REDEEMED",
      "purchase has already been redeemed by another app installation",
    );
  }
}

function fulfillment(productId, product, replay, purchaseLedgerId) {
  return { verified: true, productId, gemAmount: product.gemAmount, removeAds: product.removeAds, replay, purchaseLedgerId };
}
