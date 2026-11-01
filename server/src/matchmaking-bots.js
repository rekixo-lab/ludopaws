import { LUDO_PAWS_CHARACTER_IDS } from "./ludo-paws-characters.js";

const BOT_NAMES = Object.freeze([
  "Aarav", "Maya", "Leo", "Zara", "Arjun", "Ava",
  "Noah", "Anaya", "Ishaan", "Mia", "Kabir", "Riya",
  "Ethan", "Meera", "Ayaan", "Sofia", "Reyansh", "Ira",
  "Vihaan", "Nora", "Dev", "Tara", "Rohan", "Kiara",
]);

/**
 * Draw from the whole pool with 32-bit rejection sampling. The previous
 * single-byte index could never reach character indexes >= 256 after future
 * roster expansions, and modulo directly introduces distribution bias.
 */
function secureIndex(length) {
  if (!Number.isSafeInteger(length) || length < 1 || length > 0x1_0000_0000) {
    throw new RangeError("invalid bot selection pool size");
  }
  const universe = 0x1_0000_0000;
  const ceiling = universe - universe % length;
  const random = new Uint32Array(1);
  do {
    crypto.getRandomValues(random);
  } while (random[0] >= ceiling);
  return random[0] % length;
}

/** Tests can inject an index picker; production always uses cryptographic RNG. */
export function selectBotCharacterFromRoster(roster, usedCharacters, indexPicker = secureIndex) {
  if (!Array.isArray(roster) || roster.length === 0) {
    throw new RangeError("bot character roster must not be empty");
  }
  const unused = roster.filter((id) => !usedCharacters.has(id));
  const pool = unused.length > 0 ? unused : roster;
  return pool[indexPicker(pool.length)];
}

const DEFAULT_FILL_DELAY_MS = 15_000;
const DEFAULT_TURN_DELAY_MS = 1_100;
const DEFAULT_RESULT_HOLD_MS = 1_400;
const DEFAULT_RETRY_DELAY_MS = 3_000;

function isEnabled(value, fallback = true) {
  if (value == null || String(value).trim() === "") return fallback;
  return !["0", "false", "off", "no", "disabled"].includes(
    String(value).trim().toLowerCase(),
  );
}

function boundedMilliseconds(value, fallback, min, max) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= min && number <= max
    ? number
    : fallback;
}

export function botMatchFillEnabled(env) {
  return isEnabled(env?.LUDOPROOF_BOT_MATCH_FILL_ENABLED);
}

export function botTurnsEnabled(env) {
  return isEnabled(env?.LUDOPROOF_BOT_TURNS_ENABLED);
}

export function botFillDelayMs(env) {
  return boundedMilliseconds(
    env?.LUDOPROOF_BOT_FILL_DELAY_MS,
    DEFAULT_FILL_DELAY_MS,
    250,
    120_000,
  );
}

export function botTurnDelayMs(env) {
  return boundedMilliseconds(
    env?.LUDOPROOF_BOT_TURN_DELAY_MS,
    DEFAULT_TURN_DELAY_MS,
    1_100,
    10_000,
  );
}

export function botResultHoldMs(env) {
  return boundedMilliseconds(
    env?.LUDOPROOF_BOT_RESULT_HOLD_MS,
    DEFAULT_RESULT_HOLD_MS,
    1_400,
    5_000,
  );
}

export function botRetryDelayMs(env) {
  return boundedMilliseconds(
    env?.LUDOPROOF_BOT_RETRY_DELAY_MS,
    DEFAULT_RETRY_DELAY_MS,
    250,
    30_000,
  );
}

/**
 * Creates server-owned bot seats, not fake authenticated users or queue tickets.
 * Names are natural display names; the public playerType remains BOT so UI can
 * label them transparently. IDs are ephemeral per match and never have profiles.
 */
export function createBotParticipants({
  count,
  playerCount,
  matchMode,
  rulesetKey = matchMode === "TEAM_UP" ? "TEAM_UP_V4" : "CLASSIC_V4",
  existingNames = [],
  existingCharacterIds = [],
  now = Date.now(),
}) {
  if (!Number.isInteger(count) || count < 0 || count > 3) {
    throw new RangeError("bot seat count must be between zero and three");
  }
  if (playerCount !== 2 && playerCount !== 4) {
    throw new RangeError("bot player count must be two or four");
  }
  if (matchMode !== "ONLINE" && matchMode !== "TEAM_UP") {
    throw new RangeError("bots are supported only in public Online and Team Up matches");
  }
  if (matchMode === "TEAM_UP" && playerCount !== 4) {
    throw new RangeError("Team Up bots require four seats");
  }
  const allowedRulesets = matchMode === "TEAM_UP"
    ? ["TEAM_UP_V1", "TEAM_UP_V2", "TEAM_UP_V3", "TEAM_UP_V4", "TEAM_UP_V5"]
    : ["CLASSIC_V1", "CLASSIC_V2", "CLASSIC_V3", "CLASSIC_V4", "CLASSIC_V5", "CLASSIC_V6"];
  if (!allowedRulesets.includes(rulesetKey)) {
    throw new RangeError("bot ruleset does not match the selected match mode");
  }

  const usedNames = new Set(existingNames.map((name) => String(name).trim().toLocaleLowerCase("en")));
  const usedCharacters = new Set(
    existingCharacterIds
      .map((id) => String(id ?? "").trim().toLowerCase())
      .filter((id) => LUDO_PAWS_CHARACTER_IDS.includes(id)),
  );
  const bots = [];
  for (let index = 0; index < count; index += 1) {
    const availableNames = BOT_NAMES.filter(
      (name) => !usedNames.has(name.toLocaleLowerCase("en")),
    );
    const namePool = availableNames.length > 0 ? availableNames : BOT_NAMES;
    const displayName = namePool[secureIndex(namePool.length)];
    usedNames.add(displayName.toLocaleLowerCase("en"));

    // Prefer a character not already assigned to a human or earlier bot.
    // The entire server roster remains available to auto-filled bots,
    // irrespective of player unlocks or new premium characters added later.
    const characterId = selectBotCharacterFromRoster(LUDO_PAWS_CHARACTER_IDS, usedCharacters);
    usedCharacters.add(characterId);

    bots.push({
      clientRequestId: crypto.randomUUID(),
      displayName,
      characterId,
      profileId: null,
      playerType: "BOT",
      playerCount,
      matchMode,
      rulesetKey,
      joinedAt: now,
      lastSeenAt: now,
    });
  }
  return bots;
}
