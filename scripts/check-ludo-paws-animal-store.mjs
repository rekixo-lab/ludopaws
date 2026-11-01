import fs from "node:fs";

const read = (path) => fs.readFileSync(path, "utf8");
const requireText = (source, token, message) => {
  if (!source.includes(token)) throw new Error(message);
};

const models = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/domain/model/CosmeticModels.kt",
);
const storeModels = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/domain/model/StoreModels.kt",
);
const catalog = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/domain/StoreCosmeticCatalog.kt",
);
const inventory = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/data/local/CosmeticInventoryStore.kt",
);
const preferences = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/data/local/StorePreferences.kt",
);
const contentUi = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/components/StoreContentUi.kt",
);
const pawsUi = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/components/StorePawsCatalogUi.kt",
);
const actions = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/actions/StoreCosmeticActions.kt",
);
const boardCatalogUi = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/components/StoreBoardCatalogUi.kt",
);
const diceCatalogUi = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/components/StoreDiceCatalogUi.kt",
);
const tests = read(
  "android/app/src/test/java/com/rekixo/ludopaws/feature/store/domain/CharacterPackStoreCatalogTest.kt",
);

requireText(models, "CHARACTER_PACK", "Character packs must be a first-class cosmetic category.");
requireText(models, "EVENT", "Character-pack progression must support verified event unlocks.");
requireText(models, "contentAvailable", "Character-pack offers must fail closed until their full content ships.");
requireText(storeModels, 'PAWS("PAWS")', "The store must expose a dedicated PAWS tab.");
requireText(catalog, "DEFAULT_CHARACTER_PACK", "Starter Paws must have a stable default store ID.");
requireText(catalog, "character_pack_starter_paws", "Starter Paws store offer is missing.");
requireText(catalog, "CosmeticUnlockKind.GEMS", "Gem animal-pack progression is missing.");
requireText(catalog, "CosmeticUnlockKind.REWARDED_ADS", "Rewarded-ad animal-pack progression is missing.");

const availabilityGuard = inventory.indexOf("if (!cosmetic.contentAvailable || !cosmetic.purchaseAvailable)");
const gemDebit = inventory.indexOf("balance -");
if (availabilityGuard < 0 || gemDebit < 0 || availabilityGuard > gemDebit) {
  throw new Error("Unavailable animal packs must be rejected before any gem debit can occur.");
}
requireText(inventory, "recordVerifiedEventEntitlement", "Verified event entitlement support is missing.");
requireText(inventory, "CharacterSelectionStore", "Equipping a character pack must synchronize character selection.");
requireText(inventory, "KEY_SELECTED_CHARACTER_PACK", "Selected character pack must persist independently.");
requireText(preferences, "KEY_EVENT_ENTITLEMENTS", "Verified event entitlements must persist.");
requireText(contentUi, "StoreTab.PAWS", "PAWS tab must route to animal-pack content.");
requireText(pawsUi, "LudoPawsCharacterCatalog.charactersForPack(LudoPawsCharacterCatalog.STARTER_PACK_ID)", "Store must list shipped starter characters rather than coming-soon packs.");
requireText(boardCatalogUi, "StoreCosmeticCatalog.boards.filter { it.contentAvailable }", "Only released boards must be listed.");
requireText(diceCatalogUi, "StoreCosmeticCatalog.dice.filter { it.contentAvailable }", "Only released dice must be listed.");
requireText(catalog, "return (visibleBuiltIns + newRemoteItems).filter { it.contentAvailable }",
  "The public Shop catalog must hide every unreleased or non-renderable item.");
requireText(catalog, "get() = emptyList()", "The Avatar Shop catalog must be empty.");
for (const [source, name] of [[pawsUi, "Paws"], [boardCatalogUi, "Board"], [diceCatalogUi, "Dice"]]) {
  if (/COMING SOON|UPCOMING PAW PACKS|AVATARS/.test(source)) throw new Error(`${name} Shop still contains retired placeholder or Avatar text.`);
}
if (pawsUi.includes("StoreCosmeticCatalog.avatars") || catalog.includes("character_pack_safari_paws")) {
  throw new Error("Avatar Shop or speculative character packs are still exposed.");
}
for (const obsolete of [
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/components/StoreAvatarCatalogUi.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/components/StoreCosmeticGridUi.kt",
]) {
  if (fs.existsSync(obsolete)) throw new Error(`Obsolete Shop component is still present: ${obsolete}`);
}
const featuredUi = read("android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/components/StoreLockedSpotlightView.kt");
requireText(featuredUi, "item.contentAvailable && !store.cosmetics.isOwned(item.id)", "Spotlights must show available, unowned content only.");
for (const category of ["BOARD", "DICE", "CHARACTER_PACK"]) {
  requireText(featuredUi, `CosmeticCategory.${category}`, `Locked ${category} products need a real preview.`);
  requireText(contentUi, `CosmeticCategory.${category}`, `${category} Store tab needs the shared spotlight.`);
}
requireText(featuredUi, "show(1)", "Locked product spotlights must still advance automatically.");
requireText(featuredUi, "postDelayed(cycle, 7000L)", "Locked product rotation interval must be preserved.");
requireText(actions, "CONTENT_UNAVAILABLE", "Unavailable pack taps must be handled without spending value.");
requireText(actions, "NEED_EVENT", "Event pack UX is missing.");

for (const marker of [
  "starter paws is free content ready and linked to complete character content",
  "only ready animal packs and priced character offers appear",
  "future packs are removed instead of disabled placeholders",
  "event packs require a stable event key",
]) {
  requireText(tests, marker, `Phase 14 catalog coverage is missing: ${marker}`);
}

console.log(
  "Ludo Paws Shop gate passed: released-only Board/Dice/Paws, no avatars/upcoming shelf, legacy entitlement compatibility and purchase-safety guards.",
);
