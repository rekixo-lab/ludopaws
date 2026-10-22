import fs from 'node:fs';

const required = {
  backdrop: 'android/app/src/main/java/com/rekixo/ludopaws/core/ui/art/ArcadeBackdropView.kt',
  surface: 'android/app/src/main/java/com/rekixo/ludopaws/core/ui/art/AppSurfaceBackgrounds.kt',
  plywood: 'android/app/src/main/java/com/rekixo/ludopaws/core/ui/art/PlywoodTextureDrawable.kt',
  theme: 'android/app/src/main/java/com/rekixo/ludopaws/core/ui/theme/LudoProofTheme.kt',
  offlineGameplay: 'android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/gameplay/OfflineGameplayScreenUi.kt',
  offlineSetup: 'android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/setup/OfflineSetupScreenUi.kt',
  onlineMain: 'android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/MainActivity.kt',
  onlineScreen: 'android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/MainActivityScreen.kt',
};

for (const [label, file] of Object.entries(required)) {
  if (!fs.existsSync(file)) throw new Error('Unified plywood gate: missing ' + label + ': ' + file);
}
for (const legacyImage of [
  'android/app/src/main/res/drawable-nodpi/ludo_paws_game_background.webp',
  'android/app/src/main/res/drawable-nodpi/ludo_paws_ui_background.webp',
  'android/app/src/main/res/raw/ludo_paws_background_keep.xml',
]) {
  if (fs.existsSync(legacyImage)) {
    throw new Error('Unified plywood gate: obsolete background resource still present: ' + legacyImage);
  }
}

const read = label => fs.readFileSync(required[label], 'utf8');
const backdrop = read('backdrop');
for (const marker of [
  'fun useGameplayBackground()',
  'fun useUiBackground()',
  'applySharedBackdrop()',
  'plywoodBackground(context, cornerRadiusDp = 22.5f)',
  'background !is PlywoodTextureDrawable',
]) {
  if (!backdrop.includes(marker)) throw new Error('Unified plywood gate: backdrop missing ' + marker);
}
if (!/\)\s*:\s*View\(context, attrs\)/.test(backdrop)) {
  throw new Error('Shared plywood backdrop must be a lightweight View, not an ImageView.');
}
if (/ImageView|setImageResource|getIdentifier|\.webp/.test(backdrop)) {
  throw new Error('Shared plywood backdrop must not load image resources.');
}
const gameplayEntry = backdrop.slice(backdrop.indexOf('fun useGameplayBackground()'), backdrop.indexOf('fun useUiBackground()'));
const uiEntry = backdrop.slice(backdrop.indexOf('fun useUiBackground()'), backdrop.indexOf('private fun applySharedBackdrop()'));
if (!gameplayEntry.includes('applySharedBackdrop()') || !uiEntry.includes('applySharedBackdrop()')) {
  throw new Error('Gameplay and regular UI must use the same plywood implementation.');
}
if (!read('surface').includes('internal fun plywoodBackground(') ||
    !read('plywood').includes('class PlywoodTextureDrawable')) {
  throw new Error('Backdrop must use the existing plain plywood skin.');
}
if (/RecessedPlywoodDrawable|recessedPlywoodBackground/.test(backdrop)) {
  throw new Error('Shared full-screen backdrop must not use recessed plywood.');
}
const theme = read('theme');
if (!theme.includes('ArcadeBackdropView(context)')) {
  throw new Error('Shared root must use ArcadeBackdropView.');
}
if (!read('offlineGameplay').includes('LudoProofTheme.arcadeRoot(') ||
    !read('offlineSetup').includes('LudoProofTheme.arcadeRoot(this)') ||
    !read('onlineMain').includes('buildOnlineMatchScreen()') ||
    !read('onlineScreen').includes('arcadeRootView = root')) {
  throw new Error('Offline and online screens must use the shared background root.');
}
console.log('Unified background gate passed: all UI/gameplay screens use code-drawn plain plywood; obsolete WebP background removed.');
