import test from "node:test";
import assert from "node:assert/strict";
import { MatchRoom } from "../src/match-room.js";
import { MatchRoom as BotMatchRoom } from "../src/match-room-bots.js";
import { derivePlayerIdentity, sha256Hex } from "../src/crypto.js";
import { addPlayer, attachHostAuth, newMatch, startMatch, actingSeatForTurn } from "../src/game.js";
import { verifyFastDiceProof, FAST_DICE_PROTOCOL } from "../src/dice-fast.js";

class Storage {
  values = new Map();
  writes = 0;
  async get(key) { return structuredClone(this.values.get(key)); }
  async put(key, value) { this.writes++; this.values.set(key, structuredClone(value)); }
  async setAlarm(at) { this.alarmAt = at; }
}

async function fixture({ mode = "ONLINE", Room = MatchRoom, bot = false } = {}) {
  const env = { LUDOPROOF_SESSION_HMAC_KEY: "fast_dice_test_hmac_key_12345678901234567890" };
  const storage = new Storage();
  const messages = [];
  const ctx = { storage, getWebSockets: () => [{ send: (message) => messages.push(JSON.parse(message)) }] };
  const matchId = "LPFASTROLL";
  const count = mode === "TEAM_UP" ? 4 : 2;
  const identities = [];
  for (let seat = 0; seat < count; seat++) {
    const joinRequestId = crypto.randomUUID();
    identities.push({ ...await derivePlayerIdentity(env, matchId, joinRequestId), joinRequestId });
  }
  let state = newMatch({
    matchId, hostPlayerId: identities[0].playerId, hostDisplayName: "Host", now: Date.now(),
    targetPlayerCount: count, matchMode: mode, rulesetKey: mode === "TEAM_UP" ? "TEAM_UP_V4" : "CLASSIC_V4",
  });
  state = attachHostAuth(state, await sha256Hex("ludoproof:player-token:v1:" + identities[0].playerToken), Date.now());
  for (let seat = 1; seat < count; seat++) {
    state = addPlayer(state, {
      playerId: identities[seat].playerId, displayName: "Player " + seat,
      playerType: bot && seat === 1 ? "BOT" : "HUMAN",
      tokenAuthHash: await sha256Hex("ludoproof:player-token:v1:" + identities[seat].playerToken), now: Date.now(),
    });
  }
  state.players.forEach((player, seat) => { player.joinRequestId = identities[seat].joinRequestId; });
  state = startMatch(state, identities[0].playerId, Date.now());
  state.history = [];
  await storage.put("match-state", state);
  storage.writes = 0;
  return { storage, ctx, env, messages, identities, room: new Room(ctx, env) };
}

async function action(room, token, path, body) {
  const response = await room.fetch(new Request("https://room" + path, {
    method: "POST", headers: { authorization: "Bearer " + token, "content-type": "application/json" },
    body: JSON.stringify(body),
  }));
  return { status: response.status, ...await response.json() };
}
const rollBody = (expectedEventIndex = 0) => ({ clientRequestId: crypto.randomUUID(), expectedEventIndex });

for (const mode of ["ONLINE", "FRIENDS", "TEAM_UP"]) {
  test(mode + " resolves one authoritative roll in one durable write with no provider calls", async (t) => {
    t.mock.method(globalThis, "fetch", async () => { throw new Error("external fetch forbidden"); });
    const f = await fixture({ mode });
    const before = await f.storage.get("match-state");
    const result = await action(f.room, f.identities[0].playerToken, "/roll", rollBody());
    assert.equal(result.status, 200);
    assert.equal(result.outcome, 6, "V4 opening six is preserved");
    assert.equal(result.openingRollApplied, true);
    assert.equal(result.replayed, false);
    assert.deepEqual(result.legalTokenIndexes, [0, 1, 2, 3]);
    assert.equal(await verifyFastDiceProof(result.proof), true);
    assert.equal(result.proof.protocol, FAST_DICE_PROTOCOL);
    assert.equal(result.state.randomEventIndex, 1);
    assert.equal(result.state.revision, before.revision + 1);
    assert.equal(result.state.pendingRoll.status, "RESOLVED");
    assert.equal(result.state.history.length, 1);
    assert.equal(result.state.history[0].outcome, 6);
    assert.equal(result.state.lastRoll.proofDigest, result.proofDigest);
    assert.equal(JSON.stringify(result.state).includes("serverSeed"), false);
    assert.equal(JSON.stringify(result.state).includes("rollRequests"), false);
    assert.equal(f.storage.writes, 1);
    assert.equal(f.messages.length, 1);
    assert.equal(f.messages[0].state.randomEventIndex, 1);
  });
}

test("concurrent retries and a restarted room replay the same persisted result", async () => {
  const f = await fixture();
  const body = rollBody();
  const results = await Promise.all(Array.from({ length: 8 }, () => action(f.room, f.identities[0].playerToken, "/roll", body)));
  assert.ok(results.every((result) => result.status === 200));
  assert.equal(results.filter((result) => result.replayed === false).length, 1);
  assert.ok(results.every((result) => result.proofDigest === results[0].proofDigest));
  assert.equal(f.storage.writes, 1);
  const restarted = new MatchRoom(f.ctx, f.env);
  const retry = await action(restarted, f.identities[0].playerToken, "/roll", body);
  assert.equal(retry.replayed, true);
  assert.deepEqual(retry.proof, results[0].proof);
  assert.equal((await f.storage.get("match-state")).randomEventIndex, 1);
});

test("a delayed retry cannot replace a later pending roll and move retries stay idempotent", async () => {
  const f = await fixture();
  const token = f.identities[0].playerToken;
  const body = rollBody();
  const first = await action(f.room, token, "/roll", body);
  const move = await action(f.room, token, "/move", { tokenIndex: 0, eventIndex: 0 });
  assert.equal(move.status, 200);
  assert.equal(move.state.players[0].tokens[0], 0);
  assert.equal(move.extraTurn, true);
  const movedAgain = await action(f.room, token, "/move", { tokenIndex: 0, eventIndex: 0 });
  assert.equal(movedAgain.replayed, true);
  const changed = await action(f.room, token, "/move", { tokenIndex: 1, eventIndex: 0 });
  assert.equal(changed.error, "MOVE_ALREADY_APPLIED");
  const second = await action(f.room, token, "/roll", rollBody(1));
  const revision = second.state.revision;
  const retry = await action(f.room, token, "/roll", body);
  assert.equal(retry.proofDigest, first.proofDigest);
  assert.equal(retry.awaitingMove, false);
  assert.deepEqual(retry.legalTokenIndexes, []);
  assert.equal(retry.state.revision, revision);
  assert.equal(retry.state.pendingRoll?.eventIndex ?? second.state.pendingRoll?.eventIndex, second.state.pendingRoll?.eventIndex);
  assert.equal(retry.state.randomEventIndex, 2);
});

test("authorization, turn ownership, request IDs and event indexes are checked before dice", async () => {
  const f = await fixture();
  const badAuth = await action(f.room, "invalid", "/roll", rollBody());
  assert.equal(badAuth.status, 403);
  const wrongTurn = await action(f.room, f.identities[1].playerToken, "/roll", rollBody());
  assert.equal(wrongTurn.error, "NOT_YOUR_TURN");
  const missingId = await action(f.room, f.identities[0].playerToken, "/roll", { expectedEventIndex: 0 });
  assert.equal(missingId.error, "INVALID_CLIENT_REQUEST_ID");
  const missingIndex = await action(f.room, f.identities[0].playerToken, "/roll", { clientRequestId: crypto.randomUUID() });
  assert.equal(missingIndex.error, "INVALID_EVENT_INDEX");
  const stale = await action(f.room, f.identities[0].playerToken, "/roll", rollBody(42));
  assert.equal(stale.error, "EVENT_INDEX_DRIFT");
  assert.equal(f.storage.writes, 0);
  const state = await f.storage.get("match-state");
  state.players[0].forfeitedAt = Date.now();
  await f.storage.put("match-state", state);
  const forfeited = await action(f.room, f.identities[0].playerToken, "/roll", rollBody());
  assert.equal(forfeited.error, "PLAYER_FORFEITED");
});

test("bounded receipt retention rejects an evicted stale retry instead of rerolling", async () => {
  const f = await fixture();
  const original = rollBody();
  const first = await action(f.room, f.identities[0].playerToken, "/roll", original);
  await action(f.room, f.identities[0].playerToken, "/move", { tokenIndex: 0, eventIndex: 0 });
  for (let index = 1; index <= 65; index++) {
    const state = await f.storage.get("match-state");
    const seat = actingSeatForTurn(state);
    const token = f.identities[seat].playerToken;
    const result = await action(f.room, token, "/roll", rollBody(index));
    assert.equal(result.status, 200, JSON.stringify(result));
    if (result.awaitingMove) {
      const moved = await action(f.room, token, "/move", { tokenIndex: result.legalTokenIndexes[0], eventIndex: index });
      assert.equal(moved.status, 200);
    }
  }
  const before = await f.storage.get("match-state");
  assert.equal(Object.keys(before.rollRequests).length, 64);
  assert.equal(before.rollRequests[f.identities[0].playerId + ":" + original.clientRequestId], undefined);
  const retry = await action(f.room, f.identities[0].playerToken, "/roll", original);
  assert.equal(retry.status, 409);
  assert.equal(retry.error, "EVENT_INDEX_DRIFT");
  const after = await f.storage.get("match-state");
  assert.equal(after.randomEventIndex, before.randomEventIndex);
  assert.equal(after.revision, before.revision);
  assert.ok(first.proofDigest);
});

test("bots roll once through the same provider-free handler and retain the result hold", async () => {
  const f = await fixture({ Room: BotMatchRoom, bot: true });
  const state = await f.storage.get("match-state");
  state.turnSeat = 1;
  state.turnDeadlineSeat = 1;
  await f.storage.put("match-state", state);
  await f.room.fetch(new Request("https://room/state", { headers: { authorization: "Bearer " + f.identities[0].playerToken } }));
  const scheduled = await f.storage.get("match-state");
  scheduled.botTurn.runAt = Date.now() - 1;
  await f.storage.put("match-state", scheduled);
  await f.room.alarm();
  const shown = await f.storage.get("match-state");
  assert.equal(shown.pendingRoll.status, "RESOLVED");
  assert.equal(shown.pendingRoll.outcome, 6);
  assert.ok(shown.players[1].tokens.every((token) => token === -1));
  assert.equal(shown.botTurn.inProgress, false);
  assert.ok(shown.botTurn.runAt > Date.now());
  shown.botTurn.runAt = Date.now() - 1;
  await f.storage.put("match-state", shown);
  await f.room.alarm();
  const moved = await f.storage.get("match-state");
  assert.equal(moved.randomEventIndex, 1);
  assert.equal(moved.pendingRoll, null);
  assert.ok(moved.players[1].tokens.some((token) => token === 0));
  assert.equal(Object.keys(moved.rollRequests).length, 1);
});

test("legacy commit/reveal paths explicitly require the single-call protocol", async () => {
  const f = await fixture();
  for (const path of ["/roll/commit", "/roll/reveal"]) {
    const result = await action(f.room, f.identities[0].playerToken, path, {});
    assert.equal(result.status, 410);
    assert.equal(result.error, "ROLL_PROTOCOL_UPGRADE_REQUIRED");
  }
});

test("two different concurrent roll commands cannot consume the same turn", async () => {
  const f = await fixture();
  const results = await Promise.all([
    action(f.room, f.identities[0].playerToken, "/roll", rollBody()),
    action(f.room, f.identities[0].playerToken, "/roll", rollBody()),
  ]);
  assert.equal(results.filter((result) => result.status === 200).length, 1);
  assert.equal(results.filter((result) => result.status === 409).length, 1);
  assert.equal(f.storage.writes, 1);
  assert.equal((await f.storage.get("match-state")).randomEventIndex, 1);
});

test("a failure after the atomic state save replays the durable result on retry", async () => {
  const f = await fixture();
  let failAlarm = true;
  f.storage.setAlarm = async () => {
    if (failAlarm) { failAlarm = false; throw new Error("alarm unavailable"); }
  };
  const body = rollBody();
  const first = await action(f.room, f.identities[0].playerToken, "/roll", body);
  assert.equal(first.status, 500);
  const saved = await f.storage.get("match-state");
  assert.equal(saved.randomEventIndex, 1);
  const retry = await action(f.room, f.identities[0].playerToken, "/roll", body);
  assert.equal(retry.status, 200);
  assert.equal(retry.replayed, true);
  assert.equal(retry.proofDigest, saved.pendingRoll.proofDigest);
  assert.equal(f.storage.writes, 1);
});
