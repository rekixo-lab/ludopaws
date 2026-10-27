import test from "node:test";
import assert from "node:assert/strict";
import { applyReferralRuntimeVars, buildReferralRuntimeVars } from "./referral-runtime-config.mjs";
import { buildCloudflareRuntimeVars } from "./cloudflare-runtime-config.mjs";

const active = {
  LUDOPAWS_REFERRALS_ENABLED: "true",
  LUDOPAWS_REFERRAL_POLICY_APPROVED: "true",
  LUDOPAWS_REFERRAL_SDK_REVIEW_APPROVED: "true",
  LUDOPAWS_REFERRAL_RELEASE_VALIDATED: "true",
  LUDOPAWS_GOOGLE_LINKING_ENABLED: "true",
  LUDOPAWS_INTEGRITY_PROJECT_NUMBER: "314526321614",
  LUDOPAWS_PLAY_SIGNING_SHA256: "ab".repeat(32),
  LUDOPAWS_REFERRAL_START_MS: "1793061000000",
  LUDOPAWS_REFERRAL_MIN_VERSION_CODE: "10",
};

test("unset deployment inputs clear stale approval, dates and credential plaintext", () => {
  const vars = applyReferralRuntimeVars({ ...active, LUDOPAWS_INTEGRITY_PRIVATE_KEY: "do-not-persist", LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL: "secret-binding", APP_ENVIRONMENT: "test" }, {});
  assert.equal(vars.LUDOPAWS_REFERRALS_ENABLED, "false");
  assert.equal(vars.LUDOPAWS_REFERRAL_POLICY_APPROVED, "false");
  assert.equal(vars.LUDOPAWS_REFERRAL_SDK_REVIEW_APPROVED, "false");
  assert.equal(vars.LUDOPAWS_REFERRAL_RELEASE_VALIDATED, "false");
  for (const name of ["LUDOPAWS_REFERRAL_START_MS", "LUDOPAWS_INTEGRITY_PRIVATE_KEY", "LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL"]) assert.equal(name in vars, false);
  assert.equal(vars.APP_ENVIRONMENT, "test");
});

test("activation requires each review, Google eligibility and each public setting", () => {
  for (const name of Object.keys(active)) {
    if (name === "LUDOPAWS_REFERRALS_ENABLED") continue;
    const missing = { ...active };
    delete missing[name];
    assert.throws(() => buildReferralRuntimeVars(missing), /Referral activation/);
  }
  assert.equal(buildReferralRuntimeVars(active).LUDOPAWS_REFERRALS_ENABLED, "true");
});

test("malformed and partially invalid configuration fails closed", () => {
  for (const [name, value] of [
    ["LUDOPAWS_REFERRALS_ENABLED", "TRUE"],
    ["LUDOPAWS_INTEGRITY_PROJECT_NUMBER", "not-a-number"],
    ["LUDOPAWS_PLAY_SIGNING_SHA256", "ab".repeat(32) + ",invalid"],
    ["LUDOPAWS_REFERRAL_START_MS", "Infinity"],
    ["LUDOPAWS_REFERRAL_MIN_VERSION_CODE", "0"],
  ]) assert.throws(() => buildReferralRuntimeVars({ ...active, [name]: value }));
});

test("Play certificate fingerprints normalize and deduplicate", () => {
  const certificate = Array(32).fill("AB").join(":");
  const vars = buildReferralRuntimeVars({ ...active, LUDOPAWS_PLAY_SIGNING_SHA256: certificate + "," + active.LUDOPAWS_PLAY_SIGNING_SHA256 });
  assert.equal(vars.LUDOPAWS_PLAY_SIGNING_SHA256, "ab".repeat(32));
});

test("the full deployment builder applies referral gates and never serializes secrets", () => {
  const vars = buildCloudflareRuntimeVars({}, {
    ...active,
    GOOGLE_SIGN_IN_WEB_CLIENT_ID: "1234567890-example.apps.googleusercontent.com",
    LUDOPROOF_ACCOUNT_WEB_ORIGIN: "https://ludopaws.rekixo.com",
    LUDOPAWS_INTEGRITY_PRIVATE_KEY: "do-not-persist",
  });
  assert.equal(vars.LUDOPAWS_INTEGRITY_PROJECT_NUMBER, active.LUDOPAWS_INTEGRITY_PROJECT_NUMBER);
  assert.equal(vars.LUDOPAWS_REFERRALS_ENABLED, "true");
  assert.equal("LUDOPAWS_INTEGRITY_PRIVATE_KEY" in vars, false);
});
