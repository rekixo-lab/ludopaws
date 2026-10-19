import {
  bearerToken,
  deriveFriendIdentity,
  httpError,
  issueFriendRoomJoinToken,
  normalizeDisplayName,
  requireClientRequestId,
  sha256Hex,
} from "./crypto.js";

const FRIEND_ID =
  /^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/;
const MATCH_ID =
  /^LP[A-Z2-9]{8}$/;
const FRIEND_TOKEN =
  /^lf_[A-Za-z0-9_-]{32,}$/;
const PRESENCE_TTL_MS =
  75 * 1000;
const INVITE_TTL_MS =
  24 * 60 * 60 * 1000;
const JOIN_TOKEN_TTL_MS =
  5 * 60 * 1000;
const REPORT_RETENTION_MS = 90 * 24 * 60 * 60 * 1000;

export class FriendDirectory {
  constructor(ctx, env) {
    this.ctx =
      ctx;
    this.env =
      env;

    ctx.blockConcurrencyWhile(
      async () => {
        const sql =
          ctx.storage.sql;

        sql.exec(`
          CREATE TABLE IF NOT EXISTS friend_profiles (
            friend_id TEXT PRIMARY KEY,
            token_hash TEXT NOT NULL UNIQUE,
            registration_request_id TEXT NOT NULL UNIQUE,
            display_name TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            last_seen_at INTEGER NOT NULL
          )
        `);

        sql.exec(`CREATE TABLE IF NOT EXISTS deleted_friend_profiles (friend_id TEXT PRIMARY KEY, deleted_at INTEGER NOT NULL)`);
        sql.exec(`CREATE TABLE IF NOT EXISTS deleted_friend_registration_requests (request_hash TEXT PRIMARY KEY, deleted_at INTEGER NOT NULL)`);

        sql.exec(`
          CREATE TABLE IF NOT EXISTS friend_requests (
            request_id TEXT PRIMARY KEY,
            sender_id TEXT NOT NULL,
            receiver_id TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
          )
        `);

        sql.exec(`
          CREATE INDEX IF NOT EXISTS idx_friend_requests_receiver
          ON friend_requests (
            receiver_id,
            status,
            updated_at DESC
          )
        `);

        sql.exec(`
          CREATE INDEX IF NOT EXISTS idx_friend_requests_sender
          ON friend_requests (
            sender_id,
            status,
            updated_at DESC
          )
        `);

        sql.exec(`
          CREATE TABLE IF NOT EXISTS friendships (
            friend_a TEXT NOT NULL,
            friend_b TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            PRIMARY KEY (
              friend_a,
              friend_b
            )
          )
        `);

        sql.exec(`
          CREATE INDEX IF NOT EXISTS idx_friendships_b
          ON friendships (
            friend_b,
            friend_a
          )
        `);

        sql.exec(`
          CREATE TABLE IF NOT EXISTS friend_invites (
            invite_id TEXT PRIMARY KEY,
            sender_id TEXT NOT NULL,
            receiver_id TEXT NOT NULL,
            match_id TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            expires_at INTEGER NOT NULL
          )
        `);

        sql.exec(`
          CREATE INDEX IF NOT EXISTS idx_friend_invites_receiver
          ON friend_invites (
            receiver_id,
            status,
            created_at DESC
          )
        `);
        sql.exec(`CREATE TABLE IF NOT EXISTS friend_conversation_index (
          friend_id TEXT NOT NULL, other_friend_id TEXT NOT NULL, last_message_at INTEGER NOT NULL,
          PRIMARY KEY(friend_id, other_friend_id)
        )`);
        sql.exec(`CREATE TABLE IF NOT EXISTS friend_recent_index (
          owner_friend_id TEXT NOT NULL, opponent_friend_id TEXT NOT NULL, played_at INTEGER NOT NULL,
          PRIMARY KEY(owner_friend_id, opponent_friend_id)
        )`);
        sql.exec("CREATE INDEX IF NOT EXISTS idx_friend_conversation_index_other ON friend_conversation_index(other_friend_id, friend_id)");
        sql.exec("CREATE INDEX IF NOT EXISTS idx_friend_recent_index_opponent ON friend_recent_index(opponent_friend_id, owner_friend_id)");
        // A block is directional, but protection works in both directions.
        sql.exec(`CREATE TABLE IF NOT EXISTS friend_blocks (
          blocker_id TEXT NOT NULL, blocked_id TEXT NOT NULL, created_at INTEGER NOT NULL,
          PRIMARY KEY (blocker_id, blocked_id)
        )`);
        sql.exec("CREATE INDEX IF NOT EXISTS idx_friend_blocks_target ON friend_blocks(blocked_id)");
        // Metadata-only reports; never store raw chat content in moderation records.
        sql.exec(`CREATE TABLE IF NOT EXISTS friend_reports (
          request_id TEXT PRIMARY KEY, reporter_id TEXT NOT NULL, target_id TEXT NOT NULL,
          reason TEXT NOT NULL, created_at INTEGER NOT NULL,
          status TEXT NOT NULL DEFAULT 'OPEN', resolved_at INTEGER, reviewer_id TEXT
        )`);
        sql.exec("CREATE INDEX IF NOT EXISTS idx_friend_reports_author ON friend_reports(reporter_id, created_at)");
        sql.exec("CREATE INDEX IF NOT EXISTS idx_friend_reports_status ON friend_reports(status, created_at)");
      },
    );
  }

  async alarm() {
    this.ctx.storage.sql.exec(
      "DELETE FROM friend_reports WHERE created_at < ?",
      Date.now() - REPORT_RETENTION_MS,
    );
    await this.#scheduleReportCleanup();
  }

  async #scheduleReportCleanup() {
    if (typeof this.ctx.storage.setAlarm !== "function") return;
    const next = [...this.ctx.storage.sql.exec(
      "SELECT MIN(created_at) AS oldest FROM friend_reports",
    )][0]?.oldest;
    if (next != null) {
      await this.ctx.storage.setAlarm(Math.max(Date.now() + 1000, Number(next) + REPORT_RETENTION_MS + 1));
    }
  }

  async fetch(request) {
    try {
      const url =
        new URL(
          request.url,
        );

      if (
        request.method ===
          "POST" &&
        url.pathname ===
          "/register"
      ) {
        return await this.#register(
          request,
        );
      }

      if (
        request.method ===
          "GET" &&
        url.pathname ===
          "/identity"
      ) {
        return await this.#identity(
          request,
        );
      }

      // Internal-only recovery endpoint. The public Worker exposes it only after
      // the caller's account session and existing account-to-friend link are verified.
      if (
        request.method === "POST" &&
        url.pathname === "/account/recover"
      ) {
        return await this.#recoverAccountIdentity(request);
      }
      if (request.method === "POST" && url.pathname === "/conversation/register") return await this.#registerConversation(request);
      if (request.method === "POST" && url.pathname === "/recent/register") return await this.#registerRecent(request);
      if (request.method === "POST" && url.pathname === "/account/delete-preview") return await this.#previewAccountDeletion(request);
      if (request.method === "POST" && url.pathname === "/account/delete") return await this.#deleteAccount(request);

      if (request.method === "POST" && url.pathname === "/block") return await this.#blockFriend(request);
      if (request.method === "POST" && url.pathname === "/unblock") return await this.#unblockFriend(request);
      if (request.method === "POST" && url.pathname === "/report") return await this.#reportFriend(request);
      // These methods are invoked only through the authenticated Worker admin path.
      if (request.method === "POST" && url.pathname === "/moderation/list") return this.#listReports();
      if (request.method === "POST" && url.pathname === "/moderation/resolve") return await this.#resolveReport(request);
      if (
        request.method ===
          "GET" &&
        url.pathname ===
          "/snapshot"
      ) {
        return await this.#snapshot(
          request,
        );
      }

      if (
        request.method ===
          "POST" &&
        url.pathname ===
          "/heartbeat"
      ) {
        return await this.#heartbeat(
          request,
        );
      }

      if (
        request.method ===
          "POST" &&
        url.pathname ===
          "/request"
      ) {
        return await this.#sendRequest(
          request,
        );
      }

      if (
        request.method ===
          "POST" &&
        url.pathname ===
          "/request/respond"
      ) {
        return await this.#respondRequest(
          request,
        );
      }

      if (
        request.method ===
          "POST" &&
        url.pathname ===
          "/remove"
      ) {
        return await this.#removeFriend(
          request,
        );
      }

      if (
        request.method ===
          "POST" &&
        url.pathname ===
          "/invite"
      ) {
        return await this.#invite(
          request,
        );
      }

      if (
        request.method ===
          "POST" &&
        url.pathname ===
          "/invite/respond"
      ) {
        return await this.#respondInvite(
          request,
        );
      }

      if (
        request.method ===
          "POST" &&
        url.pathname ===
          "/invite/join-check"
      ) {
        return await this.#checkInviteForJoin(
          request,
        );
      }

      return json(
        404,
        {
          error:
            "NOT_FOUND",
          message:
            "route not found",
        },
      );
    } catch (error) {
      return errorResponse(
        error,
      );
    }
  }

  #listReports() {
    const reports = [...this.ctx.storage.sql.exec(
      "SELECT request_id, reporter_id, target_id, reason, created_at, status FROM friend_reports WHERE status = 'OPEN' ORDER BY created_at ASC LIMIT 100",
    )];
    return json(200, { ok: true, reports });
  }

  async #resolveReport(request) {
    const body = await readJson(request);
    const requestId = requireClientRequestId(body?.reportId);
    const outcome = body?.outcome;
    const reviewerId = body?.reviewerId;
    if (!["REVIEWED", "DISMISSED", "ACTIONED"].includes(outcome)) {
      throw httpError(400, "REPORT_OUTCOME_INVALID", "invalid moderation decision");
    }
    if (typeof reviewerId !== "string" || !/^[0-9a-f-]{36}$/i.test(reviewerId)) {
      throw httpError(400, "MODERATOR_INVALID", "a moderator account is required");
    }
    const records = [...this.ctx.storage.sql.exec(
      "SELECT status, reviewer_id FROM friend_reports WHERE request_id = ? LIMIT 1", requestId,
    )];
    if (!records.length) throw httpError(404, "REPORT_NOT_FOUND", "report was not found");
    if (records[0].status !== "OPEN") {
      if (records[0].status !== outcome || records[0].reviewer_id !== reviewerId) {
        throw httpError(409, "REPORT_ALREADY_RESOLVED", "report has already been reviewed");
      }
      return json(200, { ok: true, replayed: true });
    }
    this.ctx.storage.sql.exec(
      "UPDATE friend_reports SET status = ?, resolved_at = ?, reviewer_id = ? WHERE request_id = ? AND status = 'OPEN'",
      outcome, Date.now(), reviewerId, requestId,
    );
    return json(200, { ok: true, replayed: false });
  }

  #isBlocked(first, second) {
    return [...this.ctx.storage.sql.exec(
      "SELECT 1 FROM friend_blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?) LIMIT 1",
      first, second, second, first,
    )].length > 0;
  }

  #assertNotBlocked(first, second) {
    if (this.#isBlocked(first, second)) {
      throw httpError(403, "FRIEND_BLOCKED", "interaction is unavailable between these players");
    }
  }

  async #blockFriend(request) {
    const me = await this.#authorize(request);
    const body = await readJson(request);
    const target = normalizeFriendId(body?.friendId);
    if (target === me.friendId) throw httpError(400, "CANNOT_BLOCK_SELF", "you cannot block yourself");
    if (!this.#profile(target)) throw httpError(404, "FRIEND_NOT_FOUND", "friend ID was not found");
    const [first, second] = canonicalPair(me.friendId, target);
    const now = Date.now();
    this.ctx.storage.transactionSync(() => {
      this.ctx.storage.sql.exec(
        "INSERT OR IGNORE INTO friend_blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)",
        me.friendId, target, now,
      );
      // A blocked account must not contact the blocker through an old friendship,
      // pending request or accepted invitation. Unblock never restores friendship.
      this.ctx.storage.sql.exec("DELETE FROM friendships WHERE friend_a = ? AND friend_b = ?", first, second);
      this.ctx.storage.sql.exec(
        "DELETE FROM friend_requests WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)",
        first, second, second, first,
      );
      this.ctx.storage.sql.exec(
        "UPDATE friend_invites SET status = 'REVOKED', updated_at = ? WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) AND status IN ('PENDING', 'ACCEPTED')",
        now, first, second, second, first,
      );
      this.ctx.storage.sql.exec(
        "DELETE FROM friend_conversation_index WHERE (friend_id = ? AND other_friend_id = ?) OR (friend_id = ? AND other_friend_id = ?)",
        first, second, second, first,
      );
    });
    return json(200, { ok: true, blocked: true, friendId: target });
  }

  async #unblockFriend(request) {
    const me = await this.#authorize(request);
    const body = await readJson(request);
    const target = normalizeFriendId(body?.friendId);
    this.ctx.storage.sql.exec(
      "DELETE FROM friend_blocks WHERE blocker_id = ? AND blocked_id = ?",
      me.friendId, target,
    );
    return json(200, { ok: true, blocked: false, friendId: target });
  }

  async #reportFriend(request) {
    const me = await this.#authorize(request);
    const body = await readJson(request);
    const target = normalizeFriendId(body?.friendId);
    const reason = body?.reason;
    const requestId = requireClientRequestId(body?.clientRequestId);
    const reasons = new Set(["HARASSMENT", "SPAM", "HATE", "THREATS", "SEXUAL_CONTENT", "OTHER"]);
    if (target === me.friendId) throw httpError(400, "CANNOT_REPORT_SELF", "you cannot report yourself");
    if (!this.#profile(target)) throw httpError(404, "FRIEND_NOT_FOUND", "friend ID was not found");
    if (!reasons.has(reason)) throw httpError(400, "REPORT_REASON_INVALID", "choose a valid report reason");
    const existing = [...this.ctx.storage.sql.exec(
      "SELECT reporter_id, target_id, reason FROM friend_reports WHERE request_id = ? LIMIT 1",
      requestId,
    )];
    if (existing.length) {
      if (existing[0].reporter_id !== me.friendId || existing[0].target_id !== target || existing[0].reason !== reason) {
        throw httpError(409, "REPORT_ID_CONFLICT", "report request ID was already used");
      }
      return json(200, { ok: true, reported: true, replayed: true });
    }
    const now = Date.now();
    const prior = [...this.ctx.storage.sql.exec(
      "SELECT COUNT(*) AS count FROM friend_reports WHERE reporter_id = ? AND created_at >= ?",
      me.friendId, now - 24 * 60 * 60 * 1000,
    )];
    if (Number(prior[0]?.count ?? 0) >= 10) {
      throw httpError(429, "REPORT_LIMIT", "too many reports; try again later");
    }
    this.ctx.storage.sql.exec(
      "INSERT INTO friend_reports (request_id, reporter_id, target_id, reason, created_at) VALUES (?, ?, ?, ?, ?)",
      requestId, me.friendId, target, reason, now,
    );
    // A scheduled Durable Object alarm purges metadata even when no new reports arrive.
    this.ctx.storage.sql.exec("DELETE FROM friend_reports WHERE created_at < ?", now - REPORT_RETENTION_MS);
    await this.#scheduleReportCleanup();
    return json(201, { ok: true, reported: true, replayed: false });
  }

  async #register(
    request,
  ) {
    const body =
      await readJson(
        request,
      );
    const clientRequestId =
      requireClientRequestId(
        body
          ?.clientRequestId,
      );
    const displayName =
      normalizeDisplayName(
        body
          ?.displayName,
      );
    const identity =
      await deriveFriendIdentity(
        this.env,
        clientRequestId,
      );
    const tokenHash =
      await friendTokenHash(
        identity
          .friendToken,
      );
    const registrationRequestHash = await sha256Hex(
      "ludoproof:friend-registration:v1:" + clientRequestId,
    );
    const deletedProfileRows = [...this.ctx.storage.sql.exec(
      "SELECT friend_id FROM deleted_friend_profiles WHERE friend_id = ? LIMIT 1",
      identity.friendId,
    )];
    const deletedRequestRows = [...this.ctx.storage.sql.exec(
      "SELECT request_hash FROM deleted_friend_registration_requests WHERE request_hash = ? LIMIT 1",
      registrationRequestHash,
    )];
    if (deletedProfileRows.length || deletedRequestRows.length) {
      throw httpError(410, "FRIEND_IDENTITY_DELETED", "this friend identity has been deleted");
    }
    const now =
      Date.now();

    const existingRows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT
              friend_id,
              registration_request_id
            FROM friend_profiles
            WHERE
              friend_id = ?
              OR registration_request_id = ?
              OR token_hash = ?
            LIMIT 1
          `,
          identity
            .friendId,
          clientRequestId,
          tokenHash,
        ),
    ];

    if (
      existingRows.length >
      0
    ) {
      const existing =
        existingRows[0];
      if (
        String(
          existing
            .friend_id,
        ) !==
          identity
            .friendId ||
        String(
          existing
            .registration_request_id,
        ) !==
          clientRequestId
      ) {
        throw httpError(
          409,
          "FRIEND_ID_CONFLICT",
          "friend identity could not be recovered",
        );
      }

      this.ctx.storage.sql
        .exec(
          `
            UPDATE friend_profiles
            SET
              token_hash = ?,
              display_name = ?,
              last_seen_at = ?
            WHERE friend_id = ?
          `,
          tokenHash,
          displayName,
          now,
          identity
            .friendId,
        );
    } else {
      this.ctx.storage.sql
        .exec(
          `
            INSERT INTO friend_profiles (
              friend_id,
              token_hash,
              registration_request_id,
              display_name,
              created_at,
              last_seen_at
            ) VALUES (?, ?, ?, ?, ?, ?)
          `,
          identity
            .friendId,
          tokenHash,
          clientRequestId,
          displayName,
          now,
          now,
        );
    }

    return json(
      200,
      {
        ok: true,
        friendId:
          identity
            .friendId,
        friendToken:
          identity
            .friendToken,
        displayName,
        online:
          true,
      },
    );
  }

  async #identity(
    request,
  ) {
    const me =
      await this.#authorize(
        request,
      );

    return json(
      200,
      {
        ok: true,
        friendId:
          me.friendId,
        displayName:
          me.displayName,
      },
    );
  }

  async #recoverAccountIdentity(request) {
    const body = await readJson(request);
    const friendId = normalizeFriendId(body?.friendId);
    const rows = [
      ...this.ctx.storage.sql.exec(
        "SELECT friend_id, registration_request_id, display_name FROM friend_profiles WHERE friend_id = ? LIMIT 1",
        friendId,
      ),
    ];
    if (rows.length !== 1) {
      throw httpError(404, "FRIEND_IDENTITY_NOT_FOUND", "linked friend identity could not be recovered");
    }
    const row = rows[0];
    const registrationRequestId = String(row.registration_request_id);
    const identity = await deriveFriendIdentity(this.env, registrationRequestId);
    if (identity.friendId !== friendId) {
      throw httpError(409, "FRIEND_IDENTITY_RECOVERY_CONFLICT", "linked friend identity did not match its recovery record");
    }
    const tokenHash = await friendTokenHash(identity.friendToken);
    this.ctx.storage.sql.exec(
      "UPDATE friend_profiles SET token_hash = ?, last_seen_at = ? WHERE friend_id = ?",
      tokenHash,
      Date.now(),
      friendId,
    );
    return json(200, {
      ok: true,
      friendId,
      friendToken: identity.friendToken,
      registrationRequestId,
      displayName: String(row.display_name),
    });
  }

  async #registerConversation(request) {
    const body = await readJson(request);
    const friendId = normalizeFriendId(body?.friendId), otherFriendId = normalizeFriendId(body?.otherFriendId);
    if (friendId === otherFriendId) throw httpError(400, "INVALID_CONVERSATION", "cannot index a conversation with yourself");
    const profiles = [...this.ctx.storage.sql.exec("SELECT friend_id FROM friend_profiles WHERE friend_id IN (?, ?)", friendId, otherFriendId)];
    if (profiles.length !== 2) throw httpError(404, "FRIEND_PROFILE_NOT_FOUND", "conversation participant was not found");
    this.#assertNotBlocked(friendId, otherFriendId);
    const now = Date.now();
    for (const [owner, other] of [[friendId, otherFriendId], [otherFriendId, friendId]]) this.ctx.storage.sql.exec(
      "INSERT INTO friend_conversation_index (friend_id, other_friend_id, last_message_at) VALUES (?, ?, ?) ON CONFLICT(friend_id, other_friend_id) DO UPDATE SET last_message_at = excluded.last_message_at", owner, other, now,
    );
    return json(200, { ok: true, registered: true });
  }

  async #registerRecent(request) {
    const body = await readJson(request);
    const owner = normalizeFriendId(body?.ownerFriendId), opponent = normalizeFriendId(body?.opponentFriendId);
    if (owner === opponent) throw httpError(400, "INVALID_RECENT_PLAYER", "recent opponent must be another player");
    if (this.#isBlocked(owner, opponent)) return json(200, { ok: true, registered: false });
    this.ctx.storage.sql.exec("INSERT INTO friend_recent_index (owner_friend_id, opponent_friend_id, played_at) VALUES (?, ?, ?) ON CONFLICT(owner_friend_id, opponent_friend_id) DO UPDATE SET played_at = excluded.played_at", owner, opponent, Date.now());
    return json(200, { ok: true, registered: true });
  }

  async #previewAccountDeletion(request) {
    const body = await readJson(request), friendId = normalizeFriendId(body?.friendId);
    const profile = [...this.ctx.storage.sql.exec("SELECT friend_id FROM friend_profiles WHERE friend_id = ? LIMIT 1", friendId)];
    if (!profile.length) return json(200, { ok: true, found: false, friendId, relatedFriendIds: [] });
    const related = new Set();
    const add = (a, b) => { const first = String(a ?? "").toUpperCase(), second = String(b ?? "").toUpperCase(); if (first === friendId && second && second !== friendId) related.add(second); if (second === friendId && first && first !== friendId) related.add(first); };
    for (const row of this.ctx.storage.sql.exec("SELECT friend_a, friend_b FROM friendships WHERE friend_a = ? OR friend_b = ?", friendId, friendId)) add(row.friend_a, row.friend_b);
    for (const row of this.ctx.storage.sql.exec("SELECT sender_id, receiver_id FROM friend_requests WHERE sender_id = ? OR receiver_id = ?", friendId, friendId)) add(row.sender_id, row.receiver_id);
    for (const row of this.ctx.storage.sql.exec("SELECT sender_id, receiver_id FROM friend_invites WHERE sender_id = ? OR receiver_id = ?", friendId, friendId)) add(row.sender_id, row.receiver_id);
    for (const row of this.ctx.storage.sql.exec("SELECT other_friend_id FROM friend_conversation_index WHERE friend_id = ?", friendId)) add(friendId, row.other_friend_id);
    for (const row of this.ctx.storage.sql.exec("SELECT owner_friend_id FROM friend_recent_index WHERE opponent_friend_id = ?", friendId)) add(row.owner_friend_id, friendId);
    for (const row of this.ctx.storage.sql.exec("SELECT blocker_id, blocked_id FROM friend_blocks WHERE blocker_id = ? OR blocked_id = ?", friendId, friendId)) add(row.blocker_id, row.blocked_id);
    return json(200, { ok: true, found: true, friendId, relatedFriendIds: [...related].sort() });
  }

  async #deleteAccount(request) {
    const body = await readJson(request), friendId = normalizeFriendId(body?.friendId);
    const requireInternalOk = async (response, code) => {
      let value; try { value = await response.json(); } catch { throw httpError(503, code, "internal social storage returned an invalid response"); }
      if (!response.ok) throw httpError(response.status, value?.error ?? code, value?.message ?? "internal social cleanup failed");
      return value;
    };
    const preview = await this.#previewAccountDeletion(new Request("https://friends/account/delete-preview", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ friendId }) })).then((response) => response.json());
    const related = Array.isArray(preview.relatedFriendIds) ? preview.relatedFriendIds : [];
    if (preview.found) {
      if (!this.env?.LUDOPROOF_FRIEND_CONVERSATIONS || !this.env?.LUDOPROOF_FRIEND_RECENT) throw httpError(503, "FRIEND_DATA_DELETE_NOT_CONFIGURED", "friend conversation and recent-player stores are required for account deletion");
      for (const otherId of related) {
        const pair = [friendId, otherId].sort().join(":");
        const conversation = this.env.LUDOPROOF_FRIEND_CONVERSATIONS.get(this.env.LUDOPROOF_FRIEND_CONVERSATIONS.idFromName(pair));
        await requireInternalOk(await conversation.fetch(new Request("https://conversation/delete-for-friend", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ friendId }) })), "FRIEND_CONVERSATION_DELETE_FAILED");
        const recent = this.env.LUDOPROOF_FRIEND_RECENT.get(this.env.LUDOPROOF_FRIEND_RECENT.idFromName(otherId));
        await requireInternalOk(await recent.fetch(new Request("https://recent/purge-opponent", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ ownerFriendId: otherId, opponentFriendId: friendId }) })), "FRIEND_RECENT_DELETE_FAILED");
      }
      const ownRecent = this.env.LUDOPROOF_FRIEND_RECENT.get(this.env.LUDOPROOF_FRIEND_RECENT.idFromName(friendId));
      await requireInternalOk(await ownRecent.fetch(new Request("https://recent/delete-owner", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ ownerFriendId: friendId }) })), "FRIEND_RECENT_DELETE_FAILED");
    }
    const profileRows = [...this.ctx.storage.sql.exec(
      "SELECT registration_request_id FROM friend_profiles WHERE friend_id = ? LIMIT 1",
      friendId,
    )];
    const registrationRequestHash = profileRows.length
      ? await sha256Hex("ludoproof:friend-registration:v1:" + String(profileRows[0].registration_request_id))
      : null;
    const deletedAt = Date.now();
    this.ctx.storage.transactionSync(() => {
      this.ctx.storage.sql.exec(
        "INSERT OR IGNORE INTO deleted_friend_profiles (friend_id, deleted_at) VALUES (?, ?)",
        friendId,
        deletedAt,
      );
      if (registrationRequestHash) {
        this.ctx.storage.sql.exec(
          "INSERT OR IGNORE INTO deleted_friend_registration_requests (request_hash, deleted_at) VALUES (?, ?)",
          registrationRequestHash,
          deletedAt,
        );
      }
      this.ctx.storage.sql.exec("DELETE FROM friend_requests WHERE sender_id = ? OR receiver_id = ?", friendId, friendId);
      this.ctx.storage.sql.exec("DELETE FROM friend_invites WHERE sender_id = ? OR receiver_id = ?", friendId, friendId);
      this.ctx.storage.sql.exec("DELETE FROM friendships WHERE friend_a = ? OR friend_b = ?", friendId, friendId);
      this.ctx.storage.sql.exec("DELETE FROM friend_conversation_index WHERE friend_id = ? OR other_friend_id = ?", friendId, friendId);
      this.ctx.storage.sql.exec("DELETE FROM friend_recent_index WHERE owner_friend_id = ? OR opponent_friend_id = ?", friendId, friendId);
      this.ctx.storage.sql.exec("DELETE FROM friend_blocks WHERE blocker_id = ? OR blocked_id = ?", friendId, friendId);
      this.ctx.storage.sql.exec("DELETE FROM friend_reports WHERE reporter_id = ? OR target_id = ?", friendId, friendId);
      this.ctx.storage.sql.exec("DELETE FROM friend_profiles WHERE friend_id = ?", friendId);
    });
    return json(200, { ok: true, deleted: preview.found === true, replay: preview.found !== true });
  }

  async #snapshot(
    request,
  ) {
    const me =
      await this.#authorize(
        request,
      );
    const now =
      Date.now();
    this.#expireInvites(
      now,
    );
    this.#touch(
      me.friendId,
      now,
    );

    const blockedFriends = [...this.ctx.storage.sql.exec(
      "SELECT blocked_id FROM friend_blocks WHERE blocker_id = ? ORDER BY created_at DESC",
      me.friendId,
    )].map((row) => String(row.blocked_id));
    const friends =
      this.#friendRows(
        me.friendId,
        now,
      );
    const incomingRequests =
      this.#requestRows(
        me.friendId,
        true,
      );
    const outgoingRequests =
      this.#requestRows(
        me.friendId,
        false,
      );
    const invites =
      this.#inviteRows(
        me.friendId,
        now,
      );

    return json(
      200,
      {
        friendId:
          me.friendId,
        displayName:
          me.displayName,
        online:
          true,
        presenceTtlMs:
          PRESENCE_TTL_MS,
        blockedFriends,
        friends,
        incomingRequests,
        outgoingRequests,
        invites,
      },
    );
  }

  async #heartbeat(
    request,
  ) {
    const me =
      await this.#authorize(
        request,
      );
    const body =
      await readJson(
        request,
      );
    const now =
      Date.now();

    let displayName =
      me.displayName;
    if (
      body
        ?.displayName !=
      null
    ) {
      displayName =
        normalizeDisplayName(
          body
            .displayName,
        );
    }

    this.ctx.storage.sql
      .exec(
        `
          UPDATE friend_profiles
          SET
            display_name = ?,
            last_seen_at = ?
          WHERE friend_id = ?
        `,
        displayName,
        now,
        me.friendId,
      );

    return json(
      200,
      {
        ok: true,
        friendId:
          me.friendId,
        displayName,
        onlineUntil:
          now +
          PRESENCE_TTL_MS,
      },
    );
  }

  async #sendRequest(
    request,
  ) {
    const me =
      await this.#authorize(
        request,
      );
    const body =
      await readJson(
        request,
      );
    const targetFriendId =
      normalizeFriendId(
        body
          ?.friendId,
      );

    if (
      targetFriendId ===
      me.friendId
    ) {
      throw httpError(
        409,
        "CANNOT_ADD_SELF",
        "you cannot send a friend request to yourself",
      );
    }

    this.#assertNotBlocked(me.friendId, targetFriendId);

    const target =
      this.#profile(
        targetFriendId,
      );
    if (!target) {
      throw httpError(
        404,
        "FRIEND_NOT_FOUND",
        "friend ID was not found",
      );
    }

    if (
      this.#areFriends(
        me.friendId,
        targetFriendId,
      )
    ) {
      return json(
        200,
        {
          ok: true,
          status:
            "ALREADY_FRIENDS",
          friend:
            publicProfile(
              target,
              Date.now(),
            ),
        },
      );
    }

    const reverseRows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT request_id
            FROM friend_requests
            WHERE
              sender_id = ?
              AND receiver_id = ?
              AND status = 'PENDING'
            LIMIT 1
          `,
          targetFriendId,
          me.friendId,
        ),
    ];

    if (
      reverseRows.length >
      0
    ) {
      return json(
        200,
        {
          ok: true,
          status:
            "INCOMING_PENDING",
          requestId:
            String(
              reverseRows[0]
                .request_id,
            ),
        },
      );
    }

    const requestId =
      await friendshipRequestId(
        me.friendId,
        targetFriendId,
      );
    const now =
      Date.now();

    this.ctx.storage.sql
      .exec(
        `
          INSERT INTO friend_requests (
            request_id,
            sender_id,
            receiver_id,
            status,
            created_at,
            updated_at
          ) VALUES (?, ?, ?, 'PENDING', ?, ?)
          ON CONFLICT(request_id) DO UPDATE SET
            status = 'PENDING',
            updated_at = excluded.updated_at
        `,
        requestId,
        me.friendId,
        targetFriendId,
        now,
        now,
      );

    this.#touch(
      me.friendId,
      now,
    );

    return json(
      200,
      {
        ok: true,
        status:
          "PENDING",
        requestId,
        friend:
          publicProfile(
            target,
            now,
          ),
      },
    );
  }

  async #respondRequest(
    request,
  ) {
    const me =
      await this.#authorize(
        request,
      );
    const body =
      await readJson(
        request,
      );
    const requestId =
      normalizeActionId(
        body
          ?.requestId,
        "FRQ",
      );
    const accept =
      body
        ?.accept ===
      true;

    const rows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT
              request_id,
              sender_id,
              receiver_id,
              status
            FROM friend_requests
            WHERE request_id = ?
            LIMIT 1
          `,
          requestId,
        ),
    ];

    if (
      rows.length !==
      1
    ) {
      throw httpError(
        404,
        "FRIEND_REQUEST_NOT_FOUND",
        "friend request was not found",
      );
    }

    const row =
      rows[0];
    if (
      String(
        row.receiver_id,
      ) !==
      me.friendId
    ) {
      throw httpError(
        403,
        "FRIEND_REQUEST_FORBIDDEN",
        "only the receiver can respond to this friend request",
      );
    }

    const senderId =
      String(
        row.sender_id,
      );
    if (accept) this.#assertNotBlocked(me.friendId, senderId);
    const now =
      Date.now();

    if (
      String(
        row.status,
      ) ===
      "PENDING"
    ) {
      this.ctx.storage
        .transactionSync(
          () => {
            this.ctx.storage.sql
              .exec(
                `
                  UPDATE friend_requests
                  SET
                    status = ?,
                    updated_at = ?
                  WHERE request_id = ?
                `,
                accept
                  ? "ACCEPTED"
                  : "DECLINED",
                now,
                requestId,
              );

            if (accept) {
              const [
                friendA,
                friendB,
              ] =
                canonicalPair(
                  me.friendId,
                  senderId,
                );
              this.ctx.storage.sql
                .exec(
                  `
                    INSERT OR IGNORE INTO friendships (
                      friend_a,
                      friend_b,
                      created_at
                    ) VALUES (?, ?, ?)
                  `,
                  friendA,
                  friendB,
                  now,
                );
            }
          },
        );
    }

    const sender =
      this.#profile(
        senderId,
      );

    return json(
      200,
      {
        ok: true,
        status:
          accept
            ? "ACCEPTED"
            : "DECLINED",
        friend:
          sender
            ? publicProfile(
                sender,
                now,
              )
            : null,
      },
    );
  }

  async #removeFriend(
    request,
  ) {
    const me =
      await this.#authorize(
        request,
      );
    const body =
      await readJson(
        request,
      );
    const targetFriendId =
      normalizeFriendId(
        body
          ?.friendId,
      );
    const [
      friendA,
      friendB,
    ] =
      canonicalPair(
        me.friendId,
        targetFriendId,
      );

    const now =
      Date.now();
    let removed =
      false;

    this.ctx.storage
      .transactionSync(
        () => {
          const result =
            this.ctx.storage.sql
              .exec(
                `
                  DELETE FROM friendships
                  WHERE
                    friend_a = ?
                    AND friend_b = ?
                `,
                friendA,
                friendB,
              );
          removed =
            Number(
              result.rowsWritten ??
                0,
            ) >
            0;

          this.ctx.storage.sql
            .exec(
              `
                UPDATE friend_invites
                SET
                  status = 'REVOKED',
                  updated_at = ?
                WHERE
                  status IN (
                    'PENDING',
                    'ACCEPTED'
                  )
                  AND (
                    (
                      sender_id = ?
                      AND receiver_id = ?
                    )
                    OR (
                      sender_id = ?
                      AND receiver_id = ?
                    )
                  )
              `,
              now,
              me.friendId,
              targetFriendId,
              targetFriendId,
              me.friendId,
            );
        },
      );

    return json(
      200,
      {
        ok: true,
        removed,
      },
    );
  }

  async #invite(
    request,
  ) {
    const me =
      await this.#authorize(
        request,
      );
    const body =
      await readJson(
        request,
      );
    const targetFriendId =
      normalizeFriendId(
        body
          ?.friendId,
      );
    const matchId =
      normalizeMatchId(
        body
          ?.matchId,
      );
    const clientRequestId =
      requireClientRequestId(
        body
          ?.clientRequestId,
      );

    await this.#assertFriendRoomHost(
      matchId,
      request.headers.get(
        "x-ludoproof-room-token",
      ),
      me.friendId,
    );

    if (
      !this.#areFriends(
        me.friendId,
        targetFriendId,
      )
    ) {
      throw httpError(
        403,
        "INVITE_REQUIRES_FRIEND",
        "private room invites can only be sent to friends",
      );
    }

    const now =
      Date.now();
    this.#expireInvites(
      now,
    );

    const pendingRows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT
              invite_id,
              expires_at
            FROM friend_invites
            WHERE
              sender_id = ?
              AND receiver_id = ?
              AND match_id = ?
              AND status = 'PENDING'
              AND expires_at > ?
            ORDER BY
              created_at DESC
            LIMIT 1
          `,
          me.friendId,
          targetFriendId,
          matchId,
          now,
        ),
    ];

    if (
      pendingRows.length >
      0
    ) {
      return json(
        200,
        {
          ok: true,
          inviteId:
            String(
              pendingRows[0]
                .invite_id,
            ),
          status:
            "PENDING",
          matchId,
          expiresAt:
            Number(
              pendingRows[0]
                .expires_at,
            ),
          replayed:
            true,
        },
      );
    }

    const inviteId =
      await friendInviteId(
        clientRequestId,
      );
    const expiresAt =
      now +
      INVITE_TTL_MS;

    const existingRows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT
              sender_id,
              receiver_id,
              match_id
            FROM friend_invites
            WHERE invite_id = ?
            LIMIT 1
          `,
          inviteId,
        ),
    ];

    if (
      existingRows.length >
      0
    ) {
      const existing =
        existingRows[0];
      if (
        String(
          existing.sender_id,
        ) !==
          me.friendId ||
        String(
          existing.receiver_id,
        ) !==
          targetFriendId ||
        String(
          existing.match_id,
        ) !==
          matchId
      ) {
        throw httpError(
          409,
          "INVITE_IDEMPOTENCY_CONFLICT",
          "invite request ID was already used for different data",
        );
      }
    } else {
      this.ctx.storage.sql
        .exec(
          `
            INSERT INTO friend_invites (
              invite_id,
              sender_id,
              receiver_id,
              match_id,
              status,
              created_at,
              updated_at,
              expires_at
            ) VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?)
          `,
          inviteId,
          me.friendId,
          targetFriendId,
          matchId,
          now,
          now,
          expiresAt,
        );
    }

    this.#touch(
      me.friendId,
      now,
    );

    return json(
      200,
      {
        ok: true,
        inviteId,
        status:
          "PENDING",
        matchId,
        expiresAt,
      },
    );
  }

  async #respondInvite(
    request,
  ) {
    const me =
      await this.#authorize(
        request,
      );
    const body =
      await readJson(
        request,
      );
    const inviteId =
      normalizeActionId(
        body
          ?.inviteId,
        "FIV",
      );
    const accept =
      body
        ?.accept ===
      true;
    const joinRequestId =
      accept
        ? requireClientRequestId(
            body
              ?.clientRequestId,
          )
        : null;
    const now =
      Date.now();

    this.#expireInvites(
      now,
    );

    const rows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT
              invite_id,
              sender_id,
              receiver_id,
              match_id,
              status,
              updated_at,
              expires_at
            FROM friend_invites
            WHERE invite_id = ?
            LIMIT 1
          `,
          inviteId,
        ),
    ];

    if (
      rows.length !==
      1
    ) {
      throw httpError(
        404,
        "INVITE_NOT_FOUND",
        "friend invite was not found",
      );
    }

    const row =
      rows[0];
    if (
      String(
        row.receiver_id,
      ) !==
      me.friendId
    ) {
      throw httpError(
        403,
        "INVITE_FORBIDDEN",
        "only the invited friend can respond",
      );
    }

    const status =
      String(
        row.status,
      );

    if (
      status ===
        "EXPIRED" ||
      status ===
        "REVOKED"
    ) {
      throw httpError(
        410,
        status ===
          "REVOKED"
          ? "INVITE_REVOKED"
          : "INVITE_EXPIRED",
        status ===
          "REVOKED"
          ? "friend invite was revoked"
          : "friend invite expired",
      );
    }

    if (
      status !==
        "PENDING"
    ) {
      const expectedStatus =
        accept
          ? "ACCEPTED"
          : "DECLINED";
      if (
        status !==
        expectedStatus
      ) {
        throw httpError(
          409,
          "INVITE_ALREADY_RESPONDED",
          "friend invite was already answered",
        );
      }

      if (
        status ===
          "ACCEPTED"
      ) {
        const senderId =
          String(
            row.sender_id,
          );
        if (
          !this.#areFriends(
            senderId,
            me.friendId,
          )
        ) {
          throw httpError(
            410,
            "INVITE_REVOKED",
            "friend invite is no longer valid",
          );
        }

        const joinTokenExpiresAt =
          Math.min(
            Number(
              row.expires_at,
            ),
            now +
              JOIN_TOKEN_TTL_MS,
          );
        const friendJoinToken =
          await issueFriendRoomJoinToken(
            this.env,
            {
              matchId:
                String(
                  row.match_id,
                ),
              hostFriendId:
                senderId,
              friendId:
                me.friendId,
              inviteId,
              expiresAt:
                joinTokenExpiresAt,
            },
          );

        return json(
          200,
          {
            ok: true,
            status,
            matchId:
              String(
                row.match_id,
              ),
            friendJoinToken,
            joinTokenExpiresAt,
            joinRequestId,
            replayed:
              true,
          },
        );
      }

      return json(
        200,
        {
          ok: true,
          status,
          matchId:
            null,
          replayed:
            true,
        },
      );
    }

    if (
      status ===
      "PENDING"
    ) {
      this.ctx.storage.sql
        .exec(
          `
            UPDATE friend_invites
            SET
              status = ?,
              updated_at = ?
            WHERE invite_id = ?
          `,
          accept
            ? "ACCEPTED"
            : "DECLINED",
          now,
          inviteId,
        );
    }

    this.#touch(
      me.friendId,
      now,
    );

    if (
      accept
    ) {
      const senderId =
        String(
          row.sender_id,
        );
      if (
        !this.#areFriends(
          senderId,
          me.friendId,
        )
      ) {
        throw httpError(
          410,
          "INVITE_REVOKED",
          "friend invite is no longer valid",
        );
      }

      const joinTokenExpiresAt =
        Math.min(
          Number(
            row.expires_at,
          ),
          now +
            JOIN_TOKEN_TTL_MS,
        );
      const friendJoinToken =
        await issueFriendRoomJoinToken(
          this.env,
          {
            matchId:
              String(
                row.match_id,
              ),
            hostFriendId:
              senderId,
            friendId:
              me.friendId,
            inviteId,
            expiresAt:
              joinTokenExpiresAt,
          },
        );

      return json(
        200,
        {
          ok: true,
          status:
            "ACCEPTED",
          matchId:
            String(
              row.match_id,
            ),
          friendJoinToken,
          joinTokenExpiresAt,
          joinRequestId,
        },
      );
    }

    return json(
      200,
      {
        ok: true,
        status:
          "DECLINED",
        matchId:
          null,
      },
    );
  }

  async #checkInviteForJoin(
    request,
  ) {
    const body =
      await readJson(
        request,
      );
    const inviteId =
      normalizeActionId(
        body
          ?.inviteId,
        "FIV",
      );
    const matchId =
      normalizeMatchId(
        body
          ?.matchId,
      );
    const hostFriendId =
      normalizeFriendId(
        body
          ?.hostFriendId,
      );
    const friendId =
      normalizeFriendId(
        body
          ?.friendId,
      );
    const now =
      Date.now();

    this.#expireInvites(
      now,
    );

    const rows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT
              sender_id,
              receiver_id,
              match_id,
              status,
              expires_at
            FROM friend_invites
            WHERE invite_id = ?
            LIMIT 1
          `,
          inviteId,
        ),
    ];

    if (
      rows.length !==
      1
    ) {
      throw httpError(
        404,
        "INVITE_NOT_FOUND",
        "friend invite was not found",
      );
    }

    const row =
      rows[0];
    const status =
      String(
        row.status,
      );
    if (
      status ===
        "EXPIRED" ||
      status ===
        "REVOKED"
    ) {
      throw httpError(
        410,
        status ===
          "REVOKED"
          ? "INVITE_REVOKED"
          : "INVITE_EXPIRED",
        status ===
          "REVOKED"
          ? "friend invite was revoked"
          : "friend invite expired",
      );
    }
    if (
      status !==
        "ACCEPTED"
    ) {
      throw httpError(
        403,
        "INVITE_NOT_ACCEPTED",
        "friend invite must be accepted before joining",
      );
    }
    if (
      String(
        row.sender_id,
      ) !==
        hostFriendId ||
      String(
        row.receiver_id,
      ) !==
        friendId ||
      String(
        row.match_id,
      ) !==
        matchId
    ) {
      throw httpError(
        403,
        "INVITE_JOIN_MISMATCH",
        "friend-room join credential does not match this invite",
      );
    }
    if (
      Number(
        row.expires_at,
      ) <= now ||
      !this.#areFriends(
        hostFriendId,
        friendId,
      )
    ) {
      throw httpError(
        410,
        "INVITE_REVOKED",
        "friend invite is no longer valid",
      );
    }

    return json(
      200,
      {
        ok: true,
        inviteId,
        matchId,
        hostFriendId,
        friendId,
      },
    );
  }

  async #assertFriendRoomHost(
    matchId,
    roomToken,
    hostFriendId,
  ) {
    if (
      typeof roomToken !==
        "string" ||
      !/^lp_[A-Za-z0-9_-]{32,}$/
        .test(
          roomToken,
        )
    ) {
      throw httpError(
        401,
        "ROOM_AUTH_REQUIRED",
        "private room host credential is required",
      );
    }

    if (
      !this.env
        .LUDOPROOF_MATCHES
    ) {
      throw httpError(
        503,
        "MATCH_STORE_NOT_CONFIGURED",
        "match storage is not configured",
      );
    }

    const id =
      this.env
        .LUDOPROOF_MATCHES
        .idFromName(
          matchId,
        );
    const target =
      this.env
        .LUDOPROOF_MATCHES
        .get(
          id,
        );
    const response =
      await target.fetch(
        new Request(
          "https://room/friend-host/assert",
          {
            method:
              "POST",
            headers: {
              authorization:
                "Bearer " +
                roomToken,
              "content-type":
                "application/json",
            },
            body:
              JSON.stringify({
                hostFriendId,
              }),
          },
        ),
      );

    let body;
    try {
      body =
        await response.json();
    } catch {
      throw httpError(
        503,
        "ROOM_AUTH_BAD_RESPONSE",
        "private room authorization returned invalid data",
      );
    }

    if (
      !response.ok ||
      body
        ?.ok !==
        true
    ) {
      throw httpError(
        response.status ===
          401
          ? 401
          : response.status,
        body
          ?.error ??
          "ROOM_AUTH_INVALID",
        body
          ?.message ??
          "private room host credential is invalid",
      );
    }
  }

  async #authorize(
    request,
  ) {
    const token =
      bearerToken(
        request,
      );
    if (
      !FRIEND_TOKEN.test(
        token,
      )
    ) {
      throw httpError(
        401,
        "FRIEND_AUTH_INVALID",
        "friend credential is invalid",
      );
    }

    const tokenHash =
      await friendTokenHash(
        token,
      );
    const rows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT
              friend_id,
              display_name,
              last_seen_at
            FROM friend_profiles
            WHERE token_hash = ?
            LIMIT 1
          `,
          tokenHash,
        ),
    ];

    if (
      rows.length !==
      1
    ) {
      throw httpError(
        401,
        "FRIEND_AUTH_INVALID",
        "friend credential is invalid",
      );
    }

    return {
      friendId:
        String(
          rows[0]
            .friend_id,
        ),
      displayName:
        String(
          rows[0]
            .display_name,
        ),
      lastSeenAt:
        Number(
          rows[0]
            .last_seen_at,
        ),
    };
  }

  #profile(
    friendId,
  ) {
    const rows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT
              friend_id,
              display_name,
              last_seen_at
            FROM friend_profiles
            WHERE friend_id = ?
            LIMIT 1
          `,
          friendId,
        ),
    ];
    return rows[0] ??
      null;
  }

  #friendRows(
    friendId,
    now,
  ) {
    const rows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT
              p.friend_id,
              p.display_name,
              p.last_seen_at,
              f.created_at
            FROM friendships AS f
            JOIN friend_profiles AS p
              ON p.friend_id = CASE
                WHEN f.friend_a = ? THEN f.friend_b
                ELSE f.friend_a
              END
            WHERE
              f.friend_a = ?
              OR f.friend_b = ?
            ORDER BY
              p.last_seen_at DESC,
              p.display_name COLLATE NOCASE ASC,
              p.friend_id ASC
          `,
          friendId,
          friendId,
          friendId,
        ),
    ];

    return rows.map(
      (row) => ({
        ...publicProfile(
          row,
          now,
        ),
        friendsSince:
          Number(
            row.created_at,
          ),
      }),
    );
  }

  #requestRows(
    friendId,
    incoming,
  ) {
    const ownColumn =
      incoming
        ? "receiver_id"
        : "sender_id";
    const otherColumn =
      incoming
        ? "sender_id"
        : "receiver_id";

    const rows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT
              r.request_id,
              r.created_at,
              r.updated_at,
              p.friend_id,
              p.display_name,
              p.last_seen_at
            FROM friend_requests AS r
            JOIN friend_profiles AS p
              ON p.friend_id = r.${otherColumn}
            WHERE
              r.${ownColumn} = ?
              AND r.status = 'PENDING'
            ORDER BY
              r.updated_at DESC,
              r.request_id ASC
          `,
          friendId,
        ),
    ];
    const now =
      Date.now();

    return rows.map(
      (row) => ({
        requestId:
          String(
            row.request_id,
          ),
        createdAt:
          Number(
            row.created_at,
          ),
        updatedAt:
          Number(
            row.updated_at,
          ),
        friend:
          publicProfile(
            row,
            now,
          ),
      }),
    );
  }

  #inviteRows(
    friendId,
    now,
  ) {
    const rows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT
              i.invite_id,
              i.match_id,
              i.created_at,
              i.expires_at,
              p.friend_id,
              p.display_name,
              p.last_seen_at
            FROM friend_invites AS i
            JOIN friend_profiles AS p
              ON p.friend_id = i.sender_id
            WHERE
              i.receiver_id = ?
              AND i.status = 'PENDING'
              AND i.expires_at > ?
            ORDER BY
              i.created_at DESC,
              i.invite_id ASC
          `,
          friendId,
          now,
        ),
    ];

    return rows.map(
      (row) => ({
        inviteId:
          String(
            row.invite_id,
          ),
        matchId:
          String(
            row.match_id,
          ),
        createdAt:
          Number(
            row.created_at,
          ),
        expiresAt:
          Number(
            row.expires_at,
          ),
        from:
          publicProfile(
            row,
            now,
          ),
      }),
    );
  }

  #areFriends(
    first,
    second,
  ) {
    if (this.#isBlocked(first, second)) return false;
    const [
      friendA,
      friendB,
    ] =
      canonicalPair(
        first,
        second,
      );
    const rows = [
      ...this.ctx.storage.sql
        .exec(
          `
            SELECT 1
            FROM friendships
            WHERE
              friend_a = ?
              AND friend_b = ?
            LIMIT 1
          `,
          friendA,
          friendB,
        ),
    ];
    return rows.length ===
      1;
  }

  #touch(
    friendId,
    now,
  ) {
    this.ctx.storage.sql
      .exec(
        `
          UPDATE friend_profiles
          SET last_seen_at = ?
          WHERE friend_id = ?
        `,
        now,
        friendId,
      );
  }

  #expireInvites(
    now,
  ) {
    this.ctx.storage.sql
      .exec(
        `
          UPDATE friend_invites
          SET
            status = 'EXPIRED',
            updated_at = ?
          WHERE
            status IN (
              'PENDING',
              'ACCEPTED'
            )
            AND expires_at <= ?
        `,
        now,
        now,
      );
  }
}

function normalizeFriendId(
  value,
) {
  const normalized =
    String(
      value ??
        "",
    )
      .trim()
      .toUpperCase();
  if (
    !FRIEND_ID.test(
      normalized,
    )
  ) {
    throw httpError(
      400,
      "INVALID_FRIEND_ID",
      "Friend ID must look like LPF-ABCD-EFGH-JKLM",
    );
  }
  return normalized;
}

function normalizeMatchId(
  value,
) {
  const normalized =
    String(
      value ??
        "",
    )
      .trim()
      .toUpperCase();
  if (
    !MATCH_ID.test(
      normalized,
    )
  ) {
    throw httpError(
      400,
      "INVALID_MATCH_ID",
      "invalid LudoProof match ID",
    );
  }
  return normalized;
}

function normalizeActionId(
  value,
  prefix,
) {
  const normalized =
    String(
      value ??
        "",
    )
      .trim()
      .toUpperCase();
  const pattern =
    new RegExp(
      "^" +
        prefix +
        "-[A-F0-9]{20}$",
    );
  if (
    !pattern.test(
      normalized,
    )
  ) {
    throw httpError(
      400,
      "INVALID_ACTION_ID",
      "invalid friend action ID",
    );
  }
  return normalized;
}

function canonicalPair(
  first,
  second,
) {
  return first <
    second
    ? [
        first,
        second,
      ]
    : [
        second,
        first,
      ];
}

function publicProfile(
  row,
  now,
) {
  const lastSeenAt =
    Number(
      row.last_seen_at ??
        0,
    );
  return {
    friendId:
      String(
        row.friend_id,
      ),
    displayName:
      String(
        row.display_name,
      ),
    online:
      lastSeenAt +
        PRESENCE_TTL_MS >
      now,
    lastSeenAt,
  };
}

async function friendshipRequestId(
  senderId,
  receiverId,
) {
  const digest =
    await sha256Hex(
      "ludoproof:friend-request:v1:" +
        senderId +
        ":" +
        receiverId,
    );
  return (
    "FRQ-" +
    digest
      .slice(
        0,
        20,
      )
      .toUpperCase()
  );
}

async function friendInviteId(
  clientRequestId,
) {
  const digest =
    await sha256Hex(
      "ludoproof:friend-invite:v1:" +
        clientRequestId,
    );
  return (
    "FIV-" +
    digest
      .slice(
        0,
        20,
      )
      .toUpperCase()
  );
}

async function friendTokenHash(
  token,
) {
  return sha256Hex(
    "ludoproof:friend-token-hash:v1:" +
      token,
  );
}

async function readJson(
  request,
) {
  const type =
    request.headers.get(
      "content-type",
    ) ??
    "";
  if (
    !type
      .toLowerCase()
      .startsWith(
        "application/json",
      )
  ) {
    throw httpError(
      415,
      "UNSUPPORTED_MEDIA_TYPE",
      "content-type must be application/json",
    );
  }

  const text =
    await request.text();
  if (
    new TextEncoder()
      .encode(
        text,
      )
      .byteLength >
    16 * 1024
  ) {
    throw httpError(
      413,
      "REQUEST_TOO_LARGE",
      "request body is too large",
    );
  }

  try {
    const value =
      text.length ===
      0
        ? {}
        : JSON.parse(
            text,
          );
    if (
      !value ||
      typeof value !==
        "object" ||
      Array.isArray(
        value,
      )
    ) {
      throw new Error(
        "object required",
      );
    }
    return value;
  } catch {
    throw httpError(
      400,
      "INVALID_JSON",
      "request body must be valid JSON",
    );
  }
}

function json(
  status,
  body,
) {
  return new Response(
    JSON.stringify(
      body,
    ),
    {
      status,
      headers: {
        "content-type":
          "application/json; charset=utf-8",
        "cache-control":
          "no-store",
        "x-content-type-options":
          "nosniff",
      },
    },
  );
}

function errorResponse(
  error,
) {
  const status =
    Number.isInteger(
      error
        ?.status,
    )
      ? error.status
      : 500;

  return json(
    status,
    {
      error:
        error
          ?.code ??
        "INTERNAL_ERROR",
      message:
        status ===
          500 &&
        !error
          ?.code
          ? "internal server error"
          : String(
              error
                ?.message ??
                "internal server error",
            ),
    },
  );
}
