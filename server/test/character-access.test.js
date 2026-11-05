import test from "node:test";
import assert from "node:assert/strict";
import { issueAccessToken } from "../src/account-security.js";
import { assertHumanCharacterAccess, CHARACTER_ACCOUNT_HEADER } from "../src/character-access.js";
import { characterNeedsOwnership, LUDO_PAWS_CHARACTER_IDS } from "../src/ludo-paws-characters.js";

const ACCOUNT_ID = "00000000-0000-4000-8000-000000000001";
const SESSION_ID = "00000000-0000-4000-8000-000000000002";
const PROFILE_ID = "00000000-0000-4000-8000-000000000003";
const FRIEND_ID = "LPF-ABCD-EFGH-JKLM";
const PROFILE = { profileId: PROFILE_ID };
const SECRET = "character-ownership-test-secret-more-than-32-bytes";

function environment(owned, {
  active = true, walletAccountId = ACCOUNT_ID, linkedAccountId = ACCOUNT_ID,
} = {}) {
  return {
    LUDOPROOF_SESSION_HMAC_KEY: SECRET,
    LUDOPROOF_ACCOUNT_DIRECTORY: {
      idFromName: (id) => id,
      get: (id) => {
        assert.equal(id, "GLOBAL:V1");
        return {
          async fetch(request) {
            assert.equal(new URL(request.url).pathname, "/referral/identity");
            const identity = await request.json();
            assert.ok(identity.profileId === PROFILE_ID || identity.friendId === FRIEND_ID);
            return Response.json({ ok: true, accountId: linkedAccountId });
          },
        };
      },
    },
    LUDOPROOF_PLAYER_ACCOUNTS: {
      idFromName: (id) => id,
      get: (id) => {
        assert.equal(id, ACCOUNT_ID);
        return {
          async fetch(request) {
            assert.equal(new URL(request.url).pathname, "/wallet/get");
            const data = await request.json();
            assert.equal(data.accountId, ACCOUNT_ID);
            assert.equal(data.sessionId, SESSION_ID);
            if (!active) return Response.json({ error: "ACCOUNT_SESSION_INVALID" }, { status: 401 });
            return Response.json({
              ok: true,
              wallet: { accountId: walletAccountId, ownedCosmetics: owned },
            });
          },
        };
      },
    },
  };
}

async function withAccountToken(env) {
  const token = await issueAccessToken(env, ACCOUNT_ID, SESSION_ID);
  return new Request("https://game.example/matchmaking/search", {
    method: "POST",
    headers: { [CHARACTER_ACCOUNT_HEADER]: token },
  });
}

test("known free human paws require no account; all future roster members are locked by default", () => {
  const free = new Set(["dog", "goat", "duck", "cat"]);
  for (const id of LUDO_PAWS_CHARACTER_IDS) {
    assert.equal(characterNeedsOwnership(id), !free.has(id));
  }
  assert.equal(characterNeedsOwnership("panda"), true);
  assert.equal(characterNeedsOwnership("goat_lumi"), true);
});

test("free human paws remain playable even when account service is unavailable", async () => {
  await assert.doesNotReject(assertHumanCharacterAccess(new Request("https://game.example"), {}, "duck"));
});

test("premium human paws require a valid account token", async () => {
  await assert.rejects(
    assertHumanCharacterAccess(new Request("https://game.example"), environment([]), "panda", PROFILE),
    (err) => err.code === "CHARACTER_ACCOUNT_REQUIRED" && err.status === 403,
  );
});

test("server wallet ownership permits a purchased premium character", async () => {
  const env = environment(["character_panda"]);
  await assert.doesNotReject(assertHumanCharacterAccess(await withAccountToken(env), env, "panda", PROFILE));
});

test("another premium purchase or a refunded paw cannot be substituted", async () => {
  const env = environment(["character_monkey"]);
  await assert.rejects(
    assertHumanCharacterAccess(await withAccountToken(env), env, "panda", PROFILE),
    (err) => err.code === "CHARACTER_NOT_OWNED" && err.status === 403,
  );
});

test("an unlinked or different social profile cannot borrow a premium wallet", async () => {
  const env = environment(["character_panda"]);
  await assert.rejects(
    assertHumanCharacterAccess(await withAccountToken(env), env, "panda"),
    (err) => err.code === "CHARACTER_IDENTITY_REQUIRED" && err.status === 403,
  );
  const mismatched = environment(["character_panda"], { linkedAccountId: SESSION_ID });
  await assert.rejects(
    assertHumanCharacterAccess(await withAccountToken(mismatched), mismatched, "panda", PROFILE),
    (err) => err.code === "CHARACTER_ACCOUNT_MISMATCH" && err.status === 403,
  );
});

test("owned premium paw works for an identity-linked Friends account too", async () => {
  const env = environment(["character_panda"]);
  await assert.doesNotReject(assertHumanCharacterAccess(await withAccountToken(env), env, "panda", { friendId: FRIEND_ID }));
});

test("revoked sessions and mismatched wallet owners fail closed", async () => {
  const revoked = environment(["character_panda"], { active: false });
  await assert.rejects(
    assertHumanCharacterAccess(await withAccountToken(revoked), revoked, "panda", PROFILE),
    (err) => err.code === "CHARACTER_ACCOUNT_SESSION_INVALID" && err.status === 403,
  );
  const other = environment(["character_panda"], { walletAccountId: SESSION_ID });
  await assert.rejects(
    assertHumanCharacterAccess(await withAccountToken(other), other, "panda", PROFILE),
    (err) => err.code === "CHARACTER_WALLET_INVALID" && err.status === 503,
  );
});
