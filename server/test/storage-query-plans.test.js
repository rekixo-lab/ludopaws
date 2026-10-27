import test from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { AccountDirectory } from "../src/account-directory.js";
import { ReferralCampaign } from "../src/referral-campaign.js";

function context() {
  const db = new DatabaseSync(":memory:");
  return { db, storage: { sql: { exec: (sql, ...args) => {
    const stmt = db.prepare(sql);
    if (stmt.columns().length) return stmt.all(...args);
    stmt.run(...args); return [];
  } } }, blockConcurrencyWhile: action => action() };
}
test("expiry deletes and account/time lookups use indexes after repeat-safe migrations", () => {
  const directory = context();
  new AccountDirectory(directory, {}); new AccountDirectory(directory, {});
  const plan = (ctx, query, ...args) => ctx.db.prepare("EXPLAIN QUERY PLAN " + query).all(...args).map(row => row.detail).join("\n");
  assert.match(plan(directory, "DELETE FROM google_nonces WHERE expires_at < ?", 100), /idx_google_nonces_expiry/);
  const referral = context();
  new ReferralCampaign(referral, {}); new ReferralCampaign(referral, {});
  assert.match(plan(referral, "DELETE FROM challenges WHERE expires_at < ?", 100), /referral_challenge_expiry/);
  assert.match(plan(referral, "DELETE FROM subject_history WHERE expires_at < ?", 100), /referral_history_expiry/);
  assert.match(plan(referral, "SELECT match_id FROM qualifying_matches WHERE account_id = ? AND started_at >= ? AND finished_at <= ?", "test", 0, 100), /referral_match_account_time/);
  assert.match(plan(referral, "SELECT inviter FROM awards WHERE status = 'PENDING' ORDER BY created_at"), /referral_award_pending/);
});
