import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

export function assertReferralReleaseVersion(ready, versionCode) {
  if (!/^\d{1,12}$/.test(String(versionCode)) || Number(versionCode) <= 0) throw Error("Invalid release versionCode");
  if (ready?.checks?.referralChecks?.enabled !== true) return;
  const minimum = ready.checks.referralMinimumVersionCode;
  if (!Number.isSafeInteger(minimum) || minimum <= 0) throw Error("Active referral minimum version is missing; deploy the reviewed backend before signing");
  if (Number(versionCode) < minimum) throw Error("Release versionCode is below the active referral minimum; do not publish an ineligible client");
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  assertReferralReleaseVersion(JSON.parse(readFileSync(0, "utf8")), process.argv[2]);
  console.log("Release version matches referral minimum. Public Play availability still requires separate rollout verification.");
}
