import { httpError, readJsonObject, sha256Hex } from "./crypto.js";
import { DAY, REFERRAL_CAMPAIGN, REFERRAL_ORIGIN, REFERRAL_REWARD, REFERRAL_TERMS_VERSION, referralsConfigured, referralsEnabled } from "./referral-config.js";
import { verifyReferralIntegrity } from "./referral-integrity.js";

const opaque = () => crypto.randomUUID().replaceAll("-", "") + crypto.randomUUID().replaceAll("-", "");
const json = (status, body) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
const fail = (code, message) => { throw httpError(409, code, message); };

async function internal(namespace, name, path, body) {
  const response = await namespace.get(namespace.idFromName(name)).fetch(new Request("https://internal" + path, {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body), signal: AbortSignal.timeout(10_000),
  }));
  const result = await response.json();
  if (!response.ok) throw httpError(response.status, result.error ?? "REFERRAL_SERVICE_UNAVAILABLE", result.message ?? "Referral service is temporarily unavailable.");
  return result;
}

export class ReferralCampaign {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    ctx.blockConcurrencyWhile(async () => {
      const sql = ctx.storage.sql;
      sql.exec("CREATE TABLE IF NOT EXISTS invites (account_id TEXT PRIMARY KEY, token TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS clicks (token TEXT PRIMARY KEY, inviter TEXT NOT NULL, clicked_at INTEGER NOT NULL, consumed INTEGER NOT NULL DEFAULT 0)");
      sql.exec("CREATE TABLE IF NOT EXISTS challenges (token TEXT PRIMARY KEY, account_id TEXT NOT NULL, click TEXT NOT NULL, install_at INTEGER NOT NULL, request_hash TEXT NOT NULL, expires_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS claims (account_id TEXT PRIMARY KEY, subject_hash TEXT NOT NULL UNIQUE, inviter TEXT NOT NULL, install_at INTEGER NOT NULL, deadline INTEGER NOT NULL, matched_at INTEGER, match_id TEXT)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_claim_inviter ON claims(inviter)");
      sql.exec("CREATE TABLE IF NOT EXISTS awards (inviter TEXT PRIMARY KEY, status TEXT NOT NULL, created_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS deleted_accounts (account_id TEXT PRIMARY KEY)");
      sql.exec("CREATE TABLE IF NOT EXISTS used_subjects (subject_hash TEXT PRIMARY KEY, expires_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS qualifying_matches (account_id TEXT NOT NULL, match_id TEXT NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER NOT NULL, PRIMARY KEY(account_id, match_id))");
      sql.exec("CREATE TABLE IF NOT EXISTS participation (account_id TEXT PRIMARY KEY, terms_version TEXT NOT NULL, accepted_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS delivery_attempts (inviter TEXT PRIMARY KEY, attempts INTEGER NOT NULL, next_at INTEGER NOT NULL, last_error TEXT)");
      sql.exec("CREATE TABLE IF NOT EXISTS referral_limits (action TEXT PRIMARY KEY, started_at INTEGER NOT NULL, count INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS campaign_meta (id INTEGER PRIMARY KEY CHECK(id = 1), start_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS subject_history (subject_hash TEXT PRIMARY KEY, first_account_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_click_inviter ON clicks(inviter, clicked_at)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_match_expiry ON qualifying_matches(finished_at)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_subject_expiry ON used_subjects(expires_at)");
    });
  }

  async fetch(request) {
    try {
      const path = new URL(request.url).pathname;
      if (request.method !== "POST") return json(405, { error: "METHOD_NOT_ALLOWED" });
      const body = await readJsonObject(request, 24 * 1024);
      if (path === "/delete") return await this.deleteAccount(body.accountId);
      if (path === "/maintenance") { await this.alarm(); return json(200, { ok: true }); }
      if (path === "/health") return json(200, { ok: true,
        configurationMatches: !this.rows("SELECT start_at FROM campaign_meta WHERE id = 1").length || Number(this.rows("SELECT start_at FROM campaign_meta WHERE id = 1")[0].start_at) === Number(this.env.LUDOPAWS_REFERRAL_START_MS),
        pending: Number(this.rows("SELECT COUNT(*) AS total FROM awards WHERE status = 'PENDING'")[0].total),
        oldestPendingAt: this.rows("SELECT MIN(created_at) AS oldest FROM awards WHERE status = 'PENDING'")[0].oldest,
        failedDeliveries: Number(this.rows("SELECT COUNT(*) AS total FROM delivery_attempts WHERE last_error IS NOT NULL")[0].total),
      });
      if (path === "/withdraw") {
        await this.identity(body.accountId);
        this.ctx.storage.transactionSync(() => {
          this.ctx.storage.sql.exec("DELETE FROM participation WHERE account_id = ?", body.accountId);
          this.ctx.storage.sql.exec("DELETE FROM challenges WHERE account_id = ?", body.accountId);
          this.ctx.storage.sql.exec("DELETE FROM claims WHERE account_id = ? AND matched_at IS NULL", body.accountId);
          this.ctx.storage.sql.exec("DELETE FROM qualifying_matches WHERE account_id = ?", body.accountId);
        });
        return json(200, { ok: true });
      }
      if (!(path === "/status" || path === "/match" ? referralsConfigured(this.env) : referralsEnabled(this.env))) return json(503, { error: "REFERRALS_UNAVAILABLE", message: "Friendship rewards are not available yet." });
      const start = Number(this.env.LUDOPAWS_REFERRAL_START_MS);
      this.ctx.storage.sql.exec("INSERT OR IGNORE INTO campaign_meta (id, start_at) VALUES (1, ?)", start);
      if (Number(this.rows("SELECT start_at FROM campaign_meta WHERE id = 1")[0].start_at) !== start) throw httpError(503, "CAMPAIGN_CONFIGURATION_CHANGED", "Campaign dates changed unexpectedly. Contact support.");
      if (path === "/click") return await this.click(body);
      if (path === "/match") return await this.recordMatch(body);
      const identity = await this.identity(body.accountId);
      if (path === "/status") this.limit("status:" + body.accountId, 60);
      if (path === "/invite" || path === "/enroll") this.limit(path + ":" + body.accountId, 6);
      if (path === "/status") {
        try { await this.deliver(body.accountId); } catch { }
        const award = this.rows("SELECT status FROM awards WHERE inviter = ?", body.accountId)[0];
        const invite = this.rows("SELECT token FROM invites WHERE account_id = ?", body.accountId)[0];
        const counts = this.rows("SELECT COUNT(*) AS total FROM claims WHERE inviter = ? AND (deadline > ? OR matched_at IS NOT NULL)", body.accountId, Date.now())[0];
        return json(200, { ok: true, enabled: referralsEnabled(this.env), campaign: REFERRAL_CAMPAIGN, reward: REFERRAL_REWARD,
          termsVersion: REFERRAL_TERMS_VERSION, consented: this.participating(body.accountId),
          attribution: this.rows("SELECT deadline, matched_at FROM claims WHERE account_id = ?", body.accountId).map(row => ({ state: row.matched_at ? "QUALIFIED" : "BOUND", deadline: Number(row.deadline) }))[0] ?? null,
          googleLinked: Boolean(identity.subjectHash), state: award?.status === "DELIVERED" ? "REWARDED" : award ? "VERIFYING" : Number(counts.total) > 0 ? "FRIEND_JOINED" : "INVITE",
          shareUrl: invite ? REFERRAL_ORIGIN + "/r/" + invite.token : null,
          endsAt: Number(this.env.LUDOPAWS_REFERRAL_START_MS) + 90 * DAY });
      }
      if (!identity.subjectHash) fail("GOOGLE_LINK_REQUIRED", "Link your Google account in Settings to use friendship rewards.");
      if (path === "/enroll") {
        if (body.termsVersion !== REFERRAL_TERMS_VERSION || body.consent !== true) fail("TERMS_ACCEPTANCE_REQUIRED", "Read and accept the current invitation terms first.");
        if (this.rows("SELECT account_id FROM deleted_accounts WHERE account_id = ?", body.accountId).length) fail("ACCOUNT_DELETED", "This account is unavailable.");
        this.ctx.storage.sql.exec("INSERT INTO participation (account_id, terms_version, accepted_at) VALUES (?, ?, ?) ON CONFLICT(account_id) DO UPDATE SET terms_version = excluded.terms_version, accepted_at = excluded.accepted_at", body.accountId, REFERRAL_TERMS_VERSION, Date.now());
        this.ctx.storage.sql.exec("INSERT INTO subject_history (subject_hash, first_account_at, expires_at) VALUES (?, ?, ?) ON CONFLICT(subject_hash) DO UPDATE SET first_account_at = MIN(first_account_at, excluded.first_account_at)", identity.subjectHash, identity.createdAt, Number(this.env.LUDOPAWS_REFERRAL_START_MS) + 104 * DAY);
        await this.schedule();
        return json(200, { ok: true });
      }
      if (!this.participating(body.accountId)) fail("TERMS_ACCEPTANCE_REQUIRED", "Read and accept the invitation terms before participating.");
      if (path === "/invite") {
        if (this.rows("SELECT inviter FROM awards WHERE inviter = ?", body.accountId).length) fail("BUNDLE_ALREADY_QUALIFIED", "This account has already qualified for its one-time bundle.");
        const now = Date.now();
        this.ctx.storage.sql.exec("INSERT OR IGNORE INTO invites (account_id, token, created_at) VALUES (?, ?, ?)", body.accountId, opaque(), now);
        const invite = this.rows("SELECT token FROM invites WHERE account_id = ?", body.accountId)[0];
        await this.schedule();
        return json(200, { ok: true, shareUrl: REFERRAL_ORIGIN + "/r/" + invite.token });
      }
      if (path === "/challenge") return await this.challenge(body, identity);
      if (path === "/bind") return await this.bind(body, identity);
      return json(404, { error: "NOT_FOUND" });
    } catch (error) {
      return json(Number.isInteger(error?.status) ? error.status : 500, {
        error: error?.code ?? "REFERRAL_SERVICE_UNAVAILABLE",
        message: Number.isInteger(error?.status) ? error.message : "Friendship rewards are temporarily unavailable. Please retry later.",
      });
    }
  }

  rows(query, ...args) { return [...this.ctx.storage.sql.exec(query, ...args)]; }

  participating(accountId) { return this.rows("SELECT account_id FROM participation WHERE account_id = ? AND terms_version = ?", accountId, REFERRAL_TERMS_VERSION).length === 1; }

  limit(action, maximum, window = 60_000) {
    this.ctx.storage.transactionSync(() => {
      const now = Date.now();
      const prior = this.rows("SELECT started_at, count FROM referral_limits WHERE action = ?", action)[0];
      if (prior && Number(prior.started_at) + window > now && Number(prior.count) >= maximum) throw httpError(429, "REFERRAL_RATE_LIMIT", "Too many attempts. Please try again later.");
      if (!prior || Number(prior.started_at) + window <= now) this.ctx.storage.sql.exec("INSERT OR REPLACE INTO referral_limits (action, started_at, count) VALUES (?, ?, 1)", action, now);
      else this.ctx.storage.sql.exec("UPDATE referral_limits SET count = count + 1 WHERE action = ?", action);
    });
  }

  async identity(accountId) {
    if (typeof accountId !== "string" || !/^[a-f0-9-]{36}$/.test(accountId)) fail("INVALID_ACCOUNT", "Reconnect your account.");
    if (this.rows("SELECT account_id FROM deleted_accounts WHERE account_id = ?", accountId).length) fail("ACCOUNT_DELETED", "This account is unavailable.");
    const identity = await internal(this.env.LUDOPROOF_ACCOUNT_DIRECTORY, "GLOBAL:V1", "/referral/identity", { accountId });
    if (this.rows("SELECT account_id FROM deleted_accounts WHERE account_id = ?", accountId).length) fail("ACCOUNT_DELETED", "This account is unavailable.");
    return identity;
  }

  async click(body) {
    if (!/^[a-f0-9]{64}$/.test(String(body.token ?? ""))) fail("INVITE_INVALID", "This invitation is unavailable.");
    const invite = this.rows("SELECT account_id FROM invites WHERE token = ?", body.token)[0];
    if (!invite) fail("INVITE_INVALID", "This invitation is unavailable.");
    await this.identity(invite.account_id);
    if (!this.participating(invite.account_id) || this.rows("SELECT inviter FROM awards WHERE inviter = ?", invite.account_id).length) return json(200, { ok: true, referrer: null });
    this.limit("click:" + invite.account_id, 200, DAY);
    const token = opaque();
    this.ctx.storage.sql.exec("INSERT INTO clicks (token, inviter, clicked_at) VALUES (?, ?, ?)", token, invite.account_id, Date.now());
    await this.schedule();
    return json(200, { ok: true, referrer: "lp_ref=" + token });
  }

  async challenge(body, identity) {
    this.limit("challenge:" + body.accountId, 4);
    const click = this.rows("SELECT * FROM clicks WHERE token = ?", String(body.click ?? ""))[0];
    const install = Number(body.installAt);
    const clicked = Number(body.clickedAt);
    if (this.rows("SELECT account_id FROM claims WHERE account_id = ?", body.accountId).length) return json(200, { ok: true, bound: true });
    if (!click || click.consumed || click.inviter === body.accountId || !Number.isSafeInteger(install) || !Number.isSafeInteger(clicked) ||
        clicked < click.clicked_at - 60_000 || clicked > click.clicked_at + 60 * 60_000 || install < clicked ||
        install < click.clicked_at - 2000 || install > click.clicked_at + 7 * DAY || install > Date.now() + 60_000 ||
        Date.now() > install + 14 * DAY || identity.createdAt < install - 60_000 || identity.createdAt < click.clicked_at ||
        Number(this.rows("SELECT first_account_at FROM subject_history WHERE subject_hash = ?", identity.subjectHash)[0]?.first_account_at ?? identity.createdAt) < install - 60_000 ||
        this.rows("SELECT subject_hash FROM used_subjects WHERE subject_hash = ? AND expires_at > ?", identity.subjectHash, Date.now()).length) {
      fail("REFERRAL_NOT_ELIGIBLE", "Only a new friend installing from a valid invitation can qualify.");
    }
    const inviter = await this.identity(click.inviter);
    if (!inviter.subjectHash || inviter.subjectHash === identity.subjectHash) fail("SELF_REFERRAL", "You cannot invite your own account.");
    if (!this.participating(body.accountId) || !this.participating(click.inviter)) fail("TERMS_ACCEPTANCE_REQUIRED", "Both participants must accept the current invitation terms.");
    if (!referralsEnabled(this.env) || this.rows("SELECT account_id FROM deleted_accounts WHERE account_id IN (?, ?)", body.accountId, click.inviter).length) fail("REFERRAL_NOT_ELIGIBLE", "This invitation is unavailable.");
    const token = opaque();
    const requestHash = await sha256Hex([REFERRAL_CAMPAIGN, REFERRAL_TERMS_VERSION, body.accountId, body.click, install, token].join(":"));
    this.ctx.storage.sql.exec("DELETE FROM challenges WHERE account_id = ? OR expires_at < ?", body.accountId, Date.now());
    this.ctx.storage.sql.exec("INSERT INTO challenges (token, account_id, click, install_at, request_hash, expires_at) VALUES (?, ?, ?, ?, ?, ?)", token, body.accountId, body.click, install, requestHash, Date.now() + 5 * 60_000);
    await this.schedule();
    return json(200, { ok: true, challenge: token, requestHash, cloudProjectNumber: String(this.env.LUDOPAWS_INTEGRITY_PROJECT_NUMBER) });
  }

  async bind(body, identity) {
    this.limit("bind:" + body.accountId, 6);
    if (this.rows("SELECT account_id FROM claims WHERE account_id = ?", body.accountId).length) return json(200, { ok: true, bound: true });
    const challenge = this.rows("SELECT * FROM challenges WHERE token = ? AND account_id = ? AND expires_at > ?", String(body.challenge ?? ""), body.accountId, Date.now())[0];
    if (!challenge) fail("CHALLENGE_EXPIRED", "Retry installation verification.");
    this.ctx.storage.sql.exec("DELETE FROM challenges WHERE token = ?", challenge.token);
    await verifyReferralIntegrity(this.env, body.integrityToken, challenge.request_hash);
    const freshIdentity = await this.identity(body.accountId);
    if (freshIdentity.subjectHash !== identity.subjectHash) fail("ACCOUNT_CHANGED", "Reconnect your account.");
    const inviterId = this.rows("SELECT inviter FROM clicks WHERE token = ?", challenge.click)[0]?.inviter;
    if (!inviterId) fail("REFERRAL_NOT_ELIGIBLE", "This invitation is unavailable.");
    const inviter = await this.identity(inviterId);
    if (!inviter.subjectHash || inviter.subjectHash === identity.subjectHash) fail("SELF_REFERRAL", "You cannot invite your own account.");
    this.ctx.storage.transactionSync(() => {
      if (this.rows("SELECT account_id FROM claims WHERE account_id = ?", body.accountId).length) return;
      const click = this.rows("SELECT * FROM clicks WHERE token = ? AND consumed = 0", challenge.click)[0];
      if (!referralsEnabled(this.env) || !this.participating(body.accountId) || !this.participating(inviterId) ||
          this.rows("SELECT account_id FROM deleted_accounts WHERE account_id IN (?, ?)", body.accountId, inviterId).length ||
          !click || click.inviter !== inviterId || challenge.install_at + 14 * DAY < Date.now() ||
          challenge.expires_at < Date.now() ||
          this.rows("SELECT subject_hash FROM used_subjects WHERE subject_hash = ? AND expires_at > ?", identity.subjectHash, Date.now()).length) fail("REFERRAL_NOT_ELIGIBLE", "This installation is expired, unavailable or already attributed.");
      this.ctx.storage.sql.exec("INSERT INTO claims (account_id, subject_hash, inviter, install_at, deadline) VALUES (?, ?, ?, ?, ?)", body.accountId, identity.subjectHash, click.inviter, challenge.install_at, challenge.install_at + 14 * DAY);
      this.ctx.storage.sql.exec("UPDATE clicks SET consumed = 1 WHERE token = ?", challenge.click);
      this.ctx.storage.sql.exec("INSERT OR REPLACE INTO used_subjects (subject_hash, expires_at) VALUES (?, ?)", identity.subjectHash, Number(this.env.LUDOPAWS_REFERRAL_START_MS) + 104 * DAY);
    });
    this.qualify(body.accountId);
    await this.schedule();
    return json(200, { ok: true, bound: true });
  }

  async recordMatch(body) {
    const state = body.state;
    if (!state || state.status !== "FINISHED" || !state.winnerPlayerId || !Number.isSafeInteger(state.finishedAt) ||
        !/^LP[A-Z2-9]{8}$/.test(String(state.matchId ?? "")) || state.finishedAt > Date.now() + 60_000 ||
        state.finishedAt > Number(this.env.LUDOPAWS_REFERRAL_START_MS) + 90 * DAY ||
        state.createdAt < Number(this.env.LUDOPAWS_REFERRAL_START_MS) ||
        !Number.isSafeInteger(state.createdAt) || state.finishedAt - state.createdAt < 180_000 ||
        !Array.isArray(state.players) || state.players.length < 2 ||
        state.players.some(player => player.playerType === "BOT" || player.forfeitedAt != null) ||
        !state.players.some(player => player.playerId === state.winnerPlayerId && Array.isArray(player.tokens) && player.tokens.length === 4 && player.tokens.every(position => position === body.homePosition))) {
      return json(200, { ok: true, qualified: false });
    }
    for (const player of state.players) {
      if ((!player.profileId && !player.friendId) || !Number.isSafeInteger(player.moveCount) || player.moveCount < 1) continue;
      const identity = await internal(this.env.LUDOPROOF_ACCOUNT_DIRECTORY, "GLOBAL:V1", "/referral/identity", { profileId: player.profileId, friendId: player.friendId });
      if (!identity.accountId || !identity.subjectHash) continue;
      if (!this.participating(identity.accountId) && !this.rows("SELECT account_id FROM claims WHERE account_id = ?", identity.accountId).length) continue;
      if (this.rows("SELECT account_id FROM deleted_accounts WHERE account_id = ?", identity.accountId).length) continue;
      this.ctx.storage.sql.exec("INSERT OR IGNORE INTO qualifying_matches (account_id, match_id, started_at, finished_at) VALUES (?, ?, ?, ?)", identity.accountId, String(state.matchId), state.createdAt, state.finishedAt);
      this.qualify(identity.accountId);
    }
    await this.schedule();
    return json(200, { ok: true });
  }

  qualify(accountId) {
    this.ctx.storage.transactionSync(() => {
      const claim = this.rows("SELECT * FROM claims WHERE account_id = ?", accountId)[0];
      if (!claim || this.rows("SELECT account_id FROM deleted_accounts WHERE account_id = ?", claim.inviter).length) return;
      const match = this.rows("SELECT match_id, finished_at FROM qualifying_matches WHERE account_id = ? AND started_at >= ? AND finished_at <= ? ORDER BY finished_at LIMIT 1", accountId, claim.install_at, claim.deadline)[0];
      if (!match) return;
      this.ctx.storage.sql.exec("UPDATE claims SET matched_at = ?, match_id = ? WHERE account_id = ? AND matched_at IS NULL", match.finished_at, match.match_id, accountId);
      this.ctx.storage.sql.exec("INSERT OR IGNORE INTO awards (inviter, status, created_at) VALUES (?, 'PENDING', ?)", claim.inviter, Date.now());
    });
  }

  async deliver(accountId) {
    if (!this.rows("SELECT inviter FROM awards WHERE inviter = ? AND status = 'PENDING'", accountId).length) return;
    const attempt = this.rows("SELECT attempts, next_at FROM delivery_attempts WHERE inviter = ?", accountId)[0];
    if (attempt && Number(attempt.next_at) > Date.now()) return;
    const attempts = Number(attempt?.attempts ?? 0) + 1;
    this.ctx.storage.sql.exec("INSERT INTO delivery_attempts (inviter, attempts, next_at, last_error) VALUES (?, ?, ?, NULL) ON CONFLICT(inviter) DO UPDATE SET attempts = excluded.attempts, next_at = excluded.next_at", accountId, attempts, Date.now() + 30_000);
    try {
      await this.identity(accountId);
      await internal(this.env.LUDOPROOF_PLAYER_ACCOUNTS, accountId, "/wallet/referral-bundle", { accountId, campaign: REFERRAL_CAMPAIGN });
      this.ctx.storage.sql.exec("UPDATE awards SET status = 'DELIVERED' WHERE inviter = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM delivery_attempts WHERE inviter = ?", accountId);
    } catch (error) {
      if (this.rows("SELECT inviter FROM awards WHERE inviter = ? AND status = 'PENDING'", accountId).length) {
        const delay = Math.min(6 * 60 * 60_000, 60_000 * 2 ** Math.min(attempts, 8));
        const code = /^[A-Z0-9_]{1,64}$/.test(String(error?.code)) ? error.code : "SERVICE_UNAVAILABLE";
        this.ctx.storage.sql.exec("UPDATE delivery_attempts SET next_at = ?, last_error = ? WHERE inviter = ?", Date.now() + delay, code, accountId);
      }
      throw error;
    }
  }

  async schedule() {
    const target = Date.now() + 60_000;
    const existing = await this.ctx.storage.getAlarm();
    if (existing == null || existing <= Date.now() || existing > target) await this.ctx.storage.setAlarm(target);
  }

  async alarm() {
    const now = Date.now();
    this.ctx.storage.sql.exec("DELETE FROM challenges WHERE expires_at < ?", now);
    this.ctx.storage.sql.exec("DELETE FROM clicks WHERE clicked_at < ?", now - 21 * DAY);
    this.ctx.storage.sql.exec("DELETE FROM used_subjects WHERE expires_at < ?", now);
    this.ctx.storage.sql.exec("DELETE FROM subject_history WHERE expires_at < ?", now);
    this.ctx.storage.sql.exec("DELETE FROM qualifying_matches WHERE finished_at < ?", now - 14 * DAY);
    this.ctx.storage.sql.exec("DELETE FROM referral_limits WHERE started_at < ?", now - DAY);
    const recordedStart = Number(this.rows("SELECT start_at FROM campaign_meta WHERE id = 1")[0]?.start_at ?? this.env.LUDOPAWS_REFERRAL_START_MS);
    if (referralsConfigured(this.env) && recordedStart === Number(this.env.LUDOPAWS_REFERRAL_START_MS)) {
      for (const row of this.rows("SELECT a.inviter FROM awards a LEFT JOIN delivery_attempts d ON d.inviter = a.inviter WHERE a.status = 'PENDING' AND COALESCE(d.next_at, 0) <= ? ORDER BY COALESCE(d.next_at, 0), a.created_at LIMIT 50", now)) {
        try { await this.deliver(row.inviter); } catch { }
      }
    }
    if (now > recordedStart + 104 * DAY) {
      for (const table of ["invites", "clicks", "challenges", "claims", "used_subjects", "qualifying_matches", "participation", "referral_limits", "subject_history"]) this.ctx.storage.sql.exec("DELETE FROM " + table);
    }
    if (this.rows("SELECT inviter FROM awards WHERE status = 'PENDING' LIMIT 1").length ||
        this.rows("SELECT token FROM clicks LIMIT 1").length || this.rows("SELECT subject_hash FROM used_subjects LIMIT 1").length ||
        this.rows("SELECT account_id FROM qualifying_matches LIMIT 1").length || this.rows("SELECT account_id FROM invites LIMIT 1").length ||
        this.rows("SELECT account_id FROM participation LIMIT 1").length) {
      await this.ctx.storage.setAlarm(now + 5 * 60_000);
    }
  }

  async deleteAccount(accountId) {
    if (typeof accountId !== "string" || !/^[a-f0-9-]{36}$/.test(accountId)) fail("INVALID_ACCOUNT", "Invalid account.");
    this.ctx.storage.transactionSync(() => {
      this.ctx.storage.sql.exec("INSERT OR IGNORE INTO deleted_accounts (account_id) VALUES (?)", accountId);
      this.ctx.storage.sql.exec("DELETE FROM challenges WHERE account_id = ? OR click IN (SELECT token FROM clicks WHERE inviter = ?)", accountId, accountId);
      this.ctx.storage.sql.exec("DELETE FROM clicks WHERE inviter = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM invites WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM claims WHERE account_id = ? OR inviter = ?", accountId, accountId);
      this.ctx.storage.sql.exec("DELETE FROM awards WHERE inviter = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM qualifying_matches WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM participation WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM delivery_attempts WHERE inviter = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM referral_limits WHERE action IN (?, ?, ?, ?, ?, ?)", "click:" + accountId, "challenge:" + accountId, "bind:" + accountId, "status:" + accountId, "/invite:" + accountId, "/enroll:" + accountId);
    });
    return json(200, { ok: true });
  }
}
