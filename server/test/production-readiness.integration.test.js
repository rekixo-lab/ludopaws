import test from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { FriendDirectory } from "../src/friend-directory.js";
import { LeaderboardRoom } from "../src/leaderboard-room.js";
import {
  FriendConversation,
  FriendRecentRoom,
  syncRecentFriends,
} from "../src/friend-social.js";
import { PlayerAccount } from "../src/player-account.js";
function context() {
  const db = new DatabaseSync(":memory:"),
    values = new Map();
  return {
    storage: {
      sql: {
        exec(sql, ...args) {
          const stmt = db.prepare(sql);
          const rows = stmt.columns().length ? stmt.all(...args) : [];
          rows.rowsWritten = stmt.columns().length
            ? 0
            : Number(stmt.run(...args).changes);
          return rows;
        },
      },
      transactionSync(f) {
        db.exec("BEGIN");
        try {
          const r = f();
          db.exec("COMMIT");
          return r;
        } catch (e) {
          db.exec("ROLLBACK");
          throw e;
        }
      },
      async get(k) {
        return structuredClone(values.get(k));
      },
      async put(k, v) {
        values.set(k, structuredClone(v));
      },
      async delete(k) {
        return values.delete(k);
      },
    },
    blockConcurrencyWhile(f) {
      return f();
    },
  };
}
const uuid = (n) => "00000000-0000-4000-8000-" + String(n).padStart(12, "0");
const secret = {
  LUDOPROOF_SESSION_HMAC_KEY: "test-only-production-regression-key-32-bytes",
};
async function call(obj, path, body, token) {
  const r = await obj.fetch(
    new Request("https://test.local" + path, {
      method: body == null ? "GET" : "POST",
      headers: {
        "content-type": "application/json",
        ...(token ? { authorization: "Bearer " + token } : {}),
      },
      ...(body == null ? {} : { body: JSON.stringify(body) }),
    }),
  );
  return { status: r.status, body: await r.json() };
}
function social() {
  const env = { ...secret };
  const ns = (Type) => {
    const objects = new Map();
    return {
      idFromName: (n) => n,
      get(n) {
        if (!objects.has(n)) objects.set(n, new Type(context(), env));
        return objects.get(n);
      },
    };
  };
  env.LUDOPROOF_FRIEND_RECENT = ns(FriendRecentRoom);
  env.LUDOPROOF_FRIEND_CONVERSATIONS = ns(FriendConversation);
  const dir = new FriendDirectory(context(), env);
  env.LUDOPROOF_FRIENDS = { idFromName: (n) => n, get: () => dir };
  return { env, dir };
}

test("answered Friend replay reports its committed answer", async () => {
  const { dir } = social();
  const a = (
      await call(dir, "/register", {
        clientRequestId: uuid(1),
        displayName: "Alpha",
      })
    ).body,
    b = (
      await call(dir, "/register", {
        clientRequestId: uuid(2),
        displayName: "Beta",
      })
    ).body;
  const sent = await call(
    dir,
    "/request",
    { friendId: b.friendId },
    a.friendToken,
  );
  assert.equal(
    (
      await call(
        dir,
        "/request/respond",
        { requestId: sent.body.requestId, accept: false },
        b.friendToken,
      )
    ).body.status,
    "DECLINED",
  );
  assert.equal(
    (
      await call(
        dir,
        "/request/respond",
        { requestId: sent.body.requestId, accept: true },
        b.friendToken,
      )
    ).body.status,
    "DECLINED",
  );
});
test("delayed finished-room sync cannot resurrect a deleted identity", async () => {
  const { env, dir } = social();
  const a = (
      await call(dir, "/register", {
        clientRequestId: uuid(3),
        displayName: "Alpha",
      })
    ).body,
    b = (
      await call(dir, "/register", {
        clientRequestId: uuid(4),
        displayName: "Beta",
      })
    ).body;
  const state = {
    status: "FINISHED",
    matchMode: "FRIENDS",
    matchId: "LPABCDEFGH",
    finishedAt: Date.now(),
    players: [
      { friendId: a.friendId, displayName: "Alpha" },
      { friendId: b.friendId, displayName: "Beta" },
    ],
  };
  await syncRecentFriends(env, state);
  assert.equal(
    (await call(dir, "/account/delete", { friendId: a.friendId })).status,
    200,
  );
  await syncRecentFriends(env, state);
  assert.deepEqual(
    (
      await call(env.LUDOPROOF_FRIEND_RECENT.get(b.friendId), "/list", {
        ownerFriendId: b.friendId,
      })
    ).body.recent,
    [],
  );
});
test("deletion of an empty recent sink also blocks future records", async () => {
  const room = new FriendRecentRoom(context()),
    ownerFriendId = "LPF-ABCD-EFGH-JKLM",
    opponentFriendId = "LPF-MNPQ-RSTU-VWXY";
  await call(room, "/purge-opponent", { ownerFriendId, opponentFriendId });
  assert.equal(
    (
      await call(room, "/record", {
        ownerFriendId,
        opponentFriendId,
        displayName: "Old name",
        matchId: "LPABCDEFGH",
        playedAt: Date.now(),
      })
    ).body.ignored,
    true,
  );
  assert.deepEqual(
    (await call(room, "/list", { ownerFriendId })).body.recent,
    [],
  );
});
test("deleted ranked participant does not suppress survivors; unknown profiles still reject", async () => {
  const room = new LeaderboardRoom(context(), secret),
    a = (await call(room, "/profile/register", { clientRequestId: uuid(5) }))
      .body,
    b = (await call(room, "/profile/register", { clientRequestId: uuid(6) }))
      .body;
  await call(room, "/profile/delete", { profileId: a.profileId });
  assert.equal(
    (
      await call(room, "/record", {
        matchId: "LPABCDEFGH",
        players: [
          { profileId: a.profileId, displayName: "Alpha", won: false },
          { profileId: b.profileId, displayName: "Beta", won: true },
        ],
      })
    ).status,
    200,
  );
  const list = (await call(room, "/list")).body;
  assert.equal(list.entries.length, 1);
  assert.equal(list.entries[0].displayName, "Beta");
  assert.equal(
    (
      await call(room, "/record", {
        matchId: "LPABCDEFGJ",
        players: [{ profileId: uuid(999), displayName: "Unknown", won: true }],
      })
    ).status,
    403,
  );
});
test("leaderboard missing limit returns a full default page", async () => {
  const room = new LeaderboardRoom(context(), secret);
  for (let n = 10; n < 13; n++) {
    const p = (
      await call(room, "/profile/register", { clientRequestId: uuid(n) })
    ).body;
    await call(room, "/record", {
      matchId: "LPABCDEFG" + String.fromCharCode(65 + n),
      players: [{ profileId: p.profileId, displayName: "Player", won: true }],
    });
  }
  assert.equal((await call(room, "/list")).body.entries.length, 3);
});
test("wallet free-equip mutations advance revision but reads do not", async () => {
  const room = new PlayerAccount(context(), secret),
    accountId = uuid(20),
    session = (
      await call(room, "/session/create", { accountId, displayName: "Player" })
    ).body;
  const first = await call(room, "/wallet/get", {
    accountId,
    sessionId: session.sessionId,
  });
  const next = await call(room, "/wallet/cosmetic/select", {
    accountId,
    sessionId: session.sessionId,
    cosmeticId: "dice_pumpkin",
  });
  assert.equal(next.status, 200);
  assert.ok(next.body.wallet.revision > first.body.wallet.revision);
  assert.equal(next.body.wallet.balance, first.body.wallet.balance);
  assert.equal(
    (
      await call(room, "/wallet/get", {
        accountId,
        sessionId: session.sessionId,
      })
    ).body.wallet.revision,
    next.body.wallet.revision,
  );
});


test("family conversation storage cannot send or list messages; deletion remains usable", async () => {
  const room = new FriendConversation(context(), { LUDOPROOF_AGE_SAFETY_MODE: "FAMILY_V1" });
  for (const path of ["/send", "/list"]) {
    assert.equal((await call(room, path, {})).status, 403);
  }
  assert.equal((await call(room, "/delete-for-friend", { friendId: "LPF-ABCD-EFGH-JKLM" })).status, 200);
});

test("Play refunds revoke once, advance wallet revisions and reject a late purchase grant", async () => {
  const room = new PlayerAccount(context(), secret), accountId = uuid(81);
  const session = (await call(room, "/session/create", { accountId })).body;
  const purchase = { accountId, sessionId: session.sessionId, productId: "gems_300",
    purchaseTokenHash: "a".repeat(64), purchaseLedgerId: "b".repeat(64) };
  const grant = await call(room, "/wallet/play-purchase", purchase);
  assert.equal(grant.status, 200, JSON.stringify(grant.body));
  const revoke = await call(room, "/wallet/play-revoke", {accountId, purchaseLedgerId: purchase.purchaseLedgerId});
  assert.equal(revoke.status, 200, JSON.stringify(revoke.body));
  const first = (await call(room, "/wallet/get", {accountId, sessionId:session.sessionId})).body.wallet;
  assert.equal(first.balance, 0);
  assert.ok(first.revision > grant.body.wallet.revision);
  assert.equal((await call(room, "/wallet/play-revoke", {accountId, purchaseLedgerId: purchase.purchaseLedgerId})).body.replay, true);
  assert.equal((await call(room, "/wallet/play-purchase", purchase)).body.error, "PLAY_PURCHASE_REVOKED");
  const early = {...purchase, productId: "remove_ads", purchaseLedgerId:"c".repeat(64), purchaseTokenHash:"d".repeat(64)};
  await call(room, "/wallet/play-revoke", {accountId, purchaseLedgerId:early.purchaseLedgerId});
  assert.equal((await call(room, "/wallet/play-purchase", early)).body.error, "PLAY_PURCHASE_REVOKED");
});

test("refunding Remove Ads preserves another valid entitlement and never makes gems negative", async () => {
  const room = new PlayerAccount(context(), secret), accountId = uuid(82);
  const session = (await call(room, "/session/create", {accountId})).body;
  for (const [token, ledger] of [["a","b"],["c","d"]]) {
    const r = await call(room,"/wallet/play-purchase",{accountId, sessionId:session.sessionId, productId:"remove_ads",
      purchaseTokenHash:token.repeat(64),purchaseLedgerId:ledger.repeat(64)});
    assert.equal(r.status,200);
  }
  await call(room,"/wallet/play-revoke",{accountId,purchaseLedgerId:"b".repeat(64)});
  let wallet = (await call(room,"/wallet/get",{accountId,sessionId:session.sessionId})).body.wallet;
  assert.equal(wallet.adsRemoved,true);
  assert.equal(wallet.balance,5);
  await call(room,"/wallet/play-revoke",{accountId,purchaseLedgerId:"d".repeat(64)});
  wallet = (await call(room,"/wallet/get",{accountId,sessionId:session.sessionId})).body.wallet;
  assert.equal(wallet.adsRemoved,false);
  assert.equal(wallet.balance,0);
});

test("old purchase owners enroll on wallet access and an already-refunded entitlement is removed", async () => {
  const { StorePurchaseLedger } = await import("../src/store-purchase-ledger.js");
  const { billingAccountId } = await import("../src/account-security.js");
  const accountId = uuid(83), ledgerId = "b".repeat(64), ledger = new StorePurchaseLedger(context()), env = {...secret};
  const room = new PlayerAccount(context(), env);
  const session = (await call(room,"/session/create",{accountId})).body;
  await call(ledger,"/reserve",{accountId:await billingAccountId(accountId),productId:"remove_ads",gemAmount:5,removeAds:true});
  await call(ledger,"/complete",{accountId:await billingAccountId(accountId),productId:"remove_ads"});
  await call(room,"/wallet/play-purchase",{accountId,sessionId:session.sessionId,productId:"remove_ads",purchaseTokenHash:"a".repeat(64),purchaseLedgerId:ledgerId});
  await call(ledger,"/revoke",{});
  env.LUDOPROOF_STORE_PURCHASES = {idFromName:n=>n,get:()=>ledger};
  const wallet = await call(room,"/wallet/get",{accountId,sessionId:session.sessionId});
  assert.equal(wallet.status,200,JSON.stringify(wallet.body));
  assert.equal(wallet.body.wallet.adsRemoved,false);
  assert.equal(wallet.body.wallet.balance,0);
  const state = (await call(ledger,"/status")).body.purchase;
  assert.equal(state.ownerAccountId,accountId);
  assert.equal(state.state,"REVOKED");
});
