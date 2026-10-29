import test from "node:test";
import assert from "node:assert/strict";
import { referralHealth } from "../src/referral-config.js";
import worker from "../src/index-account.js";

function environment(fetch) {
  return { LUDOPAWS_REFERRALS_ENABLED: "false", LUDOPAWS_REFERRALS: { idFromName: value => value, get: () => ({ fetch }) } };
}

test("delivery health remains available while new referrals are disabled", async () => {
  const result = await referralHealth(environment(async request => {
    assert.equal(new URL(request.url).pathname, "/health");
    return Response.json({ ok: true, configurationMatches: true, pending: 1, oldestPendingAt: 1793061000000, failedDeliveries: 1 });
  }));
  assert.deepEqual(result, { ok: true, configurationMatches: true, pending: 1, oldestPendingAt: 1793061000000, failedDeliveries: 1 });
});

test("health exports aggregate fields only", async () => {
  const result = await referralHealth(environment(async () => Response.json({ ok: true, configurationMatches: true, pending: 0, oldestPendingAt: null, failedDeliveries: 0, accountId: "private-account", providerToken: "private-token",
    integrity: { verified: 1, rejected: 2, providerFailures: 0, quotaFailures: 0, accountId: "private-account", providerToken: "private-token" } })));
  assert.doesNotMatch(JSON.stringify(result), /private/);
  assert.deepEqual(result.integrity, { verified: 1, rejected: 2, providerFailures: 0, quotaFailures: 0 });
});

for (const failure of ["network", "http", "json", "negative"]) {
  test(`health ${failure} failure is safe and does not throw into ordinary gameplay`, async () => {
    const result = await referralHealth(environment(async () => {
      if (failure === "network") throw new Error("private-provider-diagnostic");
      if (failure === "http") return new Response(null, { status: 503 });
      if (failure === "json") return new Response("not-json");
      return Response.json({ ok: false });
    }));
    assert.deepEqual(result, { ok: false, error: "REFERRAL_HEALTH_UNAVAILABLE" });
  });
}

test("no referral binding yields no fabricated delivery health", async () => {
  assert.equal(await referralHealth({}), null);
});

function readyEnvironment(fetch, enabled) {
  return {
    ...environment(fetch),
    LUDOPROOF_MATCHES: {}, LUDOPROOF_API_GATE: {}, LUDOPROOF_LEADERBOARD: {}, LUDOPROOF_MATCHMAKER: {}, LUDOPROOF_FRIENDS: {},
    LUDOPROOF_TEAM_MATCHMAKER: {}, LUDOPROOF_FRIEND_CONVERSATIONS: {}, LUDOPROOF_FRIEND_RECENT: {},
    LUDOPROOF_ACCOUNT_DIRECTORY: {}, LUDOPROOF_PLAYER_ACCOUNTS: {},
    LUDOPROOF_SESSION_HMAC_KEY: "local-health-fixture-session-key-at-least-32-bytes",
    GOOGLE_SIGN_IN_WEB_CLIENT_ID: "fixture.apps.googleusercontent.com",
    LUDOPROOF_AGE_SAFETY_MODE: "FAMILY_V2", LUDOPAWS_GOOGLE_LINKING_ENABLED: "true",
    LUDOPAWS_REFERRALS_ENABLED: String(enabled), LUDOPAWS_REFERRAL_POLICY_APPROVED: "true",
    LUDOPAWS_REFERRAL_SDK_REVIEW_APPROVED: "true", LUDOPAWS_REFERRAL_RELEASE_VALIDATED: "true",
    LUDOPAWS_REFERRAL_START_MS: String(Date.now() - 60000), LUDOPAWS_REFERRAL_MIN_VERSION_CODE: "10",
    LUDOPAWS_PLAY_SIGNING_SHA256: "ab".repeat(32), LUDOPAWS_INTEGRITY_PROJECT_NUMBER: "314526321614",
    LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL: "local-test@example.invalid", LUDOPAWS_INTEGRITY_PRIVATE_KEY: "no-live-key",
  };
}

for (const enabled of [false, true]) {
  test(`readiness handles unavailable delivery when participation is ${enabled ? "active" : "paused"}`, async () => {
    const response = await worker.fetch(new Request("https://api.ludopaws.rekixo.com/ready"), readyEnvironment(async () => { throw new Error("private-diagnostic"); }, enabled));
    assert.equal(response.status, enabled ? 503 : 200);
    const body = await response.json();
    assert.equal(body.ready, !enabled);
    assert.deepEqual(body.checks.referralDelivery, { ok: false, error: "REFERRAL_HEALTH_UNAVAILABLE" });
    assert.equal(body.productionClaim, false);
  });
}

test("paused readiness still exposes pending deliveries without player identifiers", async () => {
  const response = await worker.fetch(new Request("https://api.ludopaws.rekixo.com/ready"), readyEnvironment(async () => Response.json({ ok: true, configurationMatches: true, pending: 3, oldestPendingAt: 1793061000000, failedDeliveries: 1, accountId: "not-public" }), false));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.checks.referralDelivery.pending, 3);
  assert.equal("accountId" in body.checks.referralDelivery, false);
});
