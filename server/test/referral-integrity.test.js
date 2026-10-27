import test, { before } from "node:test";
import assert from "node:assert/strict";
import { verifyReferralIntegrity } from "../src/referral-integrity.js";
import { signingCertificates } from "../src/referral-config.js";

let privateKey;
let sequence = 0;
const requestHash = "cd".repeat(32);
const integrityToken = "local-test-only-".repeat(8);

before(async () => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  privateKey = "-----BEGIN PRIVATE KEY-----\n" + Buffer.from(await crypto.subtle.exportKey("pkcs8", pair.privateKey)).toString("base64") + "\n-----END PRIVATE KEY-----";
});

function environment() {
  return {
    LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL: `test-${++sequence}@fixture.iam.gserviceaccount.com`,
    LUDOPAWS_INTEGRITY_PRIVATE_KEY: privateKey,
    LUDOPAWS_REFERRAL_MIN_VERSION_CODE: "10",
    LUDOPAWS_PLAY_SIGNING_SHA256: "ab".repeat(32),
  };
}

function payload() {
  return { tokenPayloadExternal: {
    requestDetails: { requestPackageName: "com.rekixo.ludopaws", requestHash, timestampMillis: String(Date.now()) },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: "com.rekixo.ludopaws", versionCode: "10", certificateSha256Digest: [Buffer.from("ab".repeat(32), "hex").toString("base64url")] },
    accountDetails: { appLicensingVerdict: "LICENSED" },
    deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
  } };
}

function mockProvider(context, options = {}) {
  const counts = { oauth: 0, decode: 0 };
  context.mock.method(globalThis, "fetch", async (url, request) => {
    assert.ok(request.signal);
    if (url === "https://oauth2.googleapis.com/token") {
      counts.oauth++;
      return options.oauth ? options.oauth(counts.oauth) : Response.json({ access_token: "local-access-token", expires_in: 3600 });
    }
    assert.equal(url, "https://playintegrity.googleapis.com/v1/com.rekixo.ludopaws:decodeIntegrityToken");
    assert.deepEqual(JSON.parse(request.body), { integrityToken });
    counts.decode++;
    return options.decode ? options.decode(counts.decode) : Response.json(payload());
  });
  return counts;
}

const rejectsWith = (status, code) => error => error.status === status && error.code === code;

test("concurrent Integrity checks share one OAuth refresh without sharing verdicts", async context => {
  const env = environment();
  const counts = mockProvider(context);
  await Promise.all(Array.from({ length: 16 }, () => verifyReferralIntegrity(env, integrityToken, requestHash)));
  assert.equal(counts.oauth, 1);
  assert.equal(counts.decode, 16);
});

for (const [label, mutate] of [
  ["request package", value => { value.requestDetails.requestPackageName = "other.app"; }],
  ["request hash", value => { value.requestDetails.requestHash = "00".repeat(32); }],
  ["expired timestamp", value => { value.requestDetails.timestampMillis = String(Date.now() - 301000); }],
  ["future timestamp", value => { value.requestDetails.timestampMillis = String(Date.now() + 31000); }],
  ["unrecognized app", value => { value.appIntegrity.appRecognitionVerdict = "UNRECOGNIZED_VERSION"; }],
  ["app package", value => { value.appIntegrity.packageName = "other.app"; }],
  ["certificate", value => { value.appIntegrity.certificateSha256Digest = []; }],
  ["old version", value => { value.appIntegrity.versionCode = "9"; }],
  ["unsafe numeric version", value => { value.appIntegrity.versionCode = "99999999999999999999"; }],
  ["missing license", value => { value.accountDetails.appLicensingVerdict = "UNLICENSED"; }],
  ["device verdict", value => { value.deviceIntegrity.deviceRecognitionVerdict = ["MEETS_BASIC_INTEGRITY"]; }],
  ["malformed device verdict", value => { value.deviceIntegrity.deviceRecognitionVerdict = "NOT_MEETS_DEVICE_INTEGRITY"; }],
]) {
  test(`Integrity denies an invalid ${label}`, async context => {
    mockProvider(context, { decode: () => { const body = payload(); mutate(body.tokenPayloadExternal); return Response.json(body); } });
    await assert.rejects(verifyReferralIntegrity(environment(), integrityToken, requestHash), rejectsWith(403, "INSTALL_NOT_VERIFIED"));
  });
}

for (const status of [401, 429, 500, 503]) {
  test(`provider HTTP ${status} is retryable without granting verification`, async context => {
    mockProvider(context, { decode: () => new Response(null, { status }) });
    await assert.rejects(verifyReferralIntegrity(environment(), integrityToken, requestHash), rejectsWith(503, "INTEGRITY_UNAVAILABLE"));
  });
}

for (const status of [400, 403]) {
  test(`provider HTTP ${status} rejects the installation`, async context => {
    mockProvider(context, { decode: () => new Response(null, { status }) });
    await assert.rejects(verifyReferralIntegrity(environment(), integrityToken, requestHash), rejectsWith(403, "INSTALL_NOT_VERIFIED"));
  });
}

for (const operation of ["oauth", "decode"]) {
  for (const failure of ["network", "malformed JSON"]) {
    test(`${operation} ${failure} becomes a safe retryable error`, async context => {
      mockProvider(context, { [operation]: () => {
        if (failure === "network") throw new Error("provider-private-diagnostic-must-not-leak");
        return new Response("not-json", { headers: { "content-type": "application/json" } });
      } });
      await assert.rejects(verifyReferralIntegrity(environment(), integrityToken, requestHash), error => {
        assert.equal(error.message.includes("provider-private"), false);
        return rejectsWith(503, "INTEGRITY_UNAVAILABLE")(error);
      });
    });
  }
}

for (const token of [{ access_token: "", expires_in: 3600 }, { access_token: "token" }, { access_token: "token", expires_in: -1 }]) {
  test("malformed OAuth credentials are not cached or sent to decode", async context => {
    const counts = mockProvider(context, { oauth: () => Response.json(token) });
    await assert.rejects(verifyReferralIntegrity(environment(), integrityToken, requestHash), rejectsWith(503, "INTEGRITY_UNAVAILABLE"));
    assert.equal(counts.decode, 0);
  });
}

test("failed shared refresh can retry rather than leaving a rejected promise cached", async context => {
  const env = environment();
  const counts = mockProvider(context, { oauth: attempt => attempt === 1 ? new Response(null, { status: 503 }) : Response.json({ access_token: "local-access-token", expires_in: 3600 }) });
  const results = await Promise.allSettled(Array.from({ length: 8 }, () => verifyReferralIntegrity(env, integrityToken, requestHash)));
  assert.ok(results.every(result => result.status === "rejected" && result.reason.code === "INTEGRITY_UNAVAILABLE"));
  await verifyReferralIntegrity(env, integrityToken, requestHash);
  assert.equal(counts.oauth, 2);
  assert.equal(counts.decode, 1);
});

test("HTTP 401 invalidates the cached OAuth token for the next request", async context => {
  const env = environment();
  const counts = mockProvider(context, { decode: attempt => attempt === 1 ? new Response(null, { status: 401 }) : Response.json(payload()) });
  await assert.rejects(verifyReferralIntegrity(env, integrityToken, requestHash), rejectsWith(503, "INTEGRITY_UNAVAILABLE"));
  await verifyReferralIntegrity(env, integrityToken, requestHash);
  assert.equal(counts.oauth, 2);
});

test("invalid verification configuration fails closed before provider access", async context => {
  const counts = mockProvider(context);
  for (const override of [
    { LUDOPAWS_REFERRAL_MIN_VERSION_CODE: "missing" },
    { LUDOPAWS_REFERRAL_MIN_VERSION_CODE: "0" },
    { LUDOPAWS_REFERRAL_MIN_VERSION_CODE: "1e1" },
    { LUDOPAWS_PLAY_SIGNING_SHA256: "ab".repeat(32) + ",invalid" },
    { LUDOPAWS_INTEGRITY_PRIVATE_KEY: "invalid-key" },
  ]) await assert.rejects(verifyReferralIntegrity({ ...environment(), ...override }, integrityToken, requestHash), rejectsWith(503, "INTEGRITY_UNAVAILABLE"));
  assert.equal(counts.oauth, 0);
  assert.equal(counts.decode, 0);
});

test("missing or oversized Integrity tokens never reach a provider", async context => {
  const counts = mockProvider(context);
  for (const value of [undefined, "", "short", "x".repeat(18001)]) {
    await assert.rejects(verifyReferralIntegrity(environment(), value, requestHash), rejectsWith(400, "INTEGRITY_REQUIRED"));
  }
  assert.equal(counts.oauth, 0);
  assert.equal(counts.decode, 0);
});

test("certificate configuration rejects partial or excessive lists and normalizes valid rotation", () => {
  for (const value of ["", "ab".repeat(32) + ",", "ab".repeat(32) + ",invalid", Array(11).fill("ab".repeat(32)).join(",")]) {
    assert.deepEqual(signingCertificates({ LUDOPAWS_PLAY_SIGNING_SHA256: value }), []);
  }
  assert.deepEqual(signingCertificates({ LUDOPAWS_PLAY_SIGNING_SHA256: Array(32).fill("AB").join(":") + "," + "ab".repeat(32) + "," + "cd".repeat(32) }), ["ab".repeat(32), "cd".repeat(32)]);
});
