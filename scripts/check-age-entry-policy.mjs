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
