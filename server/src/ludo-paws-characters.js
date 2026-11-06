import { httpError } from "./crypto.js";

export const DEFAULT_CHARACTER_ID = "dog";
export const CHARACTER_ID_WIRE_FIELD = "characterId";
export const CHARACTER_ID_SCHEMA_VERSION = 1;

// Server-created bots may use EVERY entry in this roster (including future additions).
// Free human defaults must be explicitly registered; newly added characters
// remain locked to humans until granted by the account cosmetic system.
export const FREE_HUMAN_CHARACTER_IDS = Object.freeze(["dog", "goat", "duck", "cat"]);

export const LUDO_PAWS_CHARACTER_IDS = Object.freeze([
  "dog",
  "goat",
  "duck",
  "cat",
  "donkey",
  "goat_lumi",
  "cock",
  "pig",
  "elephant",
  "horse",
  "rabbit",
  "panda",
  "monkey",
  "giraffe",
]);

const CHARACTER_IDS = new Set(LUDO_PAWS_CHARACTER_IDS);
const FREE_HUMAN_IDS = new Set(FREE_HUMAN_CHARACTER_IDS);
if (!FREE_HUMAN_CHARACTER_IDS.every((id) => CHARACTER_IDS.has(id))) {
  throw new Error("free human characters must be in the registered roster");
}

export function characterNeedsOwnership(characterId) {
  return !FREE_HUMAN_IDS.has(normalizeCharacterId(characterId));
}

const LEGACY_CHARACTER_ALIASES = new Map([
  ["squirrel", "dog"],
  ["hedgehog", "goat"],
  ["sheep", "cat"],
  ["lion", "donkey"],
]);

function canonicalCharacterId(value) {
  const characterId = String(value).trim().toLowerCase();
  return LEGACY_CHARACTER_ALIASES.get(characterId) ?? characterId;
}

/**
 * Normalizes presentation-only Ludo Paws identity coming from an Android client.
 * The wire field/schema stay stable while old starter ids are migrated onto the
 * canonical registered 3D animal roster.
 */
export function normalizeCharacterId(value) {
  if (value == null || String(value).trim() === "") {
    return DEFAULT_CHARACTER_ID;
  }

  const characterId = canonicalCharacterId(value);
  if (!CHARACTER_IDS.has(characterId)) {
    throw httpError(
      400,
      "INVALID_CHARACTER_ID",
      "selected Ludo Paws character is not supported",
    );
  }
  return characterId;
}

/**
 * Reads legacy persisted state safely without trusting arbitrary stored values.
 * Old valid starter ids are translated; unrelated values fall back to Dog.
 */
export function publicCharacterId(value) {
  const raw =
    typeof value === "string"
      ? value.trim().toLowerCase()
      : "";
  if (!raw) {
    return DEFAULT_CHARACTER_ID;
  }
  const characterId = canonicalCharacterId(raw);
  return CHARACTER_IDS.has(characterId)
    ? characterId
    : DEFAULT_CHARACTER_ID;
}
