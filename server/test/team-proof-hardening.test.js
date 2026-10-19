import test from "node:test";
import assert from "node:assert/strict";
import {
  newMatch,
} from "../src/game.js";
import {
  rulesetHashForState,
} from "../src/match-room.js";
import {
  MatchRoom as TeamMatchRoom,
} from "../src/match-room-team.js";

const CLASSIC_RULESET_HASH =
  "9dabc40bc9652695a308bc476fb6b3d9e4bc567b24ade82359876245e9b66e07";
const TEAM_RULESET_HASH =
  "f27000c749cb19e5b2e5cb259f1c04610e90be516fe070652bd3ffe2bb98342d";

class TestStorage {
  constructor() {
    this.values = new Map();
    this.putCount = 0;
    this.alarm = null;
  }

  async get(key) {
    const value = this.values.get(key);
    return value == null ? value : structuredClone(value);
  }

  async put(key, value) {
    this.putCount += 1;
    this.values.set(key, structuredClone(value));
  }

  async setAlarm(timestamp) {
    this.alarm = timestamp;
  }
}

function roomHarness() {
  const storage = new TestStorage();
  const ctx = {
    storage,
    getWebSockets() {
      return [];
    },
  };
  const env = {
    LUDOPROOF_SESSION_HMAC_KEY: "t".repeat(32),
  };
  return {
    storage,
    room: new TeamMatchRoom(ctx, env),
  };
}

function createRequest({
  matchMode = "TEAM_UP",
  targetPlayerCount = 4,
} = {}) {
  return new Request("https://match/create", {
    method: "POST",
    headers: {
      "content-type": "application/json",
    },
    body: JSON.stringify({
      displayName: "Red",
      characterId: "duck",
      matchId: "LPABCDEFGH",
      clientRequestId: "00000000-0000-4000-8000-000000000001",
      targetPlayerCount,
      matchMode,
    }),
  });
}

test("classic ruleset digest remains frozen", async () => {
  const state = newMatch({
    matchId: "LPQWERTY23",
    hostPlayerId: "p_host",
    hostDisplayName: "Host",
    now: 1,
    targetPlayerCount: 2,
    matchMode: "ONLINE",
  });

  assert.equal(
    await rulesetHashForState(state),
    CLASSIC_RULESET_HASH,
  );
});

test("Team Up proof binds the frozen team ruleset digest", async () => {
  const state = newMatch({
    matchId: "LPABCDEFGH",
    hostPlayerId: "p_host",
    hostDisplayName: "Host",
    now: 1,
    targetPlayerCount: 4,
    matchMode: "TEAM_UP",
  });

  assert.equal(
    await rulesetHashForState(state),
    TEAM_RULESET_HASH,
  );
  assert.notEqual(TEAM_RULESET_HASH, CLASSIC_RULESET_HASH);
});

test("Team Up create persists the final team state exactly once", async () => {
  const { room, storage } = roomHarness();
  const response = await room.fetch(createRequest());
  const payload = await response.json();
  const stored = await storage.get("match-state");

  assert.equal(response.status, 201);
  assert.equal(storage.putCount, 1);
  assert.equal(stored.matchMode, "TEAM_UP");
  assert.deepEqual(stored.teamAssignments, ["A", "B", "A", "B"]);
  assert.equal(stored.players[0].teamId, "A");
  assert.equal(payload.state.matchMode, "TEAM_UP");
  assert.equal(payload.state.rulesetId, "ludoproof-team-v4");
});

test("Team Up create rejects any player count other than four before persistence", async () => {
  const { room, storage } = roomHarness();
  const response = await room.fetch(
    createRequest({ targetPlayerCount: 2 }),
  );
  const payload = await response.json();

  assert.equal(response.status, 400);
  assert.equal(payload.error, "TEAM_UP_REQUIRES_FOUR_PLAYERS");
  assert.equal(storage.putCount, 0);
  assert.equal(await storage.get("match-state"), undefined);
});
