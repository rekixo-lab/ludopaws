import fs from 'node:fs';

const required = [
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsGameMomentDetector.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsReactionEngine.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsIdleReactionPolicy.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsReactiveBoardView.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsPlayerCardView.kt',
  'android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/gameplay/OfflinePlayerRailUi.kt',
  'android/app/src/test/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsGameMomentDetectorTest.kt',
  'android/app/src/test/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsPoorRollStreakTest.kt',
  'android/app/src/test/java/com/rekixo/ludopaws/feature/characters/domain/reaction/LudoPawsIdleReactionPolicyTest.kt',
];

for (const path of required) {
  if (!fs.existsSync(path)) throw new Error(`Missing game-moment file: ${path}`);
}

const detector = fs.readFileSync(required[0], 'utf8');
const reaction = fs.readFileSync(required[1], 'utf8');
const idlePolicy = fs.readFileSync(required[2], 'utf8');
const reactiveBoard = fs.readFileSync(required[3], 'utf8');
const card = fs.readFileSync(required[4], 'utf8');
const rail = fs.readFileSync(required[5], 'utf8');
const tests = fs.readFileSync(required[6], 'utf8');
const poorRollTests = fs.readFileSync(required[7], 'utf8');
const idleTests = fs.readFileSync(required[8], 'utf8');

for (const marker of [
  'TURN_STARTED', 'ROLL_STARTED', 'SIX_ROLLED', 'LOW_ROLL', 'POOR_ROLL_STREAK',
  'TOKEN_LEFT_YARD', 'TOKEN_MOVED', 'ONLY_LEGAL_MOVE', 'CAPTURE_MADE',
  'TOKEN_CAPTURED', 'SAFE_REACHED', 'HOME_LANE_ENTERED', 'HOME_REACHED',
  'EXACT_HOME_MISS', 'TOKEN_THREATENED', 'NO_LEGAL_MOVE', 'THIRD_SIX_FORFEIT',
  'PLAYER_LEADING', 'IDLE_WAITING', 'MATCH_WIN', 'MATCH_LOSS', 'TEAM_WIN', 'TEAM_LOSS',
]) {
  if (!detector.includes(marker)) throw new Error(`Situation detector is missing ${marker}`);
}
for (const marker of [
  'isPoorRollStreak',
  'POOR_ROLL_STREAK_LENGTH',
  'wasOnlyLegalMove',
  'isExactHomeMiss',
  'threateningPlayer',
  'sameTeam',
  'safeGlobalCells',
]) {
  if (!detector.includes(marker)) throw new Error(`Situation detector policy is missing ${marker}`);
}

for (const marker of [
  'LudoPawsGameMomentDetector',
  'deriveIdle',
  'VoiceCue.FRUSTRATED',
  'VoiceCue.NERVOUS',
  'VoiceCue.PROUD',
  'VoiceCue.HOME_LANE',
  'VoiceCue.YARD_EXIT',
]) {
  if (!reaction.includes(marker)) throw new Error(`Reaction engine is missing ${marker}`);
}
for (const marker of ['IDLE_THRESHOLD_MILLIS = 24_000L', 'meaningfulStateKey', 'idleReactionKey', 'delayUntilEligibleMillis']) {
  if (!idlePolicy.includes(marker)) throw new Error(`Idle reaction policy is missing ${marker}`);
}
for (const marker of [
  'idleReactionRunnable',
  'scheduleIdleReaction',
  'playIdleReactionIfEligible',
  'LudoPawsReactionEngine',
  'deriveIdle',
  'removeCallbacks(idleReactionRunnable)',
]) {
  if (!reactiveBoard.includes(marker)) throw new Error(`Reactive board idle integration is missing ${marker}`);
}

// The gameplay card now renders the selected animal through the dedicated mascot
// avatar instead of the retired transparent fallback drawable. Keep this gate tied
// to semantic character identity and authoritative token progress rather than old
// implementation strings such as fallbackDrawableName/RACING.
for (const marker of [
  'LudoPawsCharacterAvatarView',
  'LudoPawsCharacterCatalog::character',
  'portrait.bind(',
  'progressText.text',
  'LudoPathEncoding.HOME_POSITION',
  'ALL PAWS HOME',
]) {
  if (!card.includes(marker)) {
    throw new Error(`Character player card is missing production presentation marker: ${marker}`);
  }
}
const railConsumesCharacters = rail.includes('characterIds') || rail.includes('activeCharacterIdsBySeat');
if (!rail.includes('LudoPawsPlayerCardView') || !railConsumesCharacters) {
  throw new Error('Offline player rails must use selected Ludo Paws character cards.');
}

for (const marker of [
  'thirdSixIsNotMisclassifiedAsNoLegalMove',
  'detectsOnlyLegalMoveFromResolvedAuthoritativePendingRoll',
  'detectsHomeLaneEntrySeparatelyFromHomeArrival',
  'exactHomeOvershootIsNotGenericNoMove',
  'unsafePawnCanBecomeThreatenedButSafePawnAndTeamPartnerDoNotTrigger',
  'emitsTeamWinAndLossMoments',
]) {
  if (!tests.includes(marker)) throw new Error(`Situation detector regression coverage is missing ${marker}`);
}
if (
  !poorRollTests.includes('threePoorRollsForSamePlayerAcrossOtherTurnsCreateOneStreakMoment') ||
  !poorRollTests.includes('poorRollStreakMapsToControlledFrustrationReaction')
) {
  throw new Error('Poor-roll frustration regression coverage is incomplete.');
}
if (
  !idleTests.includes('identicalRefreshKeepsMeaningfulKeyStable') ||
  !idleTests.includes('teamUpIdleKeyUsesActingSeatNotTeamTurnOwner') ||
  !idleTests.includes('24_000L')
) {
  throw new Error('Quiet idle scheduling regression coverage is incomplete.');
}

for (const forbidden of ['OfflineGameEngine(', 'session.move(', 'session.roll(', 'NativeDiceEngine']) {
  if (detector.includes(forbidden) || idlePolicy.includes(forbidden)) {
    throw new Error(`Game-moment presentation policy must stay authority-free: ${forbidden}`);
  }
}

console.log('Ludo Paws rich game-moment gate passed.');
