const FLAGS = [
  "LUDOPAWS_REFERRALS_ENABLED",
  "LUDOPAWS_REFERRAL_POLICY_APPROVED",
  "LUDOPAWS_REFERRAL_SDK_REVIEW_APPROVED",
  "LUDOPAWS_REFERRAL_RELEASE_VALIDATED",
];
const SETTINGS = [
  "LUDOPAWS_INTEGRITY_PROJECT_NUMBER",
  "LUDOPAWS_PLAY_SIGNING_SHA256",
  "LUDOPAWS_REFERRAL_START_MS",
  "LUDOPAWS_REFERRAL_MIN_VERSION_CODE",
];

export function buildReferralRuntimeVars(environment) {
  const vars = {};
  for (const name of FLAGS) {
    const value = String(environment[name] ?? "").trim();
    if (value && value !== "true" && value !== "false") throw new Error(`${name} must be exactly "true" or "false".`);
    vars[name] = value || "false";
  }
  for (const name of SETTINGS) {
    const value = String(environment[name] ?? "").trim();
    if (value) vars[name] = value;
  }
  if (vars.LUDOPAWS_INTEGRITY_PROJECT_NUMBER && !/^\d{6,20}$/.test(vars.LUDOPAWS_INTEGRITY_PROJECT_NUMBER)) {
    throw new Error("LUDOPAWS_INTEGRITY_PROJECT_NUMBER must be the numeric linked Cloud project number.");
  }
  if (vars.LUDOPAWS_REFERRAL_START_MS && (!Number.isSafeInteger(Number(vars.LUDOPAWS_REFERRAL_START_MS)) || Number(vars.LUDOPAWS_REFERRAL_START_MS) <= 0)) {
    throw new Error("LUDOPAWS_REFERRAL_START_MS must be a positive UTC Unix timestamp in milliseconds.");
  }
  if (vars.LUDOPAWS_REFERRAL_MIN_VERSION_CODE && (!/^\d{1,12}$/.test(vars.LUDOPAWS_REFERRAL_MIN_VERSION_CODE) || Number(vars.LUDOPAWS_REFERRAL_MIN_VERSION_CODE) <= 0)) {
    throw new Error("LUDOPAWS_REFERRAL_MIN_VERSION_CODE must be a positive versionCode.");
  }
  if (vars.LUDOPAWS_PLAY_SIGNING_SHA256) {
    const certificates = vars.LUDOPAWS_PLAY_SIGNING_SHA256.split(",").map(value => value.trim().replaceAll(":", "").toLowerCase());
    if (certificates.length > 10 || certificates.some(value => !/^[a-f0-9]{64}$/.test(value))) {
      throw new Error("LUDOPAWS_PLAY_SIGNING_SHA256 must contain at most ten valid Play app-signing SHA-256 fingerprints.");
    }
    vars.LUDOPAWS_PLAY_SIGNING_SHA256 = [...new Set(certificates)].join(",");
  }
  if (vars.LUDOPAWS_REFERRALS_ENABLED === "true") {
    if (FLAGS.slice(1).some(name => vars[name] !== "true")) throw new Error("Referral activation requires separate policy, SDK and release validation approvals.");
    if (environment.LUDOPAWS_GOOGLE_LINKING_ENABLED !== "true") throw new Error("Referral activation requires reviewed Google linking.");
    if (SETTINGS.some(name => !vars[name])) throw new Error("Referral activation requires the linked project, Play signing certificate, immutable start time and validated versionCode.");
  }
  return vars;
}

export function applyReferralRuntimeVars(existingVars, environment) {
  const vars = { ...existingVars };
  for (const name of [...FLAGS, ...SETTINGS, "LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL", "LUDOPAWS_INTEGRITY_PRIVATE_KEY"]) delete vars[name];
  return { ...vars, ...buildReferralRuntimeVars(environment) };
}
