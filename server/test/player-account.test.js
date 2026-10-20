import test from "node:test";
import assert from "node:assert/strict";
import { PlayerAccount } from "../src/player-account.js";

class FakeAccountSql {
  constructor() {
    this.accountMeta = new Map();
    this.deletedAccounts = new Map();
    this.deletionClaims = new Map();
    this.wallet = new Map();
    this.sessions = new Map();
    this.ownedCosmetics = new Map();
    this.selectedCosmetics = new Map();
    this.adProgress = new Map();
    this.walletTransactions = new Map();
    this.revokedPurchases = new Map();
  }

  exec(statement, ...params) {
    const sql = String(statement).replace(/\s+/g, " ").trim();
    if (sql.startsWith("INSERT OR IGNORE INTO wallet_revision")) return { rowsWritten: 0 };
    if (sql.startsWith("SELECT revision FROM wallet_revision")) return [{ revision: 0 }];
    if (/^(CREATE TABLE|CREATE INDEX|CREATE TRIGGER)/i.test(sql)) return { rowsWritten: 0 };
    if (/^DROP TABLE IF EXISTS paws_plus_subscriptions$/i.test(sql)) return { rowsWritten: 0 };

    if (sql.startsWith("SELECT account_id FROM account_deletions WHERE account_id = ?")) {
      return this.deletedAccounts.has(params[0]) ? [{ account_id: params[0] }] : [];
    }
    if (sql.startsWith("SELECT account_id FROM account_deletion_claims WHERE account_id = ?")) {
      return this.deletionClaims.has(params[0]) ? [{ account_id: params[0] }] : [];
    }
    if (sql.startsWith("INSERT OR IGNORE INTO account_deletion_claims")) {
      if (!this.deletionClaims.has(params[0])) this.deletionClaims.set(params[0], { claimed_at: params[1] });
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("DELETE FROM account_deletion_claims WHERE account_id = ?")) {
      this.deletionClaims.delete(params[0]);
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("SELECT source_ref_hash FROM wallet_transactions WHERE kind IN")) {
      return [...this.walletTransactions.values()].filter(row => ["PLAY_GEM_PURCHASE", "PLAY_REMOVE_ADS"].includes(row.kind) && row.source_ref_hash).map(row => ({ source_ref_hash: row.source_ref_hash }));
    }
    if (/^DELETE FROM account_sessions$/.test(sql)) { this.sessions.clear(); return { rowsWritten: 1 }; }
    if (sql.startsWith("SELECT ledger_id FROM revoked_play_purchases")) return this.revokedPurchases.has(params[0]) ? [{ledger_id:params[0]}] : [];
    if (/^DELETE FROM purchase_ledger_ownership$/.test(sql)) return { rowsWritten: 0 };
    if (/^DELETE FROM revoked_play_purchases$/.test(sql)) { this.revokedPurchases.clear(); return { rowsWritten: 1 }; }
    if (/^DELETE FROM wallet_transactions$/.test(sql)) { this.walletTransactions.clear(); return { rowsWritten: 1 }; }
    if (/^DELETE FROM cosmetic_ad_progress$/.test(sql)) { this.adProgress.clear(); return { rowsWritten: 1 }; }
    if (/^DELETE FROM selected_cosmetics$/.test(sql)) { this.selectedCosmetics.clear(); return { rowsWritten: 1 }; }
    if (/^DELETE FROM owned_cosmetics$/.test(sql)) { this.ownedCosmetics.clear(); return { rowsWritten: 1 }; }
    if (/^DELETE FROM wallet$/.test(sql)) { this.wallet.clear(); return { rowsWritten: 1 }; }
    if (sql.startsWith("DELETE FROM account_meta WHERE account_id = ?")) { this.accountMeta.delete(params[0]); return { rowsWritten: 1 }; }
    if (sql.startsWith("INSERT INTO account_deletions")) { this.deletedAccounts.set(params[0], { deleted_at: params[1] }); return { rowsWritten: 1 }; }
    if (sql.startsWith("SELECT account_id FROM account_meta WHERE account_id = ?")) {
      return this.accountMeta.has(params[0]) ? [{ account_id: params[0] }] : [];
    }
    if (sql.startsWith("INSERT INTO account_meta")) {
      this.accountMeta.set(params[0], {
        account_id: params[0], display_name: params[1], created_at: params[2], updated_at: params[3],
      });
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("SELECT account_id, display_name, created_at, updated_at FROM account_meta")) {
      const row = this.accountMeta.get(params[0]);
      return row ? [structuredClone(row)] : [];
    }
    if (sql.startsWith("UPDATE account_meta SET display_name = ?")) {
      const row = this.accountMeta.get(params[2]);
      if (!row) return { rowsWritten: 0 };
      row.display_name = params[0];
      row.updated_at = params[1];
      return { rowsWritten: 1 };
    }

    if (sql.startsWith("INSERT OR IGNORE INTO wallet")) {
      const accountId = params[0];
      if (this.wallet.has(accountId)) return { rowsWritten: 0 };
      this.wallet.set(accountId, {
        account_id: accountId, balance: 0, ads_removed: 0, updated_at: Number(params[1]),
      });
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("SELECT balance, ads_removed, updated_at FROM wallet WHERE account_id = ?")) {
      const row = this.wallet.get(params[0]);
      return row ? [structuredClone(row)] : [];
    }
    if (sql.startsWith("SELECT balance FROM wallet WHERE account_id = ?")) {
      const row = this.wallet.get(params[0]);
      return row ? [{ balance: row.balance }] : [];
    }
    if (sql.startsWith("UPDATE wallet SET balance = ?, ads_removed = CASE")) {
      const row = this.wallet.get(params[3]);
      if (!row) return { rowsWritten: 0 };
      row.balance = params[0];
      if (params[1] === 1) row.ads_removed = 1;
      row.updated_at = params[2];
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("UPDATE wallet SET balance = ?, updated_at = ? WHERE account_id = ?")) {
      const row = this.wallet.get(params[2]);
      if (!row) return { rowsWritten: 0 };
      row.balance = params[0];
      row.updated_at = params[1];
      return { rowsWritten: 1 };
    }

    if (sql.startsWith("INSERT INTO account_sessions")) {
      this.sessions.set(params[0], {
        session_id: params[0],
        refresh_token_hash: params[1],
        previous_refresh_hash: null,
        last_refresh_request_id: null,
        created_at: params[2],
        expires_at: params[3],
        revoked_at: null,
        last_seen_at: params[4],
      });
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("SELECT refresh_token_hash, previous_refresh_hash, last_refresh_request_id, expires_at, revoked_at FROM account_sessions WHERE session_id = ?")) {
      const session = this.sessions.get(params[0]);
      return session ? [{
        refresh_token_hash: session.refresh_token_hash,
        previous_refresh_hash: session.previous_refresh_hash,
        last_refresh_request_id: session.last_refresh_request_id,
        expires_at: session.expires_at,
        revoked_at: session.revoked_at,
      }] : [];
    }
    if (sql.startsWith("UPDATE account_sessions SET refresh_token_hash = ?, previous_refresh_hash = ?, last_refresh_request_id = ?, expires_at = ?, last_seen_at = ? WHERE session_id = ? AND refresh_token_hash = ?")) {
      const session = this.sessions.get(params[5]);
      if (
        !session ||
        session.refresh_token_hash !== params[6] ||
        session.revoked_at != null ||
        session.expires_at <= params[7]
      ) return { rowsWritten: 0 };
      session.refresh_token_hash = params[0];
      session.previous_refresh_hash = params[1];
      session.last_refresh_request_id = params[2];
      session.expires_at = params[3];
      session.last_seen_at = params[4];
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("SELECT session_id FROM account_sessions WHERE session_id = ? AND revoked_at IS NULL")) {
      const session = this.sessions.get(params[0]);
      return session && session.revoked_at == null && session.expires_at > params[1] && this.accountMeta.has(params[2])
        ? [{ session_id: session.session_id }]
        : [];
    }

    if (sql.startsWith("INSERT OR IGNORE INTO owned_cosmetics")) {
      const cosmeticId = params[0];
      if (this.ownedCosmetics.has(cosmeticId)) return { rowsWritten: 0 };
      this.ownedCosmetics.set(cosmeticId, {
        cosmetic_id: cosmeticId,
        category: params[1],
        source: "DEFAULT",
        acquired_at: params[2],
        source_ref_hash: null,
      });
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("INSERT INTO owned_cosmetics")) {
      const source = sql.includes("'GEMS'") ? "GEMS" : "REWARDED_ADS";
      this.ownedCosmetics.set(params[0], {
        cosmetic_id: params[0], category: params[1], source, acquired_at: params[2], source_ref_hash: params[3] ?? null,
      });
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("SELECT cosmetic_id FROM owned_cosmetics WHERE cosmetic_id = ?")) {
      return this.ownedCosmetics.has(params[0]) ? [{ cosmetic_id: params[0] }] : [];
    }
    if (sql.startsWith("SELECT cosmetic_id FROM owned_cosmetics ORDER BY cosmetic_id")) {
      return [...this.ownedCosmetics.keys()].sort().map((cosmetic_id) => ({ cosmetic_id }));
    }

    if (sql.startsWith("INSERT OR IGNORE INTO selected_cosmetics")) {
      const category = params[0];
      if (this.selectedCosmetics.has(category)) return { rowsWritten: 0 };
      this.selectedCosmetics.set(category, { category, cosmetic_id: params[1], updated_at: params[2] });
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("INSERT INTO selected_cosmetics")) {
      const category = params[0];
      this.selectedCosmetics.set(category, { category, cosmetic_id: params[1], updated_at: params[2] });
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("SELECT category, cosmetic_id FROM selected_cosmetics ORDER BY category")) {
      return [...this.selectedCosmetics.values()].sort((a, b) => a.category.localeCompare(b.category)).map((row) => ({
        category: row.category, cosmetic_id: row.cosmetic_id,
      }));
    }

    if (sql.startsWith("SELECT payload_hash FROM wallet_transactions WHERE idempotency_key = ?")) {
      const row = this.walletTransactions.get(params[0]);
      return row ? [{ payload_hash: row.payload_hash }] : [];
    }
    if (sql.startsWith("SELECT payload_hash, balance_after FROM wallet_transactions WHERE idempotency_key = ?")) {
      const row = this.walletTransactions.get(params[0]);
      return row ? [{ payload_hash: row.payload_hash, balance_after: row.balance_after }] : [];
    }
    if (sql.startsWith("INSERT INTO wallet_transactions")) {
      const isCosmeticInsert = sql.includes("'COSMETIC_PURCHASE'");
      const idempotencyKey = params[1];
      const payloadHash = params[2];
      const kind = isCosmeticInsert ? "COSMETIC_PURCHASE" : params[3];
      const delta = isCosmeticInsert ? params[3] : params[4];
      const balanceAfter = isCosmeticInsert ? params[4] : params[5];
      this.walletTransactions.set(idempotencyKey, {
        payload_hash: payloadHash, kind, delta, balance_after: balanceAfter,
        source_ref_hash: isCosmeticInsert ? params[5] ?? null : params[6] ?? null,
      });
      return { rowsWritten: 1 };
    }

    if (sql.startsWith("SELECT cosmetic_id, progress FROM cosmetic_ad_progress ORDER BY cosmetic_id")) {
      return [...this.adProgress.entries()].map(([cosmetic_id, progress]) => ({ cosmetic_id, progress }));
    }
    if (sql.startsWith("INSERT INTO cosmetic_ad_progress")) {
      this.adProgress.set(params[0], Number(params[1]));
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("SELECT progress FROM cosmetic_ad_progress WHERE cosmetic_id = ?")) {
      return this.adProgress.has(params[0]) ? [{ progress: this.adProgress.get(params[0]) }] : [];
    }
    throw new Error("Unexpected PlayerAccount SQL in test: " + sql);
  }
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
  const sql = new FakeAccountSql();
  const ctx = {
    storage: { sql, transactionSync: (callback) => callback() },
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
  sql.wallet.get(accountId).balance = 500;

  const purchase = {
    accountId,
    sessionId: created.sessionId,
    cosmeticId: "dice_heart",
    clientRequestId: "4c4e7f90-68e5-4c30-8fb8-c5a7b0f0f8aa",
  };
  const firstResponse = await post(account, "/wallet/cosmetic/purchase", purchase);
  assert.equal(firstResponse.status, 200);
  const first = await firstResponse.json();
  assert.equal(first.wallet.balance, 250);
  assert.equal(first.wallet.ownedCosmetics.includes("dice_heart"), true);
  assert.equal(first.wallet.selectedCosmetics.DICE, "dice_heart");
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
  sql.wallet.get(accountId).balance = 500;

  const response = await post(account, "/wallet/cosmetic/purchase", {
    accountId,
    sessionId: created.sessionId,
    cosmeticId: "dice_colors",
    clientRequestId: "8a9b7c6d-5e4f-4321-9876-123456789abc",
  });

  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.wallet.balance, 300);
  assert.equal(body.wallet.ownedCosmetics.includes("dice_colors"), true);
  assert.equal(body.wallet.selectedCosmetics.DICE, "dice_colors");
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
    cosmeticId: "dice_diwali",
  };

  const firstResponse = await post(account, "/wallet/reward-ad", event);
  assert.equal(firstResponse.status, 200);
  const first = await firstResponse.json();
  assert.equal(first.wallet.balance, 5);
  assert.equal(sql.wallet.get(accountId).balance, 5);
  assert.equal(sql.adProgress.get("dice_diwali"), 1);
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
  assert.equal(sql.adProgress.get("dice_diwali"), 1);
  assert.equal(sql.walletTransactions.size, 1);
});


test("catalog-managed purchases use the current server price and grant the catalog category", async () => {
  const { account, sql } = makeAccount({
    LUDOPROOF_STORE_CATALOG: makeCatalogNamespace([
      catalogItem({ id: "dice_heart", category: "DICE", name: "Heart Festival" }),
    ]),
  });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Catalog Player" })).json();
  sql.wallet.get(accountId).balance = 200;

  const response = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "dice_heart",
    clientRequestId: "d47ddef4-9c33-44da-9187-5d774267a3c5",
    catalogCountry: "IN",
    // A client has no price field here; the DO takes its price from published catalog state.
  });
  assert.equal(response.status, 200);
  const result = await response.json();
  assert.equal(result.wallet.balance, 75);
  assert.ok(result.wallet.ownedCosmetics.includes("dice_heart"));
  assert.equal(result.wallet.selectedCosmetics.DICE, "dice_heart");
  assert.equal(sql.walletTransactions.size, 1);
});

test("archived catalog entry cannot bypass its status by falling back to the legacy cosmetic ID", async () => {
  const archived = catalogItem({
    id: "dice_heart",
    category: "DICE",
    name: "Archived Heart",
    status: "ARCHIVED",
    unlock: { channels: ["GEMS"], gems: { enabled: true, price: 1 } },
  });
  const { account, sql } = makeAccount({
    LUDOPROOF_STORE_CATALOG: makeCatalogNamespace([archived]),
  });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Catalog Player" })).json();
  sql.wallet.get(accountId).balance = 500;

  const response = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "dice_heart",
    clientRequestId: "e25b0212-af25-44ef-b9f4-c280c9d32e31",
  });
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "COSMETIC_UNAVAILABLE");
  assert.equal(sql.wallet.get(accountId).balance, 500);
  assert.equal(sql.walletTransactions.size, 0);
});

test("catalog-managed multi-channel purchase requires an explicit enabled channel and respects country rules", async () => {
  const multi = catalogItem({
    id: "dice_heart",
    category: "DICE",
    name: "Multi-channel Heart",
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
  sql.wallet.get(accountId).balance = 150;

  const wrongCountry = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "dice_heart",
    clientRequestId: "b6b07c67-7c13-4a54-83ab-37e79d097d6f", catalogCountry: "US",
  });
  assert.equal(wrongCountry.status, 400);
  assert.equal((await wrongCountry.json()).error, "COSMETIC_UNAVAILABLE");

  const ambiguous = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "dice_heart",
    clientRequestId: "8cc64312-e42b-4d4d-a6b2-3cfd16c3f098", catalogCountry: "IN",
  });
  assert.equal(ambiguous.status, 400);
  assert.equal((await ambiguous.json()).error, "COSMETIC_UNLOCK_CHANNEL_REQUIRED");
  assert.equal(sql.wallet.get(accountId).balance, 150);

  const selectedChannel = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "dice_heart", unlockChannel: "GEMS",
    clientRequestId: "c0e51f8e-2663-4d35-a0d1-248eaf508282", catalogCountry: "IN",
  });
  assert.equal(selectedChannel.status, 200);
  assert.equal((await selectedChannel.json()).wallet.balance, 60);
});

test("catalog-managed rewarded ads verify current eligibility and can progress to ownership", async () => {
  const rewardItem = catalogItem({
    id: "dice_diwali",
    category: "DICE",
    name: "Diwali Dice",
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
      accountId, transactionId, kind: "cosmetic", cosmeticId: "dice_diwali", catalogCountry: "IN",
    });
    assert.equal(reward.status, 200);
  }
  assert.equal(sql.wallet.get(accountId).balance, 10);
  assert.equal(sql.adProgress.get("dice_diwali"), 2);

  const purchase = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: (await created.json()).sessionId, cosmeticId: "dice_diwali",
    clientRequestId: "7ab6933e-2465-4b01-9cd2-aa33b2020605", catalogCountry: "IN",
  });
  assert.equal(purchase.status, 200);
  assert.ok((await purchase.json()).wallet.ownedCosmetics.includes("dice_diwali"));
});

test("new catalog IDs stay unpurchasable until server renderer and entitlement support ship", async () => {
  const { account, sql } = makeAccount({
    LUDOPROOF_STORE_CATALOG: makeCatalogNamespace([
      catalogItem({ id: "paw_safari_01", category: "PAW", name: "Safari Paw" }),
    ]),
  });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Locked Catalog Player" })).json();
  sql.wallet.get(accountId).balance = 500;

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
  const paw = catalogItem({ id: "dice_heart", category: "DICE", name: "Heart Festival" });
  const catalog = makeCatalogNamespace([paw]);
  const { account, sql } = makeAccount({ LUDOPROOF_STORE_CATALOG: catalog });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Catalog Player" })).json();
  sql.wallet.get(accountId).balance = 200;

  const bought = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "dice_heart",
    clientRequestId: "afc2b41d-9f61-47b1-9732-0b4890ff3d42", catalogCountry: "IN",
  });
  assert.equal(bought.status, 200);
  assert.ok((await bought.json()).wallet.ownedCosmetics.includes("dice_heart"));

  // The namespace is a test double over current state; archive after ownership exists.
  paw.status = "ARCHIVED";
  const select = await post(account, "/wallet/cosmetic/select", {
    accountId, sessionId: created.sessionId, cosmeticId: "dice_heart", catalogCountry: "IN",
  });
  assert.equal(select.status, 400);
  assert.equal((await select.json()).error, "COSMETIC_UNAVAILABLE");

  const wallet = await post(account, "/wallet/get", { accountId, sessionId: created.sessionId });
  assert.equal(wallet.status, 200);
  assert.ok((await wallet.json()).wallet.ownedCosmetics.includes("dice_heart"));
});

test("catalog service outage fails closed for purchase and does not use the legacy table", async () => {
  const { account, sql } = makeAccount({
    LUDOPROOF_STORE_CATALOG: makeCatalogNamespace([], { fail: true }),
  });
  const accountId = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
  const created = await (await post(account, "/session/create", { accountId, displayName: "Outage Player" })).json();
  sql.wallet.get(accountId).balance = 500;

  const response = await post(account, "/wallet/cosmetic/purchase", {
    accountId, sessionId: created.sessionId, cosmeticId: "dice_heart",
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

