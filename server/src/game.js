import * as legacy from "./game-v1-core.js";
import {
  LEGACY_RULESET,
  LEGACY_RULESET_KEY,
  LEGACY_TEAM_RULESET,
  LEGACY_TEAM_RULESET_KEY,
  ROLL_REVEAL_TIMEOUT_MS,
  RULESET,
  RULESET_KEY,
  RULESET_V3,
  RULESET_V3_KEY,
  RULESET_V4,
  RULESET_V4_KEY,
  RULESET_V5,
  RULESET_V5_KEY,
  TEAM_ASSIGNMENTS,
  TEAM_RULESET,
  TEAM_RULESET_KEY,
  TEAM_RULESET_V3,
  TEAM_RULESET_V3_KEY,
  TEAM_RULESET_V4,
  TEAM_RULESET_V4_KEY,
  TEAM_RULESET_V5,
  TEAM_RULESET_V5_KEY,
  rulesetForKey,
  rulesetKeyForState,
} from "./rulesets.js";

// Keep the battle-tested v1 engine implementation intact. This compatibility
// layer binds explicit newer contracts to a match without changing the
// production default. Missing rulesetKey intentionally means immutable v1 so
// an accidental backend deploy cannot strand older installed Android clients.
export * from "./game-v1-core.js";

export {
  LEGACY_RULESET,
  LEGACY_RULESET_KEY,
  LEGACY_TEAM_RULESET,
  LEGACY_TEAM_RULESET_KEY,
  ROLL_REVEAL_TIMEOUT_MS,
  RULESET,
  RULESET_KEY,
  RULESET_V3,
  RULESET_V3_KEY,
  RULESET_V4,
  RULESET_V4_KEY,
  RULESET_V5,
  RULESET_V5_KEY,
  TEAM_ASSIGNMENTS,
  TEAM_RULESET,
  TEAM_RULESET_KEY,
  TEAM_RULESET_V3,
  TEAM_RULESET_V3_KEY,
  TEAM_RULESET_V4,
  TEAM_RULESET_V4_KEY,
  TEAM_RULESET_V5,
  TEAM_RULESET_V5_KEY,
};

export function isTeamUp(state) {
  return legacy.isTeamUp(state);
}

export function rulesetForState(state) {
  const teamUp = isTeamUp(state);
  const key = rulesetKeyForState(state, { teamUp });
  const allowed = teamUp
    ? new Set([
        LEGACY_TEAM_RULESET_KEY,
        TEAM_RULESET_KEY,
        TEAM_RULESET_V3_KEY,
        TEAM_RULESET_V4_KEY,
        TEAM_RULESET_V5_KEY,
      ])
    : new Set([
        LEGACY_RULESET_KEY,
        RULESET_KEY,
        RULESET_V3_KEY,
        RULESET_V4_KEY,
        RULESET_V5_KEY,
      ]);

  if (!allowed.has(key)) {
    throw legacy.gameError(
      "UNSUPPORTED_RULESET",
      `ruleset ${String(key)} is not valid for this match mode`,
    );
  }

  try {
    return rulesetForKey(key);
  } catch {
    throw legacy.gameError(
      "UNSUPPORTED_RULESET",
      `unsupported ruleset ${String(key)}`,
    );
  }
}

export function newMatch(options) {
  const state = legacy.newMatch(options);
  const explicitRulesetKey = options?.rulesetKey;
  if (explicitRulesetKey == null) {
    return state;
  }

  const versionedState = {
    ...state,
    rulesetKey: explicitRulesetKey,
  };
  rulesetForState(versionedState);

  if (isV3State(versionedState)) {
    versionedState.openingRollConsumed =
      versionedState.players.map(() => false);
  }
  return versionedState;
}

export function addPlayer(state, options) {
  const next = legacy.addPlayer(state, options);
  if (!isV3State(next)) {
    return next;
  }

  const previous = openingRollState(state);
  next.openingRollConsumed = [
    ...previous,
    false,
  ];
  return next;
}

export function registerResolvedRoll(state, options) {
  if (!isV3State(state)) {
    return legacy.registerResolvedRoll(state, options);
  }

  const seat = state.pendingRoll?.seat;
  if (!Number.isInteger(seat)) {
    return legacy.registerResolvedRoll(state, options);
  }

  const prepared = structuredClone(state);
  prepared.openingRollConsumed = openingRollState(state);

  const player = prepared.players?.[seat];
  const openingRollApplied =
    prepared.openingRollConsumed[seat] === false &&
    Array.isArray(player?.tokens) &&
    player.tokens.length === 4 &&
    player.tokens.every((position) => position === -1);

  // The entitlement is consumed by the first resolved roll even if a corrupted
  // or migrated state somehow no longer has all four tokens in the yard.
  prepared.openingRollConsumed[seat] = true;

  const effectiveOutcome = openingRollApplied
    ? 6
    : options.outcome;

  const result = legacy.registerResolvedRoll(
    prepared,
    {
      ...options,
      outcome: effectiveOutcome,
    },
  );

  // The guaranteed opening six is a ruleset bonus, not a random six. It must
  // not move the player closer to the three-consecutive-sixes penalty.
  if (openingRollApplied) {
    result.state.consecutiveSixes[seat] = 0;
  }

  if (result.state.pendingRoll?.status === "RESOLVED") {
    result.state.pendingRoll.randomOutcome = options.outcome;
    result.state.pendingRoll.openingRollApplied = openingRollApplied;
  }

  return {
    ...result,
    randomOutcome: options.outcome,
    effectiveOutcome,
    openingRollApplied,
  };
}

export function applyMove(state, options) {
  const pending = state.pendingRoll;
  const v3 = isV3State(state);
  const eventIndex = pending?.eventIndex;
  const effectiveOutcome = pending?.outcome;
  const randomOutcome =
    pending?.randomOutcome ??
    effectiveOutcome;
  const openingRollApplied =
    Boolean(pending?.openingRollApplied);

  const result = legacy.applyMove(state, options);

  if (
    v3 &&
    Number.isInteger(eventIndex) &&
    Number.isInteger(effectiveOutcome) &&
    Array.isArray(result.state.history)
  ) {
    const historyIndex = result.state.history.findLastIndex(
      (event) =>
        event.eventIndex === eventIndex &&
        event.playerId === options.playerId,
    );
    if (historyIndex >= 0) {
      result.state.history[historyIndex] = {
        ...result.state.history[historyIndex],
        randomOutcome,
        effectiveOutcome,
        openingRollApplied,
      };
    }
  }

  return result;
}

export function authoritativeStateForRandomness(state) {
  let material = legacy.authoritativeStateForRandomness(state);
  if (isTeamUp(state)) {
    material = {
      ...material,
      teamRulesetId: rulesetForState(state).id,
    };
  }
  if (isV3State(state)) {
    material = {
      ...material,
      openingRollConsumed: openingRollState(state),
    };
  }
  return material;
}

export function publicState(state) {
  const base = legacy.publicState(state);
  const pendingRoll =
    base.pendingRoll && state.pendingRoll
      ? {
          ...base.pendingRoll,
          ...(Number.isInteger(state.pendingRoll.randomOutcome)
            ? { randomOutcome: state.pendingRoll.randomOutcome }
            : {}),
          ...(typeof state.pendingRoll.openingRollApplied === "boolean"
            ? { openingRollApplied: state.pendingRoll.openingRollApplied }
            : {}),
        }
      : base.pendingRoll;

  return {
    ...base,
    pendingRoll,
    rulesetId: rulesetForState(state).id,
    turnDeadlineAt: Number.isSafeInteger(state.turnDeadlineAt)
      ? state.turnDeadlineAt
      : null,
    turnRollTimeoutMs: Number.isSafeInteger(rulesetForState(state).turnRollTimeoutMs)
      ? rulesetForState(state).turnRollTimeoutMs
      : null,
    serverNow: Date.now(),
    ...(isV3State(state)
      ? { openingRollConsumed: openingRollState(state) }
      : {}),
  };
}

function isV3State(state) {
  const key = rulesetKeyForState(state, {
    teamUp: isTeamUp(state),
  });
  return key === RULESET_V3_KEY ||
    key === TEAM_RULESET_V3_KEY ||
    key === RULESET_V4_KEY ||
    key === TEAM_RULESET_V4_KEY || key === RULESET_V5_KEY || key === TEAM_RULESET_V5_KEY;
}

function openingRollState(state) {
  const playerCount = Array.isArray(state?.players)
    ? state.players.length
    : 0;
  const stored = Array.isArray(state?.openingRollConsumed)
    ? state.openingRollConsumed
    : [];

  return Array.from(
    { length: playerCount },
    (_, seat) => stored[seat] === true,
  );
}
