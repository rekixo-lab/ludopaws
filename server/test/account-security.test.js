import test from "node:test";
import assert from "node:assert/strict";
import {
  hashGoogleSubject,
  issueAccessToken,
  verifyAccessToken,
  verifyGoogleIdToken,
} from "../src/account-security.js";

function encode(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}
async function rsaFixture() {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true, ["sign", "verify"],
  );
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-kid", alg: "RS256", use: "sig" });
  return { pair, jwk };
}
async function googleToken(privateKey, claims) {
  const header = encode({ alg: "RS256", typ: "JWT", kid: "test-kid" });
  const payload = encode(claims);
  const input = header + "." + payload;
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", privateKey, new TextEncoder().encode(input));
  return input + "." + Buffer.from(signature).toString("base64url");
}

test("Google identity tokens must have trusted signatures and matching claims", async () => {
  const { pair, jwk } = await rsaFixture();
  const now = Date.now();
  const nonce = "ab".repeat(32);
  const claims = {
    iss: "https://accounts.google.com",
    aud: "ludo-web-client.apps.googleusercontent.com",
    sub: "google-subject-123",
    iat: Math.floor(now / 1000),
    exp: Math.floor(now / 1000) + 3600,
    nonce,
    name: "  Ludo   Player ",
  };
  const fetchImpl = async () => Response.json({ keys: [jwk] }, { headers: { "cache-control": "public, max-age=3600" } });
  const token = await googleToken(pair.privateKey, claims);
  assert.deepEqual(
    await verifyGoogleIdToken({ GOOGLE_SIGN_IN_WEB_CLIENT_ID: claims.aud }, token, nonce, fetchImpl, now),
    { subject: claims.sub, displayName: "Ludo Player" },
  );
  assert.equal(await hashGoogleSubject(claims.sub), await hashGoogleSubject(claims.sub));
});

test("Google Android presenters require an explicit allowlist and still validate audience and nonce", async () => {
  const { pair, jwk } = await rsaFixture();
  const now = Date.now();
  const nonce = "ad".repeat(32);
  const clientId = "123-web.apps.googleusercontent.com";
  const androidId = "123-android.apps.googleusercontent.com";
  const env = { GOOGLE_SIGN_IN_WEB_CLIENT_ID: clientId, GOOGLE_SIGN_IN_ANDROID_CLIENT_IDS: androidId };
  const claims = {
    iss: "https://accounts.google.com", aud: clientId, azp: androidId,
    sub: "android-subject", iat: Math.floor(now / 1000), exp: Math.floor(now / 1000) + 3600, nonce,
  };
  const fetchImpl = async () => Response.json({ keys: [jwk] });
  const token = await googleToken(pair.privateKey, claims);
  assert.equal((await verifyGoogleIdToken(env, token, nonce, fetchImpl, now)).subject, claims.sub);
  await assert.rejects(verifyGoogleIdToken({ GOOGLE_SIGN_IN_WEB_CLIENT_ID: clientId }, token, nonce, fetchImpl, now));
  for (const patch of [
    { azp: "123-untrusted.apps.googleusercontent.com" },
    { aud: androidId },
    { nonce: "wrong-nonce" },
    { exp: Math.floor(now / 1000) - 300 },
  ]) {
    const rejected = await googleToken(pair.privateKey, { ...claims, ...patch });
    await assert.rejects(verifyGoogleIdToken(env, rejected, nonce, fetchImpl, now),
      (error) => error.code === "GOOGLE_ID_TOKEN_INVALID");
  }
});

test("Google identity rejects wrong audience, nonce and expiry", async () => {
  const { pair, jwk } = await rsaFixture();
  const now = Date.now();
  const nonce = "cd".repeat(32);
  const base = {
    iss: "accounts.google.com",
    aud: "wrong-client",
    sub: "google-subject-456",
    iat: Math.floor(now / 1000),
    exp: Math.floor(now / 1000) + 3600,
    nonce,
  };
  const fetchImpl = async () => Response.json({ keys: [jwk] });
  const wrongAudience = await googleToken(pair.privateKey, base);
  await assert.rejects(
    verifyGoogleIdToken({ GOOGLE_SIGN_IN_WEB_CLIENT_ID: "expected-client" }, wrongAudience, nonce, fetchImpl, now),
    (error) => error.code === "GOOGLE_ID_TOKEN_INVALID",
  );
  const wrongNonce = await googleToken(pair.privateKey, { ...base, aud: "expected-client" });
  await assert.rejects(
    verifyGoogleIdToken({ GOOGLE_SIGN_IN_WEB_CLIENT_ID: "expected-client" }, wrongNonce, "ef".repeat(32), fetchImpl, now),
    (error) => error.code === "GOOGLE_ID_TOKEN_INVALID",
  );
  const expired = await googleToken(pair.privateKey, { ...base, aud: "expected-client", exp: Math.floor(now / 1000) - 300 });
  await assert.rejects(
    verifyGoogleIdToken({ GOOGLE_SIGN_IN_WEB_CLIENT_ID: "expected-client" }, expired, nonce, fetchImpl, now),
    (error) => error.code === "GOOGLE_ID_TOKEN_INVALID",
  );
  const invalidTimeOrder = await googleToken(pair.privateKey, {
    ...base,
    aud: "expected-client",
    iat: Math.floor(now / 1000) + 40,
    exp: Math.floor(now / 1000) + 30,
  });
  await assert.rejects(
    verifyGoogleIdToken({ GOOGLE_SIGN_IN_WEB_CLIENT_ID: "expected-client" }, invalidTimeOrder, nonce, fetchImpl, now),
    (error) => error.code === "GOOGLE_ID_TOKEN_INVALID",
  );
});

test("access tokens are signed, session-bound, short-lived and reject tampering", async () => {
  const env = { LUDOPROOF_SESSION_HMAC_KEY: "test-session-hmac-key-that-is-long-enough-123456" };
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const sessionId = "6382a0ee-f216-46f8-b10d-51411e1f38f2";
  const now = Date.now();
  const token = await issueAccessToken(env, accountId, sessionId, now);
  const claims = await verifyAccessToken(env, token, now + 1000);
  assert.equal(claims.accountId, accountId);
  assert.equal(claims.sessionId, sessionId);
  const parts = token.split(".");
  const payload = JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"));
  payload.sub = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  parts[1] = Buffer.from(JSON.stringify(payload)).toString("base64url");
  await assert.rejects(
    verifyAccessToken(env, parts.join("."), now + 1000),
    (error) => error.code === "ACCOUNT_SESSION_INVALID",
  );
  await assert.rejects(
    verifyAccessToken(env, token, now + 16 * 60 * 1000),
    (error) => error.code === "ACCOUNT_SESSION_INVALID",
  );
});
