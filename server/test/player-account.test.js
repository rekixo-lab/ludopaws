import test, { afterEach } from "node:test";
import { DatabaseSync } from "node:sqlite";
import assert from "node:assert/strict";
import { PlayerAccount } from "../src/player-account.js";

const databases = new Set();
afterEach(() => { for (const database of databases) database.close(); databases.clear(); });

class AccountSqlFixture {
  constructor() {
    this.database = new DatabaseSync(":memory:");
    databases.add(this.database);
  }

  exec(statement, ...parameters) {
    const prepared = this.database.prepare(statement);
    if (prepared.columns().length) return prepared.all(...parameters);
    const rows = [];
    rows.rowsWritten = Number(prepared.run(...parameters).changes);
    return rows;
  }

  transactionSync(callback) {
    this.database.exec("BEGIN");
    try { const result = callback(); this.database.exec("COMMIT"); return result; }
    catch (error) { this.database.exec("ROLLBACK"); throw error; }
  }

  get accountMeta() { return new Map(this.exec("SELECT * FROM account_meta").map(row => [row.account_id, row])); }
  get deletedAccounts() { return new Map(this.exec("SELECT * FROM account_deletions").map(row => [row.account_id, row])); }
  get deletionClaims() { return new Map(this.exec("SELECT * FROM account_deletion_claims").map(row => [row.account_id, row])); }
  get wallet() { return new Map(this.exec("SELECT * FROM wallet").map(row => [row.account_id, row])); }
  get sessions() { return new Map(this.exec("SELECT * FROM account_sessions").map(row => [row.session_id, row])); }
  get ownedCosmetics() { return new Map(this.exec("SELECT * FROM owned_cosmetics").map(row => [row.cosmetic_id, row])); }
  get adProgress() { return new Map(this.exec("SELECT * FROM cosmetic_ad_progress").map(row => [row.cosmetic_id, row.progress])); }
  get walletTransactions() { return new Map(this.exec("SELECT * FROM wallet_transactions").map(row => [row.idempotency_key, row])); }
}

function makeCatalogNamespace(items, { fail = false } = {}) {
  return {
    idFromName(name) {
      assert.equal(name, "GLOBAL:V1");
      return name;
    },
    get() {
      return {
        async fetch(request) {
          assert.equal(new URL(request.url).pathname, "/catalog/current");
          if (fail) return Response.json({ error: "UNAVAILABLE" }, { status: 503 });
          return Response.json({ revision: 1, catalogVersion: 1, items, updatedAt: "2026-10-14T00:00:00.000Z" });
        },
      };
    },
  };
}

function catalogItem(overrides = {}) {
  return {
    id: "paw_safari_01",
    category: "PAW",
    name: "Safari Paw",
    description: "A catalog-managed cosmetic",
    status: "PUBLISHED",
    contentReady: true,
    asset: {
      thumbnail: "r2://published/paw_safari_01/v1/thumbnail.png",
      manifest: "r2://published/paw_safari_01/v1/manifest.json",
      version: 1,
      sha256: "a".repeat(64),
      format: "GLB",
    },
    unlock: { channels: ["GEMS"], gems: { enabled: true, price: 125 } },
    availability: {
      worldwide: true, allowedCountries: [], blockedCountries: [],
      startsAt: null, endsAt: null,
    },
    catalogVersion: 1,
    ...overrides,
  };
}

function makeAccount(envOverrides = {}) {
  const sql = new AccountSqlFixture();
  const ctx = {
    storage: { sql, transactionSync: callback => sql.transactionSync(callback) },
    blockConcurrencyWhile: (callback) => callback(),
  };
  const env = {
    LUDOPROOF_SESSION_HMAC_KEY: "test-session-hmac-key-that-is-long-enough-123456",
    ...envOverrides,
  };
  return { account: new PlayerAccount(ctx, env), sql };
}

async function post(account, path, body) {
  return account.fetch(new Request("https://player-account" + path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  }));
}

test("account deletion requires explicit confirmation, clears server-owned data, and prevents resurrection", async () => {
  const { account, sql } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await post(account, "/session/create", { accountId, displayName: "Delete Me" });
  assert.equal(created.status, 200);
  const session = await created.json();
  assert.equal(sql.wallet.get(accountId).balance, 0);

  const denied = await post(account, "/account/delete", {
    accountId, sessionId: session.sessionId, confirmation: "wrong",
  });
  assert.equal(denied.status, 400);
  assert.ok(sql.accountMeta.has(accountId));

  const deleted = await post(account, "/account/delete", {
    accountId, sessionId: session.sessionId, confirmation: "DELETE_MY_ACCOUNT",
  });
  assert.equal(deleted.status, 200);
  assert.equal((await deleted.json()).deleted, true);
  assert.equal(sql.accountMeta.has(accountId), false);
  assert.equal(sql.wallet.has(accountId), false);
  assert.equal(sql.sessions.size, 0);
  assert.ok(sql.deletedAccounts.has(accountId));

  const retry = await post(account, "/account/delete", {
    accountId, sessionId: session.sessionId, confirmation: "DELETE_MY_ACCOUNT",
  });
  assert.equal(retry.status, 200);
  assert.equal((await retry.json()).replay, true);

  const resurrect = await post(account, "/session/create", { accountId, displayName: "Again" });
  assert.equal(resurrect.status, 410);
  assert.equal((await resurrect.json()).error, "ACCOUNT_DELETED");
});

test("account sessions are required to read the server-owned wallet and cannot be replayed for another account", async () => {
  const { account } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const createdResponse = await post(account, "/session/create", { accountId, displayName: "Test Player" });
  assert.equal(createdResponse.status, 200);
  const created = await createdResponse.json();
  assert.equal(created.accountId, accountId);
  assert.equal(created.wallet.balance, 0);
  assert.ok(created.wallet.ownedCosmetics.includes("dice_classic"));

  const walletResponse = await post(account, "/wallet/get", { accountId, sessionId: created.sessionId });
  assert.equal(walletResponse.status, 200);
  assert.equal((await walletResponse.json()).wallet.balance, 0);

  const forgedAccountResponse = await post(account, "/wallet/get", {
    accountId: "1b3d4b94-e1f0-4bc9-9cd5-592b7e0a1f21",
    sessionId: created.sessionId,
  });
  assert.equal(forgedAccountResponse.status, 401);
  assert.equal((await forgedAccountResponse.json()).error, "ACCOUNT_SESSION_INVALID");
});

test("cosmetic wallet purchase atomically debits gems, owns/selects once, and is idempotent", async () => {
  const { account, sql } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Test Player" })).json();
  sql.exec("UPDATE wallet SET balance = ? WHERE account_id = ?", 750, accountId);

  const purchase = {
    accountId,
    sessionId: created.sessionId,
    cosmeticId: "avatar_2",
    clientRequestId: "4c4e7f90-68e5-4c30-8fb8-c5a7b0f0f8aa",
  };
  const firstResponse = await post(account, "/wallet/cosmetic/purchase", purchase);
  assert.equal(firstResponse.status, 200);
  const first = await firstResponse.json();
  assert.equal(first.wallet.balance, 250);
  assert.equal(first.wallet.ownedCosmetics.includes("avatar_2"), true);
  assert.equal(first.wallet.selectedCosmetics.AVATAR, "avatar_2");
  assert.equal(sql.walletTransactions.size, 1);

  const replayResponse = await post(account, "/wallet/cosmetic/purchase", purchase);
  assert.equal(replayResponse.status, 200);
  const replay = await replayResponse.json();
  assert.equal(replay.replay, true);
  assert.equal(replay.wallet.balance, 250);
  assert.equal(sql.walletTransactions.size, 1);
});


test("content-ready cosmetic purchases do not require client-only level progression", async () => {
  const { account, sql } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Cosmetic Tester" })).json();
  sql.exec("UPDATE wallet SET balance = ? WHERE account_id = ?", 800, accountId);

  const response = await post(account, "/wallet/cosmetic/purchase", {
    accountId,
    sessionId: created.sessionId,
    cosmeticId: "avatar_10",
    clientRequestId: "8a9b7c6d-5e4f-4321-9876-123456789abc",
  });

  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.wallet.balance, 300);
  assert.equal(body.wallet.ownedCosmetics.includes("avatar_10"), true);
  assert.equal(body.wallet.selectedCosmetics.AVATAR, "avatar_10");
});

test("refresh-token retries with the same request ID return the same rotated token", async () => {
  const { account } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const session = await (await post(account, "/session/create", { accountId, displayName: "Test Player" })).json();
  const refreshRequest = {
    accountId,
    sessionId: session.sessionId,
    refreshToken: session.refreshToken,
    clientRequestId: "4c4e7f90-68e5-4c30-8fb8-c5a7b0f0f8aa",
  };

  const firstResponse = await post(account, "/session/refresh", refreshRequest);
  assert.equal(firstResponse.status, 200);
  const first = await firstResponse.json();
  assert.equal(first.replay, false);

  const retryResponse = await post(account, "/session/refresh", refreshRequest);
  assert.equal(retryResponse.status, 200);
  const retry = await retryResponse.json();
  assert.equal(retry.replay, true);
  assert.equal(retry.refreshToken, first.refreshToken);
  assert.equal(retry.sessionExpiresAt, first.sessionExpiresAt);
});

test("cosmetic-targeted AdMob view grants the configured 5 gems and progress exactly once", async () => {
  const { account, sql } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const sessionResponse = await post(account, "/session/create", { accountId, displayName: "Ad Tester" });
  assert.equal(sessionResponse.status, 200);
  const event = {
    accountId,
    transactionId: "a1b2c3d4e5f60718293a4b5c6d7e8f90",
    kind: "cosmetic",
    cosmeticId: "character_goat_lumi",
  };

  const firstResponse = await post(account, "/wallet/reward-ad", event);
  assert.equal(firstResponse.status, 200);
  const first = await firstResponse.json();
  assert.equal(first.wallet.balance, 5);
  assert.equal(sql.wallet.get(accountId).balance, 5);
  assert.equal(sql.adProgress.get("character_goat_lumi"), 1);
  assert.equal(sql.walletTransactions.size, 1);
  const transaction = [...sql.walletTransactions.values()][0];
  assert.equal(transaction.kind, "REWARDED_AD_COSMETIC_PROGRESS_AND_GEMS");
  assert.equal(transaction.delta, 5);
  assert.equal(transaction.balance_after, 5);

  const replayResponse = await post(account, "/wallet/reward-ad", event);
  assert.equal(replayResponse.status, 200);
  const replay = await replayResponse.json();
  assert.equal(replay.replay, true);
  assert.equal(replay.wallet.balance, 5);
  assert.equal(sql.wallet.get(accountId).balance, 5);
  assert.equal(sql.adProgress.get("character_goat_lumi"), 1);
  assert.equal(sql.walletTransactions.size, 1);
});


test("catalog-managed purchases use the current server price and grant the catalog category", async () => {
  const { account, sql } = makeAccount({
    LUDOPROOF_STORE_CATALOG: makeCatalogNamespace([
      catalogItem({ id: "avatar_2", category: "AVATAR", name: "Premium Avatar" }),
    ]),
  });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Catalog Player" })).json();
  sql.exec("UPDATE wallet SET balance = ? WHERE account_id = ?", 200, accountId);

  const response = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "avatar_2",
    clientRequestId: "d47ddef4-9c33-44da-9187-5d774267a3c5",
    catalogCountry: "IN",
    // A client has no price field here; the DO takes its price from published catalog state.
  });
  assert.equal(response.status, 200);
  const result = await response.json();
  assert.equal(result.wallet.balance, 75);
  assert.ok(result.wallet.ownedCosmetics.includes("avatar_2"));
  assert.equal(result.wallet.selectedCosmetics.AVATAR, "avatar_2");
  assert.equal(sql.walletTransactions.size, 1);
});

test("archived catalog entry cannot bypass its status by falling back to the legacy cosmetic ID", async () => {
  const archived = catalogItem({
    id: "avatar_2",
    category: "AVATAR",
    name: "Archived Avatar",
    status: "ARCHIVED",
    unlock: { channels: ["GEMS"], gems: { enabled: true, price: 1 } },
  });
  const { account, sql } = makeAccount({
    LUDOPROOF_STORE_CATALOG: makeCatalogNamespace([archived]),
  });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Catalog Player" })).json();
  sql.exec("UPDATE wallet SET balance = ? WHERE account_id = ?", 500, accountId);

  const response = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "avatar_2",
    clientRequestId: "e25b0212-af25-44ef-b9f4-c280c9d32e31",
  });
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "COSMETIC_UNAVAILABLE");
  assert.equal(sql.wallet.get(accountId).balance, 500);
  assert.equal(sql.walletTransactions.size, 0);
});

test("catalog-managed multi-channel purchase requires an explicit enabled channel and respects country rules", async () => {
  const multi = catalogItem({
    id: "avatar_2",
    category: "AVATAR",
    name: "Multi-channel Avatar",
    unlock: {
      channels: ["GEMS", "REWARDED_ADS"],
      gems: { enabled: true, price: 90 },
      rewardedAds: { enabled: true, requiredCompletedAds: 2 },
    },
    availability: {
      worldwide: false, allowedCountries: ["IN"], blockedCountries: [],
      startsAt: null, endsAt: null,
    },
  });
  const { account, sql } = makeAccount({
    LUDOPROOF_STORE_CATALOG: makeCatalogNamespace([multi]),
  });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Catalog Player" })).json();
  sql.exec("UPDATE wallet SET balance = ? WHERE account_id = ?", 150, accountId);

  const wrongCountry = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "avatar_2",
    clientRequestId: "b6b07c67-7c13-4a54-83ab-37e79d097d6f", catalogCountry: "US",
  });
  assert.equal(wrongCountry.status, 400);
  assert.equal((await wrongCountry.json()).error, "COSMETIC_UNAVAILABLE");

  const ambiguous = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "avatar_2",
    clientRequestId: "8cc64312-e42b-4d4d-a6b2-3cfd16c3f098", catalogCountry: "IN",
  });
  assert.equal(ambiguous.status, 400);
  assert.equal((await ambiguous.json()).error, "COSMETIC_UNLOCK_CHANNEL_REQUIRED");
  assert.equal(sql.wallet.get(accountId).balance, 150);

  const selectedChannel = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "avatar_2", unlockChannel: "GEMS",
    clientRequestId: "c0e51f8e-2663-4d35-a0d1-248eaf508282", catalogCountry: "IN",
  });
  assert.equal(selectedChannel.status, 200);
  assert.equal((await selectedChannel.json()).wallet.balance, 60);
});

test("catalog-managed rewarded ads verify current eligibility and can progress to ownership", async () => {
  const rewardItem = catalogItem({
    id: "avatar_2",
    category: "AVATAR",
    name: "Reward Avatar",
    unlock: {
      channels: ["REWARDED_ADS"],
      rewardedAds: { enabled: true, requiredCompletedAds: 2 },
    },
  });
  const { account, sql } = makeAccount({
    LUDOPROOF_STORE_CATALOG: makeCatalogNamespace([rewardItem]),
  });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await post(account, "/session/create", { accountId, displayName: "Reward Player" });
  assert.equal(created.status, 200);

  for (const transactionId of [
    "a1b2c3d4e5f60718293a4b5c6d7e8f90",
    "b1c2d3e4f5a60718293a4b5c6d7e8f90",
  ]) {
    const reward = await post(account, "/wallet/reward-ad", {
      accountId, transactionId, kind: "cosmetic", cosmeticId: "avatar_2", catalogCountry: "IN",
    });
    assert.equal(reward.status, 200);
  }
  assert.equal(sql.wallet.get(accountId).balance, 10);
  assert.equal(sql.adProgress.get("avatar_2"), 2);

  const purchase = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: (await created.json()).sessionId, cosmeticId: "avatar_2",
    clientRequestId: "7ab6933e-2465-4b01-9cd2-aa33b2020605", catalogCountry: "IN",
  });
  assert.equal(purchase.status, 200);
  assert.ok((await purchase.json()).wallet.ownedCosmetics.includes("avatar_2"));
});

test("new catalog IDs stay unpurchasable until server renderer and entitlement support ship", async () => {
  const { account, sql } = makeAccount({
    LUDOPROOF_STORE_CATALOG: makeCatalogNamespace([
      catalogItem({ id: "paw_safari_01", category: "PAW", name: "Safari Paw" }),
    ]),
  });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Locked Catalog Player" })).json();
  sql.exec("UPDATE wallet SET balance = ? WHERE account_id = ?", 500, accountId);

  const purchase = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "paw_safari_01",
    clientRequestId: "d45b3bf6-099e-4c23-8d71-e2e5170a5c6a", catalogCountry: "IN",
  });
  assert.equal(purchase.status, 400);
  assert.equal((await purchase.json()).error, "COSMETIC_UNAVAILABLE");
  assert.equal(sql.wallet.get(accountId).balance, 500);
  assert.equal(sql.walletTransactions.size, 0);
  assert.equal(sql.ownedCosmetics.has("paw_safari_01"), false);
});

test("archived dynamic cosmetic cannot be equipped but prior ownership is retained", async () => {
  const paw = catalogItem({ id: "avatar_2", category: "AVATAR", name: "Premium Avatar" });
  const catalog = makeCatalogNamespace([paw]);
  const { account, sql } = makeAccount({ LUDOPROOF_STORE_CATALOG: catalog });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Catalog Player" })).json();
  sql.exec("UPDATE wallet SET balance = ? WHERE account_id = ?", 200, accountId);

  const bought = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "avatar_2",
    clientRequestId: "afc2b41d-9f61-47b1-9732-0b4890ff3d42", catalogCountry: "IN",
  });
  assert.equal(bought.status, 200);
  assert.ok((await bought.json()).wallet.ownedCosmetics.includes("avatar_2"));

  // The namespace is a test double over current state; archive after ownership exists.
  paw.status = "ARCHIVED";
  const select = await post(account, "/wallet/cosmetic/select", {
    accountId, sessionId: created.sessionId, cosmeticId: "avatar_2", catalogCountry: "IN",
  });
  assert.equal(select.status, 400);
  assert.equal((await select.json()).error, "COSMETIC_UNAVAILABLE");

  const wallet = await post(account, "/wallet/get", { accountId, sessionId: created.sessionId });
  assert.equal(wallet.status, 200);
  assert.ok((await wallet.json()).wallet.ownedCosmetics.includes("avatar_2"));
});

test("catalog service outage fails closed for purchase and does not use the legacy table", async () => {
  const { account, sql } = makeAccount({
    LUDOPROOF_STORE_CATALOG: makeCatalogNamespace([], { fail: true }),
  });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Outage Player" })).json();
  sql.exec("UPDATE wallet SET balance = ? WHERE account_id = ?", 500, accountId);

  const response = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "avatar_2",
    clientRequestId: "d6c8e7f9-8251-48c7-93b8-60d532f78626",
  });
  assert.equal(response.status, 503);
  assert.equal((await response.json()).error, "STORE_CATALOG_UNAVAILABLE");
  assert.equal(sql.wallet.get(accountId).balance, 500);
  assert.equal(sql.walletTransactions.size, 0);
});

test("deletion preview locks account mutations until the deletion saga commits or retries", async () => {
  const { account, sql } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const createdResponse = await post(account, "/session/create", { accountId, displayName: "Delete Me" });
  assert.equal(createdResponse.status, 200);
  const session = await createdResponse.json();
  const before = sql.wallet.get(accountId).balance;

  const preview = await post(account, "/account/delete-preview", {
    accountId, sessionId: session.sessionId,
  });
  assert.equal(preview.status, 200);
  const previewBody = await preview.json();
  assert.equal(previewBody.deleted, false);
  assert.equal(previewBody.deletionPending, true);
  assert.equal(sql.deletionClaims.has(accountId), true);

  const wallet = await post(account, "/wallet/get", { accountId, sessionId: session.sessionId });
  assert.equal(wallet.status, 409);
  assert.equal((await wallet.json()).error, "ACCOUNT_DELETION_IN_PROGRESS");

  const profile = await post(account, "/profile/update", {
    accountId, sessionId: session.sessionId, displayName: "Changed during deletion",
  });
  assert.equal(profile.status, 409);
  assert.equal((await profile.json()).error, "ACCOUNT_DELETION_IN_PROGRESS");
  assert.equal(sql.wallet.get(accountId).balance, before);
  assert.equal(sql.accountMeta.get(accountId).display_name, "Delete Me");

  const normalSessionCheck = await post(account, "/session/check", { accountId, sessionId: session.sessionId });
  assert.equal(normalSessionCheck.status, 409);
  assert.equal((await normalSessionCheck.json()).error, "ACCOUNT_DELETION_IN_PROGRESS");

  const deletionRouteSessionCheck = await post(account, "/session/check", {
    accountId, sessionId: session.sessionId, allowDeletionPending: true,
  });
  assert.equal(deletionRouteSessionCheck.status, 200);

  const deletion = await post(account, "/account/delete", {
    accountId, sessionId: session.sessionId, confirmation: "DELETE_MY_ACCOUNT",
  });
  assert.equal(deletion.status, 200);
  assert.equal((await deletion.json()).deleted, true);
  assert.equal(sql.deletionClaims.has(accountId), false);
  assert.equal(sql.deletedAccounts.has(accountId), true);
});



for (const [cosmeticId, required] of [["character_goat_lumi", 10], ["character_donkey", 15]]) {
  test(`${cosmeticId} unlocks only at ${required} unique verified ads`, async () => {
    const { account, sql } = makeAccount();
    const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
    const session = await (await post(account, "/session/create", { accountId, displayName: "Character Tester" })).json();
    for (let i = 1; i <= required; i++) {
      const event = { accountId, kind: "cosmetic", cosmeticId, transactionId: i.toString(16).padStart(32, "0") };
      const result = await post(account, "/wallet/reward-ad", event);
      assert.equal(result.status, 200);
      const body = await result.json();
      assert.equal(body.wallet.rewardedAdProgress[cosmeticId], i);
      assert.equal(body.wallet.ownedCosmetics.includes(cosmeticId), i === required);
      const replay = await (await post(account, "/wallet/reward-ad", event)).json();
      assert.equal(replay.replay, true);
      assert.equal(replay.wallet.rewardedAdProgress[cosmeticId], i);
      assert.equal(replay.wallet.balance, i * 5);
    }
    assert.equal(sql.ownedCosmetics.has(cosmeticId), true);
    const other = cosmeticId === "character_goat_lumi" ? "character_donkey" : "character_goat_lumi";
    assert.equal(sql.ownedCosmetics.has(other), false);
    const equip = await post(account, "/wallet/cosmetic/select", { accountId, sessionId: session.sessionId, cosmeticId });
    assert.equal(equip.status, 200);
  });
}

test("only Classic dice are granted; Coming Soon dice cannot consume gems or ads", async () => {
  const { account, sql } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const session = await (await post(account, "/session/create", { accountId, displayName: "Dice Tester" })).json();
  assert.ok(session.wallet.ownedCosmetics.includes("dice_classic"));
  assert.equal(session.wallet.ownedCosmetics.includes("dice_pumpkin"), false);
  sql.exec("UPDATE wallet SET balance = ? WHERE account_id = ?", 1000, accountId);
  for (const cosmeticId of ["dice_pumpkin", "dice_diwali", "dice_football", "dice_cricket", "dice_summers", "dice_colors", "dice_heart"]) {
    const purchase = await post(account, "/wallet/cosmetic/purchase", {
      accountId, sessionId: session.sessionId, cosmeticId, clientRequestId: crypto.randomUUID(),
    });
    assert.equal(purchase.status, 400);
    assert.equal((await purchase.json()).error, "COSMETIC_UNAVAILABLE");
    const ad = await post(account, "/wallet/reward-ad", {
      accountId, cosmeticId, kind: "cosmetic", transactionId: "cccccccccccccccccccccccccccccccc",
    });
    assert.equal(ad.status, 400);
  }
  assert.equal(sql.wallet.get(accountId).balance, 1000);
  assert.equal(sql.walletTransactions.size, 0);
});

test("server match credit is idempotent, policy bounded and account scoped", async () => {
  const { account, sql } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  await post(account, "/session/create", { accountId, displayName: "Winner" });
  const award = { accountId, matchId: "LPABC23456", mode: "ONLINE", playerCount: 4,
    rank: 2, amount: 2 };
  const paid = await post(account, "/wallet/match-reward", award);
  assert.equal(paid.status, 200);
  assert.equal((await paid.json()).wallet.balance, 2);
  assert.equal(sql.walletTransactions.size, 1);
  const replay = await (await post(account, "/wallet/match-reward", award)).json();
  assert.equal(replay.replay, true);
  assert.equal(replay.amount, 2);
  assert.equal(replay.wallet.balance, 2);
  const forgery = await post(account, "/wallet/match-reward", { ...award, amount: 3 });
  assert.equal(forgery.status, 400);
  assert.equal(sql.wallet.get(accountId).balance, 2);
  const wrongRank = await post(account, "/wallet/match-reward", { ...award, rank: 1, amount: 3 });
  assert.equal(wrongRank.status, 409);
  assert.equal((await wrongRank.json()).error, "WALLET_IDEMPOTENCY_CONFLICT");
  assert.equal(sql.wallet.get(accountId).balance, 2);
});

test("three-player friend rewards obey 3/2/0 and daily earning cap with deterministic replay", async () => {
  const { account, sql } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  await post(account, "/session/create", { accountId, displayName: "Friend Winner" });
  for (let i = 0; i < 4; i++) {
    const matchId = "LPABC2345" + (i + 2);
    const body = { accountId, matchId, mode: "FRIENDS", playerCount: 3, rank: 1, amount: 3 };
    const response = await post(account, "/wallet/match-reward", body);
    assert.equal(response.status, 200);
    const receipt = await response.json();
    assert.equal(receipt.amount, i < 3 ? 3 : 0);
    assert.equal(receipt.wallet.balance, Math.min(i + 1, 3) * 3);
    const again = await (await post(account, "/wallet/match-reward", body)).json();
    assert.equal(again.replay, true);
    assert.equal(again.amount, receipt.amount);
  }
  assert.equal(sql.walletTransactions.size, 4);
  const runnerUp = await post(account, "/wallet/match-reward", {
    accountId, matchId: "LPABC23458", mode: "FRIENDS", playerCount: 3, rank: 2, amount: 2,
  });
  assert.equal(runnerUp.status, 200);
  assert.equal((await runnerUp.json()).amount, 0);
  const invalidThird = await post(account, "/wallet/match-reward", {
    accountId, matchId: "LPABC23459", mode: "FRIENDS", playerCount: 3, rank: 3, amount: 1,
  });
  assert.equal(invalidThird.status, 400);
});


test("simultaneous Friends claims cannot exceed the three-award daily cap", async () => {
  const { account, sql } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  await post(account, "/session/create", { accountId, displayName: "Cap Race" });
  const awards = await Promise.all(Array.from({ length: 8 }, (_, i) =>
    post(account, "/wallet/match-reward", {
      accountId, matchId: "LPABC2345" + (i + 2), mode: "FRIENDS",
      playerCount: 3, rank: 1, amount: 3,
    }).then(async response => ({ status: response.status, body: await response.json() })),
  ));
  assert.ok(awards.every(item => item.status === 200));
  assert.equal(awards.reduce((sum, item) => sum + item.body.amount, 0), 9);
  assert.equal(sql.wallet.get(accountId).balance, 9);
  assert.equal([...sql.walletTransactions.values()].filter(tx => tx.delta === 3).length, 3);
  assert.equal([...sql.walletTransactions.values()].filter(tx => tx.delta === 0).length, 5);
});

test("wallet confirms only the signed ad request receipt and preserves it across transaction replay", async () => {
  const { account } = makeAccount();
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Tester" })).json();
  const requestId = "00000000-0000-4000-8000-000000000099";
  const event = { accountId, transactionId: "a1b2c3d4e5f60718293a4b5c6d7e8f90", kind: "gems", requestId };
  const first = await (await post(account, "/wallet/reward-ad", event)).json();
  assert.deepEqual(first.wallet.creditedAdRequestIds, [requestId]);
  assert.equal(first.wallet.balance, 5);
  const replay = await (await post(account, "/wallet/reward-ad", event)).json();
  assert.deepEqual(replay.wallet.creditedAdRequestIds, [requestId]);
  assert.equal(replay.wallet.balance, 5);
  await post(account, "/account/delete", { accountId, sessionId: created.sessionId, confirmation: "DELETE_MY_ACCOUNT" });
});
