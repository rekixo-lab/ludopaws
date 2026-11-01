import test from "node:test";
import assert from "node:assert/strict";

import { MatchmakerQueue } from "../src/matchmaker-queue.js";
import { TeamMatchmakerQueue } from "../src/team-matchmaker-queue.js";
import { MatchRoom, chooseBotMove } from "../src/match-room-bots.js";
import { LUDO_PAWS_CHARACTER_IDS } from "../src/ludo-paws-characters.js";
import {
  botFillDelayMs,
  botMatchFillEnabled,
  botResultHoldMs,
  botTurnDelayMs,
  botTurnsEnabled,
  createBotParticipants,
  selectBotCharacterFromRoster,
} from "../src/matchmaking-bots.js";

const SESSION_KEY = "lp_test_session_hmac_key_1234567890abcdef";

class FakeStorage {
  constructor() {
    this.values = new Map();
    this.alarmAt = null;
  }

  async get(key) {
    const value = this.values.get(key);
    return value === undefined ? undefined : structuredClone(value);
  }

  async put(key, value) {
    this.values.set(key, structuredClone(value));
  }

  async setAlarm(timestamp) {
    this.alarmAt = timestamp;
  }

  async deleteAlarm() {
    this.alarmAt = null;
  }

  async deleteAll() {
    this.values.clear();
    this.alarmAt = null;
  }
}

class MatchNamespace {
  constructor(env) {
    this.env = env;
    this.rooms = new Map();
  }

  idFromName(name) {
    return name;
  }

  get(id) {
    if (!this.rooms.has(id)) {
      this.rooms.set(
        id,
        new MatchRoom({ storage: new FakeStorage() }, this.env),
      );
    }
    return this.rooms.get(id);
  }
}

function profileTokenFor(clientRequestId) {
  const suffix = String(clientRequestId ?? "")
    .replaceAll("-", "")
    .slice(-12)
    .padStart(12, "0");
  return "lpp_" + "A".repeat(32) + suffix;
}

function makeEnv() {
  const env = {
    LUDOPROOF_SESSION_HMAC_KEY: SESSION_KEY,
    LUDOPROOF_BOT_MATCH_FILL_ENABLED: "true",
    LUDOPROOF_BOT_TURNS_ENABLED: "false",
    LUDOPROOF_BOT_FILL_DELAY_MS: "250",
    LUDOPROOF_BOT_TURN_DELAY_MS: "100",
  };
  env.LUDOPROOF_MATCHES = new MatchNamespace(env);
  env.LUDOPROOF_LEADERBOARD = {
    idFromName(name) {
      assert.equal(name, "global");
      return name;
    },
    get() {
      return {
        async fetch(request) {
          const authorization = request.headers.get("authorization") ?? "";
          const match = authorization.match(/^Bearer\s+(lpp_[A-Za-z0-9_-]{32,})$/);
          if (!match) {
            return Response.json({ ok: false, error: "PROFILE_AUTH_INVALID" }, { status: 401 });
          }
          const suffix = match[1].slice(-12);
          return Response.json({
            ok: true,
            profileId: "10000000-0000-4000-8000-" + suffix,
          });
        },
      };
    },
  };
  return env;
}

function request(path, body, profileToken = null) {
  const headers = { "content-type": "application/json" };
  if (profileToken) headers.authorization = "Bearer " + profileToken;
  return new Request("https://matchmaker" + path, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
}

function human(suffix, playerCount, team = false) {
  const clientRequestId =
    "00000000-0000-4000-8000-" + String(suffix).padStart(12, "0");
  return {
    clientRequestId,
    displayName: "Player " + suffix,
    ...(team ? {} : { playerCount }),
    characterId: ["dog", "cat", "goat", "duck"][suffix % 4],
    profileToken: profileTokenFor(clientRequestId),
  };
}

async function parse(response) {
  return { status: response.status, body: await response.json() };
}

async function backdateQueue(queue, storageKey, queueIndex = 0) {
  const state = await queue.ctx.storage.get(storageKey);
  state.queue[queueIndex].joinedAt = Date.now() - 10_000;
  state.queue[queueIndex].lastSeenAt = Date.now();
  await queue.ctx.storage.put(storageKey, state);
}

test("bot settings are bounded and bots receive non-profile identities", () => {
  assert.equal(botMatchFillEnabled({ LUDOPROOF_BOT_MATCH_FILL_ENABLED: "false" }), false);
  assert.equal(botTurnsEnabled({ LUDOPROOF_BOT_TURNS_ENABLED: "off" }), false);
  assert.equal(botFillDelayMs({ LUDOPROOF_BOT_FILL_DELAY_MS: "10" }), 15_000);
  assert.equal(botTurnDelayMs({}), 1_100);
  assert.equal(botTurnDelayMs({ LUDOPROOF_BOT_TURN_DELAY_MS: "650" }), 1_100);
  assert.equal(botTurnDelayMs({ LUDOPROOF_BOT_TURN_DELAY_MS: "12001" }), 1_100);
  assert.equal(botResultHoldMs({}), 1_400);
  assert.equal(botResultHoldMs({ LUDOPROOF_BOT_RESULT_HOLD_MS: "700" }), 1_400);
  assert.equal(botResultHoldMs({ LUDOPROOF_BOT_RESULT_HOLD_MS: "100" }), 1_400);
  assert.equal(botResultHoldMs({ LUDOPROOF_BOT_RESULT_HOLD_MS: "1250" }), 1_400);
  assert.equal(botResultHoldMs({ LUDOPROOF_BOT_RESULT_HOLD_MS: "1650" }), 1_650);
  const bots = createBotParticipants({
    count: 3,
    playerCount: 4,
    matchMode: "ONLINE",
    rulesetKey: "CLASSIC_V3",
    existingNames: ["Aarav"],
    existingCharacterIds: ["dog"],
    now: 100,
  });
  assert.equal(bots.length, 3);
  assert.ok(bots.every((bot) => bot.playerType === "BOT" && bot.profileId === null));
  assert.ok(bots.every((bot) => bot.rulesetKey === "CLASSIC_V3"));
  assert.ok(bots.every((bot) => /^[0-9a-f-]{36}$/i.test(bot.clientRequestId)));
  assert.equal(new Set(bots.map((bot) => bot.displayName.toLowerCase())).size, 3);
  assert.ok(bots.every((bot) => bot.displayName.toLowerCase() !== "aarav"));
  assert.equal(new Set(["dog", ...bots.map((bot) => bot.characterId)]).size, 4);
  assert.ok(bots.every((bot) => bot.characterId !== "dog"));
});

test("future server bot rosters above 256 entries can select their final character", () => {
  const roster = Array.from({ length: 400 }, (_, index) => "future-paw-" + index);
  const chosen = selectBotCharacterFromRoster(roster, new Set(), (length) => length - 1);
  assert.equal(chosen, "future-paw-399");
  assert.equal(selectBotCharacterFromRoster(roster, new Set(roster.slice(0, 399))), "future-paw-399");
});

test("automatic server bots may use every premium roster character without user ownership", () => {
  const premium = "panda";
  const bot = createBotParticipants({
    count: 1,
    playerCount: 4,
    matchMode: "TEAM_UP",
    rulesetKey: "TEAM_UP_V5",
    existingCharacterIds: LUDO_PAWS_CHARACTER_IDS.filter((id) => id !== premium),
  })[0];
  assert.equal(bot.characterId, premium);
  assert.equal(bot.playerType, "BOT");
  assert.equal(bot.profileId, null);
});

test("bot participant creation supports both V4 rulesets", () => {
  const onlineBots = createBotParticipants({
    count: 1,
    playerCount: 2,
    matchMode: "ONLINE",
    rulesetKey: "CLASSIC_V4",
  });
  const teamBots = createBotParticipants({
    count: 1,
    playerCount: 4,
    matchMode: "TEAM_UP",
    rulesetKey: "TEAM_UP_V4",
  });

  assert.equal(onlineBots[0].rulesetKey, "CLASSIC_V4");
  assert.equal(teamBots[0].rulesetKey, "TEAM_UP_V4");
  assert.equal(onlineBots[0].playerType, "BOT");
  assert.equal(teamBots[0].playerType, "BOT");
});

test("disabling bot fill keeps an expired real-player search queued without creating a match", async () => {
  const env = makeEnv();
  env.LUDOPROOF_BOT_MATCH_FILL_ENABLED = "false";
  const queue = new MatchmakerQueue({ storage: new FakeStorage() }, env);
  const person = human(9, 2);

  await queue.fetch(
    request("/search", {
      clientRequestId: person.clientRequestId,
      displayName: person.displayName,
      playerCount: 2,
      characterId: person.characterId,
    }, person.profileToken),
  );
  await backdateQueue(queue, "matchmaking-state");
  await queue.alarm();

  const result = await parse(await queue.fetch(
    request("/status", {
      clientRequestId: person.clientRequestId,
      playerCount: 2,
    }, person.profileToken),
  ));
  assert.equal(result.body.status, "SEARCHING");
  assert.equal(result.body.queuedPlayers, 1);
  assert.equal(env.LUDOPROOF_MATCHES.rooms.size, 0);
});

test("Online 4-player fallback adds only the missing bots and assigns no bot client tickets", async () => {
  const env = makeEnv();
  const queue = new MatchmakerQueue({ storage: new FakeStorage() }, env);
  const first = human(1, 4);
  const second = human(2, 4);

  for (const person of [first, second]) {
    const result = await parse(await queue.fetch(
      request("/search", {
        clientRequestId: person.clientRequestId,
        displayName: person.displayName,
        playerCount: 4,
        characterId: person.characterId,
      }, person.profileToken),
    ));
    assert.equal(result.body.status, "SEARCHING");
  }

  await backdateQueue(queue, "matchmaking-state");
  await queue.alarm();

  const result = await parse(await queue.fetch(
    request("/status", {
      clientRequestId: first.clientRequestId,
      playerCount: 4,
    }, first.profileToken),
  ));
  assert.equal(result.body.status, "MATCHED");
  assert.equal(result.body.state.status, "ACTIVE");
  assert.equal(result.body.state.players.length, 4);
  assert.deepEqual(
    result.body.state.players.map((player) => player.playerType),
    ["HUMAN", "HUMAN", "BOT", "BOT"],
  );
  assert.ok(result.body.state.players.slice(2).every((player) => player.displayName.length >= 2));
  const stored = await env.LUDOPROOF_MATCHES.rooms
    .get(result.body.matchId).ctx.storage.get("match-state");
  assert.ok(stored.players.slice(2).every((player) => player.profileId == null));
  const queueState = await queue.ctx.storage.get("matchmaking-state");
  assert.deepEqual(Object.keys(queueState.assignments).sort(), [
    first.clientRequestId,
    second.clientRequestId,
  ].sort());
});

test("Team Up fallback fills remaining seats while retaining A/B/A/B teams", async () => {
  const env = makeEnv();
  const queue = new TeamMatchmakerQueue({ storage: new FakeStorage() }, env);
  const people = [human(11, 4, true), human(12, 4, true), human(13, 4, true)];

  for (const person of people) {
    const result = await parse(await queue.fetch(
      request("/search", {
        clientRequestId: person.clientRequestId,
        displayName: person.displayName,
        characterId: person.characterId,
      }, person.profileToken),
    ));
    assert.equal(result.body.status, "SEARCHING");
  }

  await backdateQueue(queue, "team-matchmaking-state-v1");
  await queue.alarm();

  const result = await parse(await queue.fetch(
    request("/status", {
      clientRequestId: people[0].clientRequestId,
    }, people[0].profileToken),
  ));
  assert.equal(result.body.status, "MATCHED");
  assert.equal(result.body.matchMode, "TEAM_UP");
  assert.equal(result.body.state.players.length, 4);
  assert.deepEqual(result.body.state.players.map((player) => player.teamId), ["A", "B", "A", "B"]);
  assert.deepEqual(result.body.state.players.map((player) => player.playerType), ["HUMAN", "HUMAN", "HUMAN", "BOT"]);
  assert.equal(result.body.teamId, "A");
  const stored = await env.LUDOPROOF_MATCHES.rooms
    .get(result.body.matchId).ctx.storage.get("match-state");
  assert.equal(stored.players[3].profileId, null);
  assert.equal(stored.players[3].playerType, "BOT");
});


test("Online matchmaking never mixes clients that request different rulesets", async () => {
  const env = makeEnv();
  env.LUDOPROOF_BOT_MATCH_FILL_ENABLED = "false";
  const queue = new MatchmakerQueue({ storage: new FakeStorage() }, env);
  const legacy = human(61, 2);
  const versioned = human(62, 2);
  for (const person of [legacy, versioned]) {
    const result = await parse(await queue.fetch(request("/search", {
      clientRequestId: person.clientRequestId, displayName: person.displayName,
      playerCount: 2, characterId: person.characterId,
      rulesetKey: person === legacy ? "CLASSIC_V1" : "CLASSIC_V3",
    }, person.profileToken)));
    assert.equal(result.body.status, "SEARCHING");
  }
  for (const person of [legacy, versioned]) {
    const result = await parse(await queue.fetch(request("/status", {
      clientRequestId: person.clientRequestId, playerCount: 2,
    }, person.profileToken)));
    assert.equal(result.body.status, "SEARCHING");
    assert.equal(result.body.queuedPlayers, 1);
  }
  assert.equal(env.LUDOPROOF_MATCHES.rooms.size, 0);
});

test("Team Up matchmaking never mixes clients that request different rulesets", async () => {
  const env = makeEnv();
  env.LUDOPROOF_BOT_MATCH_FILL_ENABLED = "false";
  const queue = new TeamMatchmakerQueue({ storage: new FakeStorage() }, env);
  const legacy = human(63, 4, true);
  const versioned = human(64, 4, true);
  for (const person of [legacy, versioned]) {
    const result = await parse(await queue.fetch(request("/search", {
      clientRequestId: person.clientRequestId, displayName: person.displayName,
      characterId: person.characterId,
      rulesetKey: person === legacy ? "TEAM_UP_V1" : "TEAM_UP_V3",
    }, person.profileToken)));
    assert.equal(result.body.status, "SEARCHING");
  }
  for (const person of [legacy, versioned]) {
    const result = await parse(await queue.fetch(request("/status", {
      clientRequestId: person.clientRequestId,
    }, person.profileToken)));
    assert.equal(result.body.status, "SEARCHING");
    assert.equal(result.body.queuedPlayers, 1);
  }
  assert.equal(env.LUDOPROOF_MATCHES.rooms.size, 0);
});


test("Online matchmaking prefers a different-character human without losing queue fairness", async () => {
  const env = makeEnv();
  env.LUDOPROOF_BOT_MATCH_FILL_ENABLED = "false";
  const queue = new MatchmakerQueue({ storage: new FakeStorage() }, env);
  const first = human(70, 2);
  const sameCharacter = human(71, 2);
  const differentCharacter = human(72, 2);
  first.characterId = "dog";
  sameCharacter.characterId = "dog";
  differentCharacter.characterId = "cat";

  for (const person of [first, sameCharacter]) {
    const result = await parse(await queue.fetch(request("/search", {
      clientRequestId: person.clientRequestId,
      displayName: person.displayName,
      playerCount: 2,
      characterId: person.characterId,
      rulesetKey: "CLASSIC_V3",
    }, person.profileToken)));
    assert.equal(result.body.status, "SEARCHING");
  }

  let state = await queue.ctx.storage.get("matchmaking-state");
  assert.ok(state.characterPreferences["2:CLASSIC_V3"]);
  assert.equal(state.assignments[first.clientRequestId], undefined);
  assert.equal(state.assignments[sameCharacter.clientRequestId], undefined);

  const matched = await parse(await queue.fetch(request("/search", {
    clientRequestId: differentCharacter.clientRequestId,
    displayName: differentCharacter.displayName,
    playerCount: 2,
    characterId: differentCharacter.characterId,
    rulesetKey: "CLASSIC_V3",
  }, differentCharacter.profileToken)));
  assert.equal(matched.body.status, "MATCHED");
  assert.deepEqual(
    matched.body.state.players.map((player) => player.characterId),
    ["dog", "cat"],
  );

  state = await queue.ctx.storage.get("matchmaking-state");
  assert.ok(state.assignments[first.clientRequestId]);
  assert.ok(state.assignments[differentCharacter.clientRequestId]);
  assert.equal(state.assignments[sameCharacter.clientRequestId], undefined);
  assert.ok(state.queue.some((player) => player.clientRequestId === sameCharacter.clientRequestId));
});

test("Online character preference falls back to the available group after its deadline", async () => {
  const env = makeEnv();
  env.LUDOPROOF_BOT_MATCH_FILL_ENABLED = "false";
  const queue = new MatchmakerQueue({ storage: new FakeStorage() }, env);
  const first = human(73, 2);
  const second = human(74, 2);
  first.characterId = "dog";
  second.characterId = "dog";

  for (const person of [first, second]) {
    const result = await parse(await queue.fetch(request("/search", {
      clientRequestId: person.clientRequestId,
      displayName: person.displayName,
      playerCount: 2,
      characterId: person.characterId,
      rulesetKey: "CLASSIC_V3",
    }, person.profileToken)));
    assert.equal(result.body.status, "SEARCHING");
  }

  const state = await queue.ctx.storage.get("matchmaking-state");
  state.characterPreferences["2:CLASSIC_V3"].readyAt = Date.now() - 1;
  await queue.ctx.storage.put("matchmaking-state", state);
  await queue.alarm();

  const matched = await parse(await queue.fetch(request("/status", {
    clientRequestId: first.clientRequestId,
    playerCount: 2,
  }, first.profileToken)));
  assert.equal(matched.body.status, "MATCHED");
  assert.deepEqual(
    matched.body.state.players.map((player) => player.characterId),
    ["dog", "dog"],
  );
  const finalized = await queue.ctx.storage.get("matchmaking-state");
  assert.equal(finalized.characterPreferences["2:CLASSIC_V3"], undefined);
});

test("Team Up selects a fully diverse group when one arrives during the preference window", async () => {
  const env = makeEnv();
  env.LUDOPROOF_BOT_MATCH_FILL_ENABLED = "false";
  const queue = new TeamMatchmakerQueue({ storage: new FakeStorage() }, env);
  const people = [
    human(80, 4, true),
    human(81, 4, true),
    human(82, 4, true),
    human(83, 4, true),
    human(84, 4, true),
  ];
  const characters = ["dog", "dog", "goat", "duck", "cat"];
  people.forEach((person, index) => { person.characterId = characters[index]; });

  for (const person of people.slice(0, 4)) {
    const result = await parse(await queue.fetch(request("/search", {
      clientRequestId: person.clientRequestId,
      displayName: person.displayName,
      characterId: person.characterId,
      rulesetKey: "TEAM_UP_V3",
    }, person.profileToken)));
    assert.equal(result.body.status, "SEARCHING");
  }

  let state = await queue.ctx.storage.get("team-matchmaking-state-v1");
  assert.ok(state.characterPreferences["4:TEAM_UP_V3"]);
  const matched = await parse(await queue.fetch(request("/search", {
    clientRequestId: people[4].clientRequestId,
    displayName: people[4].displayName,
    characterId: people[4].characterId,
    rulesetKey: "TEAM_UP_V3",
  }, people[4].profileToken)));
  assert.equal(matched.body.status, "MATCHED");
  assert.deepEqual(
    matched.body.state.players.map((player) => player.characterId),
    ["dog", "goat", "duck", "cat"],
  );

  state = await queue.ctx.storage.get("team-matchmaking-state-v1");
  assert.ok(state.assignments[people[0].clientRequestId]);
  assert.equal(state.assignments[people[1].clientRequestId], undefined);
  assert.ok(state.assignments[people[2].clientRequestId]);
  assert.ok(state.assignments[people[3].clientRequestId]);
  assert.ok(state.assignments[people[4].clientRequestId]);
  assert.ok(state.queue.some((player) => player.clientRequestId === people[1].clientRequestId));
});

test("Team Up character preference expires to prevent an indefinite wait", async () => {
  const env = makeEnv();
  env.LUDOPROOF_BOT_MATCH_FILL_ENABLED = "false";
  const queue = new TeamMatchmakerQueue({ storage: new FakeStorage() }, env);
  const people = [human(85, 4, true), human(86, 4, true), human(87, 4, true), human(88, 4, true)];
  for (const person of people) person.characterId = "dog";

  for (const person of people) {
    const result = await parse(await queue.fetch(request("/search", {
      clientRequestId: person.clientRequestId,
      displayName: person.displayName,
      characterId: person.characterId,
      rulesetKey: "TEAM_UP_V3",
    }, person.profileToken)));
    assert.equal(result.body.status, "SEARCHING");
  }

  const state = await queue.ctx.storage.get("team-matchmaking-state-v1");
  assert.ok(state.characterPreferences["4:TEAM_UP_V3"]);
  state.characterPreferences["4:TEAM_UP_V3"].readyAt = Date.now() - 1;
  await queue.ctx.storage.put("team-matchmaking-state-v1", state);
  await queue.alarm();

  const matched = await parse(await queue.fetch(request("/status", {
    clientRequestId: people[0].clientRequestId,
  }, people[0].profileToken)));
  assert.equal(matched.body.status, "MATCHED");
  assert.equal(matched.body.state.players.length, 4);
  assert.ok(matched.body.state.players.every((player) => player.characterId === "dog"));
});

test("Team Up bot strategy prefers sharing a road cell with its teammate when legal", () => {
  const state = {
    schemaVersion: 1,
    matchId: "TEAMTEST1",
    status: "ACTIVE",
    matchMode: "TEAM_UP",
    targetPlayerCount: 4,
    rulesetKey: "TEAM_UP_V3",
    revision: 1,
    updatedAt: 1,
    createdAt: 1,
    turnSeat: 0,
    teamAssignments: ["A", "B", "A", "B"],
    players: [
      { playerId: "bot", color: "RED", teamId: "A", playerType: "BOT", tokens: [1, 0, -1, -1] },
      { playerId: "opponent1", color: "GREEN", teamId: "B", playerType: "HUMAN", tokens: [-1, -1, -1, -1] },
      { playerId: "partner", color: "YELLOW", teamId: "A", playerType: "HUMAN", tokens: [28, -1, -1, -1] },
      { playerId: "opponent2", color: "BLUE", teamId: "B", playerType: "HUMAN", tokens: [-1, -1, -1, -1] },
    ],
    pendingRoll: {
      status: "RESOLVED",
      seat: 0,
      scheduledSeat: 0,
      playerId: "bot",
      outcome: 1,
      legalTokenIndexes: [0, 1],
    },
    consecutiveSixes: [0, 0, 0, 0],
    turnTimeoutCounts: {},
    winnerPlayerId: null,
    winnerTeamId: null,
  };
  assert.equal(chooseBotMove(state, "bot", [0, 1]), 0);
});

test("Online 2-player fallback adds exactly one bot for a lone queued human", async () => {
  const env = makeEnv();
  const queue = new MatchmakerQueue({ storage: new FakeStorage() }, env);
  const person = human(21, 2);
  const searching = await parse(await queue.fetch(
    request("/search", {
      clientRequestId: person.clientRequestId,
      displayName: person.displayName,
      playerCount: 2,
      characterId: person.characterId,
      rulesetKey: "CLASSIC_V3",
    }, person.profileToken),
  ));
  assert.equal(searching.body.status, "SEARCHING");

  await backdateQueue(queue, "matchmaking-state");
  await queue.alarm();

  const result = await parse(await queue.fetch(
    request("/status", {
      clientRequestId: person.clientRequestId,
      playerCount: 2,
    }, person.profileToken),
  ));
  assert.equal(result.body.status, "MATCHED");
  assert.equal(result.body.state.players.length, 2);
  assert.equal(result.body.state.rulesetId, "ludoproof-standard-v3");
  assert.deepEqual(
    result.body.state.players.map((player) => player.playerType),
    ["HUMAN", "BOT"],
  );
  assert.equal(
    (await queue.ctx.storage.get("matchmaking-state")).queue.length,
    0,
  );
});

test("Team Up V4 fallback fills bots and starts an authoritative V4 match", async () => {
  const env = makeEnv();
  const queue = new TeamMatchmakerQueue({ storage: new FakeStorage() }, env);
  const player = human(61, 4, true);
  const search = await parse(await queue.fetch(
    request("/search", {
      clientRequestId: player.clientRequestId,
      displayName: player.displayName,
      characterId: player.characterId,
      rulesetKey: "TEAM_UP_V4",
    }, player.profileToken),
  ));
  assert.equal(search.status, 202);
  assert.equal(search.body.status, "SEARCHING");

  await backdateQueue(queue, "team-matchmaking-state-v1");
  await queue.alarm();

  const matched = await parse(await queue.fetch(
    request("/status", {
      clientRequestId: player.clientRequestId,
    }, player.profileToken),
  ));
  assert.equal(matched.status, 200);
  assert.equal(matched.body.status, "MATCHED");
  assert.equal(matched.body.state.status, "ACTIVE");
  assert.equal(matched.body.state.matchMode, "TEAM_UP");
  assert.equal(matched.body.state.rulesetId, "ludoproof-team-v4");
  assert.equal(matched.body.state.players.length, 4);
  assert.deepEqual(
    matched.body.state.players.map((seat) => seat.playerType),
    ["HUMAN", "BOT", "BOT", "BOT"],
  );
  assert.deepEqual(
    matched.body.state.players.map((seat) => seat.teamId),
    ["A", "B", "A", "B"],
  );
});

test("Team Up fallback can fill all three empty seats with bots", async () => {
  const env = makeEnv();
  const queue = new TeamMatchmakerQueue({ storage: new FakeStorage() }, env);
  const person = human(31, 4, true);
  person.characterId = "dog";
  const searching = await parse(await queue.fetch(
    request("/search", {
      clientRequestId: person.clientRequestId,
      displayName: person.displayName,
      characterId: person.characterId,
      rulesetKey: "TEAM_UP_V3",
    }, person.profileToken),
  ));
  assert.equal(searching.body.status, "SEARCHING");

  await backdateQueue(queue, "team-matchmaking-state-v1");
  await queue.alarm();

  const result = await parse(await queue.fetch(
    request("/status", {
      clientRequestId: person.clientRequestId,
    }, person.profileToken),
  ));
  assert.equal(result.body.status, "MATCHED");
  assert.equal(result.body.state.players.length, 4);
  assert.equal(result.body.state.rulesetId, "ludoproof-team-v3");
  assert.deepEqual(
    result.body.state.players.map((player) => player.playerType),
    ["HUMAN", "BOT", "BOT", "BOT"],
  );
  assert.deepEqual(
    result.body.state.players.map((player) => player.teamId),
    ["A", "B", "A", "B"],
  );
  assert.equal(new Set(result.body.state.players.map((player) => player.characterId)).size, 4);
  const stored = await env.LUDOPROOF_MATCHES.rooms
    .get(result.body.matchId).ctx.storage.get("match-state");
  assert.equal(stored.players.filter((player) => player.playerType === "BOT").length, 3);
  assert.ok(stored.players.filter((player) => player.playerType === "BOT")
    .every((player) => player.profileId == null));
});


test("Online reservation retries the same bot seats and blocks cancellation/remixing after a partial room setup", async () => {
  const env = makeEnv();
  const namespace = env.LUDOPROOF_MATCHES;
  const originalGet = namespace.get.bind(namespace);
  let failFirstJoin = true;
  namespace.get = (id) => {
    const room = originalGet(id);
    return {
      fetch: async (request) => {
        if (failFirstJoin && new URL(request.url).pathname === "/join") {
          failFirstJoin = false;
          return Response.json(
            { error: "TEMPORARY_TEST_FAILURE", message: "temporary room failure" },
            { status: 503 },
          );
        }
        return room.fetch(request);
      },
    };
  };

  const queue = new MatchmakerQueue({ storage: new FakeStorage() }, env);
  const first = human(41, 2);
  const firstSearch = await parse(await queue.fetch(
    request("/search", {
      clientRequestId: first.clientRequestId,
      displayName: first.displayName,
      playerCount: 2,
      characterId: first.characterId,
    }, first.profileToken),
  ));
  assert.equal(firstSearch.body.status, "SEARCHING");

  await backdateQueue(queue, "matchmaking-state");
  await queue.alarm();

  let reservedState = await queue.ctx.storage.get("matchmaking-state");
  assert.ok(reservedState.pendingMatch);
  const reservedBots = reservedState.pendingMatch.participants
    .filter((player) => player.playerType === "BOT");
  assert.equal(reservedBots.length, 1);
  const reservedMatchId = reservedState.pendingMatch.matchId;
  assert.ok(reservedMatchId);
  const reservedBotRequestId = reservedBots[0].clientRequestId;
  const reservedBotName = reservedBots[0].displayName;
  assert.equal(reservedState.assignments[first.clientRequestId], undefined);

  const cancel = await parse(await queue.fetch(
    request("/cancel", {
      clientRequestId: first.clientRequestId,
      playerCount: 2,
    }, first.profileToken),
  ));
  assert.equal(cancel.body.status, "MATCHED");
  assert.equal(cancel.body.pending, true);
  assert.equal(cancel.body.cancelled, false);

  const second = human(42, 2);
  const secondSearch = await parse(await queue.fetch(
    request("/search", {
      clientRequestId: second.clientRequestId,
      displayName: second.displayName,
      playerCount: 2,
      characterId: second.characterId,
    }, second.profileToken),
  ));
  assert.equal(secondSearch.body.status, "SEARCHING");
  reservedState = await queue.ctx.storage.get("matchmaking-state");
  assert.equal(reservedState.pendingMatch.participants[0].clientRequestId, first.clientRequestId);
  assert.ok(reservedState.queue.some((player) => player.clientRequestId === second.clientRequestId));

  reservedState.pendingMatch.retryAt = Date.now() - 1;
  await queue.ctx.storage.put("matchmaking-state", reservedState);
  await queue.alarm();

  const finalized = await queue.ctx.storage.get("matchmaking-state");
  assert.equal(finalized.pendingMatch, undefined);
  assert.ok(finalized.assignments[first.clientRequestId]);
  assert.equal(finalized.assignments[second.clientRequestId], undefined);
  assert.ok(finalized.queue.some((player) => player.clientRequestId === second.clientRequestId));

  const matched = await parse(await queue.fetch(
    request("/status", {
      clientRequestId: first.clientRequestId,
      playerCount: 2,
    }, first.profileToken),
  ));
  assert.equal(matched.body.status, "MATCHED");
  assert.equal(matched.body.matchId, reservedMatchId);
  assert.deepEqual(
    matched.body.state.players.map((player) => player.playerType),
    ["HUMAN", "BOT"],
  );
  assert.equal(matched.body.state.players[1].displayName, reservedBotName);

  const room = namespace.rooms.get(matched.body.matchId);
  const storedMatch = await room.ctx.storage.get("match-state");
  assert.equal(storedMatch.players[1].joinRequestId, reservedBotRequestId);
  assert.ok(storedMatch.players[1].profileId == null);
  assert.equal(storedMatch.players[1].playerType, "BOT");
});


test("Team Up reservation retries the same three bot seats after partial room materialization", async () => {
  const env = makeEnv();
  const namespace = env.LUDOPROOF_MATCHES;
  const originalGet = namespace.get.bind(namespace);
  let failFirstJoin = true;
  namespace.get = (id) => {
    const room = originalGet(id);
    return {
      fetch: async (request) => {
        if (failFirstJoin && new URL(request.url).pathname === "/join") {
          failFirstJoin = false;
          return Response.json(
            { error: "TEMPORARY_TEST_FAILURE", message: "temporary room failure" },
            { status: 503 },
          );
        }
        return room.fetch(request);
      },
    };
  };

  const queue = new TeamMatchmakerQueue({ storage: new FakeStorage() }, env);
  const first = human(51, 4, true);
  const firstSearch = await parse(await queue.fetch(
    request("/search", {
      clientRequestId: first.clientRequestId,
      displayName: first.displayName,
      characterId: first.characterId,
    }, first.profileToken),
  ));
  assert.equal(firstSearch.body.status, "SEARCHING");

  await backdateQueue(queue, "team-matchmaking-state-v1");
  await queue.alarm();

  let reservedState = await queue.ctx.storage.get("team-matchmaking-state-v1");
  assert.ok(reservedState.pendingMatch);
  const reservedBots = reservedState.pendingMatch.participants
    .filter((player) => player.playerType === "BOT");
  assert.equal(reservedBots.length, 3);
  const reservedMatchId = reservedState.pendingMatch.matchId;
  assert.ok(reservedMatchId);
  const reservedBotIds = reservedBots.map((bot) => bot.clientRequestId);

  const second = human(52, 4, true);
  const secondSearch = await parse(await queue.fetch(
    request("/search", {
      clientRequestId: second.clientRequestId,
      displayName: second.displayName,
      characterId: second.characterId,
    }, second.profileToken),
  ));
  assert.equal(secondSearch.body.status, "SEARCHING");

  reservedState = await queue.ctx.storage.get("team-matchmaking-state-v1");
  reservedState.pendingMatch.retryAt = Date.now() - 1;
  await queue.ctx.storage.put("team-matchmaking-state-v1", reservedState);
  await queue.alarm();

  const finalized = await queue.ctx.storage.get("team-matchmaking-state-v1");
  assert.equal(finalized.pendingMatch, undefined);
  assert.ok(finalized.assignments[first.clientRequestId]);
  assert.equal(finalized.assignments[second.clientRequestId], undefined);
  assert.ok(finalized.queue.some((player) => player.clientRequestId === second.clientRequestId));

  const matched = await parse(await queue.fetch(
    request("/status", {
      clientRequestId: first.clientRequestId,
    }, first.profileToken),
  ));
  assert.equal(matched.body.status, "MATCHED");
  assert.equal(matched.body.matchId, reservedMatchId);
  assert.deepEqual(
    matched.body.state.players.map((player) => player.playerType),
    ["HUMAN", "BOT", "BOT", "BOT"],
  );
  assert.deepEqual(
    matched.body.state.players.map((player) => player.teamId),
    ["A", "B", "A", "B"],
  );

  const room = namespace.rooms.get(matched.body.matchId);
  const storedMatch = await room.ctx.storage.get("match-state");
  assert.deepEqual(
    storedMatch.players.filter((player) => player.playerType === "BOT")
      .map((player) => player.joinRequestId),
    reservedBotIds,
  );
});
