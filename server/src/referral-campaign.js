import { httpError, readJsonObject, sha256Hex } from "./crypto.js";
import { DAY, REFERRAL_CAMPAIGN, REFERRAL_ORIGIN, REFERRAL_REWARD, REFERRAL_TERMS_VERSION, referralsConfigured, referralsEnabled, TIMED_REFERRAL_CAMPAIGN, TIMED_REFERRAL_DURATION, TIMED_REFERRAL_REWARD, timedReferralsEnabled } from "./referral-config.js";
import { verifyReferralIntegrity } from "./referral-integrity.js";

const opaque = () => crypto.randomUUID().replaceAll("-", "") + crypto.randomUUID().replaceAll("-", "");
const json = (status, body) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
const awardTable = timed => timed ? "timed_awards" : "awards";
const deliveryTable = timed => timed ? "timed_delivery_attempts" : "delivery_attempts";
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
      sql.exec("CREATE TABLE IF NOT EXISTS timed_offers (account_id TEXT PRIMARY KEY, started_at INTEGER NOT NULL, token TEXT UNIQUE)");
      sql.exec("CREATE TABLE IF NOT EXISTS timed_awards (inviter TEXT PRIMARY KEY, status TEXT NOT NULL, created_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS timed_delivery_attempts (inviter TEXT PRIMARY KEY, attempts INTEGER NOT NULL, next_at INTEGER NOT NULL, last_error TEXT)");
      sql.exec("CREATE INDEX IF NOT EXISTS timed_award_pending ON timed_awards(status, created_at)");
      sql.exec("CREATE INDEX IF NOT EXISTS timed_delivery_due ON timed_delivery_attempts(next_at)");
      if (![...sql.exec("PRAGMA table_info(clicks)")].some(column => column.name === "timed")) {
        sql.exec("ALTER TABLE clicks ADD COLUMN timed INTEGER NOT NULL DEFAULT 0");
      }
      if (![...sql.exec("PRAGMA table_info(claims)")].some(column => column.name === "verified_at")) {
        sql.exec("ALTER TABLE claims ADD COLUMN verified_at INTEGER");
      }
      sql.exec("CREATE TABLE IF NOT EXISTS campaign_meta (id INTEGER PRIMARY KEY CHECK(id = 1), start_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS subject_history (subject_hash TEXT PRIMARY KEY, first_account_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_click_inviter ON clicks(inviter, clicked_at)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_match_expiry ON qualifying_matches(finished_at)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_subject_expiry ON used_subjects(expires_at)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_challenge_expiry ON challenges(expires_at)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_challenge_account ON challenges(account_id)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_history_expiry ON subject_history(expires_at)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_match_account_time ON qualifying_matches(account_id, started_at, finished_at)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_award_pending ON awards(status, created_at)");
      sql.exec("CREATE INDEX IF NOT EXISTS referral_delivery_due ON delivery_attempts(next_at)");
      // Aggregate operational counters only; no account, device or token fields.
      sql.exec("CREATE TABLE IF NOT EXISTS integrity_health (bucket INTEGER PRIMARY KEY, verified INTEGER NOT NULL DEFAULT 0, rejected INTEGER NOT NULL DEFAULT 0, provider_failures INTEGER NOT NULL DEFAULT 0, quota_failures INTEGER NOT NULL DEFAULT 0)");
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
        pending: Number(this.rows("SELECT COUNT(*) AS total FROM (SELECT status FROM awards UNION ALL SELECT status FROM timed_awards) WHERE status = 'PENDING'")[0].total),
        oldestPendingAt: this.rows("SELECT MIN(created_at) AS oldest FROM (SELECT status, created_at FROM awards UNION ALL SELECT status, created_at FROM timed_awards) WHERE status = 'PENDING'")[0].oldest,
        failedDeliveries: Number(this.rows("SELECT COUNT(*) AS total FROM (SELECT last_error FROM delivery_attempts UNION ALL SELECT last_error FROM timed_delivery_attempts) WHERE last_error IS NOT NULL")[0].total),
        integrity: this.rows("SELECT COALESCE(SUM(verified), 0) AS verified, COALESCE(SUM(rejected), 0) AS rejected, COALESCE(SUM(provider_failures), 0) AS providerFailures, COALESCE(SUM(quota_failures), 0) AS quotaFailures FROM integrity_health WHERE bucket >= ?", Math.floor((Date.now() - 15 * 60_000) / 60_000))[0],
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
      if (!(path === "/status" || path === "/match" || path === "/offer" ? referralsConfigured(this.env) : (referralsEnabled(this.env) || timedReferralsEnabled(this.env)))) return json(503, { error: "REFERRALS_UNAVAILABLE", message: "Friendship rewards are not available yet." });
      const start = Number(this.env.LUDOPAWS_REFERRAL_START_MS);
      this.ctx.storage.sql.exec("INSERT OR IGNORE INTO campaign_meta (id, start_at) VALUES (1, ?)", start);
      if (Number(this.rows("SELECT start_at FROM campaign_meta WHERE id = 1")[0].start_at) !== start) throw httpError(503, "CAMPAIGN_CONFIGURATION_CHANGED", "Campaign dates changed unexpectedly. Contact support.");
      if (path === "/click") return await this.click(body);
      if (path === "/match") return await this.recordMatch(body);
      const identity = await this.identity(body.accountId);
      if (path === "/status") this.limit("status:" + body.accountId, 60);
      if (path === "/invite" || path === "/enroll") this.limit(path + ":" + body.accountId, 6);
      if (path === "/offer") {
        this.limit("offer:" + body.accountId, 12);
        if (timedReferralsEnabled(this.env) && referralsEnabled(this.env)) {
          this.ctx.storage.sql.exec("INSERT OR IGNORE INTO timed_offers (account_id, started_at) VALUES (?, ?)", body.accountId, Date.now());
        }
        return json(200, { ok: true, timedOffer: this.timedStatus(body.accountId) });
      }
      if (path === "/status") {
        try { await this.deliver(body.accountId); } catch { }
        try { await this.deliver(body.accountId, true); } catch { }
        const award = this.rows("SELECT status FROM awards WHERE inviter = ?", body.accountId)[0];
        const invite = this.rows("SELECT token FROM invites WHERE account_id = ?", body.accountId)[0];
        const counts = this.rows("SELECT COUNT(*) AS total FROM claims WHERE inviter = ? AND verified_at IS NULL AND (deadline > ? OR matched_at IS NOT NULL)", body.accountId, Date.now())[0];
        return json(200, { ok: true, enabled: referralsEnabled(this.env), campaign: REFERRAL_CAMPAIGN, reward: REFERRAL_REWARD,
          timedOffer: this.timedStatus(body.accountId),
          timedOfferAvailable: timedReferralsEnabled(this.env) && referralsEnabled(this.env),
          verificationEnabled: referralsEnabled(this.env) || timedReferralsEnabled(this.env),
          termsVersion: REFERRAL_TERMS_VERSION, consented: this.participating(body.accountId),
          attribution: this.rows("SELECT deadline, matched_at, verified_at FROM claims WHERE account_id = ?", body.accountId).map(row => ({ state: row.matched_at ? "QUALIFIED" : "BOUND", deadline: Number(row.deadline), together: row.verified_at != null }))[0] ?? null,
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
        if (body.campaign === TIMED_REFERRAL_CAMPAIGN) {
          const now = Date.now();
          if (!this.timedOpen(body.accountId, now)) fail("OFFER_EXPIRED", "Your 48-hour friendship offer has ended.");
          if (this.rows("SELECT inviter FROM timed_awards WHERE inviter = ?", body.accountId).length) fail("BUNDLE_ALREADY_QUALIFIED", "This account has already qualified for its one-time bundle.");
          this.ctx.storage.sql.exec("UPDATE timed_offers SET token = COALESCE(token, ?) WHERE account_id = ?", opaque(), body.accountId);
          const offer = this.rows("SELECT token FROM timed_offers WHERE account_id = ?", body.accountId)[0];
          return json(200, { ok: true, shareUrl: REFERRAL_ORIGIN + "/r/" + offer.token });
        }
        if (!referralsEnabled(this.env)) fail("REFERRALS_UNAVAILABLE", "This friendship offer has ended.");
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

  timedDeadline(accountId) {
    const offer = this.rows("SELECT started_at FROM timed_offers WHERE account_id = ?", accountId)[0];
    return offer ? Number(offer.started_at) + TIMED_REFERRAL_DURATION : 0;
  }

  timedOpen(accountId, now = Date.now()) {
    return timedReferralsEnabled(this.env) && this.timedDeadline(accountId) > now;
  }

  timedStatus(accountId) {
    const endsAt = this.timedDeadline(accountId);
    if (!endsAt) return null;
    const serverNow = Date.now();
    const award = this.rows("SELECT status FROM timed_awards WHERE inviter = ?", accountId)[0];
    const joined = this.rows("SELECT account_id FROM claims WHERE inviter = ? AND verified_at IS NOT NULL LIMIT 1", accountId).length > 0;
    const state = award?.status === "DELIVERED" ? "REWARDED" : award ? "VERIFYING" :
      serverNow >= endsAt ? "EXPIRED" : !timedReferralsEnabled(this.env) ? "UNAVAILABLE" : joined ? "FRIEND_JOINED" : "INVITE";
    return { campaign: TIMED_REFERRAL_CAMPAIGN, reward: TIMED_REFERRAL_REWARD, state, endsAt, serverNow };
  }

  clickOpen(click) {
    return click.timed ? this.timedOpen(click.inviter) : referralsEnabled(this.env);
  }

  participating(accountId) { return this.rows("SELECT account_id FROM participation WHERE account_id = ? AND terms_version = ?", accountId, REFERRAL_TERMS_VERSION).length === 1; }

  recordIntegrity(kind, quota = false) {
    if (!["verified", "rejected", "provider_failures"].includes(kind)) return;
    const bucket = Math.floor(Date.now() / 60_000);
    this.ctx.storage.sql.exec("INSERT OR IGNORE INTO integrity_health (bucket) VALUES (?)", bucket);
    this.ctx.storage.sql.exec(`UPDATE integrity_health SET ${kind} = ${kind} + 1, quota_failures = quota_failures + ? WHERE bucket = ?`, quota ? 1 : 0, bucket);
    this.ctx.storage.sql.exec("DELETE FROM integrity_health WHERE bucket < ?", bucket - 60);
  }

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
    const invite = this.rows("SELECT account_id, 0 AS timed FROM invites WHERE token = ? UNION ALL SELECT account_id, 1 AS timed FROM timed_offers WHERE token = ?", body.token, body.token)[0];
    if (!invite) fail("INVITE_INVALID", "This invitation is unavailable.");
    await this.identity(invite.account_id);
    if (!this.clickOpen({ inviter: invite.account_id, timed: invite.timed }) || !this.participating(invite.account_id) ||
        this.rows(`SELECT inviter FROM ${awardTable(invite.timed)} WHERE inviter = ?`, invite.account_id).length) return json(200, { ok: true, referrer: null });
    this.limit("click:" + invite.account_id, 200, DAY);
    const token = opaque();
    this.ctx.storage.sql.exec("INSERT INTO clicks (token, inviter, clicked_at, timed) VALUES (?, ?, ?, ?)", token, invite.account_id, Date.now(), invite.timed);
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
    if (!this.clickOpen(click) || this.rows("SELECT account_id FROM deleted_accounts WHERE account_id IN (?, ?)", body.accountId, click.inviter).length) fail("REFERRAL_NOT_ELIGIBLE", "This invitation is unavailable.");
    const token = opaque();
    const requestHash = await sha256Hex([click.timed ? TIMED_REFERRAL_CAMPAIGN : REFERRAL_CAMPAIGN, REFERRAL_TERMS_VERSION, body.accountId, body.click, install, token].join(":"));
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
    try {
      await verifyReferralIntegrity(this.env, body.integrityToken, challenge.request_hash);
      this.recordIntegrity("verified");
    } catch (error) {
      this.recordIntegrity(error?.code === "INTEGRITY_UNAVAILABLE" ? "provider_failures" : "rejected", error?.providerStatus === 429);
      throw error;
    }
    const freshIdentity = await this.identity(body.accountId);
    if (freshIdentity.subjectHash !== identity.subjectHash) fail("ACCOUNT_CHANGED", "Reconnect your account.");
    const inviterId = this.rows("SELECT inviter FROM clicks WHERE token = ?", challenge.click)[0]?.inviter;
    if (!inviterId) fail("REFERRAL_NOT_ELIGIBLE", "This invitation is unavailable.");
    const inviter = await this.identity(inviterId);
    if (!inviter.subjectHash || inviter.subjectHash === identity.subjectHash) fail("SELF_REFERRAL", "You cannot invite your own account.");
    this.ctx.storage.transactionSync(() => {
      if (this.rows("SELECT account_id FROM claims WHERE account_id = ?", body.accountId).length) return;
      const click = this.rows("SELECT * FROM clicks WHERE token = ? AND consumed = 0", challenge.click)[0];
      if (!click || !this.clickOpen(click) || !this.participating(body.accountId) || !this.participating(inviterId) ||
          this.rows("SELECT account_id FROM deleted_accounts WHERE account_id IN (?, ?)", body.accountId, inviterId).length ||
          click.inviter !== inviterId || challenge.install_at + 14 * DAY < Date.now() ||
          challenge.expires_at < Date.now() ||
          this.rows("SELECT subject_hash FROM used_subjects WHERE subject_hash = ? AND expires_at > ?", identity.subjectHash, Date.now()).length) fail("REFERRAL_NOT_ELIGIBLE", "This installation is expired, unavailable or already attributed.");
      const deadline = click.timed ? this.timedDeadline(inviterId) : challenge.install_at + 14 * DAY;
      this.ctx.storage.sql.exec("INSERT INTO claims (account_id, subject_hash, inviter, install_at, deadline, verified_at) VALUES (?, ?, ?, ?, ?, ?)",
        body.accountId, identity.subjectHash, click.inviter, challenge.install_at, deadline, click.timed ? Date.now() : null);
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
        state.finishedAt > Number(this.env.LUDOPAWS_REFERRAL_START_MS) + 90 * DAY + TIMED_REFERRAL_DURATION ||
        state.createdAt < Number(this.env.LUDOPAWS_REFERRAL_START_MS) ||
        !Number.isSafeInteger(state.createdAt) || state.finishedAt - state.createdAt < 180_000 ||
        !Array.isArray(state.players) || state.players.length < 2 ||
        state.players.some(player => player.playerType === "BOT" || player.forfeitedAt != null) ||
        !state.players.some(player => player.playerId === state.winnerPlayerId && Array.isArray(player.tokens) && player.tokens.length === 4 && player.tokens.every(position => position === body.homePosition))) {
      return json(200, { ok: true, qualified: false });
    }
    const matchedAccounts = [];
    for (const player of state.players) {
      if ((!player.profileId && !player.friendId) || !Number.isSafeInteger(player.moveCount) || player.moveCount < 1) continue;
      const identity = await internal(this.env.LUDOPROOF_ACCOUNT_DIRECTORY, "GLOBAL:V1", "/referral/identity", { profileId: player.profileId, friendId: player.friendId });
      if (!identity.accountId || !identity.subjectHash) continue;
      if (!this.participating(identity.accountId) && !this.rows("SELECT account_id FROM claims WHERE account_id = ?", identity.accountId).length) continue;
      if (this.rows("SELECT account_id FROM deleted_accounts WHERE account_id = ?", identity.accountId).length) continue;
      this.ctx.storage.sql.exec("INSERT OR IGNORE INTO qualifying_matches (account_id, match_id, started_at, finished_at) VALUES (?, ?, ?, ?)", identity.accountId, String(state.matchId), state.createdAt, state.finishedAt);
      matchedAccounts.push(identity.accountId);
    }
    for (const accountId of matchedAccounts) this.qualify(accountId);
    await this.schedule();
    return json(200, { ok: true });
  }

  qualify(accountId) {
    this.ctx.storage.transactionSync(() => {
      const claim = this.rows("SELECT * FROM claims WHERE account_id = ?", accountId)[0];
      if (!claim || this.rows("SELECT account_id FROM deleted_accounts WHERE account_id = ?", claim.inviter).length) return;
      const timed = claim.verified_at != null;
      const match = timed
        ? this.rows(`SELECT guest.match_id, guest.finished_at FROM qualifying_matches guest
            JOIN qualifying_matches inviter ON inviter.account_id = ? AND inviter.match_id = guest.match_id
              AND inviter.started_at = guest.started_at AND inviter.finished_at = guest.finished_at
            WHERE guest.account_id = ? AND guest.started_at >= ? AND guest.finished_at <= ?
            ORDER BY guest.finished_at LIMIT 1`, claim.inviter, accountId, claim.verified_at, claim.deadline)[0]
        : this.rows("SELECT match_id, finished_at FROM qualifying_matches WHERE account_id = ? AND started_at >= ? AND finished_at <= ? ORDER BY finished_at LIMIT 1",
          accountId, claim.install_at, Math.min(claim.deadline, Number(this.env.LUDOPAWS_REFERRAL_START_MS) + 90 * DAY))[0];
      if (!match) return;
      this.ctx.storage.sql.exec("UPDATE claims SET matched_at = ?, match_id = ? WHERE account_id = ? AND matched_at IS NULL", match.finished_at, match.match_id, accountId);
      this.ctx.storage.sql.exec(`INSERT OR IGNORE INTO ${awardTable(timed)} (inviter, status, created_at) VALUES (?, 'PENDING', ?)`, claim.inviter, Date.now());
    });
  }

  async deliver(accountId, timed = false) {
    const awards = awardTable(timed);
    const attemptsTable = deliveryTable(timed);
    if (!this.rows(`SELECT inviter FROM ${awards} WHERE inviter = ? AND status = 'PENDING'`, accountId).length) return;
    const attempt = this.rows(`SELECT attempts, next_at FROM ${attemptsTable} WHERE inviter = ?`, accountId)[0];
    if (attempt && Number(attempt.next_at) > Date.now()) return;
    const attempts = Number(attempt?.attempts ?? 0) + 1;
    this.ctx.storage.sql.exec(`INSERT INTO ${attemptsTable} (inviter, attempts, next_at, last_error) VALUES (?, ?, ?, NULL) ON CONFLICT(inviter) DO UPDATE SET attempts = excluded.attempts, next_at = excluded.next_at`, accountId, attempts, Date.now() + 30_000);
    try {
      await this.identity(accountId);
      await internal(this.env.LUDOPROOF_PLAYER_ACCOUNTS, accountId, "/wallet/referral-bundle", { accountId, campaign: timed ? TIMED_REFERRAL_CAMPAIGN : REFERRAL_CAMPAIGN });
      this.ctx.storage.sql.exec(`UPDATE ${awards} SET status = 'DELIVERED' WHERE inviter = ?`, accountId);
      this.ctx.storage.sql.exec(`DELETE FROM ${attemptsTable} WHERE inviter = ?`, accountId);
    } catch (error) {
      if (this.rows(`SELECT inviter FROM ${awards} WHERE inviter = ? AND status = 'PENDING'`, accountId).length) {
        const delay = Math.min(6 * 60 * 60_000, 60_000 * 2 ** Math.min(attempts, 8));
        const code = /^[A-Z0-9_]{1,64}$/.test(String(error?.code)) ? error.code : "SERVICE_UNAVAILABLE";
        this.ctx.storage.sql.exec(`UPDATE ${attemptsTable} SET next_at = ?, last_error = ? WHERE inviter = ?`, Date.now() + delay, code, accountId);
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
    this.ctx.storage.sql.exec("DELETE FROM integrity_health WHERE bucket < ?", Math.floor(now / 60_000) - 60);
    this.ctx.storage.sql.exec("DELETE FROM challenges WHERE expires_at < ?", now);
    this.ctx.storage.sql.exec("DELETE FROM clicks WHERE clicked_at < ?", now - 21 * DAY);
    this.ctx.storage.sql.exec("DELETE FROM used_subjects WHERE expires_at < ?", now);
    this.ctx.storage.sql.exec("DELETE FROM subject_history WHERE expires_at < ?", now);
    this.ctx.storage.sql.exec("DELETE FROM qualifying_matches WHERE finished_at < ?", now - 14 * DAY);
    this.ctx.storage.sql.exec("DELETE FROM referral_limits WHERE started_at < ?", now - DAY);
    const recordedStart = Number(this.rows("SELECT start_at FROM campaign_meta WHERE id = 1")[0]?.start_at ?? this.env.LUDOPAWS_REFERRAL_START_MS);
    if (referralsConfigured(this.env) && recordedStart === Number(this.env.LUDOPAWS_REFERRAL_START_MS)) {
      for (const timed of [false, true]) {
        for (const row of this.rows(`SELECT a.inviter FROM ${awardTable(timed)} a LEFT JOIN ${deliveryTable(timed)} d ON d.inviter = a.inviter
            WHERE a.status = 'PENDING' AND COALESCE(d.next_at, 0) <= ? ORDER BY COALESCE(d.next_at, 0), a.created_at LIMIT 50`, now)) {
          try { await this.deliver(row.inviter, timed); } catch { }
        }
      }
    }
    if (now > recordedStart + 104 * DAY) {
      for (const table of ["invites", "clicks", "challenges", "claims", "used_subjects", "qualifying_matches", "participation", "referral_limits", "subject_history"]) this.ctx.storage.sql.exec("DELETE FROM " + table);
    }
    if (now > recordedStart + 104 * DAY) this.ctx.storage.sql.exec("DELETE FROM timed_offers");
    if (this.rows("SELECT inviter FROM awards WHERE status = 'PENDING' UNION ALL SELECT inviter FROM timed_awards WHERE status = 'PENDING' LIMIT 1").length ||
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
      this.ctx.storage.sql.exec("DELETE FROM timed_offers WHERE account_id = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM timed_awards WHERE inviter = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM timed_delivery_attempts WHERE inviter = ?", accountId);
      this.ctx.storage.sql.exec("DELETE FROM referral_limits WHERE action = ?", "offer:" + accountId);
      this.ctx.storage.sql.exec("DELETE FROM referral_limits WHERE action IN (?, ?, ?, ?, ?, ?)", "click:" + accountId, "challenge:" + accountId, "bind:" + accountId, "status:" + accountId, "/invite:" + accountId, "/enroll:" + accountId);
    });
    return json(200, { ok: true });
  }
}
