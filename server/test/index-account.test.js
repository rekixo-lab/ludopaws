import test from "node:test";
import assert from "node:assert/strict";
import accountWorker, { handleAccountRequest, rateLimitSignedAdMobCallback } from "../src/index-account.js";
import { issueAccessToken } from "../src/account-security.js";
import { sha256Hex } from "../src/crypto.js";

const ACCOUNT_A = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7";
const ACCOUNT_B = "1b3d4b94-e1f0-4bc9-9cd5-592b7e0a1f21";
const SESSION_A = "6382a0ee-f216-46f8-b10d-51411e1f38f2";
const SECRET = "test-session-hmac-key-that-is-long-enough-123456";

function makeRateGate(allowed = true) {
  return {
    idFromName(name) { return name; },
    get(name) {
      return {
        async fetch(request) {
          assert.equal(new URL(request.url).pathname, "/check");
          return allowed
            ? Response.json({ allowed: true, remaining: 119 })
            : Response.json(
                { allowed: false, remaining: 0 },
                { status: 429, headers: { "retry-after": "60" } },
              );
        },
      };
    },
  };
}

function makePlayerNamespace() {
  const calls = [];
  return {
    calls,
    idFromName(name) { return name; },
    get(accountId) {
      return {
        async fetch(request) {
          const url = new URL(request.url);
          const body = await request.json();
          calls.push({ accountId, path: url.pathname, body });
          if (url.pathname === "/session/check") {
            return Response.json({
              ok: true,
              accountId,
              profile: { accountId, displayName: "Ludo Player" },
            });
          }
          if (url.pathname === "/wallet/get") {
            return Response.json({
              ok: true,
              wallet: {
                accountId,
                balance: 42,
                adsRemoved: false,
                ownedCosmetics: ["dice_classic"],
                selectedCosmetics: { DICE: "dice_classic" },
                rewardedAdProgress: {},
              },
            });
          }
          return Response.json({ error: "NOT_FOUND", message: "unknown test route" }, { status: 404 });
        },
      };
    },
  };
}

function makeCatalogNamespace(defaultResponse, calls = []) {
  return {
    idFromName(name) { return name; },
    get(name) {
      return {
        async fetch(request) {
          const url = new URL(request.url);
          let requestBody = null;
          if (request.method === "POST") requestBody = await request.json();
          calls.push({ name, method: request.method, path: url.pathname, search: url.search, body: requestBody });
          if (url.pathname === "/catalog/draft/save" && requestBody) {
            return Response.json({ ok: true, revision: requestBody.expectedRevision + 1, catalogVersion: 0, draft: requestBody.item });
          }
          if (url.pathname === "/catalog/current") return Response.json(defaultResponse);
          if (url.pathname === "/catalog/admin-state") return Response.json({
            ...defaultResponse, drafts: {}, audit: [],
          });
          if (url.pathname === "/catalog/history") return Response.json({
            ...defaultResponse, versions: [], audit: [],
          });
          return Response.json({ ok: true, received: requestBody });
        },
      };
    },
  };
}

test("signed AdMob callbacks use isolated player buckets, not Google's shared IP", async () => {
  const requestsPerPlayer = new Map();
  const env = {
    LUDOPROOF_API_GATE: {
      idFromName: key => key,
      get: key => ({
        async fetch(request) {
          assert.equal(new URL(request.url).pathname, "/check");
          const n = (requestsPerPlayer.get(key) ?? 0) + 1;
          requestsPerPlayer.set(key, n);
          return n === 1
            ? Response.json({ allowed: true, remaining: 119 })
            : Response.json({ allowed: false, remaining: 0 }, {
              status: 429, headers: { "retry-after": "60" },
            });
        },
      }),
    },
  };
  const sharedIp = "203.0.113.90";
  const source = ip => new Request("https://api.example.test/api/admob/ssv", {
    headers: { "cf-connecting-ip": ip },
  });
  await rateLimitSignedAdMobCallback(env, source(sharedIp), ACCOUNT_A);
  await rateLimitSignedAdMobCallback(env, source(sharedIp), ACCOUNT_B);
  assert.equal(requestsPerPlayer.size, 2, "distinct verified players need distinct buckets");
  await assert.rejects(rateLimitSignedAdMobCallback(env, source("198.51.100.66"), ACCOUNT_A),
    error => error.status === 429 && error.code === "RATE_LIMITED");
  assert.equal(requestsPerPlayer.size, 2, "rotating Google source IP must not reset a player's limit");
  await assert.rejects(rateLimitSignedAdMobCallback(env, source(sharedIp), "not-a-signed-player"),
    error => error.status === 400 && error.code === "SSV_ACCOUNT_INVALID");
  assert.equal(requestsPerPlayer.size, 2);
});

test("malformed unsigned AdMob SSV is rejected before a signed-player quota is touched", async () => {
  let gateChecks = 0;
  const env = {
    LUDOPROOF_ADMOB_SSV_ENABLED: "true",
    LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-9987799703505828/5052207546",
    LUDOPROOF_API_GATE: {
      idFromName: () => { gateChecks++; throw new Error("must not allocate an account gate for unsigned traffic"); },
    },
  };
  const response = await handleAccountRequest(new Request(
    "https://api.example.test/api/admob/ssv?reward_amount=5&user_id=" + ACCOUNT_A,
  ), env);
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "SSV_SIGNATURE_MISSING");
  assert.equal(gateChecks, 0);
});

test("account wallet API derives the account from the verified bearer token, not URL input", async () => {
  const player = makePlayerNamespace();
  const env = {
    LUDOPROOF_SESSION_HMAC_KEY: SECRET,
    LUDOPROOF_API_GATE: makeRateGate(),
    LUDOPROOF_PLAYER_ACCOUNTS: player,
  };
  const now = Date.now();
  const token = await issueAccessToken(env, ACCOUNT_A, SESSION_A, now);
  const response = await handleAccountRequest(
    new Request(
      "https://api.example.test/api/wallet?accountId=" + ACCOUNT_B,
      { method: "GET", headers: { authorization: "Bearer " + token } },
    ),
    env,
  );

  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.wallet.accountId, ACCOUNT_A);
  assert.equal(body.wallet.balance, 42);
  assert.ok(player.calls.length >= 2);
  assert.ok(player.calls.every((call) => call.accountId === ACCOUNT_A));
  assert.ok(player.calls.every((call) => call.body.accountId === ACCOUNT_A));
  assert.ok(player.calls.some((call) => call.path === "/session/check"));
  assert.ok(player.calls.some((call) => call.path === "/wallet/get"));
});

test("catalog read requires a session and filters by status, schedule, and region with ETag support", async () => {
  const now = Date.now();
  const entry = (id, overrides = {}) => ({
    id, status: "PUBLISHED", contentReady: true,
    availability: { worldwide: true, allowedCountries: [], blockedCountries: [], startsAt: null, endsAt: null },
    ...overrides,
  });
  const state = {
    revision: 5,
    catalogVersion: 7,
    updatedAt: new Date(now).toISOString(),
    items: [
      entry("paw_global_01"),
      entry("paw_draft_01", { status: "DRAFT" }),
      entry("paw_archived_01", { status: "ARCHIVED" }),
      entry("paw_future_01", { availability: {
        worldwide: true, allowedCountries: [], blockedCountries: [],
        startsAt: new Date(now + 60_000).toISOString(), endsAt: null,
      } }),
      entry("paw_india_01", { availability: {
        worldwide: false, allowedCountries: ["IN"], blockedCountries: [],
        startsAt: null, endsAt: null,
      } }),
    ],
  };
  const catalog = makeCatalogNamespace(state);
  const env = {
    LUDOPROOF_SESSION_HMAC_KEY: SECRET,
    LUDOPROOF_API_GATE: makeRateGate(),
    LUDOPROOF_PLAYER_ACCOUNTS: makePlayerNamespace(),
    LUDOPROOF_STORE_CATALOG: catalog,
  };
  const token = await issueAccessToken(env, ACCOUNT_A, SESSION_A, Date.now());
  const response = await handleAccountRequest(new Request("https://api.example.test/api/store/catalog", {
    headers: { authorization: "Bearer " + token, "cf-ipcountry": "IN" },
  }), env);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(body.items.map(item => item.id).sort(), ["paw_global_01", "paw_india_01"]);
  assert.deepEqual(body.suppressedIds, ["paw_archived_01", "paw_draft_01", "paw_future_01"]);
  const etag = response.headers.get("etag");
  assert.match(etag, /ludopaws-catalog-v7-IN-/);
  assert.equal(response.headers.get("cache-control"), "private, max-age=60, must-revalidate");

  const cached = await handleAccountRequest(new Request("https://api.example.test/api/store/catalog", {
    headers: {
      authorization: "Bearer " + token,
      "cf-ipcountry": "IN",
      "if-none-match": etag,
    },
  }), env);
  assert.equal(cached.status, 304);
});

test("catalog administration fails closed without an allowlist and denies non-admin accounts", async () => {
  const env = {
    LUDOPROOF_SESSION_HMAC_KEY: SECRET,
    LUDOPROOF_API_GATE: makeRateGate(),
    LUDOPROOF_PLAYER_ACCOUNTS: makePlayerNamespace(),
  };
  const tokenA = await issueAccessToken(env, ACCOUNT_A, SESSION_A, Date.now());
  const missingAllowlist = await accountWorker.fetch(new Request("https://api.example.test/api/admin/store/catalog", {
    headers: { authorization: "Bearer " + tokenA },
  }), env);
  assert.equal(missingAllowlist.status, 503);
  assert.equal((await missingAllowlist.json()).error, "CATALOG_ADMIN_NOT_CONFIGURED");

  env.LUDOPROOF_CATALOG_ADMIN_ACCOUNT_IDS = ACCOUNT_B;
  const forbidden = await accountWorker.fetch(new Request("https://api.example.test/api/admin/store/catalog", {
    headers: { authorization: "Bearer " + tokenA },
  }), env);
  assert.equal(forbidden.status, 403);
  assert.equal((await forbidden.json()).error, "CATALOG_ADMIN_FORBIDDEN");
});

test("admin draft route binds the audit actor to the verified account, not body-supplied identity", async () => {
  const calls = [];
  const catalog = makeCatalogNamespace({ revision: 0, catalogVersion: 0, items: [], updatedAt: null }, calls);
  const env = {
    LUDOPROOF_SESSION_HMAC_KEY: SECRET,
    LUDOPROOF_API_GATE: makeRateGate(),
    LUDOPROOF_PLAYER_ACCOUNTS: makePlayerNamespace(),
    LUDOPROOF_STORE_CATALOG: catalog,
    LUDOPROOF_CATALOG_ADMIN_ACCOUNT_IDS: ACCOUNT_A,
  };
  const token = await issueAccessToken(env, ACCOUNT_A, SESSION_A, Date.now());
  const item = {
    id: "paw_admin_01", category: "PAW", name: "Admin Paw", description: "",
    status: "DRAFT", contentReady: false,
    asset: { thumbnail: "r2://staging/a.png", manifest: "r2://staging/m.json", version: 1, sha256: "a".repeat(64), format: "GLB" },
    unlock: { channels: ["GEMS"], gems: { enabled: true, price: 50 } },
    availability: { worldwide: true, allowedCountries: [], blockedCountries: [], startsAt: null, endsAt: null },
    catalogVersion: 1,
  };
  const response = await accountWorker.fetch(new Request("https://api.example.test/api/admin/store/catalog/items/paw_admin_01", {
    method: "PUT",
    headers: { authorization: "Bearer " + token, "content-type": "application/json" },
    body: JSON.stringify({
      expectedRevision: 0,
      reason: "Add admin draft",
      actorId: ACCOUNT_B,
      item,
    }),
  }), env);
  assert.equal(response.status, 200);
  const call = calls.find(entry => entry.path === "/catalog/draft/save");
  assert.ok(call);
  assert.equal(call.body.actorId, ACCOUNT_A);
  assert.notEqual(call.body.actorId, ACCOUNT_B);
});

test("account wallet API rejects requests without a verified bearer token", async () => {
  const env = {
    LUDOPROOF_API_GATE: makeRateGate(),
    LUDOPROOF_PLAYER_ACCOUNTS: makePlayerNamespace(),
  };
  const response = await handleAccountRequest(
    new Request("https://api.example.test/api/wallet"),
    env,
  );
  assert.equal(response.status, 401);
  assert.equal((await response.json()).error, "AUTH_REQUIRED");
});


test("account deletion cannot be authorized by a caller-supplied account ID", async () => {
  const env = {
    LUDOPROOF_API_GATE: makeRateGate(),
    LUDOPROOF_PLAYER_ACCOUNTS: makePlayerNamespace(),
  };
  const response = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/delete", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        accountId: ACCOUNT_A,
        confirmation: "DELETE_MY_ACCOUNT",
      }),
    }),
    env,
  );
  assert.equal(response.status, 401);
  assert.equal((await response.json()).error, "AUTH_REQUIRED");
});

test("account deletion web CORS allows only the configured static-site origin and expected headers", async () => {
  const allowed = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/delete", {
      method: "OPTIONS",
      headers: {
        origin: "https://ludopaws.rekixo.com",
        "access-control-request-method": "POST",
        "access-control-request-headers": "authorization,content-type",
      },
    }),
    { LUDOPROOF_ACCOUNT_WEB_ORIGIN: "https://ludopaws.rekixo.com" },
  );
  assert.equal(allowed.status, 204);
  assert.equal(allowed.headers.get("access-control-allow-origin"), "https://ludopaws.rekixo.com");
  assert.equal(allowed.headers.get("access-control-allow-methods"), "POST, OPTIONS");

  const denied = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/delete", {
      method: "OPTIONS",
      headers: {
        origin: "https://attacker.example",
        "access-control-request-method": "POST",
        "access-control-request-headers": "authorization,content-type",
      },
    }),
    { LUDOPROOF_ACCOUNT_WEB_ORIGIN: "https://ludopaws.rekixo.com" },
  );
  assert.equal(denied.status, 403);
  assert.equal(denied.headers.get("access-control-allow-origin"), null);

  const unexpectedHeader = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/delete", {
      method: "OPTIONS",
      headers: {
        origin: "https://ludopaws.rekixo.com",
        "access-control-request-method": "POST",
        "access-control-request-headers": "authorization,x-not-allowed",
      },
    }),
    { LUDOPROOF_ACCOUNT_WEB_ORIGIN: "https://ludopaws.rekixo.com" },
  );
  assert.equal(unexpectedHeader.status, 403);

  const unconfigured = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/delete", {
      method: "OPTIONS",
      headers: {
        origin: "https://ludopaws.rekixo.com",
        "access-control-request-method": "POST",
        "access-control-request-headers": "authorization,content-type",
      },
    }),
    {},
  );
  assert.equal(unconfigured.status, 403);
  assert.equal(unconfigured.headers.get("access-control-allow-origin"), null);
});

test("store status disables rewarded ads when configured reward item is not gems", async () => {
  const response = await accountWorker.fetch(
    new Request("https://api.example.test/api/store/status"),
    {
      LUDOPROOF_ADMOB_SSV_ENABLED: "true",
      LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-123/456",
      LUDOPROOF_ADMOB_REWARD_ITEM: "coins",
    },
  );
  assert.equal(response.status, 200);
  const status = await response.json();
  assert.equal(status.rewardedAdsAvailable, false);
});

test("store status explains why Play Billing is disabled without returning secrets", async () => {
  const privateKey = "-----BEGIN PRIVATE KEY-----\\n" + "A".repeat(256) + "\\n-----END PRIVATE KEY-----";
  const response = await accountWorker.fetch(
    new Request("https://api.example.test/api/store/status"),
    {
      GOOGLE_PLAY_BILLING_ENABLED: "false",
      GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL: "ludo-publisher@ludo-project.iam.gserviceaccount.com",
      GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY: privateKey,
      LUDOPROOF_STORE_PURCHASES: {},
      LUDOPROOF_ACCOUNT_DIRECTORY: {},
      LUDOPROOF_PLAYER_ACCOUNTS: {},
    },
  );
  assert.equal(response.status, 200);
  const status = await response.json();
  assert.equal(status.billingAvailable, false);
  assert.deepEqual(status.billingChecks, {
    featureFlagEnabled: false,
    serviceAccountEmailConfigured: true,
    privateKeyPemPresent: true,
    purchaseLedgerBindingConfigured: true,
    credentialsConfigured: true,
    enabled: false,
    accountDirectoryConfigured: true,
    playerAccountsConfigured: true,
    available: false,
  });
  assert.equal(JSON.stringify(status).includes(privateKey), false);
});

test("store status requires credentials, purchase ledger, and account bindings before Billing is available", async () => {
  const response = await accountWorker.fetch(
    new Request("https://api.example.test/api/store/status"),
    { GOOGLE_PLAY_BILLING_ENABLED: "true" },
  );
  assert.equal(response.status, 200);
  const status = await response.json();
  assert.equal(status.billingAvailable, false);
  assert.equal(status.billingChecks.featureFlagEnabled, true);
  assert.equal(status.billingChecks.serviceAccountEmailConfigured, false);
  assert.equal(status.billingChecks.privateKeyPemPresent, false);
  assert.equal(status.billingChecks.purchaseLedgerBindingConfigured, false);
  assert.equal(status.billingChecks.credentialsConfigured, false);
  assert.equal(status.billingChecks.accountDirectoryConfigured, false);
  assert.equal(status.billingChecks.playerAccountsConfigured, false);
  assert.equal(status.billingChecks.available, false);
});



test("AdMob SSV rate limiting runs before signature/key verification", async () => {
  const env = {
    LUDOPROOF_API_GATE: makeRateGate(false),
    LUDOPROOF_ADMOB_SSV_ENABLED: "true",
    LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-123/456",
  };
  const response = await handleAccountRequest(
    new Request("https://api.example.test/api/admob/ssv?invalid=signature"),
    env,
  );
  assert.equal(response.status, 429);
  assert.equal((await response.json()).error, "RATE_LIMITED");
  assert.equal(response.headers.get("retry-after"), "60");
});


test("AdMob SSV validation logs the safe error code without logging callback data", async () => {
  const warnings = [];
  const originalWarn = console.warn;
  console.warn = (...args) => warnings.push(args);
  try {
    const response = await handleAccountRequest(
      new Request("https://api.example.test/api/admob/ssv"),
      {
        LUDOPROOF_API_GATE: makeRateGate(),
        LUDOPROOF_ADMOB_SSV_ENABLED: "true",
        LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-123/456",
      },
    );
    assert.equal(response.status, 400);
    assert.equal((await response.json()).error, "SSV_SIGNATURE_MISSING");
    assert.deepEqual(warnings, [[
      "admob-ssv-request-rejected",
      { status: 400, code: "SSV_SIGNATURE_MISSING" },
    ]]);
    assert.doesNotMatch(JSON.stringify(warnings), /signature=|user_id|custom_data|transaction_id/i);
  } finally {
    console.warn = originalWarn;
  }
});


function makeDeletionReplayEnv(deleted) {
  const calls = [];
  const directoryCalls = [];
  const directory = {
    idFromName(name) { return name; },
    get(name) {
      return {
        async fetch(request) {
          const path = new URL(request.url).pathname;
          const body = await request.json();
          directoryCalls.push({ name, path, body });
          if (path === "/account/delete") return Response.json({ ok: true, deleted: true });
          return Response.json({ error: "NOT_FOUND" }, { status: 404 });
        },
      };
    },
  };
  const player = {
    calls,
    idFromName(name) { return name; },
    get(accountId) {
      return {
        async fetch(request) {
          const url = new URL(request.url);
          const body = await request.json();
          calls.push({ accountId, path: url.pathname, body });
          if (url.pathname === "/session/check") {
            return Response.json({ error: "ACCOUNT_SESSION_INVALID" }, { status: 401 });
          }
          if (url.pathname === "/account/delete-preview") {
            return Response.json({ ok: true, deleted, purchaseLedgerIds: [] });
          }
          return Response.json({ error: "NOT_FOUND" }, { status: 404 });
        },
      };
    },
  };
  return {
    player,
    directoryCalls,
    env: {
      LUDOPROOF_SESSION_HMAC_KEY: SECRET,
      LUDOPROOF_API_GATE: makeRateGate(),
      LUDOPROOF_PLAYER_ACCOUNTS: player,
      LUDOPROOF_ACCOUNT_DIRECTORY: directory,
    },
  };
}

test("expired signed access token can only confirm an already-deleted account", async () => {
  const { env, player, directoryCalls } = makeDeletionReplayEnv(true);
  const expiredToken = await issueAccessToken(env, ACCOUNT_A, SESSION_A, Date.now() - 20 * 60 * 1000);
  const response = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/delete", {
      method: "POST",
      headers: { authorization: "Bearer " + expiredToken, "content-type": "application/json" },
      body: JSON.stringify({}),
    }),
    env,
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, deleted: true, replay: true });
  assert.equal(player.calls.length, 1);
  assert.equal(player.calls[0].path, "/account/delete-preview");
  assert.equal(player.calls[0].accountId, ACCOUNT_A);
  assert.equal(player.calls[0].body.sessionId, SESSION_A);
  assert.deepEqual(directoryCalls.map(call => call.path), ["/account/delete"]);
});

test("expired signed access token cannot authorize a deletion when no deletion tombstone exists", async () => {
  const { env, player } = makeDeletionReplayEnv(false);
  const expiredToken = await issueAccessToken(env, ACCOUNT_A, SESSION_A, Date.now() - 20 * 60 * 1000);
  const response = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/delete", {
      method: "POST",
      headers: { authorization: "Bearer " + expiredToken, "content-type": "application/json" },
      body: JSON.stringify({ confirmation: "DELETE_MY_ACCOUNT" }),
    }),
    env,
  );

  assert.equal(response.status, 401);
  assert.equal((await response.json()).error, "ACCOUNT_SESSION_INVALID");
  assert.equal(player.calls.length, 1);
  assert.equal(player.calls[0].path, "/account/delete-preview");
});


test("expired token replay still rejects a forged signature before checking the tombstone", async () => {
  const { env, player, directoryCalls } = makeDeletionReplayEnv(true);
  const token = await issueAccessToken(env, ACCOUNT_A, SESSION_A, Date.now() - 20 * 60 * 1000);
  const parts = token.split(".");
  parts[2] = (parts[2][0] === "A" ? "B" : "A") + parts[2].slice(1);
  const tamperedToken = parts.join(".");

  const response = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/delete", {
      method: "POST",
      headers: { authorization: "Bearer " + tamperedToken, "content-type": "application/json" },
      body: JSON.stringify({}),
    }),
    env,
  );

  assert.equal(response.status, 401);
  assert.equal((await response.json()).error, "ACCOUNT_SESSION_INVALID");
  assert.equal(player.calls.length, 0);
  assert.equal(directoryCalls.length, 0);
});


function makeDeletionSagaEnv({ directoryDeleteFailures = 0, failureStage = null, linked = false } = {}) {
  const calls = [];
  let playerDeleted = false;
  let directoryDeleted = false;
  let storeDeleted = false;
  let friendDeleted = false;
  let leaderboardDeleted = false;
  let remainingDirectoryDeleteFailures = directoryDeleteFailures;
  let injectedFailureStage = failureStage;
  const purchaseLedgerIds = linked ? ["a".repeat(64)] : [];
  const friendId = "LPF-ABCD-EFGH-IJKL";
  const leaderboardProfileId = "LPB-ABCD-EFGH-IJKL";

  function shouldFail(stage) {
    if (injectedFailureStage !== stage) return false;
    injectedFailureStage = null;
    return true;
  }

  function injectedFailure(stage) {
    return Response.json(
      { error: "INJECTED_DELETE_FAILURE", message: "injected " + stage + " deletion failure" },
      { status: 503 },
    );
  }

  const player = {
    idFromName(name) { return name; },
    get(accountId) {
      return {
        async fetch(request) {
          const path = new URL(request.url).pathname;
          const body = await request.json();
          calls.push("player:" + path);
          if (path === "/session/check") {
            if (playerDeleted) {
              return Response.json({ error: "ACCOUNT_SESSION_INVALID" }, { status: 401 });
            }
            return Response.json({
              ok: true,
              accountId,
              profile: { accountId, displayName: "Delete Me" },
            });
          }
          if (path === "/account/delete-preview") {
            return Response.json({ ok: true, deleted: playerDeleted, purchaseLedgerIds: playerDeleted ? [] : purchaseLedgerIds });
          }
          if (path === "/account/delete") {
            assert.equal(body.confirmation, "DELETE_MY_ACCOUNT");
            if (shouldFail("player")) return injectedFailure("player");
            playerDeleted = true;
            return Response.json({ ok: true, deleted: true, replay: false });
          }
          return Response.json({ error: "NOT_FOUND" }, { status: 404 });
        },
      };
    },
  };

  const directory = {
    idFromName(name) { return name; },
    get(name) {
      return {
        async fetch(request) {
          const path = new URL(request.url).pathname;
          calls.push("directory:" + path);
          if (path === "/account/delete-prepare") {
            const body = await request.clone().json();
            assert.equal(body.source, "SESSION");
            return Response.json({ ok: true, claimed: true, replay: false });
          }
          if (path === "/identity/list") {
            return Response.json({
              ok: true,
              accountId: ACCOUNT_A,
              googleLinked: false,
              friendId: linked ? friendId : null,
              leaderboardProfileId: linked ? leaderboardProfileId : null,
            });
          }
          if (path === "/account/delete") {
            if (shouldFail("directory")) return injectedFailure("directory");
            if (remainingDirectoryDeleteFailures > 0) {
              remainingDirectoryDeleteFailures -= 1;
              return Response.json(
                { error: "ACCOUNT_DIRECTORY_UNAVAILABLE", message: "injected directory failure" },
                { status: 503 },
              );
            }
            directoryDeleted = true;
            return Response.json({ ok: true, deleted: true });
          }
          return Response.json({ error: "NOT_FOUND" }, { status: 404 });
        },
      };
    },
  };

  const storePurchases = {
    idFromName(name) { return name; },
    get(name) {
      return {
        async fetch(request) {
          const path = new URL(request.url).pathname;
          calls.push("store:" + path);
          if (path !== "/delete") return Response.json({ error: "NOT_FOUND" }, { status: 404 });
          if (shouldFail("store")) return injectedFailure("store");
          storeDeleted = true;
          return Response.json({ ok: true, deleted: true });
        },
      };
    },
  };

  const friends = {
    idFromName(name) { return name; },
    get(name) {
      return {
        async fetch(request) {
          const path = new URL(request.url).pathname;
          calls.push("friend:" + path);
          if (path !== "/account/delete") return Response.json({ error: "NOT_FOUND" }, { status: 404 });
          if (shouldFail("friend")) return injectedFailure("friend");
          friendDeleted = true;
          return Response.json({ ok: true, deleted: true });
        },
      };
    },
  };

  const leaderboard = {
    idFromName(name) { return name; },
    get(name) {
      return {
        async fetch(request) {
          const path = new URL(request.url).pathname;
          calls.push("leaderboard:" + path);
          if (path !== "/profile/delete") return Response.json({ error: "NOT_FOUND" }, { status: 404 });
          if (shouldFail("leaderboard")) return injectedFailure("leaderboard");
          leaderboardDeleted = true;
          return Response.json({ ok: true, deleted: true });
        },
      };
    },
  };

  const env = {
    LUDOPROOF_SESSION_HMAC_KEY: SECRET,
    LUDOPROOF_API_GATE: makeRateGate(),
    LUDOPROOF_PLAYER_ACCOUNTS: player,
    LUDOPROOF_ACCOUNT_DIRECTORY: directory,
    LUDOPROOF_STORE_PURCHASES: storePurchases,
    LUDOPROOF_FRIENDS: friends,
    LUDOPROOF_LEADERBOARD: leaderboard,
  };

  return {
    env,
    calls,
    state: () => ({
      playerDeleted,
      directoryDeleted,
      remainingDirectoryDeleteFailures,
      ...(linked ? { storeDeleted, friendDeleted, leaderboardDeleted } : {}),
    }),
  };
}

test("account deletion commits the PlayerAccount tombstone before removing account-directory identities", async () => {
  const fixture = makeDeletionSagaEnv();
  const token = await issueAccessToken(fixture.env, ACCOUNT_A, SESSION_A, Date.now());
  const response = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/delete", {
      method: "POST",
      headers: { authorization: "Bearer " + token, "content-type": "application/json" },
      body: JSON.stringify({ confirmation: "DELETE_MY_ACCOUNT" }),
    }),
    fixture.env,
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, deleted: true, replay: false });
  const state = fixture.state();
  assert.equal(state.playerDeleted, true);
  assert.equal(state.directoryDeleted, true);
  assert.ok(fixture.calls.indexOf("player:/account/delete") >= 0);
  assert.ok(fixture.calls.indexOf("directory:/account/delete") > fixture.calls.indexOf("player:/account/delete"));
});

test("a directory cleanup failure after wallet tombstoning is not reported as success and retry finishes cleanup", async () => {
  const fixture = makeDeletionSagaEnv({ directoryDeleteFailures: 1 });
  const activeToken = await issueAccessToken(fixture.env, ACCOUNT_A, SESSION_A, Date.now());
  const first = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/delete", {
      method: "POST",
      headers: { authorization: "Bearer " + activeToken, "content-type": "application/json" },
      body: JSON.stringify({ confirmation: "DELETE_MY_ACCOUNT" }),
    }),
    fixture.env,
  );

  assert.equal(first.status, 503);
  assert.equal((await first.json()).error, "ACCOUNT_DIRECTORY_UNAVAILABLE");
  assert.deepEqual(fixture.state(), {
    playerDeleted: true,
    directoryDeleted: false,
    remainingDirectoryDeleteFailures: 0,
  });

  const expiredButSignedToken = await issueAccessToken(
    fixture.env,
    ACCOUNT_A,
    SESSION_A,
    Date.now() - 20 * 60 * 1000,
  );
  const retry = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/delete", {
      method: "POST",
      headers: { authorization: "Bearer " + expiredButSignedToken, "content-type": "application/json" },
      body: JSON.stringify({}),
    }),
    fixture.env,
  );

  assert.equal(retry.status, 200);
  assert.deepEqual(await retry.json(), { ok: true, deleted: true, replay: true });
  assert.deepEqual(fixture.state(), {
    playerDeleted: true,
    directoryDeleted: true,
    remainingDirectoryDeleteFailures: 0,
  });
});

test("linked account deletion retries safely after each injected downstream failure", async () => {
  const failureStages = ["store", "friend", "leaderboard", "player", "directory"];
  const callForStage = {
    store: "store:/delete",
    friend: "friend:/account/delete",
    leaderboard: "leaderboard:/profile/delete",
    player: "player:/account/delete",
    directory: "directory:/account/delete",
  };

  for (const failureStage of failureStages) {
    const fixture = makeDeletionSagaEnv({ failureStage, linked: true });
    const token = await issueAccessToken(fixture.env, ACCOUNT_A, SESSION_A, Date.now());
    const submit = () => accountWorker.fetch(
      new Request("https://api.example.test/api/account/delete", {
        method: "POST",
        headers: { authorization: "Bearer " + token, "content-type": "application/json" },
        body: JSON.stringify({ confirmation: "DELETE_MY_ACCOUNT" }),
      }),
      fixture.env,
    );

    const first = await submit();
    assert.equal(first.status, 503, failureStage + " injected failure must not be reported as success");
    const afterFirst = fixture.state();
    assert.equal(afterFirst.directoryDeleted, false, failureStage + " failure must not unlink the directory");
    assert.equal(afterFirst.playerDeleted, failureStage === "directory", failureStage + " failure should preserve the expected PlayerAccount tombstone state");

    const retry = await submit();
    assert.equal(retry.status, 200, failureStage + " retry should complete deletion");
    assert.deepEqual(await retry.json(), { ok: true, deleted: true, replay: failureStage === "directory" });
    assert.deepEqual(fixture.state(), {
      playerDeleted: true,
      directoryDeleted: true,
      remainingDirectoryDeleteFailures: 0,
      storeDeleted: true,
      friendDeleted: true,
      leaderboardDeleted: true,
    });
    assert.equal(fixture.calls.filter(call => call === callForStage[failureStage]).length, 2, failureStage + " operation should be retried exactly once");

    const lastIndex = value => fixture.calls.lastIndexOf(value);
    assert.ok(lastIndex(callForStage.store) < lastIndex(callForStage.friend), "Store ledger cleanup must precede Friend cleanup");
    assert.ok(lastIndex(callForStage.friend) < lastIndex(callForStage.leaderboard), "Friend cleanup must precede leaderboard cleanup");
    assert.ok(lastIndex(callForStage.leaderboard) < lastIndex(callForStage.player), "Linked cleanup must precede PlayerAccount tombstoning");
    assert.ok(lastIndex(callForStage.player) < lastIndex(callForStage.directory), "PlayerAccount tombstone must precede directory unlink");
  }
});


function makeGuestDeletionApiEnv(expectedCredentialHash, { directoryDeleteFailures = 0, serverGeneratedCredential = "12".repeat(32), rotatedCredential = "34".repeat(32) } = {}) {
  const calls = [];
  let playerDeleted = false;
  let directoryDeleted = false;
  let remainingDirectoryDeleteFailures = directoryDeleteFailures;

  const player = {
    idFromName(name) { return name; },
    get(accountId) {
      return {
        async fetch(request) {
          const path = new URL(request.url).pathname;
          const body = await request.json();
          calls.push("player:" + path);
          if (path === "/session/check") {
            return Response.json({
              ok: true,
              accountId,
              profile: { accountId, displayName: "Guest Player" },
            });
          }
          if (path === "/account/delete-preview") {
            assert.equal(body.guestRecoveryVerified, true);
            return Response.json({ ok: true, deleted: playerDeleted, purchaseLedgerIds: [] });
          }
          if (path === "/account/delete") {
            assert.equal(body.guestRecoveryVerified, true);
            assert.equal(body.confirmation, "DELETE_MY_ACCOUNT");
            const replay = playerDeleted;
            playerDeleted = true;
            return Response.json({ ok: true, deleted: true, replay });
          }
          return Response.json({ error: "NOT_FOUND" }, { status: 404 });
        },
      };
    },
  };

  const directory = {
    idFromName(name) { return name; },
    get(name) {
      return {
        async fetch(request) {
          const path = new URL(request.url).pathname;
          const body = await request.json();
          calls.push("directory:" + path);
          if (path === "/guest-deletion/register") {
            assert.equal(body.accountId, ACCOUNT_A);
            assert.equal(Object.hasOwn(body, "credentialHash"), false, "client must not choose the deletion code or its hash");
            assert.equal(typeof body.rotate, "boolean");
            return Response.json({
              ok: true,
              ready: true,
              credential: body.rotate ? rotatedCredential : serverGeneratedCredential,
            });
          }
          if (path === "/guest-deletion/claim") {
            assert.match(body.credentialHash, /^[a-f0-9]{64}$/, "only a credential hash should be sent to AccountDirectory");
            if (body.credentialHash !== expectedCredentialHash) {
              return Response.json({ error: "GUEST_DELETION_CODE_INVALID", message: "guest deletion code is invalid or no longer available" }, { status: 404 });
            }
            return directoryDeleted
              ? Response.json({ ok: true, deleted: true, replay: true })
              : Response.json({ ok: true, accountId: ACCOUNT_A, deleted: false, replay: false });
          }
          if (path === "/account/delete-prepare") {
            assert.equal(body.source, "GUEST_CODE");
            assert.equal(body.credentialHash, expectedCredentialHash);
            return Response.json({ ok: true, claimed: true, replay: false });
          }
          if (path === "/identity/list") {
            return Response.json({
              ok: true, accountId: ACCOUNT_A, googleLinked: false,
              friendId: null, leaderboardProfileId: null,
            });
          }
          if (path === "/account/delete") {
            if (remainingDirectoryDeleteFailures > 0) {
              remainingDirectoryDeleteFailures -= 1;
              return Response.json(
                { error: "ACCOUNT_DIRECTORY_UNAVAILABLE", message: "injected directory failure" },
                { status: 503 },
              );
            }
            directoryDeleted = true;
            return Response.json({ ok: true, deleted: true });
          }
          return Response.json({ error: "NOT_FOUND" }, { status: 404 });
        },
      };
    },
  };

  const env = {
    LUDOPROOF_SESSION_HMAC_KEY: SECRET,
    LUDOPROOF_API_GATE: makeRateGate(),
    LUDOPROOF_PLAYER_ACCOUNTS: player,
    LUDOPROOF_ACCOUNT_DIRECTORY: directory,
  };
  return {
    env,
    calls,
    state: () => ({ playerDeleted, directoryDeleted, remainingDirectoryDeleteFailures }),
  };
}

test("guest delete-only code is required and never returns account data on an invalid code", async () => {
  const code = "ab".repeat(32);
  const expectedHash = await sha256Hex("ludoproof:guest-deletion-credential:v1:" + code);
  const fixture = makeGuestDeletionApiEnv(expectedHash);
  const response = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/guest/delete", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ credential: "cd".repeat(32), confirmation: "DELETE_MY_ACCOUNT" }),
    }),
    fixture.env,
  );
  assert.equal(response.status, 404);
  assert.equal((await response.json()).error, "GUEST_DELETION_CODE_INVALID");
  assert.deepEqual(fixture.state(), {
    playerDeleted: false, directoryDeleted: false, remainingDirectoryDeleteFailures: 0,
  });
  assert.equal(fixture.calls.some(call => call.startsWith("player:")), false);
});

test("guest delete-only code completes uninstalled guest deletion and retries after directory failure", async () => {
  const code = "ef".repeat(32);
  const credentialHash = await sha256Hex("ludoproof:guest-deletion-credential:v1:" + code);
  const fixture = makeGuestDeletionApiEnv(credentialHash, { directoryDeleteFailures: 1 });
  const submit = () => accountWorker.fetch(
    new Request("https://api.example.test/api/account/guest/delete", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ credential: code, confirmation: "DELETE_MY_ACCOUNT" }),
    }),
    fixture.env,
  );

  const first = await submit();
  assert.equal(first.status, 503);
  assert.deepEqual(fixture.state(), {
    playerDeleted: true, directoryDeleted: false, remainingDirectoryDeleteFailures: 0,
  });
  assert.ok(fixture.calls.indexOf("player:/account/delete") > fixture.calls.indexOf("player:/account/delete-preview"));
  assert.ok(fixture.calls.indexOf("directory:/account/delete") > fixture.calls.indexOf("player:/account/delete"));

  const retry = await submit();
  assert.equal(retry.status, 200);
  assert.deepEqual(await retry.json(), { ok: true, deleted: true, replay: true });
  assert.deepEqual(fixture.state(), {
    playerDeleted: true, directoryDeleted: true, remainingDirectoryDeleteFailures: 0,
  });
});

test("guest deletion code endpoint returns a server-generated code to an active session only", async () => {
  const code = "ab".repeat(32);
  const hash = await sha256Hex("ludoproof:guest-deletion-credential:v1:" + code);
  const fixture = makeGuestDeletionApiEnv(hash, { serverGeneratedCredential: code });
  const unauthorized = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/guest/deletion-code", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({}),
    }),
    fixture.env,
  );
  assert.equal(unauthorized.status, 401);
  assert.equal((await unauthorized.json()).error, "AUTH_REQUIRED");

  const token = await issueAccessToken(fixture.env, ACCOUNT_A, SESSION_A, Date.now());
  const response = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/guest/deletion-code", {
      method: "POST",
      headers: { authorization: "Bearer " + token, "content-type": "application/json" },
      body: JSON.stringify({}),
    }),
    fixture.env,
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, ready: true, credential: code });

  const replacement = await accountWorker.fetch(
    new Request("https://api.example.test/api/account/guest/deletion-code", {
      method: "POST",
      headers: { authorization: "Bearer " + token, "content-type": "application/json" },
      body: JSON.stringify({ rotate: true }),
    }),
    fixture.env,
  );
  assert.equal(replacement.status, 200);
  assert.deepEqual(await replacement.json(), { ok: true, ready: true, credential: "34".repeat(32) });
});
