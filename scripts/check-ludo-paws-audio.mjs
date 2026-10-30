import fs from 'node:fs';
import path from 'node:path';
import { parseCharacterAudioProfiles } from './ludopaws-content-audio-tools.mjs';

const requiredSources = [
  'android/app/src/main/java/com/rekixo/ludopaws/core/audio/LudoPawsAudioCatalog.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/core/audio/LudoPawsSoundPool.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/settings/data/local/GameSoundFeedback.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/settings/data/local/GameMusicController.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/settings/data/local/GameSettingsStore.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/settings/data/local/LudoPawsHaptics.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/data/audio/LudoPawsVoicePlayer.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/data/audio/LudoPawsAudioAssetPlayer.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/data/audio/LudoPawsProceduralAudio.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/audio/LudoPawsReactionAudioProfile.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/settings/presentation/SettingsDialogUi.kt',
  'android/app/src/main/AndroidManifest.xml',
  'android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsMovementSoundPolicy.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPaws3DSceneView.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsGameplayPacingPolicy.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/OnlineMatchActions.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/OnlineLudoPawsPresentation.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/feedback/OfflineLudoPawsFeedback.kt',
];

const reactionFeedbackOwners = [
  'android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/OnlineLudoPawsPresentation.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/feedback/OfflineLudoPawsFeedback.kt',
];
const feedbackLedger =
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsFeedbackLedger.kt';
const reactiveBoard =
  'android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsReactiveBoardView.kt';
const deletedSpokenLines =
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsVoiceLines.kt';

for (const file of [...requiredSources, ...reactionFeedbackOwners, feedbackLedger, reactiveBoard]) {
  if (!fs.existsSync(file)) {
    throw new Error(`Missing production audio source: ${file}`);
  }
}
if (fs.existsSync(deletedSpokenLines)) {
  throw new Error('Spoken character commentary file must stay removed.');
}

const audioCatalog = fs.readFileSync(requiredSources[0], 'utf8');
const soundPool = fs.readFileSync(requiredSources[1], 'utf8');
const sfx = fs.readFileSync(requiredSources[2], 'utf8');
const music = fs.readFileSync(requiredSources[3], 'utf8');
const settings = fs.readFileSync(requiredSources[4], 'utf8');
const haptics = fs.readFileSync(requiredSources[5], 'utf8');
const voice = fs.readFileSync(requiredSources[6], 'utf8');
const resolver = fs.readFileSync(requiredSources[7], 'utf8');
const procedural = fs.readFileSync(requiredSources[8], 'utf8');
const profile = fs.readFileSync(requiredSources[9], 'utf8');
const settingsUi = fs.readFileSync(requiredSources[10], 'utf8');
const manifest = fs.readFileSync(requiredSources[11], 'utf8');
const ledgerSource = fs.readFileSync(feedbackLedger, 'utf8');
const movementPolicy = fs.readFileSync(requiredSources[12], 'utf8');
const movementScene = fs.readFileSync(requiredSources[13], 'utf8');
const pacingPolicy = fs.readFileSync(requiredSources[14], 'utf8');
const onlineActionsSource = fs.readFileSync(requiredSources[15], 'utf8');
const onlinePresentationSource = fs.readFileSync(requiredSources[16], 'utf8');
const offlineFeedbackSource = fs.readFileSync(requiredSources[17], 'utf8');

for (const marker of [
  'MOVE_JUMP',
  'audio/sfx/gameplay/movement/jump/lp_sfx_jump_01.wav',
  'MOVE_STEP',
  'audio/sfx/gameplay/movement/step/lp_sfx_step_01.wav',
  'MOVE_STEP_DOG',
  'MOVE_STEP_GOAT',
  'MOVE_STEP_DUCK',
  'MOVE_STEP_CAT',
  'audio/sfx/gameplay/six/lp_sfx_six_01.wav',
  'audio/sfx/gameplay/dice/settle/lp_sfx_dice_settle_01.wav',
  'audio/sfx/gameplay/exact_home_miss/lp_sfx_exact_home_miss_01.wav',
  'DICE_SETTLE',
  'EXACT_HOME_MISS',
  'SIX',
]) {
  if (!audioCatalog.includes(marker)) throw new Error(`Audio catalog is missing ${marker}`);
}
for (const marker of ['SoundPool', 'USAGE_GAME', 'CONTENT_TYPE_SONIFICATION', 'pendingByKey']) {
  if (!soundPool.includes(marker)) throw new Error(`SoundPool engine is missing ${marker}`);
}
for (const marker of [
  'lp_sfx_dice_roll',
  'lp_sfx_dice_settle',
  'lp_sfx_exact_home_miss',
  'fun moveStep(',
  'fun jump(',
  'PRIORITY_CRITICAL',
  'lp_sfx_move_paw',
  'lp_sfx_move_hoof',
  'lp_sfx_move_web',
  'lp_sfx_capture_impact',
  'lp_sfx_safe_shimmer',
  'lp_sfx_home_lane',
  'lp_sfx_home_sparkle',
  'lp_sfx_third_six',
  'fun reaction(',
]) {
  if (!sfx.includes(marker)) throw new Error(`Replaceable game SFX facade is missing ${marker}`);
}
if (sfx.includes('ToneGenerator')) {
  throw new Error('Production SFX must not use one-ToneGenerator-per-event feedback.');
}

for (const marker of ['duckForVoice', 'AudioFocusRequest', 'AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK', 'audio/music', 'ludoproof_theme', 'createAssetPlayer', 'duplicateMusicFilenameIndex']) {
  if (!music.includes(marker)) throw new Error(`Music/audio-focus controller is missing ${marker}`);
}
for (const marker of ['animalVoicesEnabled', 'hapticsEnabled']) {
  if (!settings.includes(marker)) throw new Error(`Settings store is missing ${marker}`);
}
// The compact Settings design intentionally consolidates audio preferences into
// one user-facing Sound row; the underlying voice/haptics preferences remain stored.
for (const marker of ['Music', 'Sound', 'Quick chat', 'Game Speed', 'Boards', 'Dice', 'setAnimalVoicesEnabled', 'ScrollView']) {
  if (!settingsUi.includes(marker)) throw new Error(`Settings UI is missing ${marker}`);
}

for (const forbidden of [
  'import android.speech.tts.TextToSpeech',
  'TextToSpeech(',
  'Six! Let',
  'Got you!',
  'Back to the yard',
  'Home sweet home',
  'I win!',
  'Good game.',
]) {
  if (voice.includes(forbidden)) {
    throw new Error(`Animal reactions must remain non-verbal; found ${forbidden}`);
  }
}
for (const marker of [
  'LudoPawsReactionAudioProfile',
  'LudoPawsAudioAssetPlayer',
  'animalVoicesEnabled',
  'canonicalCharacterForSeat',
  'MAX_CAPTURE_VOCALS',
]) {
  if (!voice.includes(marker)) throw new Error(`Animal vocal renderer is missing ${marker}`);
}

for (const marker of [
  'getIdentifier',
  'LudoPawsSoundPool',
  'playVocal',
  'playSfx',
  'playAuthoredSfx',
  'availableAssetPaths',
  'audio/sfx/voices/',
  'nextSfxVariantByFamily',
  'duplicateFilenameIndex',
  'filenamePattern',
  'assets.list(directory)',
]) {
  if (!resolver.includes(marker)) throw new Error(`Audio resource resolver is missing ${marker}`);
}

const movementStepStart = sfx.indexOf('fun moveStep(');
const jumpStart = sfx.indexOf('    /** Plays at hop take-off', movementStepStart);
if (movementStepStart < 0 || jumpStart < 0) {
  throw new Error('Movement SFX must define distinct step and jump entry points.');
}
if (sfx.slice(movementStepStart, jumpStart).includes('MOVE_JUMP')) {
  throw new Error('Normal cell landing must never resolve the jump asset family.');
}

for (const marker of [
  'movementAudioNextHop',
  'GameSoundFeedback.jump(context)',
  'GameSoundFeedback.moveStep(',
  'Coalesce missed hops into one cue',
  'play only the newest one instead of bursting old cues',
  'latestLandmark?.second?.let',
]) {
  if (!movementScene.includes(marker)) {
    throw new Error(`Movement audio clock is missing ${marker}`);
  }
}
for (const retiredThreshold of [
  'MAX_HOP_AUDIO_LATENESS_MILLIS',
  'MAX_LANDING_AUDIO_LATENESS_MILLIS',
]) {
  if (movementScene.includes(retiredThreshold)) {
    throw new Error(
      `Movement audio must coalesce missed-frame cues instead of discarding them at ${retiredThreshold}.`,
    );
  }
}
for (const marker of [
  'captureContactDelayMillis',
  'forwardAnimationDurationMillis',
]) {
  if (!pacingPolicy.includes(marker)) {
    throw new Error(`Movement/capture pacing is missing ${marker}`);
  }
}
const captureBoardSource = fs.readFileSync(reactiveBoard, 'utf8');
for (const marker of [
  'captureContactDelaysByEvent',
  'captureContactDelayMillis = captureContactDelayFor(reactions)',
]) {
  if (!captureBoardSource.includes(marker)) {
    throw new Error(`Capture voice timing is missing ${marker}`);
  }
}
for (const marker of ['AudioTrack', 'ENCODING_PCM_16BIT', 'DOG_YIP', 'GOAT_BLEAT', 'DUCK_QUACK', 'CAT_CHIRP', 'Sfx.DICE_SETTLE', 'Sfx.EXACT_HOME_MISS']) {
  if (!procedural.includes(marker)) throw new Error(`Procedural fallback is missing ${marker}`);
}
for (const marker of [
  'lp_vocal_${characterId}_${cue.wireName}',
  'probabilityPercent',
  'deterministicVariantIndex',
  'DOG_WHINE',
  'GOAT_SOFT_BLEAT',
  'DUCK_SOFT_QUACK',
  'CAT_PURR',
]) {
  if (!profile.includes(marker)) throw new Error(`Reaction audio profile is missing ${marker}`);
}

for (const marker of ['class LudoPawsFeedbackLedger', 'LinkedHashSet', 'fun once(', 'fun filterReactions(']) {
  if (!ledgerSource.includes(marker)) throw new Error(`Exactly-once feedback ledger is missing ${marker}`);
}

const onlineRollAnimationIndex = onlineActionsSource.indexOf('diceView.startRolling()');
const onlineRollLedgerIndex = onlineActionsSource.indexOf('OnlineLudoPawsPresentation.noteLocalRollStarted(this, code, state.randomEventIndex)');
const onlineRollSoundIndex = onlineActionsSource.indexOf('GameSoundFeedback.roll(this, state.randomEventIndex)');
if (
  onlineRollAnimationIndex < 0 ||
  onlineRollLedgerIndex < onlineRollAnimationIndex ||
  onlineRollSoundIndex < onlineRollLedgerIndex
) {
  throw new Error('Online roll must start its animation, record the local event once, then play the event-indexed roll sound.');
}
if (!onlinePresentationSource.includes('GameSoundFeedback.diceSettle(activity, eventIndex)')) {
  throw new Error('Online committed roll must use the event-indexed dice-settle cue, not restart the roll rattle.');
}
for (const marker of [
  'GameSoundFeedback.diceSettle(context)',
  'OfflineFeedbackSound.EXACT_HOME_MISS -> GameSoundFeedback.exactHomeMiss(context)',
]) {
  if (!offlineFeedbackSource.includes(marker)) {
    throw new Error('Offline audio mapping is missing ' + marker);
  }
}
const forbiddenSecondaryFeedbackCalls = ['GameSoundFeedback.reaction', 'LudoPawsHaptics.reaction'];
for (const marker of forbiddenSecondaryFeedbackCalls) {
  if (voice.includes(marker)) {
    throw new Error(`Animal vocal renderer must remain vocal-only; ${marker} belongs to mode dispatchers.`);
  }
}
const boardSource = fs.readFileSync(reactiveBoard, 'utf8');
for (const marker of forbiddenSecondaryFeedbackCalls) {
  if (boardSource.includes(marker)) {
    throw new Error(`Reactive board must not become a second feedback owner: ${marker}`);
  }
}
for (const file of reactionFeedbackOwners) {
  const source = fs.readFileSync(file, 'utf8');
  for (const marker of ['GameSoundFeedback.', 'LudoPawsHaptics.reaction', 'LudoPawsFeedbackLedger', '.once(']) {
    if (!source.includes(marker)) throw new Error(`Mode feedback owner ${file} is missing ${marker}`);
  }
}

if (!haptics.includes('hapticsEnabled') || !haptics.includes('VibrationEffect')) {
  throw new Error('Haptics must be preference-gated and use VibrationEffect.');
}
if (!manifest.includes('android.permission.VIBRATE')) {
  throw new Error('Haptics require the VIBRATE manifest permission.');
}

const forbiddenGameplayMarkers = ['OfflineGameEngine(', 'session.roll(', 'session.move(', 'EntroNexV4Local'];
for (const file of [requiredSources[1], requiredSources[2], requiredSources[5], requiredSources[6], requiredSources[7], requiredSources[8], requiredSources[9]]) {
  const source = fs.readFileSync(file, 'utf8');
  for (const marker of forbiddenGameplayMarkers) {
    if (source.includes(marker)) {
      throw new Error(`Audio presentation layer must not mutate gameplay/proof state: ${file} contains ${marker}`);
    }
  }
}

const authoredAudioReadme = 'audio/README.md';
const jumpAudioReadme = 'audio/sfx/gameplay/movement/jump/README.md';
const stepAudioReadme = 'audio/sfx/gameplay/movement/step/README.md';
const authoredAudioSlots = [
  authoredAudioReadme,
  jumpAudioReadme,
  stepAudioReadme,
  // Species-specific footfalls are optional overrides. Missing/empty folders
  // are valid because the resolver falls back to the shared step family.
  'audio/sfx/gameplay/six/.gitkeep',
  'audio/sfx/gameplay/dice/settle/.gitkeep',
  'audio/sfx/gameplay/exact_home_miss/.gitkeep',
];
for (const file of authoredAudioSlots) {
  if (!fs.existsSync(file)) throw new Error(`Missing authored audio folder/guide slot: ${file}`);
}

// Validate real authored audio on every CI run. .gitkeep and documentation are
// ignored; bogus zero-byte WAVs and mislabeled OGGs should fail before packaging.
function listAudioFiles(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) return listAudioFiles(entryPath);
    const supportedAudioExtension =
      directory === 'audio/music' ? /\.(wav|ogg|mp3)$/i : /\.(wav|ogg)$/i;
    return supportedAudioExtension.test(entry.name) ? [entryPath] : [];
  });
}

const authoredAudioFiles = [
  ...listAudioFiles('audio/sfx'),
  ...listAudioFiles('audio/music'),
];

// Every physical gameplay/UI clip must be addressable by the runtime catalog.
// Windows duplicate suffixes like " (3)" are aliases of the canonical filename,
// matching LudoPawsAudioAssetPlayer.resolveAssetPaths().
const catalogSfxPaths = [
  ...audioCatalog.matchAll(/["'](audio\/sfx\/(?:gameplay|ui)\/[^"']+\.(?:wav|ogg))["']/gi),
].map(match => match[1]);
// Later registered species construct their own three step paths at runtime.
// Validate those registered slots as well as the original static catalog paths.
const registeredProfiles = parseCharacterAudioProfiles(fs.readFileSync(
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/audio/GeneratedCharacterAudioProfiles.kt', 'utf8',
));
for (const { id } of registeredProfiles) {
  for (let take = 1; take <= 3; take += 1) {
    catalogSfxPaths.push(`audio/sfx/gameplay/movement/step/${id}/lp_sfx_step_${id}_${String(take).padStart(2, '0')}.wav`);
  }
}

const normalizeRepoPath = file => file.split(path.sep).join('/');

function catalogResolvesAsset(actualPath, expectedPath) {
  const actual = normalizeRepoPath(actualPath);
  const expected = normalizeRepoPath(expectedPath);
  if (path.posix.dirname(actual) !== path.posix.dirname(expected)) return false;

  const expectedName = path.posix.basename(expected);
  const actualName = path.posix.basename(actual)
    .replace(/Lumi Goat/gi, 'goat_lumi')
    .replace(/\.wav\.wav$/i, '.wav');
  if (actualName.toLowerCase() === expectedName.toLowerCase()) return true;

  const extension = path.posix.extname(expectedName);
  const expectedStem = expectedName.slice(0, -extension.length).toLowerCase();
  const duplicate = actualName.match(/^(.*) \((\d+)\)(\.[^.]+)$/);
  return Boolean(
    duplicate &&
    duplicate[1].toLowerCase() === expectedStem &&
    duplicate[3].toLowerCase() === extension.toLowerCase(),
  );
}

const unregisteredSfxAssets = authoredAudioFiles
  .map(normalizeRepoPath)
  .filter(file =>
    /^audio\/sfx\/(?:gameplay|ui)\//i.test(file) &&
    !catalogSfxPaths.some(expected => catalogResolvesAsset(file, expected)),
  );
if (unregisteredSfxAssets.length > 0) {
  throw new Error(
    `Authored gameplay/UI SFX cannot be resolved by LudoPawsAudioCatalog: ${unregisteredSfxAssets.join(', ')}` ,
  );
}
for (const file of authoredAudioFiles) {
  const bytes = fs.readFileSync(file);
  const lower = file.toLowerCase();
  if (lower.endsWith('.wav')) {
    if (
      bytes.length < 44 ||
      bytes.toString('ascii', 0, 4) !== 'RIFF' ||
      bytes.toString('ascii', 8, 12) !== 'WAVE'
    ) {
      throw new Error(`Invalid or truncated RIFF/WAVE asset: ${file}`);
    }
  } else if (lower.endsWith('.ogg')) {
    if (
      bytes.length < 4 ||
      bytes.toString('ascii', 0, 4) !== 'OggS'
    ) {
      throw new Error(`Invalid or truncated OGG asset: ${file}`);
    }
  } else if (lower.endsWith('.mp3')) {
    const hasId3Header = bytes.toString('ascii', 0, 3) === 'ID3';
    const hasMpegFrameSync =
      bytes.length >= 2 && bytes[0] === 0xff && (bytes[1] & 0xe0) === 0xe0;
    // Preserve the submitted music download's name. MediaPlayer detects its
    // container from content; this author supplied PCM WAVE under an MP3 name.
    const hasWaveContainer = file.startsWith('audio/music/') && bytes.length >= 44 &&
      bytes.toString('ascii', 0, 4) === 'RIFF' && bytes.toString('ascii', 8, 12) === 'WAVE';
    if (!hasId3Header && !hasMpegFrameSync && !hasWaveContainer) {
      throw new Error(`Invalid or truncated MP3 asset: ${file}`);
    }
  }
}

console.log(`Ludo Paws non-verbal replaceable audio gate passed; validated ${authoredAudioFiles.length} authored audio file(s).`);
