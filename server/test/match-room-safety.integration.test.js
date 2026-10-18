import test from "node:test";
import assert from "node:assert/strict";

import {
  MatchRoom,
} from "../src/match-room-safe.js";
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
  if (token) {
    headers.set(
      "authorization",
      "Bearer " +
        token,
    );
  }
  if (body !== null) {
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
        body === null
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
              displayName:
                "Host",
              clientRequestId:
                crypto.randomUUID(),
            },
          },
        ),
      ),
    );
  assert.equal(
    created.response.status,
    201,
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

test(
  "forfeit persistence keeps another player's reveal deadline as the next alarm",
  async () => {
    const {
      ctx,
      room,
      players,
    } =
      await createStartedMatch(
        3,
      );

    const stored =
      await ctx.storage.get(
        "match-state",
      );
    const revealDeadlineAt =
      Date.now() +
      120_000;

    stored.randomEventIndex =
      1;
    stored.pendingRoll = {
      status:
        "COMMITTED",
      seat: 0,
      playerId:
        players[0]
          .playerId,
      clientCommitment:
        "a".repeat(64),
      eventIndex: 0,
      eventId:
        "roll:0",
      actorHash:
        "c".repeat(64),
      previousStateHash:
        "d".repeat(64),
      rulesetHash:
        "e".repeat(64),
      roundId:
        "round-pending",
      serverCommitment:
        "b".repeat(64),
      proofDigest:
        null,
      outcome:
        null,
      legalTokenIndexes:
        null,
      revealDeadlineAt,
    };
    await ctx.storage.put(
      "match-state",
      stored,
    );
    await ctx.storage.setAlarm(
      revealDeadlineAt,
    );

    const result =
      await json(
        await room.fetch(
          request(
            "/start",
            {
              method:
                "POST",
              token:
                players[1]
                  .playerToken,
              body: {
                forfeit:
                  true,
              },
            },
          ),
        ),
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
      "ACTIVE",
    );
    assert.equal(
      result.body.state.pendingRoll
        .eventIndex,
      0,
    );
    assert.equal(
      ctx.storage.alarmAt,
      revealDeadlineAt,
    );
  },
);

test(
  "native roll retry is a read-only replay after a lost response",
  async () => {
    const { ctx, room, players } = await createStartedMatch(2);
    const initial = await ctx.storage.get("match-state");
    initial.players[0].tokens[0] = 0;
    await ctx.storage.put("match-state", initial);

    const clientSeed = "a".repeat(64);
    const clientCommitment = await sha256Hex(
      "ludopaws:native-roll:v1:client-commit:" + clientSeed,
    );
    const makeRequest = () => request("/roll", {
      method: "POST",
      token: players[0].playerToken,
      body: { clientSeed, clientCommitment },
    });

    const first = await json(await room.fetch(makeRequest()));
    assert.equal(first.response.status, 200, JSON.stringify(first.body));
    assert.ok(first.body.outcome >= 1 && first.body.outcome <= 6);
    assert.equal(first.body.proof.protocol, "ludopaws-server-csprng-v1");
    const afterFirst = await ctx.storage.get("match-state");

    const replay = await json(await room.fetch(makeRequest()));
    assert.equal(replay.response.status, 200, JSON.stringify(replay.body));
    assert.equal(replay.body.replayed, true);
    assert.equal(replay.body.outcome, first.body.outcome);
    assert.equal(replay.body.roundId, first.body.roundId);
    assert.deepEqual(
      await ctx.storage.get("match-state"),
      afterFirst,
      "native roll replay must not mutate authoritative match state",
    );
  },
);
