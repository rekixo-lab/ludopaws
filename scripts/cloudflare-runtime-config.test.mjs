import test from "node:test";
import assert from "node:assert/strict";
import { ACCOUNT_WEB_ORIGIN, buildCloudflareRuntimeVars } from "./cloudflare-runtime-config.mjs";

const valid = {
  GOOGLE_SIGN_IN_WEB_CLIENT_ID: "1234567890-exampleclient.apps.googleusercontent.com",
  LUDOPROOF_ACCOUNT_WEB_ORIGIN: ACCOUNT_WEB_ORIGIN,
};
const adUnit = "ca-app-pub-1234567890123456/1234567890";

test("runtime vars require a valid Google Web OAuth client and the account-site origin", () => {
  const vars = buildCloudflareRuntimeVars({ APP_ENVIRONMENT: "test" }, valid);
  assert.equal(vars.GOOGLE_SIGN_IN_WEB_CLIENT_ID, valid.GOOGLE_SIGN_IN_WEB_CLIENT_ID);
  assert.equal(vars.LUDOPROOF_ACCOUNT_WEB_ORIGIN, ACCOUNT_WEB_ORIGIN);
  assert.equal(vars.APP_ENVIRONMENT, "test");
  assert.equal(vars.LUDOPROOF_AGE_SAFETY_MODE, "FAMILY_V2");
});

test("runtime store flags fail closed when GitHub variables are unset", () => {
  const vars = buildCloudflareRuntimeVars({}, valid);
  assert.equal(vars.GOOGLE_PLAY_BILLING_ENABLED, "false");
  assert.equal(vars.LUDOPROOF_ADMOB_SSV_ENABLED, "false");
  assert.equal(vars.LUDOPROOF_ADMOB_REWARD_ITEM, "gems");
  assert.equal("GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL" in vars, false);
  assert.equal("LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID" in vars, false);
});

test("Android OAuth presenters are explicit, validated and cleared when unset", () => {
  const androidId = "1234567890-android.apps.googleusercontent.com";
  const vars = buildCloudflareRuntimeVars({}, { ...valid, GOOGLE_SIGN_IN_ANDROID_CLIENT_IDS: ` ${androidId},${androidId} ` });
  assert.equal(vars.GOOGLE_SIGN_IN_ANDROID_CLIENT_IDS, androidId);
  assert.equal(buildCloudflareRuntimeVars(vars, valid).GOOGLE_SIGN_IN_ANDROID_CLIENT_IDS, "");
  assert.throws(() => buildCloudflareRuntimeVars({}, { ...valid, GOOGLE_SIGN_IN_ANDROID_CLIENT_IDS: "*" }), /comma-separated OAuth/);
});

test("invalid OAuth client ID is rejected", () => {
  assert.throws(
    () => buildCloudflareRuntimeVars({}, { ...valid, GOOGLE_SIGN_IN_WEB_CLIENT_ID: "not-a-web-client-id" }),
    /valid Google Web OAuth client ID/,
  );
});

test("account-site origin mismatch is rejected", () => {
  assert.throws(
    () => buildCloudflareRuntimeVars({}, { ...valid, LUDOPROOF_ACCOUNT_WEB_ORIGIN: "https://example.com" }),
    /must be exactly/,
  );
});

test("Billing cannot be enabled without the Google Play service-account email", () => {
  assert.throws(
    () => buildCloudflareRuntimeVars({}, { ...valid, GOOGLE_PLAY_BILLING_ENABLED: "true", LUDOPROOF_BILLING_ENABLED: "true" }),
    /cannot be enabled until GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL/,
  );
});

test("Reviewed family V2 enables configured billing", () => {
  const vars = buildCloudflareRuntimeVars({}, {
    ...valid,
    GOOGLE_PLAY_BILLING_ENABLED: "true",
    LUDOPROOF_BILLING_ENABLED: "true",
    GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL: "ludoproof-play@ludoproof-prod.iam.gserviceaccount.com",
  });
  assert.equal(vars.GOOGLE_PLAY_BILLING_ENABLED, "true");
});

test("AdMob SSV cannot be enabled without a real rewarded-unit ID", () => {
  assert.throws(
    () => buildCloudflareRuntimeVars({}, { ...valid, LUDOPROOF_ADMOB_SSV_ENABLED: "true", LUDOPROOF_ADS_ENABLED: "true" }),
    /cannot be enabled until LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID/,
  );
});

test("Reviewed family V2 enables matching ads and validates reward settings", () => {
  const vars = buildCloudflareRuntimeVars({}, {
    ...valid,
    LUDOPROOF_ADMOB_SSV_ENABLED: "true",
    LUDOPROOF_ADS_ENABLED: "true",
    LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: adUnit,
    LUDOPROOF_ADMOB_REWARD_ITEM: "gems",
    ADMOB_APP_ID: "ca-app-pub-1234567890123456~1234567890",
    ADMOB_REWARDED_AD_UNIT_ID: adUnit,
    LUDOPAWS_AD_SAFETY_REVIEW_APPROVED: "true",
  });
  assert.equal(vars.LUDOPROOF_ADMOB_SSV_ENABLED, "true");
  assert.equal(vars.LUDOPAWS_AD_SAFETY_REVIEW_APPROVED, "true");
  assert.throws(
    () => buildCloudflareRuntimeVars({}, { ...valid, LUDOPROOF_ADMOB_REWARD_ITEM: "coins" }),
    /must be "gems"/,
  );
  assert.throws(
    () => buildCloudflareRuntimeVars({}, { ...valid, LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-123/456" }),
    /must be a real AdMob rewarded-unit ID/,
  );
});

test("one 2-Gem unit matches Android and Worker and clears stale v2 bindings", () => {
  const config = { ...valid,
    LUDOPROOF_ADMOB_SSV_ENABLED: "true", LUDOPROOF_ADS_ENABLED: "true",
    LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: adUnit, ADMOB_REWARDED_AD_UNIT_ID: adUnit,
    ADMOB_APP_ID: "ca-app-pub-1234567890123456~1234567890",
    LUDOPAWS_AD_SAFETY_REVIEW_APPROVED: "true",
  };
  const prepared = buildCloudflareRuntimeVars({ LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID_V2: "stale" }, config);
  assert.equal(prepared.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID, adUnit);
  assert.equal("LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID_V2" in prepared, false);
  assert.throws(() => buildCloudflareRuntimeVars({}, {
    ...config, ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-1234567890123456/0000000005",
  }), /must both be valid and match/);
});

test("Worker and Android store flags must match before deployment", () => {
  assert.throws(
    () => buildCloudflareRuntimeVars({}, { ...valid, GOOGLE_PLAY_BILLING_ENABLED: "true" }),
    /must match before deployment/,
  );
  assert.throws(
    () => buildCloudflareRuntimeVars({}, { ...valid, LUDOPROOF_ADMOB_SSV_ENABLED: "true" }),
    /must match before deployment/,
  );
});

test("feature flags reject ambiguous or non-canonical values instead of interpreting them as true", () => {
  assert.throws(
    () => buildCloudflareRuntimeVars({}, { ...valid, GOOGLE_PLAY_BILLING_ENABLED: "yes" }),
    /must be exactly "true" or "false"/,
  );
  assert.throws(
    () => buildCloudflareRuntimeVars({}, { ...valid, GOOGLE_PLAY_BILLING_ENABLED: "TRUE", LUDOPROOF_BILLING_ENABLED: "TRUE" }),
    /must be exactly "true" or "false"/,
  );
});


test("untrusted environment and existing dashboard vars cannot enable unrestricted capabilities", () => {
  const vars = buildCloudflareRuntimeVars(
    { LUDOPROOF_AGE_SAFETY_MODE: "ENABLED" },
    { ...valid, LUDOPROOF_AGE_SAFETY_MODE: "ENABLED" },
  );
  assert.equal(vars.LUDOPROOF_AGE_SAFETY_MODE, "FAMILY_V2");
});

test("ads still require publisher approval and unknown-age-safe Android treatment", () => {
  const configured = { ...valid, LUDOPROOF_ADMOB_SSV_ENABLED: "true", LUDOPROOF_ADS_ENABLED: "true",
    LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: adUnit, ADMOB_REWARDED_AD_UNIT_ID: adUnit,
    ADMOB_APP_ID: "ca-app-pub-1234567890123456~1234567890" };
  assert.throws(() => buildCloudflareRuntimeVars({}, configured), /AD_SAFETY_REVIEW_APPROVED/);
});

// A release must prove the live Worker came from the exact signed source commit.
test("runtime vars bind a deploy to the exact, validated GitHub commit", () => {
  const sha = "8e359b5e3c7dc92a6c8a9df33b67851599d4d31b";
  const vars = buildCloudflareRuntimeVars({}, { ...valid, GITHUB_SHA: sha });
  assert.equal(vars.LUDOPROOF_DEPLOY_COMMIT_SHA, sha);
  assert.equal(
    "LUDOPROOF_DEPLOY_COMMIT_SHA" in buildCloudflareRuntimeVars(vars, valid),
    false,
  );
  assert.throws(() => buildCloudflareRuntimeVars({}, { ...valid, GITHUB_SHA: "not-a-commit" }), /GITHUB_SHA/);
});
