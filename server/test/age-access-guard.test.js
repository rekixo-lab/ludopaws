import test from "node:test";
import assert from "node:assert/strict";
import { ageSafetyBlockResponse, isAgeRestrictedEndpoint } from "../src/age-access-guard.js";
import accountWorker from "../src/index-account.js";

test("all connected game, chat, matchmaking, social and paid routes are locked down", async () => {
  for (const path of [
    "/api/matches", "/api/matches/LPABCDEFG/events",
    "/api/matchmaking/search", "/api/team-matchmaking/search",
    "/api/friends/register", "/api/friends/messages/send", "/api/friends/recent",
    "/api/leaderboard", "/api/wallet", "/api/wallet/cosmetics/purchase",
    "/api/store/purchases/verify", "/api/store/catalog",
    "/api/admob/ssv", "/api/account/guest", "/api/account/google/link",
    "/api/account/link/friend", "/api/account/link/leaderboard",
  ]) {
    assert.equal(isAgeRestrictedEndpoint(path), true, path);
    const response = ageSafetyBlockResponse(
      new Request("https://api.ludopaws.rekixo.com" + path, {
        method: "POST",
        headers: { authorization: "Bearer forged", "x-age": "adult" },
      }),
      { LUDOPROOF_AGE_SAFETY_MODE: "LOCKDOWN" },
    );
    assert.equal(response.status, 403, path);
    assert.equal((await response.json()).error, "AGE_ACCESS_NOT_AVAILABLE");
    assert.equal(response.headers.get("cache-control"), "no-store");
  }
});

test("account recovery and deletion still work during safety lockdown", () => {
  for (const path of [
    "/", "/health", "/ready", "/api/account/delete",
    "/api/account/guest/delete", "/api/account/google/login",
    "/api/account/google/challenge", "/api/account/refresh",
    "/api/account/logout", "/api/account/me", "/api/store/status",
  ]) {
    assert.equal(isAgeRestrictedEndpoint(path), false, path);
    assert.equal(ageSafetyBlockResponse(
      new Request("https://api.ludopaws.rekixo.com" + path),
      { LUDOPROOF_AGE_SAFETY_MODE: "LOCKDOWN" },
    ), null);
  }
});

test("lockdown uses server-only config, never client age assertions", () => {
  assert.equal(ageSafetyBlockResponse(
    new Request("https://api.ludopaws.rekixo.com/api/friends/register?age=adult"),
    { LUDOPROOF_AGE_SAFETY_MODE: "LOCKDOWN" },
  ).status, 403);
  for (const env of [{}, undefined, { LUDOPROOF_AGE_SAFETY_MODE: "OPEN" },
    { LUDOPROOF_AGE_SAFETY_MODE: "LOCKD0WN" }]) {
    assert.equal(ageSafetyBlockResponse(
      new Request("https://api.ludopaws.rekixo.com/api/friends/register"),
      env,
    ).status, 403, "misconfiguration must never allow connected gameplay");
  }
});

test("outermost Worker rejects direct requests before downstream account routing", async () => {
  const env = { LUDOPROOF_AGE_SAFETY_MODE: "LOCKDOWN" };
  const paths = [
    "/api/friends/messages/send",
    "/api/matches/LPABCDEFG/events",
    "/api/team-matchmaking/search",
    "/api/store/purchases/verify",
    "/api/account/guest",
  ];
  for (const path of paths) {
    const response = await accountWorker.fetch(new Request("https://api.example.test" + path, {
      method: "POST",
      headers: { "x-age": "adult", authorization: "Bearer forged" },
    }), env);
    assert.equal(response.status, 403, path);
    assert.equal((await response.json()).error, "AGE_ACCESS_NOT_AVAILABLE", path);
  }
});

test("outer Worker remains locked down if vars disappear or are invalid", async () => {
  for (const env of [{}, { LUDOPROOF_AGE_SAFETY_MODE: "ENABLED" }]) {
    for (const path of ["/api/matches", "/api/friends/report",
      "/api/account/guest", "/api/wallet/cosmetics/purchase",
      "/api/store/purchases/verify", "/api/matches/LPABCDEFG/events"]) {
      const r = await accountWorker.fetch(new Request("https://api.example.test" + path, {
        method: "POST",
        headers: { "x-age": "adult", authorization: "Bearer forged" },
      }), env);
      assert.equal(r.status, 403, path);
      assert.equal((await r.json()).error, "AGE_ACCESS_NOT_AVAILABLE", path);
    }
  }
});

test("fail closed without breaking account deletion and basic operational status", () => {
  for (const env of [{}, { LUDOPROOF_AGE_SAFETY_MODE: "OPEN" }]) {
    for (const path of ["/api/account/delete", "/api/account/guest/delete",
      "/api/account/guest/deletion-code", "/api/account/logout",
      "/health", "/ready"]) {
      assert.equal(ageSafetyBlockResponse(
        new Request("https://api.example.test" + path), env,
      ), null, path);
    }
  }
});
