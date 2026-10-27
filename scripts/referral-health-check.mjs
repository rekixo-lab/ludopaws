import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

const requiredChecks = ["policyReviewed", "sdkReviewed", "releaseValidated", "googleLinking", "bindings", "startTime", "cloudProject", "credentials", "certificates", "minimumVersion"];

export function evaluateReferralHealth(body, { now = Date.now(), maxPendingAgeMs = 15 * 60_000 } = {}) {
  if (!Number.isSafeInteger(now) || now <= 0 || !Number.isSafeInteger(maxPendingAgeMs) || maxPendingAgeMs <= 0) throw new TypeError("Invalid health-check limits");
  const failures = [];
  const checks = body?.checks?.referralChecks;
  const enabled = checks?.enabled === true;
  if (body?.ok !== true || body?.ready !== true) failures.push("API_NOT_READY");
  if (typeof checks?.enabled !== "boolean") failures.push("REFERRAL_CONFIGURATION_MISSING");
  const missing = requiredChecks.filter(name => checks?.[name] !== true);
  if (enabled && missing.length) failures.push("ACTIVE_CONFIGURATION_INCOMPLETE");
  const delivery = body?.checks?.referralDelivery;
  let pending = null;
  let oldestPendingAgeMs = null;
  let failedDeliveries = null;
  if (delivery?.ok === true) {
    pending = delivery.pending;
    failedDeliveries = delivery.failedDeliveries;
    if (!Number.isSafeInteger(pending) || pending < 0 || !Number.isSafeInteger(failedDeliveries) || failedDeliveries < 0 || typeof delivery.configurationMatches !== "boolean") failures.push("INVALID_DELIVERY_METRICS");
    if (enabled && delivery.configurationMatches !== true) failures.push("CAMPAIGN_DATES_CHANGED");
    if (pending > 0) {
      if (!Number.isSafeInteger(delivery.oldestPendingAt) || delivery.oldestPendingAt <= 0 || delivery.oldestPendingAt > now + 30_000) failures.push("INVALID_QUEUE_TIMESTAMP");
      else {
        oldestPendingAgeMs = Math.max(0, now - delivery.oldestPendingAt);
        if (oldestPendingAgeMs > maxPendingAgeMs) failures.push("DELIVERY_QUEUE_STALE");
      }
    } else if (pending === 0 && delivery.oldestPendingAt !== null) failures.push("INVALID_QUEUE_TIMESTAMP");
    if (failedDeliveries > 0) failures.push("DELIVERY_RETRIES_FAILING");
  } else if (enabled || checks?.bindings === true) failures.push("DELIVERY_HEALTH_UNAVAILABLE");
  return {
    status: failures.length ? "blocked" : enabled ? "healthy" : "inactive",
    enabled,
    missingActivationChecks: missing,
    pending: Number.isSafeInteger(pending) && pending >= 0 ? pending : null,
    oldestPendingAgeMs,
    failedDeliveries: Number.isSafeInteger(failedDeliveries) && failedDeliveries >= 0 ? failedDeliveries : null,
    failures,
    deviceAcceptanceVerified: false,
  };
}

async function main() {
  const argumentsList = process.argv.slice(2);
  if (argumentsList.some(value => value !== "--require-active")) throw new Error("Usage: node scripts/referral-health-check.mjs [--require-active]");
  const response = await fetch("https://api.ludopaws.rekixo.com/ready", { signal: AbortSignal.timeout(20_000), cache: "no-store" });
  const report = evaluateReferralHealth(await response.json());
  console.log(JSON.stringify(report, null, 2));
  if (!response.ok || report.status === "blocked" || (argumentsList.includes("--require-active") && report.status !== "healthy")) process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(() => { console.error("Referral operational check failed. No credentials or provider response bodies are printed."); process.exitCode = 1; });
}
