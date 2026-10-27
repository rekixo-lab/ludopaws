import test from "node:test";
import assert from "node:assert/strict";
import { StorePurchaseLedger } from "../src/store-purchase-ledger.js";
import {
  GOOGLE_PLAY_PRODUCTS,
  verifyAndRedeemGooglePlayPurchase,
} from "../src/google-play-billing.js";
import worker from "../src/index.js";
import { sha256Hex } from "../src/crypto.js";

class FakeStorage {
  constructor() {
    this.values = new Map();
  }

  async get(key) {
    const value = this.values.get(key);
    return value === undefined ? undefined : structuredClone(value);
  }

  async put(key, value) {
    this.values.set(key, structuredClone(value));
  }

  async delete(key) {
    return this.values.delete(key);
  }
}

function makeLedgerNamespace() {
  const objects = new Map();
  return {
    idFromName(name) {
      return name;
    },
    get(id) {
      if (!objects.has(id)) {
        objects.set(id, new StorePurchaseLedger({
          storage: new FakeStorage(),
        }));
      }
      return objects.get(id);
    },
  };
}

async function serviceAccountKey() {
  const keyPair = await crypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"],
  );
  const encoded =
    Buffer.from(
      await crypto.subtle.exportKey("pkcs8", keyPair.privateKey),
    ).toString("base64");
  return [
    "-----BEGIN PRIVATE KEY-----",
    ...encoded.match(/.{1,64}/g),
    "-----END PRIVATE KEY-----",
  ].join("\n");
}

function mockPlayApi({
  productId,
  accountId,
  purchaseState = 0,
  consumptionState = 0,
  acknowledgementState = 0,
  legacyOnly = false,
} = {}) {
  const calls = [];
  const fetchImpl = async (input, init = {}) => {
    const url = String(input);
    calls.push({ url, init });
    if (url === "https://oauth2.googleapis.com/token") {
      const form = new URLSearchParams(init.body);
      const assertion = form.get("assertion");
      assert.equal(form.get("grant_type"), "urn:ietf:params:oauth:grant-type:jwt-bearer");
      assert.equal(assertion.split(".").length, 3);
      const claims = JSON.parse(
        Buffer.from(assertion.split(".")[1], "base64url").toString("utf8"),
      );
      assert.equal(claims.scope, "https://www.googleapis.com/auth/androidpublisher");
      return Response.json({ access_token: "test-access-token", expires_in: 3600 });
    }
    if (init.method === "GET" && url.includes("/purchases/productsv2/tokens/")) {
      if (legacyOnly) return new Response(null, { status: 404 });
      const states = {
        0: "PURCHASED",
        1: "CANCELLED",
        2: "PENDING",
      };
      return Response.json({
        productLineItem: [{
          productId,
          productOfferDetails: {
            quantity: 1,
            consumptionState: consumptionState === 1
              ? "CONSUMPTION_STATE_CONSUMED"
              : "CONSUMPTION_STATE_YET_TO_BE_CONSUMED",
          },
        }],
        purchaseStateContext: { purchaseState: states[purchaseState] ?? "PURCHASE_STATE_UNSPECIFIED" },
        acknowledgementState: acknowledgementState === 1
          ? "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
          : "ACKNOWLEDGEMENT_STATE_PENDING",
        obfuscatedExternalAccountId: accountId,
      });
    }
    if (init.method === "GET" && url.includes("/purchases/products/")) {
      return Response.json({
        productId,
        purchaseState,
        consumptionState,
        acknowledgementState,
        obfuscatedExternalAccountId: accountId,
      });
    }
    if (url.endsWith(":consume") || url.endsWith(":acknowledge")) {
      return new Response(null, { status: 204 });
    }
    throw new Error("Unexpected Google API request: " + url);
  };
  return { calls, fetchImpl };
}

async function makeEnv() {
  return {
    GOOGLE_PLAY_BILLING_ENABLED: "true",
    GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL:
      "ludo-publisher@ludo-project.iam.gserviceaccount.com",
    GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY:
      await serviceAccountKey(),
    LUDOPROOF_STORE_PURCHASES: makeLedgerNamespace(),
  };
}

test("purchase ledger deletion is owner-bound and idempotent", async () => {
  const storage = new FakeStorage();
  const ledger = new StorePurchaseLedger({ storage });
  const owner = "a".repeat(64);
  const other = "b".repeat(64);
  const reserve = await ledger.fetch(new Request("https://ledger/reserve", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ accountId: owner, productId: "gems_300", gemAmount: 300, removeAds: false }),
  }));
  assert.equal(reserve.status, 200);

  const denied = await ledger.fetch(new Request("https://ledger/delete", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ accountId: other }),
  }));
  assert.equal(denied.status, 409);

  const deleted = await ledger.fetch(new Request("https://ledger/delete", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ accountId: owner }),
  }));
  assert.equal(deleted.status, 200);
  assert.equal((await deleted.json()).deleted, true);

  const status = await ledger.fetch(new Request("https://ledger/status"));
  assert.equal((await status.json()).purchase, null);
  const replay = await ledger.fetch(new Request("https://ledger/delete", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ accountId: owner }),
  }));
  assert.equal(replay.status, 200);
});

test("server product grants match the Android product definitions", () => {
  assert.deepEqual(
    Object.fromEntries(
      Object.entries(GOOGLE_PLAY_PRODUCTS).map(([productId, product]) => [
        productId,
        [product.gemAmount, product.removeAds],
      ]),
    ),
    {
      character_cock: [0, false],
      character_pig: [0, false],
      character_elephant: [0, false],
      character_horse: [0, false],
      character_rabbit: [0, false],
      board_astral: [0, false],
      board_cloud_lagoon: [0, false],
      board_autumn_forest: [0, false],
      board_blossom_garden: [0, false],
      board_winter_haven: [0, false],
      board_monsoon_mire: [0, false],
      remove_ads: [5, true],
      gems_300: [300, false],
      gems_1500: [1_200, false],
      gems_3500: [3_000, false],
      gems_20000: [7_500, false],
      gems_40000: [16_000, false],
      gems_200000: [35_000, false],
    },
  );
});

test("cosmetic Play products are non-consumable entitlements and Pearl Paw has no cash SKU", () => {
  const cosmeticIds = ["character_cock", "character_pig", "character_elephant", "character_horse", "character_rabbit", "board_astral", "board_cloud_lagoon", "board_autumn_forest", "board_blossom_garden", "board_winter_haven", "board_monsoon_mire"];
  for (const cosmeticId of cosmeticIds) {
    assert.equal(GOOGLE_PLAY_PRODUCTS[cosmeticId].cosmeticId, cosmeticId);
    assert.equal(GOOGLE_PLAY_PRODUCTS[cosmeticId].consumable, false);
  }
  assert.equal(GOOGLE_PLAY_PRODUCTS.dice_pearl_paw, undefined);
});

test("verified purchases are consumed, redeemed once, and recover idempotently", async () => {
  const env = await makeEnv();
  const accountId = "a".repeat(64);
  const productId = "gems_300";
  const purchaseToken = "google-play-test-purchase-token";
  const { calls, fetchImpl } = mockPlayApi({ productId, accountId });

  const result = await verifyAndRedeemGooglePlayPurchase(
    env,
    { productId, purchaseToken, accountId },
    fetchImpl,
  );
  assert.deepEqual(result, {
    verified: true,
    productId,
    gemAmount: 300,
    removeAds: false,
    replay: false,
    purchaseLedgerId: await sha256Hex("ludoproof:google-play-purchase:v1:" + purchaseToken),
  });
  assert.equal(calls.filter(({ url }) => url.endsWith(":consume")).length, 1);
  assert.ok(
    calls.some(({ url, init }) =>
      init.method === "GET" &&
      url.includes("/applications/com.rekixo.ludopaws/"),
    ),
    "Google Play verification must target the final Rekixo Android application ID",
  );

  const recovered = await verifyAndRedeemGooglePlayPurchase(
    env,
    { productId, purchaseToken, accountId },
    fetchImpl,
  );
  assert.equal(recovered.replay, true);
  assert.equal(recovered.gemAmount, 300);
  assert.equal(
    calls.filter(
      ({ url, init }) =>
        url.includes("/purchases/productsv2/tokens/") && init.method === "GET",
    ).length,
    1,
  );

  await assert.rejects(
    verifyAndRedeemGooglePlayPurchase(
      env,
      { productId, purchaseToken, accountId: "b".repeat(64) },
      fetchImpl,
    ),
    (error) => error.status === 409 && error.code === "PURCHASE_ALREADY_REDEEMED",
  );
});


test("current one-time product status is checked with productsv2 and finalized with products.consume", async () => {
  const env = await makeEnv();
  const accountId = "8".repeat(64);
  const { calls, fetchImpl } = mockPlayApi({ productId: "gems_300", accountId });
  await verifyAndRedeemGooglePlayPurchase(env, {
    productId: "gems_300", purchaseToken: "modern-action-token", accountId,
  }, fetchImpl);
  assert.equal(calls.filter(({ url }) => url.includes("/purchases/productsv2/tokens/") && calls.find(c => c.url === url)?.init?.method === "GET").length, 1);
  assert.equal(calls.filter(({ url, init }) => url.includes("/purchases/products/gems_300/tokens/") && url.endsWith(":consume") && init.method === "POST").length, 1);
});
test("legacy in-app products fall back only when productsv2 returns 404", async () => {
  const env = await makeEnv();
  const accountId = "e".repeat(64);
  const { calls, fetchImpl } = mockPlayApi({
    productId: "gems_300",
    accountId,
    legacyOnly: true,
  });

  const result = await verifyAndRedeemGooglePlayPurchase(
    env,
    { productId: "gems_300", purchaseToken: "legacy-purchase-token", accountId },
    fetchImpl,
  );
  assert.equal(result.verified, true);
  assert.equal(result.gemAmount, 300);
  assert.equal(
    calls.filter(({ url, init }) => url.includes("/purchases/productsv2/tokens/") && init.method === "GET").length,
    1,
  );
  assert.equal(
    calls.filter(({ url, init }) => url.includes("/purchases/products/gems_300/tokens/") && init.method === "GET").length,
    1,
  );
});

test("invalid and pending Play purchases are never reserved or acknowledged", async () => {
  const env = await makeEnv();
  const accountId = "c".repeat(64);
  const pending = mockPlayApi({
    productId: "gems_300",
    accountId,
    purchaseState: 2,
  });

  await assert.rejects(
    verifyAndRedeemGooglePlayPurchase(
      env,
      {
        productId: "gems_300",
        purchaseToken: "pending-purchase-token",
        accountId,
      },
      pending.fetchImpl,
    ),
    (error) => error.status === 400 && error.code === "PLAY_PURCHASE_NOT_VALID",
  );
  assert.equal(
    pending.calls.some(({ url }) => url.endsWith(":consume") || url.endsWith(":acknowledge")),
    false,
  );
  assert.equal(
    pending.calls.some(({ url }) => url.includes("/purchases/productsv2/tokens/")),
    true,
  );

  await assert.rejects(
    verifyAndRedeemGooglePlayPurchase(
      env,
      {
        productId: "unknown-product",
        purchaseToken: "pending-purchase-token",
        accountId,
      },
      pending.fetchImpl,
    ),
    (error) => error.status === 400 && error.code === "UNKNOWN_STORE_PRODUCT",
  );

  const consumed = mockPlayApi({
    productId: "gems_300",
    accountId,
    consumptionState: 1,
  });
  await assert.rejects(
    verifyAndRedeemGooglePlayPurchase(
      env,
      {
        productId: "gems_300",
        purchaseToken: "previously-consumed-token",
        accountId,
      },
      consumed.fetchImpl,
    ),
    (error) =>
      error.status === 409 &&
      error.code === "PLAY_PURCHASE_ALREADY_CONSUMED",
  );
});

test("non-consumable purchase is acknowledged after Play verification", async () => {
  const env = await makeEnv();
  const accountId = "d".repeat(64);
  const { calls, fetchImpl } = mockPlayApi({
    productId: "remove_ads",
    accountId,
  });
  const result = await verifyAndRedeemGooglePlayPurchase(
    env,
    {
      productId: "remove_ads",
      purchaseToken: "remove-ads-test-token",
      accountId,
    },
    fetchImpl,
  );
  assert.equal(result.removeAds, true);
  assert.equal(result.gemAmount, 5);
  assert.equal(calls.filter(({ url }) => url.endsWith(":acknowledge")).length, 1);
  assert.equal(calls.filter(({ url }) => url.endsWith(":consume")).length, 0);

  await assert.rejects(
    verifyAndRedeemGooglePlayPurchase(
      env,
      {
        productId: "remove_ads",
        purchaseToken: "remove-ads-test-token",
        accountId: "9".repeat(64),
      },
      fetchImpl,
    ),
    (error) => error.status === 409 && error.code === "PURCHASE_ALREADY_REDEEMED",
  );
  assert.equal(
    calls.filter(({ url, init }) => url.includes("/purchases/productsv2/tokens/") && init.method === "GET").length,
    1,
  );
});

test("store purchase ledger rejects token reuse by another installation", async () => {
  const ledger = new StorePurchaseLedger({ storage: new FakeStorage() });
  const reserve = (accountId) =>
    ledger.fetch(
      new Request("https://store-purchase/reserve", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          productId: "gems_300",
          accountId,
          gemAmount: 300,
          removeAds: false,
        }),
      }),
    );
  assert.equal((await reserve("e".repeat(64))).status, 200);
  assert.equal((await reserve("f".repeat(64))).status, 409);
});


test("store status and verification routes stay closed until server billing is enabled", async () => {
  const env = {
    ...await makeEnv(),
    GOOGLE_PLAY_BILLING_ENABLED: "false",
    LUDOPROOF_API_GATE: {
      idFromName: (name) => name,
      get: () => ({
        fetch: async () => Response.json({ allowed: true }),
      }),
    },
  };
  const status = await worker.fetch(
    new Request("https://api.example.test/api/store/status"),
    env,
  );
  assert.deepEqual(await status.json(), {
    ok: true,
    billingAvailable: false,
  });

  const verification = await worker.fetch(
    new Request("https://api.example.test/api/store/purchases/verify", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        productId: "gems_300",
        purchaseToken: "unconfigured-purchase-token",
        accountId: "7".repeat(64),
      }),
    }),
    env,
  );
  assert.equal(verification.status, 503);
  assert.equal(
    (await verification.json()).error,
    "PLAY_BILLING_NOT_ENABLED",
  );

  env.GOOGLE_PLAY_BILLING_ENABLED = "true";
  const enabledStatus = await worker.fetch(
    new Request("https://api.example.test/api/store/status"),
    env,
  );
  assert.deepEqual(await enabledStatus.json(), {
    ok: true,
    billingAvailable: true,
  });
});

