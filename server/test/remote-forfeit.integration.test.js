import test from "node:test";
import assert from "node:assert/strict";

import {
  MatchRoom,
} from "../src/match-room-forfeit.js";
import { sha256Hex } from "../src/crypto.js";

const SESSION_KEY =
  "lp_test_session_hmac_key_1234567890abcdef";

class FakeStorage {
  constructor() {
    this.values =
      new Map();
    this.alarmAt =
      null;
  }

  async get(key) {
    const value =
      this.values.get(
        key,
      );
    return value ===
      undefined
      ? undefined
      : structuredClone(
          value,
        );
  }

  async put(key, value) {
    this.values.set(
      key,
      structuredClone(
        value,
      ),
    );
  }

  async setAlarm(timestamp) {
    this.alarmAt =
      timestamp;
  }

  async deleteAll() {
    this.values.clear();
  }
}

function makeRoom() {
  const ctx = {
    storage:
      new FakeStorage(),
  };
  const env = {
    LUDOPROOF_SESSION_HMAC_KEY:
      SESSION_KEY,
  };
  return {
    ctx,
    room:
      new MatchRoom(
        ctx,
        env,
      ),
  };
}

function request(
  path,
  {
    method = "GET",
    token = null,
    body = null,
  } = {},
) {
  const headers =
    new Headers();
  if (
    token
  ) {
    headers.set(
      "authorization",
      "Bearer " +
        token,
    );
  }
  if (
    body !==
    null
  ) {
    headers.set(
      "content-type",
      "application/json",
    );
  }

  return new Request(
    "https://room" +
      path,
    {
      method,
      headers,
      body:
        body ===
        null
          ? undefined
          : JSON.stringify(
              body,
            ),
    },
  );
}

async function json(response) {
  return {
    response,
    body:
      await response.json(),
  };
}

async function createStartedMatch(
  playerCount,
  matchMode = "ONLINE",
  rulesetKey = null,
) {
  const {
    ctx,
    room,
  } =
    makeRoom();
  const matchId =
    "LPABCDEFGH";

  const created =
    await json(
      await room.fetch(
        request(
          "/create",
          {
            method:
              "POST",
            body: {
              matchId,
              displayName: "Host",
              clientRequestId: crypto.randomUUID(),
              ...(matchMode === "TEAM_UP"
                ? {
                    targetPlayerCount: playerCount,
                    matchMode,
                    rulesetKey: rulesetKey ?? "TEAM_UP_V4",
                  }
                : rulesetKey != null
                  ? { rulesetKey }
                  : {}),
            },
          },
        ),
      ),
    );
  assert.equal(
    created.response.status,
    201,
    JSON.stringify(
      created.body,
    ),
  );

  const players = [
    {
      playerId:
        created.body.playerId,
      playerToken:
        created.body.playerToken,
    },
  ];

  for (
    let index = 1;
    index <
      playerCount;
    index += 1
  ) {
    const joined =
      await json(
        await room.fetch(
          request(
            "/join",
            {
              method:
                "POST",
              body: {
                displayName:
                  "Player " +
                    (
                      index +
                        1
                    ),
                clientRequestId:
                  crypto.randomUUID(),
              },
            },
          ),
        ),
      );
    assert.equal(
      joined.response.status,
      201,
      JSON.stringify(
        joined.body,
      ),
    );
    players.push({
      playerId:
        joined.body.playerId,
      playerToken:
        joined.body.playerToken,
    });
  }

  const started =
    await json(
      await room.fetch(
        request(
          "/start",
          {
            method:
              "POST",
            token:
              players[0]
                .playerToken,
            body: {},
          },
        ),
      ),
    );
  assert.equal(
    started.response.status,
    200,
    JSON.stringify(
      started.body,
    ),
  );
  assert.equal(
    started.body.state.status,
    "ACTIVE",
  );

  return {
    ctx,
    room,
    players,
  };
}

async function forfeit(
  room,
  playerToken,
) {
  return json(
    await room.fetch(
      request(
        "/start",
        {
          method:
            "POST",
          token:
            playerToken,
          body: {
            forfeit:
              true,
          },
        },
      ),
    ),
  );
}

test(
  "two-player exit is an authoritative loss and awards the opponent",
  async () => {
    const {
      room,
      players,
    } =
      await createStartedMatch(
        2,
      );

    const result =
      await forfeit(
        room,
        players[1]
          .playerToken,
      );

    assert.equal(
      result.response.status,
      200,
      JSON.stringify(
        result.body,
      ),
    );
    assert.equal(
      result.body.state.status,
      "FINISHED",
    );
    assert.equal(
      result.body.winnerPlayerId,
      players[0]
        .playerId,
    );
    assert.equal(
      result.body.state.players[1]
        .forfeited,
      true,
    );

    const replay =
      await forfeit(
        room,
        players[1]
          .playerToken,
      );
    assert.equal(
      replay.response.status,
      200,
    );
    assert.equal(
      replay.body.replayed,
      true,
    );
  },
);

test(
  "classic V4 exit advances the active seat and resets the deadline",
  async () => {
    const { ctx, room, players } = await createStartedMatch(4, "ONLINE", "CLASSIC_V4");
    const before = await ctx.storage.get("match-state");
    assert.equal(before.turnDeadlineSeat, 0);
    const oldDeadline = before.turnDeadlineAt;

    const result = await forfeit(room, players[0].playerToken);
    assert.equal(result.response.status, 200, JSON.stringify(result.body));
    assert.equal(result.body.state.status, "ACTIVE");
    assert.equal(result.body.state.turnSeat, 1);
    assert.ok(result.body.state.turnDeadlineAt > oldDeadline);

    const stored = await ctx.storage.get("match-state");
    assert.equal(stored.turnDeadlineSeat, 1);
    assert.ok(stored.turnDeadlineAt > oldDeadline);
  },
);

test(
  "Team Up exit forfeits both teammates and awards the opposite team",
  async () => {
    const { room, players } = await createStartedMatch(4, "TEAM_UP");

    const result = await forfeit(room, players[1].playerToken);
    assert.equal(result.response.status, 200, JSON.stringify(result.body));
    assert.equal(result.body.state.status, "FINISHED");
    assert.equal(result.body.state.winnerTeamId, "A");
    assert.equal(result.body.state.winnerPlayerId, null);
    assert.equal(result.body.state.players[1].forfeited, true);
    assert.equal(result.body.state.players[3].forfeited, true);
    assert.equal(result.body.state.players[3].forfeitedAt, result.body.state.players[1].forfeitedAt);
    assert.equal(result.body.state.players[0].forfeited, false);
    assert.equal(result.body.state.players[2].forfeited, false);

    const replay = await forfeit(room, players[3].playerToken);
    assert.equal(replay.response.status, 200);
    assert.equal(replay.body.replayed, true);
    assert.equal(replay.body.state.winnerTeamId, "A");
  },
);

test(
  "Team Up exit closes a pending roll even when another seat owns it",
  async () => {
    const { ctx, room, players } = await createStartedMatch(4, "TEAM_UP");
    const stored = await ctx.storage.get("match-state");
    stored.randomEventIndex = 1;
    stored.pendingRoll = {
      status: "COMMITTED",
      seat: 0,
      scheduledSeat: 0,
      playerId: players[0].playerId,
      clientCommitment: "a".repeat(64),
      serverCommitment: "b".repeat(64),
      actorHash: "c".repeat(64),
      previousStateHash: "d".repeat(64),
      rulesetHash: "e".repeat(64),
      proofDigest: null,
      outcome: null,
      legalTokenIndexes: null,
      eventIndex: 0,
      eventId: "roll:0",
      roundId: "round-pending-team-exit",
      revealDeadlineAt: Date.now() + 120_000,
    };
    await ctx.storage.put("match-state", stored);

    const result = await forfeit(room, players[1].playerToken);
    assert.equal(result.response.status, 200, JSON.stringify(result.body));
    assert.equal(result.body.state.status, "FINISHED");
    assert.equal(result.body.state.pendingRoll, null);
    assert.equal(result.body.state.winnerTeamId, "A");
    assert.equal(result.body.state.history.length, 1);
    assert.equal(result.body.state.history[0].eventIndex, 0);
    assert.equal(result.body.state.history[0].status, "FORFEITED");
    assert.equal(result.body.state.randomEventIndex, 1);

    const final = await ctx.storage.get("match-state");
    assert.equal(final.pendingRoll, null);
    assert.equal(final.history.length, 1);
    assert.equal(final.randomEventIndex, 1);
    assert.equal(Number.isSafeInteger(final.players[1].forfeitedAt), true);
    assert.equal(Number.isSafeInteger(final.players[3].forfeitedAt), true);
    assert.deepEqual(final.players[1].tokens, [-1, -1, -1, -1]);
    assert.deepEqual(final.players[3].tokens, [-1, -1, -1, -1]);
  },
);

test(
  "four-player exits keep the match active until one player remains",
  async () => {
    const {
      room,
      players,
    } =
      await createStartedMatch(
        4,
      );

    const first =
      await forfeit(
        room,
        players[1]
          .playerToken,
      );
    assert.equal(
      first.response.status,
      200,
    );
    assert.equal(
      first.body.state.status,
      "ACTIVE",
    );

    const second =
      await forfeit(
        room,
        players[2]
          .playerToken,
      );
    assert.equal(
      second.response.status,
      200,
    );
    assert.equal(
      second.body.state.status,
      "ACTIVE",
    );

    const third =
      await forfeit(
        room,
        players[3]
          .playerToken,
      );
    assert.equal(
      third.response.status,
      200,
    );
    assert.equal(
      third.body.state.status,
      "FINISHED",
    );
    assert.equal(
      third.body.winnerPlayerId,
      players[0]
        .playerId,
    );
  },
);

test(
  "state normalization skips a forfeited turn seat",
  async () => {
    const {
      ctx,
      room,
      players,
    } =
      await createStartedMatch(
        4,
      );

    await forfeit(
      room,
      players[1]
        .playerToken,
    );

    const stored =
      await ctx.storage.get(
        "match-state",
      );
    stored.turnSeat =
      1;
    await ctx.storage.put(
      "match-state",
      stored,
    );

    const state =
      await json(
        await room.fetch(
          request(
            "/state",
            {
              token:
                players[0]
                  .playerToken,
            },
          ),
        ),
      );

    assert.equal(
      state.response.status,
      200,
      JSON.stringify(
        state.body,
      ),
    );
    assert.equal(
      state.body.state.turnSeat,
      2,
    );
  },
);

test(
  "a forfeited player cannot mutate the match again",
  async () => {
    const {
      room,
      players,
    } =
      await createStartedMatch(
        4,
      );

    await forfeit(
      room,
      players[1]
        .playerToken,
    );

    const clientSeed = "a".repeat(64);
    const clientCommitment = await sha256Hex(
      "ludopaws:native-roll:v1:client-commit:" + clientSeed,
    );

    const mutation =
      await json(
        await room.fetch(
          request(
            "/roll",
            {
              method:
                "POST",
              token:
                players[1]
                  .playerToken,
              body: {
                clientSeed,
                clientCommitment,
              },
            },
          ),
        ),
      );

    assert.equal(
      mutation.response.status,
      409,
    );
    assert.equal(
      mutation.body.error,
      "PLAYER_FORFEITED",
    );
  },
);
