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
  "cornerRadiusDp = 11f,", "cornerRadiusDp = 999f,",
  'PlywoodCloseButton(context, contentDescription = "Close settings")',
  "settingsCompactRow(", "settingsDropdownField(", "SettingsBooleanControl(",
  "setTextColor(0xFFFFD34E.toInt())", "setTextColor(Color.WHITE)",
  "background = null", "elevation = 0f",
], "visual kit");
expectAll(dialog, [
  'label = "Music"', 'label = "Sound"', 'label = "Quick chat"',
  'label = "Game Speed"', 'label = "Boards"', 'label = "Dice"',
  'text = "Privacy Policy"', 'text = "How to Play"',
  "settingsDropdownField(context, \"Privacy Policy\")",
  "settingsDropdownField(context, \"How to Play\")",
  "setMusicEnabled(", "setSoundEnabled(", "setQuickChatEnabled(",
  "setGameSpeed(", "selectSettingsCosmetic(", "showSettingsChoiceDialog(",
  "SETTINGS_CONTENT_BUILD_DELAY_MS",
], "behavior");
if (dialog.includes("panel.addView(settingsDivider(context))") ||
    dialog.includes("panel.addView(\n            settingsDivider(context),")) {
  throw new Error("Settings popup: old stacked thin dividers remain");
}
expectAll(audio, ["0x33104483", "0xFF247FCB.toInt()", "stroke.color = Color.WHITE", "fill.color = Color.WHITE", "SettingsAudioIconKind.MUSIC", "SettingsAudioIconKind.SOUND"], "audio icons");
const matchCross = ["0xFF63351E.toInt()", "0xFFE0463B.toInt()", "0xFFFFC4A1.toInt()",
  "7.4f * density", "5.1f * density", "1.3f * density", "7.5f * density",
  "outerCornerRadiusDp = 24f", "cross(outline)", "cross(red)", "cross(glint)"];
expectAll(close, matchCross, "red close button");
expectAll(rating, matchCross.filter(token => token !== "outerCornerRadiusDp = 24f"), "rating-style reference");

const rowStart = kit.indexOf("internal fun settingsCompactRow(");
const rowEnd = kit.indexOf("internal fun settingsDivider(", rowStart);
const dropdownStart = kit.indexOf("internal fun settingsDropdownField(");
const dropdownEnd = kit.indexOf("internal fun settingsDropdownLabel(", dropdownStart);
const toggleStart = kit.indexOf("internal class SettingsBooleanControl(");
const toggleEnd = kit.indexOf("internal fun settingsPrivacyLink(", toggleStart);
const headerStart = kit.indexOf("private fun settingsHeader(");
const headerEnd = kit.indexOf("internal fun settingsCompactRow(", headerStart);
for (const [start, end, name] of [
  [rowStart, rowEnd, "flat settings rows"],
  [dropdownStart, dropdownEnd, "dropdown fields"],
  [toggleStart, toggleEnd, "quick chat switch"],
  [headerStart, headerEnd, "settings header"],
]) {
  if (start < 0 || end <= start) throw new Error("Settings popup: missing section " + name);
}
const row = kit.slice(rowStart, rowEnd);
const dropdown = kit.slice(dropdownStart, dropdownEnd);
const toggle = kit.slice(toggleStart, toggleEnd);
const header = kit.slice(headerStart, headerEnd);
expectAll(row, ["background = null", "elevation = 0f", "setTextColor(Color.WHITE)"], "plain label rows");
if (/transparentDarkBlueStrokeBackground|GradientDrawable|setStroke\(/.test(row)) {
  throw new Error("Settings popup: blue background must NOT cover entire settings rows");
}
expectAll(dropdown, ["background = transparentDarkBlueStrokeBackground(", "setTextColor(Color.WHITE)"], "blue dropdown only");
expectAll(toggle, ["background = transparentDarkBlueStrokeBackground(", "render()"], "switch only");
expectAll(header, ["setTextColor(0xFFFFD34E.toInt())", "PlywoodCloseButton("], "original gold Settings title");
expectAll(dialog, ["setTextColor(android.graphics.Color.WHITE)", 'text = "Privacy Policy"', 'text = "How to Play"'], "white footer buttons");
console.log("Ludo Paws clean Settings popup: gold title, flat rows, white icons/text and blue controls passed.");
