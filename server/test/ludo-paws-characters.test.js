import test from "node:test";
import assert from "node:assert/strict";
import {
  CHARACTER_ID_SCHEMA_VERSION,
  CHARACTER_ID_WIRE_FIELD,
  LUDO_PAWS_CHARACTER_IDS,
  normalizeCharacterId,
  publicCharacterId,
} from "../src/ludo-paws-characters.js";
import {
  addPlayer,
  authoritativeStateForRandomness,
  newMatch,
  publicState,
} from "../src/game.js";

test("character identity wire contract stays backward compatible", () => {
  assert.equal(CHARACTER_ID_WIRE_FIELD, "characterId");
  assert.equal(CHARACTER_ID_SCHEMA_VERSION, 1);
});

test("canonical 3D Ludo Paws character IDs are accepted and normalized", () => {
  assert.deepEqual(
    LUDO_PAWS_CHARACTER_IDS,
    ["dog", "goat", "duck", "cat", "donkey", "goat_lumi"],
  );
  assert.equal(normalizeCharacterId(), "dog");
  assert.equal(normalizeCharacterId(null), "dog");
  assert.equal(normalizeCharacterId("   "), "dog");
  assert.equal(normalizeCharacterId("  CAT  "), "cat");
});

test("legacy starter IDs migrate to the canonical 3D animals", () => {
  assert.equal(normalizeCharacterId("squirrel"), "dog");
  assert.equal(normalizeCharacterId("hedgehog"), "goat");
  assert.equal(normalizeCharacterId("sheep"), "cat");
  assert.equal(publicCharacterId("SQUIRREL"), "dog");
  assert.equal(publicCharacterId("HEDGEHOG"), "goat");
  assert.equal(publicCharacterId("SHEEP"), "cat");
});

test("unsupported remote character IDs are rejected at the request boundary", () => {
  assert.throws(
    () => normalizeCharacterId("dragon"),
    (error) =>
      error?.status === 400 &&
      error?.code === "INVALID_CHARACTER_ID",
  );
});

test("unknown persisted character values fall back without making rooms unreadable", () => {
  assert.equal(publicCharacterId(undefined), "dog");
  assert.equal(publicCharacterId("legacy_pet"), "dog");
});

test("legacy create and join calls that omit character identity resolve to dog", () => {
  const created = newMatch({
    matchId: "LPPAWS1234",
    hostPlayerId: "p1",
    hostDisplayName: "Host",
    now: 1,
    targetPlayerCount: 2,
    matchMode: "ONLINE",
  });
  const joined = addPlayer(created, {
    playerId: "p2",
    displayName: "Guest",
    tokenAuthHash: "hash",
    now: 2,
  });

  assert.deepEqual(
    publicState(joined).players.map((player) => player.characterId),
    ["dog", "dog"],
  );
});

test("public player state exposes character identity but proof state ignores cosmetics", () => {
  const dog = newMatch({
    matchId: "LPPAWS1234",
    hostPlayerId: "p1",
    hostDisplayName: "Player",
    hostCharacterId: "dog",
    now: 1,
    targetPlayerCount: 2,
    matchMode: "ONLINE",
  });
  const cat = structuredClone(dog);
  cat.players[0].characterId = "cat";

  assert.equal(publicState(dog).players[0].characterId, "dog");
  assert.equal(publicState(cat).players[0].characterId, "cat");
  assert.deepEqual(
    authoritativeStateForRandomness(dog),
    authoritativeStateForRandomness(cat),
  );
  assert.equal(
    CHARACTER_ID_WIRE_FIELD in authoritativeStateForRandomness(dog).players[0],
    false,
  );
});
