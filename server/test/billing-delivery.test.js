import test from "node:test";
import assert from "node:assert/strict";
import { StorePurchaseLedger } from "../src/store-purchase-ledger.js";

const purchase = { accountId: "a".repeat(64), productId: "gems_300", gemAmount: 300, removeAds: false,
  ownerAccountId: "00000000-0000-4000-8000-000000000081", purchaseTokenHash: "b".repeat(64), purchaseLedgerId: "c".repeat(64) };
function setup() {
  const values = new Map();
  let failCleanup = false;
  const ctx = { storage: {
    get: async key => structuredClone(values.get(key)),
    put: async (key, value) => values.set(key, structuredClone(value)),
    delete: async key => values.delete(key),
    setAlarm: async time => values.set("alarm", time),
    deleteAlarm: async () => { if (failCleanup) throw Error("alarm cleanup unavailable"); values.delete("alarm"); },
  } };
  let status = 503, grants = 0;
  const transactions = new Set();
  const env = { LUDOPROOF_PLAYER_ACCOUNTS: { idFromName: id => id, get: id => ({ fetch: async request => {
    assert.equal(id, purchase.ownerAccountId);
    assert.equal(new URL(request.url).pathname, "/wallet/play-delivery");
    const body = await request.json();
    assert.equal(body.purchaseLedgerId, purchase.purchaseLedgerId);
    assert.equal(body.sessionId, undefined);
    if (status === 200 && !transactions.has(body.purchaseLedgerId)) { transactions.add(body.purchaseLedgerId); grants++; }
    return Response.json(status === 200 ? { ok: true } : { error: status === 404 ? "ACCOUNT_NOT_FOUND" : "UNAVAILABLE" }, { status });
  } }) } };
  let ledger = new StorePurchaseLedger(ctx, env);
  const call = async (path, body = purchase) => ledger.fetch(new Request("https://ledger" + path, { method: "POST", body: JSON.stringify(body) }));
  return { values, get ledger() { return ledger; }, restart: () => { ledger = new StorePurchaseLedger(ctx, env); },
    call, failCleanup: () => { failCleanup = true; }, setStatus: value => { status = value; }, grants: () => grants };
}

test("verified delivery survives lost consume completion, wallet outage and object recreation", async () => {
  const x = setup();
  assert.equal((await x.call("/reserve")).status, 200);
  await x.ledger.alarm();
  assert.equal(x.grants(), 0); // Reservation alone never authorizes money.
  assert.equal((await x.call("/verified")).status, 200);
  assert.ok(x.values.get("alarm"));
  // Simulate client/server disappearing immediately after Publisher consume,
  // before /complete and without any future client purchase-token retry.
  await x.ledger.alarm();
  assert.equal(x.values.get("purchase:v1").lastDeliveryError, "WALLET_DELIVERY_FAILED");
  x.restart();
  x.setStatus(200);
  await x.ledger.alarm();
  assert.equal(x.grants(), 1);
  await x.ledger.alarm();
  assert.equal(x.grants(), 1);
  assert.ok(x.values.get("purchase:v1").deliveredAt);
  assert.equal(x.values.has("alarm"), false);
  assert.equal((await x.call("/complete")).status, 200);
  assert.ok(x.values.get("purchase:v1").deliveredAt);
  assert.equal(JSON.stringify(x.values.get("purchase:v1")).includes("purchaseToken\""), false);
});

test("refund and deletion stop queued deliveries; owner mismatches cannot authorize them", async () => {
  const x = setup();
  await x.call("/reserve");
  assert.equal((await x.call("/verified", { ...purchase, accountId: "d".repeat(64) })).status, 409);
  await x.call("/verified");
  await x.call("/revoke", {});
  x.setStatus(200);
  await x.ledger.alarm();
  assert.equal(x.grants(), 0);
  const deleted = setup();
  await deleted.call("/reserve"); await deleted.call("/verified");
  deleted.setStatus(404);
  await deleted.ledger.alarm();
  assert.equal(deleted.values.has("purchase:v1"), false);
});

test("alarm cleanup failure cannot undo delivered state or resurrect a deleted owner's record", async () => {
  for (const status of [200, 404]) {
    const x = setup();
    await x.call("/reserve"); await x.call("/verified");
    x.setStatus(status); x.failCleanup();
    await assert.rejects(x.ledger.alarm(), /alarm cleanup unavailable/);
    if (status === 200) assert.ok(x.values.get("purchase:v1").deliveredAt);
    else assert.equal(x.values.has("purchase:v1"), false);
    x.restart();
    await x.ledger.alarm();
    assert.equal(x.grants(), status === 200 ? 1 : 0);
  }
});
