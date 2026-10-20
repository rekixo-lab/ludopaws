export const ROLL_REVEAL_TIMEOUT_MS = 4 * 60 * 1000;

export const LEGACY_RULESET_KEY = "CLASSIC_V1";
export const LEGACY_TEAM_RULESET_KEY = "TEAM_UP_V1";
export const RULESET_KEY = "CLASSIC_V2";
export const TEAM_RULESET_KEY = "TEAM_UP_V2";
export const RULESET_V3_KEY = "CLASSIC_V3";
export const TEAM_RULESET_V3_KEY = "TEAM_UP_V3";
export const RULESET_V4_KEY = "CLASSIC_V4";
export const TEAM_RULESET_V4_KEY = "TEAM_UP_V4";

export const RULESET_V5_KEY = "CLASSIC_V5";
export const TEAM_RULESET_V5_KEY = "TEAM_UP_V5";

export const TURN_ROLL_TIMEOUT_MS = 15_000;
export const MAX_MISSED_ROLLS_BEFORE_FORFEIT = 4;

export const TEAM_ASSIGNMENTS = Object.freeze(["A", "B", "A", "B"]);

export const LEGACY_RULESET = Object.freeze({
  id: "ludoproof-standard-v1",
  boardTrackCells: 52,
  homePosition: 57,
  tokensPerPlayer: 4,
  startOffsets: Object.freeze({
    RED: 0,
    GREEN: 13,
    YELLOW: 26,
    BLUE: 39,
  }),
  safeGlobalCells: Object.freeze([0, 8, 13, 21, 26, 34, 39, 47]),
  leaveYardRequiresSix: true,
  exactRollToHome: true,
  extraTurnOnSix: true,
  extraTurnOnCapture: true,
  threeConsecutiveSixesForfeit: true,
  captureOnSafeCell: false,
});

export const LEGACY_TEAM_RULESET = Object.freeze({
  ...LEGACY_RULESET,
  id: "ludoproof-team-v1",
  mode: "TEAM_UP",
  teams: Object.freeze(["A", "B"]),
  teamAssignments: TEAM_ASSIGNMENTS,
  friendlyCapture: false,
  partnerTurnHandoff: true,
  winCondition: "BOTH_PARTNERS_ALL_TOKENS_HOME",
});

/**
 * Ruleset v2 deliberately preserves v1 gameplay while making previously
 * implicit behavior explicit in the cryptographically hashed ruleset material.
 * Changing any value below therefore changes rulesetHash for newly-created
 * matches instead of silently changing gameplay under an existing proof ID.
 */
export const RULESET = Object.freeze({
  ...LEGACY_RULESET,
  id: "ludoproof-standard-v2",
  extraTurnOnHome: true,
  rollRevealTimeoutMs: ROLL_REVEAL_TIMEOUT_MS,
  rollTimeoutPolicy: "FORFEIT_ROLL_AND_ADVANCE_TURN",
  replacementRoundAfterTimeout: false,
  resetConsecutiveSixesOnTimeout: true,
  ownTokenStacking: "ALLOWED",
  opponentStackCapture: "CAPTURE_ALL_ON_UNSAFE_CELL",
  startingPlayerPolicy: "HOST_SEAT_ZERO",
  turnOrderPolicy: "SEQUENTIAL_SEAT_ORDER",
});

export const TEAM_RULESET = Object.freeze({
  ...RULESET,
  id: "ludoproof-team-v2",
  mode: "TEAM_UP",
  teams: Object.freeze(["A", "B"]),
  teamAssignments: TEAM_ASSIGNMENTS,
  friendlyCapture: false,
  partnerTurnHandoff: true,
  winCondition: "BOTH_PARTNERS_ALL_TOKENS_HOME",
});

/**
 * Ruleset v3 adds explicit player-assistance behavior without changing v1/v2.
 * The opening bonus is a deterministic gameplay transform over a verified roll:
 * the player's first roll is treated as six only while all four tokens are still
 * in the starting yard. It is consumed once and is excluded from the three-six
 * penalty. A single legal move may be auto-submitted by compatible clients.
 */
export const RULESET_V3 = Object.freeze({
  ...RULESET,
  id: "ludoproof-standard-v3",
  openingRollPolicy: "GUARANTEED_SIX_ONCE_PER_PLAYER",
  openingRollRequiresAllTokensInYard: true,
  openingRollCountsTowardConsecutiveSixes: false,
  singleLegalMovePolicy: "AUTO_MOVE",
});

export const TEAM_RULESET_V3 = Object.freeze({
  ...RULESET_V3,
  id: "ludoproof-team-v3",
  mode: "TEAM_UP",
  teams: Object.freeze(["A", "B"]),
  teamAssignments: TEAM_ASSIGNMENTS,
  friendlyCapture: false,
  partnerTurnHandoff: true,
  winCondition: "BOTH_PARTNERS_ALL_TOKENS_HOME",
});

/**
 * V4 adds server-authoritative turn deadlines and Team-Up stack protection.
 * Previous rulesets remain immutable for existing match/proof compatibility.
 */
export const RULESET_V4 = Object.freeze({
  ...RULESET_V3,
  id: "ludoproof-standard-v4",
  turnRollTimeoutMs: TURN_ROLL_TIMEOUT_MS,
  maxMissedRollsBeforeForfeit: MAX_MISSED_ROLLS_BEFORE_FORFEIT,
  turnTimeoutPolicy: "ADVANCE_TURN_AND_FORFEIT_AFTER_LIMIT",
});

export const TEAM_RULESET_V4 = Object.freeze({
  ...TEAM_RULESET_V3,
  id: "ludoproof-team-v4",
  teamStackProtection: "SAFE_WHEN_TEAM_HAS_AT_LEAST_TWO_TOKENS_ON_CELL",
  turnRollTimeoutMs: TURN_ROLL_TIMEOUT_MS,
  maxMissedRollsBeforeForfeit: MAX_MISSED_ROLLS_BEFORE_FORFEIT,
  turnTimeoutPolicy: "ADVANCE_TURN_AND_FORFEIT_AFTER_LIMIT",
});

// V5 corrects the declared contract to match stack protection already enforced by the engine.
// Older version/hash objects remain immutable for existing receipts and saved matches.
export const RULESET_V5 = Object.freeze({
  ...RULESET_V4, id: "ludoproof-standard-v5", opponentStackCapture: "OPPONENT_STACK_PROTECTED",
});
export const TEAM_RULESET_V5 = Object.freeze({
  ...TEAM_RULESET_V4, id: "ludoproof-team-v5", opponentStackCapture: "OPPONENT_STACK_PROTECTED",
});

export function rulesetKeyForState(state, { teamUp = false } = {}) {
  const key = state?.rulesetKey;
  if (key == null) {
    return teamUp ? LEGACY_TEAM_RULESET_KEY : LEGACY_RULESET_KEY;
  }
  return key;
}

export function rulesetForKey(key) {
  switch (key) {
    case LEGACY_RULESET_KEY:
      return LEGACY_RULESET;
    case LEGACY_TEAM_RULESET_KEY:
      return LEGACY_TEAM_RULESET;
    case RULESET_KEY:
      return RULESET;
    case TEAM_RULESET_KEY:
      return TEAM_RULESET;
    case RULESET_V3_KEY:
      return RULESET_V3;
    case TEAM_RULESET_V3_KEY:
      return TEAM_RULESET_V3;
    case RULESET_V4_KEY:
      return RULESET_V4;
    case TEAM_RULESET_V4_KEY:
      return TEAM_RULESET_V4;
    case RULESET_V5_KEY: return RULESET_V5;
    case TEAM_RULESET_V5_KEY: return TEAM_RULESET_V5;
    default: {
      const error = new Error(`unsupported ruleset key: ${String(key)}`);
      error.code = "UNSUPPORTED_RULESET";
      throw error;
    }
  }
}
