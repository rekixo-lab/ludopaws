import fs from "node:fs";

const source = fs.readFileSync(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/safety/FamilyOnlineSafety.kt", "utf8",
);
const home = fs.readFileSync(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/home/presentation/components/HomeModesUi.kt", "utf8",
);
const rating = fs.readFileSync(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/home/presentation/components/HomeRatingDialogUi.kt", "utf8",
);
const test = fs.readFileSync(
  "android/app/src/test/java/com/rekixo/ludopaws/feature/safety/FamilyOnlineSafetyNoticeCopyTest.kt", "utf8",
);
for (const marker of [
  "if (!BuildConfig.LUDOPAWS_FAMILY_MODE) return false",
  '"ludopaws_family_notice_v1"',
  'prefs.getBoolean("seen", false)',
  'prefs.edit().putBoolean("seen", true).apply()',
  'text = "CONTINUE  →"',
  'text = "Cancel"',
  "if (!submitted) {",
  "dialog.dismiss()",
  "onContinue()",
  "plywoodBackground(activity, cornerRadiusDp = 22f)",
  "background = transparentDarkBlueStrokeBackground(",
  "context = activity,",
  "cornerRadiusDp = 14f,",
  "safetyContent.childCount > 0",
  "fun addTip(number: String, title: String, details: String)",
  'addTip("01"',
  'addTip("02"',
  'addTip("03"',
  "RatingSpectrumDrawable(activity)",
  "spectrum.shimmerPosition = it.animatedValue as Float",
  "shimmerAnimator?.cancel()",
  "LinearLayout.LayoutParams.MATCH_PARENT, dp(if (compact) 49 else 54)",
  "ValueAnimator.areAnimatorsEnabled()",
  "sizeDialog(dialog, if (compact) .94f else .88f)",
  "Online games connect through the Ludo Paws game server",
  "Other players see an automatic animal name",
  "Free-text chat and voice chat are not available",
  "Block or report players in Friends",
]) {
  if (!source.includes(marker)) throw new Error("Online safety notice gate missing: " + marker);
}
if (/ScrollView|PlywoodCloseButton|recessedPlywoodBackground|val header|setOnCancelListener/.test(source)) {
  throw new Error("The safety notice must have no scrolling content or header badges");
}
if (!rating.includes("internal class RatingSpectrumDrawable(context: Context) : Drawable()") ||
    !rating.includes("RatingSpectrumDrawable(this)")) {
  throw new Error("Online safety CTA must use the same RGB-shine drawable as Rating");
}
if (!home.includes("mode = GameMode.ONLINE") ||
    !home.includes("mode = GameMode.TEAM_UP") ||
    !home.includes("mode = GameMode.FRIENDS")) {
  throw new Error("Home Online / Team Up / Friends controls missing");
}
if (source.includes("AlertDialog.Builder") || source.includes("ads and purchases are unavailable")) {
  throw new Error("Old safety dialog or inaccurate ads/store statement returned");
}
for (const name of [
  "noticeExplainsPublicNamesAndPrivateNickname",
  "noticeExplainsChatRestrictionsAndPlayerControls",
  "noticeDoesNotMisstateStoreOrAdsAsUnavailable",
]) {
  if (!test.includes(name)) throw new Error("Safety notice unit test missing: " + name);
}
console.log("Ludo Paws compact non-scrolling safety notice with shared RGB Continue passed.");
