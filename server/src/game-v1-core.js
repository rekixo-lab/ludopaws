import {
  DEFAULT_CHARACTER_ID,
  publicCharacterId,
} from "./ludo-paws-characters.js";
import { rulesetForKey } from "./rulesets.js";

export const COLORS = ["RED", "GREEN", "YELLOW", "BLUE"];

export const ROLL_REVEAL_TIMEOUT_MS = 4 * 60 * 1000;

export const RULESET_KEY = "CLASSIC_V1";
export const TEAM_RULESET_KEY = "TEAM_UP_V1";
export const TEAM_ASSIGNMENTS = Object.freeze(["A", "B", "A", "B"]);

export const RULESET = Object.freeze({
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

export const TEAM_RULESET = Object.freeze({
  ...RULESET,
  id: "ludoproof-team-v1",
  mode: "TEAM_UP",
  teams: Object.freeze(["A", "B"]),
  teamAssignments: TEAM_ASSIGNMENTS,
  friendlyCapture: false,
  partnerTurnHandoff: true,
  winCondition: "BOTH_PARTNERS_ALL_TOKENS_HOME",
});

export function isTeamUp(state) {
  return state?.matchMode === "TEAM_UP" || state?.teamMode === "TEAM_UP";
}

export function rulesetForState(state) {
  return isTeamUp(state) ? TEAM_RULESET : RULESET;
}

export function teamIdForSeat(state, seat) {
  if (!isTeamUp(state) || !Number.isInteger(seat) || seat < 0 || seat > 3) {
    return null;
  }
  return state.teamAssignments?.[seat] ?? TEAM_ASSIGNMENTS[seat] ?? null;
}

export function actingSeatForTurn(state) {
  const scheduledSeat = state?.turnSeat;
  if (!Number.isInteger(scheduledSeat)) return scheduledSeat;
  if (!isTeamUp(state)) return scheduledSeat;

  const scheduled = state.players?.[scheduledSeat];
  if (!scheduled || !playerFinished(scheduled)) return scheduledSeat;

  const partnerSeat = (scheduledSeat + 2) % 4;
  const partner = state.players?.[partnerSeat];
  if (partner && !playerFinished(partner)) return partnerSeat;
  return scheduledSeat;
}

export function newMatch({
  matchId,
  hostPlayerId,
  hostDisplayName,
  hostCharacterId = DEFAULT_CHARACTER_ID,
  hostPlayerType = "HUMAN",
  now,
  targetPlayerCount = null,
  matchMode = "ONLINE",
}) {
  const teamUp = matchMode === "TEAM_UP";
  const normalizedHostPlayerType = hostPlayerType === "BOT" ? "BOT" : "HUMAN";
  return {
    schemaVersion: 1,
    matchId,
    status: "WAITING",
    createdAt: now,
    updatedAt: now,
    revision: 1,
    hostPlayerId,
    targetPlayerCount,
    matchMode,
    ...(teamUp
      ? {
          teamAssignments: [...TEAM_ASSIGNMENTS],
          winnerTeamId: null,
        }
      : {}),
    players: [
      {
        playerId: hostPlayerId,
        displayName: hostDisplayName,
        characterId: publicCharacterId(hostCharacterId),
        playerType: normalizedHostPlayerType,
        color: COLORS[0],
        ...(teamUp ? { teamId: TEAM_ASSIGNMENTS[0] } : {}),
        tokenAuthHash: null,
        tokens: [-1, -1, -1, -1],
      },
    ],
    turnSeat: null,
    randomEventIndex: 0,
    consecutiveSixes: [0],
    pendingRoll: null,
    turnTimeoutCounts: {},
    turnTimeoutNotice: null,
    turnDeadlineAt: null,
    turnDeadlineSeat: null,
    winnerPlayerId: null,
    finishedAt: null,
  };
}

function colorForSeat(state, seat) {
  // Public two-player Online matches occupy opposing diagonal bases: host Red
  // at bottom-left and guest Yellow at top-right. Seat order and turn order
  // remain sequential; only the color/home quadrant assignment changes.
  if (
    state?.matchMode === "ONLINE" &&
    state?.targetPlayerCount === 2 &&
    seat === 1
  ) {
    return "YELLOW";
  }
  return COLORS[seat];
}

export function addPlayer(
  state,
  {
    playerId,
    displayName,
    characterId = DEFAULT_CHARACTER_ID,
    playerType = "HUMAN",
    tokenAuthHash,
    now,
  },
) {
  requireStatus(state, "WAITING");
  const playerLimit = Number.isInteger(state.targetPlayerCount)
    ? state.targetPlayerCount
    : 4;
  if (state.players.length >= playerLimit) {
    throw gameError("MATCH_FULL", "match already has all required players");
  }
  if (state.players.some((player) => player.playerId === playerId)) {
    throw gameError("PLAYER_EXISTS", "player already joined");
  }

  const next = clone(state);
  const seat = next.players.length;
  next.players.push({
    playerId,
    displayName,
    characterId: publicCharacterId(characterId),
    playerType: playerType === "BOT" ? "BOT" : "HUMAN",
    color: colorForSeat(next, seat),
    ...(isTeamUp(next) ? { teamId: teamIdForSeat(next, seat) } : {}),
    tokenAuthHash,
    tokens: [-1, -1, -1, -1],
  });
  next.consecutiveSixes.push(0);
  touch(next, now);
  return next;
}

export function attachHostAuth(state, tokenAuthHash, now) {
  const next = clone(state);
  next.players[0].tokenAuthHash = tokenAuthHash;
  touch(next, now);
  return next;
}

export function startMatch(state, playerId, now) {
  requireStatus(state, "WAITING");
  if (state.hostPlayerId !== playerId) {
    throw gameError("HOST_ONLY", "only the host can start the match");
  }
  if (state.players.length < 2) {
    throw gameError("NEED_MORE_PLAYERS", "at least two players are required");
  }
  if (
    Number.isInteger(state.targetPlayerCount) &&
    state.players.length !== state.targetPlayerCount
  ) {
    throw gameError(
      "WAITING_FOR_PLAYERS",
      "all invited seats must be filled before the match can start",
    );
  }
  if (
    isTeamUp(state) &&
    (state.targetPlayerCount !== 4 || state.players.length !== 4)
  ) {
    throw gameError(
      "TEAM_UP_REQUIRES_FOUR_PLAYERS",
      "team up requires exactly four players",
    );
  }

  const next = clone(state);
  next.status = "ACTIVE";
  next.startedAt = now;
  next.turnSeat = 0;
  next.pendingRoll = null;
  touch(next, now);
  return next;
}

export function assertCanCommitRoll(state, playerId) {
  requireStatus(state, "ACTIVE");
  const seat = seatForPlayer(state, playerId);
  const actingSeat = actingSeatForTurn(state);
  if (actingSeat !== seat) {
    throw gameError("NOT_YOUR_TURN", "it is not this player's turn");
  }
  if (state.pendingRoll) {
    throw gameError("ROLL_ALREADY_PENDING", "the current turn already has a roll");
  }
  return seat;
}

export function authoritativeStateForRandomness(state) {
  const base = {
    schemaVersion: state.schemaVersion,
    matchId: state.matchId,
    status: state.status,
    players: state.players.map((player) => ({
      playerId: player.playerId,
      color: player.color,
      tokens: [...player.tokens],
    })),
    turnSeat: state.turnSeat,
    randomEventIndex: state.randomEventIndex,
    consecutiveSixes: [...state.consecutiveSixes],
    winnerPlayerId: state.winnerPlayerId,
  };

  // Preserve the exact historical ONLINE proof-state shape. Character identity
  // is deliberately excluded because cosmetics must never alter proof/dice.
  // Team semantics are appended only for TEAM_UP so existing CLASSIC_V1 proofs
  // remain compatible.
  if (!isTeamUp(state)) return base;

  return {
    ...base,
    matchMode: "TEAM_UP",
    teamRulesetId: TEAM_RULESET.id,
    teamAssignments: [...(state.teamAssignments ?? TEAM_ASSIGNMENTS)],
    actingSeat: actingSeatForTurn(state),
    winnerTeamId: state.winnerTeamId ?? null,
  };
}

export function reserveRoll(state, {
  playerId,
  clientCommitment,
  eventIndex,
  actorHash,
  previousStateHash,
  rulesetHash,
  now,
}) {
  const seat = assertCanCommitRoll(state, playerId);
  if (eventIndex !== state.randomEventIndex) {
    throw gameError("EVENT_INDEX_DRIFT", "random event index drifted");
  }

  const next = clone(state);
  next.pendingRoll = {
    status: "CREATING",
    seat,
    ...(isTeamUp(state) ? { scheduledSeat: state.turnSeat } : {}),
    playerId,
    clientCommitment,
    eventIndex,
    eventId: `roll:${eventIndex}`,
    actorHash,
    previousStateHash,
    rulesetHash,
    roundId: null,
    serverCommitment: null,
    proofDigest: null,
    outcome: null,
    legalTokenIndexes: null,
    createdAt: now,
    resolvedAt: null,
    committedAt: null,
    revealDeadlineAt: now + ROLL_REVEAL_TIMEOUT_MS,
  };
  touch(next, now);
  return next;
}

export function attachRoundCommitment(state, {
  roundId,
  serverCommitment,
  revealDeadlineAt,
  now,
}) {
  if (!state.pendingRoll || state.pendingRoll.status !== "CREATING") {
    throw gameError("ROLL_STATE_CONFLICT", "roll is no longer waiting for a server commitment");
  }

  const next = clone(state);
  next.pendingRoll.status = "COMMITTED";
  next.pendingRoll.roundId = roundId;
  next.pendingRoll.serverCommitment = serverCommitment;
  next.pendingRoll.committedAt = now;
  next.pendingRoll.revealDeadlineAt = Number.isSafeInteger(revealDeadlineAt)
    ? revealDeadlineAt
    : now + ROLL_REVEAL_TIMEOUT_MS;
  next.randomEventIndex += 1;
  touch(next, now);
  return next;
}

export function forfeitTimedOutRoll(state, {
  now,
  reason = "REVEAL_TIMEOUT",
}) {
  requireStatus(state, "ACTIVE");
  const pending = state.pendingRoll;
  if (!pending) {
    throw gameError("NO_PENDING_ROLL", "there is no pending roll to forfeit");
  }
  if (!["CREATING", "COMMITTED", "RESOLVING"].includes(pending.status)) {
    throw gameError("ROLL_STATE_CONFLICT", "only an unresolved roll can time out");
  }
  if (!Number.isSafeInteger(pending.revealDeadlineAt) || now < pending.revealDeadlineAt) {
    throw gameError("ROLL_NOT_TIMED_OUT", "pending roll has not reached its reveal deadline");
  }

  const next = clone(state);
  const timedOut = structuredClone(next.pendingRoll);

  if (
    timedOut.status === "CREATING" &&
    next.randomEventIndex === timedOut.eventIndex
  ) {
    next.randomEventIndex += 1;
  }

  next.consecutiveSixes[timedOut.seat] = 0;
  next.pendingRoll = null;
  advanceTurn(next);
  touch(next, now);

  return {
    state: next,
    timedOutRoll: {
      ...timedOut,
      status: "TIMED_OUT",
      timeoutReason: reason,
      timedOutAt: now,
      replacementRoundAllowed: false,
    },
  };
}

/**
 * Expires an unrolled turn for a ruleset that opted into the v4 deadline.
 * Only the Durable Object alarm path calls this function.
 */
export function forfeitTimedOutTurn(state, { now }) {
  requireStatus(state, "ACTIVE");
  if (state.pendingRoll != null) {
    throw gameError("ROLL_ALREADY_PENDING", "a roll is already in progress");
  }
  const ruleset =
    state.rulesetKey == null ? null : rulesetForKey(state.rulesetKey);
  if (
    !Number.isSafeInteger(ruleset?.turnRollTimeoutMs) ||
    !Number.isSafeInteger(ruleset?.maxMissedRollsBeforeForfeit)
  ) {
    throw gameError("TURN_TIMEOUT_DISABLED", "this ruleset has no turn-roll deadline");
  }
  if (!Number.isSafeInteger(state.turnDeadlineAt) || now < state.turnDeadlineAt) {
    throw gameError("TURN_NOT_TIMED_OUT", "turn deadline has not elapsed");
  }
  const seat = actingSeatForTurn(state);
  if (!Number.isInteger(seat) || seat < 0 || seat >= state.players.length) {
    throw gameError("INVALID_SEAT", "active turn seat is invalid");
  }
  if (state.turnDeadlineSeat !== seat) {
    throw gameError("TURN_DEADLINE_STALE", "deadline does not match the active player");
  }

  const next = clone(state);
  const player = next.players[seat];
  const count = Math.max(0, Number(next.turnTimeoutCounts?.[player.playerId]) || 0) + 1;
  next.turnTimeoutCounts = { ...(next.turnTimeoutCounts ?? {}), [player.playerId]: count };
  next.turnTimeoutNotice = { playerId: player.playerId, count, at: now };

  let forfeited = false;
  if (count >= ruleset.maxMissedRollsBeforeForfeit) {
    forfeited = true;
    player.forfeitedAt = now;
    player.forfeitReason = "TURN_TIMEOUT";
    player.tokens = [-1, -1, -1, -1];
    if (Array.isArray(next.consecutiveSixes)) next.consecutiveSixes[seat] = 0;

    if (isTeamUp(next)) {
      const losingTeam = teamIdForSeat(next, seat);
      // Team Up is a shared 2-vs-2 result. Forfeiting one partner forfeits
      // the whole team; persist both partners as forfeited, not just the actor.
      next.players.forEach((teammate, teammateSeat) => {
        if (teamIdForSeat(next, teammateSeat) !== losingTeam) return;
        teammate.forfeitedAt = now;
        teammate.forfeitReason =
          teammateSeat === seat ? "TURN_TIMEOUT" : "TEAMMATE_TURN_TIMEOUT";
        teammate.tokens = [-1, -1, -1, -1];
        if (Array.isArray(next.consecutiveSixes)) next.consecutiveSixes[teammateSeat] = 0;
      });
      next.status = "FINISHED";
      next.winnerPlayerId = null;
      next.winnerTeamId = losingTeam === "A" ? "B" : "A";
      next.finishedAt = now;
      next.leaderboardRecordedAt = now;
      next.pendingRoll = null;
    } else {
      const activeSeats = next.players
        .map((candidate, index) => ({ candidate, index }))
        .filter(({ candidate }) => !Number.isSafeInteger(candidate.forfeitedAt) &&
          !(next.finishOrderPlayerIds ?? []).includes(candidate.playerId));
      if (activeSeats.length <= 1) {
        const winner = activeSeats[0] ?? null;
        next.status = "FINISHED";
        if (winner && Array.isArray(next.finishOrderPlayerIds) && !next.finishOrderPlayerIds.includes(winner.candidate.playerId)) {
          next.finishOrderPlayerIds.push(winner.candidate.playerId);
        }
        next.winnerPlayerId = next.finishOrderPlayerIds?.[0] ?? winner?.candidate.playerId ?? null;
        next.winnerTeamId = null;
        next.turnSeat = winner?.index ?? seat;
        next.finishedAt = now;
        next.pendingRoll = null;
      } else {
        advanceTurn(next);
      }
    }
  } else {
    advanceTurn(next);
  }

  next.turnDeadlineAt = null;
  next.turnDeadlineSeat = null;
  touch(next, now);
  return { state: next, playerId: player.playerId, count, forfeited };
}

const LAST_RELATIVE_TRACK_POSITION = 50;
const RESERVED_HOME_ENTRY_POSITION = 51;
const FIRST_HOME_LANE_POSITION = 52;

// Position 51 is a compatibility gap from the old off-by-one path encoding.
// New moves leave the shared track after relative position 50 and jump directly
// to encoded home-lane position 52 on the next movement step.
export function destinationForRoll(position, roll) {
  if (!Number.isInteger(roll) || roll < 1 || roll > 6) return null;
  if (position === -1) return roll === 6 ? 0 : null;
  if (position === RULESET.homePosition) return null;

  const normalized =
    position === RESERVED_HOME_ENTRY_POSITION
      ? FIRST_HOME_LANE_POSITION
      : position;
  if (normalized < 0 || normalized > 56) return null;

  let destination = normalized + roll;
  if (
    normalized <= LAST_RELATIVE_TRACK_POSITION &&
    destination > LAST_RELATIVE_TRACK_POSITION
  ) {
    destination += 1;
  }
  return destination <= RULESET.homePosition ? destination : null;
}

export function legalTokenIndexes(state, seat, roll) {
  if (!Number.isInteger(roll) || roll < 1 || roll > 6) {
    throw gameError("INVALID_ROLL", "roll must be between 1 and 6");
  }
  const player = state.players[seat];
  if (!player) throw gameError("INVALID_SEAT", "player seat is invalid");

  const legal = [];
  player.tokens.forEach((position, index) => {
    if (destinationForRoll(position, roll) !== null) {
      legal.push(index);
    }
  });
  return legal;
}

export function registerResolvedRoll(state, {
  outcome,
  proofDigest,
  now,
}) {
  if (!state.pendingRoll || !["COMMITTED", "RESOLVING"].includes(state.pendingRoll.status)) {
    throw gameError("ROLL_STATE_CONFLICT", "roll is not ready to resolve");
  }
  const seat = state.pendingRoll.seat;
  if (isTeamUp(state)) {
    if (
      state.pendingRoll.scheduledSeat !== state.turnSeat ||
      seat !== actingSeatForTurn(state)
    ) {
      throw gameError("TURN_STATE_CONFLICT", "team turn changed before roll resolution");
    }
  } else if (seat !== state.turnSeat) {
    throw gameError("TURN_STATE_CONFLICT", "turn changed before roll resolution");
  }

  const next = clone(state);
  if (outcome === 6) {
    next.consecutiveSixes[seat] = (next.consecutiveSixes[seat] ?? 0) + 1;
  } else {
    next.consecutiveSixes[seat] = 0;
  }

  const ruleset = rulesetForState(next);
  const thirdSix =
    ruleset.threeConsecutiveSixesForfeit &&
    outcome === 6 &&
    next.consecutiveSixes[seat] >= 3;

  if (thirdSix) {
    next.consecutiveSixes[seat] = 0;
    next.pendingRoll = null;
    advanceTurn(next);
    touch(next, now);
    return {
      state: next,
      awaitingMove: false,
      forfeitedThirdSix: true,
      legalTokenIndexes: [],
    };
  }

  const legal = legalTokenIndexes(next, seat, outcome);
  if (legal.length === 0) {
    next.pendingRoll = null;
    if (!(ruleset.extraTurnOnSix && outcome === 6)) {
      advanceTurn(next);
    }
    touch(next, now);
    return {
      state: next,
      awaitingMove: false,
      forfeitedThirdSix: false,
      legalTokenIndexes: [],
    };
  }

  next.pendingRoll.status = "RESOLVED";
  next.pendingRoll.outcome = outcome;
  next.pendingRoll.proofDigest = proofDigest;
  next.pendingRoll.legalTokenIndexes = legal;
  next.pendingRoll.resolvedAt = now;
  touch(next, now);

  return {
    state: next,
    awaitingMove: true,
    forfeitedThirdSix: false,
    legalTokenIndexes: legal,
  };
}

export function applyMove(state, { playerId, tokenIndex, now }) {
  requireStatus(state, "ACTIVE");
  const pending = state.pendingRoll;
  if (!pending || pending.status !== "RESOLVED") {
    throw gameError("NO_RESOLVED_ROLL", "a verified roll is required before moving");
  }
  const seat = seatForPlayer(state, playerId);
  const expectedSeat = actingSeatForTurn(state);
  if (
    seat !== expectedSeat ||
    pending.seat !== seat ||
    (isTeamUp(state) && pending.scheduledSeat !== state.turnSeat)
  ) {
    throw gameError("NOT_YOUR_TURN", "it is not this player's move");
  }
  if (!pending.legalTokenIndexes.includes(tokenIndex)) {
    throw gameError("ILLEGAL_TOKEN_MOVE", "selected token cannot use this roll");
  }

  const next = clone(state);
  const player = next.players[seat];
  const roll = pending.outcome;
  const current = player.tokens[tokenIndex];
  const destination = destinationForRoll(current, roll);
  if (destination === null) {
    throw gameError("ILLEGAL_TOKEN_MOVE", "verified roll exceeds the home path");
  }
  player.tokens[tokenIndex] = destination;
  next.playerMoveCounts = { ...(next.playerMoveCounts ?? {}), [player.playerId]: (next.playerMoveCounts?.[player.playerId] ?? 0) + 1 };

  const captures = captureOpponents(next, seat, destination);
  const ruleset = rulesetForState(next);
  next.pendingRoll = null;

  if (isTeamUp(next)) {
    const teamId = teamIdForSeat(next, seat);
    if (teamFinished(next, teamId)) {
      next.status = "FINISHED";
      next.winnerPlayerId = null;
      next.winnerTeamId = teamId;
      next.finishedAt = now;
      // The legacy leaderboard is individual-winner based. Mark this result as
      // intentionally handled so it can never record one teammate as the sole
      // winner. Team ranking can be added later with a dedicated ledger.
      next.leaderboardRecordedAt = now;
      touch(next, now);
      return {
        state: next,
        captures,
        extraTurn: false,
        winnerPlayerId: null,
        winnerTeamId: teamId,
      };
    }
  } else if (playerFinished(player)) {
    if (Array.isArray(next.finishOrderPlayerIds) && !next.finishOrderPlayerIds.includes(player.playerId)) {
      next.finishOrderPlayerIds.push(player.playerId);
    }
    const remaining = next.players.filter(candidate =>
      !Number.isSafeInteger(candidate.forfeitedAt) &&
      !(next.finishOrderPlayerIds ?? []).includes(candidate.playerId));
    if (!Array.isArray(next.finishOrderPlayerIds) || remaining.length <= 1) {
      if (Array.isArray(next.finishOrderPlayerIds) && remaining.length === 1) {
        next.finishOrderPlayerIds.push(remaining[0].playerId);
      }
      next.status = "FINISHED";
      next.winnerPlayerId = next.finishOrderPlayerIds?.[0] ?? player.playerId;
      next.finishedAt = now;
    } else {
      // V6: first finisher is the winner, but play continues to determine all places.
      advanceTurn(next);
    }
    touch(next, now);
    return {
      state: next,
      captures,
      extraTurn: false,
      winnerPlayerId: next.winnerPlayerId,
      winnerTeamId: null,
    };
  }

  const reachedHome = destination === ruleset.homePosition;
  const extraTurn =
    reachedHome ||
    (ruleset.extraTurnOnSix && roll === 6) ||
    (ruleset.extraTurnOnCapture && captures > 0);

  if (!extraTurn) {
    advanceTurn(next);
  }

  touch(next, now);
  return {
    state: next,
    captures,
    extraTurn,
    winnerPlayerId: null,
    winnerTeamId: next.winnerTeamId ?? null,
  };
}

export function publicState(state) {
  const teamUp = isTeamUp(state);
  const ruleset = rulesetForState(state);
  return {
    schemaVersion: state.schemaVersion,
    matchId: state.matchId,
    status: state.status,
    createdAt: state.createdAt,
    updatedAt: state.updatedAt,
    revision: state.revision,
    hostPlayerId: state.hostPlayerId,
    targetPlayerCount: Number.isInteger(state.targetPlayerCount)
      ? state.targetPlayerCount
      : null,
    matchMode: teamUp ? "TEAM_UP" : (state.matchMode ?? "ONLINE"),
    ...(teamUp
      ? {
          teamAssignments: [...(state.teamAssignments ?? TEAM_ASSIGNMENTS)],
          actingSeat: actingSeatForTurn(state),
          winnerTeamId: state.winnerTeamId ?? null,
        }
      : {}),
    players: state.players.map((player, seat) => ({
      playerId: player.playerId,
      displayName: player.displayName,
      characterId: publicCharacterId(player.characterId),
      playerType: player.playerType === "BOT" ? "BOT" : "HUMAN",
      color: player.color,
      seat,
      turnTimeoutCount: Math.max(0, Number(state.turnTimeoutCounts?.[player.playerId]) || 0),
      forfeited: Number.isSafeInteger(player.forfeitedAt),
      ...(teamUp ? { teamId: teamIdForSeat(state, seat) } : {}),
      tokens: [...player.tokens],
    })),
    turnTimeoutNotice: state.turnTimeoutNotice &&
      typeof state.turnTimeoutNotice.playerId === "string" &&
      Number.isSafeInteger(state.turnTimeoutNotice.count) &&
      Number.isSafeInteger(state.turnTimeoutNotice.at)
      ? { ...state.turnTimeoutNotice }
      : null,
    turnSeat: state.turnSeat,
    randomEventIndex: state.randomEventIndex,
    consecutiveSixes: [...state.consecutiveSixes],
    pendingRoll: state.pendingRoll
      ? {
          status: state.pendingRoll.status,
          seat: state.pendingRoll.seat,
          ...(teamUp ? { scheduledSeat: state.pendingRoll.scheduledSeat } : {}),
          eventIndex: state.pendingRoll.eventIndex,
          eventId: state.pendingRoll.eventId,
          roundId: state.pendingRoll.roundId,
          serverCommitment: state.pendingRoll.serverCommitment,
          proofDigest: state.pendingRoll.proofDigest,
          outcome: state.pendingRoll.outcome,
          legalTokenIndexes: state.pendingRoll.legalTokenIndexes,
          clientCommitment: state.pendingRoll.clientCommitment,
          revealDeadlineAt: state.pendingRoll.revealDeadlineAt,
        }
      : null,
    winnerPlayerId: state.winnerPlayerId,
    ...(Array.isArray(state.finishOrderPlayerIds) ? { finishOrderPlayerIds: [...state.finishOrderPlayerIds] } : {}),
    rulesetId: ruleset.id,
  };
}

export function globalCellFor(color, relativePosition) {
  if (relativePosition < 0 || relativePosition > LAST_RELATIVE_TRACK_POSITION) return null;
  return (RULESET.startOffsets[color] + relativePosition) % RULESET.boardTrackCells;
}

function captureOpponents(state, movingSeat, destination) {
  if (destination < 0 || destination > LAST_RELATIVE_TRACK_POSITION) return 0;
  const mover = state.players[movingSeat];
  const cell = globalCellFor(mover.color, destination);
  if (RULESET.safeGlobalCells.includes(cell)) return 0;

  const movingTeam = teamIdForSeat(state, movingSeat);
  const activeRuleset =
    state.rulesetKey == null ? null : rulesetForKey(state.rulesetKey);
  if (
    isTeamUp(state) &&
    activeRuleset?.teamStackProtection ===
      "SAFE_WHEN_TEAM_HAS_AT_LEAST_TWO_TOKENS_ON_CELL"
  ) {
    const opposingTeamTokensOnCell = state.players.reduce(
      (total, opponent, seat) => {
        if (teamIdForSeat(state, seat) === movingTeam) return total;
        return total + opponent.tokens.filter(
          (position) => globalCellFor(opponent.color, position) === cell,
        ).length;
      },
      0,
    );
    if (opposingTeamTokensOnCell >= 2) return 0;
  }

  let captures = 0;
  state.players.forEach((opponent, seat) => {
    if (seat === movingSeat) return;
    if (isTeamUp(state) && teamIdForSeat(state, seat) === movingTeam) return;

    const matchingTokenIndexes = opponent.tokens.reduce(
      (indexes, position, index) => {
        if (globalCellFor(opponent.color, position) === cell) {
          indexes.push(index);
        }
        return indexes;
      },
      [],
    );

    if (matchingTokenIndexes.length !== 1) return;

    opponent.tokens[matchingTokenIndexes[0]] = -1;
    captures += 1;
  });
  return captures;
}

function teamFinished(state, teamId) {
  if (!teamId) return false;
  const members = state.players.filter(
    (_, seat) => teamIdForSeat(state, seat) === teamId,
  );
  return members.length === 2 && members.every(playerFinished);
}

function playerFinished(player) {
  return Array.isArray(player?.tokens) &&
    player.tokens.length === 4 &&
    player.tokens.every((position) => position === RULESET.homePosition);
}

function advanceTurn(state) {
  const count = state.players.length;
  if (count < 1) return;
  const start = Number.isInteger(state.turnSeat) ? state.turnSeat : -1;
  for (let offset = 1; offset <= count; offset += 1) {
    const candidate = ((start + offset) % count + count) % count;
    if (!Number.isSafeInteger(state.players[candidate]?.forfeitedAt) &&
        !(state.finishOrderPlayerIds ?? []).includes(state.players[candidate]?.playerId)) {
      state.turnSeat = candidate;
      return;
    }
  }
  state.turnSeat = start >= 0 && start < count ? start : 0;
}

function seatForPlayer(state, playerId) {
  const seat = state.players.findIndex((player) => player.playerId === playerId);
  if (seat < 0) throw gameError("PLAYER_NOT_FOUND", "player is not part of this match");
  return seat;
}

function requireStatus(state, expected) {
  if (state.status !== expected) {
    throw gameError("INVALID_MATCH_STATUS", `match must be ${expected}`);
  }
}

function touch(state, now) {
  state.updatedAt = now;
  state.revision += 1;

  const activeRuleset =
    state.rulesetKey == null ? null : rulesetForKey(state.rulesetKey);
  const timeoutMs = activeRuleset?.turnRollTimeoutMs;
  if (
    state.status === "ACTIVE" &&
    state.pendingRoll == null &&
    Number.isSafeInteger(timeoutMs) &&
    timeoutMs > 0
  ) {
    const actingSeat = actingSeatForTurn(state);
    if (
      state.turnDeadlineSeat !== actingSeat ||
      !Number.isSafeInteger(state.turnDeadlineAt)
    ) {
      state.turnDeadlineSeat = actingSeat;
      state.turnDeadlineAt = now + timeoutMs;
    }
  } else {
    state.turnDeadlineAt = null;
    state.turnDeadlineSeat = null;
  }
}

function clone(value) {
  return structuredClone(value);
}

export function gameError(code, message, status = 409) {
  const error = new Error(message);
  error.code = code;
  error.status = status;
  return error;
}
