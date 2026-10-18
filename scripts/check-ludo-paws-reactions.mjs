import fs from 'node:fs';

const requiredFiles = [
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsReactionEngine.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsGameMomentDetector.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsReactionDirector.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/data/audio/LudoPawsVoicePlayer.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/audio/LudoPawsReactionAudioProfile.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsReactiveBoardView.kt',
  'android/app/src/test/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsReactionEngineTest.kt',
  'android/app/src/test/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsGameMomentDetectorTest.kt',
];

for (const file of requiredFiles) {
  if (!fs.existsSync(file)) throw new Error(`Missing reaction file: ${file}`);
}

const spokenLines =
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsVoiceLines.kt';
if (fs.existsSync(spokenLines)) {
  throw new Error('Spoken character commentary must stay removed.');
}

const engine = fs.readFileSync(requiredFiles[0], 'utf8');
const moments = fs.readFileSync(requiredFiles[1], 'utf8');
const director = fs.readFileSync(requiredFiles[2], 'utf8');
const voice = fs.readFileSync(requiredFiles[3], 'utf8');
const profile = fs.readFileSync(requiredFiles[4], 'utf8');
const board = fs.readFileSync(requiredFiles[5], 'utf8');

for (const marker of [
  'VoiceCue.SILENT',
  'VoiceCue.SIX',
  'VoiceCue.YARD_EXIT',
  'VoiceCue.THIRD_SIX',
  'VoiceCue.CAPTURE',
  'VoiceCue.CAPTURED',
  'VoiceCue.SAFE',
  'VoiceCue.HOME_LANE',
  'VoiceCue.HOME',
  'VoiceCue.FRUSTRATED',
  'VoiceCue.NERVOUS',
  'VoiceCue.PROUD',
  'VoiceCue.VICTORY',
  'VoiceCue.DEFEAT',
]) {
  if (!engine.includes(marker)) throw new Error(`Reaction engine is missing ${marker}`);
}
for (const marker of [
  'ONLY_LEGAL_MOVE',
  'HOME_LANE_ENTERED',
  'EXACT_HOME_MISS',
  'TOKEN_THREATENED',
  'isExactHomeMiss',
  'threateningPlayer',
  'sameTeam',
]) {
  if (!moments.includes(marker)) throw new Error(`Situation detector is missing ${marker}`);
}
for (const marker of ['IDLE -> 24_000L', 'PROUD -> 12_000L', 'NERVOUS -> 8_000L']) {
  if (!director.includes(marker)) throw new Error(`Reaction scheduler is missing ${marker}`);
}

if (!voice.includes('GameSettingsStore') || !voice.includes('LudoPawsReactionAudioProfile')) {
  throw new Error('Animal vocal player must respect settings and use the reaction audio profile.');
}
for (const forbidden of [
  'import android.speech.tts.TextToSpeech',
  'TextToSpeech(',
  'Six! Let',
  'Got you!',
  'I win!',
]) {
  if (voice.includes(forbidden)) throw new Error(`Spoken character commentary returned: ${forbidden}`);
}
for (const marker of ['probabilityPercent', 'rawResourceNames', 'fallback', 'deterministicVariantIndex']) {
  if (!profile.includes(marker)) throw new Error(`Vocal profile is missing ${marker}`);
}

if (
  !board.includes('LudoPawsReactionEngine') ||
  !board.includes('.detect(') ||
  !board.includes('LudoPawsVoicePlayer')
) {
  throw new Error('Reactive board is not wired to reaction derivation and animal vocals.');
}

for (const source of [engine, moments, director]) {
  for (const marker of ['OfflineGameEngine(', 'session.move(', 'session.roll(', 'NativeDiceEngine']) {
    if (source.includes(marker)) {
      throw new Error(`Reaction presentation layer must not mutate gameplay/proof state: ${marker}`);
    }
  }
}

console.log('Ludo Paws situational non-verbal reaction gate passed.');
