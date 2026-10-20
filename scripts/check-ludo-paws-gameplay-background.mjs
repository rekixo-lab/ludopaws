import fs from 'node:fs';

const required = {
  asset: 'android/app/src/main/res/drawable-nodpi/ludo_paws_game_background.webp',
  backdrop: 'android/app/src/main/java/com/rekixo/ludopaws/core/ui/art/ArcadeBackdropView.kt',
  theme: 'android/app/src/main/java/com/rekixo/ludopaws/core/ui/theme/LudoProofTheme.kt',
  offlineGameplay: 'android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/gameplay/OfflineGameplayScreenUi.kt',
  offlineSetup: 'android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/setup/OfflineSetupScreenUi.kt',
  onlineMain: 'android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/MainActivity.kt',
  onlineScreen: 'android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/MainActivityScreen.kt',
  keep: 'android/app/src/main/res/raw/ludo_paws_background_keep.xml',
};

const removedLegacyAsset = 'android/app/src/main/res/drawable-nodpi/ludo_paws_ui_background.webp';

for (const [label, file] of Object.entries(required)) {
  if (!fs.existsSync(file)) throw new Error(`Unified background gate: missing ${label}: ${file}`);
}
if (fs.existsSync(removedLegacyAsset)) {
  throw new Error('The old ludo_paws_ui_background.webp must be removed; all screens must use the shared gameplay background.');
}

const read = label => fs.readFileSync(required[label], 'utf8');
const asset = fs.readFileSync(required.asset);
if (asset.length < 1000 || asset.length > 1_000_000) {
  throw new Error(`Shared background must be optimized (1 KB–1 MB); got ${asset.length} bytes.`);
}
if (
  asset.toString('ascii', 0, 4) !== 'RIFF' ||
  asset.toString('ascii', 8, 12) !== 'WEBP'
) {
  throw new Error('Shared background asset is not a valid RIFF/WEBP file.');
}

const backdrop = read('backdrop');
for (const marker of ['useGameplayBackground()', 'useUiBackground()', 'applySharedBackdrop()', 'ludo_paws_game_background']) {
  if (!backdrop.includes(marker)) throw new Error(`Shared backdrop implementation is missing ${marker}`);
}
if (backdrop.includes('ludo_paws_ui_background')) {
  throw new Error('Backdrop source must not reference the removed UI background.');
}
const gameplayEntry = backdrop.slice(backdrop.indexOf('fun useGameplayBackground()'), backdrop.indexOf('fun useUiBackground()'));
const uiEntry = backdrop.slice(backdrop.indexOf('fun useUiBackground()'), backdrop.indexOf('@Suppress("DiscouragedApi")'));
if (!gameplayEntry.includes('applySharedBackdrop()') || !uiEntry.includes('applySharedBackdrop()')) {
  throw new Error('Gameplay and regular UI must use the same backdrop implementation.');
}

const theme = read('theme');
if (!theme.includes('ArcadeBackdropView(context)')) {
  throw new Error('Shared root must use ArcadeBackdropView for its full-screen background.');
}
if (!read('offlineGameplay').includes('LudoProofTheme.arcadeRoot(')) {
  throw new Error('Offline gameplay must use the shared root background.');
}
if (!read('offlineSetup').includes('LudoProofTheme.arcadeRoot(this)')) {
  throw new Error('Offline setup must use the same shared root background.');
}
if (
  !read('onlineMain').includes('buildOnlineMatchScreen()') ||
  !read('onlineScreen').includes('LudoProofTheme') ||
  !read('onlineScreen').includes('arcadeRootView = root')
) {
  throw new Error('Online Activity must compose its screen with the shared root background.');
}

const keep = read('keep');
if (!keep.includes('@drawable/ludo_paws_game_background')) {
  throw new Error('Release resource keep list is missing the shared background.');
}
if (keep.includes('@drawable/ludo_paws_ui_background')) {
  throw new Error('Release resource keep list must not refer to the removed background.');
}

console.log(`Unified background gate passed: WebP asset (${asset.length} bytes), shared by UI/gameplay, legacy asset removed.`);
