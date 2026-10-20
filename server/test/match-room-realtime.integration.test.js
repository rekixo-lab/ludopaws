import test from "node:test";
import assert from "node:assert/strict";
import { MatchRoom } from "../src/match-room-realtime.js";
import worker from "../src/index.js";
import teamWorker from "../src/index-team.js";
import { MatchmakerQueue } from "../src/matchmaker-queue.js";

class Storage {
  values = new Map();
  alarmAt = null;
  async get(key) { return structuredClone(this.values.get(key)); }
  async put(key, value) { this.values.set(key, structuredClone(value)); }
  async setAlarm(at) { this.alarmAt = at; }
}

function fakeSocket(tags = []) {
  return {
    tags, messages: [], closed: [], attachment: {}, failSend: false,
    send(message) {
      if (this.failSend) throw new Error("socket disconnected");
      this.messages.push(JSON.parse(message));
    },
    close(code, reason) { this.closed.push({ code, reason }); },
    serializeAttachment(value) { this.attachment = structuredClone(value); },
    deserializeAttachment() { return structuredClone(this.attachment); },
  };
}

function request(path, token, body = null) {
  const headers = new Headers();
  if (token) headers.set("authorization", "Bearer " + token);
  if (body !== null) headers.set("content-type", "application/json");
  return new Request("https://room" + path, {
    method: body === null ? "GET" : "POST", headers,
    body: body === null ? undefined : JSON.stringify(body),
  });
}

async function setup({ mode = "ONLINE", playerCount = 2 } = {}) {
  const sockets = [];
  const ctx = {
    storage: new Storage(),
    getWebSockets: () => sockets,
    getTags: (socket) => socket.tags,
    acceptWebSocket(socket, tags) { socket.tags = tags; sockets.push(socket); },
  };
  const env = { LUDOPROOF_SESSION_HMAC_KEY: "session-key-for-realtime-".repeat(3) };
  const room = new MatchRoom(ctx, env);
  const created = await room.fetch(request("/create", null, {
    matchId: "LPABCDEFGH", displayName: "Alice", clientRequestId: crypto.randomUUID(),
    matchMode: mode, targetPlayerCount: playerCount,
    rulesetKey: mode === "TEAM_UP" ? "TEAM_UP_V4" : "CLASSIC_V4",
  }));
  assert.equal(created.status, 201, await created.clone().text());
  const host = await created.json();
  const players = [host];
  for (let i = 1; i < playerCount; i += 1) {
    const joined = await room.fetch(request("/join", null, {
      displayName: "Player " + i, clientRequestId: crypto.randomUUID(),
    }));
    assert.equal(joined.status, 201, await joined.clone().text());
    players.push(await joined.json());
  }
  const started = await room.fetch(request("/start", host.playerToken, {}));
  assert.equal(started.status, 200, await started.clone().text());
  const sender = fakeSocket(["player:" + host.playerId]);
  const receiver = fakeSocket(["player:" + players[1].playerId]);
  sockets.push(sender, receiver);
  return { room, ctx, env, host, players, sender, receiver };
}

async function command(room, socket, action, body, requestId = crypto.randomUUID()) {
  await room.webSocketMessage(socket, JSON.stringify({ type: "command", requestId, action, body }));
  return socket.messages.findLast((message) => message.type === "command_result" && message.requestId === requestId);
}

function assertPublicSnapshot(snapshot) {
  assert.ok(snapshot.players.length >= 2);
  const serialized = JSON.stringify(snapshot);
  for (const forbidden of ["tokenAuthHash", "joinRequestId", "playerToken", "serverSeed", "rollRequests", "clientRequestId"]) {
    assert.equal(serialized.includes(forbidden), false, forbidden + " must remain private");
  }
  assert.ok(snapshot.history.length <= 1);
}

test("socket roll/move and HTTP fallback share idempotency and push compact snapshots", async () => {
  const { room, ctx, host, sender, receiver } = await setup();
  const body = { clientRequestId: crypto.randomUUID(), expectedEventIndex: 0 };
  const rolled = await command(room, sender, "ROLL", body);
  assert.equal(rolled.statusCode, 200, JSON.stringify(rolled));
  assert.equal(rolled.response.state.randomEventIndex, 1);
  const retry = await room.fetch(request("/roll", host.playerToken, body));
  const replay = await retry.json();
  assert.equal(replay.replayed, true);
  assert.equal(replay.proofDigest, rolled.response.proofDigest);

  const pushed = receiver.messages.findLast((message) => message.type === "STATE_CHANGED");
  assertPublicSnapshot(pushed.state);
  assert.equal(pushed.revision, rolled.response.state.revision);
  assert.equal(pushed.state.randomEventIndex, 1);

  const moveBody = { clientRequestId: crypto.randomUUID(), tokenIndex: 0, eventIndex: 0 };
  const moved = await command(room, sender, "MOVE", moveBody);
  assert.equal(moved.statusCode, 200, JSON.stringify(moved));
  const beforeRetry = await ctx.storage.get("match-state");
  const movedAgain = await (await room.fetch(request("/move", host.playerToken, moveBody))).json();
  assert.equal(movedAgain.replayed, true);
  const afterRetry = await ctx.storage.get("match-state");
  assert.deepEqual(afterRetry.players.map((player) => player.tokens), beforeRetry.players.map((player) => player.tokens));
  assert.equal(afterRetry.revision, beforeRetry.revision);
  assertPublicSnapshot(receiver.messages.findLast((message) => message.type === "STATE_CHANGED").state);
});

test("simultaneous socket and HTTP retries resolve one roll and one move", async () => {
  const { room, ctx, host, sender } = await setup();
  const roll = { clientRequestId: crypto.randomUUID(), expectedEventIndex: 0 };
  const [socketRoll, httpRoll] = await Promise.all([
    command(room, sender, "ROLL", roll),
    room.fetch(request("/roll", host.playerToken, roll)),
  ]);
  const httpRollBody = await httpRoll.json();
  assert.equal(socketRoll.statusCode, 200);
  assert.equal(httpRoll.status, 200);
  assert.equal(socketRoll.response.proofDigest, httpRollBody.proofDigest);
  const move = { eventIndex: 0, tokenIndex: 0, clientRequestId: crypto.randomUUID() };
  const [socketMove, httpMove] = await Promise.all([
    command(room, sender, "MOVE", move),
    room.fetch(request("/move", host.playerToken, move)),
  ]);
  assert.equal(socketMove.statusCode, 200);
  assert.equal(httpMove.status, 200);
  const state = await ctx.storage.get("match-state");
  assert.equal(state.randomEventIndex, 1);
  assert.equal(state.history.length, 1);
  assert.equal(state.players[0].tokens[0], 0);
});

test("Team Up reconnect and lost acknowledgement preserve one authoritative dice outcome", async () => {
  const { room, ctx, host, sender, receiver } = await setup({ mode: "TEAM_UP", playerCount: 4 });
  sender.failSend = true; // command applied, acknowledgement lost
  const first = { clientRequestId: crypto.randomUUID(), expectedEventIndex: 0 };
  await command(room, sender, "ROLL", first, "lost-ack-team");
  const afterFirst = await ctx.storage.get("match-state");
  assert.equal(afterFirst.randomEventIndex, 1);
  // Emulate a returning Android client asking for a fresh authoritative snapshot.
  const fresh = await (await room.fetch(request("/state", host.playerToken))).json();
  assert.equal(fresh.state.revision, afterFirst.revision);
  const retry = await (await room.fetch(request("/roll", host.playerToken, first))).json();
  assert.equal(retry.replayed, true);
  assert.equal(retry.state.revision, afterFirst.revision);
  assert.equal(retry.state.randomEventIndex, 1);
  // A new action with stale event index must be rejected rather than rerolling.
  const stale = await room.fetch(request("/roll", host.playerToken, {
    clientRequestId: crypto.randomUUID(), expectedEventIndex: 0,
  }));
  assert.notEqual(stale.status, 200);
  const afterRetry = await ctx.storage.get("match-state");
  assert.equal(afterRetry.revision, afterFirst.revision);
  assert.ok(receiver.messages.some((message) => message.type === "STATE_CHANGED"));
});

test("commands use socket identity, reject malformed/oversized commands and preserve heartbeat", async () => {
  const { room, ctx, host, sender, players } = await setup();
  const before = await ctx.storage.get("match-state");
  const anonymous = fakeSocket();
  const denied = await command(room, anonymous, "ROLL", { clientRequestId: crypto.randomUUID(), expectedEventIndex: 0 });
  assert.equal(denied.statusCode, 401);
  assert.equal(denied.response.ok, false);
  const forged = fakeSocket(["player:" + players[1].playerId]);
  const wrongTurn = await command(room, forged, "ROLL", {
    playerId: host.playerId, playerToken: host.playerToken,
    clientRequestId: crypto.randomUUID(), expectedEventIndex: 0,
  });
  assert.equal(wrongTurn.statusCode, 409);
  assert.equal(wrongTurn.response.error, "NOT_YOUR_TURN");
  assert.equal((await command(room, sender, "DELETE_MATCH", {})).response.error, "INVALID_COMMAND");
  assert.equal((await command(room, sender, "ROLL", [])).response.error, "INVALID_COMMAND_BODY");
  const large = await command(room, sender, "ROLL", { text: "x".repeat(9_000) });
  assert.equal(large.statusCode, 413);
  await room.webSocketMessage(sender, "ping");
  assert.equal(sender.messages.at(-1).type, "PONG");
  const after = await ctx.storage.get("match-state");
  assert.equal(after.randomEventIndex, before.randomEventIndex);
  assert.deepEqual(after.players.map((player) => player.tokens), before.players.map((player) => player.tokens));
});

test("lost command acknowledgement and broken broadcast socket allow the same HTTP retry", async () => {
  const { room, ctx, host, sender, receiver } = await setup();
  sender.failSend = true;
  receiver.failSend = true;
  const body = { clientRequestId: crypto.randomUUID(), expectedEventIndex: 0 };
  await command(room, sender, "ROLL", body);
  assert.equal((await ctx.storage.get("match-state")).randomEventIndex, 1);
  const retry = await (await room.fetch(request("/roll", host.playerToken, body))).json();
  assert.equal(retry.replayed, true);
  assert.ok(sender.closed.some((closed) => closed.code === 1011));
  await room.webSocketClose(receiver, 1000, "finished");
  assert.ok(receiver.closed.some((closed) => closed.code === 1000));
});

test("authenticated connect immediately pushes a compact snapshot; invalid tokens cannot open sockets", async (t) => {
  const { room, ctx, host } = await setup();
  const OriginalResponse = globalThis.Response;
  const OriginalPair = globalThis.WebSocketPair;
  const pairs = [];
  globalThis.Response = class extends OriginalResponse {
    constructor(body, options) {
      const upgrade = options?.status === 101;
      super(body, upgrade ? { ...options, status: 200 } : options);
      if (upgrade) {
        Object.defineProperty(this, "status", { value: 101 });
        Object.defineProperty(this, "ok", { value: false });
        this.webSocket = options.webSocket;
      }
    }
  };
  globalThis.WebSocketPair = class {
    constructor() { this[0] = fakeSocket(); this[1] = fakeSocket(); pairs.push(this); }
  };
  t.after(() => { globalThis.Response = OriginalResponse; globalThis.WebSocketPair = OriginalPair; });
  const connect = (token) => new Request("https://room/events", {
    headers: { upgrade: "websocket", authorization: "Bearer " + token },
  });
  const invalid = await room.fetch(connect("bad-token"));
  assert.equal(invalid.status, 403);
  assert.equal(pairs.length, 0);
  const accepted = await room.fetch(connect(host.playerToken));
  assert.equal(accepted.status, 101);
  const server = pairs[0][1];
  const sync = server.messages[0];
  assert.equal(sync.type, "SYNC");
  assert.equal(sync.matchId, "LPABCDEFGH");
  assert.equal(sync.revision, sync.state.revision);
  assertPublicSnapshot(sync.state);
  assert.deepEqual(ctx.getTags(server), ["player:" + host.playerId]);
  assert.deepEqual(server.attachment, { playerId: host.playerId, voiceJoined: false });
});

test("HTTP and socket commands share bounded per-player rates; invalid identities cannot create buckets", async () => {
  const { room, host, sender, players } = await setup();
  // Starting consumed one host mutation. Ninety is the total window allowance.
  for (let i = 0; i < 88; i += 1) {
    assert.equal((await room.fetch(request("/move", host.playerToken, { tokenIndex: 0, eventIndex: 0 }))).status, 409);
  }
  const lastAllowed = await command(room, sender, "MOVE", { tokenIndex: 0, eventIndex: 0 });
  assert.equal(lastAllowed.statusCode, 409);
  const limited = await command(room, sender, "MOVE", { tokenIndex: 0, eventIndex: 0 });
  assert.equal(limited.statusCode, 429);
  const httpLimited = await room.fetch(request("/move", host.playerToken, { tokenIndex: 0, eventIndex: 0 }));
  assert.equal(httpLimited.status, 429);
  assert.ok(Number(httpLimited.headers.get("retry-after")) > 0);
  assert.equal((await room.fetch(request("/state", players[1].playerToken))).status, 200);
  for (let i = 0; i < 32; i += 1) {
    const response = await room.fetch(request("/state", "invalid-" + i));
    assert.equal(response.status, i < 30 ? 403 : 429);
  }
  assert.ok(room.gameplayRateBuckets.size <= 4 * 2 + 1);
  for (const bucket of room.gameplayRateBuckets.values()) bucket.windowId -= 1;
  assert.equal((await room.fetch(request("/move", host.playerToken, { tokenIndex: 0, eventIndex: 0 }))).status, 409);
});

test("team forfeiture and turn-timeout snapshots contain public state without another fetch", async () => {
  const { room, ctx, host, receiver } = await setup({ mode: "TEAM_UP", playerCount: 4 });
  const forfeited = await room.fetch(request("/start", host.playerToken, { forfeit: true }));
  assert.equal(forfeited.status, 200);
  const pushed = receiver.messages.findLast((message) => message.type === "STATE_CHANGED");
  assert.equal(pushed.state.status, "FINISHED");
  assertPublicSnapshot(pushed.state);

  const online = await setup();
  const state = await online.ctx.storage.get("match-state");
  state.turnDeadlineAt = Date.now() - 1;
  state.turnDeadlineSeat = 0;
  await online.ctx.storage.put("match-state", state);
  await online.room.alarm();
  const timeout = online.receiver.messages.findLast((message) => message.type === "STATE_CHANGED");
  assert.equal(timeout.state.turnSeat, 1);
  assertPublicSnapshot(timeout.state);
  assert.equal((await ctx.storage.get("match-state")).status, "FINISHED");
});

test("Friends voice peers recover from a dropped signaling socket without stale connected peers", async () => {
  const { room, ctx, host, players, sender, receiver } = await setup();
  // Authorized room state fixture: this test isolates signaling and reconnection;
  // friend invitation / token validation is covered by its own integration suite.
  const state = await ctx.storage.get("match-state");
  state.matchMode = "FRIENDS";
  await ctx.storage.put("match-state", state);
  await room.webSocketMessage(sender, JSON.stringify({ type: "VOICE_JOIN" }));
  await room.webSocketMessage(receiver, JSON.stringify({ type: "VOICE_JOIN" }));
  assert.deepEqual(sender.messages.findLast((m) => m.type === "VOICE_PEERS").playerIds, [players[1].playerId]);
  assert.deepEqual(receiver.messages.findLast((m) => m.type === "VOICE_PEERS").playerIds, [host.playerId]);

  await room.webSocketMessage(sender, JSON.stringify({
    type: "VOICE_SIGNAL", toPlayerId: players[1].playerId,
    signal: { type: "offer", sdp: "v=0\\n" },
  }));
  assert.equal(receiver.messages.findLast((m) => m.type === "VOICE_SIGNAL").fromPlayerId, host.playerId);
  await room.webSocketClose(receiver, 1000, "backgrounded");
  assert.deepEqual(sender.messages.findLast((m) => m.type === "VOICE_PEERS").playerIds, []);

  // Rejoined from a newly authenticated connection; the old socket must not rejoin.
  const recovered = fakeSocket(["player:" + players[1].playerId]);
  ctx.getWebSockets().push(recovered);
  await room.webSocketMessage(recovered, JSON.stringify({ type: "VOICE_JOIN" }));
  assert.deepEqual(sender.messages.findLast((m) => m.type === "VOICE_PEERS").playerIds, [players[1].playerId]);
  assert.deepEqual(recovered.messages.findLast((m) => m.type === "VOICE_PEERS").playerIds, [host.playerId]);
});

test("non-Friends matches cannot activate private voice signaling", async () => {
  const { room, sender, receiver, players } = await setup({ mode: "ONLINE" });
  await room.webSocketMessage(sender, JSON.stringify({ type: "VOICE_JOIN" }));
  await room.webSocketMessage(receiver, JSON.stringify({ type: "VOICE_JOIN" }));
  await room.webSocketMessage(sender, JSON.stringify({
    type: "VOICE_SIGNAL", toPlayerId: players[1].playerId,
    signal: { type: "offer", sdp: "v=0\\n" },
  }));
  assert.equal(receiver.messages.some((m) => m.type === "VOICE_SIGNAL"), false);
  assert.notEqual(sender.attachment.voiceJoined, true);
});

test("router sends authenticated gameplay directly to room and retains gate for public joins", async () => {
  const calls = [];
  let gateCalls = 0;
  const env = {
    LUDOPROOF_MATCHES: {
      idFromName: (name) => name,
      get: (id) => ({ fetch: async (request) => { calls.push({ id, path: new URL(request.url).pathname }); return Response.json({ ok: true }); } }),
    },
    LUDOPROOF_API_GATE: {
      idFromName: (name) => name,
      get: () => ({ fetch: async () => { gateCalls += 1; return Response.json({ allowed: true }); } }),
    },
  };
  for (const path of ["state", "events", "roll", "move"]) {
    const isGet = path === "state" || path === "events";
    const response = await worker.fetch(new Request("https://api.test/api/matches/LPABCDEFGH/" + path, {
      method: isGet ? "GET" : "POST",
      headers: { authorization: "Bearer lp_authenticated", "content-type": "application/json" },
      body: isGet ? undefined : "{}",
    }), env);
    assert.equal(response.status, 200);
  }
  assert.equal(gateCalls, 0);
  assert.deepEqual(calls.map((call) => call.path), ["/state", "/events", "/roll", "/move"]);
  await worker.fetch(new Request("https://api.test/api/matches/LPABCDEFGH/join", {
    method: "POST", headers: { "content-type": "application/json" }, body: "{}",
  }), env);
  assert.equal(gateCalls, 1);
});

test("new rooms receive a location hint from the creating request", async () => {
  const hints = [];
  const env = {
    LUDOPROOF_MATCHES: {
      idFromName: (name) => name,
      get: (_id, options) => { hints.push(options); return { fetch: async () => Response.json({ ok: true }, { status: 201 }) }; },
    },
    LUDOPROOF_API_GATE: {
      idFromName: (name) => name,
      get: () => ({ fetch: async () => Response.json({ allowed: true }) }),
    },
  };
  const create = new Request("https://api.test/api/matches", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ clientRequestId: crypto.randomUUID(), displayName: "Alice" }),
  });
  Object.defineProperty(create, "cf", { value: { continent: "AS" } });
  assert.equal((await worker.fetch(create, env)).status, 201);
  assert.deepEqual(hints, [{ locationHint: "apac" }]);
});

test("public matchmaking forwards the platform location and discards client-supplied hints", async () => {
  const seen = [];
  const gate = { idFromName: (name) => name, get: () => ({ fetch: async () => Response.json({ allowed: true }) }) };
  const queue = {
    idFromName: (name) => name,
    get: () => ({ fetch: async (request) => { seen.push(request.headers.get("x-ludopaws-match-location-hint")); return Response.json({ status: "SEARCHING" }); } }),
  };
  const env = { LUDOPROOF_API_GATE: gate, LUDOPROOF_MATCHMAKER: queue, LUDOPROOF_TEAM_MATCHMAKER: queue };
  for (const [handler, path] of [[worker, "/api/matchmaking/search"], [teamWorker, "/api/team-matchmaking/search"]]) {
    const request = new Request("https://api.test" + path, {
      method: "POST", headers: { "content-type": "application/json", "x-ludopaws-match-location-hint": "wnam" },
      body: JSON.stringify({ playerCount: 2 }),
    });
    Object.defineProperty(request, "cf", { value: { continent: "EU" } });
    assert.equal((await handler.fetch(request, env)).status, 200);
  }
  assert.deepEqual(seen, ["weur", "weur"]);
});

test("queued matches retain the first player's platform location when materialized later", async () => {
  const hints = [];
  const rooms = new Map();
  const env = { LUDOPROOF_SESSION_HMAC_KEY: "location-test-session-key-".repeat(3) };
  env.LUDOPROOF_MATCHES = {
    idFromName: (name) => name,
    get(id, options) {
      hints.push(options?.locationHint ?? null);
      if (!rooms.has(id)) rooms.set(id, new MatchRoom({ storage: new Storage() }, env));
      return rooms.get(id);
    },
  };
  const queue = new MatchmakerQueue({ storage: new Storage() }, env);
  const people = [
    { clientRequestId: crypto.randomUUID(), displayName: "Alice", characterId: "dog" },
    { clientRequestId: crypto.randomUUID(), displayName: "Bob", characterId: "cat" },
  ];
  for (const person of people) {
    const request = new Request("https://queue/search", {
      method: "POST", headers: { "content-type": "application/json", "x-ludopaws-match-location-hint": "apac" },
      body: JSON.stringify({ ...person, playerCount: 2 }),
    });
    const response = await queue.fetch(request);
    assert.ok(response.ok, await response.clone().text());
  }
  assert.equal(hints[0], "apac");
  const state = await queue.ctx.storage.get("matchmaking-state");
  assert.equal(state.assignments[people[0].clientRequestId].rulesetKey, "CLASSIC_V4");
});
