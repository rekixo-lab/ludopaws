import test from "node:test";
import assert from "node:assert/strict";
import {
  MatchRoom,
  FORCED_MOVE_RECOVERY_MS,
  MOVE_SELECTION_TIMEOUT_MS,
  TURN_ROLL_TIMEOUT_MS,
  forcedAutomaticTokenIndex,
} from "../src/match-room-lifecycle.js";

import { registerResolvedRoll } from "../src/game.js";

class FakeStorage {
  constructor(state) {
    this.values = new Map([["match-state", structuredClone(state)]]);
    this.alarmAt = null;
  }

  async get(key) {
    const value = this.values.get(key);
    return value == null ? value : structuredClone(value);
  }

  async put(key, value) {
    this.values.set(key, structuredClone(value));
  }

  async setAlarm(timestamp) {
    this.alarmAt = timestamp;
  }

  async deleteAll() {
    this.values.clear();
  }
}

function stateWithResolvedMove(resolvedAt) {
  return {
    schemaVersion: 1,
    matchId: "LPABCDEFGH",
    status: "ACTIVE",
    createdAt: resolvedAt - 5_000,
    updatedAt: resolvedAt,
    revision: 7,
    hostPlayerId: "p1",
    targetPlayerCount: 2,
    matchMode: "ONLINE",
    rulesetKey: "CLASSIC_V4",
    players: [
      {
        playerId: "p1",
        displayName: "One",
        characterId: "duck",
        color: "RED",
        tokenAuthHash: "a".repeat(64),
        tokens: [0, -1, -1, -1],
      },
      {
        playerId: "p2",
        displayName: "Two",
        characterId: "dog",
        color: "GREEN",
        tokenAuthHash: "b".repeat(64),
        tokens: [-1, -1, -1, -1],
      },
    ],
    turnSeat: 0,
    randomEventIndex: 1,
    consecutiveSixes: [1, 0],
    pendingRoll: {
      status: "RESOLVED",
      seat: 0,
      playerId: "p1",
      eventIndex: 0,
      eventId: "roll:0",
      roundId: "round-0",
      clientCommitment: "c".repeat(64),
      serverCommitment: "d".repeat(64),
      proofDigest: "e".repeat(64),
      outcome: 6,
      legalTokenIndexes: [0, 1],
      resolvedAt,
      revealDeadlineAt: resolvedAt + 4 * 60 * 1000,
    },
    winnerPlayerId: null,
    finishedAt: null,
    history: [
      {
        eventIndex: 0,
        playerId: "p1",
        status: "RESOLVED",
        outcome: 6,
        fairnessDigest: "f".repeat(64),
      },
    ],
  };
}

function makeRoom(state) {
  const storage = new FakeStorage(state);
  const ctx = {
    storage,
    getWebSockets() {
      return [];
    },
  };
  return {
    storage,
    room: new MatchRoom(ctx, {}),
  };
}

test("resolved roll schedules the authoritative move-selection deadline", async () => {
  const resolvedAt = Date.now();
  const { room, storage } = makeRoom(stateWithResolvedMove(resolvedAt));

  await room.alarm();

  assert.equal(
    storage.alarmAt,
    resolvedAt + MOVE_SELECTION_TIMEOUT_MS,
  );
  const stored = await storage.get("match-state");
  assert.equal(stored.pendingRoll.status, "RESOLVED");
  assert.equal(stored.turnSeat, 0);
});

for (const [matchMode, rulesetKey] of [
  ["ONLINE", "CLASSIC_V7"], ["FRIENDS", "CLASSIC_V7"], ["TEAM_UP", "TEAM_UP_V6"],
]) {
  test(`${matchMode} recovers a third-roll comeback if the client never moves`, async () => {
    const now = Date.now();
    const resolvedAt = now - FORCED_MOVE_RECOVERY_MS - 1_000;
    const state = stateWithResolvedMove(resolvedAt);
    state.matchMode = matchMode;
    state.rulesetKey = rulesetKey;
    state.players[0].tokens = [57, -1, -1, -1];
    if (matchMode === "TEAM_UP") {
      state.targetPlayerCount = 4;
      state.players.push(
        { playerId: "p3", color: "YELLOW", tokens: [-1, -1, -1, -1] },
        { playerId: "p4", color: "BLUE", tokens: [-1, -1, -1, -1] },
      );
      state.pendingRoll.scheduledSeat = 0;
    }
    state.openingRollConsumed = state.players.map(() => true);
    state.comebackWaits = state.players.map((_, seat) => seat === 0 ? 2 : -1);
    state.consecutiveSixes = state.players.map(() => 0);
    state.pendingRoll.status = "COMMITTED";
    const proofDigest = state.pendingRoll.proofDigest;
    const resolved = registerResolvedRoll(state, { outcome: 3, proofDigest, now: resolvedAt });
    assert.equal(resolved.comebackBonusApplied, true);
    assert.deepEqual(resolved.legalTokenIndexes, [1]);
    resolved.state.history[0] = {
      ...resolved.state.history[0], proofDigest, outcome: 3, comebackBonusApplied: true,
    };
    const { room, storage } = makeRoom(resolved.state);

    const invalid = structuredClone(resolved.state);
    invalid.pendingRoll.legalTokenIndexes = [2];
    assert.equal(forcedAutomaticTokenIndex(invalid), null);

    await room.alarm();

    const stored = await storage.get("match-state");
    assert.deepEqual(stored.players[0].tokens, [57, 0, -1, -1]);
    assert.equal(stored.comebackWaits[0], -1);
    assert.equal(stored.pendingRoll, null);
    assert.equal(stored.turnSeat, 1);
    assert.equal(stored.history[0].outcome, 3);
    assert.equal(stored.history[0].extraTurn, false);
    assert.equal(stored.history[0].autoMoveSource, "SERVER_FORCED_MOVE_RECOVERY");
  });
}

test("expired resolved roll forfeits the move and advances the turn", async () => {
  const resolvedAt = Date.now() - MOVE_SELECTION_TIMEOUT_MS - 5_000;
  const { room, storage } = makeRoom(stateWithResolvedMove(resolvedAt));

  await room.alarm();

  const stored = await storage.get("match-state");
  assert.equal(stored.status, "ACTIVE");
  assert.equal(stored.pendingRoll, null);
  assert.equal(stored.turnSeat, 1);
  assert.deepEqual(stored.consecutiveSixes, [0, 0]);
  assert.equal(stored.revision, 8);
  assert.equal(
    stored.history[0].moveTimeoutReason,
    "MOVE_SELECTION_TIMEOUT",
  );
  assert.ok(Number.isSafeInteger(stored.history[0].moveTimedOutAt));
  assert.equal(stored.turnDeadlineAt, stored.updatedAt + TURN_ROLL_TIMEOUT_MS);
  assert.equal(stored.turnDeadlineSeat, 1);
});

test("legacy rulesets do not inherit the V4 inactivity forfeit policy", async () => {
  const now = Date.now();
  const state = stateWithResolvedMove(now - TURN_ROLL_TIMEOUT_MS - 1);
  state.pendingRoll = null;
  state.rulesetKey = "CLASSIC_V3";
  state.updatedAt = now - TURN_ROLL_TIMEOUT_MS - 1;
  const { room, storage } = makeRoom(state);

  await room.alarm();

  const stored = await storage.get("match-state");
  assert.equal(stored.status, "ACTIVE");
  assert.equal(stored.turnSeat, 0);
  assert.equal(stored.turnTimeoutNotice, undefined);
  assert.equal(storage.alarmAt, stored.updatedAt + 7 * 24 * 60 * 60 * 1000);
});

test("an unrolled turn times out after 15 seconds, warns and advances", async () => {
  const now = Date.now();
  const state = stateWithResolvedMove(now - TURN_ROLL_TIMEOUT_MS - 1);
  state.pendingRoll = null;
  state.updatedAt = now - TURN_ROLL_TIMEOUT_MS - 1;
  state.turnTimeoutCounts = {};
  const { room, storage } = makeRoom(state);

  await room.alarm();

  const stored = await storage.get("match-state");
  assert.equal(stored.status, "ACTIVE");
  assert.equal(stored.turnSeat, 1);
  assert.equal(stored.turnTimeoutCounts.p1, 1);
  assert.equal(stored.turnTimeoutNotice.playerId, "p1");
  assert.equal(stored.turnTimeoutNotice.count, 1);
  assert.ok(stored.turnTimeoutNotice.at >= now);
  assert.equal(storage.alarmAt, stored.updatedAt + TURN_ROLL_TIMEOUT_MS);
});

test("four missed dice turns forfeit a classic player and finish a two-player match", async () => {
  const now = Date.now();
  const state = stateWithResolvedMove(now - TURN_ROLL_TIMEOUT_MS - 1);
  state.pendingRoll = null;
  state.updatedAt = now - TURN_ROLL_TIMEOUT_MS - 1;
  state.turnTimeoutCounts = { p1: 3 };
  const { room, storage } = makeRoom(state);

  await room.alarm();

  const stored = await storage.get("match-state");
  assert.equal(stored.status, "FINISHED");
  assert.equal(stored.players[0].forfeitedAt, stored.turnTimeoutNotice.at);
  assert.equal(stored.turnTimeoutNotice.count, 4);
  assert.equal(stored.winnerPlayerId, "p2");
  assert.equal(stored.winnerTeamId, null);
});

test("Team Up timeout advances from the scheduled seat when its finished partner was acting", async () => {
  const now = Date.now();
  const state = stateWithResolvedMove(now - TURN_ROLL_TIMEOUT_MS - 1);
  state.matchMode = "TEAM_UP";
  state.rulesetKey = "TEAM_UP_V4";
  state.teamAssignments = ["A", "B", "A", "B"];
  state.targetPlayerCount = 4;
  state.players[0].teamId = "A";
  state.players[0].tokens = [57, 57, 57, 57];
  state.players[1].teamId = "B";
  state.players.push(
    {
      playerId: "p3", displayName: "Three", characterId: "goat", playerType: "HUMAN",
      color: "YELLOW", teamId: "A", tokenAuthHash: "c".repeat(64), tokens: [-1, -1, -1, -1],
    },
    {
      playerId: "p4", displayName: "Four", characterId: "cat", playerType: "HUMAN",
      color: "BLUE", teamId: "B", tokenAuthHash: "d".repeat(64), tokens: [-1, -1, -1, -1],
    },
  );
  state.consecutiveSixes = [0, 0, 0, 0];
  state.turnTimeoutCounts = {};
  state.pendingRoll = null;
  state.turnSeat = 0;
  state.updatedAt = now - TURN_ROLL_TIMEOUT_MS - 1;
  const { room, storage } = makeRoom(state);

  await room.alarm();

  const stored = await storage.get("match-state");
  assert.equal(stored.turnTimeoutCounts.p3, 1);
  assert.equal(stored.turnTimeoutNotice.playerId, "p3");
  assert.equal(stored.turnSeat, 1);
});

test("four missed dice turns in Team Up award the other team", async () => {
  const now = Date.now();
  const state = stateWithResolvedMove(now - TURN_ROLL_TIMEOUT_MS - 1);
  state.matchMode = "TEAM_UP";
  state.rulesetKey = "TEAM_UP_V4";
  state.teamAssignments = ["A", "B", "A", "B"];
  state.targetPlayerCount = 4;
  state.players[0].teamId = "A";
  state.players[1].teamId = "B";
  state.players.push(
    {
      playerId: "p3", displayName: "Three", characterId: "goat", playerType: "HUMAN",
      color: "YELLOW", teamId: "A", tokenAuthHash: "c".repeat(64), tokens: [-1, -1, -1, -1],
    },
    {
      playerId: "p4", displayName: "Four", characterId: "cat", playerType: "HUMAN",
      color: "BLUE", teamId: "B", tokenAuthHash: "d".repeat(64), tokens: [-1, -1, -1, -1],
    },
  );
  state.consecutiveSixes = [0, 0, 0, 0];
  state.turnTimeoutCounts = { p1: 3 };
  state.pendingRoll = null;
  state.updatedAt = now - TURN_ROLL_TIMEOUT_MS - 1;
  const { room, storage } = makeRoom(state);

  await room.alarm();

  const stored = await storage.get("match-state");
  assert.equal(stored.status, "FINISHED");
  assert.equal(stored.winnerPlayerId, null);
  assert.equal(stored.winnerTeamId, "B");
  assert.equal(stored.turnTimeoutNotice.count, 4);
});

test("move timeout skips players that already forfeited", async () => {
  const resolvedAt = Date.now() - MOVE_SELECTION_TIMEOUT_MS - 5_000;
  const state = stateWithResolvedMove(resolvedAt);
  state.targetPlayerCount = 4;
  state.players.push(
    {
      playerId: "p3",
      displayName: "Three",
      characterId: "goat",
      color: "YELLOW",
      tokenAuthHash: "c".repeat(64),
      tokens: [-1, -1, -1, -1],
    },
    {
      playerId: "p4",
      displayName: "Four",
      characterId: "cat",
      color: "BLUE",
      tokenAuthHash: "d".repeat(64),
      tokens: [-1, -1, -1, -1],
    },
  );
  state.players[1].forfeitedAt = Date.now() - 10_000;
  state.consecutiveSixes = [1, 0, 0, 0];

  const { room, storage } = makeRoom(state);
  await room.alarm();

  const stored = await storage.get("match-state");
  assert.equal(stored.turnSeat, 2);
});
