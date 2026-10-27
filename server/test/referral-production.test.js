import test from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { PlayerAccount } from "../src/player-account.js";
import { ReferralCampaign } from "../src/referral-campaign.js";
import { MatchRoom } from "../src/match-room.js";
import { DAY, REFERRAL_CAMPAIGN, REFERRAL_REWARD, REFERRAL_TERMS_VERSION, referralsConfigured } from "../src/referral-config.js";

const accountId = "00000000-0000-4000-8000-000000000001";
const sessionEnvironment = { LUDOPROOF_SESSION_HMAC_KEY: "referral-regression-test-key-at-least-32-bytes" };

function storageContext(testContext) {
  const database = new DatabaseSync(":memory:");
  testContext.after(() => database.close());
  const values = new Map();
  let alarm = null;
  return {
    blockConcurrencyWhile: callback => callback(),
    storage: {
      sql: { exec(statement, ...argumentsList) {
        const prepared = database.prepare(statement);
        if (prepared.columns().length) return prepared.all(...argumentsList);
        const rows = [];
        rows.rowsWritten = Number(prepared.run(...argumentsList).changes);
        return rows;
      } },
      transactionSync(callback) {
        database.exec("BEGIN");
        try { const result = callback(); database.exec("COMMIT"); return result; }
        catch (error) { database.exec("ROLLBACK"); throw error; }
      },
      get: async key => structuredClone(values.get(key)),
      put: async (key, value) => values.set(key, structuredClone(value)),
      delete: async key => Array.isArray(key) ? key.forEach(item => values.delete(item)) : values.delete(key),
      list: async () => new Map(values),
      getAlarm: async () => alarm,
      setAlarm: async value => { alarm = value; },
    },
  };
}

async function call(target, path, body = {}) {
  const response = await target.fetch(new Request("https://internal" + path, {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body),
  }));
  return { status: response.status, body: await response.json() };
}

function configuredEnvironment() {
  return {
    LUDOPROOF_AGE_SAFETY_MODE: "FAMILY_V2", LUDOPAWS_GOOGLE_LINKING_ENABLED: "true",
    LUDOPAWS_REFERRALS_ENABLED: "true", LUDOPAWS_REFERRAL_POLICY_APPROVED: "true",
    LUDOPAWS_REFERRAL_SDK_REVIEW_APPROVED: "true", LUDOPAWS_REFERRAL_RELEASE_VALIDATED: "true",
    LUDOPAWS_REFERRAL_START_MS: String(Date.now() - DAY), LUDOPAWS_INTEGRITY_PROJECT_NUMBER: "123456789012",
    LUDOPAWS_REFERRAL_MIN_VERSION_CODE: "42", LUDOPAWS_PLAY_SIGNING_SHA256: "ab".repeat(32),
    LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL: "test-only@example.invalid", LUDOPAWS_INTEGRITY_PRIVATE_KEY: "test-only-no-live-credential",
    LUDOPAWS_REFERRALS: {}, LUDOPROOF_PLAYER_ACCOUNTS: {},
    LUDOPROOF_ACCOUNT_DIRECTORY: { idFromName: value => value, get: () => ({ fetch: async request => {
      const body = await request.json();
      return Response.json({ ok: true, accountId: body.accountId, subjectHash: "cd".repeat(32), createdAt: Date.now() - 60_000 });
    } }) },
  };
}

test("production referral gates require separate SDK and release approval", () => {
  const environment = configuredEnvironment();
  assert.equal(referralsConfigured(environment), true);
  for (const flag of ["LUDOPAWS_REFERRAL_POLICY_APPROVED", "LUDOPAWS_REFERRAL_SDK_REVIEW_APPROVED", "LUDOPAWS_REFERRAL_RELEASE_VALIDATED"]) {
    assert.equal(referralsConfigured({ ...environment, [flag]: "false" }), false);
  }
  assert.equal(referralsConfigured({ ...environment, LUDOPAWS_REFERRAL_MIN_VERSION_CODE: "" }), false);
});

test("referral bundle grants exactly 100 Gems once and leaves selections unchanged", async testContext => {
  const context = storageContext(testContext);
  const account = new PlayerAccount(context, sessionEnvironment);
  const session = (await call(account, "/session/create", { accountId, displayName: "Player" })).body;
  const before = (await call(account, "/wallet/get", { accountId, sessionId: session.sessionId })).body.wallet;
  const first = await call(account, "/wallet/referral-bundle", { accountId, campaign: REFERRAL_CAMPAIGN });
  const second = await call(account, "/wallet/referral-bundle", { accountId, campaign: REFERRAL_CAMPAIGN });
  assert.equal(first.status, 200);
  assert.equal(first.body.wallet.balance, before.balance + 100);
  assert.equal(second.body.wallet.balance, first.body.wallet.balance);
  assert.equal(second.body.replay, true);
  assert.deepEqual(first.body.wallet.selectedCosmetics, before.selectedCosmetics);
  for (const cosmetic of REFERRAL_REWARD.cosmetics) assert.ok(first.body.wallet.ownedCosmetics.includes(cosmetic));
});

test("full wallet cannot cause a partial referral grant and can retry later", async testContext => {
  const context = storageContext(testContext);
  const account = new PlayerAccount(context, sessionEnvironment);
  await call(account, "/session/create", { accountId, displayName: "Player" });
  context.storage.sql.exec("UPDATE wallet SET balance = 2000000000 WHERE account_id = ?", accountId);
  assert.equal((await call(account, "/wallet/referral-bundle", { accountId, campaign: REFERRAL_CAMPAIGN })).status, 409);
  assert.equal([...context.storage.sql.exec("SELECT cosmetic_id FROM cosmetic_referral_grants")].length, 0);
  assert.equal([...context.storage.sql.exec("SELECT transaction_id FROM wallet_transactions WHERE kind = 'REFERRAL_BUNDLE'")].length, 0);
  context.storage.sql.exec("UPDATE wallet SET balance = 1999999900 WHERE account_id = ?", accountId);
  const retry = await call(account, "/wallet/referral-bundle", { accountId, campaign: REFERRAL_CAMPAIGN });
  assert.equal(retry.status, 200);
  assert.equal(retry.body.wallet.balance, 2000000000);
});

test("all-owned bundle still awards Gems and preserves gifted ownership after a Play refund", async testContext => {
  const context = storageContext(testContext);
  const account = new PlayerAccount(context, sessionEnvironment);
  const session = (await call(account, "/session/create", { accountId, displayName: "Player" })).body;
  for (const cosmeticId of REFERRAL_REWARD.cosmetics) {
    const category = cosmeticId.startsWith("board_") ? "BOARD" : cosmeticId.startsWith("dice_") ? "DICE" : "CHARACTER_PACK";
    context.storage.sql.exec("INSERT OR IGNORE INTO owned_cosmetics (cosmetic_id, category, source, acquired_at) VALUES (?, ?, 'PLAY_PRODUCT', ?)", cosmeticId, category, Date.now());
    context.storage.sql.exec("UPDATE owned_cosmetics SET source = 'PLAY_PRODUCT' WHERE cosmetic_id = ?", cosmeticId);
  }
  const ledgerId = "ef".repeat(32);
  context.storage.sql.exec("INSERT INTO cosmetic_play_grants (ledger_id, cosmetic_id) VALUES (?, 'character_rabbit')", ledgerId);
  assert.equal((await call(account, "/wallet/referral-bundle", { accountId, campaign: REFERRAL_CAMPAIGN })).body.wallet.balance, 100);
  assert.equal((await call(account, "/wallet/play-revoke", { accountId, purchaseLedgerId: ledgerId })).status, 200);
  const snapshot = (await call(account, "/wallet/get", { accountId, sessionId: session.sessionId })).body.wallet;
  assert.ok(snapshot.ownedCosmetics.includes("character_rabbit"));
  assert.equal(snapshot.balance, 100);
});

test("sharing requires current participation and withdrawal stops new attribution", async testContext => {
  const context = storageContext(testContext);
  const campaign = new ReferralCampaign(context, configuredEnvironment());
  assert.equal((await call(campaign, "/invite", { accountId })).body.error, "TERMS_ACCEPTANCE_REQUIRED");
  assert.equal((await call(campaign, "/enroll", { accountId, termsVersion: "old", consent: true })).status, 409);
  assert.equal((await call(campaign, "/enroll", { accountId, termsVersion: REFERRAL_TERMS_VERSION, consent: true })).status, 200);
  const invite = await call(campaign, "/invite", { accountId });
  assert.equal(invite.status, 200);
  const token = invite.body.shareUrl.split("/").pop();
  assert.match((await call(campaign, "/click", { token })).body.referrer, /^lp_ref=[a-f0-9]{64}$/);
  assert.equal((await call(campaign, "/withdraw", { accountId })).status, 200);
  assert.equal((await call(campaign, "/click", { token })).body.referrer, null);
  assert.equal((await call(campaign, "/status", { accountId })).body.consented, false);
});

test("campaign launch time cannot be changed after enrollment", async testContext => {
  const context = storageContext(testContext);
  const environment = configuredEnvironment();
  const campaign = new ReferralCampaign(context, environment);
  assert.equal((await call(campaign, "/status", { accountId })).status, 200);
  environment.LUDOPAWS_REFERRAL_START_MS = String(Number(environment.LUDOPAWS_REFERRAL_START_MS) - DAY);
  assert.equal((await call(campaign, "/status", { accountId })).body.error, "CAMPAIGN_CONFIGURATION_CHANGED");
});

test("withdrawal cancels an unmatched incoming claim without reopening its subject", async testContext => {
  const context = storageContext(testContext);
  const campaign = new ReferralCampaign(context, configuredEnvironment());
  await call(campaign, "/enroll", { accountId, termsVersion: REFERRAL_TERMS_VERSION, consent: true });
  const subject = "cd".repeat(32);
  context.storage.sql.exec("INSERT INTO claims (account_id, subject_hash, inviter, install_at, deadline) VALUES (?, ?, ?, ?, ?)", accountId, subject, "00000000-0000-4000-8000-000000000002", Date.now(), Date.now() + DAY);
  context.storage.sql.exec("INSERT INTO used_subjects (subject_hash, expires_at) VALUES (?, ?)", subject, Date.now() + DAY);
  assert.equal((await call(campaign, "/withdraw", { accountId })).status, 200);
  assert.equal([...context.storage.sql.exec("SELECT account_id FROM claims")].length, 0);
  assert.equal([...context.storage.sql.exec("SELECT subject_hash FROM used_subjects")].length, 1);
  assert.equal([...context.storage.sql.exec("SELECT subject_hash FROM subject_history")].length, 1);
});

test("deferred failures cannot starve due reward deliveries", async testContext => {
  const context = storageContext(testContext);
  const campaign = new ReferralCampaign(context, configuredEnvironment());
  for (let index = 0; index < 60; index += 1) {
    const inviter = "deferred-" + index;
    context.storage.sql.exec("INSERT INTO awards (inviter, status, created_at) VALUES (?, 'PENDING', ?)", inviter, Date.now() - DAY);
    context.storage.sql.exec("INSERT INTO delivery_attempts (inviter, attempts, next_at) VALUES (?, 5, ?)", inviter, Date.now() + DAY);
  }
  context.storage.sql.exec("INSERT INTO awards (inviter, status, created_at) VALUES (?, 'PENDING', ?)", accountId, Date.now());
  const delivered = [];
  campaign.deliver = async inviter => { delivered.push(inviter); };
  await campaign.alarm();
  assert.deepEqual(delivered, [accountId]);
});

test("durable terminal outbox retries after a room restart without full match state", async testContext => {
  const context = storageContext(testContext);
  const environment = configuredEnvironment();
  let available = false;
  environment.LUDOPAWS_REFERRALS = { idFromName: value => value, get: () => ({ fetch: async () => new Response("{}", { status: available ? 200 : 503 }) }) };
  await context.storage.put("referral-outbox", { attempts: 0, nextAt: 0, expiresAt: Date.now() + DAY, payload: { state: { matchId: "LPABCDEFGH" } } });
  await new MatchRoom(context, environment).recordReferralResult();
  const pending = await context.storage.get("referral-outbox");
  assert.equal(pending.attempts, 1);
  assert.ok(pending.nextAt > Date.now());
  await context.storage.put("referral-outbox", { ...pending, nextAt: 0 });
  available = true;
  await new MatchRoom(context, environment).recordReferralResult();
  assert.equal(await context.storage.get("referral-outbox"), undefined);
  assert.equal(await context.storage.get("referral-delivered"), true);
});
