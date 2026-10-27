import test from "node:test";
import assert from "node:assert/strict";
import { assertReferralReleaseVersion } from "./check-referral-release-version.mjs";
test("active referrals cannot sign a client older than their minimum", () => {
  const ready = { checks: { referralChecks: { enabled: true }, referralMinimumVersionCode: 10 } };
  assert.throws(() => assertReferralReleaseVersion(ready, 9), /below/);
  assert.doesNotThrow(() => assertReferralReleaseVersion(ready, 10));
  assert.doesNotThrow(() => assertReferralReleaseVersion(ready, 11));
  delete ready.checks.referralMinimumVersionCode;
  assert.throws(() => assertReferralReleaseVersion(ready, 11), /missing/);
  ready.checks.referralChecks.enabled = false;
  assert.doesNotThrow(() => assertReferralReleaseVersion(ready, 9));
});
