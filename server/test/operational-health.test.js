import test from "node:test";
import assert from "node:assert/strict";
import { evaluateOperationalHealth } from "../src/operational-health.js";

const now = 1793098917090;
const healthy = () => ({ ok: true, ready: true, checks: {
  referralChecks: { enabled: true },
  referralDelivery: { ok: true, configurationMatches: true, pending: 0, oldestPendingAt: null, failedDeliveries: 0,
    integrity: { verified: 5, rejected: 1, providerFailures: 0, quotaFailures: 0 } },
  playBillingEnabled: true, billingReconciliationStatus: { lastCompletedAt: now - 60_000, lastFailureAt: null },
} });
test("operations catch delivery delay, provider quota and stale refunds without leaking payloads", () => {
  assert.equal(evaluateOperationalHealth(healthy(), now).ok, true);
  const data = healthy();
  Object.assign(data.checks.referralDelivery, { pending: 1, oldestPendingAt: now - 16 * 60_000, failedDeliveries: 1 });
  Object.assign(data.checks.referralDelivery.integrity, { providerFailures: 3, quotaFailures: 1 });
  data.checks.billingReconciliationStatus.lastCompletedAt = now - 25 * 60 * 60_000;
  data.privateKey = "secret-never-copy";
  const result = evaluateOperationalHealth(data, now);
  assert.deepEqual(result.failures, ["REFERRAL_QUEUE_STALE", "REFERRAL_DELIVERY_FAILING", "INTEGRITY_PROVIDER_FAILURES", "INTEGRITY_QUOTA_FAILURES", "BILLING_RECONCILIATION_STALE"]);
  assert.equal(JSON.stringify(result).includes("secret-never-copy"), false);
  assert.equal(result.deviceAcceptanceVerified, false);
});
test("invalid metrics fail and paused delivery failures stay visible", () => {
  const data = healthy();
  data.checks.referralChecks.enabled = false;
  data.checks.referralDelivery.failedDeliveries = 1;
  data.checks.referralDelivery.integrity.quotaFailures = -1;
  assert.deepEqual(evaluateOperationalHealth(data, now).failures, ["REFERRAL_DELIVERY_FAILING", "INTEGRITY_METRICS_INVALID"]);
  data.checks.playBillingEnabled = false;
  data.checks.referralDelivery = null;
  assert.equal(evaluateOperationalHealth(data, now).ok, true);
});

test("paused participation cannot hide an unavailable configured reward queue", () => {
  const data = healthy();
  Object.assign(data.checks.referralChecks, { enabled: false, bindings: true });
  data.checks.referralDelivery = { ok: false };
  assert.deepEqual(evaluateOperationalHealth(data, now).failures, ["REFERRAL_HEALTH_UNAVAILABLE"]);
});
