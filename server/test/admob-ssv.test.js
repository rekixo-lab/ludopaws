import test from "node:test";
import assert from "node:assert/strict";
import { verifyAdMobSsvRequest } from "../src/admob-ssv.js";

function base64(value) { return Buffer.from(value).toString("base64"); }
function rawToDer(raw) {
  const part = (bytes) => {
    let value = Buffer.from(bytes);
    while (value.length > 1 && value[0] === 0) value = value.subarray(1);
    if (value[0] & 0x80) value = Buffer.concat([Buffer.from([0]), value]);
    return Buffer.concat([Buffer.from([0x02, value.length]), value]);
  };
  const body = Buffer.concat([part(raw.subarray(0, raw.length / 2)), part(raw.subarray(raw.length / 2))]);
  return Buffer.concat([Buffer.from([0x30, body.length]), body]);
}
async function signedRequest({ privateKey, keyId, accountId, adUnit, customData, timestamp, transactionId }) {
  const params = new URLSearchParams();
  params.append("ad_network", "5450213213286189855");
  params.append("ad_unit", adUnit);
  params.append("custom_data", customData);
  params.append("reward_amount", "5");
  params.append("reward_item", "gems");
  params.append("timestamp", String(timestamp));
  params.append("transaction_id", transactionId);
  params.append("user_id", accountId);
  const content = params.toString();
  // Google’s reference verifier signs the decoded query component (URI.getQuery),
  // not the percent-encoded URL representation. JSON custom_data exercises this path.
  const signedContent = decodeURIComponent(content);
  const raw = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, privateKey, new TextEncoder().encode(signedContent)));
  return new Request("https://api.example.test/api/admob/ssv?" + content +
    "&signature=" + rawToDer(raw).toString("base64url") + "&key_id=" + keyId);
}

test("AdMob SSV accepts the numeric ad_unit identifier corresponding to the configured SDK ad unit", async () => {
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const spki = await crypto.subtle.exportKey("spki", pair.publicKey);
  const keys = { keys: [{ keyId: 129, base64: base64(spki) }] };
  const fetchImpl = async () => Response.json(keys, { headers: { "cache-control": "public, max-age=3600" } });
  const now = Date.now();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const tx = "a1b2c3d4e5f60718293a4b5c6d7e8f90";
  const request = await signedRequest({
    privateKey: pair.privateKey, keyId: 129, accountId, adUnit: "456",
    customData: JSON.stringify({ kind: "gems" }), timestamp: now, transactionId: tx,
  });
  const result = await verifyAdMobSsvRequest(
    request, { LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-123/456" }, fetchImpl, now,
  );
  assert.deepEqual(result, { accountId, transactionId: tx, kind: "gems", cosmeticId: null, timestamp: now });

  await assert.rejects(
    verifyAdMobSsvRequest(new Request(request.url.replace("reward_amount=5", "reward_amount=50")),
      { LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-123/456" }, fetchImpl, now),
  );
  await assert.rejects(
    verifyAdMobSsvRequest(
      request,
      {
        LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-123/456",
        LUDOPROOF_ADMOB_REWARD_ITEM: "coins",
      },
      fetchImpl,
      now,
    ),
    (error) => error.code === "SSV_REWARD_INVALID",
  );
});

test("AdMob SSV refuses callback from a different configured ad unit", async () => {
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const spki = await crypto.subtle.exportKey("spki", pair.publicKey);
  const fetchImpl = async () => Response.json({ keys: [{ keyId: 130, base64: base64(spki) }] });
  const now = Date.now();
  const request = await signedRequest({
    privateKey: pair.privateKey, keyId: 130,
    accountId: "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7",
    adUnit: "456", customData: JSON.stringify({ kind: "gems" }), timestamp: now,
    transactionId: "fedcba98765432100123456789abcdef",
  });
  await assert.rejects(
    verifyAdMobSsvRequest(request, { LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-999/789" }, fetchImpl, now),
    (error) => error.code === "SSV_AD_UNIT_INVALID",
  );
});


test("AdMob test signature wins over a colliding production key ID and never grants a production reward", async () => {
  const productionPair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const testPair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const productionSpki = await crypto.subtle.exportKey("spki", productionPair.publicKey);
  const testSpki = await crypto.subtle.exportKey("spki", testPair.publicKey);
  const fetchImpl = async (url) => {
    const isTest = String(url).includes("verifier-keys-test.json");
    // Intentionally collide key IDs to model production and test key sets where
    // the incoming ID exists in both: only signature verification identifies the signer.
    return Response.json({
      keys: [{ keyId: 3335741209, base64: base64(isTest ? testSpki : productionSpki) }],
    }, { headers: { "cache-control": "public, max-age=3600" } });
  };
  const now = Date.now();
  const request = await signedRequest({
    privateKey: testPair.privateKey, keyId: 3335741209,
    accountId: "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7",
    adUnit: "1234567890", customData: JSON.stringify({ kind: "gems" }), timestamp: now,
    transactionId: "123456789",
  });
  const result = await verifyAdMobSsvRequest(
    request, { LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-123/456" }, fetchImpl, now,
  );
  assert.equal(result.isTest, true);
  assert.equal(result.kind, "gems");
  assert.equal(result.transactionId, "123456789");
  const consoleVerificationRequest = await signedRequest({
    privateKey: productionPair.privateKey, keyId: 3335741209,
    accountId: "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7",
    adUnit: "1234567890", customData: JSON.stringify({ kind: "gems" }), timestamp: now,
    transactionId: "123456789",
  });
  const consoleResult = await verifyAdMobSsvRequest(
    consoleVerificationRequest,
    { LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-123/456" },
    fetchImpl,
    now,
  );
  assert.equal(consoleResult.isTest, true);
  assert.equal(consoleResult.transactionId, "123456789");

  const malformedProductionRequest = await signedRequest({
    privateKey: productionPair.privateKey, keyId: 3335741209,
    accountId: "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7",
    adUnit: "456", customData: JSON.stringify({ kind: "gems" }), timestamp: now,
    transactionId: "not-a-valid-tx-id",
  });
  await assert.rejects(
    verifyAdMobSsvRequest(
      malformedProductionRequest,
      { LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-123/456" },
      fetchImpl,
      now,
    ),
    (error) => error.code === "SSV_TRANSACTION_INVALID",
  );
});

test("signed custom-data request ID is returned for exact reward correlation", async () => {
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const spki = await crypto.subtle.exportKey("spki", pair.publicKey);
  const fetchImpl = async () => Response.json({ keys: [{ keyId: 129, base64: base64(spki) }] });
  const now = Date.now();
  const requestId = "00000000-0000-4000-8000-000000000099";
  const request = await signedRequest({ privateKey: pair.privateKey, keyId: 129,
    accountId: "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7", adUnit: "1234567890",
    customData: JSON.stringify({ kind: "gems", requestId }), timestamp: now,
    transactionId: "a1b2c3d4e5f60718293a4b5c6d7e8f90" });
  const result = await verifyAdMobSsvRequest(request, { LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-1234567890123456/1234567890", LUDOPROOF_ADMOB_REWARD_ITEM: "gems" }, fetchImpl, now);
  assert.equal(result.requestId, requestId);
});
