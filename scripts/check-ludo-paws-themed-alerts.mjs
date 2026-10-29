import fs from "node:fs";
import path from "node:path";

const appSource = "android/app/src/main/java/com/rekixo/ludopaws";
const expectedFiles = [
  "core/navigation/GameModeNavigation.kt",
  "feature/friends/presentation/FriendsActivity.kt",
  "feature/profile/presentation/ProfileActivity.kt",
  "feature/profile/presentation/components/ProfileMenuUi.kt",
  "feature/profile/presentation/components/ProfileCommonUi.kt",
  "feature/store/presentation/StoreActivity.kt",
  "feature/settings/presentation/SettingsDialogUi.kt",
  "feature/safety/FamilyAgeScreen.kt",
];
const helperPath = path.join(appSource, "core/ui/dialogs/components/PlywoodAlertDialogBuilder.kt");
const helper = fs.readFileSync(helperPath, "utf8");
const insist = (source, text, label) => {
  if (!source.includes(text)) throw new Error(`Plywood dialog gate missing ${label}: ${text}`);
};
for (const marker of [
  "internal class PlywoodAlertDialogBuilder",
  "fun setTitle(", "fun setMessage(", "fun setView(", "fun setItems(",
  "fun setPositiveButton(", "fun setNegativeButton(", "fun setNeutralButton(",
  "fun setOnCancelListener(", "fun setOnDismissListener(", "fun setCancelable(",
  "fun show(): Dialog",
  "background = plywoodBackground(context, cornerRadiusDp = 22f)",
  "PlywoodCloseButton(context",
  "background = transparentDarkBlueStrokeBackground(context, cornerRadiusDp = 12f)",
  "playGamesSignInBackground(context)",
  "intArrayOf(0xFFE14E4D.toInt(), 0xFFAD2433.toInt())",
  "onChoice?.onClick(dialog, index)",
  "action.callback?.onClick(dialog, which)",
  "dialog.dismiss()",
  "dialog.setOnCancelListener { cancelListener?.onCancel(it) }",
  "dialog.setOnDismissListener { dismissListener?.onDismiss(it) }",
  "sizeDialog(",
]) insist(helper, marker, "shared wood/blue dialog surface");

// Native listener invocation must happen before dismissal to preserve
// the Age & Privacy on-dismiss callback registry and chained dialogs.
const itemCallback = helper.indexOf("onChoice?.onClick(dialog, index)");
const itemDismiss = helper.indexOf("dialog.dismiss()", itemCallback);
const actionCallback = helper.indexOf("action.callback?.onClick(dialog, which)");
const actionDismiss = helper.indexOf("dialog.dismiss()", actionCallback);
if (itemCallback < 0 || itemDismiss < itemCallback ||
    actionCallback < 0 || actionDismiss < actionCallback) {
  throw new Error("Plywood dialog gate: callbacks must run before dialog dismissal");
}
const found = new Map();
for (const relative of expectedFiles) {
  const source = fs.readFileSync(path.join(appSource, relative), "utf8");
  if (!source.includes("PlywoodAlertDialogBuilder(")) {
    throw new Error("Plywood dialog gate: not migrated " + relative);
  }
  found.set(relative, (source.match(/PlywoodAlertDialogBuilder\(/g) ?? []).length);
}
const count = [...found.values()].reduce((a, b) => a + b, 0);
// Guest checkout opens the native Play sheet; the backup and extra confirmation dialogs were removed.
if (count < 28) {
  throw new Error(`Plywood dialog gate: expected at least 28 migrated call sites including Friends menu, found ${count}`);
}
const friends = fs.readFileSync(path.join(appSource, "feature/friends/presentation/FriendsActivity.kt"), "utf8");
insist(friends, 'setTitle("FRIEND OPTIONS")', "Friends custom options menu");
for (const action of ["Copy Friend ID", "Remove Friend", "Block Player", "Report Player"]) {
  insist(friends, action, "Friends menu action");
}
const age = fs.readFileSync(path.join(appSource, "feature/safety/FamilyAgeScreen.kt"), "utf8");
for (const marker of ['"Under 13"', '"18 or older"', '.setOnCancelListener { pending.remove(activity) }',
                      '.setOnDismissListener { pending.remove(activity) }', 'putInt(KEY, which)']) {
  insist(age, marker, "age/privacy behavior");
}
const store = fs.readFileSync(path.join(appSource, "feature/store/presentation/StoreActivity.kt"), "utf8");
for (const marker of ["billing.launchPurchase(productId)", "selectCosmetic(", "launchReadyCheckout()",
                      "PurchaseAccountSafety.canLaunch"]) {
  insist(store, marker, "guest checkout behavior");
}
const profile = fs.readFileSync(path.join(appSource, "feature/profile/presentation/ProfileActivity.kt"), "utf8");
for (const marker of ["Delete account and data?", "performAccountDeletion()",
                      "Restore another account?", "startGoogleAccountFlow("]) {
  insist(profile, marker, "account confirmation behavior");
}
function scan(directory, files = []) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) scan(full, files);
    else if (entry.name.endsWith(".kt")) files.push(full);
  }
  return files;
}
// Reject future plain Android AlertDialogs and white default PopupMenus anywhere
// in the Android app's Kotlin source; not just our existing eight migrated files.
for (const file of scan(appSource)) {
  const source = fs.readFileSync(file, "utf8");
  if (/\b(?:android\.app\.)?AlertDialog\s*(?:\.\s*)?Builder\s*\(/.test(source) ||
      /\bPopupMenu\s*\(/.test(source)) {
    throw new Error("Plywood dialog gate: native unthemed popup remains in " + file);
  }
}
console.log(`Ludo Paws premium dialogs: ${count} themed call sites verified; account, purchase and age flows guarded.`);
