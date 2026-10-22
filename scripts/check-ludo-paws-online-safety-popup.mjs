import fs from "node:fs";

const source = fs.readFileSync(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/safety/FamilyOnlineSafety.kt", "utf8",
);
const home = fs.readFileSync(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/home/presentation/components/HomeModesUi.kt", "utf8",
);
const test = fs.readFileSync(
  "android/app/src/test/java/com/rekixo/ludopaws/feature/safety/FamilyOnlineSafetyNoticeCopyTest.kt", "utf8",
);
for (const marker of [
  "if (!BuildConfig.LUDOPAWS_FAMILY_MODE) return false",
  '"ludopaws_family_notice_v1"',
  'prefs.getBoolean("seen", false)',
  "prefs.edit().putBoolean(\"seen\", true).apply()",
  "val cancel = actionButton(\"CANCEL\"",
  'actionButton("CONTINUE  →"',
  "if (!submitted) {",
  "dialog.dismiss()",
  "onContinue()",
  "plywoodBackground(activity, cornerRadiusDp = 22f)",
  "recessedPlywoodBackground(activity, outerCornerRadiusDp = 20f)",
  "PlywoodCloseButton(activity",
  "fun addTip(number: String, title: String, details: String)",
  'addTip("01"',
  'addTip("02"',
  'addTip("03"',
  "object : ScrollView(activity)",
  "ValueAnimator.areAnimatorsEnabled()",
  "sizeDialog(dialog, if (compact) .94f else .88f)",
  "LinearLayout.LayoutParams(0, dp(54), 1.65f)",
  "Online games connect through the Ludo Paws game server",
  "Other players see an automatic animal name",
  "Free-text chat and voice chat are not available",
  "Block or report players in Friends",
]) {
  if (!source.includes(marker)) throw new Error("Online safety notice gate missing: " + marker);
}
if (source.includes("AlertDialog.Builder") || source.includes("ads and purchases are unavailable")) {
  throw new Error("Old safety dialog or outdated store/ads claim returned");
}
if (!home.includes("mode = GameMode.ONLINE") ||
    !home.includes("mode = GameMode.TEAM_UP") ||
    !home.includes("mode = GameMode.FRIENDS")) {
  throw new Error("Home Online / Team Up / Friends controls missing");
}
for (const name of [
  "noticeExplainsPublicNamesAndPrivateNickname",
  "noticeExplainsChatRestrictionsAndPlayerControls",
  "noticeDoesNotMisstateStoreOrAdsAsUnavailable",
]) {
  if (!test.includes(name)) throw new Error("Safety notice unit test missing: " + name);
}
console.log("Ludo Paws premium family online safety notice gate passed.");
