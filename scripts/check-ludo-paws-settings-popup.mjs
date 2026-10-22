import fs from "node:fs";

const root = "android/app/src/main/java/com/rekixo/ludopaws/";
const kit = fs.readFileSync(root + "feature/settings/presentation/SettingsVisualKit.kt", "utf8");
const dialog = fs.readFileSync(root + "feature/settings/presentation/SettingsDialogUi.kt", "utf8");
const audio = fs.readFileSync(root + "feature/settings/presentation/SettingsAudioIconView.kt", "utf8");
const close = fs.readFileSync(root + "core/ui/components/PlywoodCloseButton.kt", "utf8");
const rating = fs.readFileSync(root + "feature/home/presentation/components/HomeRatingDialogUi.kt", "utf8");
const surface = fs.readFileSync(root + "core/ui/art/AppSurfaceBackgrounds.kt", "utf8");

const expectAll = (source, tokens, where) => {
  for (const token of tokens) {
    if (!source.includes(token)) throw new Error(`Settings popup ${where} missing: ${token}`);
  }
};
expectAll(surface, ['0x33104483', '0xFF247FCB.toInt()'], "shared blue palette");
expectAll(kit, [
  "import com.rekixo.ludopaws.core.ui.art.transparentDarkBlueStrokeBackground",
  "background = transparentDarkBlueStrokeBackground(",
  "cornerRadiusDp = 13f,", "cornerRadiusDp = 11f,", "cornerRadiusDp = 999f,",
  'PlywoodCloseButton(context, contentDescription = "Close settings")',
  "settingsCompactRow(", "settingsDropdownField(", "SettingsBooleanControl(",
], "visual kit");
expectAll(dialog, [
  'label = "Music"', 'label = "Sound"', 'label = "Quick chat"',
  'label = "Game Speed"', 'label = "Boards"', 'label = "Dice"',
  'text = "Privacy Policy"', 'text = "How to Play"',
  "settingsDropdownField(context, \"Privacy Policy\")",
  "settingsDropdownField(context, \"How to Play\")",
  "settingsStore.set", "selectSettingsCosmetic(", "showSettingsChoiceDialog(",
  "SETTINGS_CONTENT_BUILD_DELAY_MS",
], "behavior");
if (dialog.includes("panel.addView(settingsDivider(context))") ||
    dialog.includes("panel.addView(\n            settingsDivider(context),")) {
  throw new Error("Settings popup: old stacked thin dividers remain");
}
expectAll(audio, ["0x33104483", "0xFF247FCB.toInt()", "SettingsAudioIconKind.MUSIC", "SettingsAudioIconKind.SOUND"], "audio icons");
const matchCross = ["0xFF63351E.toInt()", "0xFFE0463B.toInt()", "0xFFFFC4A1.toInt()",
  "7.4f * density", "5.1f * density", "1.3f * density", "7.5f * density",
  "outerCornerRadiusDp = 24f", "cross(outline)", "cross(red)", "cross(glint)"];
expectAll(close, matchCross, "red close button");
expectAll(rating, matchCross.filter(token => token !== "outerCornerRadiusDp = 24f"), "rating-style reference");
console.log("Ludo Paws premium compact blue Settings popup styling gate passed.");
