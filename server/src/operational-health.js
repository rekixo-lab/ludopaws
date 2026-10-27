/** Configuration readiness and delivery health are separate signals. Only
 * aggregate counters and fixed error codes may leave this evaluator.
 */
export function evaluateOperationalHealth(body, now = Date.now()) {
  const failures = [];
  const checks = body?.checks ?? {};
  if (body?.ready !== true || body?.ok !== true) failures.push("API_NOT_READY");
  const referral = checks.referralDelivery;
  if (checks.referralChecks?.enabled === true || checks.referralChecks?.bindings === true || referral?.ok === true) {
    if (referral?.ok !== true) failures.push("REFERRAL_HEALTH_UNAVAILABLE");
    else {
      if (!Number.isSafeInteger(referral.pending) || referral.pending < 0 ||
          !Number.isSafeInteger(referral.failedDeliveries) || referral.failedDeliveries < 0) failures.push("REFERRAL_METRICS_INVALID");
      if (referral.pending > 0 && (!Number.isSafeInteger(referral.oldestPendingAt) ||
          referral.oldestPendingAt <= 0 || referral.oldestPendingAt > now + 30_000 ||
          now - referral.oldestPendingAt > 15 * 60_000)) failures.push("REFERRAL_QUEUE_STALE");
      if (referral.failedDeliveries > 0) failures.push("REFERRAL_DELIVERY_FAILING");
      if (checks.referralChecks?.enabled === true && referral.configurationMatches !== true) failures.push("REFERRAL_CONFIGURATION_CHANGED");
      const integrity = referral.integrity;
      if (integrity) {
        if (![integrity.verified, integrity.rejected, integrity.providerFailures, integrity.quotaFailures]
          .every(value => Number.isSafeInteger(value) && value >= 0)) failures.push("INTEGRITY_METRICS_INVALID");
        if (integrity.providerFailures >= 3) failures.push("INTEGRITY_PROVIDER_FAILURES");
        if (integrity.quotaFailures > 0) failures.push("INTEGRITY_QUOTA_FAILURES");
      }
    }
  }
  const billing = checks.billingReconciliationStatus;
  if (checks.playBillingEnabled === true) {
    if (!Number.isSafeInteger(billing?.lastCompletedAt) || billing.lastCompletedAt <= 0 ||
        billing.lastCompletedAt > now + 30_000 || now - billing.lastCompletedAt > 24 * 60 * 60_000)
      failures.push("BILLING_RECONCILIATION_STALE");
    if (Number.isSafeInteger(billing?.lastFailureAt) && billing.lastFailureAt >= (billing.lastCompletedAt ?? 0))
      failures.push("BILLING_RECONCILIATION_FAILING");
  }
  return { ok: failures.length === 0, failures, checkedAt: now,
    deviceAcceptanceVerified: false };
}
