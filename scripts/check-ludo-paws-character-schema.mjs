import fs from "node:fs";

const read = (path) => fs.readFileSync(path, "utf8");
const requireText = (source, token, message) => {
  if (!source.includes(token)) throw new Error(message);
};

const serverContract = read("server/src/ludo-paws-characters.js");
const gameCorePath = fs.existsSync("server/src/game-v1-core.js")
  ? "server/src/game-v1-core.js"
  : "server/src/game.js";
const game = read(gameCorePath);
const room = read("server/src/match-room.js");
const publicQueue = read("server/src/matchmaker-queue.js");
const teamQueue = read("server/src/team-matchmaker-queue.js");
const serverTests = read("server/test/ludo-paws-characters.test.js");
const androidContract = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/domain/LudoPawsCharacterIdentityContract.kt",
);
const androidPolicy = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/domain/OnlineLudoPawsCharacterPolicy.kt",
);
const gameApi = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/data/remote/GameApi.kt",
);
const models = read(
  "android/app/src/main/java/com/rekixo/ludopaws/game/domain/model/GameModels.kt",
);

requireText(serverContract, 'CHARACTER_ID_WIRE_FIELD = "characterId"', "Server character wire field changed unexpectedly.");
requireText(serverContract, "CHARACTER_ID_SCHEMA_VERSION = 1", "Server character schema version must stay explicit.");
requireText(serverContract, 'DEFAULT_CHARACTER_ID = "dog"', "Server must retain deterministic canonical Dog fallback.");
requireText(serverContract, "LEGACY_CHARACTER_ALIASES", "Server must retain an explicit legacy-id compatibility bridge.");
requireText(serverContract, "INVALID_CHARACTER_ID", "Unsupported new-client character IDs must be rejected.");

requireText(androidContract, 'WIRE_FIELD = "characterId"', "Android character wire field must match server.");
requireText(androidContract, "SCHEMA_VERSION = 1", "Android character schema version must match server.");
requireText(androidContract, "resolveRemote", "Android must have one fallback resolver for remote character IDs.");
requireText(androidPolicy, "LudoPawsCharacterIdentityContract", "Online rendering must consume the centralized character contract.");
requireText(models, "val characterId: String? = null", "Old server states must keep characterId optional on Android.");
// The wire identity is normalized by the centralized compatibility contract.
const normalizedCharacterRequest =
  /\.put\(\s*"characterId"\s*,\s*(?:com\.rekixo\.ludopaws\.feature\.online\.domain\.)?LudoPawsCharacterIdentityContract\.resolveForRequest\(\s*characterId\s*\)\s*\)/g;
if ((gameApi.match(normalizedCharacterRequest) ?? []).length < 4) {
  throw new Error("New Android create/join/search calls must send normalized cosmetic identity.");
}

for (const source of [room, publicQueue, teamQueue]) {
  requireText(source, "characterId", "Every remote room/matchmaking path must preserve character identity.");
}

const proofStart = game.indexOf("export function authoritativeStateForRandomness");
const proofEnd = game.indexOf("export function reserveRoll", proofStart);
if (proofStart < 0 || proofEnd <= proofStart) {
  throw new Error("Could not locate authoritative randomness state.");
}
const proofState = game.slice(proofStart, proofEnd);
if (proofState.includes("characterId") || proofState.includes("characterSchema")) {
  throw new Error("Cosmetic character metadata must stay outside authoritative randomness/proof state.");
}

for (const marker of [
  "canonical 3D Ludo Paws character IDs are accepted and normalized",
  "legacy starter IDs migrate to the canonical 3D animals",
  "unknown persisted character values fall back without making rooms unreadable",
  "legacy create and join calls that omit character identity resolve to dog",
  "proof state ignores cosmetics",
]) {
  requireText(serverTests, marker, `Character-schema compatibility coverage is missing: ${marker}`);
}

console.log(
  "Ludo Paws character-schema gate passed: canonical 3D roster, stable wire/schema contract, legacy aliases, request validation and proof isolation are locked.",
);
