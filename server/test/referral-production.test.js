import test from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { PlayerAccount } from "../src/player-account.js";
import { ReferralCampaign } from "../src/referral-campaign.js";
import { MatchRoom } from "../src/match-room.js";
import { DAY, REFERRAL_CAMPAIGN, REFERRAL_REWARD, REFERRAL_TERMS_VERSION, referralsConfigured } from "../src/referral-config.js";

test("verified invitation binds once, ignores bots and grants the losing friend's inviter atomically", async testContext => {
  const initialTime = Date.now();
  let now = initialTime;
  testContext.mock.method(Date, "now", () => now);
  const invitedId = "00000000-0000-4000-8000-000000000002";
  const environment = configuredEnvironment();
  const identities = new Map([
    [accountId, { accountId, subjectHash: "cd".repeat(32), createdAt: initialTime - 2 * DAY }],
    [invitedId, { accountId: invitedId, subjectHash: "ef".repeat(32), createdAt: initialTime + 2000 }],
  ]);
  environment.LUDOPROOF_ACCOUNT_DIRECTORY = { idFromName: value => value, get: () => ({ fetch: async request => {
    const body = await request.json();
    return Response.json(identities.get(body.accountId ?? (body.profileId === "invited-profile" ? invitedId : accountId)));
  } }) };
  const walletContext = storageContext(testContext);
  const wallet = new PlayerAccount(walletContext, sessionEnvironment);
  await call(wallet, "/session/create", { accountId, displayName: "Inviter" });
  environment.LUDOPROOF_PLAYER_ACCOUNTS = { idFromName: value => value, get: identity => {
    assert.equal(identity, accountId);
    return wallet;
  } };
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  environment.LUDOPAWS_INTEGRITY_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n" + Buffer.from(await crypto.subtle.exportKey("pkcs8", pair.privateKey)).toString("base64") + "\n-----END PRIVATE KEY-----";
  let requestHash;
  let reportedVersion = "41";
  testContext.mock.method(globalThis, "fetch", async (url, options) => {
    if (url === "https://oauth2.googleapis.com/token") return Response.json({ access_token: "local-test-only", expires_in: 3600 });
    assert.equal(url, "https://playintegrity.googleapis.com/v1/com.rekixo.ludopaws:decodeIntegrityToken");
    assert.deepEqual(JSON.parse(options.body), { integrityToken: "i".repeat(64) });
    return Response.json({ tokenPayloadExternal: {
      requestDetails: { requestPackageName: "com.rekixo.ludopaws", requestHash, timestampMillis: String(now) },
      appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: "com.rekixo.ludopaws", versionCode: reportedVersion, certificateSha256Digest: [Buffer.from("ab".repeat(32), "hex").toString("base64url")] },
      accountDetails: { appLicensingVerdict: "LICENSED" }, deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
    } });
  });
  const context = storageContext(testContext);
  const campaign = new ReferralCampaign(context, environment);
  assert.equal((await call(campaign, "/enroll", { accountId, termsVersion: REFERRAL_TERMS_VERSION, consent: true })).status, 200);
  const invite = await call(campaign, "/invite", { accountId });
  const click = await call(campaign, "/click", { token: invite.body.shareUrl.split("/").pop() });
  const clickToken = click.body.referrer.slice("lp_ref=".length);
  now = initialTime + 5000;
  assert.equal((await call(campaign, "/enroll", { accountId: invitedId, termsVersion: REFERRAL_TERMS_VERSION, consent: true })).status, 200);
  const challenge = await call(campaign, "/challenge", { accountId: invitedId, click: clickToken, clickedAt: initialTime, installAt: initialTime + 1000 });
  assert.equal(challenge.status, 200);
  requestHash = challenge.body.requestHash;
  const rejected = await call(campaign, "/bind", { accountId: invitedId, challenge: challenge.body.challenge, integrityToken: "i".repeat(64) });
  assert.equal(rejected.status, 403);
  assert.equal(rejected.body.error, "INSTALL_NOT_VERIFIED");
  assert.equal([...context.storage.sql.exec("SELECT account_id FROM claims")].length, 0);
  reportedVersion = "42";
  const retry = await call(campaign, "/challenge", { accountId: invitedId, click: clickToken, clickedAt: initialTime, installAt: initialTime + 1000 });
  assert.equal(retry.status, 200);
  requestHash = retry.body.requestHash;
  const binding = { accountId: invitedId, challenge: retry.body.challenge, integrityToken: "i".repeat(64) };
  assert.equal((await call(campaign, "/bind", binding)).status, 200);
  assert.equal((await call(campaign, "/bind", binding)).body.bound, true);
  now = initialTime + 205000;
  const state = { matchId: "LPABCDEFGH", status: "FINISHED", createdAt: initialTime + 10000, finishedAt: initialTime + 200000, winnerPlayerId: "winner",
    players: [
      { playerId: "winner", playerType: "HUMAN", tokens: [57, 57, 57, 57], moveCount: 4 },
      { playerId: "friend", profileId: "invited-profile", playerType: "HUMAN", tokens: [0, 0, -1, -1], moveCount: 1 },
    ] };
  const botMatch = structuredClone(state);
  botMatch.players[0].playerType = "BOT";
  assert.equal((await call(campaign, "/match", { state: botMatch, homePosition: 57 })).body.qualified, false);
  assert.equal([...context.storage.sql.exec("SELECT inviter FROM awards")].length, 0);
  assert.equal((await call(campaign, "/match", { state, homePosition: 57 })).status, 200);
  assert.equal((await call(campaign, "/status", { accountId })).body.state, "REWARDED");
  assert.equal((await call(campaign, "/match", { state, homePosition: 57 })).status, 200);
  await campaign.alarm();
  assert.equal([...walletContext.storage.sql.exec("SELECT balance FROM wallet")][0].balance, 100);
  assert.equal([...walletContext.storage.sql.exec("SELECT transaction_id FROM wallet_transactions WHERE kind = 'REFERRAL_BUNDLE'")].length, 1);
  for (const cosmeticId of REFERRAL_REWARD.cosmetics) assert.equal([...walletContext.storage.sql.exec("SELECT cosmetic_id FROM owned_cosmetics WHERE cosmetic_id = ?", cosmeticId)].length, 1);
  assert.equal((await call(campaign, "/status", { accountId: invitedId })).body.attribution.state, "QUALIFIED");
});

const accountId = "00000000-0000-4000-8000-000000000001";
test("concurrent participant bursts keep invitations unique and duplicate retries stable", async testContext => {
  const context = storageContext(testContext);
  const environment = configuredEnvironment();
  environment.LUDOPROOF_ACCOUNT_DIRECTORY = { idFromName: value => value, get: () => ({ fetch: async request => {
    const body = await request.json();
    return Response.json({ accountId: body.accountId, subjectHash: body.accountId.replaceAll("-", "").padEnd(64, "0"), createdAt: Date.now() - DAY });
  } }) };
  const campaign = new ReferralCampaign(context, environment);
  const participants = Array.from({ length: 250 }, () => crypto.randomUUID());
  const startedAt = performance.now();
  for (let offset = 0; offset < participants.length; offset += 25) {
    await Promise.all(participants.slice(offset, offset + 25).map(async participant => {
      assert.equal((await call(campaign, "/enroll", { accountId: participant, termsVersion: REFERRAL_TERMS_VERSION, consent: true })).status, 200);
      const invitations = await Promise.all(Array.from({ length: 3 }, () => call(campaign, "/invite", { accountId: participant })));
      assert.ok(invitations.every(result => result.status === 200));
      assert.equal(new Set(invitations.map(result => result.body.shareUrl)).size, 1);
    }));
  }
  assert.equal([...context.storage.sql.exec("SELECT COUNT(*) AS total FROM invites")][0].total, participants.length);
  assert.equal([...context.storage.sql.exec("SELECT COUNT(DISTINCT token) AS total FROM invites")][0].total, participants.length);
  assert.equal([...context.storage.sql.exec("SELECT COUNT(*) AS total FROM subject_history")][0].total, participants.length);
  assert.equal([...context.storage.sql.exec("SELECT COUNT(*) AS total FROM awards")][0].total, 0);
  testContext.diagnostic(`Local SQLite burst: 1,000 requests in ${Math.round(performance.now() - startedAt)}ms; not a Cloudflare/provider throughput guarantee.`);
});

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
