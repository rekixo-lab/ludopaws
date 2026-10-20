import test from "node:test";
import assert from "node:assert/strict";
import {
  RULESET,
  rulesetForState,
  addPlayer,
  applyMove,
  attachHostAuth,
  attachRoundCommitment,
  authoritativeStateForRandomness,
  forfeitTimedOutRoll,
  destinationForRoll,
  globalCellFor,
  legalTokenIndexes,
  newMatch,
  registerResolvedRoll,
  reserveRoll,
  startMatch,
} from "../src/game.js";

function activeMatch(rulesetKey) {
  let state = newMatch({
    matchId: "ABC12345",
    rulesetKey,
    hostPlayerId: "p1",
    hostDisplayName: "One",
    now: 1,
  });
  state = attachHostAuth(state, "a".repeat(64), 2);
  state = addPlayer(state, {
    playerId: "p2",
    displayName: "Two",
    tokenAuthHash: "b".repeat(64),
    now: 3,
  });
  return startMatch(state, "p1", 4);
}

test("a match requires at least two players and starts with host turn", () => {
  let state = newMatch({
    matchId: "M",
    hostPlayerId: "p1",
    hostDisplayName: "One",
    now: 1,
  });
  assert.throws(() => startMatch(state, "p1", 2), /at least two players/);
  state = attachHostAuth(state, "a".repeat(64), 2);
  state = addPlayer(state, {
    playerId: "p2",
    displayName: "Two",
    tokenAuthHash: "b".repeat(64),
    now: 3,
  });
  state = startMatch(state, "p1", 4);
  assert.equal(state.status, "ACTIVE");
  assert.equal(state.turnSeat, 0);
});

test("yard token can leave only on six", () => {
  const state = activeMatch();
  assert.deepEqual(legalTokenIndexes(state, 0, 5), []);
  assert.deepEqual(legalTokenIndexes(state, 0, 6), [0, 1, 2, 3]);
});

test("authoritative randomness snapshot excludes secret auth hashes", () => {
  const snapshot = authoritativeStateForRandomness(activeMatch());
  assert.equal(JSON.stringify(snapshot).includes("tokenAuthHash"), false);
});

test("one roll is reserved and bound to the current event index", () => {
  const state = activeMatch();
  const reserved = reserveRoll(state, {
    playerId: "p1",
    clientCommitment: "c".repeat(64),
    eventIndex: 0,
    actorHash: "1".repeat(64),
    previousStateHash: "2".repeat(64),
    rulesetHash: "3".repeat(64),
    now: 5,
  });
  assert.equal(reserved.pendingRoll.status, "CREATING");
  assert.equal(reserved.pendingRoll.eventIndex, 0);

  const committed = attachRoundCommitment(reserved, {
    roundId: "11111111-1111-4111-8111-111111111111",
    serverCommitment: "4".repeat(64),
    now: 6,
  });
  assert.equal(committed.pendingRoll.status, "COMMITTED");
  assert.equal(committed.randomEventIndex, 1);
});

test("verified six lets a token enter and grants an extra turn", () => {
  let state = activeMatch();
  state = reserveRoll(state, {
    playerId: "p1",
    clientCommitment: "c".repeat(64),
    eventIndex: 0,
    actorHash: "1".repeat(64),
    previousStateHash: "2".repeat(64),
    rulesetHash: "3".repeat(64),
    now: 5,
  });
  state = attachRoundCommitment(state, {
    roundId: "11111111-1111-4111-8111-111111111111",
    serverCommitment: "4".repeat(64),
    now: 6,
  });

  const resolved = registerResolvedRoll(state, {
    outcome: 6,
    proofDigest: "5".repeat(64),
    now: 7,
  });
  assert.deepEqual(resolved.legalTokenIndexes, [0, 1, 2, 3]);

  const moved = applyMove(resolved.state, {
    playerId: "p1",
    tokenIndex: 0,
    now: 8,
  });
  assert.equal(moved.state.players[0].tokens[0], 0);
  assert.equal(moved.extraTurn, true);
  assert.equal(moved.state.turnSeat, 0);
});

test("capture returns opponent token to yard and grants extra turn", () => {
  let state = activeMatch();
  state.players[0].tokens[0] = 1;
  state.players[1].tokens[0] = 41;
  assert.equal(globalCellFor("RED", 2), globalCellFor("GREEN", 41));

  state = reserveRoll(state, {
    playerId: "p1",
    clientCommitment: "c".repeat(64),
    eventIndex: 0,
    actorHash: "1".repeat(64),
    previousStateHash: "2".repeat(64),
    rulesetHash: "3".repeat(64),
    now: 5,
  });
  state = attachRoundCommitment(state, {
    roundId: "11111111-1111-4111-8111-111111111111",
    serverCommitment: "4".repeat(64),
    now: 6,
  });
  const resolved = registerResolvedRoll(state, {
    outcome: 1,
    proofDigest: "5".repeat(64),
    now: 7,
  });
  const moved = applyMove(resolved.state, {
    playerId: "p1",
    tokenIndex: 0,
    now: 8,
  });

  assert.equal(moved.captures, 1);
  assert.equal(moved.state.players[1].tokens[0], -1);
  assert.equal(moved.extraTurn, true);
  assert.equal(moved.state.turnSeat, 0);
});

for (const rulesetKey of [undefined, "CLASSIC_V5"]) {
test(`opponent stacks are protected for ${rulesetKey ?? "legacy"}`, () => {
  for (const stackSize of [2, 3, 4]) {
    let state = activeMatch(rulesetKey);
    if (rulesetKey) {
      assert.equal(rulesetForState(state).opponentStackCapture, "OPPONENT_STACK_PROTECTED");
      assert.equal(rulesetForState(state).turnRollTimeoutMs, 15_000);
      state.openingRollConsumed[0] = true;
    }
    state.players[0].tokens[0] = 1;
    state.players[1].tokens =
      [41, 41, 41, 41].map((position, index) =>
        index < stackSize ? position : -1,
      );

    state = reserveRoll(state, {
      playerId: "p1",
      clientCommitment: "c".repeat(64),
      eventIndex: 0,
      actorHash: "1".repeat(64),
      previousStateHash: "2".repeat(64),
      rulesetHash: "3".repeat(64),
      now: 5,
    });
    state = attachRoundCommitment(state, {
      roundId: "11111111-1111-4111-8111-111111111111",
      serverCommitment: "4".repeat(64),
      now: 6,
    });
    const resolved = registerResolvedRoll(state, {
      outcome: 1,
      proofDigest: "5".repeat(64),
      now: 7,
    });
    const moved = applyMove(resolved.state, {
      playerId: "p1",
      tokenIndex: 0,
      now: 8,
    });

    assert.equal(moved.state.players[0].tokens[0], 2);
    assert.deepEqual(
      moved.state.players[1].tokens,
      [41, 41, 41, 41].map((position, index) =>
        index < stackSize ? position : -1,
      ),
    );
    assert.equal(moved.captures, 0);
    assert.equal(moved.extraTurn, false);
    assert.equal(moved.state.turnSeat, 1);
  }
});

}

test("third consecutive six forfeits the roll and passes turn", () => {
  let state = activeMatch();
  state.players[0].tokens[0] = 0;
  state.consecutiveSixes[0] = 2;
  state = reserveRoll(state, {
    playerId: "p1",
    clientCommitment: "c".repeat(64),
    eventIndex: 0,
    actorHash: "1".repeat(64),
    previousStateHash: "2".repeat(64),
    rulesetHash: "3".repeat(64),
    now: 5,
  });
  state = attachRoundCommitment(state, {
    roundId: "11111111-1111-4111-8111-111111111111",
    serverCommitment: "4".repeat(64),
    now: 6,
  });
  const resolved = registerResolvedRoll(state, {
    outcome: 6,
    proofDigest: "5".repeat(64),
    now: 7,
  });
  assert.equal(resolved.forfeitedThirdSix, true);
  assert.equal(resolved.state.turnSeat, 1);
  assert.equal(resolved.state.pendingRoll, null);
  assert.equal(resolved.state.consecutiveSixes[0], 0);
});

test("home lane starts immediately after relative track position 50", () => {
  assert.equal(destinationForRoll(50, 1), 52);
  assert.equal(destinationForRoll(49, 2), 52);
  assert.equal(destinationForRoll(50, 6), 57);
  assert.equal(destinationForRoll(51, 1), 53);
  assert.equal(destinationForRoll(56, 2), null);

  for (const color of ["RED", "GREEN", "YELLOW", "BLUE"]) {
    assert.notEqual(globalCellFor(color, 50), null);
    assert.equal(globalCellFor(color, 51), null);
  }

  const state = activeMatch();
  state.players[0].tokens[0] = 50;
  state.pendingRoll = {
    status: "RESOLVED",
    seat: 0,
    playerId: "p1",
    outcome: 1,
    legalTokenIndexes: [0],
  };
  const moved = applyMove(state, {
    playerId: "p1",
    tokenIndex: 0,
    now: 9,
  });
  assert.equal(moved.state.players[0].tokens[0], 52);
});

test("exact roll is required to reach home", () => {
  const state = activeMatch();
  state.players[0].tokens = [55, 56, 57, -1];
  assert.deepEqual(legalTokenIndexes(state, 0, 2), [0]);
  assert.deepEqual(legalTokenIndexes(state, 0, 1), [0, 1]);
});

test("ruleset contains safe global cells and no capture there", () => {
  assert.deepEqual(RULESET.safeGlobalCells, [0, 8, 13, 21, 26, 34, 39, 47]);
});


test("only the host can start the match", () => {
  let state = newMatch({
    matchId: "M",
    hostPlayerId: "p1",
    hostDisplayName: "One",
    now: 1,
  });
  state = attachHostAuth(
    state,
    "a".repeat(64),
    2,
  );
  state = addPlayer(state, {
    playerId: "p2",
    displayName: "Two",
    tokenAuthHash: "b".repeat(64),
    now: 3,
  });

  assert.throws(
    () => startMatch(state, "p2", 4),
    (error) =>
      error.code === "HOST_ONLY",
  );
});

test("waiting room caps at four players and locks after start", () => {
  let state = newMatch({
    matchId: "M",
    hostPlayerId: "p1",
    hostDisplayName: "One",
    now: 1,
  });
  state = attachHostAuth(
    state,
    "a".repeat(64),
    2,
  );

  for (const [index, id] of [
    [2, "p2"],
    [3, "p3"],
    [4, "p4"],
  ]) {
    state = addPlayer(state, {
      playerId: id,
      displayName: id,
      tokenAuthHash:
        String(index).repeat(64),
      now: index + 2,
    });
  }

  assert.equal(state.players.length, 4);
  assert.throws(
    () =>
      addPlayer(state, {
        playerId: "p5",
        displayName: "Five",
        tokenAuthHash: "f".repeat(64),
        now: 10,
      }),
    (error) =>
      error.code === "MATCH_FULL",
  );

  const started =
    startMatch(state, "p1", 11);
  assert.throws(
    () =>
      addPlayer(started, {
        playerId: "late",
        displayName: "Late",
        tokenAuthHash: "e".repeat(64),
        now: 12,
      }),
    (error) =>
      error.code ===
      "INVALID_MATCH_STATUS",
  );
});

test("safe cells never capture an opponent token", () => {
  let state = activeMatch();
  state.players[0].tokens[0] = 7;
  state.players[1].tokens[0] = 47;

  assert.equal(
    globalCellFor("RED", 8),
    8,
  );
  assert.equal(
    globalCellFor("GREEN", 47),
    8,
  );

  state = reserveRoll(state, {
    playerId: "p1",
    clientCommitment: "c".repeat(64),
    eventIndex: 0,
    actorHash: "1".repeat(64),
    previousStateHash: "2".repeat(64),
    rulesetHash: "3".repeat(64),
    now: 5,
  });
  state = attachRoundCommitment(
    state,
    {
      roundId:
        "11111111-1111-4111-8111-111111111111",
      serverCommitment:
        "4".repeat(64),
      now: 6,
    },
  );
  const resolved =
    registerResolvedRoll(state, {
      outcome: 1,
      proofDigest:
        "5".repeat(64),
      now: 7,
    });
  const moved =
    applyMove(resolved.state, {
      playerId: "p1",
      tokenIndex: 0,
      now: 8,
    });

  assert.equal(moved.captures, 0);
  assert.equal(
    moved.state.players[1]
      .tokens[0],
    47,
  );
});

test("the final exact move finishes the match and records the winner", () => {
  let state = activeMatch();
  state.players[0].tokens =
    [57, 57, 57, 56];

  state = reserveRoll(state, {
    playerId: "p1",
    clientCommitment: "c".repeat(64),
    eventIndex: 0,
    actorHash: "1".repeat(64),
    previousStateHash: "2".repeat(64),
    rulesetHash: "3".repeat(64),
    now: 5,
  });
  state = attachRoundCommitment(
    state,
    {
      roundId:
        "11111111-1111-4111-8111-111111111111",
      serverCommitment:
        "4".repeat(64),
      now: 6,
    },
  );
  const resolved =
    registerResolvedRoll(state, {
      outcome: 1,
      proofDigest:
        "5".repeat(64),
      now: 7,
    });
  const moved =
    applyMove(resolved.state, {
      playerId: "p1",
      tokenIndex: 3,
      now: 8,
    });

  assert.equal(
    moved.state.status,
    "FINISHED",
  );
  assert.equal(
    moved.winnerPlayerId,
    "p1",
  );
  assert.equal(
    moved.state.winnerPlayerId,
    "p1",
  );
  assert.equal(
    moved.state.pendingRoll,
    null,
  );
});

test("public two-player Online assigns the second seat to the top-right Yellow home", () => {
  let state = newMatch({
    matchId: "DUO12345",
    hostPlayerId: "p1",
    hostDisplayName: "One",
    now: 1,
    targetPlayerCount: 2,
    matchMode: "ONLINE",
    rulesetKey: "CLASSIC_V3",
  });
  state = addPlayer(state, {
    playerId: "p2",
    displayName: "Two",
    tokenAuthHash: "b".repeat(64),
    now: 2,
  });

  assert.deepEqual(state.players.map((player) => player.color), ["RED", "YELLOW"]);
  assert.equal(state.players[0].playerId, "p1");
  assert.equal(state.players[1].playerId, "p2");
  assert.equal(globalCellFor(state.players[0].color, 0), 0);
  assert.equal(globalCellFor(state.players[1].color, 0), 26);
});

test("Team Up teammates can share a road cell without capturing each other", () => {
  let state = newMatch({
    matchId: "TEAM1234",
    hostPlayerId: "p1",
    hostDisplayName: "One",
    now: 1,
    targetPlayerCount: 4,
    matchMode: "TEAM_UP",
    rulesetKey: "TEAM_UP_V3",
  });
  state = attachHostAuth(state, "a".repeat(64), 2);
  for (const [index, id] of [[1, "p2"], [2, "p3"], [3, "p4"]]) {
    state = addPlayer(state, {
      playerId: id,
      displayName: id,
      tokenAuthHash: String(index + 1).repeat(64),
      now: index + 2,
    });
  }
  state = startMatch(state, "p1", 6);
  assert.deepEqual(state.players.map((player) => player.teamId), ["A", "B", "A", "B"]);

  // Red's token advances from relative position 1 to 2, where Yellow's
  // teammate token already stands (Yellow relative position 28 => global 2).
  state.players[0].tokens[0] = 1;
  state.players[2].tokens[0] = 28;
  state = reserveRoll(state, {
    playerId: "p1",
    clientCommitment: "c".repeat(64),
    eventIndex: 0,
    actorHash: "1".repeat(64),
    previousStateHash: "2".repeat(64),
    rulesetHash: "3".repeat(64),
    now: 7,
  });
  state = attachRoundCommitment(state, {
    roundId: "11111111-1111-4111-8111-111111111111",
    serverCommitment: "4".repeat(64),
    now: 8,
  });
  const resolved = registerResolvedRoll(state, {
    outcome: 1,
    proofDigest: "5".repeat(64),
    now: 9,
  });
  const moved = applyMove(resolved.state, {
    playerId: "p1",
    tokenIndex: 0,
    now: 10,
  });

  assert.equal(moved.captures, 0);
  assert.equal(moved.state.players[0].tokens[0], 2);
  assert.equal(moved.state.players[2].tokens[0], 28);
  assert.equal(
    globalCellFor(moved.state.players[0].color, moved.state.players[0].tokens[0]),
    globalCellFor(moved.state.players[2].color, moved.state.players[2].tokens[0]),
  );
});

test("all color start offsets map to their expected global cells", () => {
  assert.equal(
    globalCellFor("RED", 0),
    0,
  );
  assert.equal(
    globalCellFor("GREEN", 0),
    13,
  );
  assert.equal(
    globalCellFor("YELLOW", 0),
    26,
  );
  assert.equal(
    globalCellFor("BLUE", 0),
    39,
  );
});


test("timed-out committed roll is forfeited without replacement and advances turn", () => {
  let state = activeMatch();
  state = reserveRoll(state, {
    playerId: "p1",
    clientCommitment: "c".repeat(64),
    eventIndex: 0,
    actorHash: "1".repeat(64),
    previousStateHash: "2".repeat(64),
    rulesetHash: "3".repeat(64),
    now: 5,
  });
  state = attachRoundCommitment(state, {
    roundId: "11111111-1111-4111-8111-111111111111",
    serverCommitment: "4".repeat(64),
    revealDeadlineAt: 100,
    now: 6,
  });

  const result = forfeitTimedOutRoll(state, {
    now: 100,
  });

  assert.equal(result.state.pendingRoll, null);
  assert.equal(result.state.turnSeat, 1);
  assert.equal(result.state.randomEventIndex, 1);
  assert.equal(result.timedOutRoll.status, "TIMED_OUT");
  assert.equal(result.timedOutRoll.replacementRoundAllowed, false);
});

test("timed-out uncertain create consumes the logical event before advancing turn", () => {
  let state = activeMatch();
  state = reserveRoll(state, {
    playerId: "p1",
    clientCommitment: "c".repeat(64),
    eventIndex: 0,
    actorHash: "1".repeat(64),
    previousStateHash: "2".repeat(64),
    rulesetHash: "3".repeat(64),
    now: 5,
  });
  state.pendingRoll.revealDeadlineAt = 100;

  const result = forfeitTimedOutRoll(state, {
    now: 100,
  });

  assert.equal(result.state.randomEventIndex, 1);
  assert.equal(result.state.turnSeat, 1);
  assert.equal(result.state.pendingRoll, null);
  assert.equal(result.timedOutRoll.roundId, null);
});
