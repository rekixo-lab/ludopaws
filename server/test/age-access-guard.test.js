import test from "node:test";
import assert from "node:assert/strict";
import { ageSafetyBlockResponse, isAgeRestrictedEndpoint } from "../src/age-access-guard.js";

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
  assert.equal(ageSafetyBlockResponse(
    new Request("https://api.ludopaws.rekixo.com/api/friends/register"),
    {},
  ), null);
});
