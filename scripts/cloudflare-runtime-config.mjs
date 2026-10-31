import { applyReferralRuntimeVars } from "./referral-runtime-config.mjs";

const GOOGLE_WEB_CLIENT_ID = /^[0-9]+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$/;
const GOOGLE_PLAY_SERVICE_ACCOUNT = /^[^@\s]+@[^@\s]+\.iam\.gserviceaccount\.com$/;
const ADMOB_REWARDED_UNIT = /^ca-app-pub-[0-9]{16}\/[0-9]{10}$/;
export const ACCOUNT_WEB_ORIGIN = "https://ludopaws.rekixo.com";

function readBoolean(name, raw) {
  const value = String(raw ?? "").trim();
  if (!value) return false;
  if (value === "true") return true;
  if (value === "false") return false;
  throw new Error(`${name} must be exactly "true" or "false" when set.`);
}

/**
 * Build the full non-secret runtime vars set for the production Worker.
 * Explicitly materialize security-critical vars so Wrangler deployments do not
 * silently remove dashboard-managed settings or preserve stale feature flags.
 */
export function buildCloudflareRuntimeVars(existingVars, environment) {
  const oauthClientId = String(environment.GOOGLE_SIGN_IN_WEB_CLIENT_ID ?? "").trim();
  if (!GOOGLE_WEB_CLIENT_ID.test(oauthClientId)) {
    throw new Error("GOOGLE_SIGN_IN_WEB_CLIENT_ID repository variable is required and must be a valid Google Web OAuth client ID.");
  }
  const androidClients = [...new Set(String(environment.GOOGLE_SIGN_IN_ANDROID_CLIENT_IDS ?? "")
    .split(",").map((value) => value.trim()).filter(Boolean))];
  if (androidClients.length > 10 || androidClients.some((value) => !GOOGLE_WEB_CLIENT_ID.test(value))) {
    throw new Error("GOOGLE_SIGN_IN_ANDROID_CLIENT_IDS must contain at most ten comma-separated OAuth client IDs.");
  }

  const accountOrigin = String(environment.LUDOPROOF_ACCOUNT_WEB_ORIGIN ?? "").trim();
  if (accountOrigin !== ACCOUNT_WEB_ORIGIN) {
    throw new Error(`LUDOPROOF_ACCOUNT_WEB_ORIGIN must be exactly ${ACCOUNT_WEB_ORIGIN} for the Rekixo landing and account site.`);
  }

  const billingEnabled = readBoolean("GOOGLE_PLAY_BILLING_ENABLED", environment.GOOGLE_PLAY_BILLING_ENABLED);
  const androidBillingEnabled = readBoolean("LUDOPROOF_BILLING_ENABLED", environment.LUDOPROOF_BILLING_ENABLED);
  const adsEnabled = readBoolean("LUDOPROOF_ADMOB_SSV_ENABLED", environment.LUDOPROOF_ADMOB_SSV_ENABLED);
  const androidAdsEnabled = readBoolean("LUDOPROOF_ADS_ENABLED", environment.LUDOPROOF_ADS_ENABLED);
  if (billingEnabled !== androidBillingEnabled) {
    throw new Error("GOOGLE_PLAY_BILLING_ENABLED and LUDOPROOF_BILLING_ENABLED must match before deployment.");
  }
  if (adsEnabled !== androidAdsEnabled) {
    throw new Error("LUDOPROOF_ADMOB_SSV_ENABLED and LUDOPROOF_ADS_ENABLED must match before deployment.");
  }

  const serviceAccountEmail = String(environment.GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL ?? "").trim();
  if (serviceAccountEmail && !GOOGLE_PLAY_SERVICE_ACCOUNT.test(serviceAccountEmail)) {
    throw new Error("GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL must be a Google Cloud service-account email.");
  }
  if (billingEnabled && !serviceAccountEmail) {
    throw new Error("Billing cannot be enabled until GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL is configured as a repository variable.");
  }

  const rewardedAdUnitId = String(environment.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID ?? "").trim();
  if (rewardedAdUnitId && !ADMOB_REWARDED_UNIT.test(rewardedAdUnitId)) {
    throw new Error("LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID must be a real AdMob rewarded-unit ID (ca-app-pub-<16 digits>/<10 digits>).");
  }
  const rewardItem = String(environment.LUDOPROOF_ADMOB_REWARD_ITEM ?? "gems").trim() || "gems";
  if (rewardItem !== "gems") {
    throw new Error('LUDOPROOF_ADMOB_REWARD_ITEM must be "gems"; the Worker requires a 5-Gem legacy unit and optionally a separate 2-Gem unit.');
  }
  const androidRewardedAdUnitId = String(environment.ADMOB_REWARDED_AD_UNIT_ID ?? "").trim();
  const rewardedAdUnitV2 = String(environment.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID_V2 ?? "").trim();
  const androidRewardedAdUnitV2 = String(environment.ADMOB_REWARDED_AD_UNIT_ID_V2 ?? "").trim();
  const androidAppId = String(environment.ADMOB_APP_ID ?? "").trim();
  // The optional v2 rollout is atomic: both build-side and server-side IDs
  // must match the same distinct real AdMob unit. Old 5-Gem clients remain live
  // while the new AdMob placement is being provisioned.
  if (rewardedAdUnitV2 || androidRewardedAdUnitV2) {
    if (!ADMOB_REWARDED_UNIT.test(rewardedAdUnitV2) ||
        rewardedAdUnitV2 !== androidRewardedAdUnitV2 ||
        rewardedAdUnitV2 === rewardedAdUnitId ||
        rewardedAdUnitV2 === "ca-app-pub-3940256099942544/5224354917") {
      throw new Error("Updated 2-Gem AdMob unit must be real, distinct from legacy and match ADMOB_REWARDED_AD_UNIT_ID_V2 and LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID_V2.");
    }
  }
  if (androidRewardedAdUnitId && !ADMOB_REWARDED_UNIT.test(androidRewardedAdUnitId)) {
    throw new Error("ADMOB_REWARDED_AD_UNIT_ID must be a real AdMob rewarded-unit ID.");
  }
  if (adsEnabled && !rewardedAdUnitId) {
    throw new Error("AdMob SSV cannot be enabled until LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID is configured.");
  }
  if (adsEnabled) {
    if (!/^ca-app-pub-[0-9]{16}~[0-9]{10}$/.test(androidAppId)) {
      throw new Error("ADMOB_APP_ID must be a real AdMob app ID before enabling production ads.");
    }
    if (!ADMOB_REWARDED_UNIT.test(androidRewardedAdUnitId) || androidRewardedAdUnitId !== rewardedAdUnitId) {
      throw new Error("Android and Worker AdMob rewarded-unit IDs must both be valid and match.");
    }
    if (androidAppId === "ca-app-pub-3940256099942544~3347511713" ||
        androidRewardedAdUnitId === "ca-app-pub-3940256099942544/5224354917") {
      throw new Error("Google sample AdMob IDs are not allowed in production configuration.");
    }
  }

  const adSafetyApproved = readBoolean("LUDOPAWS_AD_SAFETY_REVIEW_APPROVED", environment.LUDOPAWS_AD_SAFETY_REVIEW_APPROVED);
  if (adsEnabled && !adSafetyApproved) throw new Error("Advertising requires LUDOPAWS_AD_SAFETY_REVIEW_APPROVED=true.");
  const googleLinking = readBoolean("LUDOPAWS_GOOGLE_LINKING_ENABLED", environment.LUDOPAWS_GOOGLE_LINKING_ENABLED);
  const vars = {
    ...existingVars,
    GOOGLE_SIGN_IN_WEB_CLIENT_ID: oauthClientId,
    // Explicit empty value clears stale presenters when the repository var is removed.
    GOOGLE_SIGN_IN_ANDROID_CLIENT_IDS: androidClients.join(","),
    LUDOPROOF_ACCOUNT_WEB_ORIGIN: accountOrigin,
    GOOGLE_PLAY_BILLING_ENABLED: String(billingEnabled),
    LUDOPROOF_ADMOB_SSV_ENABLED: String(adsEnabled),
    LUDOPROOF_ADMOB_REWARD_ITEM: "gems",
    // Family capabilities are enforced on the server for old and new clients.
    LUDOPROOF_AGE_SAFETY_MODE: "FAMILY_V2",
    LUDOPAWS_AD_SAFETY_REVIEW_APPROVED: String(adSafetyApproved),
    LUDOPAWS_GOOGLE_LINKING_ENABLED: String(googleLinking),
  };

  if (serviceAccountEmail) vars.GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL = serviceAccountEmail;
  else delete vars.GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL;
  if (rewardedAdUnitId) vars.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID = rewardedAdUnitId;
  else delete vars.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID;
  if (rewardedAdUnitV2) vars.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID_V2 = rewardedAdUnitV2;
  else delete vars.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID_V2;

  // Emit a public, non-secret deployment identity so signed releases and
  // smoke tests cannot silently validate an older Cloudflare Worker.
  const deployCommit = String(environment.GITHUB_SHA ?? "").trim().toLowerCase();
  if (deployCommit && !/^[a-f0-9]{40}$/.test(deployCommit)) {
    throw new Error("GITHUB_SHA must be a 40-character Git commit SHA for a production deployment.");
  }
  if (deployCommit) vars.LUDOPROOF_DEPLOY_COMMIT_SHA = deployCommit;
  else delete vars.LUDOPROOF_DEPLOY_COMMIT_SHA;
  return applyReferralRuntimeVars(vars, environment);
}
