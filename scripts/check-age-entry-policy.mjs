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
const observer = read(files[4]);
const gradle = read("android/app/build.gradle.kts");
const server = read("server/src/age-access-guard.js");
if (!observer.includes("AgeSignalsStatus.SHARED") ||
    !observer.includes("AgeAccessPolicy.fromPlayRange") ||
    !observer.includes("AgeSignalsManagerFactory.create") ||
    !observer.includes("requestAgeSignalsAccess") ||
    !observer.includes("checkAgeSignals")) throw Error("Google Play access/check contract missing");
if (!gradle.includes('com.google.android.play:age-signals:0.0.4')) throw Error("Expected vetted Play SDK");
if (!gradle.includes('buildConfigField("boolean", "LUDOPAWS_CONNECTED_FEATURES_ENABLED", "false")'))
  throw Error("No independently approved multiplayer eligibility");
if (!server.includes("AGE_ACCESS_NOT_AVAILABLE")) throw Error("Server fail-closed lockdown missing");
if (!read(files[0]).includes("PlayAgeSignalsObserver.refreshOnce(this)"))
  throw Error("Home no longer checks Google Play age-sharing at entry");
console.log("PASS: no DOB UI, Google-managed age sharing, safe offline lockdown");
