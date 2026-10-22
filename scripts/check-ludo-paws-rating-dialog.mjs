import fs from "node:fs";

const root = "android/app/src/main/java/com/rekixo/ludopaws/feature/home/presentation/components/HomeRatingDialogUi.kt";
const source = fs.readFileSync(root, "utf8");
const test = fs.readFileSync("android/app/src/test/java/com/rekixo/ludopaws/HomeRatingAnimationPolicyTest.kt", "utf8");
const home = fs.readFileSync("android/app/src/main/java/com/rekixo/ludopaws/feature/home/presentation/components/HomeBottomActionsUi.kt", "utf8");

for (const token of [
  "showHomeRatingDialog()",
  "GameSettingsStore(this).snapshot().reduceMotionEnabled",
  "RatingStarFillPolicy.fraction(displayedProgress, index)",
  "canvas.clipRect(0f, 0f, 100f * fillFraction, 100f)",
  "duration = if (intro) 1750L",
  "ValueAnimator.ofFloat(displayedProgress, destination)",
  "OvershootInterpolator",
  "shimmerAnimator?.cancel()",
  "dialog.setOnDismissListener",
  "spectrum.shimmerPosition = it.animatedValue as Float",
  "HomeGooglePlayIconView(",
  "ctaText.text = if (selectedRating == 1)",
  "dialog.dismiss()",
  "openPlayStoreRating(this)",
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
    !test.includes("clampsFilledStarBoundaries")) throw new Error("Missing rating fill regression tests");
if (source.includes("isFilled:") || source.includes("ratingPrimaryButtonDrawable(")) {
  throw new Error("Old instant-filled rating UI is still present");
}
console.log("Ludo Paws premium rating dialog gate passed.");
