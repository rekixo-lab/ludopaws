import { httpError, readJsonObject, sha256Hex } from "./crypto.js";
import { hashGoogleNonce, isAccountId } from "./account-security.js";

const FRIEND_ID = /^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/;
const PROFILE_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const NONCE_TTL_MS = 5 * 60 * 1000;

export class AccountDirectory {
  constructor(ctx) {
    this.ctx = ctx;
    ctx.blockConcurrencyWhile(async () => {
      const sql = ctx.storage.sql;
      sql.exec(`CREATE TABLE IF NOT EXISTS account_registry (account_id TEXT PRIMARY KEY, account_kind TEXT NOT NULL, created_at INTEGER NOT NULL)`);
      sql.exec(`CREATE TABLE IF NOT EXISTS guest_requests (client_request_id TEXT PRIMARY KEY, account_id TEXT NOT NULL, created_at INTEGER NOT NULL)`);
      sql.exec(`CREATE TABLE IF NOT EXISTS deleted_guest_requests (client_request_id TEXT PRIMARY KEY, deleted_at INTEGER NOT NULL)`);
      sql.exec(`CREATE TABLE IF NOT EXISTS guest_deletion_credentials (credential_hash TEXT PRIMARY KEY, account_id TEXT NOT NULL, created_at INTEGER NOT NULL)`);
      sql.exec("CREATE INDEX IF NOT EXISTS idx_guest_deletion_credentials_account ON guest_deletion_credentials(account_id)");
      sql.exec(`CREATE TABLE IF NOT EXISTS guest_deletion_claims (account_id TEXT PRIMARY KEY, credential_hash TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL)`);
      sql.exec(`CREATE TABLE IF NOT EXISTS deleted_guest_deletion_credentials (credential_hash TEXT PRIMARY KEY, deleted_at INTEGER NOT NULL)`);
      sql.exec(`CREATE TABLE IF NOT EXISTS account_deletion_claims (account_id TEXT PRIMARY KEY, source TEXT NOT NULL, credential_hash TEXT, claimed_at INTEGER NOT NULL)`);
      sql.exec(`CREATE TABLE IF NOT EXISTS google_identities (subject_hash TEXT PRIMARY KEY, account_id TEXT NOT NULL UNIQUE, linked_at INTEGER NOT NULL)`);
      sql.exec(`CREATE TABLE IF NOT EXISTS google_nonces (nonce_hash TEXT PRIMARY KEY, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, consumed_at INTEGER)`);
      sql.exec(`CREATE TABLE IF NOT EXISTS account_identity_links (identity_type TEXT NOT NULL, external_id TEXT NOT NULL, account_id TEXT NOT NULL, linked_at INTEGER NOT NULL, PRIMARY KEY(identity_type, external_id), UNIQUE(identity_type, account_id))`);
      sql.exec("CREATE INDEX IF NOT EXISTS idx_account_links_account ON account_identity_links(account_id, identity_type)");
    });
  }

  async fetch(request) {
    try {
      const url = new URL(request.url);
      if (request.method === "POST" && url.pathname === "/guest") return await this.#createGuest(request);
      if (request.method === "POST" && url.pathname === "/account/delete") return await this.#deleteAccount(request);
      if (request.method === "POST" && url.pathname === "/account/delete-prepare") return await this.#prepareAccountDeletion(request);
      if (request.method === "POST" && url.pathname === "/guest-deletion/register") return await this.#registerGuestDeletionCredential(request);
      if (request.method === "POST" && url.pathname === "/guest-deletion/claim") return await this.#claimGuestDeletionCredential(request);
      if (request.method === "POST" && url.pathname === "/nonce/create") return await this.#createNonce();
      if (request.method === "POST" && url.pathname === "/nonce/consume") return await this.#consumeNonce(request);
      if (request.method === "POST" && url.pathname === "/google/link") return await this.#linkGoogle(request);
      if (request.method === "POST" && url.pathname === "/google/lookup") return await this.#lookupGoogle(request);
      if (request.method === "POST" && url.pathname === "/identity/link") return await this.#linkExternalIdentity(request);
      if (request.method === "POST" && url.pathname === "/identity/list") return await this.#listExternalIdentities(request);
      if (request.method === "POST" && url.pathname === "/referral/identity") return await this.#referralIdentity(request);
      return json(404, { error: "NOT_FOUND", message: "route not found" });
    } catch (error) { return errorResponse(error); }
  }

  async #createGuest(request) {
    const body = await readJson(request);
    const requestId = requireUuid(body?.clientRequestId, "clientRequestId");
    // This key is a guest-account recovery credential. Never persist the raw bearer-like value.
    const requestKey = await sha256Hex("ludoproof:guest-recovery-key:v1:" + requestId);
    const deleted = [...this.ctx.storage.sql.exec("SELECT client_request_id FROM deleted_guest_requests WHERE client_request_id = ? LIMIT 1", requestKey)];
    if (deleted.length) throw httpError(410, "GUEST_ACCOUNT_DELETED", "this guest account has already been deleted");
    const existing = [...this.ctx.storage.sql.exec("SELECT account_id FROM guest_requests WHERE client_request_id = ? LIMIT 1", requestKey)];
    if (existing.length === 1) {
      const accountId = String(existing[0].account_id);
      if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletion_claims WHERE account_id = ? LIMIT 1", accountId)].length ||
          [...this.ctx.storage.sql.exec("SELECT account_id FROM guest_deletion_claims WHERE account_id = ? LIMIT 1", accountId)].length) {
        throw httpError(409, "ACCOUNT_DELETION_IN_PROGRESS", "this guest account is being deleted");
      }
      return json(200, { ok: true, accountId, replay: true });
    }

    const generatedId = crypto.randomUUID().toLowerCase();
    const now = Date.now();
    this.ctx.storage.transactionSync(() => {
      const raced = [...this.ctx.storage.sql.exec("SELECT account_id FROM guest_requests WHERE client_request_id = ? LIMIT 1", requestKey)];
      if (raced.length > 0) return;
      this.ctx.storage.sql.exec("INSERT INTO account_registry (account_id, account_kind, created_at) VALUES (?, 'GUEST', ?)", generatedId, now);
      this.ctx.storage.sql.exec("INSERT INTO guest_requests (client_request_id, account_id, created_at) VALUES (?, ?, ?)", requestKey, generatedId, now);
    });
    const saved = [...this.ctx.storage.sql.exec("SELECT account_id FROM guest_requests WHERE client_request_id = ? LIMIT 1", requestKey)];
    if (saved.length !== 1) throw httpError(503, "ACCOUNT_CREATE_FAILED", "guest account could not be created safely");
    return json(200, { ok: true, accountId: String(saved[0].account_id), replay: String(saved[0].account_id) !== generatedId });
  }

  async #deleteAccount(request) {
    const body = await readJson(request);
    const accountId = requireAccountId(body?.accountId);
    const keys = [...this.ctx.storage.sql.exec("SELECT client_request_id FROM guest_requests WHERE account_id = ?", accountId)]
      .map((row) => String(row.client_request_id));
    const credentialHashes = [
      ...this.ctx.storage.sql.exec("SELECT credential_hash FROM guest_deletion_credentials WHERE account_id = ?", accountId),
      ...this.ctx.storage.sql.exec("SELECT credential_hash FROM guest_deletion_claims WHERE account_id = ?", accountId),
    ].map((row) => String(row.credential_hash));
    let existed = false;
    const now = Date.now();
    this.ctx.storage.transactionSync(() => {
      existed = [...this.ctx.storage.sql.exec("SELECT account_id FROM account_registry WHERE account_id = ? LIMIT 1", accountId)].length === 1;
      for (const key of keys) this.ctx.storage.sql.exec(
        "INSERT OR IGNORE INTO deleted_guest_requests (client_request_id, deleted_at) VALUES (?, ?)", key, now,
      );
      for (const credentialHash of new Set(credentialHashes)) this.ctx.storage.sql.exec(
        "INSERT OR IGNORE INTO deleted_guest_deletion_credentials (credential_hash, deleted_at) VALUES (?, ?)",
        credentialHash, now,
      );
      this.ctx.storage.sql.exec("DELETE FROM guest_deletion_credentials WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM guest_deletion_claims WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM account_deletion_claims WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM guest_requests WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM google_identities WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM account_identity_links WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM account_registry WHERE account_id = ?", accountId);
    });
    return json(200, { ok: true, deleted: true, replay: !existed });
  }

  async #prepareAccountDeletion(request) {
    const body = await readJson(request);
    const accountId = requireAccountId(body?.accountId);
    const source = String(body?.source ?? "SESSION");
    if (!["SESSION", "GUEST_CODE"].includes(source)) throw httpError(400, "ACCOUNT_DELETE_SOURCE_INVALID", "account deletion source is invalid");
    const credentialHash = source === "GUEST_CODE" ? requireHash(body?.credentialHash, "credentialHash") : null;
    let replay = false;

    this.ctx.storage.transactionSync(() => {
      const accounts = [...this.ctx.storage.sql.exec(
        "SELECT account_id FROM account_registry WHERE account_id = ? LIMIT 1", accountId,
      )];
      if (!accounts.length) throw httpError(404, "ACCOUNT_NOT_FOUND", "account was not found");
      const existing = [...this.ctx.storage.sql.exec(
        "SELECT source, credential_hash FROM account_deletion_claims WHERE account_id = ? LIMIT 1", accountId,
      )];
      if (existing.length) {
        if (
          String(existing[0].source) !== source ||
          String(existing[0].credential_hash ?? "") !== String(credentialHash ?? "")
        ) throw httpError(409, "ACCOUNT_DELETION_IN_PROGRESS", "a different account deletion request is already in progress");
        replay = true;
        return;
      }
      if (source === "GUEST_CODE") {
        const codeClaim = [...this.ctx.storage.sql.exec(
          "SELECT credential_hash FROM guest_deletion_claims WHERE account_id = ? LIMIT 1", accountId,
        )];
        if (!codeClaim.length || String(codeClaim[0].credential_hash) !== credentialHash) {
          throw httpError(409, "ACCOUNT_DELETION_IN_PROGRESS", "guest deletion code was not claimed");
        }
      }
      this.ctx.storage.sql.exec(
        "INSERT INTO account_deletion_claims (account_id, source, credential_hash, claimed_at) VALUES (?, ?, ?, ?)",
        accountId, source, credentialHash, Date.now(),
      );
    });
    return json(200, { ok: true, claimed: true, replay });
  }

  async #registerGuestDeletionCredential(request) {
    const body = await readJson(request);
    const accountId = requireAccountId(body?.accountId);
    const rotate = body?.rotate === true;
    // The server, not the client, chooses the 256-bit secret. Only its domain-separated
    // hash is persisted; the plaintext is returned once to the authenticated app session.
    const bytes = new Uint8Array(32);
    crypto.getRandomValues(bytes);
    const credential = [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
    const credentialHash = await sha256Hex("ludoproof:guest-deletion-credential:v1:" + credential);

    this.ctx.storage.transactionSync(() => {
      const accounts = [...this.ctx.storage.sql.exec(
        "SELECT account_kind FROM account_registry WHERE account_id = ? LIMIT 1", accountId,
      )];
      if (!accounts.length) throw httpError(404, "ACCOUNT_NOT_FOUND", "account was not found");
      const google = [...this.ctx.storage.sql.exec(
        "SELECT account_id FROM google_identities WHERE account_id = ? LIMIT 1", accountId,
      )];
      if (google.length || String(accounts[0].account_kind) !== "GUEST") {
        throw httpError(409, "GUEST_DELETION_CODE_NOT_AVAILABLE", "guest deletion codes are available only for unlinked guest accounts");
      }
      const deletionClaim = [...this.ctx.storage.sql.exec(
        "SELECT account_id FROM account_deletion_claims WHERE account_id = ? LIMIT 1", accountId,
      )];
      const guestClaim = [...this.ctx.storage.sql.exec(
        "SELECT credential_hash FROM guest_deletion_claims WHERE account_id = ? LIMIT 1", accountId,
      )];
      if (deletionClaim.length || guestClaim.length) {
        throw httpError(409, "ACCOUNT_DELETION_IN_PROGRESS", "this guest account is being deleted");
      }
      if (rotate) {
        // Explicit rotation revokes every previously saved code for this guest account.
        this.ctx.storage.sql.exec("DELETE FROM guest_deletion_credentials WHERE account_id = ?", accountId);
      }
      this.ctx.storage.sql.exec(
        "INSERT INTO guest_deletion_credentials (credential_hash, account_id, created_at) VALUES (?, ?, ?)",
        credentialHash, accountId, Date.now(),
      );
    });
    return json(200, { ok: true, ready: true, credential });
  }

  async #claimGuestDeletionCredential(request) {
    const body = await readJson(request);
    const credentialHash = requireHash(body?.credentialHash, "credentialHash");
    const deleted = [...this.ctx.storage.sql.exec(
      "SELECT credential_hash FROM deleted_guest_deletion_credentials WHERE credential_hash = ? LIMIT 1", credentialHash,
    )];
    if (deleted.length) return json(200, { ok: true, deleted: true, replay: true });

    let accountId = null;
    this.ctx.storage.transactionSync(() => {
      const deletedAgain = [...this.ctx.storage.sql.exec(
        "SELECT credential_hash FROM deleted_guest_deletion_credentials WHERE credential_hash = ? LIMIT 1", credentialHash,
      )];
      if (deletedAgain.length) return;
      const credentials = [...this.ctx.storage.sql.exec(
        "SELECT account_id FROM guest_deletion_credentials WHERE credential_hash = ? LIMIT 1", credentialHash,
      )];
      if (!credentials.length) throw httpError(404, "GUEST_DELETION_CODE_INVALID", "guest deletion code is invalid or no longer available");
      accountId = requireAccountId(String(credentials[0].account_id));
      const accounts = [...this.ctx.storage.sql.exec(
        "SELECT account_kind FROM account_registry WHERE account_id = ? LIMIT 1", accountId,
      )];
      const google = [...this.ctx.storage.sql.exec(
        "SELECT account_id FROM google_identities WHERE account_id = ? LIMIT 1", accountId,
      )];
      if (!accounts.length || google.length || String(accounts[0].account_kind) !== "GUEST") {
        throw httpError(409, "GUEST_DELETION_CODE_NOT_AVAILABLE", "this code can delete only an unlinked guest account");
      }
      const existingClaim = [...this.ctx.storage.sql.exec(
        "SELECT credential_hash FROM guest_deletion_claims WHERE account_id = ? LIMIT 1", accountId,
      )];
      if (existingClaim.length && String(existingClaim[0].credential_hash) !== credentialHash) {
        throw httpError(409, "ACCOUNT_DELETION_IN_PROGRESS", "this guest account is already being deleted");
      }
      const deletionClaim = [...this.ctx.storage.sql.exec(
        "SELECT source, credential_hash FROM account_deletion_claims WHERE account_id = ? LIMIT 1", accountId,
      )];
      if (deletionClaim.length && (
        String(deletionClaim[0].source) !== "GUEST_CODE" ||
        String(deletionClaim[0].credential_hash ?? "") !== credentialHash
      )) {
        throw httpError(409, "ACCOUNT_DELETION_IN_PROGRESS", "this account already has a different deletion request");
      }
      this.ctx.storage.sql.exec(
        "INSERT OR IGNORE INTO account_deletion_claims (account_id, source, credential_hash, claimed_at) VALUES (?, 'GUEST_CODE', ?, ?)",
        accountId, credentialHash, Date.now(),
      );
      this.ctx.storage.sql.exec(
        "INSERT OR IGNORE INTO guest_deletion_claims (account_id, credential_hash, created_at) VALUES (?, ?, ?)",
        accountId, credentialHash, Date.now(),
      );
    });
    if (accountId == null) return json(200, { ok: true, deleted: true, replay: true });
    return json(200, { ok: true, accountId, deleted: false, replay: false });
  }

  async #createNonce() {
    const nonce = [...crypto.getRandomValues(new Uint8Array(32))].map((byte) => byte.toString(16).padStart(2, "0")).join("");
    const nonceHash = await hashGoogleNonce(nonce);
    const now = Date.now();
    this.ctx.storage.sql.exec("INSERT INTO google_nonces (nonce_hash, created_at, expires_at, consumed_at) VALUES (?, ?, ?, NULL)", nonceHash, now, now + NONCE_TTL_MS);
    this.ctx.storage.sql.exec("DELETE FROM google_nonces WHERE expires_at < ?", now - NONCE_TTL_MS);
    return json(200, { ok: true, nonce, expiresAt: now + NONCE_TTL_MS });
  }

  async #consumeNonce(request) {
    const body = await readJson(request);
    if (typeof body?.nonce !== "string" || body.nonce.length < 16 || body.nonce.length > 512) throw httpError(400, "GOOGLE_NONCE_INVALID", "Google sign-in challenge is invalid");
    const hash = await hashGoogleNonce(body.nonce);
    const now = Date.now();
    let consumed = false;
    this.ctx.storage.transactionSync(() => {
      const update = this.ctx.storage.sql.exec("UPDATE google_nonces SET consumed_at = ? WHERE nonce_hash = ? AND consumed_at IS NULL AND expires_at > ?", now, hash, now);
      consumed = Number(update.rowsWritten ?? 0) === 1;
    });
    if (!consumed) throw httpError(401, "GOOGLE_NONCE_EXPIRED_OR_REPLAYED", "Google sign-in challenge expired or was already used");
    return json(200, { ok: true });
  }

  async #linkGoogle(request) {
    const body = await readJson(request);
    const accountId = requireAccountId(body?.accountId);
    const subjectHash = requireHash(body?.subjectHash, "subjectHash");
    if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_registry WHERE account_id = ? LIMIT 1", accountId)].length !== 1) throw httpError(404, "ACCOUNT_NOT_FOUND", "account was not found");
    if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletion_claims WHERE account_id = ? LIMIT 1", accountId)].length ||
        [...this.ctx.storage.sql.exec("SELECT credential_hash FROM guest_deletion_claims WHERE account_id = ? LIMIT 1", accountId)].length) {
      throw httpError(409, "ACCOUNT_DELETION_IN_PROGRESS", "this account is being deleted and cannot be linked");
    }
    const rows = [...this.ctx.storage.sql.exec("SELECT subject_hash, account_id FROM google_identities WHERE subject_hash = ? OR account_id = ? LIMIT 2", subjectHash, accountId)];
    if (rows.length) {
      const exact = rows.some((row) => String(row.subject_hash) === subjectHash && String(row.account_id) === accountId);
      const conflict = rows.some((row) => String(row.subject_hash) !== subjectHash || String(row.account_id) !== accountId);
      if (exact && !conflict) return json(200, { ok: true, accountId, linked: true, replay: true });
      throw httpError(409, "GOOGLE_IDENTITY_ALREADY_LINKED", "this Google account is already linked to another Ludo Paws account, or this account already has a different Google identity");
    }
    this.ctx.storage.transactionSync(() => {
      if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletion_claims WHERE account_id = ? LIMIT 1", accountId)].length ||
          [...this.ctx.storage.sql.exec("SELECT credential_hash FROM guest_deletion_claims WHERE account_id = ? LIMIT 1", accountId)].length) {
        throw httpError(409, "ACCOUNT_DELETION_IN_PROGRESS", "this account is being deleted and cannot be linked");
      }
      this.ctx.storage.sql.exec("INSERT INTO google_identities (subject_hash, account_id, linked_at) VALUES (?, ?, ?)", subjectHash, accountId, Date.now());
      this.ctx.storage.sql.exec("DELETE FROM guest_deletion_credentials WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("UPDATE account_registry SET account_kind = 'GOOGLE_LINKED' WHERE account_id = ?", accountId);
    });
    return json(200, { ok: true, accountId, linked: true, replay: false });
  }

  async #lookupGoogle(request) {
    const body = await readJson(request);
    const subjectHash = requireHash(body?.subjectHash, "subjectHash");
    const rows = [...this.ctx.storage.sql.exec("SELECT account_id FROM google_identities WHERE subject_hash = ? LIMIT 1", subjectHash)];
    if (!rows.length) throw httpError(404, "GOOGLE_IDENTITY_NOT_LINKED", "this Google account has not been linked to a Ludo Paws account yet");
    return json(200, { ok: true, accountId: String(rows[0].account_id) });
  }

  async #linkExternalIdentity(request) {
    const body = await readJson(request);
    const accountId = requireAccountId(body?.accountId);
    const identityType = body?.identityType;
    const externalId = String(body?.externalId ?? "").trim();
    if (!["friend", "leaderboard"].includes(identityType)) throw httpError(400, "IDENTITY_TYPE_INVALID", "identity type is invalid");
    if ((identityType === "friend" && !FRIEND_ID.test(externalId)) || (identityType === "leaderboard" && !PROFILE_ID.test(externalId))) throw httpError(400, "EXTERNAL_ID_INVALID", "linked identity is invalid");
    if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_registry WHERE account_id = ? LIMIT 1", accountId)].length !== 1) throw httpError(404, "ACCOUNT_NOT_FOUND", "account was not found");
    if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletion_claims WHERE account_id = ? LIMIT 1", accountId)].length ||
        [...this.ctx.storage.sql.exec("SELECT credential_hash FROM guest_deletion_claims WHERE account_id = ? LIMIT 1", accountId)].length) {
      throw httpError(409, "ACCOUNT_DELETION_IN_PROGRESS", "this account is being deleted and cannot link another identity");
    }
    const rows = [...this.ctx.storage.sql.exec("SELECT identity_type, external_id, account_id FROM account_identity_links WHERE identity_type = ? AND (external_id = ? OR account_id = ?) LIMIT 2", identityType, externalId, accountId)];
    if (rows.length) {
      const exact = rows.some((row) => String(row.external_id) === externalId && String(row.account_id) === accountId);
      const conflict = rows.some((row) => String(row.external_id) !== externalId || String(row.account_id) !== accountId);
      if (exact && !conflict) return json(200, { ok: true, identityType, externalId, replay: true });
      throw httpError(409, "ACCOUNT_IDENTITY_CONFLICT", "this profile or friend identity is already linked to another account");
    }
    let replay = false;
    this.ctx.storage.transactionSync(() => {
      if ([...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletion_claims WHERE account_id = ? LIMIT 1", accountId)].length ||
          [...this.ctx.storage.sql.exec("SELECT credential_hash FROM guest_deletion_claims WHERE account_id = ? LIMIT 1", accountId)].length) {
        throw httpError(409, "ACCOUNT_DELETION_IN_PROGRESS", "this account is being deleted and cannot link another identity");
      }
      const current = [...this.ctx.storage.sql.exec(
        "SELECT identity_type, external_id, account_id FROM account_identity_links WHERE identity_type = ? AND (external_id = ? OR account_id = ?) LIMIT 2",
        identityType, externalId, accountId,
      )];
      if (current.length) {
        const exact = current.some((row) => String(row.external_id) === externalId && String(row.account_id) === accountId);
        const conflict = current.some((row) => String(row.external_id) !== externalId || String(row.account_id) !== accountId);
        if (exact && !conflict) { replay = true; return; }
        throw httpError(409, "ACCOUNT_IDENTITY_CONFLICT", "this profile or friend identity is already linked to another account");
      }
      this.ctx.storage.sql.exec("INSERT INTO account_identity_links (identity_type, external_id, account_id, linked_at) VALUES (?, ?, ?, ?)", identityType, externalId, accountId, Date.now());
    });
    return json(200, { ok: true, identityType, externalId, replay });
  }

  async #referralIdentity(request) {
    const body = await readJson(request);
    let accountId = body.accountId;
    if (!accountId && typeof body.profileId === "string" && PROFILE_ID.test(body.profileId)) {
      accountId = [...this.ctx.storage.sql.exec("SELECT account_id FROM account_identity_links WHERE identity_type = 'leaderboard' AND external_id = ?", body.profileId.toLowerCase())][0]?.account_id;
    }
    if (!accountId && typeof body.friendId === "string" && FRIEND_ID.test(body.friendId)) {
      accountId = [...this.ctx.storage.sql.exec("SELECT account_id FROM account_identity_links WHERE identity_type = 'friend' AND external_id = ?", body.friendId)][0]?.account_id;
    }
    if (!accountId) return json(200, { ok: true, accountId: null });
    accountId = requireAccountId(accountId);
    const account = [...this.ctx.storage.sql.exec("SELECT created_at FROM account_registry WHERE account_id = ?", accountId)][0];
    if (!account || [...this.ctx.storage.sql.exec("SELECT account_id FROM account_deletion_claims WHERE account_id = ?", accountId)].length ||
        [...this.ctx.storage.sql.exec("SELECT account_id FROM guest_deletion_claims WHERE account_id = ?", accountId)].length) {
      if (!body.accountId) return json(200, { ok: true, accountId: null });
      throw httpError(409, "ACCOUNT_UNAVAILABLE", "This account is unavailable.");
    }
    const google = [...this.ctx.storage.sql.exec("SELECT subject_hash FROM google_identities WHERE account_id = ?", accountId)][0];
    return json(200, { ok: true, accountId, createdAt: Number(account.created_at), subjectHash: google?.subject_hash ?? null });
  }

  async #listExternalIdentities(request) {
    const body = await readJson(request);
    const accountId = requireAccountId(body?.accountId);
    const rows = [...this.ctx.storage.sql.exec("SELECT identity_type, external_id FROM account_identity_links WHERE account_id = ?", accountId)];
    const links = {};
    for (const row of rows) links[String(row.identity_type)] = String(row.external_id);
    const googleRows = [...this.ctx.storage.sql.exec("SELECT subject_hash FROM google_identities WHERE account_id = ? LIMIT 1", accountId)];
    return json(200, { ok: true, accountId, googleLinked: googleRows.length === 1, friendId: links.friend ?? null, leaderboardProfileId: links.leaderboard ?? null });
  }
}

function requireUuid(value, field) {
  if (typeof value !== "string" || !UUID_V4.test(value)) throw httpError(400, "INVALID_REQUEST_ID", field + " must be a UUID v4");
  return value.toLowerCase();
}
function requireAccountId(value) {
  if (!isAccountId(value)) throw httpError(400, "INVALID_ACCOUNT_ID", "account identity is invalid");
  return value.toLowerCase();
}
function requireHash(value, field) {
  if (typeof value !== "string" || !/^[a-f0-9]{64}$/.test(value)) throw httpError(400, "INVALID_" + field.toUpperCase(), field + " is invalid");
  return value;
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
function json(status, body) {
  return Response.json(body, { status, headers: { "cache-control": "no-store", "content-type": "application/json; charset=utf-8" } });
}
