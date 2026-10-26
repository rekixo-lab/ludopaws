import { isFamilyMode } from "./family-safety.js";
import { httpError, readJsonObject, requireClientRequestId } from "./crypto.js";
import {
  billingAccountId, deriveRotatedRefreshToken, hashRefreshToken, isAccountId, isSessionId,
  issueAccessToken, newRefreshToken, SESSION_TTL_MS, validRefreshToken, sha256Hex,
} from "./account-security.js";
import { GOOGLE_PLAY_PRODUCTS } from "./google-play-billing.js";

const MAX_BALANCE = 2_000_000_000;
const NAME_LENGTH = 24;
const DEFAULT_COSMETICS = [
  ["board_classic", "BOARD"], ["board_astral", "BOARD"], ["board_cloud_lagoon", "BOARD"], ["board_autumn_forest", "BOARD"], ["board_blossom_garden", "BOARD"], ["board_winter_haven", "BOARD"], ["dice_classic", "DICE"],
  ["avatar_9", "AVATAR"], ["avatar_4", "AVATAR"], ["avatar_3", "AVATAR"],
  ["avatar_5", "AVATAR"], ["avatar_6", "AVATAR"], ["avatar_7", "AVATAR"],
  ["avatar_1", "AVATAR"], ["avatar_8", "AVATAR"], ["character_pack_starter_paws", "CHARACTER_PACK"],
];
const DEFAULT_SELECTIONS = { BOARD: "board_classic", DICE: "dice_classic", AVATAR: "avatar_4", CHARACTER_PACK: "character_pack_starter_paws" };
const COSMETICS = Object.freeze({
  board_classic: { category: "BOARD", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  board_astral: { category: "BOARD", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  board_cloud_lagoon: { category: "BOARD", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  board_autumn_forest: { category: "BOARD", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  board_blossom_garden: { category: "BOARD", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  board_winter_haven: { category: "BOARD", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  board_checkers: { category: "BOARD", kind: "FREE", available: false, gemPrice: 0, level: 0, ads: 0 },
  board_chess: { category: "BOARD", kind: "REWARDED_ADS", available: false, gemPrice: 0, level: 0, ads: 5 },
  board_diwali: { category: "BOARD", kind: "REWARDED_ADS", available: false, gemPrice: 0, level: 0, ads: 10 },
  board_denim: { category: "BOARD", kind: "REWARDED_ADS", available: false, gemPrice: 0, level: 0, ads: 10 },
  board_neon: { category: "BOARD", kind: "REWARDED_ADS", available: false, gemPrice: 0, level: 10, ads: 20 },
  board_pirate: { category: "BOARD", kind: "GEMS", available: false, gemPrice: 200, level: 16, ads: 0 },
  board_alien: { category: "BOARD", kind: "GEMS", available: false, gemPrice: 250, level: 22, ads: 0 },
  board_penguin: { category: "BOARD", kind: "GEMS", available: false, gemPrice: 250, level: 0, ads: 0 },
  dice_classic: { category: "DICE", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  dice_pumpkin: { category: "DICE", kind: "FREE", available: false, gemPrice: 0, level: 0, ads: 0 },
  dice_diwali: { category: "DICE", kind: "REWARDED_ADS", available: false, gemPrice: 0, level: 0, ads: 5 },
  dice_football: { category: "DICE", kind: "REWARDED_ADS", available: false, gemPrice: 0, level: 0, ads: 10 },
  dice_cricket: { category: "DICE", kind: "REWARDED_ADS", available: false, gemPrice: 0, level: 0, ads: 20 },
  dice_summers: { category: "DICE", kind: "REWARDED_ADS", available: false, gemPrice: 0, level: 0, ads: 30 },
  dice_colors: { category: "DICE", kind: "GEMS", available: false, gemPrice: 200, level: 0, ads: 0 },
  dice_heart: { category: "DICE", kind: "GEMS", available: false, gemPrice: 250, level: 0, ads: 0 },
  avatar_9: { category: "AVATAR", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  avatar_4: { category: "AVATAR", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  avatar_3: { category: "AVATAR", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  avatar_5: { category: "AVATAR", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  avatar_6: { category: "AVATAR", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  avatar_7: { category: "AVATAR", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  avatar_1: { category: "AVATAR", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  avatar_8: { category: "AVATAR", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  avatar_2: { category: "AVATAR", kind: "GEMS", available: true, gemPrice: 500, level: 0, ads: 0 },
  avatar_10: { category: "AVATAR", kind: "GEMS", available: true, gemPrice: 500, level: 0, ads: 0 },
  character_goat_lumi: { category: "CHARACTER_PACK", kind: "REWARDED_ADS", available: true, gemPrice: 0, level: 0, ads: 10 },
  character_donkey: { category: "CHARACTER_PACK", kind: "REWARDED_ADS", available: true, gemPrice: 0, level: 0, ads: 15 },
  character_pack_starter_paws: { category: "CHARACTER_PACK", kind: "FREE", available: true, gemPrice: 0, level: 0, ads: 0 },
  character_pack_safari_paws: { category: "CHARACTER_PACK", kind: "LEVEL", available: false, gemPrice: 0, level: 8, ads: 0 },
  character_pack_farm_paws: { category: "CHARACTER_PACK", kind: "GEMS", available: false, gemPrice: 300, level: 0, ads: 0 },
  character_pack_cozy_paws: { category: "CHARACTER_PACK", kind: "REWARDED_ADS", available: false, gemPrice: 0, level: 0, ads: 10 },
  character_pack_wild_paws: { category: "CHARACTER_PACK", kind: "EVENT", available: false, gemPrice: 0, level: 0, ads: 0 },
  character_pack_pet_paws: { category: "CHARACTER_PACK", kind: "GEMS", available: false, gemPrice: 450, level: 0, ads: 0 },
  character_pack_jungle_paws: { category: "CHARACTER_PACK", kind: "GEMS", available: false, gemPrice: 550, level: 0, ads: 0 },
});

export class PlayerAccount {
  constructor(ctx, env) {
    this.ctx = ctx; this.env = env;
    ctx.blockConcurrencyWhile(async () => {
      const sql = ctx.storage.sql;
      sql.exec("CREATE TABLE IF NOT EXISTS account_meta (account_id TEXT PRIMARY KEY, display_name TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS account_deletions (account_id TEXT PRIMARY KEY, deleted_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS account_deletion_claims (account_id TEXT PRIMARY KEY, claimed_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS account_sessions (session_id TEXT PRIMARY KEY, refresh_token_hash TEXT NOT NULL, previous_refresh_hash TEXT, last_refresh_request_id TEXT, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, revoked_at INTEGER, last_seen_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS wallet (account_id TEXT PRIMARY KEY, balance INTEGER NOT NULL DEFAULT 0 CHECK(balance >= 0), ads_removed INTEGER NOT NULL DEFAULT 0 CHECK(ads_removed IN (0, 1)), updated_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS purchase_ledger_ownership (ledger_id TEXT PRIMARY KEY)");
      sql.exec("CREATE TABLE IF NOT EXISTS revoked_play_purchases (ledger_id TEXT PRIMARY KEY, revoked_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS wallet_transactions (transaction_id TEXT PRIMARY KEY, idempotency_key TEXT NOT NULL UNIQUE, payload_hash TEXT NOT NULL, kind TEXT NOT NULL, delta INTEGER NOT NULL, balance_after INTEGER NOT NULL, source_ref_hash TEXT, created_at INTEGER NOT NULL)");
      sql.exec("DROP TABLE IF EXISTS paws_plus_subscriptions");
      sql.exec("CREATE TABLE IF NOT EXISTS owned_cosmetics (cosmetic_id TEXT PRIMARY KEY, category TEXT NOT NULL, source TEXT NOT NULL, acquired_at INTEGER NOT NULL, source_ref_hash TEXT)");
      sql.exec("CREATE TABLE IF NOT EXISTS selected_cosmetics (category TEXT PRIMARY KEY, cosmetic_id TEXT NOT NULL, updated_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS cosmetic_ad_progress (cosmetic_id TEXT PRIMARY KEY, progress INTEGER NOT NULL DEFAULT 0 CHECK(progress >= 0), updated_at INTEGER NOT NULL)");
      sql.exec("CREATE INDEX IF NOT EXISTS idx_wallet_tx_created ON wallet_transactions(created_at DESC)");
      sql.exec("CREATE TABLE IF NOT EXISTS wallet_revision (id INTEGER PRIMARY KEY CHECK(id = 1), revision INTEGER NOT NULL)");
      sql.exec("INSERT OR IGNORE INTO wallet_revision (id, revision) VALUES (1, 0)");
      for (const [table, operations] of [
        ["wallet", ["INSERT", "UPDATE"]], ["owned_cosmetics", ["INSERT", "DELETE"]],
        ["selected_cosmetics", ["INSERT", "UPDATE", "DELETE"]],
        ["cosmetic_ad_progress", ["INSERT", "UPDATE", "DELETE"]],
      ]) {
        for (const operation of operations) {
          sql.exec(`CREATE TRIGGER IF NOT EXISTS revision_${table}_${operation}
            AFTER ${operation} ON ${table} BEGIN
            UPDATE wallet_revision SET revision = revision + 1 WHERE id = 1; END`);
        }
      }
    });
  }

  async fetch(request) {
    try {
      const url = new URL(request.url);
      if (request.method === "POST" && url.pathname === "/account/delete-preview") return await this.#deletePreview(request);
      if (request.method === "POST" && url.pathname === "/account/delete") return await this.#deleteAccount(request);
      if (request.method === "POST" && url.pathname === "/session/create") return await this.#createSession(request);
      if (request.method === "POST" && url.pathname === "/session/refresh") return await this.#refreshSession(request);
      if (request.method === "POST" && url.pathname === "/session/check") return await this.#checkSession(request);
      if (request.method === "POST" && url.pathname === "/session/logout") return await this.#logout(request);
      if (request.method === "POST" && url.pathname === "/profile/get") return await this.#getProfile(request);
      if (request.method === "POST" && url.pathname === "/profile/update") return await this.#updateProfile(request);
      if (request.method === "POST" && url.pathname === "/wallet/get") return await this.#getWallet(request);
      if (request.method === "POST" && url.pathname === "/wallet/play-revoke") return await this.#revokePlayPurchase(request);
      if (request.method === "POST" && url.pathname === "/wallet/play-purchase") return await this.#grantPlayPurchase(request);
      if (request.method === "POST" && url.pathname === "/wallet/reward-ad") return await this.#grantAdReward(request);
      if (request.method === "POST" && url.pathname === "/wallet/cosmetic/purchase") return await this.#purchaseCosmetic(request);
      if (request.method === "POST" && url.pathname === "/wallet/cosmetic/select") return await this.#selectCosmetic(request);
      return json(404, { error: "NOT_FOUND", message: "route not found" });
    } catch (error) { return errorResponse(error); }
  }

  async #deletePreview(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId);
    if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletions WHERE account_id = ? LIMIT 1", accountId)].length) {
      return json(200, { ok: true, deleted: true, deletionPending: false, purchaseLedgerIds: [] });
    }

    const guestRecoveryVerified = body?.guestRecoveryVerified === true;
    const accountDeletionReplayVerified = body?.accountDeletionReplayVerified === true;
    const sessionId = guestRecoveryVerified || accountDeletionReplayVerified ? null : requireSessionId(body?.sessionId);
    let deleted = false;
    let purchaseLedgerIds = [];

    // Claim the account and snapshot all known purchase ledgers in one synchronous transaction.
    // Writes that arrive after this point reject the deletion claim before changing wallet data.
    this.ctx.storage.transactionSync(() => {
      if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletions WHERE account_id = ? LIMIT 1", accountId)].length) {
        deleted = true;
        return;
      }
      const existingClaim = [...this.ctx.storage.sql.exec(
        "SELECT account_id FROM account_deletion_claims WHERE account_id = ? LIMIT 1", accountId,
      )];
      if (accountDeletionReplayVerified && !existingClaim.length) {
        throw httpError(409, "ACCOUNT_DELETION_NOT_IN_PROGRESS", "an expired session can only resume an existing deletion");
      }
      if (!guestRecoveryVerified && !accountDeletionReplayVerified) {
        this.#requireActiveSession(accountId, sessionId, true);
      }
      this.#profileSnapshot(accountId);
      this.ctx.storage.sql.exec(
        "INSERT OR IGNORE INTO account_deletion_claims (account_id, claimed_at) VALUES (?, ?)",
        accountId, Date.now(),
      );
      purchaseLedgerIds = [
        ...[...this.ctx.storage.sql.exec(
          "SELECT source_ref_hash FROM wallet_transactions WHERE kind IN ('PLAY_GEM_PURCHASE', 'PLAY_REMOVE_ADS') AND source_ref_hash IS NOT NULL",
        )].map((row) => String(row.source_ref_hash)),
      ].filter((value) => /^[a-f0-9]{64}$/.test(value));
    });

    if (deleted) return json(200, { ok: true, deleted: true, deletionPending: false, purchaseLedgerIds: [] });
    return json(200, { ok: true, deleted: false, deletionPending: true, purchaseLedgerIds: [...new Set(purchaseLedgerIds)] });
  }

  async #deleteAccount(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId);
    if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletions WHERE account_id = ? LIMIT 1", accountId)].length) {
      return json(200, { ok: true, accountId, deleted: true, replay: true });
    }
    const guestRecoveryVerified = body?.guestRecoveryVerified === true;
    const accountDeletionReplayVerified = body?.accountDeletionReplayVerified === true;
    const sessionId = guestRecoveryVerified || accountDeletionReplayVerified ? null : requireSessionId(body?.sessionId);
    if (body?.confirmation !== "DELETE_MY_ACCOUNT") throw httpError(400, "ACCOUNT_DELETE_CONFIRMATION_REQUIRED", "account deletion confirmation is required");

    const deletedAt = Date.now();
    let replay = false;
    this.ctx.storage.transactionSync(() => {
      if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletions WHERE account_id = ? LIMIT 1", accountId)].length) {
        replay = true;
        return;
      }
      const claim = [...this.ctx.storage.sql.exec(
        "SELECT account_id FROM account_deletion_claims WHERE account_id = ? LIMIT 1", accountId,
      )];
      if (guestRecoveryVerified || accountDeletionReplayVerified) {
        if (!claim.length) throw httpError(409, "ACCOUNT_DELETION_NOT_IN_PROGRESS", "verified deletion replay requires an existing deletion claim");
      } else {
        this.#requireActiveSession(accountId, sessionId, true);
      }
      this.ctx.storage.sql.exec(
        "INSERT OR IGNORE INTO account_deletion_claims (account_id, claimed_at) VALUES (?, ?)",
        accountId, deletedAt,
      );
      this.#profileSnapshot(accountId);
      this.ctx.storage.sql.exec("DELETE FROM account_sessions");
      this.ctx.storage.sql.exec("DELETE FROM purchase_ledger_ownership");
      this.ctx.storage.sql.exec("DELETE FROM revoked_play_purchases");
      this.ctx.storage.sql.exec("DELETE FROM wallet_transactions");
      this.ctx.storage.sql.exec("DELETE FROM cosmetic_ad_progress");
      this.ctx.storage.sql.exec("DELETE FROM selected_cosmetics");
      this.ctx.storage.sql.exec("DELETE FROM owned_cosmetics");
      this.ctx.storage.sql.exec("DELETE FROM wallet");
      this.ctx.storage.sql.exec("DELETE FROM account_meta WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("INSERT INTO account_deletions (account_id, deleted_at) VALUES (?, ?)", accountId, deletedAt);
      this.ctx.storage.sql.exec("DELETE FROM account_deletion_claims WHERE account_id = ?", accountId);
    });
    return json(200, { ok: true, accountId, deleted: true, replay });
  }

  async #createSession(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId);
    this.#ensureAccount(accountId, body?.displayName);
    const sessionId = crypto.randomUUID().toLowerCase();
    const refreshToken = await newRefreshToken(), refreshHash = await hashRefreshToken(refreshToken);
    const now = Date.now(), expiresAt = now + SESSION_TTL_MS;
    const accessToken = await issueAccessToken(this.env, accountId, sessionId, now);
    const billingId = await billingAccountId(accountId);
    this.ctx.storage.transactionSync(() => {
      if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletions WHERE account_id = ? LIMIT 1", accountId)].length) {
        throw httpError(410, "ACCOUNT_DELETED", "this account has been deleted");
      }
      this.#requireAccountNotDeleting(accountId);
      this.ctx.storage.sql.exec("INSERT INTO account_sessions (session_id, refresh_token_hash, previous_refresh_hash, last_refresh_request_id, created_at, expires_at, revoked_at, last_seen_at) VALUES (?, ?, NULL, NULL, ?, ?, NULL, ?)", sessionId, refreshHash, now, expiresAt, now);
    });
    await this.#enrollLegacyPurchases(accountId);
    return json(200, { ok: true, accountId, sessionId, billingAccountId: billingId, accessToken, refreshToken, accessExpiresAt: Math.floor(now / 1000) + 900, sessionExpiresAt: expiresAt, profile: this.#profileSnapshot(accountId), wallet: this.#walletSnapshot(accountId) });
  }

  async #refreshSession(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId), sessionId = requireSessionId(body?.sessionId);
    if (!validRefreshToken(body?.refreshToken)) throw httpError(401, "REFRESH_TOKEN_INVALID", "account refresh token is invalid");
    const requestId = requireClientRequestId(body?.clientRequestId), oldToken = body.refreshToken;
    const oldHash = await hashRefreshToken(oldToken);
    const nextToken = await deriveRotatedRefreshToken(this.env, sessionId, requestId, oldToken), nextHash = await hashRefreshToken(nextToken);
    const now = Date.now(), nextExpiry = now + SESSION_TTL_MS;
    let row = null, accepted = false, replay = false;

    // Re-read the session inside the synchronous transaction. A concurrent retry may have
    // rotated the token after an earlier read; in that case accept only the exact same
    // request ID + old token + deterministically derived next token.
    this.ctx.storage.transactionSync(() => {
      this.#requireAccountNotDeleting(accountId);
      const currentRows = [...this.ctx.storage.sql.exec(
        "SELECT refresh_token_hash, previous_refresh_hash, last_refresh_request_id, expires_at, revoked_at FROM account_sessions WHERE session_id = ? LIMIT 1",
        sessionId,
      )];
      if (
        currentRows.length !== 1 ||
        currentRows[0].revoked_at != null ||
        Number(currentRows[0].expires_at) <= now
      ) return;

      row = currentRows[0];
      if (String(row.refresh_token_hash) === oldHash) {
        const update = this.ctx.storage.sql.exec(
          "UPDATE account_sessions SET refresh_token_hash = ?, previous_refresh_hash = ?, last_refresh_request_id = ?, expires_at = ?, last_seen_at = ? WHERE session_id = ? AND refresh_token_hash = ? AND revoked_at IS NULL AND expires_at > ?",
          nextHash, oldHash, requestId, nextExpiry, now, sessionId, oldHash, now,
        );
        accepted = Number(update.rowsWritten ?? 0) === 1;
      } else if (
        String(row.previous_refresh_hash ?? "") === oldHash &&
        String(row.last_refresh_request_id ?? "") === requestId &&
        String(row.refresh_token_hash) === nextHash
      ) {
        accepted = true;
        replay = true;
      }
    });

    if (!accepted || !row) {
      throw httpError(401, "REFRESH_TOKEN_INVALID", "account refresh token is invalid or has already been rotated");
    }
    const accessToken = await issueAccessToken(this.env, accountId, sessionId, now);
    return json(200, {
      ok: true,
      accountId,
      sessionId,
      billingAccountId: await billingAccountId(accountId),
      accessToken,
      refreshToken: nextToken,
      accessExpiresAt: Math.floor(now / 1000) + 900,
      sessionExpiresAt: replay ? Number(row.expires_at) : nextExpiry,
      profile: this.#profileSnapshot(accountId),
      wallet: this.#walletSnapshot(accountId),
      replay,
    });
  }

  async #checkSession(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId), sessionId = requireSessionId(body?.sessionId);
    this.#requireActiveSession(accountId, sessionId, body?.allowDeletionPending === true);
    return json(200, { ok: true, accountId, profile: this.#profileSnapshot(accountId) });
  }
  async #logout(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId), sessionId = requireSessionId(body?.sessionId);
    const rows = [...this.ctx.storage.sql.exec("SELECT session_id FROM account_sessions WHERE session_id = ? AND EXISTS (SELECT 1 FROM account_meta WHERE account_id = ?) LIMIT 1", sessionId, accountId)];
    if (!rows.length) throw httpError(401, "ACCOUNT_SESSION_INVALID", "account session was not found");
    this.ctx.storage.sql.exec("UPDATE account_sessions SET revoked_at = COALESCE(revoked_at, ?) WHERE session_id = ?", Date.now(), sessionId);
    return json(200, { ok: true });
  }
  async #getProfile(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId), sessionId = requireSessionId(body?.sessionId);
    this.#requireActiveSession(accountId, sessionId);
    await this.#enrollLegacyPurchases(accountId);
    return json(200, { ok: true, profile: this.#profileSnapshot(accountId), wallet: this.#walletSnapshot(accountId) });
  }
  async #updateProfile(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId), sessionId = requireSessionId(body?.sessionId);
    this.#requireActiveSession(accountId, sessionId);
    this.ctx.storage.sql.exec("UPDATE account_meta SET display_name = ?, updated_at = ? WHERE account_id = ?", normalizeDisplayName(body?.displayName, this.env), Date.now(), accountId);
    return json(200, { ok: true, profile: this.#profileSnapshot(accountId) });
  }
  async #getWallet(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId), sessionId = requireSessionId(body?.sessionId);
    this.#requireActiveSession(accountId, sessionId);
    await this.#enrollLegacyPurchases(accountId);
    return json(200, { ok: true, wallet: this.#walletSnapshot(accountId) });
  }

  async #enrollLegacyPurchases(accountId) {
    if (!this.env?.LUDOPROOF_STORE_PURCHASES) return;
    const pendingSql = "SELECT t.source_ref_hash, t.idempotency_key FROM wallet_transactions t LEFT JOIN purchase_ledger_ownership o ON o.ledger_id = t.source_ref_hash WHERE t.kind IN ('PLAY_GEM_PURCHASE', 'PLAY_REMOVE_ADS') AND o.ledger_id IS NULL";
    const pending = [...this.ctx.storage.sql.exec(pendingSql + " LIMIT 10")];
    if (!pending.length) return;
    const billingId = await billingAccountId(accountId);
    for (const row of pending) {
      const ledgerId = requireHash(row.source_ref_hash, "purchaseLedgerId");
      const target = this.env.LUDOPROOF_STORE_PURCHASES.get(this.env.LUDOPROOF_STORE_PURCHASES.idFromName(ledgerId));
      const bound = await target.fetch(new Request("https://purchase-ledger/bind-owner", { method: "POST", headers: {"content-type":"application/json"},
        body: JSON.stringify({ accountId: billingId, ownerAccountId: accountId,
          purchaseTokenHash: String(row.idempotency_key).slice("play-purchase:".length) }) }));
      if (!bound.ok) throw httpError(503, "PURCHASE_MIGRATION_PENDING", "purchase history could not be synchronized; retry shortly");
      const status = await target.fetch(new Request("https://purchase-ledger/status"));
      if (!status.ok) throw httpError(503, "PURCHASE_MIGRATION_PENDING", "purchase history could not be synchronized; retry shortly");
      if ((await status.json()).purchase?.state === "REVOKED") {
        const revoked = await this.#revokePlayPurchase(new Request("https://player-account/wallet/play-revoke", { method:"POST", headers:{"content-type":"application/json"}, body:JSON.stringify({accountId,purchaseLedgerId:ledgerId}) }));
        if (!revoked.ok) throw httpError(503, "PURCHASE_MIGRATION_PENDING", "purchase history could not be synchronized; retry shortly");
      }
      this.#requireAccountNotDeleting(accountId);
      this.ctx.storage.sql.exec("INSERT OR IGNORE INTO purchase_ledger_ownership (ledger_id) VALUES (?)", ledgerId);
    }
    // Bounded DO subrequests; another retry continues migration instead of exposing stale paid value.
    if ([...this.ctx.storage.sql.exec(pendingSql + " LIMIT 1")].length)
      throw httpError(503, "PURCHASE_MIGRATION_PENDING", "purchase history is being synchronized; retry shortly");
  }

  async #grantPlayPurchase(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId), sessionId = requireSessionId(body?.sessionId);
    this.#requireActiveSession(accountId, sessionId);
    const productId = String(body?.productId ?? ""), tokenHash = requireHash(body?.purchaseTokenHash, "purchaseTokenHash");
    const purchaseLedgerId = requireHash(body?.purchaseLedgerId, "purchaseLedgerId");
    const product = GOOGLE_PLAY_PRODUCTS[productId];
    if (!product) throw httpError(400, "UNKNOWN_STORE_PRODUCT", "store product is not recognized");
    const payloadHash = await sha256Hex(JSON.stringify({ productId, gems: product.gemAmount, removeAds: product.removeAds }));
    const result = this.#applyWalletTransaction({ accountId, idempotencyKey: "play-purchase:" + tokenHash, payloadHash, kind: product.removeAds ? "PLAY_REMOVE_ADS" : "PLAY_GEM_PURCHASE", delta: product.gemAmount, sourceRefHash: purchaseLedgerId, removeAds: product.removeAds });
    return json(200, { ok: true, ...result, productId, gemAmount: product.gemAmount, removeAds: product.removeAds, wallet: this.#walletSnapshot(accountId) });
  }

  // Internal binding only: no HTTP account route exposes this operation.
  async #revokePlayPurchase(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId);
    const ledgerId = requireHash(body?.purchaseLedgerId, "purchaseLedgerId");
    let replay = false;
    this.ctx.storage.transactionSync(() => {
      this.#requireAccountNotDeleting(accountId);
      this.#profileSnapshot(accountId);
      if ([...this.ctx.storage.sql.exec("SELECT ledger_id FROM revoked_play_purchases WHERE ledger_id = ?", ledgerId)].length) { replay = true; return; }
      const now = Date.now();
      const granted = [...this.ctx.storage.sql.exec("SELECT delta, kind FROM wallet_transactions WHERE source_ref_hash = ? AND kind IN ('PLAY_GEM_PURCHASE', 'PLAY_REMOVE_ADS')", ledgerId)];
      const balance = Number([...this.ctx.storage.sql.exec("SELECT balance FROM wallet WHERE account_id = ?", accountId)][0]?.balance ?? 0);
      const deduction = Math.min(balance, granted.reduce((sum, row) => sum + Math.max(0, Number(row.delta)), 0));
      this.ctx.storage.sql.exec("INSERT INTO revoked_play_purchases (ledger_id, revoked_at) VALUES (?, ?)", ledgerId, now);
      const activeRemoveAds = [...this.ctx.storage.sql.exec("SELECT t.transaction_id FROM wallet_transactions t LEFT JOIN revoked_play_purchases r ON r.ledger_id = t.source_ref_hash WHERE t.kind = 'PLAY_REMOVE_ADS' AND r.ledger_id IS NULL LIMIT 1")].length > 0;
      this.ctx.storage.sql.exec("UPDATE wallet SET balance = ?, ads_removed = ?, updated_at = ? WHERE account_id = ?", balance - deduction, activeRemoveAds ? 1 : 0, now, accountId);
      this.ctx.storage.sql.exec("INSERT INTO wallet_transactions (transaction_id, idempotency_key, payload_hash, kind, delta, balance_after, source_ref_hash, created_at) VALUES (?, ?, ?, 'PLAY_REFUND', ?, ?, ?, ?)", crypto.randomUUID().toLowerCase(), "play-refund:" + ledgerId, ledgerId, -deduction, balance - deduction, ledgerId, now);
    });
    return json(200, { ok: true, replay });
  }

  async #resolveCosmetic(cosmeticId, country = "ZZ") {
    const namespace = this.env?.LUDOPROOF_STORE_CATALOG;
    if (namespace) {
      let state;
      try {
        const target = namespace.get(namespace.idFromName("GLOBAL:V1"));
        const response = await target.fetch(new Request("https://store-catalog/catalog/current", {
          method: "GET",
        }));
        if (!response.ok) throw new Error("catalog read failed");
        state = await response.json();
      } catch {
        throw httpError(503, "STORE_CATALOG_UNAVAILABLE", "store catalog could not be verified");
      }
      if (!Array.isArray(state?.items) || !Number.isSafeInteger(state?.catalogVersion)) {
        throw httpError(503, "STORE_CATALOG_INVALID", "store catalog returned an invalid response");
      }
      const item = state.items.find((entry) => entry?.id === cosmeticId);
      if (item) {
        const channels = Array.isArray(item.unlock?.channels) ? [...item.unlock.channels] : [];
        const availability = item.availability;
        const now = Date.now();
        let available = item.status === "PUBLISHED" && item.contentReady === true &&
          availability !== null && typeof availability === "object";
        if (available && availability.startsAt != null) {
          const startsAt = Date.parse(availability.startsAt);
          available = Number.isFinite(startsAt) && startsAt <= now;
        }
        if (available && availability.endsAt != null) {
          const endsAt = Date.parse(availability.endsAt);
          available = Number.isFinite(endsAt) && endsAt > now;
        }
        const resolvedCountry = /^[A-Z]{2}$/.test(String(country)) ? String(country) : "ZZ";
        if (available && Array.isArray(availability.blockedCountries) &&
            availability.blockedCountries.includes(resolvedCountry)) {
          available = false;
        }
        if (available && availability.worldwide !== true) {
          available = Array.isArray(availability.allowedCountries) &&
            availability.allowedCountries.includes(resolvedCountry);
        }
        // A catalog entry can be published before every installed client has its
        // renderer/asset support. Only IDs already registered as available server
        // cosmetics may spend value or receive rewards; new IDs stay discoverable in
        // the catalog but locked until the app/runtime and entitlement map are shipped.
        const renderer = COSMETICS[item.id];
        const ownershipCategory = item.category === "PAW" ? "CHARACTER_PACK" : item.category;
        const rendererReady = Boolean(
          renderer &&
          renderer.available === true &&
          renderer.category === ownershipCategory,
        );
        return {
          id: item.id,
          category: renderer?.category ?? ownershipCategory,
          catalogManaged: true,
          catalogVersion: item.catalogVersion,
          available: available && rendererReady,
          channels,
          kind: channels.length === 1 ? channels[0] : null,
          gemPrice: Number.isSafeInteger(item.unlock?.gems?.price) ? item.unlock.gems.price : null,
          ads: Number.isSafeInteger(item.unlock?.rewardedAds?.requiredCompletedAds)
            ? item.unlock.rewardedAds.requiredCompletedAds : 0,
          level: Number.isSafeInteger(item.unlock?.level?.minimumLevel) ? item.unlock.level.minimumLevel : 0,
          rendererReady,
        };
      }
    }

    // The catalog is introduced alongside the legacy PlayerAccount catalog.
    // Preserve already-shipped IDs when no matching dynamic entry exists, but
    // never fall back to a legacy ID after a matching archived dynamic record.
    const legacy = COSMETICS[cosmeticId];
    if (!legacy) return null;
    const channel = ({
      FREE: "FREE",
      GEMS: "GEMS",
      REWARDED_ADS: "REWARDED_ADS",
      LEVEL: "LEVEL",
      EVENT: "EVENT",
    })[legacy.kind];
    return {
      ...legacy,
      id: cosmeticId,
      catalogManaged: false,
      channels: channel ? [channel] : [],
    };
  }

  #requireSupportedCosmeticCategory(cosmetic) {
    if (cosmetic.catalogManaged && !["PAW", "DICE", "BOARD", "AVATAR", "CHARACTER_PACK"].includes(cosmetic.category)) {
      throw httpError(409, "CATALOG_CATEGORY_NOT_READY", "this catalog category does not have an ownership/equip implementation yet");
    }
  }

  async #grantAdReward(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId);
    this.#profileSnapshot(accountId);
    const transactionId = String(body?.transactionId ?? ""), kind = String(body?.kind ?? "");
    const cosmeticId = body?.cosmeticId == null ? null : String(body.cosmeticId);
    if (!/^[A-Fa-f0-9]{16,128}$/.test(transactionId)) throw httpError(400, "AD_REWARD_INVALID", "reward transaction is invalid");
    const transactionHash = await sha256Hex("ludoproof:admob-transaction:v1:" + transactionId);
    const payloadHash = await sha256Hex(JSON.stringify({ kind, cosmeticId, transactionId }));
    const idempotencyKey = "admob:" + transactionHash;
    if (kind === "gems") {
      const result = this.#applyWalletTransaction({ accountId, idempotencyKey, payloadHash, kind: "REWARDED_AD_GEMS", delta: 5, sourceRefHash: transactionHash, removeAds: false });
      return json(200, { ok: true, ...result, wallet: this.#walletSnapshot(accountId) });
    }
    if (kind === "cosmetic") {
      const cosmetic = await this.#resolveCosmetic(cosmeticId, body?.catalogCountry);
      if (!cosmetic || !cosmetic.available ||
          !cosmetic.channels.includes("REWARDED_ADS") || cosmetic.ads < 1) {
        throw httpError(400, "COSMETIC_REWARD_INVALID", "cosmetic does not currently support rewarded ads");
      }
      this.#requireSupportedCosmeticCategory(cosmetic);
      if (cosmetic.level > 0) throw httpError(409, "PROFILE_PROGRESSION_NOT_SERVER_AUTHORITATIVE", "this cosmetic requires server-verified progression before it can be unlocked");
      let replay = false;
      this.ctx.storage.transactionSync(() => {
        this.#requireAccountNotDeleting(accountId);
        const prior = [...this.ctx.storage.sql.exec("SELECT payload_hash FROM wallet_transactions WHERE idempotency_key = ? LIMIT 1", idempotencyKey)];
        if (prior.length) {
          if (String(prior[0].payload_hash) !== payloadHash) throw httpError(409, "AD_REWARD_IDEMPOTENCY_CONFLICT", "reward transaction was reused for a different reward");
          replay = true; return;
        }
        const walletRows = [...this.ctx.storage.sql.exec("SELECT balance FROM wallet WHERE account_id = ? LIMIT 1", accountId)];
        if (!walletRows.length) throw httpError(404, "WALLET_NOT_FOUND", "wallet was not found");
        const balance = Number(walletRows[0].balance);
        // The configured AdMob unit promises 5 gems. Cosmetic-targeted views also earn
        // their requested progress, but must not suppress the ad unit's declared reward.
        const nextBalance = balance + 5;
        if (!Number.isSafeInteger(nextBalance) || nextBalance < 0 || nextBalance > MAX_BALANCE) {
          throw httpError(409, "WALLET_BALANCE_LIMIT", "wallet balance cannot safely be updated");
        }
        const current = Number([...this.ctx.storage.sql.exec("SELECT progress FROM cosmetic_ad_progress WHERE cosmetic_id = ? LIMIT 1", cosmeticId)][0]?.progress ?? 0);
        const next = Math.min(cosmetic.ads, current + 1), now = Date.now();
        this.ctx.storage.sql.exec("UPDATE wallet SET balance = ?, updated_at = ? WHERE account_id = ?", nextBalance, now, accountId);
        this.ctx.storage.sql.exec("INSERT INTO cosmetic_ad_progress (cosmetic_id, progress, updated_at) VALUES (?, ?, ?) ON CONFLICT(cosmetic_id) DO UPDATE SET progress = excluded.progress, updated_at = excluded.updated_at", cosmeticId, next, now);
        this.ctx.storage.sql.exec("INSERT INTO wallet_transactions (transaction_id, idempotency_key, payload_hash, kind, delta, balance_after, source_ref_hash, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", crypto.randomUUID().toLowerCase(), idempotencyKey, payloadHash, "REWARDED_AD_COSMETIC_PROGRESS_AND_GEMS", 5, nextBalance, transactionHash, now);
        if (next >= cosmetic.ads && ["character_goat_lumi", "character_donkey"].includes(cosmeticId)) {
          this.ctx.storage.sql.exec("INSERT OR IGNORE INTO owned_cosmetics (cosmetic_id, category, source, acquired_at, source_ref_hash) VALUES (?, ?, 'REWARDED_ADS', ?, NULL)", cosmeticId, cosmetic.category, now);
        }
      });
      return json(200, { ok: true, replay, wallet: this.#walletSnapshot(accountId) });
    }
    throw httpError(400, "AD_REWARD_INVALID", "reward type is not supported");
  }

  async #purchaseCosmetic(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId), sessionId = requireSessionId(body?.sessionId);
    this.#requireActiveSession(accountId, sessionId);
    const cosmeticId = String(body?.cosmeticId ?? ""), requestId = requireClientRequestId(body?.clientRequestId);
    const cosmetic = await this.#resolveCosmetic(cosmeticId, body?.catalogCountry);
    if (!cosmetic || !cosmetic.available) throw httpError(400, "COSMETIC_UNAVAILABLE", "this cosmetic is not currently available for purchase");
    this.#requireSupportedCosmeticCategory(cosmetic);
    let purchaseKind = cosmetic.kind;
    if (cosmetic.catalogManaged) {
      const requestedChannel = typeof body?.unlockChannel === "string"
        ? body.unlockChannel
        : cosmetic.channels.length === 1 ? cosmetic.channels[0] : null;
      if (!requestedChannel) {
        throw httpError(400, "COSMETIC_UNLOCK_CHANNEL_REQUIRED", "choose one of the item's enabled unlock channels");
      }
      if (!cosmetic.channels.includes(requestedChannel)) {
        throw httpError(409, "COSMETIC_UNLOCK_CHANNEL_UNAVAILABLE", "the requested unlock channel is not enabled for this item");
      }
      purchaseKind = requestedChannel;
    }
    if (["LEVEL", "EVENT"].includes(purchaseKind) || (!cosmetic.catalogManaged && cosmetic.level > 0)) {
      throw httpError(409, "PROFILE_PROGRESSION_NOT_SERVER_AUTHORITATIVE", "this cosmetic requires server-verified progression or event eligibility");
    }
    if (purchaseKind === "PLAY_PRODUCT") {
      throw httpError(409, "CATALOG_PLAY_PURCHASE_NOT_READY", "direct cosmetic checkout is not yet connected to item-specific Google Play entitlement verification");
    }
    if (purchaseKind === "FREE") {
      let alreadyOwned = false;
      this.ctx.storage.transactionSync(() => {
        this.#requireAccountNotDeleting(accountId);
        const now = Date.now();
        const inserted = this.ctx.storage.sql.exec("INSERT OR IGNORE INTO owned_cosmetics (cosmetic_id, category, source, acquired_at, source_ref_hash) VALUES (?, ?, 'DEFAULT', ?, NULL)", cosmeticId, cosmetic.category, now);
        alreadyOwned = Number(inserted.rowsWritten ?? 0) === 0;
        this.ctx.storage.sql.exec("INSERT INTO selected_cosmetics (category, cosmetic_id, updated_at) VALUES (?, ?, ?) ON CONFLICT(category) DO UPDATE SET cosmetic_id = excluded.cosmetic_id, updated_at = excluded.updated_at", cosmetic.category, cosmeticId, now);
      });
      return json(200, { ok: true, alreadyOwned, wallet: this.#walletSnapshot(accountId) });
    }
    if (purchaseKind === "GEMS") {
      if (!Number.isSafeInteger(cosmetic.gemPrice) || cosmetic.gemPrice < 1) throw httpError(400, "COSMETIC_PRICE_INVALID", "cosmetic price is invalid");
      const idem = "cosmetic-purchase:" + requestId;
      const payloadHash = await sha256Hex(JSON.stringify({ cosmeticId, gemPrice: cosmetic.gemPrice }));
      const sourceRefHash = await sha256Hex("ludoproof:cosmetic-purchase:v1:" + requestId);
      let result;
      this.ctx.storage.transactionSync(() => {
        this.#requireAccountNotDeleting(accountId);
        const prior = [...this.ctx.storage.sql.exec("SELECT payload_hash, balance_after FROM wallet_transactions WHERE idempotency_key = ? LIMIT 1", idem)];
        if (prior.length) {
          if (String(prior[0].payload_hash) !== payloadHash) throw httpError(409, "WALLET_IDEMPOTENCY_CONFLICT", "purchase request ID was reused for a different operation");
          result = { replay: true, balance: Number(prior[0].balance_after) }; return;
        }
        const balance = Number([...this.ctx.storage.sql.exec("SELECT balance FROM wallet WHERE account_id = ? LIMIT 1", accountId)][0]?.balance ?? 0);
        const owned = [...this.ctx.storage.sql.exec("SELECT cosmetic_id FROM owned_cosmetics WHERE cosmetic_id = ? LIMIT 1", cosmeticId)];
        if (owned.length) {
          const now = Date.now();
          this.ctx.storage.sql.exec("INSERT INTO selected_cosmetics (category, cosmetic_id, updated_at) VALUES (?, ?, ?) ON CONFLICT(category) DO UPDATE SET cosmetic_id = excluded.cosmetic_id, updated_at = excluded.updated_at", cosmetic.category, cosmeticId, now);
          result = { alreadyOwned: true, replay: false, balance }; return;
        }
        if (balance < cosmetic.gemPrice) throw httpError(409, "INSUFFICIENT_GEMS", "not enough gems to purchase this cosmetic");
        const next = balance - cosmetic.gemPrice, now = Date.now();
        this.ctx.storage.sql.exec("UPDATE wallet SET balance = ?, updated_at = ? WHERE account_id = ?", next, now, accountId);
        this.ctx.storage.sql.exec("INSERT INTO wallet_transactions (transaction_id, idempotency_key, payload_hash, kind, delta, balance_after, source_ref_hash, created_at) VALUES (?, ?, ?, 'COSMETIC_PURCHASE', ?, ?, ?, ?)", crypto.randomUUID().toLowerCase(), idem, payloadHash, -cosmetic.gemPrice, next, sourceRefHash, now);
        this.ctx.storage.sql.exec("INSERT INTO owned_cosmetics (cosmetic_id, category, source, acquired_at, source_ref_hash) VALUES (?, ?, 'GEMS', ?, ?)", cosmeticId, cosmetic.category, now, sourceRefHash);
        this.ctx.storage.sql.exec("INSERT INTO selected_cosmetics (category, cosmetic_id, updated_at) VALUES (?, ?, ?) ON CONFLICT(category) DO UPDATE SET cosmetic_id = excluded.cosmetic_id, updated_at = excluded.updated_at", cosmetic.category, cosmeticId, now);
        result = { replay: false, balance: next };
      });
      return json(200, { ok: true, ...result, cosmeticId, wallet: this.#walletSnapshot(accountId) });
    }
    if (purchaseKind === "REWARDED_ADS") {
      const progress = Number([...this.ctx.storage.sql.exec("SELECT progress FROM cosmetic_ad_progress WHERE cosmetic_id = ? LIMIT 1", cosmeticId)][0]?.progress ?? 0);
      if (progress < cosmetic.ads) throw httpError(409, "NEED_REWARDED_ADS", "this cosmetic needs more verified rewarded ads");
      let alreadyOwned = false;
      this.ctx.storage.transactionSync(() => {
        this.#requireAccountNotDeleting(accountId);
        const now = Date.now();
        const inserted = this.ctx.storage.sql.exec("INSERT OR IGNORE INTO owned_cosmetics (cosmetic_id, category, source, acquired_at, source_ref_hash) VALUES (?, ?, 'REWARDED_ADS', ?, NULL)", cosmeticId, cosmetic.category, now);
        alreadyOwned = Number(inserted.rowsWritten ?? 0) === 0;
        this.ctx.storage.sql.exec("INSERT INTO selected_cosmetics (category, cosmetic_id, updated_at) VALUES (?, ?, ?) ON CONFLICT(category) DO UPDATE SET cosmetic_id = excluded.cosmetic_id, updated_at = excluded.updated_at", cosmetic.category, cosmeticId, now);
      });
      return json(200, { ok: true, replay: false, alreadyOwned, cosmeticId, wallet: this.#walletSnapshot(accountId) });
    }
    throw httpError(409, "COSMETIC_UNLOCK_UNAVAILABLE", "this cosmetic unlock method is not available yet");
  }

  async #selectCosmetic(request) {
    const body = await readJson(request), accountId = requireAccountId(body?.accountId), sessionId = requireSessionId(body?.sessionId);
    this.#requireActiveSession(accountId, sessionId);
    const cosmeticId = String(body?.cosmeticId ?? "");
    const cosmetic = await this.#resolveCosmetic(cosmeticId, body?.catalogCountry);
    if (!cosmetic || !cosmetic.available) throw httpError(400, "COSMETIC_UNAVAILABLE", "this cosmetic is not currently available");
    this.#requireSupportedCosmeticCategory(cosmetic);
    if ([...this.ctx.storage.sql.exec("SELECT cosmetic_id FROM owned_cosmetics WHERE cosmetic_id = ? LIMIT 1", cosmeticId)].length !== 1) throw httpError(403, "COSMETIC_NOT_OWNED", "this cosmetic is not owned by this account");
    this.ctx.storage.sql.exec("INSERT INTO selected_cosmetics (category, cosmetic_id, updated_at) VALUES (?, ?, ?) ON CONFLICT(category) DO UPDATE SET cosmetic_id = excluded.cosmetic_id, updated_at = excluded.updated_at", cosmetic.category, cosmeticId, Date.now());
    return json(200, { ok: true, cosmeticId, wallet: this.#walletSnapshot(accountId) });
  }

  #applyWalletTransaction({ accountId, idempotencyKey, payloadHash, kind, delta, sourceRefHash, removeAds }) {
    let result;
    this.ctx.storage.transactionSync(() => {
      this.#requireAccountNotDeleting(accountId);
      if (kind.startsWith("PLAY_") && [...this.ctx.storage.sql.exec("SELECT ledger_id FROM revoked_play_purchases WHERE ledger_id = ? LIMIT 1", sourceRefHash)].length)
        throw httpError(409, "PLAY_PURCHASE_REVOKED", "this purchase has been revoked");
      const prior = [...this.ctx.storage.sql.exec("SELECT payload_hash, balance_after FROM wallet_transactions WHERE idempotency_key = ? LIMIT 1", idempotencyKey)];
      if (prior.length) {
        if (String(prior[0].payload_hash) !== payloadHash) throw httpError(409, "WALLET_IDEMPOTENCY_CONFLICT", "wallet transaction key was reused with different details");
        result = { replay: true, balance: Number(prior[0].balance_after) }; return;
      }
      const rows = [...this.ctx.storage.sql.exec("SELECT balance FROM wallet WHERE account_id = ? LIMIT 1", accountId)];
      if (!rows.length) throw httpError(404, "WALLET_NOT_FOUND", "wallet was not found");
      const next = Number(rows[0].balance) + delta;
      if (!Number.isSafeInteger(next) || next < 0 || next > MAX_BALANCE) throw httpError(409, "WALLET_BALANCE_LIMIT", "wallet balance cannot safely be updated");
      const now = Date.now();
      this.ctx.storage.sql.exec("UPDATE wallet SET balance = ?, ads_removed = CASE WHEN ? = 1 THEN 1 ELSE ads_removed END, updated_at = ? WHERE account_id = ?", next, removeAds ? 1 : 0, now, accountId);
      this.ctx.storage.sql.exec("INSERT INTO wallet_transactions (transaction_id, idempotency_key, payload_hash, kind, delta, balance_after, source_ref_hash, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", crypto.randomUUID().toLowerCase(), idempotencyKey, payloadHash, kind, delta, next, sourceRefHash, now);
      result = { replay: false, balance: next };
    });
    return result;
  }

  #ensureAccount(accountId, displayName) {
    if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletions WHERE account_id = ? LIMIT 1", accountId)].length) {
      throw httpError(410, "ACCOUNT_DELETED", "this account has been deleted");
    }
    this.#requireAccountNotDeleting(accountId);
    const now = Date.now();
    this.ctx.storage.transactionSync(() => {
      if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletions WHERE account_id = ? LIMIT 1", accountId)].length) throw httpError(410, "ACCOUNT_DELETED", "this account has been deleted");
      this.#requireAccountNotDeleting(accountId);
      const exists = [...this.ctx.storage.sql.exec("SELECT account_id FROM account_meta WHERE account_id = ? LIMIT 1", accountId)];
      if (!exists.length) this.ctx.storage.sql.exec("INSERT INTO account_meta (account_id, display_name, created_at, updated_at) VALUES (?, ?, ?, ?)", accountId, normalizeDisplayName(displayName, this.env), now, now);
      this.ctx.storage.sql.exec("INSERT OR IGNORE INTO wallet (account_id, balance, ads_removed, updated_at) VALUES (?, 0, 0, ?)", accountId, now);
      for (const [id, category] of DEFAULT_COSMETICS) this.ctx.storage.sql.exec("INSERT OR IGNORE INTO owned_cosmetics (cosmetic_id, category, source, acquired_at, source_ref_hash) VALUES (?, ?, 'DEFAULT', ?, NULL)", id, category, now);
      for (const [category, id] of Object.entries(DEFAULT_SELECTIONS)) this.ctx.storage.sql.exec("INSERT OR IGNORE INTO selected_cosmetics (category, cosmetic_id, updated_at) VALUES (?, ?, ?)", category, id, now);
    });
  }
  #requireActiveSession(accountId, sessionId, allowDeletionPending = false) {
    if (!allowDeletionPending) this.#requireAccountNotDeleting(accountId);
    const rows = [...this.ctx.storage.sql.exec("SELECT session_id FROM account_sessions WHERE session_id = ? AND revoked_at IS NULL AND expires_at > ? AND EXISTS (SELECT 1 FROM account_meta WHERE account_id = ?) LIMIT 1", sessionId, Date.now(), accountId)];
    if (!rows.length) throw httpError(401, "ACCOUNT_SESSION_INVALID", "account session is invalid, expired or revoked");
  }

  #requireAccountNotDeleting(accountId) {
    if ([...this.ctx.storage.sql.exec(
      "SELECT account_id FROM account_deletion_claims WHERE account_id = ? LIMIT 1", accountId,
    )].length) {
      throw httpError(409, "ACCOUNT_DELETION_IN_PROGRESS", "this account is being deleted");
    }
  }
  #profileSnapshot(accountId) {
    const rows = [...this.ctx.storage.sql.exec("SELECT account_id, display_name, created_at, updated_at FROM account_meta WHERE account_id = ? LIMIT 1", accountId)];
    if (!rows.length) throw httpError(404, "ACCOUNT_NOT_FOUND", "account was not found");
    return { accountId: String(rows[0].account_id), displayName: String(rows[0].display_name), createdAt: Number(rows[0].created_at), updatedAt: Number(rows[0].updated_at) };
  }
  #walletSnapshot(accountId) {
    const rows = [...this.ctx.storage.sql.exec(
      "SELECT balance, ads_removed, updated_at FROM wallet WHERE account_id = ? LIMIT 1",
      accountId,
    )];
    if (!rows.length) throw httpError(404, "WALLET_NOT_FOUND", "wallet was not found");
    const ownedCosmetics = [...this.ctx.storage.sql.exec(
      "SELECT cosmetic_id FROM owned_cosmetics ORDER BY cosmetic_id",
    )].map((row) => String(row.cosmetic_id));
    const selectedCosmetics = {};
    for (const row of this.ctx.storage.sql.exec(
      "SELECT category, cosmetic_id FROM selected_cosmetics ORDER BY category",
    )) {
      selectedCosmetics[String(row.category)] = String(row.cosmetic_id);
    }
    const rewardedAdProgress = {};
    for (const row of this.ctx.storage.sql.exec(
      "SELECT cosmetic_id, progress FROM cosmetic_ad_progress ORDER BY cosmetic_id",
    )) {
      rewardedAdProgress[String(row.cosmetic_id)] = Number(row.progress);
    }

    return {
      accountId,
      revision: Number([...this.ctx.storage.sql.exec("SELECT revision FROM wallet_revision WHERE id = 1")][0]?.revision ?? 0),
      balance: Number(rows[0].balance),
      adsRemoved: Number(rows[0].ads_removed) === 1,
      updatedAt: Number(rows[0].updated_at),
      ownedCosmetics,
      selectedCosmetics,
      rewardedAdProgress,
    };
  }
}

function requireAccountId(value) {
  if (!isAccountId(value)) throw httpError(400, "INVALID_ACCOUNT_ID", "account identity is invalid");
  return value.toLowerCase();
}
function requireSessionId(value) {
  if (!isSessionId(value)) throw httpError(400, "INVALID_SESSION_ID", "account session is invalid");
  return value.toLowerCase();
}
function requireHash(value, name) {
  if (typeof value !== "string" || !/^[a-f0-9]{64}$/.test(value)) throw httpError(400, "INVALID_" + name.toUpperCase(), name + " is invalid");
  return value;
}
function normalizeDisplayName(value, env) {
  if (isFamilyMode(env)) return "Player";
  return String(value ?? "").replace(/[\u0000-\u001f\u007f]/g, "").trim().replace(/\s+/g, " ").slice(0, NAME_LENGTH) || "Guest User";
}
async function readJson(request) {
  return readJsonObject(request, 8 * 1024);
}
function errorResponse(error) {
  return json(Number.isInteger(error?.status) && error.status >= 400 && error.status <= 599 ? error.status : 500, {
    error: error?.code ?? "INTERNAL_ERROR",
    message: Number.isInteger(error?.status) ? String(error?.message ?? "request failed") : "internal server error",
  });
}
function json(status, body) { return Response.json(body, { status, headers: { "cache-control": "no-store", "content-type": "application/json; charset=utf-8" } }); }
