import test from "node:test";
import assert from "node:assert/strict";
import { MatchRoom } from "../src/match-room.js";

function fixture() {
  const values = new Map();
  const ctx = { storage: {
    async get(key) { return values.has(key) ? structuredClone(values.get(key)) : null; },
    async put(key, value) { values.set(key, structuredClone(value)); },
    async delete(key) { values.delete(key); },
    async setAlarm() {},
  } };
  const linked = new Set(["p2"]);
  const credits = [];
  const env = {
    LUDOPROOF_ACCOUNT_DIRECTORY: {
      idFromName(name) { assert.equal(name, "GLOBAL:V1"); return name; },
      get() { return { async fetch(request) {
        const { profileId } = await request.json();
        return Response.json({ accountId: linked.has(profileId) ? "account-" + profileId : null });
      } }; },
    },
    LUDOPROOF_PLAYER_ACCOUNTS: {
      idFromName(id) { return id; },
      get(id) { return { async fetch(request) {
        const payload = await request.json();
        assert.equal(id, payload.accountId);
        credits.push({ ...payload });
        return Response.json({ ok: true });
      } }; },
    },
  };
  const room = new MatchRoom(ctx, env);
  const state = {
    status: "FINISHED", rulesetKey: "CLASSIC_V6",
    matchId: "LPABC23456", matchMode: "FRIENDS", winnerPlayerId: "p1",
    finishOrderPlayerIds: ["p1", "p2", "p3"],
    players: ["p1", "p2", "p3"].map(playerId => ({ playerId, profileId: playerId,
      playerType: "HUMAN", tokens: [57, 57, 57, 57] })),
  };
  return { room, ctx, credits, linked, state };
}

test("one unlinked winner does not block another recipient; retry pays only the pending player", async () => {
  const { room, ctx, credits, linked, state } = fixture();
  await room.prepareMatchGemResult(state);
  await room.recordMatchGemResult();
  assert.deepEqual(credits.map(x => [x.accountId, x.amount]), [["account-p2", 2]]);
  const pending = await ctx.storage.get("gem-reward-outbox");
  assert.equal(pending.entries[0].delivered, false);
  assert.equal(pending.entries[1].delivered, true);
  assert.ok(pending.nextAt > Date.now() - 1000);
  linked.add("p1");
  await ctx.storage.put("gem-reward-outbox", { ...pending, nextAt: Date.now() - 1 });
  await room.recordMatchGemResult();
  assert.deepEqual(credits.map(x => [x.accountId, x.amount]),
    [["account-p2", 2], ["account-p1", 3]]);
  assert.equal(await ctx.storage.get("gem-reward-outbox"), null);
  assert.equal(await ctx.storage.get("gem-reward-done"), true);
  await room.recordMatchGemResult();
  assert.equal(credits.length, 2);
});
