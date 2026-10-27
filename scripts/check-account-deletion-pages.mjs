import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";

function read(path) {
  return readFileSync(new URL(path, import.meta.url), "utf8");
}

function has(source, snippet, label) {
  assert.ok(source.includes(snippet), label + " is missing");
}

function matches(source, pattern, label) {
  assert.match(source, pattern, label + " is missing or malformed");
}

const landingPage = read("../docs/landing-page.html");
const page = read("../docs/account-deletion.html");
const policy = read("../docs/privacy-policy.html");
const terms = read("../docs/terms-of-service.html");
const worker = read("../server/src/index-account.js");
const directory = read("../server/src/account-directory.js");
const player = read("../server/src/player-account.js");
const appBuild = read("../android/app/build.gradle.kts");
const manifest = read("../android/app/src/main/AndroidManifest.xml");
const inAppPolicy = read("../android/app/src/main/java/com/rekixo/ludopaws/feature/settings/presentation/PrivacyPolicyDialogUi.kt");
const pagesWorkflow = read("../.github/workflows/account-deletion-pages.yml");

has(page, 'id="guest-deletion-code" type="password" maxlength="64"', "guest delete-only credential input");
has(page, 'id="confirm-guest-delete"', "guest deletion confirmation checkbox");
has(page, 'id="guest-delete-button"', "guest deletion submit button");
has(page, 'post("/api/account/guest/delete"', "guest deletion API request");
has(page, 'post("/api/account/delete"', "Google-linked deletion API request");
has(page, 'credentials: "omit"', "credential-free cross-origin fetch mode");
has(page, 'cache: "no-store"', "no-store fetch caching");
has(landingPage, 'href="./account-deletion.html"', "landing-page account-deletion link");
has(landingPage, 'href="./terms-of-service.html"', "landing-page terms link");
has(landingPage, 'href="./privacy-policy.html"', "landing-page privacy-policy link");
has(landingPage, "https://rekixo.com", "publisher website link");
has(page, 'connect-src \'self\' https://api.ludopaws.rekixo.com', "account API Content Security Policy allowlist");
assert.doesNotMatch(page, /localStorage|sessionStorage/, "the page must not persist session or deletion credentials in browser storage");
assert.doesNotMatch(page, /console\.(log|debug|info|warn|error)\s*\(/, "the page must not log credential-bearing UI state");

has(worker, '"/api/account/guest/delete"', "guest deletion CORS allowlist");
has(worker, '"/guest-deletion/register"', "authenticated guest code registration route");
has(worker, '"/guest-deletion/claim"', "guest code claim route");
has(worker, 'ludoproof:guest-deletion-credential:v1:', "domain-separated guest code hash");
has(directory, "deleted_guest_deletion_credentials", "guest deletion replay tombstone");
has(directory, "ACCOUNT_DELETION_IN_PROGRESS", "guest deletion claim lock");
has(player, "const guestRecoveryVerified = body?.guestRecoveryVerified === true", "guest deletion server-owned verification boundary");
has(policy, "delete-only code", "guest deletion disclosure in privacy policy");
has(policy, 'href="./account-deletion.html">official account and data deletion page</a>', "privacy policy links to the external deletion page");
has(policy, "No separate long-term match-proof archive", "privacy policy reflects removal of proof archival");
has(policy, "AI@rekixo.com", "privacy support contact");
has(page, "No separate long-term match-proof archive", "deletion page reflects removal of proof archival");
has(page, "AI@rekixo.com", "deletion page support contact");
has(policy, "does not capture microphone audio", "published family voice restriction");
has(inAppPolicy, "Free-text chat and voice are unavailable", "in-app family communication disclosure");
matches(manifest, /android:name="android.permission.RECORD_AUDIO" tools:node="remove"/, "removed microphone permission");
assert.equal(existsSync(new URL("../android/app/src/main/java/com/rekixo/ludopaws/feature/online/data/realtime/FriendsVoiceChatSession.kt", import.meta.url)), false, "voice implementation must remain removed");
assert.doesNotMatch(appBuild, /implementation\("io\.github\.webrtc-sdk:/, "family package must not include WebRTC");

has(pagesWorkflow, "LUDOPROOF_POLICY_RELEASE_APPROVED", "publisher approval gate");
has(pagesWorkflow, "GOOGLE_SIGN_IN_WEB_CLIENT_ID", "Google OAuth Web Client ID release gate");
has(pagesWorkflow, "LUDOPROOF_SUPPORT_EMAIL", "monitored support contact release gate");
has(pagesWorkflow, "LUDOPROOF_ACCOUNT_WEB_ORIGIN", "account-site CORS origin release gate");
has(pagesWorkflow, "site/CNAME", "custom-domain CNAME artifact");
has(pagesWorkflow, "https://ludopaws.rekixo.com", "Rekixo site origin");
has(pagesWorkflow, "https://api.ludopaws.rekixo.com", "Rekixo API origin");
has(pagesWorkflow, "cp docs/landing-page.html site/index.html", "landing page publication mapping");
has(pagesWorkflow, "actions/deploy-pages", "explicit Pages publishing step");
has(pagesWorkflow, "Verify published public policy routes", "post-deployment HTTPS smoke test for the legal and sign-in pages");
has(pagesWorkflow, "fetch_public_page /terms-of-service.html", "post-deployment Terms of Service route smoke test");
has(pagesWorkflow, "test -f docs/referral-terms.html", "referral terms publication file gate");
has(pagesWorkflow, "cp docs/referral-terms.html site/referral-terms.html", "referral terms publication mapping");
has(pagesWorkflow, "fetch_public_page /referral-terms.html", "post-deployment referral terms route smoke test");
has(read("../docs/referral-terms.html"), "Friendship invitation terms", "referral terms page");


const pricing = read("../docs/pricing.html");
has(landingPage, 'href="./pricing.html"', "landing-page pricing-policy link");
has(policy, 'href="./pricing.html">Pricing and in-app purchase policy</a>', "privacy-policy pricing link");
has(policy, 'href="./terms-of-service.html">Terms of Service</a>', "privacy-policy terms link");
has(policy, 'href="./pricing.html">Pricing & in-app purchases</a>', "privacy footer pricing link");
has(pricing, "child-directed", "family monetization disclosure");
has(pricing, 'href="./privacy-policy.html"', "pricing page privacy-policy link");
has(pricing, 'href="./account-deletion.html"', "pricing page account-deletion link");
has(pagesWorkflow, 'test -f docs/pricing.html', "pricing page publication file gate");
has(pagesWorkflow, 'test -f docs/terms-of-service.html', "terms page publication file gate");
has(pagesWorkflow, "TERMS_RELEASE_REVIEW_REQUIRED", "terms review marker blocks publishing");
has(pagesWorkflow, 'cp docs/terms-of-service.html site/terms-of-service.html', "terms page publication mapping");
has(pagesWorkflow, 'cp docs/pricing.html site/pricing.html', "pricing page publication mapping");
has(pagesWorkflow, 'node scripts/check-pricing-policy.mjs', "pricing contract check before publication");
has(inAppPolicy, 'https://ludopaws.rekixo.com/pricing.html', "in-app pricing-policy URL");
has(inAppPolicy, 'https://ludopaws.rekixo.com/terms-of-service.html', "in-app Terms of Service URL");


has(terms, "<title>Ludo Paws — Terms of Service</title>", "Terms of Service page title");
has(terms, "AI@rekixo.com", "Terms of Service support contact");
has(terms, 'href="./pricing.html"', "terms page links to pricing policy");
has(terms, 'href="./privacy-policy.html"', "terms page links to privacy policy");
has(terms, 'href="./account-deletion.html"', "terms page links to account deletion");
has(landingPage, 'href="./terms-of-service.html"', "landing page links to terms");
has(pricing, 'href="./terms-of-service.html"', "pricing page links to terms");
has(policy, 'href="./terms-of-service.html"', "privacy policy links to terms");
has(page, 'href="./terms-of-service.html"', "account deletion page links to terms");

console.log("Account-deletion source checks passed. Public publication remains gated by publisher/legal, OAuth, support and origin configuration.");
