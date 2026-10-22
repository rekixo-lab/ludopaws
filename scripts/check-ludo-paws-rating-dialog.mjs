import fs from "node:fs";

const root = "android/app/src/main/java/com/rekixo/ludopaws/feature/home/presentation/components/HomeRatingDialogUi.kt";
const source = fs.readFileSync(root, "utf8");
const test = fs.readFileSync("android/app/src/test/java/com/rekixo/ludopaws/HomeRatingAnimationPolicyTest.kt", "utf8");
const home = fs.readFileSync("android/app/src/main/java/com/rekixo/ludopaws/feature/home/presentation/components/HomeBottomActionsUi.kt", "utf8");

for (const token of [
  "showHomeRatingDialog()",
  "GameSettingsStore(this).snapshot().reduceMotionEnabled",
  "RatingStarFillPolicy.fraction(displayedProgress, index)",
  "canvas.clipPath(star)",
  "canvas.clipPath(fluid)",
  "RatingLiquidFillPolicy.surfaceY(fillFraction)",
  "RatingLiquidFillPolicy.waveHeight(fillFraction)",
  "canvas.drawPath(surface, waveHighlight)",
  "dialog.show()",
  "sizeDialog(dialog, .88f)",
  "LinearLayout.LayoutParams(0, dp(if (compact) 43 else 47), 1f)",
  "duration = if (intro) 2500L",
  "ValueAnimator.ofFloat(displayedProgress, destination)",
  "OvershootInterpolator",
  "shimmerAnimator?.cancel()",
  "dialog.setOnDismissListener",
  "setSingleLine(true)",
  "rateAction?.contentDescription",
  "spectrum.shimmerPosition = it.animatedValue as Float",
  "HomeGooglePlayIconView(",
  "ctaText.text = if (selectedRating == 1)",
  "dialog.dismiss()",
  "openPlayStoreRating(this@showHomeRatingDialog)",
  "Uri.parse(",
  "market://details?id=",
  "https://play.google.com/store/apps/details?id=",
  "contentDescription = \"Close rating dialog\"",
  "contentDescription = \"Not now\"",
]) {
  if (!source.includes(token)) throw new Error("Rating popup gate missing: " + token);
}
if (!home.includes("showHomeRatingDialog()")) throw new Error("Home rating entry point disconnected");
if (!test.includes("starsFillOneByOneLeftToRight") ||
    !test.includes("clampsFilledStarBoundaries") ||
    !test.includes("liquidGoldLevelRisesFromStarBottomToTop") ||
    !test.includes("liquidWaveSettlesOnceTheStarIsFull")) throw new Error("Missing rating fill regression tests");
if (source.includes("isFilled:") || source.includes("ratingPrimaryButtonDrawable(")) {
  throw new Error("Old instant-filled rating UI is still present");
}
if (source.includes("canvas.clipRect(0f, 0f, 100f * fillFraction")) {
  throw new Error("Rating popup gate: must use a rising liquid surface, not the old horizontal wipe");
}
console.log("Ludo Paws compact liquid-gold rating popup gate passed.");
