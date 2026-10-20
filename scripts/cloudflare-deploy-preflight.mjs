import fs from "node:fs";

const required = [
  "CLOUDFLARE_API_TOKEN",
  "CLOUDFLARE_ACCOUNT_ID",
];

const missing =
  required.filter(
    (name) =>
      !String(
        process.env[name] ?? "",
      ).trim(),
  );

if (missing.length > 0) {
  console.error(
    "Cloudflare production deploy is missing required configuration: " +
      missing.join(", "),
  );
  process.exit(2);
}

if (
  !/^[0-9a-f]{32}$/i.test(
    String(
      process.env.CLOUDFLARE_ACCOUNT_ID,
    ),
  )
) {
  console.error(
    "CLOUDFLARE_ACCOUNT_ID must be a 32-character Cloudflare account ID.",
  );
  process.exit(2);
}

const optionalHmac =
  String(
    process.env
      .LUDOPROOF_SESSION_HMAC_KEY ??
      "",
  );
if (
  optionalHmac.length > 0 &&
  optionalHmac.length < 32
) {
  console.error(
    "LUDOPROOF_SESSION_HMAC_KEY must contain at least 32 characters when supplied.",
  );
  process.exit(2);
}

const wrangler =
  JSON.parse(
    fs.readFileSync(
      "server/wrangler.json",
      "utf8",
    ),
  );
const vars =
  wrangler?.vars ?? {};

const apiRouteConfigured = Array.isArray(wrangler?.routes) &&
  wrangler.routes.some((route) =>
    route?.pattern === "api.ludopaws.rekixo.com/*" &&
    route?.zone_name === "rekixo.com"
  );
if (!apiRouteConfigured) {
  console.error("Cloudflare production config must explicitly route api.ludopaws.rekixo.com/* through the ludoproof-game-api Worker.");
  process.exit(2);
}

const oauthClientId = String(vars.GOOGLE_SIGN_IN_WEB_CLIENT_ID ?? "").trim();
if (!/^[0-9]+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$/.test(oauthClientId)) {
  console.error("GOOGLE_SIGN_IN_WEB_CLIENT_ID is missing or malformed in the prepared production Worker config.");
  process.exit(2);
}
if (vars.LUDOPROOF_ACCOUNT_WEB_ORIGIN !== "https://ludopaws.rekixo.com") {
  console.error("LUDOPROOF_ACCOUNT_WEB_ORIGIN must match the Rekixo landing/account site origin.");
  process.exit(2);
}
if (!["true", "false"].includes(String(vars.GOOGLE_PLAY_BILLING_ENABLED))) {
  console.error("GOOGLE_PLAY_BILLING_ENABLED must be explicitly set to true or false.");
  process.exit(2);
}
if (!["true", "false"].includes(String(vars.LUDOPROOF_ADMOB_SSV_ENABLED))) {
  console.error("LUDOPROOF_ADMOB_SSV_ENABLED must be explicitly set to true or false.");
  process.exit(2);
}
if (!["LOCKDOWN", "FAMILY_V1", "FAMILY_V2"].includes(vars.LUDOPROOF_AGE_SAFETY_MODE)) {
  console.error("The release must use explicit FAMILY_V1, FAMILY_V2 or LOCKDOWN capabilities.");
  process.exit(2);
}
const billing = vars.GOOGLE_PLAY_BILLING_ENABLED === "true";
const ads = vars.LUDOPROOF_ADMOB_SSV_ENABLED === "true";
if (String(billing) !== String(process.env.LUDOPROOF_BILLING_ENABLED ?? "false") ||
    String(ads) !== String(process.env.LUDOPROOF_ADS_ENABLED ?? "false")) {
  console.error("Android and Worker billing/ads flags must match.");
  process.exit(2);
}
if ((billing || ads || vars.LUDOPAWS_GOOGLE_LINKING_ENABLED === "true") && vars.LUDOPROOF_AGE_SAFETY_MODE !== "FAMILY_V2") {
  console.error("Commerce and new Google linking require the FAMILY_V2 release.");
  process.exit(2);
}
if (ads && vars.LUDOPAWS_AD_SAFETY_REVIEW_APPROVED !== "true") {
  console.error("Advertising requires LUDOPAWS_AD_SAFETY_REVIEW_APPROVED=true.");
  process.exit(2);
}
if (vars.LUDOPROOF_ADMOB_REWARD_ITEM !== "gems") {
  console.error('LUDOPROOF_ADMOB_REWARD_ITEM must be "gems".');
  process.exit(2);
}
if (vars.GOOGLE_PLAY_BILLING_ENABLED === "true" &&
    !/^[^@\s]+@[^@\s]+\.iam\.gserviceaccount\.com$/.test(String(vars.GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL ?? ""))) {
  console.error("Billing is enabled but GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL is missing or malformed.");
  process.exit(2);
}
if (vars.LUDOPROOF_ADMOB_SSV_ENABLED === "true" &&
    !/^ca-app-pub-[0-9]{16}\/[0-9]{10}$/.test(String(vars.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID ?? ""))) {
  console.error("AdMob SSV is enabled but LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID is missing or malformed.");
  process.exit(2);
}


console.log(
  "Cloudflare production deploy preflight passed.",
);
