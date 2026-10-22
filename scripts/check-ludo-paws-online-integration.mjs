import fs from "node:fs";

const read = (path) => fs.readFileSync(path, "utf8");
const requireText = (source, token, message) => {
  if (!source.includes(token)) throw new Error(message);
};

const characters = read("server/src/ludo-paws-characters.js");
const gameCorePath = fs.existsSync("server/src/game-v1-core.js")
  ? "server/src/game-v1-core.js"
  : "server/src/game.js";
const game = read(gameCorePath);
const room = read("server/src/match-room.js");
const publicQueue = read("server/src/matchmaker-queue.js");
const teamQueue = read("server/src/team-matchmaker-queue.js");
const models = read(
  "android/app/src/main/java/com/rekixo/ludopaws/game/domain/model/GameModels.kt",
);
const gameApi = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/data/remote/GameApi.kt",
);
const teamApi = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/team/data/remote/TeamMatchmakingApi.kt",
);
const cachedStore = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/data/local/CachedMatchStore.kt",
);
const runtime = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/data/local/LudoPawsCharacterRuntime.kt",
);
const policy = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/domain/OnlineLudoPawsCharacterPolicy.kt",
);
const mainActivity = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/MainActivity.kt",
);
const renderer = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/OnlineResponseRenderer.kt",
);
const verificationRenderer = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/OnlineVerificationRenderer.kt",
);
const sessionActions = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/OnlineSessionActions.kt",
);
const uiState = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/state/OnlineGameUiState.kt",
);
const verifiedDicePolicy = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/state/OnlineVerifiedDicePresentationPolicy.kt",
);
const verifiedDiceTests = read(
  "android/app/src/test/java/com/rekixo/ludopaws/OnlineVerifiedDicePresentationPolicyTest.kt",
);
const pendingRecoveryPolicy = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/state/OnlineStateRestorePolicy.kt",
);
const pendingRecoveryTests = read(
  "android/app/src/test/java/com/rekixo/ludopaws/OnlineStateRestorePolicyTest.kt",
);
const rollActionPolicy = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/state/OnlineRollActionPolicy.kt",
);
const rollActionTests = read(
  "android/app/src/test/java/com/rekixo/ludopaws/OnlineRollActionPolicyTest.kt",
);
const matchActions = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/OnlineMatchActions.kt",
);
const presentation = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/OnlineLudoPawsPresentation.kt",
);

for (const id of ["dog", "goat", "duck", "cat"]) {
  requireText(characters, `"${id}"`, `Server canonical 3D character roster is missing ${id}.`);
}
for (const alias of [
  '["squirrel", "dog"]',
  '["hedgehog", "goat"]',
  '["sheep", "cat"]',
]) {
  requireText(
    characters,
    alias,
    `Server compatibility bridge is missing legacy alias ${alias}.`,
  );
}
requireText(characters, 'DEFAULT_CHARACTER_ID = "dog"', "Legacy clients need Dog as the deterministic canonical fallback.");
requireText(characters, "INVALID_CHARACTER_ID", "Invalid remote character IDs must be rejected.");

requireText(game, "hostCharacterId", "Match creation must persist the host character.");
requireText(game, "characterId: publicCharacterId", "Public player state must expose character identity.");
const proofStart = game.indexOf("export function authoritativeStateForRandomness");
const proofEnd = game.indexOf("export function reserveRoll", proofStart);
if (proofStart < 0 || proofEnd <= proofStart) {
  throw new Error("Could not locate the authoritative proof-state boundary.");
}
const proofState = game.slice(proofStart, proofEnd);
if (proofState.includes("characterId")) {
  throw new Error("Character identity must never enter authoritative randomness state.");
}

requireText(room, "normalizeCharacterId", "MatchRoom must validate incoming character identity.");
requireText(room, "hostCharacterId", "MatchRoom must bind host character identity.");
requireText(publicQueue, "characterId", "Public matchmaking must preserve character identity across queue/status/replay.");
requireText(teamQueue, "characterId", "Team Up matchmaking must preserve character identity across queue/status/replay.");

requireText(runtime, "selectedCharacterId", "Android needs a process-safe selected character mirror.");
// Ensure every online/friends create, search and join writes the contract-resolved
// cosmetic ID rather than sending an unsupported or stale local selection.
const characterRequestWrite =
  /\.put\(\s*"characterId"\s*,\s*(?:com\.rekixo\.ludopaws\.feature\.online\.domain\.)?LudoPawsCharacterIdentityContract\.resolveForRequest\(\s*characterId\s*\)\s*\)/g;
if ((gameApi.match(characterRequestWrite) ?? []).length < 4) {
  throw new Error("Online/Friends create, search and join must send contract-normalized character identity.");
}
if ((teamApi.match(characterRequestWrite) ?? []).length < 1) {
  throw new Error("Team Up search must send contract-normalized character identity.");
}
requireText(models, "val characterId: String? = null", "Remote player snapshots must parse character identity.");
requireText(policy, "LudoPawsCharacterCatalog", "Remote character policy must validate IDs against the local catalog.");
requireText(policy, "LudoPawsCharacterIdentityContract", "Remote character policy must use the centralized compatibility contract.");
requireText(renderer, "OnlineLudoPawsPresentation.render", "Remote response rendering must drive Ludo Paws presentation.");

for (const marker of [
  "pending.status != \"RESOLVED\"",
  "val latest = state.history.lastOrNull()",
  "pending.proofDigest",
  "latest.proofDigest",
  "eventKey = \"${state.matchId}:$identity\"",
]) {
  requireText(
    verifiedDicePolicy,
    marker,
    `Fail-closed online verified-dice policy is missing ${marker}.`,
  );
}
for (const marker of [
  "OnlineVerifiedDicePresentationPolicy.resolve(state)",
  "presentedDiceEventKey",
  "animate = shouldAnimate",
  "Syncing the dice result",
  "onlineRollActionDecision(state)",
  "hasAuthoritativeActionState()",
]) {
  requireText(
    verificationRenderer,
    marker,
    `Remote verified-dice/recovery renderer is missing ${marker}.`,
  );
}
for (const marker of [
  "committedPendingNeverBorrowsPreviousVerifiedHistory",
  "resolvingPendingNeverBorrowsPreviousVerifiedHistory",
  "resolvedPendingWithoutProofFailsClosedInsteadOfUsingHistory",
  "eventKeyIsStableForRefreshAndChangesForNextEventEvenWithSameOutcome",
]) {
  requireText(
    verifiedDiceTests,
    marker,
    `Remote verified-dice regression coverage is missing ${marker}.`,
  );
}

for (const marker of [
  "CACHE_SOURCE_MARKER",
  "OnlineStateSource.CACHE",
  "OnlineCachedStateRestorePolicy.shouldApply",
  "currentStateSource = source",
]) {
  const target = marker === "CACHE_SOURCE_MARKER" ? cachedStore : renderer;
  requireText(
    target,
    marker,
    `Cache-safe pending-roll recovery wiring is missing ${marker}.`,
  );
}
requireText(pendingRecoveryPolicy, "sessionMatchId == cachedMatchId", "Cached state must belong to the secure session.");
requireText(pendingRecoveryTests, "cachedBoardMustBelongToTheActiveSession", "Cache/session mismatch needs regression coverage.");
for (const marker of ["hasAuthoritativeState", "SYNCING MATCH", "NEW_ROLL", "MOVE_REQUIRED"]) {
  requireText(rollActionPolicy, marker, `Authoritative action policy is missing ${marker}.`);
}
for (const marker of ["cacheOfflineAndOpponentCannotAuthorizeRoll", "resolvedDiceMustBeMovedBeforeAnotherRoll", "unexpectedPendingStatesRequireSync"]) {
  requireText(rollActionTests, marker, `Authoritative action regression coverage is missing ${marker}.`);
}
for (const marker of [
  "val decision = onlineRollActionDecision(state)",
  "session.roll(", "session.move(", "pendingOperationStore.getOrCreate", "if (!hasAuthoritativeActionState())",
]) {
  requireText(matchActions, marker, `Single-call online action flow is missing ${marker}.`);
}
for (const marker of ["api.commitRoll", "api.revealRoll", "pendingRollStore", "SeedCommitment.prepare"]) {
  if (matchActions.includes(marker)) throw new Error(`Obsolete two-stage online dice dependency: ${marker}`);
}
requireText(gameApi, "commandChannel", "Online commands must use the connected realtime transport.");
requireText(gameApi, "clientRequestId", "Online fallback must preserve request identity.");
requireText(
  uiState,
  "val currentStateSource: OnlineStateSource? = null",
  "Online UI state must preserve presentation-vs-authority source.",
);
requireText(
  sessionActions,
  "currentStateSource = null",
  "Invalid-session cleanup must clear online action authority.",
);
for (const marker of [
  "override fun onStop()",
  "currentStateSource = null",
  "realtimeConnected = false",
]) {
  requireText(
    mainActivity,
    marker,
    `Background/resume safety must invalidate online action authority: ${marker}.`,
  );
}

for (const marker of [
  "LudoPawsReactiveBoardView",
  "LudoPawsPlayerCardView",
  "characterIdsBySeat",
  "GameSoundFeedback",
  "LudoPawsHaptics",
  "LudoPawsFeedbackLedger",
  "feedbackLedger.once",
]) {
  requireText(presentation, marker, `Remote Ludo Paws presentation is missing ${marker}.`);
}

console.log("Ludo Paws online integration gate passed: character identity, shared presentation, authoritative action gates, cache/session isolation and single-call command transport.");
