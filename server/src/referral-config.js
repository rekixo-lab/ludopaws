import { familyCapabilities } from "./family-safety.js";

export const REFERRAL_CAMPAIGN = "friendship-bundle-v1";
export const REFERRAL_REWARD = Object.freeze({
  gems: 100,
  cosmetics: ["dice_pearl_paw", "board_blossom_garden", "character_rabbit"],
});
export const REFERRAL_ORIGIN = "https://api.ludopaws.rekixo.com";
export const REFERRAL_PACKAGE = "com.rekixo.ludopaws";
export const DAY = 86_400_000;

export function referralsEnabled(env) {
  const start = Number(env?.LUDOPAWS_REFERRAL_START_MS);
  return referralsConfigured(env) && Date.now() >= start && Date.now() < start + 90 * DAY;
}

export function referralsConfigured(env) {
  const start = Number(env?.LUDOPAWS_REFERRAL_START_MS);
  return env?.LUDOPAWS_REFERRALS_ENABLED === "true" &&
    env?.LUDOPAWS_REFERRAL_POLICY_APPROVED === "true" &&
    familyCapabilities(env).newGoogleLinking &&
    Boolean(env?.LUDOPAWS_REFERRALS && env?.LUDOPROOF_ACCOUNT_DIRECTORY && env?.LUDOPROOF_PLAYER_ACCOUNTS) &&
    Number.isSafeInteger(start) && start > 0 &&
    /^\d{6,20}$/.test(String(env?.LUDOPAWS_INTEGRITY_PROJECT_NUMBER ?? "")) &&
    Boolean(env?.LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL && env?.LUDOPAWS_INTEGRITY_PRIVATE_KEY) &&
    signingCertificates(env).length > 0;
}

export function signingCertificates(env) {
  return String(env?.LUDOPAWS_PLAY_SIGNING_SHA256 ?? "").split(",")
    .map(value => value.trim().replaceAll(":", "").toLowerCase())
    .filter(value => /^[a-f0-9]{64}$/.test(value));
}

export async function referralRequest(env, path, body = {}) {
  const stub = env.LUDOPAWS_REFERRALS.get(env.LUDOPAWS_REFERRALS.idFromName(REFERRAL_CAMPAIGN));
  const response = await stub.fetch(new Request("https://referrals" + path, {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body),
  }));
  return response;
}
