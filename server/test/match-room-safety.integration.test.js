import test from "node:test";
import assert from "node:assert/strict";

import {
  MatchRoom,
} from "../src/match-room-lifecycle.js";

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

test("another player's forfeit preserves the resolved roll and its move deadline", async () => {
  const { ctx, room, players } = await createStartedMatch(3);
  // V4 opening roll has legal yard exits, so a resolved move must survive.
  const state = await ctx.storage.get("match-state");
  state.rulesetKey = "CLASSIC_V4";
  await ctx.storage.put("match-state", state);
  const rolled = await json(await room.fetch(request("/roll", {
    method: "POST", token: players[0].playerToken,
    body: { clientRequestId: crypto.randomUUID(), expectedEventIndex: 0 },
  })));
  assert.equal(rolled.response.status, 200, JSON.stringify(rolled.body));
  assert.equal(rolled.body.awaitingMove, true);
  const deadline = ctx.storage.alarmAt;
  const result = await json(await room.fetch(request("/start", {
    method: "POST", token: players[1].playerToken, body: { forfeit: true },
  })));
  assert.equal(result.response.status, 200);
  assert.equal(result.body.state.status, "ACTIVE");
  assert.equal(result.body.state.pendingRoll.eventIndex, 0);
  assert.equal(ctx.storage.alarmAt, deadline);
});

test("obsolete two-stage roll routes cannot mutate a new match", async () => {
  const { ctx, room, players } = await createStartedMatch(2);
  const before = await ctx.storage.get("match-state");
  for (const path of ["/roll/commit", "/roll/reveal"]) {
    const result = await room.fetch(request(path, {
      method: "POST", token: players[0].playerToken, body: {},
    }));
    assert.equal(result.status, 410);
  }
  assert.deepEqual(await ctx.storage.get("match-state"), before);
});
