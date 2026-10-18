import test from "node:test";
import assert from "node:assert/strict";

import { MatchRoom } from "../src/match-room.js";
import { verifyFastDiceReceipt } from "../src/dice-fast.js";
import { MatchRoom as BotMatchRoom } from "../src/match-room-bots.js";
import {
  deriveLeaderboardIdentity,
  issueFriendRoomJoinToken,
  issueLeaderboardProfileAssertion,
  sha256Hex,
} from "../src/crypto.js";
const SESSION_KEY =
  "lp_test_session_hmac_key_1234567890abcdef";

class FakeStorage {
  constructor() {
    this.values = new Map();
    this.alarmAt = null;
    this.deleteAllCalls = 0;
  }

  async get(key) {
    const value = this.values.get(key);
    return value === undefined
      ? undefined
      : structuredClone(value);
  }

  async put(key, value) {
    this.values.set(key, structuredClone(value));
  }

  async setAlarm(timestamp) {
    this.alarmAt = timestamp;
  }

  async deleteAll() {
    this.values.clear();
    this.deleteAllCalls += 1;
  }
}

function makeContext() {
  return {
    storage: new FakeStorage(),
  };
}

function makeEnv() {
  return {
    LUDOPROOF_SESSION_HMAC_KEY: SESSION_KEY,
  };
}

async function json(response) {
  const body = await response.json();
  return { response, body };
}

function roomRequest(path, {
  method = "GET",
  token = null,
  friendJoinToken = null,
  profileAssertion = null,
  body = null,
} = {}) {
  const headers = new Headers();
  if (token) {
    headers.set("authorization", "Bearer " + token);
  }
  if (friendJoinToken) {
    headers.set(
      "x-ludoproof-friend-join-token",
      friendJoinToken,
    );
  }
  if (profileAssertion) {
    headers.set(
      "x-ludoproof-profile-assertion",
      profileAssertion,
    );
  }
  if (body !== null) {
    headers.set("content-type", "application/json");
  }

  return new Request("https://room" + path, {
    method,
    headers,
    body:
      body === null
        ? undefined
        : JSON.stringify(body),
  });
}

async function createMatch(
  room,
  matchId = "LPABCDEFGH",
  clientRequestId = crypto.randomUUID(),
) {
  const profile =
    await deriveLeaderboardIdentity(
      room.env,
      clientRequestId,
    );
  const profileAssertion =
    await issueLeaderboardProfileAssertion(
      room.env,
      {
        matchId,
        profileId:
          profile.profileId,
        clientRequestId,
        expiresAt:
          Date.now() +
          10 * 60 * 1000,
      },
    );

  const { response, body } = await json(
    await room.fetch(
      roomRequest("/create", {
        method: "POST",
        profileAssertion,
        body: {
          matchId,
          displayName: "Alice",
          clientRequestId,
        },
      }),
    ),
  );
  assert.equal(response.status, 201);
  return body;
}

async function joinMatch(
  room,
  displayName,
  clientRequestId = crypto.randomUUID(),
) {
  const state =
    await room.ctx.storage.get(
      "match-state",
    );
  const matchId =
    state.matchId;
  const profile =
    await deriveLeaderboardIdentity(
      room.env,
      clientRequestId,
    );
  const profileAssertion =
    await issueLeaderboardProfileAssertion(
      room.env,
      {
        matchId,
        profileId:
          profile.profileId,
        clientRequestId,
        expiresAt:
          Date.now() +
          10 * 60 * 1000,
      },
    );

  const { response, body } = await json(
    await room.fetch(
      roomRequest("/join", {
        method: "POST",
        profileAssertion,
        body: {
          displayName,
          clientRequestId,
        },
      }),
    ),
  );
  assert.equal(response.status, 201);
  return body;
}

async function startMatch(room, hostToken) {
  const { response, body } = await json(
    await room.fetch(
      roomRequest("/start", {
        method: "POST",
        token: hostToken,
        body: {},
      }),
    ),
  );
  assert.equal(response.status, 200);
  return body;
}

async function setupActiveMatch(playerCount = 2) {
  const ctx = makeContext();
  const env = makeEnv();
  const room = new MatchRoom(ctx, env);
  const host = await createMatch(room);

  const names = ["Bob", "Cara", "Dev"];
  for (let i = 1; i < playerCount; i += 1) {
    await joinMatch(room, names[i - 1]);
  }

  const started = await startMatch(
    room,
    host.playerToken,
  );

  return {
    ctx,
    env,
    room,
    host,
    started,
  };
}


function attachFriendDirectory(
  env,
  {
    hostFriendToken,
    hostFriendId,
    guestFriendId,
    matchId,
    inviteId,
  },
) {
  let joinChecks = 0;

  env.LUDOPROOF_FRIENDS = {
    idFromName(name) {
      assert.equal(
        name,
        "GLOBAL:V1",
      );
      return name;
    },
    get() {
      return {
        async fetch(request) {
          const url =
            new URL(
              request.url,
            );

          if (
            request.method ===
              "GET" &&
            url.pathname ===
              "/identity"
          ) {
            if (
              request.headers.get(
                "authorization",
              ) !==
              "Bearer " +
                hostFriendToken
            ) {
              return Response.json(
                {
                  error:
                    "FRIEND_AUTH_INVALID",
                  message:
                    "friend credential is invalid",
                },
                {
                  status:
                    401,
                },
              );
            }

            return Response.json({
              ok: true,
              friendId:
                hostFriendId,
              displayName:
                "Host Friend",
            });
          }

          if (
            request.method ===
              "POST" &&
            url.pathname ===
              "/invite/join-check"
          ) {
            joinChecks += 1;
            const body =
              await request.json();
            if (
              body?.matchId !==
                matchId ||
              body?.hostFriendId !==
                hostFriendId ||
              body?.friendId !==
                guestFriendId ||
              body?.inviteId !==
                inviteId
            ) {
              return Response.json(
                {
                  error:
                    "INVITE_JOIN_MISMATCH",
                  message:
                    "friend invite does not match",
                },
                {
                  status:
                    403,
                },
              );
            }

            return Response.json({
              ok: true,
              ...body,
            });
          }

          return Response.json(
            {
              error:
                "NOT_FOUND",
              message:
                "route not found",
            },
            {
              status:
                404,
            },
          );
        },
      };
    },
  };

  return {
    joinChecks: () =>
      joinChecks,
  };
}

test(
  "FRIENDS rooms require bound host identity and accepted invite credentials",
  { concurrency: false },
  async () => {
    const ctx =
      makeContext();
    const env =
      makeEnv();
    const room =
      new MatchRoom(
        ctx,
        env,
      );
    const matchId =
      "LPABCDEFGH";
    const hostFriendToken =
      "lf_" +
      "h".repeat(48);
    const hostFriendId =
      "LPF-ABCD-EFGH-JKLM";
    const guestFriendId =
      "LPF-MNPQ-RSTU-VWXY";
    const inviteId =
      "FIV-0123456789ABCDEF0123";

    const directory =
      attachFriendDirectory(
        env,
        {
          hostFriendToken,
          hostFriendId,
          guestFriendId,
          matchId,
          inviteId,
        },
      );

    const createRequestId =
      crypto.randomUUID();
    const missingHostAuth =
      await json(
        await room.fetch(
          roomRequest(
            "/create",
            {
              method:
                "POST",
              body: {
                matchId,
                displayName:
                  "Host Friend",
                clientRequestId:
                  createRequestId,
                targetPlayerCount:
                  2,
                matchMode:
                  "FRIENDS",
              },
            },
          ),
        ),
      );
    assert.equal(
      missingHostAuth
        .response.status,
      401,
    );
    assert.equal(
      missingHostAuth
        .body.error,
      "FRIEND_AUTH_REQUIRED",
    );

    const created =
      await json(
        await room.fetch(
          roomRequest(
            "/create",
            {
              method:
                "POST",
              token:
                hostFriendToken,
              body: {
                matchId,
                displayName:
                  "Host Friend",
                clientRequestId:
                  createRequestId,
                targetPlayerCount:
                  2,
                matchMode:
                  "FRIENDS",
              },
            },
          ),
        ),
      );
    assert.equal(
      created.response.status,
      201,
    );

    const hostAssert =
      await json(
        await room.fetch(
          roomRequest(
            "/friend-host/assert",
            {
              method:
                "POST",
              token:
                created.body
                  .playerToken,
              body: {
                hostFriendId,
              },
            },
          ),
        ),
      );
    assert.equal(
      hostAssert.response.status,
      200,
    );
    assert.equal(
      hostAssert.body.ok,
      true,
    );

    const wrongHost =
      await json(
        await room.fetch(
          roomRequest(
            "/friend-host/assert",
            {
              method:
                "POST",
              token:
                created.body
                  .playerToken,
              body: {
                hostFriendId:
                  guestFriendId,
              },
            },
          ),
        ),
      );
    assert.equal(
      wrongHost.response.status,
      403,
    );
    assert.equal(
      wrongHost.body.error,
      "FRIEND_HOST_MISMATCH",
    );

    const joinRequestId =
      crypto.randomUUID();
    const missingInvite =
      await json(
        await room.fetch(
          roomRequest(
            "/join",
            {
              method:
                "POST",
              body: {
                displayName:
                  "Guest Friend",
                clientRequestId:
                  joinRequestId,
              },
            },
          ),
        ),
      );
    assert.equal(
      missingInvite.response.status,
      401,
    );
    assert.equal(
      missingInvite.body.error,
      "FRIEND_JOIN_TOKEN_REQUIRED",
    );

    const joinToken =
      await issueFriendRoomJoinToken(
        env,
        {
          matchId,
          hostFriendId,
          friendId:
            guestFriendId,
          inviteId,
          expiresAt:
            Date.now() +
            5 * 60 * 1000,
        },
      );

    const joined =
      await json(
        await room.fetch(
          roomRequest(
            "/join",
            {
              method:
                "POST",
              friendJoinToken:
                joinToken,
              body: {
                displayName:
                  "Guest Friend",
                clientRequestId:
                  joinRequestId,
              },
            },
          ),
        ),
      );
    assert.equal(
      joined.response.status,
      201,
    );
    assert.equal(
      joined.body.state
        .players.length,
      2,
    );
    assert.equal(
      directory.joinChecks(),
      1,
    );

    const replayed =
      await json(
        await room.fetch(
          roomRequest(
            "/join",
            {
              method:
                "POST",
              friendJoinToken:
                joinToken,
              body: {
                displayName:
                  "Guest Friend",
                clientRequestId:
                  crypto.randomUUID(),
              },
            },
          ),
        ),
      );
    assert.equal(
      replayed.response.status,
      200,
    );
    assert.equal(
      replayed.body.replayed,
      true,
    );
    assert.equal(
      replayed.body.playerId,
      joined.body.playerId,
    );
    assert.equal(
      replayed.body.playerToken,
      joined.body.playerToken,
    );
    assert.equal(
      replayed.body.state
        .players.length,
      2,
      "one accepted invite must never fill a second seat",
    );
    assert.equal(
      directory.joinChecks(),
      2,
    );
  },
);

test(
  "FRIENDS voice signaling only relays between authenticated active voice peers",
  async () => {
    const ctx = makeContext();
    const room = new MatchRoom(ctx, makeEnv());
    const host = await createMatch(room);
    const guest = await joinMatch(room, "Bob");
    const sockets = [];
    ctx.getWebSockets = () => sockets;
    ctx.getTags = (socket) => socket.tags;
    const makeSocket = (playerId) => {
      const socket = {
        tags: ["player:" + playerId],
        messages: [],
        attachment: {},
        send(message) {
          this.messages.push(JSON.parse(message));
        },
        serializeAttachment(attachment) {
          this.attachment = structuredClone(attachment);
        },
        deserializeAttachment() {
          return structuredClone(this.attachment);
        },
      };
      sockets.push(socket);
      return socket;
    };
    const hostSocket = makeSocket(host.playerId);
    const guestSocket = makeSocket(guest.playerId);
    const state = await ctx.storage.get("match-state");
    state.matchMode = "FRIENDS";
    state.status = "ACTIVE";
    await ctx.storage.put("match-state", state);

    await room.webSocketMessage(
      hostSocket,
      JSON.stringify({ type: "VOICE_JOIN" }),
    );
    await room.webSocketMessage(
      guestSocket,
      JSON.stringify({ type: "VOICE_JOIN" }),
    );

    assert.deepEqual(
      hostSocket.messages.at(-1),
      { type: "VOICE_PEERS", playerIds: [guest.playerId] },
    );
    assert.deepEqual(
      guestSocket.messages.at(-1),
      { type: "VOICE_PEERS", playerIds: [host.playerId] },
    );

    await room.webSocketMessage(
      hostSocket,
      JSON.stringify({
        type: "VOICE_SIGNAL",
        toPlayerId: guest.playerId,
        fromPlayerId: "spoofed-player",
        signal: { type: "offer", sdp: "offer-sdp" },
      }),
    );
    assert.deepEqual(
      guestSocket.messages.at(-1),
      {
        type: "VOICE_SIGNAL",
        matchId: state.matchId,
        fromPlayerId: host.playerId,
        signal: { type: "offer", sdp: "offer-sdp" },
      },
    );
    assert.equal(
      hostSocket.messages.some((message) => message.type === "VOICE_SIGNAL"),
      false,
    );

    const beforeInvalidTarget = guestSocket.messages.length;
    await room.webSocketMessage(
      hostSocket,
      JSON.stringify({
        type: "VOICE_SIGNAL",
        toPlayerId: "LP-NOT-IN-ROOM",
        signal: { type: "offer", sdp: "offer-sdp" },
      }),
    );
    assert.equal(guestSocket.messages.length, beforeInvalidTarget);

    const nonFriendState = { ...state, matchMode: "ONLINE" };
    await ctx.storage.put("match-state", nonFriendState);
    const beforeWrongMode = guestSocket.messages.length;
    await room.webSocketMessage(
      hostSocket,
      JSON.stringify({
        type: "VOICE_SIGNAL",
        toPlayerId: guest.playerId,
        signal: { type: "offer", sdp: "offer-sdp" },
      }),
    );
    assert.equal(guestSocket.messages.length, beforeWrongMode);
  },
);

test(
  "legacy clients stay unranked while authenticated profiles cannot be spoofed",
  { concurrency: false },
  async () => {
    const attackerProfileId =
      "660e8400-e29b-41d4-a716-446655440000";

    const legacyCtx =
      makeContext();
    const legacyEnv =
      makeEnv();
    const legacyRoom =
      new MatchRoom(
        legacyCtx,
        legacyEnv,
      );
    const legacy =
      await json(
        await legacyRoom.fetch(
          roomRequest(
            "/create",
            {
              method:
                "POST",
              body: {
                matchId:
                  "LPABCDEFGH",
                displayName:
                  "Legacy Player",
                clientRequestId:
                  crypto.randomUUID(),
                profileId:
                  attackerProfileId,
              },
            },
          ),
        ),
      );
    assert.equal(
      legacy.response.status,
      201,
    );
    const legacyStored =
      await legacyCtx.storage.get(
        "match-state",
      );
    assert.equal(
      legacyStored.players[0]
        .profileId,
      null,
      "legacy client-supplied profile IDs must never receive leaderboard credit",
    );

    const ctx =
      makeContext();
    const env =
      makeEnv();
    const authenticatedProfileId =
      "550e8400-e29b-41d4-a716-446655440000";
    const profileToken =
      "lpp_" +
      "P".repeat(48);

    env.LUDOPROOF_LEADERBOARD = {
      idFromName(name) {
        assert.equal(
          name,
          "global",
        );
        return name;
      },
      get() {
        return {
          async fetch(request) {
            assert.equal(
              new URL(
                request.url,
              ).pathname,
              "/profile/identity",
            );
            if (
              request.headers.get(
                "authorization",
              ) !==
              "Bearer " +
                profileToken
            ) {
              return Response.json(
                {
                  error:
                    "PROFILE_AUTH_INVALID",
                  message:
                    "leaderboard profile credential is invalid",
                },
                {
                  status:
                    401,
                },
              );
            }
            return Response.json({
              ok: true,
              profileId:
                authenticatedProfileId,
            });
          },
        };
      },
    };

    const room =
      new MatchRoom(
        ctx,
        env,
      );
    const created =
      await json(
        await room.fetch(
          roomRequest(
            "/create",
            {
              method:
                "POST",
              token:
                profileToken,
              body: {
                matchId:
                  "LPBCDEFGHJ",
                displayName:
                  "Alice",
                clientRequestId:
                  crypto.randomUUID(),
                profileId:
                  attackerProfileId,
              },
            },
          ),
        ),
      );
    assert.equal(
      created.response.status,
      201,
    );

    const stored =
      await ctx.storage.get(
        "match-state",
      );
    assert.equal(
      stored.players[0]
        .profileId,
      authenticatedProfileId,
    );
    assert.notEqual(
      stored.players[0]
        .profileId,
      attackerProfileId,
    );
  },
);

test(
  "player bearer auth rejects an invalid session token",
  { concurrency: false },
  async () => {
    const ctx = makeContext();
    const env = makeEnv();
    const room = new MatchRoom(ctx, env);
    await createMatch(room);

    const { response, body } = await json(
      await room.fetch(
        roomRequest("/state", {
          token: "lp_wrong_token",
        }),
      ),
    );

    assert.equal(response.status, 403);
    assert.equal(body.error, "AUTH_INVALID");
  },
);

for (const playerCount of [2, 3, 4]) {
  test(
    `host can start an authoritative ${playerCount}-player match`,
    { concurrency: false },
    async () => {
      const {
        started,
      } =
        await setupActiveMatch(
          playerCount,
        );

      assert.equal(
        started.state.status,
        "ACTIVE",
      );
      assert.equal(
        started.state.players.length,
        playerCount,
      );
      assert.equal(
        started.state.turnSeat,
        0,
      );
      assert.equal(
        started.state.randomEventIndex,
        0,
      );
    },
  );
}

test(
  "move endpoint rejects movement without a resolved verified roll",
  { concurrency: false },
  async () => {
    const {
      room,
      host,
    } =
      await setupActiveMatch(2);

    const { response, body } = await json(
      await room.fetch(
        roomRequest("/move", {
          method: "POST",
          token: host.playerToken,
          body: {
            tokenIndex: 0,
            eventIndex: 0,
          },
        }),
      ),
    );

    assert.equal(response.status, 409);
    assert.equal(
      body.error,
      "NO_RESOLVED_ROLL",
    );
  },
);


test(
  "inactive matches are removed by the Durable Object alarm",
  { concurrency: false },
  async () => {
    const ctx = makeContext();
    const env = makeEnv();
    const room = new MatchRoom(ctx, env);
    await createMatch(room);

    assert.ok(
      ctx.storage.alarmAt > Date.now(),
      "creating a match should schedule expiry",
    );

    const state =
      await ctx.storage.get("match-state");
    state.updatedAt =
      Date.now() -
      8 * 24 * 60 * 60 * 1000;
    await ctx.storage.put(
      "match-state",
      state,
    );

    await room.alarm();

    assert.equal(
      await ctx.storage.get("match-state"),
      undefined,
    );
    assert.equal(
      ctx.storage.deleteAllCalls,
      1,
    );
  },
);


test(
  "create retry with the same request ID returns the same host session",
  { concurrency: false },
  async () => {
    const ctx = makeContext();
    const env = makeEnv();
    const room = new MatchRoom(
      ctx,
      env,
    );
    const requestId =
      "11111111-1111-4111-8111-111111111111";
    const profile =
      await deriveLeaderboardIdentity(
        env,
        requestId,
      );
    const profileAssertion =
      await issueLeaderboardProfileAssertion(
        env,
        {
          matchId:
            "LPABCDEFGH",
          profileId:
            profile.profileId,
          clientRequestId:
            requestId,
          expiresAt:
            Date.now() +
            10 * 60 * 1000,
        },
      );

    const first =
      await json(
        await room.fetch(
          roomRequest(
            "/create",
            {
              method: "POST",
              profileAssertion,
              body: {
                matchId:
                  "LPABCDEFGH",
                displayName:
                  "Alice",
                clientRequestId:
                  requestId,
              },
            },
          ),
        ),
      );
    const retry =
      await json(
        await room.fetch(
          roomRequest(
            "/create",
            {
              method: "POST",
              profileAssertion,
              body: {
                matchId:
                  "LPABCDEFGH",
                displayName:
                  "Alice",
                clientRequestId:
                  requestId,
              },
            },
          ),
        ),
      );

    assert.equal(
      first.response.status,
      201,
      JSON.stringify(
        first.body,
      ),
    );
    assert.equal(
      retry.response.status,
      200,
      JSON.stringify(
        retry.body,
      ),
    );
    assert.equal(
      retry.body.replayed,
      true,
    );
    assert.equal(
      retry.body.playerId,
      first.body.playerId,
    );
    assert.equal(
      retry.body.playerToken,
      first.body.playerToken,
    );
    assert.equal(
      retry.body.state.players.length,
      1,
    );
  },
);

test(
  "join retry with the same request ID does not create a duplicate player",
  { concurrency: false },
  async () => {
    const ctx = makeContext();
    const env = makeEnv();
    const room = new MatchRoom(
      ctx,
      env,
    );
    await createMatch(room);

    const requestId =
      "22222222-2222-4222-8222-222222222222";
    const profile =
      await deriveLeaderboardIdentity(
        env,
        requestId,
      );
    const profileAssertion =
      await issueLeaderboardProfileAssertion(
        env,
        {
          matchId:
            "LPABCDEFGH",
          profileId:
            profile.profileId,
          clientRequestId:
            requestId,
          expiresAt:
            Date.now() +
            10 * 60 * 1000,
        },
      );
    const first =
      await json(
        await room.fetch(
          roomRequest(
            "/join",
            {
              method: "POST",
              profileAssertion,
              body: {
                displayName:
                  "Bob",
                clientRequestId:
                  requestId,
              },
            },
          ),
        ),
      );
    const retry =
      await json(
        await room.fetch(
          roomRequest(
            "/join",
            {
              method: "POST",
              profileAssertion,
              body: {
                displayName:
                  "Bob",
                clientRequestId:
                  requestId,
              },
            },
          ),
        ),
      );

    assert.equal(
      first.response.status,
      201,
    );
    assert.equal(
      retry.response.status,
      200,
    );
    assert.equal(
      retry.body.replayed,
      true,
    );
    assert.equal(
      retry.body.playerId,
      first.body.playerId,
    );
    assert.equal(
      retry.body.playerToken,
      first.body.playerToken,
    );
    assert.equal(
      retry.body.state.players.length,
      2,
    );
  },
);

test(
  "host start retry is replay-safe",
  { concurrency: false },
  async () => {
    const {
      room,
      host,
    } =
      await setupActiveMatch(2);

    const retry =
      await json(
        await room.fetch(
          roomRequest(
            "/start",
            {
              method: "POST",
              token:
                host.playerToken,
              body: {},
            },
          ),
        ),
      );

    assert.equal(
      retry.response.status,
      200,
    );
    assert.equal(
      retry.body.replayed,
      true,
    );
    assert.equal(
      retry.body.state.status,
      "ACTIVE",
    );
  },
);

test(
  "move retry is idempotent for the same event and rejects token substitution",
  { concurrency: false },
  async () => {
    const { ctx, room, host } = await setupActiveMatch(2);
    const before = await ctx.storage.get("match-state");
    before.players[0].tokens[0] = 0;
    await ctx.storage.put("match-state", before);
    const clientSeed = "1".repeat(64);
    const clientCommitment = await sha256Hex(
      "ludopaws:native-roll:v1:client-commit:" + clientSeed,
    );
    const rolled = await json(await room.fetch(roomRequest("/roll", {
      method: "POST", token: host.playerToken,
      body: { clientSeed, clientCommitment },
    })));
    assert.equal(rolled.response.status, 200);
    assert.equal(rolled.body.proof.protocol, "ludopaws-server-csprng-v1");
    assert.ok(rolled.body.legalTokenIndexes.includes(0));

    const moveBody = {
      tokenIndex: 0,
      eventIndex: 0,
    };
    const first =
      await json(
        await room.fetch(
          roomRequest(
            "/move",
            {
              method: "POST",
              token:
                host.playerToken,
              body: moveBody,
            },
          ),
        ),
      );
    const retry =
      await json(
        await room.fetch(
          roomRequest(
            "/move",
            {
              method: "POST",
              token:
                host.playerToken,
              body: moveBody,
            },
          ),
        ),
      );

    assert.equal(
      first.response.status,
      200,
    );
    assert.equal(
      first.body.replayed,
      false,
    );
    assert.equal(
      retry.response.status,
      200,
    );
    assert.equal(
      retry.body.replayed,
      true,
    );

    const changed =
      await json(
        await room.fetch(
          roomRequest(
            "/move",
            {
              method: "POST",
              token:
                host.playerToken,
              body: {
                tokenIndex: 1,
                eventIndex: 0,
              },
            },
          ),
        ),
      );
    assert.equal(
      changed.response.status,
      409,
    );
    assert.equal(
      changed.body.error,
      "MOVE_ALREADY_APPLIED",
    );
  },
);

test("Quick Chat broadcasts only allowlisted emojis to other authenticated active-match sockets", async () => {
  const { ctx, room, host } = await setupActiveMatch(2);
  const state = await ctx.storage.get("match-state");
  const guest = state.players.find((player) => player.playerId !== host.playerId);
  assert.ok(guest);

  const sender = {
    messages: [],
    send(message) { this.messages.push(message); return true; },
    close() {},
  };
  const receiver = {
    messages: [],
    send(message) { this.messages.push(message); return true; },
    close() {},
  };
  ctx.getTags = (socket) =>
    socket === sender
      ? ["player:" + host.playerId]
      : ["player:" + guest.playerId];
  ctx.getWebSockets = () => [sender, receiver];

  await room.webSocketMessage(
    sender,
    JSON.stringify({ type: "QUICK_CHAT_SEND", emoji: "👏" }),
  );
  assert.equal(receiver.messages.length, 0, "unsupported emojis must be rejected");

  await room.webSocketMessage(
    sender,
    JSON.stringify({ type: "QUICK_CHAT_SEND", emoji: "😂", playerId: guest.playerId }),
  );
  assert.equal(sender.messages.length, 0, "sender must not receive its own event twice");
  assert.equal(receiver.messages.length, 1);
  const delivered = JSON.parse(receiver.messages[0]);
  assert.equal(delivered.type, "QUICK_CHAT");
  assert.equal(delivered.playerId, host.playerId, "sender identity must come from authenticated socket tags");
  assert.equal(delivered.displayName, state.players.find((player) => player.playerId === host.playerId).displayName);
  assert.equal(delivered.emoji, "😂");

  await room.webSocketMessage(
    sender,
    JSON.stringify({ type: "QUICK_CHAT_SEND", emoji: "😂" }),
  );
  await room.webSocketMessage(
    sender,
    JSON.stringify({ type: "QUICK_CHAT_SEND", emoji: "👏" }),
  );
  assert.equal(receiver.messages.length, 1, "cooldown and allowlist must reject subsequent sends");
});

test("Quick Chat rejects messages without a server-authenticated player tag", async () => {
  const { ctx, room, host } = await setupActiveMatch(2);
  const socket = {
    messages: [],
    send(message) { this.messages.push(message); return true; },
    close() {},
  };
  ctx.getTags = () => [];
  ctx.getWebSockets = () => [socket];
  await room.webSocketMessage(
    socket,
    JSON.stringify({ type: "QUICK_CHAT_SEND", emoji: "👍", playerId: host.playerId }),
  );
  assert.equal(socket.messages.length, 0);
});


test(
  "server bot takes a fast CSPRNG turn and applies a legal move",
  { concurrency: false },
  async (t) => {
    const ctx = makeContext();
    const env = makeEnv();
    const room = new BotMatchRoom(ctx, env);
    const host = await createMatch(room, "LPBOTTEST2");
    const botRequestId = crypto.randomUUID();
    const profileAssertion = await issueLeaderboardProfileAssertion(env, {
      matchId: host.matchId,
      profileId: null,
      clientRequestId: botRequestId,
      playerType: "BOT",
      expiresAt: Date.now() + 10 * 60 * 1000,
    });

    const joined = await json(
      await room.fetch(
        roomRequest("/join", {
          method: "POST",
          profileAssertion,
          body: {
            displayName: "Maya",
            clientRequestId: botRequestId,
          },
        }),
      ),
    );
    assert.equal(joined.response.status, 201);
    assert.equal(joined.body.state.players[1].playerType, "BOT");
    assert.equal(joined.body.state.players[1].displayName, "Maya");

    await startMatch(room, host.playerToken);
    const state = await ctx.storage.get("match-state");
    state.turnSeat = 1;
    // Keep one bot token on the board so any of the six random outcomes has
    // a legal move; the test must not depend on randomly rolling a six.
    state.players[1].tokens[0] = 0;
    state.updatedAt = Date.now();
    await ctx.storage.put("match-state", state);

    await room.fetch(
      roomRequest("/state", {
        token: host.playerToken,
      }),
    );
    const scheduled = await ctx.storage.get("match-state");
    assert.equal(scheduled.botTurn.playerId, scheduled.players[1].playerId);
    scheduled.botTurn.runAt = Date.now() - 1;
    scheduled.botTurn.inProgress = false;
    await ctx.storage.put("match-state", scheduled);

    await room.alarm();

    const rollShown = await ctx.storage.get("match-state");
    assert.equal(rollShown.status, "ACTIVE");
    assert.equal(rollShown.pendingRoll?.status, "RESOLVED");
    assert.equal(rollShown.players[1].tokens[0], 0);
    assert.equal(rollShown.botTurn?.inProgress, false);
    assert.ok(rollShown.botTurn?.runAt > Date.now());
    assert.equal(rollShown.history.at(-1)?.randomnessSource, "ludopaws-server-csprng-v1");

    // Simulate the result-hold window elapsing before the bot selects its paw.
    rollShown.botTurn.runAt = Date.now() - 1;
    await ctx.storage.put("match-state", rollShown);
    await room.alarm();

    const after = await ctx.storage.get("match-state");
    assert.equal(after.status, "ACTIVE");
    assert.equal(after.pendingRoll, null);
    assert.equal(after.randomEventIndex, 1);
    assert.notEqual(after.players[1].tokens[0], 0);
    assert.ok(after.history.some(
      (event) => event.playerId === after.players[1].playerId && event.moveTokenIndex != null,
    ));
    assert.ok(after.history.some((event) => event.randomnessSource === "ludopaws-server-csprng-v1"));
    if (after.botTurn) {
      // A six grants an extra turn and schedules the bot again; another face
      // correctly passes the turn to the human and clears the bot metadata.
      assert.equal(after.botTurn.eventIndex, 1);
      assert.equal(after.botTurn.inProgress, false);
    } else {
      assert.notEqual(after.turnSeat, 1);
    }
  },
);


test(
  "finished bot-filled match is explicitly kept out of the competitive leaderboard",
  { concurrency: false },
  async () => {
    const ctx = makeContext();
    const env = makeEnv();
    let leaderboardRecordCalls = 0;
    env.LUDOPROOF_LEADERBOARD = {
      idFromName(name) {
        assert.equal(name, "global");
        return name;
      },
      get() {
        return {
          async fetch(request) {
            if (new URL(request.url).pathname === "/record") {
              leaderboardRecordCalls += 1;
            }
            return Response.json({ ok: true });
          },
        };
      },
    };

    const room = new MatchRoom(ctx, env);
    const host = await createMatch(room, "LPBOTUNRAN");
    await joinMatch(room, "Maya");
    await startMatch(room, host.playerToken);

    const state = await ctx.storage.get("match-state");
    state.players[1].playerType = "BOT";
    state.players[1].profileId = null;
    state.status = "FINISHED";
    state.winnerPlayerId = state.players[0].playerId;
    state.winnerTeamId = null;
    state.finishedAt = Date.now();
    state.pendingRoll = null;
    await ctx.storage.put("match-state", state);

    const result = await json(
      await room.fetch(
        roomRequest("/state", {
          token: host.playerToken,
        }),
      ),
    );
    assert.equal(result.response.status, 200);
    assert.equal(result.body.state.players[1].playerType, "BOT");
    assert.equal(leaderboardRecordCalls, 0);
    assert.ok(
      (await ctx.storage.get("match-state")).leaderboardRecordedAt > 0,
    );
  },
);


test("native CSPRNG roll resolves and retries idempotently", async () => {
  const { room, host, env, ctx } = await setupActiveMatch(2);
  const clientSeed = "1".repeat(64);
  const clientCommitment = await sha256Hex(
    "ludopaws:native-roll:v1:client-commit:" + clientSeed,
  );
  const request = () =>
    roomRequest("/roll", {
      method: "POST",
      token: host.playerToken,
      body: { clientSeed, clientCommitment },
    });

  const first = await json(await room.fetch(request()));
  assert.equal(first.response.status, 200);
  assert.ok(first.body.outcome >= 1 && first.body.outcome <= 6);
  assert.equal(first.body.proof.protocol, "ludopaws-server-csprng-v1");
  assert.equal(await verifyFastDiceReceipt(first.body.proof), true);
  assert.equal(first.body.state.randomEventIndex, 1);
  assert.ok(
    first.body.state.history.some(
      (event) =>
        event.fastDiceReceipt?.roundId === first.body.roundId &&
        event.outcome === first.body.outcome,
    ),
  );

  const replay = await json(await room.fetch(request()));
  assert.equal(replay.response.status, 200);
  assert.equal(replay.body.replayed, true);
  assert.equal(replay.body.roundId, first.body.roundId);
  assert.equal(replay.body.outcome, first.body.outcome);
  assert.ok((await ctx.storage.get("match-state")).history.length >= 1);
});
