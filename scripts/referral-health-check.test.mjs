import test from "node:test";
import assert from "node:assert/strict";
import { evaluateReferralHealth } from "./referral-health-check.mjs";

const now = 1793061000000;
function readiness(enabled = true) {
  return { ok: true, ready: true, checks: {
    referralChecks: { enabled, policyReviewed: true, sdkReviewed: true, releaseValidated: true, googleLinking: true, bindings: true, startTime: true, cloudProject: true, credentials: true, certificates: true, minimumVersion: true },
    referralDelivery: { ok: true, configurationMatches: true, pending: 0, oldestPendingAt: null, failedDeliveries: 0 },
  } };
}

test("healthy operations are explicitly not real-device acceptance", () => {
  const report = evaluateReferralHealth(readiness(), { now });
  assert.equal(report.status, "healthy");
  assert.equal(report.deviceAcceptanceVerified, false);
});

test("paused campaigns remain inactive without claiming activation", () => {
  const body = readiness(false);
  body.checks.referralChecks.releaseValidated = false;
  assert.equal(evaluateReferralHealth(body, { now }).status, "inactive");
});

test("stale or failing delivery remains visible even while the campaign is paused", () => {
  const body = readiness(false);
  Object.assign(body.checks.referralDelivery, { pending: 2, oldestPendingAt: now - 16 * 60_000, failedDeliveries: 1 });
  const report = evaluateReferralHealth(body, { now });
  assert.equal(report.status, "blocked");
  assert.deepEqual(report.failures, ["DELIVERY_QUEUE_STALE", "DELIVERY_RETRIES_FAILING"]);
});

test("each active approval and service configuration check is mandatory", () => {
  for (const name of Object.keys(readiness().checks.referralChecks).filter(name => name !== "enabled")) {
    const body = readiness();
    body.checks.referralChecks[name] = false;
    assert.ok(evaluateReferralHealth(body, { now }).failures.includes("ACTIVE_CONFIGURATION_INCOMPLETE"));
  }
});

test("missing readiness or delivery cannot look healthy", () => {
  for (const body of [null, {}, { ok: true, ready: true }, { ...readiness(), ready: false }]) {
    assert.equal(evaluateReferralHealth(body, { now }).status, "blocked");
  }
  const body = readiness();
  body.checks.referralDelivery = null;
  assert.ok(evaluateReferralHealth(body, { now }).failures.includes("DELIVERY_HEALTH_UNAVAILABLE"));
});

test("invalid counters, timestamps and changed campaign dates fail operational checks", () => {
  for (const change of [
    { pending: -1 }, { pending: "1" }, { failedDeliveries: -1 },
    { pending: 1, oldestPendingAt: null }, { pending: 1, oldestPendingAt: now + 31_000 },
    { pending: 0, oldestPendingAt: now }, { configurationMatches: false },
  ]) {
    const body = readiness();
    Object.assign(body.checks.referralDelivery, change);
    assert.equal(evaluateReferralHealth(body, { now }).status, "blocked");
  }
});

test("pending rewards within the configured limit remain healthy", () => {
  const body = readiness();
  Object.assign(body.checks.referralDelivery, { pending: 1, oldestPendingAt: now - 60_000 });
  assert.equal(evaluateReferralHealth(body, { now }).oldestPendingAgeMs, 60_000);
  assert.equal(evaluateReferralHealth(body, { now }).status, "healthy");
});

test("health report never copies credential or account payloads", () => {
  const body = readiness();
  body.privateKey = "PRIVATE-DO-NOT-PRINT";
  body.checks.referralDelivery.accountId = "PRIVATE-ACCOUNT";
  const serialized = JSON.stringify(evaluateReferralHealth(body, { now }));
  assert.doesNotMatch(serialized, /PRIVATE/);
  assert.throws(() => evaluateReferralHealth(body, { now: NaN }));
});
