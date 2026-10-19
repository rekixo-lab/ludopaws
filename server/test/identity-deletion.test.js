import test from "node:test";
import assert from "node:assert/strict";
import { FriendDirectory } from "../src/friend-directory.js";
import { LeaderboardRoom } from "../src/leaderboard-room.js";
import { FriendConversation, FriendRecentRoom } from "../src/friend-social.js";

const SECRET = "lp_test_session_hmac_key_1234567890abcdef";
const REQUEST_ID = "00000000-0000-4000-8000-000000000001";

function request(path, body) {
  return new Request("https://unit.test" + path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
}

function namespace() {
  return {
    idFromName(name) { return name; },
    get() {
      return { async fetch() { return Response.json({ ok: true }); } };
    },
  };
}

function makeContext(sql) {
  return {
    storage: {
      sql,
      transactionSync(callback) { return callback(); },
    },
    blockConcurrencyWhile(callback) { return callback(); },
  };
}

class FriendSql {
  profiles = new Map();
  deletedProfiles = new Set();
  deletedRegistrationHashes = new Set();
  requests = new Map();
  invites = new Map();
  friendships = new Map();
  conversationIndex = new Map();
  recentIndex = new Map();
  blocks = new Map();
  reports = new Map();

  exec(statement, ...params) {
    const sql = String(statement).replace(/\s+/g, " ").trim().toLowerCase();
    if (sql.startsWith("create table") || sql.startsWith("create index")) return [];
    if (sql.startsWith("select friend_id from deleted_friend_profiles")) {
      return this.deletedProfiles.has(params[0]) ? [{ friend_id: params[0] }] : [];
    }
    if (sql.startsWith("select request_hash from deleted_friend_registration_requests")) {
      return this.deletedRegistrationHashes.has(params[0]) ? [{ request_hash: params[0] }] : [];
    }
    if (sql.startsWith("select friend_id, registration_request_id from friend_profiles")) {
      return [...this.profiles.values()]
        .filter(row => row.friend_id === params[0] || row.registration_request_id === params[1] || row.token_hash === params[2])
        .slice(0, 1)
        .map(row => ({ friend_id: row.friend_id, registration_request_id: row.registration_request_id }));
    }
    if (sql.startsWith("select registration_request_id from friend_profiles")) {
      const row = this.profiles.get(params[0]);
      return row ? [{ registration_request_id: row.registration_request_id }] : [];
    }
    if (sql.startsWith("select friend_id from friend_profiles where friend_id in (?, ?)")) {
      return params.filter(id => this.profiles.has(id)).map(friend_id => ({ friend_id }));
    }
    if (sql.startsWith("select friend_id from friend_profiles")) {
      return this.profiles.has(params[0]) ? [{ friend_id: params[0] }] : [];
    }
    if (sql.startsWith("select friend_a, friend_b from friendships")) {
      return [...this.friendships.values()].filter(row => row.friend_a === params[0] || row.friend_b === params[1]).map(row => ({ friend_a: row.friend_a, friend_b: row.friend_b }));
    }
    if (sql.startsWith("select sender_id, receiver_id from friend_requests")) {
      return [...this.requests.values()].filter(row => row.sender_id === params[0] || row.receiver_id === params[1]).map(row => ({ sender_id: row.sender_id, receiver_id: row.receiver_id }));
    }
    if (sql.startsWith("select sender_id, receiver_id from friend_invites")) {
      return [...this.invites.values()].filter(row => row.sender_id === params[0] || row.receiver_id === params[1]).map(row => ({ sender_id: row.sender_id, receiver_id: row.receiver_id }));
    }
    if (sql.startsWith("select other_friend_id from friend_conversation_index")) {
      return [...this.conversationIndex.values()].filter(row => row.friend_id === params[0]).map(row => ({ other_friend_id: row.other_friend_id }));
    }
    if (sql.startsWith("select owner_friend_id from friend_recent_index")) {
      return [...this.recentIndex.values()].filter(row => row.opponent_friend_id === params[0]).map(row => ({ owner_friend_id: row.owner_friend_id }));
    }
    if (sql.startsWith("select blocker_id, blocked_id from friend_blocks")) {
      return [...this.blocks.values()].filter(row => row.blocker_id === params[0] || row.blocked_id === params[1]);
    }
    if (sql.startsWith("insert into friend_profiles")) {
      const [friendId, tokenHash, registrationRequestId, displayName, createdAt, lastSeenAt] = params;
      this.profiles.set(friendId, { friend_id: friendId, token_hash: tokenHash, registration_request_id: registrationRequestId, display_name: displayName, created_at: createdAt, last_seen_at: lastSeenAt });
      return [{ rowsWritten: 1 }];
    }
    if (sql.startsWith("update friend_profiles")) {
      const [tokenHash, displayName, lastSeenAt, friendId] = params;
      const row = this.profiles.get(friendId);
      if (row) Object.assign(row, { token_hash: tokenHash, display_name: displayName, last_seen_at: lastSeenAt });
      return [{ rowsWritten: row ? 1 : 0 }];
    }
    if (sql.startsWith("insert or ignore into deleted_friend_profiles")) {
      this.deletedProfiles.add(params[0]);
      return [];
    }
    if (sql.startsWith("insert or ignore into deleted_friend_registration_requests")) {
      this.deletedRegistrationHashes.add(params[0]);
      return [];
    }
    if (sql.startsWith("delete from friend_requests")) {
      for (const [key, row] of this.requests) if (row.sender_id === params[0] || row.receiver_id === params[1]) this.requests.delete(key);
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("delete from friend_invites")) {
      for (const [key, row] of this.invites) if (row.sender_id === params[0] || row.receiver_id === params[1]) this.invites.delete(key);
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("delete from friendships")) {
      for (const [key, row] of this.friendships) if (row.friend_a === params[0] || row.friend_b === params[1]) this.friendships.delete(key);
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("delete from friend_conversation_index")) {
      for (const [key, row] of this.conversationIndex) if (row.friend_id === params[0] || row.other_friend_id === params[1]) this.conversationIndex.delete(key);
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("delete from friend_recent_index")) {
      for (const [key, row] of this.recentIndex) if (row.owner_friend_id === params[0] || row.opponent_friend_id === params[1]) this.recentIndex.delete(key);
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("delete from friend_blocks")) {
      for (const [key, row] of this.blocks) {
        if (row.blocker_id === params[0] || row.blocked_id === params[1]) this.blocks.delete(key);
      }
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("delete from friend_reports")) {
      for (const [key, row] of this.reports) {
        if (row.reporter_id === params[0] || row.target_id === params[1]) this.reports.delete(key);
      }
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("delete from friend_profiles")) {
      this.profiles.delete(params[0]);
      return { rowsWritten: 1 };
    }
    throw new Error("Unexpected FriendDirectory SQL in identity-deletion test: " + sql);
  }
}

class LeaderboardSql {
  profiles = new Map();
  entries = new Map();
  deletedProfiles = new Set();
  deletedRegistrationHashes = new Set();

  exec(statement, ...params) {
    const sql = String(statement).replace(/\s+/g, " ").trim().toLowerCase();
    if (sql.startsWith("create table") || sql.startsWith("create index")) return [];
    if (sql.startsWith("select profile_id from deleted_leaderboard_profiles")) {
      return this.deletedProfiles.has(params[0]) ? [{ profile_id: params[0] }] : [];
    }
    if (sql.startsWith("select request_hash from deleted_leaderboard_registration_requests")) {
      return this.deletedRegistrationHashes.has(params[0]) ? [{ request_hash: params[0] }] : [];
    }
    if (sql.startsWith("select profile_id, registration_request_id from leaderboard_profiles_v2")) {
      return [...this.profiles.values()]
        .filter(row => row.profile_id === params[0] || row.registration_request_id === params[1] || row.token_hash === params[2])
        .slice(0, 1)
        .map(row => ({ profile_id: row.profile_id, registration_request_id: row.registration_request_id }));
    }
    if (sql.startsWith("select registration_request_id from leaderboard_profiles_v2")) {
      const row = this.profiles.get(params[0]);
      return row ? [{ registration_request_id: row.registration_request_id }] : [];
    }
    if (sql.startsWith("insert into leaderboard_profiles_v2")) {
      const [profileId, tokenHash, registrationRequestId, createdAt, lastSeenAt] = params;
      this.profiles.set(profileId, { profile_id: profileId, token_hash: tokenHash, registration_request_id: registrationRequestId, created_at: createdAt, last_seen_at: lastSeenAt });
      return [{ rowsWritten: 1 }];
    }
    if (sql.startsWith("update leaderboard_profiles_v2")) {
      const [tokenHash, lastSeenAt, profileId] = params;
      const row = this.profiles.get(profileId);
      if (row) Object.assign(row, { token_hash: tokenHash, last_seen_at: lastSeenAt });
      return [{ rowsWritten: row ? 1 : 0 }];
    }
    if (sql.startsWith("insert or ignore into deleted_leaderboard_profiles")) {
      this.deletedProfiles.add(params[0]);
      return [];
    }
    if (sql.startsWith("insert or ignore into deleted_leaderboard_registration_requests")) {
      this.deletedRegistrationHashes.add(params[0]);
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("delete from leaderboard_entries_v2")) {
      this.entries.delete(params[0]);
      return { rowsWritten: 1 };
    }
    if (sql.startsWith("delete from leaderboard_profiles_v2")) {
      this.profiles.delete(params[0]);
      return { rowsWritten: 1 };
    }
    throw new Error("Unexpected LeaderboardRoom SQL in identity-deletion test: " + sql);
  }
}

test("a deleted Friend identity and its registration key cannot recreate the identity", async () => {
  const sql = new FriendSql();
  const env = {
    LUDOPROOF_SESSION_HMAC_KEY: SECRET,
    LUDOPROOF_FRIEND_CONVERSATIONS: namespace(),
    LUDOPROOF_FRIEND_RECENT: namespace(),
  };
  const directory = new FriendDirectory(makeContext(sql), env);
  const createdResponse = await directory.fetch(request("/register", { clientRequestId: REQUEST_ID, displayName: "Player One" }));
  assert.equal(createdResponse.status, 200);
  const created = await createdResponse.json();

  sql.requests.set("request-1", { sender_id: created.friendId, receiver_id: "LPF-MNPQ-RSTU-VWXY" });
  sql.invites.set("invite-1", { sender_id: "LPF-MNPQ-RSTU-VWXY", receiver_id: created.friendId });
  sql.friendships.set("friendship-1", { friend_a: created.friendId, friend_b: "LPF-MNPQ-RSTU-VWXY" });
  sql.conversationIndex.set("conversation-1", { friend_id: created.friendId, other_friend_id: "LPF-MNPQ-RSTU-VWXY" });
  sql.conversationIndex.set("conversation-2", { friend_id: "LPF-MNPQ-RSTU-VWXY", other_friend_id: created.friendId });
  sql.recentIndex.set("recent-1", { owner_friend_id: "LPF-MNPQ-RSTU-VWXY", opponent_friend_id: created.friendId });
  sql.recentIndex.set("recent-2", { owner_friend_id: created.friendId, opponent_friend_id: "LPF-MNPQ-RSTU-VWXY" });

  const deletedResponse = await directory.fetch(request("/account/delete", { friendId: created.friendId }));
  assert.equal(deletedResponse.status, 200);
  assert.equal((await deletedResponse.json()).deleted, true);
  assert.equal(sql.profiles.has(created.friendId), false);
  assert.equal(sql.deletedProfiles.has(created.friendId), true);
  assert.equal(sql.requests.size, 0);
  assert.equal(sql.invites.size, 0);
  assert.equal(sql.friendships.size, 0);
  assert.equal(sql.conversationIndex.size, 0);
  assert.equal(sql.recentIndex.size, 0);

  const replay = await directory.fetch(request("/register", { clientRequestId: REQUEST_ID, displayName: "Player One" }));
  assert.equal(replay.status, 410);
  assert.equal((await replay.json()).error, "FRIEND_IDENTITY_DELETED");
});

test("a deleted leaderboard profile and its registration key cannot recreate the profile", async () => {
  const sql = new LeaderboardSql();
  const env = { LUDOPROOF_SESSION_HMAC_KEY: SECRET };
  const leaderboard = new LeaderboardRoom(makeContext(sql), env);
  const createdResponse = await leaderboard.fetch(request("/profile/register", { clientRequestId: REQUEST_ID }));
  assert.equal(createdResponse.status, 200);
  const created = await createdResponse.json();

  sql.entries.set(created.profileId, { profile_id: created.profileId, display_name: "Player One", games: 2, wins: 1, points: 10, updated_at: Date.now() });
  const deletedResponse = await leaderboard.fetch(request("/profile/delete", { profileId: created.profileId }));
  assert.equal(deletedResponse.status, 200);
  assert.equal((await deletedResponse.json()).deleted, true);
  assert.equal(sql.profiles.has(created.profileId), false);
  assert.equal(sql.deletedProfiles.has(created.profileId), true);
  assert.equal(sql.entries.has(created.profileId), false);

  const replay = await leaderboard.fetch(request("/profile/register", { clientRequestId: REQUEST_ID }));
  assert.equal(replay.status, 410);
  assert.equal((await replay.json()).error, "LEADERBOARD_PROFILE_DELETED");
});


class MemoryObjectStorage {
  constructor() { this.values = new Map(); }
  async get(key) {
    const value = this.values.get(key);
    return value == null ? value : structuredClone(value);
  }
  async put(key, value) { this.values.set(key, structuredClone(value)); }
  async delete(key) { return this.values.delete(key); }
}

function statefulNamespace(factory, failPath = null) {
  const instances = new Map();
  const calls = [];
  let failureInjected = false;
  return {
    calls,
    idFromName(name) { return name; },
    get(name) {
      if (!instances.has(name)) instances.set(name, factory());
      const instance = instances.get(name);
      return {
        async fetch(req) {
          const path = new URL(req.url).pathname;
          calls.push({ id: name, path });
          if (path === failPath && !failureInjected) {
            failureInjected = true;
            return Response.json({ error: "INJECTED_SOCIAL_DELETE_FAILURE" }, { status: 503 });
          }
          return instance.fetch(req);
        },
      };
    },
  };
}

test("FriendDirectory deletion retries safely after conversation or recent-player Durable Object failures", async () => {
  const failureCases = [
    { stage: "conversation", route: "/delete-for-friend" },
    { stage: "peer recent history", route: "/purge-opponent" },
    { stage: "owner recent history", route: "/delete-owner" },
  ];
  const peerId = "LPF-MNPQ-RSTU-VWXY";

  for (const failureCase of failureCases) {
    const sql = new FriendSql();
    const conversations = statefulNamespace(
      () => new FriendConversation({ storage: new MemoryObjectStorage() }),
      failureCase.stage === "conversation" ? failureCase.route : null,
    );
    const recent = statefulNamespace(
      () => new FriendRecentRoom({ storage: new MemoryObjectStorage() }),
      failureCase.stage === "peer recent history" || failureCase.stage === "owner recent history"
        ? failureCase.route
        : null,
    );
    const env = {
      LUDOPROOF_SESSION_HMAC_KEY: SECRET,
      LUDOPROOF_FRIEND_CONVERSATIONS: conversations,
      LUDOPROOF_FRIEND_RECENT: recent,
    };
    const directory = new FriendDirectory(makeContext(sql), env);
    const registered = await directory.fetch(request("/register", {
      clientRequestId: REQUEST_ID,
      displayName: "Player One",
    }));
    assert.equal(registered.status, 200);
    const profile = await registered.json();
    const friendId = profile.friendId;
    const pairId = [friendId, peerId].sort().join(":");

    sql.friendships.set("friendship-1", { friend_a: friendId, friend_b: peerId });
    sql.conversationIndex.set("conversation-1", { friend_id: friendId, other_friend_id: peerId });
    sql.conversationIndex.set("conversation-2", { friend_id: peerId, other_friend_id: friendId });
    sql.recentIndex.set("recent-peer", { owner_friend_id: peerId, opponent_friend_id: friendId });
    sql.recentIndex.set("recent-owner", { owner_friend_id: friendId, opponent_friend_id: peerId });

    const conversation = conversations.get(pairId);
    const message = await conversation.fetch(request("/send", {
      senderId: friendId,
      receiverId: peerId,
      text: "Deletion retry fixture",
      clientRequestId: REQUEST_ID,
    }));
    assert.equal(message.status, 201, "conversation fixture should be persisted");

    for (const ownerFriendId of [friendId, peerId]) {
      const opponentFriendId = ownerFriendId === friendId ? peerId : friendId;
      const saved = await recent.get(ownerFriendId).fetch(request("/record", {
        ownerFriendId,
        opponentFriendId,
        displayName: "Player One",
        matchId: "LPA2B3C4D5",
        playedAt: Date.now(),
      }));
      assert.equal(saved.status, 200, "recent-player fixture should be persisted");
    }

    const first = await directory.fetch(request("/account/delete", { friendId }));
    assert.equal(first.status, 503, failureCase.stage + " failure must be returned, not hidden");
    assert.equal(sql.profiles.has(friendId), true, "profile should remain until all external cleanup succeeds");
    assert.equal(sql.deletedProfiles.has(friendId), false, "profile tombstone must not commit before external cleanup finishes");
    assert.equal(sql.friendships.size, 1, "relationship index should remain available to drive a retry");

    const retry = await directory.fetch(request("/account/delete", { friendId }));
    assert.equal(retry.status, 200, failureCase.stage + " retry should finish deletion");
    assert.equal((await retry.json()).deleted, true);
    assert.equal(sql.profiles.has(friendId), false);
    assert.equal(sql.deletedProfiles.has(friendId), true);
    assert.equal(sql.requests.size, 0);
    assert.equal(sql.invites.size, 0);
    assert.equal(sql.friendships.size, 0);
    assert.equal(sql.conversationIndex.size, 0);
    assert.equal(sql.recentIndex.size, 0);
    assert.equal(
      (failureCase.stage === "conversation" ? conversations.calls : recent.calls)
        .filter(call => call.path === failureCase.route).length >= 2,
      true,
      failureCase.stage + " downstream route should be retried",
    );

    const deletedRegistrationReplay = await directory.fetch(request("/register", {
      clientRequestId: REQUEST_ID,
      displayName: "Player One",
    }));
    assert.equal(deletedRegistrationReplay.status, 410, "deleted identity must stay tombstoned after retry");

    for (const ownerFriendId of [friendId, peerId]) {
      const listed = await recent.get(ownerFriendId).fetch(request("/list", { ownerFriendId }));
      assert.equal(listed.status, 200);
      assert.deepEqual((await listed.json()).recent, [], "deleted player must be absent from all recent-player histories");
    }
    const emptyConversation = await conversation.fetch(request("/list", { meId: friendId, otherId: peerId }));
    assert.equal(emptyConversation.status, 200);
    assert.deepEqual((await emptyConversation.json()).messages, [], "conversation contents should be gone after retry");
  }
});
