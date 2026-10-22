import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const read = (relative) => {
  const absolute = path.join(root, relative);
  if (!fs.existsSync(absolute)) {
    throw new Error(`Ludo Paws selection UX gate: missing ${relative}`);
  }
  return fs.readFileSync(absolute, "utf8");
};

const requireText = (source, needle, label) => {
  if (!source.includes(needle)) {
    throw new Error(`Ludo Paws selection UX gate: ${label} is missing ${needle}`);
  }
};

const policyPath =
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/selection/StarterPawsAssignmentPolicy.kt";
const storePath =
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/data/local/OfflineCharacterSetupStore.kt";
const uiPath =
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/setup/OfflineCharacterSelectionUi.kt";
const activityPath =
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/OfflineGameActivity.kt";
const screenPath =
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/setup/OfflineSetupScreenUi.kt";
const selectionPath =
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/setup/OfflineSetupSelectionUi.kt";
const testPath =
  "android/app/src/test/java/com/rekixo/ludopaws/feature/characters/domain/selection/StarterPawsAssignmentPolicyTest.kt";

const policy = read(policyPath);
const store = read(storePath);
const ui = read(uiPath);
const activity = read(activityPath);
const screen = read(screenPath);
const selection = read(selectionPath);
const tests = read(testPath);

for (const required of [
  "playerCount in MIN_PLAYERS..MAX_PLAYERS",
  "computerMode",
  "ownerSlot",
  "current[ownerSlot] = previousCharacterId",
  "LudoPawsCharacterCatalog.STARTER_PACK_ID",
]) {
  requireText(policy, required, "assignment policy");
}

for (const required of [
  "ludo_paws_offline_character_setup_v1",
  "saveSetup(",
  "saveActive(",
  "loadActive()",
  "StarterPawsAssignmentPolicy.normalize",
  "mode.isLocal",
]) {
  requireText(store, required, "offline character persistence");
}

for (const required of [
  '"CHOOSE YOUR PAW"',
  "charactersForPack(",
  // Character selection now uses production 3D portraits instead of fallback drawable names.
  "LudoPawsCharacterAvatarView",
  "showCircularChrome = false",
  "selectCharacterForActiveSlot(",
  "rebuildCharacterSlotButtons()",
  "characterSelectionStore.select(",
]) {
  requireText(ui, required, "character selection UI");
}

for (const required of [
  "CharacterSelectionStore(this)",
  "OfflineCharacterSetupStore(this)",
  "initializeCharacterSetup()",
  "selectedCharacterIds",
  "characterSlotsRow",
]) {
  requireText(activity, required, "offline activity integration");
}

requireText(screen, "characterPanel(", "offline setup screen");
requireText(screen, "persistActiveCharacterSetup()", "offline setup start gate");
requireText(selection, "updateSelectedPlayerCount(", "player count integration");
requireText(selection, "refreshCharacterSelectionUi()", "setup refresh integration");

for (const required of [
  "computer mode keeps human preference first and assigns unique cpus",
  "pass and play defaults to unique production animals",
  "selecting an animal owned by another local player swaps slots",
  "legacy ids are normalized before assignment",
  "invalid character request repairs to safe production assignment",
  "only two to four local players are accepted",
]) {
  requireText(tests, required, "Phase 4 unit tests");
}

// Character identity remains presentation-only. Chosen board/team colors are
// authoritative seat metadata and must flow explicitly into the local match.
const session = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/domain/session/LocalMatchSession.kt",
);
if (/character(Id|Ids|Selection)/.test(session)) {
  throw new Error(
    "Ludo Paws selection UX gate: character metadata leaked into LocalMatchSession gameplay contract",
  );
}

// Pass & Play must retain every player's selected color from setup through the
// authoritative match and rematch, not regenerate P2–P4 from P1's color.
const colorPolicy = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/domain/engine/OfflinePlayerLayout.kt",
);
const engine = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/domain/engine/OfflineGameEngine.kt",
);
const rematch = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/gameplay/OfflineGameplayControlsUi.kt",
);
const colorTests = read(
  "android/app/src/test/java/com/rekixo/ludopaws/OfflinePlayerLayoutTest.kt",
);
for (const [source, markers, label] of [
  [colorPolicy, ["normalizeColorsForSeats(", "selectColorForSeat(", "oppositeColor(requestedColor)", "colors[owner] = previous"], "color selection policy"],
  [store, ["colorsBySeat: List<String> = emptyList()", "setupColorsKey(mode)", "putString(setupColorsKey(mode)"], "color persistence"],
  [ui, ["selectedColorsBySeat = saved.colorsBySeat", "selectedColorsBySeat[selectedCharacterSlot]", "selectColorForActiveSlot("], "player slot selection"],
  [selection, ["selectColorForActiveSlot(entry.key)", "colorSelectionStatus?.text"], "color picker"],
  [screen, ["colorsBySeat = if (isComputerMode) null else selectedColorsBySeat"], "game start"],
  [session, ["colorsBySeat: List<String>? = null", "colorsBySeat = colorsBySeat"], "session contract"],
  [engine, ["colorsBySeat?.toList()", "colorsBySeat.distinct().size == playerCount"], "engine assignment"],
  [rematch, ["selectedColorsBySeat = finished.players.map { it.color }", "colorsBySeat = if (isComputerMode) null else selectedColorsBySeat"], "rematch"],
  [colorTests, ["passAndPlayThreePlayersKeepIndividualSelectedColors", "fourPlayersSwapColorsWithoutDuplicates", "twoPlayersKeepOppositeColorsWhenEitherPlayerChooses"], "regression tests"],
]) {
  for (const marker of markers) {
    requireText(source, marker, label);
  }
}

// Seat color must reach both selected-pet previews and all active 3D pawns.
const avatarView = read(
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsCharacterAvatarView.kt",
);
const sceneRenderer = read(
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPaws3DSceneRenderer.kt",
);
for (const [source, markers, label] of [
  [ui, ["modelTeamColor = selectedColor", "showTeamBadge = true", "ForegroundColorSpan(seatTint)"], "character previews"],
  [avatarView, ["showTeamBadge: Boolean = false", "resolve(modelTeamColor)?.argb", "canvas.drawCircle(83f, 83f, 9f, fill)"], "avatar team badges"],
  [sceneRenderer, ["characterIdForSeat(", "state.characterIdsBySeat, player.seat, player.color"], "3D seat-based pawns"],
]) {
  for (const marker of markers) requireText(source, marker, label);
}
if (sceneRenderer.includes("characterIdForColor(player.color)")) {
  throw new Error("Ludo Paws selection UX gate: pawn renderer still falls back to color instead of seat identity");
}

console.log(
  "Ludo Paws selection UX gate passed: unique starter characters, per-player color persistence, seat-stable match/rematch colors and gameplay isolation.",
);
