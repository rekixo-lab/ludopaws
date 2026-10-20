import test from "node:test";
import assert from "node:assert/strict";
import { rulesetHashForState } from "../src/match-room.js";
import { optionalTargetPlayerCount, normalizeMatchMode, optionalLeaderboardProfileId } from "../src/match-room-validation.js";
import { readJson as readMatchJson, json as matchJson, errorResponse as matchError } from "../src/match-room-http.js";
import { normalizeFriendId, normalizeMatchId, normalizeActionId, canonicalPair, publicProfile, friendshipRequestId, friendInviteId, friendTokenHash } from "../src/friend-directory-identity.js";
import { readJson as readFriendJson, errorResponse as friendError } from "../src/friend-directory-http.js";
import { initializeFriendDirectorySchema } from "../src/friend-directory-schema.js";

test("match input validators retain their existing error contracts", () => {
  assert.equal(optionalTargetPlayerCount(null), null);
  assert.equal(optionalTargetPlayerCount("4"), 4);
  assert.throws(() => optionalTargetPlayerCount("5"), /targetPlayerCount/);
  assert.equal(normalizeMatchMode(" friends ", 2), "FRIENDS");
  assert.throws(() => normalizeMatchMode("TEAM_UP", 2), /exactly four/);
  assert.equal(optionalLeaderboardProfileId(""), null);
  assert.throws(() => optionalLeaderboardProfileId("invalid"), /UUID v4/);
  assert.equal(typeof rulesetHashForState, "function");
});
test("match HTTP envelopes remain non-cacheable and errors redacted", async () => {
  assert.deepEqual(await readMatchJson(new Request("https://test/", { method: "POST", body: "{}" })), {});
  await assert.rejects(readMatchJson(new Request("https://test/", { method: "POST", body: "[]" })), /JSON object/);
  const r = matchJson(201, { ok: true });
  assert.equal(r.headers.get("cache-control"), "no-store");
  assert.equal(r.headers.get("x-content-type-options"), "nosniff");
  assert.equal((await matchError(new Error("private")).json()).message, "internal server error");
});
test("friend identity normalization, stable hashes and presence projection", async () => {
  assert.equal(normalizeFriendId(" lpf-abcd-efgh-jklm "), "LPF-ABCD-EFGH-JKLM");
  assert.throws(() => normalizeFriendId("bad"), /Friend ID/);
  assert.equal(normalizeMatchId(" lpabcdefgh "), "LPABCDEFGH");
  assert.equal(normalizeActionId("FRQ-0123456789ABCDEF0123", "FRQ"), "FRQ-0123456789ABCDEF0123");
  assert.deepEqual(canonicalPair("B", "A"), ["A", "B"]);
  const p = { friend_id: "LPF-ABCD-EFGH-JKLM", display_name: "A", last_seen_at: 1000 };
  assert.equal(publicProfile(p, 1000).online, true);
  assert.equal(publicProfile(p, 76001).online, false);
  assert.equal(await friendshipRequestId("a", "b"), await friendshipRequestId("a", "b"));
  assert.match(await friendInviteId("key"), /^FIV-[A-F0-9]{20}$/);
  assert.match(await friendTokenHash("secret"), /^[0-9a-f]{64}$/);
});
test("friend HTTP parser retains media-type and size limits", async () => {
  const r = (body,type) => new Request("https://test/", { method:"POST", body, headers:{"content-type":type} });
  await assert.rejects(readFriendJson(r("{}", "text/plain")), e => e.status === 415);
  await assert.rejects(readFriendJson(r("x".repeat(17000), "application/json")), e => e.status === 413);
  assert.deepEqual(await readFriendJson(r("{}", "application/json")), {});
  assert.equal((await friendError(new Error("private")).json()).message,"internal server error");
});
test("friends schema still creates core tables", () => {
  const statements = [];
  initializeFriendDirectorySchema({ exec: sql => statements.push(sql) });
  const ddl = statements.join("\n");
  for (const t of ["friend_profiles","friend_requests","friendships","friend_invites","friend_blocks","friend_reports","friend_suspensions","friend_report_messages"]) {
    assert.ok(ddl.includes("CREATE TABLE IF NOT EXISTS "+t),t);
  }
  assert.ok(statements.length>=15);
});
