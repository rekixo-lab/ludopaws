import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(new URL(path, import.meta.url), "utf8");
}

function amount(value) {
  return Number(value.replaceAll("_", ""));
}

const android = read("../android/app/src/main/java/com/rekixo/ludopaws/feature/store/domain/StoreCatalog.kt");
const server = read("../server/src/google-play-billing.js");
const pricing = read("../docs/pricing.html");
const terms = read("../docs/terms-of-service.html");
const privacy = read("../docs/privacy-policy.html");
const inAppPolicy = read("../android/app/src/main/java/com/rekixo/ludopaws/feature/settings/presentation/PrivacyPolicyDialogUi.kt");
const pagesWorkflow = read("../.github/workflows/account-deletion-pages.yml");
const androidBillingGateway = read("../android/app/src/main/java/com/rekixo/ludopaws/feature/store/data/billing/GooglePlayBillingGateway.kt");
const storeActivity = read("../android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/StoreActivity.kt");
const storeContent = read("../android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/components/StoreContentUi.kt");
const androidBuildConfig = read("../android/app/build.gradle.kts");
const accountRepository = read("../android/app/src/main/java/com/rekixo/ludopaws/feature/account/data/AccountRepository.kt");
const oneTimeBillingSetup = read("../docs/GOOGLE_PLAY_ONE_TIME_BILLING_SETUP.md");
const billingReleaseChecklist = read("../docs/GOOGLE_PLAY_BILLING_RELEASE_CHECKLIST.md");
const monetizationRoadmap = read("../docs/STORE_MONETIZATION_ROADMAP.md");
const accountWorker = read("../server/src/index-account.js");
const baseWorker = read("../server/src/index.js");
const playerAccount = read("../server/src/player-account.js");
const purchaseLedger = read("../server/src/store-purchase-ledger.js");
const releaseWorkflow = read("../.github/workflows/android-signed-release.yml");
const backendWorkflow = read("../.github/workflows/deploy-cloudflare.yml");
const runtimeConfig = read("./cloudflare-runtime-config.mjs");
const deployPreflight = read("./cloudflare-deploy-preflight.mjs");

const constants = Object.fromEntries(
  [...android.matchAll(/const\s+val\s+([A-Z0-9_]+)\s*=\s*\n?\s*"([^"]+)"/g)]
    .map((match) => [match[1], match[2]]),
);
const androidProductCatalogStart = android.indexOf("val products =");
assert.ok(androidProductCatalogStart >= 0, "Android must define the one-time products catalog");
const androidProductCatalog = android.slice(androidProductCatalogStart);
const androidProducts = {};
for (const match of androidProductCatalog.matchAll(/StoreProduct\(([\s\S]*?)\n {12}\),/g)) {
  const block = match[1];
  const constant = block.match(/productId\s*=\s*([A-Z0-9_]+)/)?.[1];
  const gemText = block.match(/gemAmount\s*=\s*([0-9_]+)/)?.[1];
  const kind = block.match(/kind\s*=\s*StoreProductKind\.([A-Z_]+)/)?.[1];
  assert.ok(constant && constants[constant], "every Android store item needs a resolvable product ID");
  assert.ok(gemText && kind, "every Android store item needs a reward amount and purchase type");
  androidProducts[constants[constant]] = {
    gemAmount: amount(gemText),
    removeAds: kind === "NON_CONSUMABLE_REMOVE_ADS",
    consumable: kind === "CONSUMABLE_GEMS",
  };
}

const serverProducts = Object.fromEntries(
  [...server.matchAll(/^\s{2}([a-z][a-z0-9_]+): Object\.freeze\(\{\s*gemAmount:\s*([0-9_]+),\s*removeAds:\s*(true|false),\s*consumable:\s*(true|false),\s*\}\),/gm)]
    .map((match) => [match[1], {
      gemAmount: amount(match[2]),
      removeAds: match[3] === "true",
      consumable: match[4] === "true",
    }]),
);

assert.ok(Object.keys(androidProducts).length > 0, "Android product catalog must not be empty");
assert.deepEqual(
  androidProducts,
  serverProducts,
  "Android product IDs, gem amounts, Remove Ads entitlement and consumable types must match server verification",
);

for (const [productId, product] of Object.entries(serverProducts)) {
  assert.ok(pricing.includes("<code>" + productId + "</code>"), "pricing page missing " + productId);
  assert.ok(pricing.includes(product.gemAmount.toLocaleString("en-US") + " Gems"), "pricing page reward amount mismatch for " + productId);
}
assert.ok(pricing.includes("localized price and currency shown in the app"), "pricing page must direct users to the localized Google Play price");
assert.ok(pricing.includes("does not take payments or offer a separate web checkout"), "pricing page must not imply web checkout");
assert.ok(pricing.includes("Google Play order history"), "pricing page must explain the order/refund support boundary");
assert.ok(pricing.includes("child-directed"), "pricing must disclose family advertising treatment");
assert.ok(terms.includes("AI@rekixo.com"), "terms must provide the publisher support contact");
assert.doesNotMatch(pricing, /(?:₹|Rs\.?\s*|US\$|USD\s*)\s*\d/i, "public pricing page must not hard-code a regional currency price");

const retiredMonthlyPlanPattern =
  /Paws\s*Plus|paws_plus|monthly-auto|monthly subscription|monthly plan|subscription|renewal|auto-renew|recurring billing|LUDOPROOF_SUBSCRIPTIONS_ENABLED|GOOGLE_PLAY_SUBSCRIPTIONS_ENABLED|GOOGLE_PLAY_RTDN_AUDIENCE|GOOGLE_PLAY_RTDN_EMAIL|\/api\/google-play\/rtdn|\/api\/store\/subscriptions\/verify|ProductType\.SUBS|SUBSCRIPTION_STATE_/i;
const retiredMonthlyPlanFiles = [
  ["Android store catalog", android],
  ["Android Billing gateway", androidBillingGateway],
  ["Android Store activity", storeActivity],
  ["Android store content", storeContent],
  ["Android build configuration", androidBuildConfig],
  ["Android account repository", accountRepository],
  ["Pricing page", pricing],
  ["Terms of Service", terms],
  ["Privacy Policy", privacy],
  ["In-app Privacy Policy", inAppPolicy],
  ["One-time billing setup", oneTimeBillingSetup],
  ["One-time billing release checklist", billingReleaseChecklist],
  ["Monetization roadmap", monetizationRoadmap],
  ["Account API routes", accountWorker],
  ["Base Worker routes", baseWorker],
  ["Purchase ledger", purchaseLedger],
  ["Android release workflow", releaseWorkflow],
  ["Backend release workflow", backendWorkflow],
  ["Cloudflare runtime configuration", runtimeConfig],
  ["Cloudflare deploy preflight", deployPreflight],
];
for (const [name, content] of retiredMonthlyPlanFiles) {
  assert.doesNotMatch(content, retiredMonthlyPlanPattern, name + " must not expose or enable the retired monthly plan");
}
assert.doesNotMatch(
  playerAccount.replace("DROP TABLE IF EXISTS paws_plus_subscriptions", ""),
  retiredMonthlyPlanPattern,
  "PlayerAccount must not implement monthly entitlements; the only legacy reference may be the one-time cleanup migration",
);
assert.match(playerAccount, /DROP TABLE IF EXISTS paws_plus_subscriptions/, "backend startup must remove the retired entitlement table");
assert.doesNotMatch(playerAccount, /CREATE TABLE IF NOT EXISTS paws_plus_subscriptions/, "backend must not recreate the retired entitlement table");

assert.ok(privacy.includes('href="./pricing.html">Pricing and in-app purchase policy</a>'), "online privacy policy must link to pricing policy");
assert.ok(privacy.includes("configured reward is five Gems"), "online privacy policy must disclose the verified rewarded-ad grant");
assert.ok(inAppPolicy.includes("configured reward is five Gems"), "in-app privacy copy must disclose the same verified rewarded-ad grant");
assert.ok(inAppPolicy.includes("https://ludopaws.rekixo.com/pricing.html"), "in-app policy must link to pricing policy");
assert.ok(inAppPolicy.includes("https://ludopaws.rekixo.com/terms-of-service.html"), "in-app policy must link to Terms of Service");
assert.ok(pagesWorkflow.includes("cp docs/pricing.html site/pricing.html"), "Pages workflow must publish the pricing policy");
assert.ok(pricing.includes('href="./terms-of-service.html"'), "pricing page must link to Terms of Service");
assert.ok(terms.includes('href="./pricing.html"'), "Terms of Service must link to pricing policy");
assert.ok(terms.includes('href="./privacy-policy.html"'), "Terms of Service must link to privacy policy");
assert.ok(terms.includes('href="./account-deletion.html"'), "Terms of Service must link to account deletion");
assert.ok(pagesWorkflow.includes("cp docs/terms-of-service.html site/terms-of-service.html"), "Pages workflow must publish Terms of Service");
assert.ok(pagesWorkflow.includes("TERMS_RELEASE_REVIEW_REQUIRED"), "Pages workflow must block on the Terms of Service review marker");
assert.ok(pagesWorkflow.includes("fetch_public_page /terms-of-service.html"), "Pages workflow must smoke-test the published Terms of Service route");
assert.ok(pagesWorkflow.includes("PRICING_RELEASE_REVIEW_REQUIRED"), "Pages workflow must block on the pricing review marker");

console.log("One-time purchase policy contract passed: Android, server and public disclosures match.");

assert.ok(!storeActivity.includes('accountRepository.linkedIdentities()'), "checkout must not require optional Google backup");
assert.ok(storeActivity.includes("accountRepository.ensureGuestSession()"), "guest checkout must use a server account");
assert.ok(storeActivity.includes("PurchaseAccountSafety.canLaunch"), "checkout must reject stale account attribution");
assert.ok(!storeActivity.includes("confirmSavedAccountPurchase"), "explicit product selection must proceed to the Play payment sheet");

const checkoutFlow = storeActivity.slice(storeActivity.indexOf("internal fun requestPurchase("), storeActivity.indexOf("internal fun requestRewardedAd("));
assert.doesNotMatch(checkoutFlow, /FamilyAgeScreen|googleLinked|linkedIdentities|Save your account/, "optional identity and age choices must not gate guest checkout");
assert.ok(checkoutFlow.includes("storeStatus()"), "a retry must recheck service availability");
assert.ok(checkoutFlow.includes("generation != checkoutGeneration"), "a stopped Store must discard stale checkout work");
for (const disclosure of [pricing, terms, privacy, inAppPolicy]) {
  assert.ok(disclosure.includes("Guest and Google-linked game accounts can open Google Play checkout"), "guest checkout disclosures must match the app");
  assert.ok(!disclosure.includes("Before starting a new Google Play purchase, save"), "Google backup must not be mandatory");
}
assert.ok(checkoutFlow.includes("billing.updateAccountId(requireNotNull(currentSession).billingAccountId)"), "checkout must pin the current billing binding immediately before Play launch");

assert.ok(storeActivity.includes("override fun onResume()"), "returning from Play must refresh purchases even without onStart");
assert.ok(storeActivity.includes("billing.refreshPurchases()"), "completed pending purchases must be queried on resume");
assert.ok(storeActivity.includes("Connecting to Google Play…"), "checkout needs visible progress while connecting");
