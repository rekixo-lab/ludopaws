import fs from 'node:fs';

const files = {
  engine: 'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsReactionEngine.kt',
  director: 'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsReactionDirector.kt',
  tests: 'android/app/src/test/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsReactionDirectorTest.kt',
};

for (const file of Object.values(files)) {
  if (!fs.existsSync(file)) {
    throw new Error(`Missing Phase 8 reaction-director file: ${file}`);
  }
}

const engine = fs.readFileSync(files.engine, 'utf8');
const director = fs.readFileSync(files.director, 'utf8');
const tests = fs.readFileSync(files.tests, 'utf8');

for (const marker of [
  'reactionKey',
  'matchId',
  'eventIndex',
  'momentType',
  'LudoPawsReactionDirector',
  'alignCapturePair',
]) {
  if (!engine.includes(marker)) {
    throw new Error(`Phase 8 reaction engine is missing ${marker}`);
  }
}

for (const marker of [
  'consumedKeys',
  'cooldownAllows',
  'queuedBatches',
  'shouldInterrupt',
  'PRIORITY_CAPTURE',
  'PRIORITY_VICTORY',
  'maxConsumedKeys',
]) {
  if (!director.includes(marker)) {
    throw new Error(`Phase 8 director is missing ${marker}`);
  }
}

for (const marker of [
  'duplicateStableKeyIsConsumedOnlyOnce',
  'noisyCueCooldownSuppressesDifferentEventsFromSamePlayer',
  'lowerPriorityReactionQueuesUntilPlaybackWindowExpires',
  'captureCanInterruptLowerPriorityPlayback',
  'sameEventWinAndLossStayInOneDeterministicBatch',
]) {
  if (!tests.includes(marker)) {
    throw new Error(`Phase 8 director tests are missing ${marker}`);
  }
}

for (const forbidden of [
  'OfflineGameEngine(',
  'NativeDiceEngine',
  'legalTokenIndexes.add',
  'winnerPlayerId =',
]) {
  if (director.includes(forbidden)) {
    throw new Error(`Reaction director crossed gameplay/proof boundary: ${forbidden}`);
  }
}

console.log('Ludo Paws Phase 8 reaction-director gate passed.');
