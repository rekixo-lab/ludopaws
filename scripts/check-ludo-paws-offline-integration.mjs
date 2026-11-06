import fs from "node:fs";

const read = (path) => fs.readFileSync(path, "utf8");

const requireText = (path, needles) => {
  const text = read(path);
  for (const needle of needles) {
    if (!text.includes(needle)) {
      throw new Error(`${path} is missing required Phase 11 marker: ${needle}`);
    }
  }
};

const forbidText = (path, needles) => {
  const text = read(path);
  for (const needle of needles) {
    if (text.includes(needle)) {
      throw new Error(`${path} contains obsolete Phase 11 pattern: ${needle}`);
    }
  }
};

requireText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/feedback/OfflineLudoPawsFeedback.kt",
  [
    "OfflineFeedbackAction",
    "OfflineFeedbackSound",
    "LudoPawsReactionEngine.derive",
    "GameSoundFeedback.six",
    "GameSoundFeedback.homeLane",
    "GameSoundFeedback.thirdSix",
    "LudoPawsHaptics.reaction",
    "tokenMovementCommitted",
  ],
);

requireText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/actions/OfflineGameActions.kt",
  [
    "GameSoundFeedback.roll",
    "OfflineLudoPawsFeedbackDispatcher.committed",
    "OfflineFeedbackAction.ROLL",
    "OfflineFeedbackAction.MOVE",
    "previous = latest",
    "LudoPawsComputerMovePolicy.chooseToken",
  ],
);

requireText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/domain/ai/LudoPawsComputerMovePolicy.kt",
  [
    "destinationForRoll",
    "SCORE_CAPTURE_EACH",
    "SCORE_HOME_LANE",
    "SCORE_RESCUE",
    "SCORE_SAFE_LANDING",
    "PENALTY_THREAT_ROUTE",
    "threatRoutes",
  ],
);

requireText(
  "android/app/src/test/java/com/rekixo/ludopaws/feature/offline/domain/ai/LudoPawsComputerMovePolicyTest.kt",
  [
    "canonical destination handles yard exit and exact home",
    "capture outranks plain forward progress",
    "safe landing outranks nearby exposed progress",
    "rescuing threatened token can beat raw progress",
    "risk penalty avoids reachable capture square when alternative is clear",
  ],
);

requireText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/domain/engine/OfflineGameEngine.kt",
  [
    "finishedPlayerIds",
    "current.finishedPlayerIds.size",
    "players.size - 1",
    "advanceTurn(current)",
  ],
);

requireText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/domain/engine/OfflineMatchInvariantPolicy.kt",
  [
    "OfflineMatchInvariantPolicy",
    "pending.legalTokenIndexes == expectedLegal",
    "historyEvent.proofDigest == pending.proofDigest",
    "winner.tokens.all",
    "LudoPathEncoding.HOME_POSITION",
  ],
);

requireText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/domain/session/LocalMatchSession.kt",
  [
    "OfflineMatchInvariantPolicy::requireValid",
    "OfflineMatchInvariantPolicy.requireValid",
  ],
);

requireText(
  "android/app/src/test/java/com/rekixo/ludopaws/OfflineMatchInvariantPolicyTest.kt",
  [
    "pending legal set must exactly match canonical path legality",
    "pending proof must match the same history event",
    "finished match requires all winner tokens at exact home",
  ],
);

requireText(
  "android/app/src/androidTest/java/com/rekixo/ludopaws/OfflineFullMatchInstrumentedTest.kt",
  [
    "twoAndFourPlayerMatchesFinishThroughProductionEngineAndCpuPolicy",
    "LudoPawsComputerMovePolicy.chooseToken",
    "OfflineMatchInvariantPolicy.requireValid",
    "finishedPlayers >= playerCount - 1",
    "MAX_TRANSITIONS",
  ],
);

requireText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/gameplay/OfflineGameplayScreenUi.kt",
  [
    "val previous =",
    "session.snapshot()",
    "OfflineLudoPawsFeedbackDispatcher.committed",
    "OfflineFeedbackAction.MOVE",
    "gameplayHud()",
    "gameplayActionPanel()",
    "VS COMPUTER",
  ],
);

forbidText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/actions/OfflineGameActions.kt",
  ["GameSoundFeedback.move("],
);
forbidText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/gameplay/OfflineGameplayScreenUi.kt",
  ["GameSoundFeedback.move("],
);

requireText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/state/OfflineCharacterPresentationState.kt",
  [
    "OfflineCharacterAssignmentPolicy",
    "resolveOfflineCharacterIds",
    "characterSelectionStore",
    "offlineCharacterSetupStore.saveActive",
    "activeCharacterMatchId",
  ],
);

requireText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/gameplay/OfflineGameplayControlsUi.kt",
  [
    "resolveOfflineCharacterIds",
    "characterIdsBySeat =",
    "turnText?.text",
    "infoText?.text",
    "pending.legalTokenIndexes.size",
    "activeHomeCount",
    "QuickMatchPolicy.RULESET_ID) 2 else 4",
    "$racingPaws/$racingPaws PAWS HOME",
    "updateOfflineResultCard(state)",
    "latestForActive",
    "presentedDiceEventKey",
    "animate = shouldAnimate",
  ],
);

requireText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/gameplay/OfflinePlayerRailUi.kt",
  ["activeCharacterIdsBySeat"],
);

requireText(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/OfflineGameActivity.kt",
  [
    "activeCharacterMatchId",
    "activeCharacterIdsBySeat",
    "presentedDiceEventKey",
  ],
);

requireText(
  "android/app/src/test/java/com/rekixo/ludopaws/feature/offline/presentation/feedback/OfflineLudoPawsFeedbackPolicyTest.kt",
  [
    "normal committed move uses move sound",
    "capture replaces generic move sound",
    "victory wins over home feedback",
    "third six uses dedicated soft penalty sound and keeps haptic reaction",
    "home lane and exact home miss have distinct sounds",
    "silent sync does not invent feedback",
  ],
);

requireText(
  "android/app/src/test/java/com/rekixo/ludopaws/feature/offline/presentation/state/OfflineCharacterAssignmentPolicyTest.kt",
  [
    "matching pass and play assignment is preserved",
    "mismatched saved presentation state is repaired deterministically",
    "computer resume keeps human preference and unique cpu characters",
  ],
);

console.log("Ludo Paws Phase 11 offline integration gate passed.");
