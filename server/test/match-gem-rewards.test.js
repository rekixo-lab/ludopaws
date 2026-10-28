import test from "node:test";
import assert from "node:assert/strict";
import { newMatch, addPlayer, startMatch, applyMove } from "../src/game.js";
import { plannedMatchGemRewards } from "../src/match-gem-rewards.js";

function completed(count, mode = "FRIENDS") {
  let state = newMatch({ matchId: "LPABC23456", hostPlayerId: "p1",
    hostDisplayName: "One", matchMode: mode, targetPlayerCount: count,
    rulesetKey: "CLASSIC_V6", now: 100 });
  for (let seat = 2; seat <= count; seat++) {
    state = addPlayer(state, { playerId: "p" + seat, displayName: "P" + seat,
      tokenAuthHash: "a".repeat(64), now: seat });
  }
  state = startMatch(state, "p1", 200);
  for (let seat = 1; seat < count; seat++) {
    const playerId = "p" + seat;
    state.turnSeat = seat - 1;
    state.players[seat - 1].tokens = [56, 57, 57, 57];
    state.pendingRoll = { status: "RESOLVED", seat: seat - 1, outcome: 1,
      eventIndex: seat - 1, legalTokenIndexes: [0] };
    state = applyMove(state, { playerId, tokenIndex: 0, now: seat * 1000 }).state;
    if (seat < count - 1) {
      assert.equal(state.status, "ACTIVE");
      assert.equal(state.winnerPlayerId, null);
      assert.equal(state.turnSeat, seat);
    }
  }
  assert.equal(state.status, "FINISHED");
  assert.equal(state.winnerPlayerId, "p1");
  assert.deepEqual(state.finishOrderPlayerIds, Array.from({ length: count }, (_, i) => "p" + (i + 1)));
  return state;
}

for (const [count, expected] of [[2, [3]], [3, [3, 2]], [4, [3, 2, 1]]]) {
  test(`V6 ${count}-player finish records placements and pays ${expected.join("/")}`, () => {
    const state = completed(count);
    assert.deepEqual(plannedMatchGemRewards(state).map(p => p.amount), expected);
    assert.deepEqual(plannedMatchGemRewards(state).map(p => p.rank),
      expected.map((_, i) => i + 1));
  });
}

test("online rewards are restricted to two or four human players", () => {
  const state = completed(3, "ONLINE");
  assert.deepEqual(plannedMatchGemRewards(state), []);
  state.matchMode = "FRIENDS";
  state.players[0].playerType = "BOT";
  assert.deepEqual(plannedMatchGemRewards(state), []);
  state.players[0].playerType = "HUMAN";
  state.players[2].forfeitedAt = Date.now();
  assert.deepEqual(plannedMatchGemRewards(state), []);
});

test("old ruleset and missing finish-order never mint gems", () => {
  const state = completed(4);
  state.rulesetKey = "CLASSIC_V5";
  assert.deepEqual(plannedMatchGemRewards(state), []);
  state.rulesetKey = "CLASSIC_V6";
  state.finishOrderPlayerIds = ["p1", "p2"];
  assert.deepEqual(plannedMatchGemRewards(state), []);
});

test("team victory credits both winners equally, never the other team", () => {
  const state = { status: "FINISHED", matchMode: "TEAM_UP", winnerTeamId: "A",
    players: [0, 1, 2, 3].map((seat) => ({
      playerId: "p" + (seat + 1), teamId: seat % 2 === 0 ? "A" : "B",
      tokens: seat % 2 === 0 ? [57, 57, 57, 57] : [-1, -1, -1, -1],
      playerType: "HUMAN",
    })) };
  assert.deepEqual(plannedMatchGemRewards(state).map(x => [x.playerId, x.amount]),
    [["p1", 3], ["p3", 3]]);
  state.players[1].forfeitedAt = Date.now();
  assert.deepEqual(plannedMatchGemRewards(state), []);
});
