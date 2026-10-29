import fs from "node:fs";
const read = (p) => fs.readFileSync(p, "utf8");
const root = "android/app/src/main/java/com/rekixo/ludopaws/";
const files = [
  root + "feature/home/presentation/HomeActivity.kt",
  root + "feature/home/presentation/components/HomeGuideDialog.kt",
  root + "core/navigation/GameModeNavigation.kt",
  root + "feature/safety/AgeAccessPolicy.kt",
  root + "feature/safety/PlayAgeSignalsObserver.kt",
];
const all = files.map(read).join("\n");
for (const prohibited of [/DatePickerDialog/, /DatePicker\(/, /fromBirthDate\(/,
  /enter\s+(?:your\s+)?(?:date of birth|birth date|birthday)/i,
  /putBoolean\(\s*"adult"/, /sendAgeSignalsToServer/]) {
  if (prohibited.test(all)) throw Error("DOB/unsafe-age-entry contract regression: " + prohibited);
}
const gradle = read("android/app/build.gradle.kts");
const server = read("server/src/age-access-guard.js");
if (!gradle.includes('buildConfigField("boolean", "LUDOPAWS_FAMILY_MODE", "true")') ||
    !gradle.includes('buildConfigField("boolean", "LUDOPAWS_CONNECTED_FEATURES_ENABLED", "true")'))
  throw Error("Expected restricted family multiplayer edition");
for (const file of files.slice(0, 2)) {
  if (read(file).includes("PlayAgeSignalsObserver.refreshOnce(")) throw Error("Family entry must not request age-sharing");
}
if (!server.includes("AGE_ACCESS_NOT_AVAILABLE") || !server.includes("FAMILY_FEATURE_UNAVAILABLE"))
  throw Error("Server capability boundary missing");
const friends = read(root + "feature/friends/presentation/FriendsActivity.kt");
if (friends.includes("private fun openChat(") || friends.includes('menu.add("Message")'))
  throw Error("Free-text chat UI must be removed from the family edition");
const manifest = read("android/app/src/main/AndroidManifest.xml");
for (const provider of ["com.google.android.gms.games.provider.PlayGamesInitProvider", "com.google.android.gms.ads.MobileAdsInitProvider"]) {
  if (!manifest.includes('android:name="' + provider + '" tools:node="remove"')) throw Error("SDK auto-init provider remains: " + provider);
}
console.log("PASS: DOB-free family multiplayer, no age-sharing prompt, removed free-text UI and guarded SDK initialization");

const adsPrivacy = read(root + "feature/store/data/ads/FamilyAdsPrivacy.kt");
for (const token of ["AgeRestrictedTreatment.CHILD", "MAX_AD_CONTENT_RATING_G", 'putString("npa", "1")', "setTagForUnderAgeOfConsent(true)"]) {
  if (!adsPrivacy.includes(token)) throw Error("Family advertising privacy regression: " + token);
}
for (const file of ["core/app/LudoProofApplication.kt",
    "core/navigation/GameModeNavigation.kt", "feature/store/data/ads/RewardedAdsController.kt",
    "feature/store/data/ads/PostWinInterstitialAdController.kt"]) {
  if (read(root + file).includes("FamilyAgeScreen")) throw Error("Age entry must not interrupt startup or gameplay: " + file);
}
for (const file of ["RewardedAdsController.kt", "PostWinInterstitialAdController.kt"]) {
  const source = read(root + "feature/store/data/ads/" + file);
  const init = source.slice(source.indexOf("private fun initializeAdsIfAllowed()"));
  if (init.indexOf("FamilyAdsPrivacy.configureBeforeInitialization()") < 0 ||
      init.indexOf("FamilyAdsPrivacy.configureBeforeInitialization()") > init.indexOf("MobileAds.initialize(activity)"))
    throw Error("Child treatment must precede SDK initialization: " + file);
  if (!source.includes("FamilyAdsPrivacy.request()") || !source.includes("FamilyAdsPrivacy.consentParameters()"))
    throw Error("Ad request bypasses family privacy: " + file);
}
const home = read(root + "feature/home/presentation/HomeActivity.kt");
if (!home.includes("FamilyAgeScreen.ensureEntryChoice") || home.includes("showFirstRunGuideIfNeeded"))
  throw Error("Home must use the single neutral privacy popup, without the old automatic guide");
if (read(root + "core/app/LudoProofApplication.kt").includes("startupPlayGames.initializeIfAllowed"))
  throw Error("PGS must not initialize before the Home privacy flow");
console.log("PASS: one neutral Home privacy popup; gameplay/ads retain family treatment and no shared DOB");
