import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const failures = [];

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function requireText(name, source, token, message) {
  if (!source.includes(token)) failures.push(`${name}: ${message}`);
}

function requirePattern(name, source, pattern, message) {
  if (!pattern.test(source)) failures.push(`${name}: ${message}`);
}

const appBuild = read("android/app/build.gradle.kts");
const signedReleaseWorkflow = read(".github/workflows/android-signed-release.yml");
const snapshotTests = read(
  "android/app/src/test/java/com/rekixo/ludopaws/GameJsonAuthoritativeSnapshotTest.kt",
);
const rulesets = read("server/src/rulesets.js");
const interstitialController = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/data/ads/PostWinInterstitialAdController.kt",
);
const interstitialPolicy = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/domain/PostWinInterstitialPolicy.kt",
);
const interstitialExitGate = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/data/ads/PostWinInterstitialExitGate.kt",
);
const rewardedAdsController = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/data/ads/RewardedAdsController.kt",
);
const storeActivity = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/StoreActivity.kt",
);
const offlineExit = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/common/OfflineExitDialogUi.kt",
);
const offlineActivity = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/OfflineGameActivity.kt",
);
const onlineMain = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/MainActivity.kt",
);

const androidSourceRoots = [
  "android/app/src/main/java/com/rekixo/ludopaws",
  "android/app/src/test/java/com/rekixo/ludopaws",
  "android/app/src/androidTest/java/com/rekixo/ludopaws",
];

function listSourceFiles(directory) {
  if (!fs.existsSync(path.join(repoRoot, directory))) {
    failures.push(`android source package: required source root ${directory} is missing.`);
    return [];
  }
  const files = [];
  for (const entry of fs.readdirSync(path.join(repoRoot, directory), { withFileTypes: true })) {
    const relative = path.join(directory, entry.name);
    if (entry.isDirectory()) files.push(...listSourceFiles(relative));
    else if (entry.isFile() && /\.(kt|java)$/.test(entry.name)) files.push(relative);
  }
  return files;
}

requireText(
  "android/app/build.gradle.kts",
  appBuild,
  'namespace = "com.rekixo.ludopaws"',
  "Android Gradle namespace must use the approved Rekixo application package.",
);
requireText(
  "android/app/build.gradle.kts",
  appBuild,
  'applicationId = "com.rekixo.ludopaws"',
  "Android applicationId must use the approved Rekixo package.",
);
const androidSourceFiles = androidSourceRoots.flatMap(listSourceFiles);
if (androidSourceFiles.length === 0) {
  failures.push("android source package: no Kotlin/Java source files were found under the Rekixo package roots.");
}
for (const relativePath of androidSourceFiles) {
  const source = read(relativePath);
  if (!/^package com\.rekixo\.ludopaws(?:\.|$)/m.test(source)) {
    failures.push(`${relativePath}: Kotlin/Java package declaration must be under com.rekixo.ludopaws.`);
  }
}

requirePattern(
  "android/app/build.gradle.kts",
  appBuild,
  /LUDOPAWS_VERSION_CODE[\s\S]*?orElse\("4"\)/,
  "Internal releases must use configurable versionCode >= 4.",
);
requirePattern(
  "android/app/build.gradle.kts",
  appBuild,
  /LUDOPAWS_VERSION_NAME[\s\S]*?orElse\("1\.0\.0-rc4"\)/,
  "Internal releases must expose their configured RC4 version.",
);

for (const variable of [
  "LUDOPROOF_RELEASE_STORE_FILE",
  "LUDOPROOF_RELEASE_STORE_PASSWORD",
  "LUDOPROOF_RELEASE_KEY_ALIAS",
  "LUDOPROOF_RELEASE_KEY_PASSWORD",
]) {
  requireText(
    "android/app/build.gradle.kts",
    appBuild,
    `environmentVariable("${variable}")`,
    `release signing must read ${variable} from the environment.`,
  );
}
requireText(
  "android/app/build.gradle.kts",
  appBuild,
  "releaseSigningConfigured",
  "release signing must be explicit and all-or-nothing.",
);
requireText(
  "android/app/build.gradle.kts",
  appBuild,
  "releaseSigningPartiallyConfigured",
  "partial release signing configuration must be detected.",
);
requireText(
  "android/app/build.gradle.kts",
  appBuild,
  "org.gradle.api.GradleException",
  "partial release signing configuration must fail closed.",
);
requireText(
  "android/app/build.gradle.kts",
  appBuild,
  'signingConfig = signingConfigs.getByName("release")',
  "release build must use the secure release signing config when configured.",
);

if (/storePassword\s*=\s*"[^"$]+"/.test(appBuild)) {
  failures.push("android/app/build.gradle.kts: release store password must never be hardcoded.");
}
if (/keyPassword\s*=\s*"[^"$]+"/.test(appBuild)) {
  failures.push("android/app/build.gradle.kts: release key password must never be hardcoded.");
}

for (const rulesetId of [
  "ludoproof-standard-v1",
  "ludoproof-standard-v2",
  "ludoproof-team-v1",
  "ludoproof-team-v2",
]) {
  requireText(
    "GameJsonAuthoritativeSnapshotTest.kt",
    snapshotTests,
    rulesetId,
    `Android authoritative snapshot coverage must include ${rulesetId}.`,
  );
}

requireText(
  "android/app/build.gradle.kts",
  appBuild,
  'providers.gradleProperty("LUDOPROOF_INTERSTITIAL_ADS_ENABLED")',
  "the post-win interstitial placement must remain independently configurable and default-off.",
);

for (const [property, variable] of [
  ["LUDOPROOF_BILLING_ENABLED", "ludoProofBillingEnabled"],
  ["LUDOPROOF_ADS_ENABLED", "ludoProofAdsEnabled"],
  ["LUDOPROOF_INTERSTITIAL_ADS_ENABLED", "ludoProofInterstitialAdsEnabled"],
]) {
  requirePattern(
    "android/app/build.gradle.kts",
    appBuild,
    new RegExp(
      `val\\s+${variable}\\s*=\\s*providers\\.gradleProperty\\("${property}"\\)\\s*\\.orElse\\("false"\\)\\s*\\.get\\(\\)\\s*\\.toBooleanStrictOrNull\\(\\)\\s*\\?:\\s*false`,
    ),
    `${property} must default to false and malformed values must fail closed.`,
  );
}

for (const [marker, message] of [
  ['[ -n "$APP_BILLING" ] || APP_BILLING="false"', "Android Billing must default off when release configuration is omitted."],
  ['[ -n "$SERVER_BILLING" ] || SERVER_BILLING="false"', "Worker Billing must default off when release configuration is omitted."],
  ['[ -n "$APP_ADS" ] || APP_ADS="false"', "Android ads must default off when release configuration is omitted."],
  ['[ -n "$INTERSTITIAL_ADS" ] || INTERSTITIAL_ADS="false"', "Interstitial ads must default off when release configuration is omitted."],
  ['[ -n "$SERVER_ADS" ] || SERVER_ADS="false"', "Worker SSV/rewarded ads must default off when release configuration is omitted."],
  ['if [ "$APP_BILLING" != "$SERVER_BILLING" ]; then', "Signed release must block mismatched Android/Worker Billing flags."],
  ['if [ "$APP_ADS" != "$SERVER_ADS" ]; then', "Signed release must block mismatched Android/Worker Ads flags."],
]) {
  requireText(
    ".github/workflows/android-signed-release.yml",
    signedReleaseWorkflow,
    marker,
    message,
  );
}

requireText(
  "PostWinInterstitialAdController.kt",
  interstitialController,
  "InterstitialAd.load(",
  "the initial placement must use AdMob interstitials, not rewarded ads.",
);
requireText(
  "PostWinInterstitialAdController.kt",
  interstitialController,
  "consentInformation.canRequestAds()",
  "interstitial requests must honor UMP consent eligibility.",
);
requireText(
  "PostWinInterstitialPolicy.kt",
  interstitialPolicy,
  'matchStatus != FINISHED_STATUS',
  "post-win placement must reject unfinished matches.",
);
requirePattern(
  "PostWinInterstitialPolicy.kt",
  interstitialPolicy,
  /shouldShowFillerInterstitial\(\s*adsRemoved:\s*Boolean,\s*\):\s*Boolean\s*=\s*!adsRemoved/,
  "Remove Ads must suppress the unsolicited interstitial placement.",
);
requireText(
  "PostWinInterstitialExitGate.kt",
  interstitialExitGate,
  'val wallet = accountRepository.wallet()',
  "Remove Ads entitlement must be freshly confirmed from the authoritative wallet before display.",
);
requireText(
  "PostWinInterstitialExitGate.kt",
  interstitialExitGate,
  "onAdDismissed = onAdDismissed",
  "the exit gate must pass the ad-dismissed callback to the activity.",
);
requireText(
  "PostWinInterstitialExitGate.kt",
  interstitialExitGate,
  "onFailedToShow = onSkipAd",
  "failed-to-show or suppressed interstitials must continue normal back navigation.",
);
requireText(
  "MainActivity.kt",
  onlineMain,
  "postWinInterstitialShownForMatchId == matchId",
  "an already-consumed remote win result must not show another interstitial.",
);
requireText(
  "RewardedAdsController.kt",
  rewardedAdsController,
  "fun showRewardedAd(",
  "the user-initiated rewarded-ad flow must remain available.",
);
if (rewardedAdsController.includes("adsRemoved")) {
  failures.push("RewardedAdsController.kt: Remove Ads must not suppress the user-requested +5 Gems rewarded ad.");
}
requireText(
  "StoreActivity.kt",
  storeActivity,
  "rewardedAdsEnabledByServer &&",
  "rewarded ads must remain independently controlled by the rewarded-ad server flag.",
);
requireText(
  "OfflineExitDialogUi.kt",
  offlineExit,
  "showPostWinInterstitialIfReady",
  "local-game interstitial must be attached to result-screen exit only.",
);
requireText(
  "OfflineGameActivity.kt",
  offlineActivity,
  "postWinInterstitialShownForMatchId == matchId",
  "an already-consumed local win result must not show another interstitial.",
);
requireText(
  "MainActivity.kt",
  onlineMain,
  "requestRemoteExitAfterWinInterstitial",
  "remote-game back actions must route through the win-only placement policy.",
);
requirePattern(
  "OfflineGameActivity.kt",
  offlineActivity,
  /onAdDismissed\s*=\s*\{\s*postWinInterstitialShownForMatchId = matchId\s*onSkipAd\(\)\s*\}/,
  "closing the interstitial after a local win must automatically navigate home.",
);
requirePattern(
  "MainActivity.kt",
  onlineMain,
  /onAdDismissed\s*=\s*\{\s*postWinInterstitialShownForMatchId = matchId\s*onSkipAd\(\)\s*\}/,
  "closing the interstitial after a remote win must automatically navigate home.",
);

for (const policyToken of [
  "extraTurnOnHome: true",
  'rollTimeoutPolicy: "FORFEIT_ROLL_AND_ADVANCE_TURN"',
  'ownTokenStacking: "ALLOWED"',
  'opponentStackCapture: "CAPTURE_ALL_ON_UNSAFE_CELL"',
  'startingPlayerPolicy: "HOST_SEAT_ZERO"',
]) {
  requireText(
    "server/src/rulesets.js",
    rulesets,
    policyToken,
    `ruleset v2 release contract is missing ${policyToken}.`,
  );
}

for (const property of [
  "LUDOPROOF_API_BASE_URL",
  "LUDOPROOF_BILLING_ENABLED",
  "LUDOPROOF_ADS_ENABLED",
  "LUDOPROOF_INTERSTITIAL_ADS_ENABLED",
  "ADMOB_APP_ID",
  "ADMOB_REWARDED_AD_UNIT_ID",
  "ADMOB_INTERSTITIAL_AD_UNIT_ID",
  "GOOGLE_SIGN_IN_WEB_CLIENT_ID",
  "PLAY_GAMES_PROJECT_ID",
]) {
  requireText(
    ".github/workflows/android-signed-release.yml",
    signedReleaseWorkflow,
    `ORG_GRADLE_PROJECT_${property}=%s\\n`,
    `validated ${property} must be mapped into a Gradle project property before the signed build.`,
  );
}

requireText(
  ".github/workflows/android-signed-release.yml",
  signedReleaseWorkflow,
  "ready.ready !== true || ready.ok !== true",
  "signed release must verify that the Worker readiness response is actually ready.",
);

requireText(
  ".github/workflows/android-signed-release.yml",
  signedReleaseWorkflow,
  "environment: production",
  "signed release must read Billing, Ads, and OAuth variables from the protected production environment.",
);
requireText(
  ".github/workflows/android-signed-release.yml",
  signedReleaseWorkflow,
  "ready.productionClaim !== false",
  "signed release must fail closed unless the Worker explicitly reports productionClaim=false.",
);

if (failures.length > 0) {
  console.error("Android release-readiness gate failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(
  "Android release-readiness gate passed: version monotonicity, fail-closed secure signing, V1/V2 dual-read coverage, and explicit Ruleset V2 contract are intact.",
);
