import test from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { FriendDirectory } from "../src/friend-directory.js";
import { deriveFriendIdentity, sha256Hex } from "../src/crypto.js";

const ENV = { LUDOPROOF_SESSION_HMAC_KEY: "lp_test_session_hmac_key_1234567890abcdef" };
const ID1 = "00000000-0000-4000-8000-000000000001";
const ID2 = "00000000-0000-4000-8000-000000000002";
const ID3 = "00000000-0000-4000-8000-000000000003";

function setupDirectory() {
  const db = new DatabaseSync(":memory:");
  const sql = {
    exec(query, ...values) {
      if (values.length === 0 && !/^\s*(SELECT|WITH|PRAGMA)/i.test(query)) {
        db.exec(query);
        return { rowsWritten: 0 };
      }
      const statement = db.prepare(query);
      return /^\s*(SELECT|WITH|PRAGMA)/i.test(query)
        ? statement.all(...values)
        : { rowsWritten: statement.run(...values).changes };
    },
  };
  const storage = {
    sql,
    transactionSync(fn) {
      db.exec("BEGIN");
      try {
        const result = fn();
        db.exec("COMMIT");
        return result;
      } catch (error) {
        db.exec("ROLLBACK");
        throw error;
      }
    },
  };
  const context = { storage, blockConcurrencyWhile(fn) { this.ready = fn(); return this.ready; } };
  const room = new FriendDirectory(context, ENV);
  return { room, context, sql, db };
}

async function register(sql, requestId, displayName) {
  const player = await deriveFriendIdentity(ENV, requestId);
  const tokenHash = await sha256Hex("ludoproof:friend-token-hash:v1:" + player.friendToken);
  sql.exec(
    "INSERT INTO friend_profiles (friend_id, token_hash, registration_request_id, display_name, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?)",
    player.friendId, tokenHash, requestId, displayName, Date.now(), Date.now(),
  );
  return player;
}
function post(path, body, token) {
  return new Request("https://friends" + path, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: "Bearer " + token },
    body: JSON.stringify(body),
  });
}
function get(path, token) {
  return new Request("https://friends" + path, { headers: { authorization: "Bearer " + token } });
}

test("block revokes friendship and pending access, remains effective in both directions", async () => {
  const { room, context, sql, db } = setupDirectory();
  try {
    await context.ready;
    const a = await register(sql, ID1, "Alice");
    const b = await register(sql, ID2, "Bob");
    const [first, second] = [a.friendId, b.friendId].sort();
    sql.exec("INSERT INTO friendships(friend_a, friend_b, created_at) VALUES(?, ?, ?)", first, second, Date.now());
    assert.equal((await room.fetch(post("/block", { friendId: b.friendId }, a.friendToken))).status, 200);
    assert.equal((await room.fetch(post("/request", { friendId: a.friendId }, b.friendToken))).status, 403);
    assert.equal((await room.fetch(post("/request", { friendId: b.friendId }, a.friendToken))).status, 403);
    const snapshot = await (await room.fetch(get("/snapshot", a.friendToken))).json();
    assert.deepEqual(snapshot.blockedFriends, [b.friendId]);
    assert.equal(snapshot.friends.length, 0);
    assert.equal((await room.fetch(post("/unblock", { friendId: b.friendId }, a.friendToken))).status, 200);
    const unblocked = await (await room.fetch(get("/snapshot", a.friendToken))).json();
    assert.deepEqual(unblocked.blockedFriends, []);
    assert.equal(unblocked.friends.length, 0, "unblock must never restore previous friendship");
    assert.equal((await room.fetch(post("/request", { friendId: a.friendId }, b.friendToken))).status, 200);
  } finally { db.close(); }
});

test("reports are authenticated, bounded, idempotent and accept only enumerated reasons", async () => {
  const { room, context, sql, db } = setupDirectory();
  try {
    await context.ready;
    const a = await register(sql, ID1, "Alice");
    const b = await register(sql, ID2, "Bob");
    const id = "00000000-0000-4000-8000-000000000001";
    const report = { friendId: b.friendId, reason: "SPAM", clientRequestId: id };
    assert.equal((await room.fetch(post("/report", report, a.friendToken))).status, 201);
    const replay = await room.fetch(post("/report", report, a.friendToken));
    assert.equal(replay.status, 200);
    assert.equal((await replay.json()).replayed, true);
    assert.equal((await room.fetch(post("/report", { ...report, reason: "HATE" }, a.friendToken))).status, 409);
    assert.equal((await room.fetch(post("/report", { ...report, clientRequestId: ID3, reason: "UNKNOWN" }, a.friendToken))).status, 400);
    assert.equal((await room.fetch(post("/report", { ...report, friendId: a.friendId }, a.friendToken))).status, 400);
    assert.equal((await room.fetch(post("/report", report, "lf_invalid"))).status, 401);
    for (let i = 2; i <= 10; i++) {
      const requestId = "00000000-0000-4000-8000-" + String(i).padStart(12, "0");
      assert.equal((await room.fetch(post("/report", { ...report, clientRequestId: requestId }, a.friendToken))).status, 201);
    }
    const beyondLimit = { ...report, clientRequestId: "00000000-0000-4000-8000-000000000011" };
    assert.equal((await room.fetch(post("/report", beyondLimit, a.friendToken))).status, 429);
    const listed = await room.fetch(post("/moderation/list", {}, a.friendToken));
    assert.equal(listed.status, 200);
    const reportQueue = (await listed.json()).reports;
    assert.equal(reportQueue.length, 10);
    const reviewerId = "00000000-0000-4000-8000-000000000099";
    const decision = { reportId: id, outcome: "REVIEWED", reviewerId };
    const resolved = await room.fetch(post("/moderation/resolve", decision, a.friendToken));
    assert.equal(resolved.status, 200);
    assert.equal((await resolved.json()).replayed, false);
    const replayResolution = await room.fetch(post("/moderation/resolve", decision, a.friendToken));
    assert.equal((await replayResolution.json()).replayed, true);
    const pending = await (await room.fetch(post("/moderation/list", {}, a.friendToken))).json();
    assert.equal(pending.reports.length, 9);
  } finally { db.close(); }
});
