// Keep old paid Paws separate from newly-priced Panda/Monkey for safe migrations.
export const LEGACY_PAID_CHARACTER_IDS = Object.freeze([
  "character_cock", "character_pig", "character_elephant", "character_horse", "character_rabbit",
]);
export const INTRODUCTORY_CHARACTER_IDS = Object.freeze(["character_panda", "character_monkey"]);
export const PAID_CHARACTER_IDS = Object.freeze([
  ...INTRODUCTORY_CHARACTER_IDS, ...LEGACY_PAID_CHARACTER_IDS,
]);

export const PAID_BOARD_IDS = Object.freeze([
  "board_astral", "board_cloud_lagoon", "board_autumn_forest", "board_blossom_garden",
  "board_winter_haven", "board_monsoon_mire",
]);

export const COSMETIC_PRICING = Object.freeze(Object.fromEntries(
  [...PAID_CHARACTER_IDS, ...PAID_BOARD_IDS].map((id) => [id, Object.freeze({
    kind: "GEMS", gemPrice: INTRODUCTORY_CHARACTER_IDS.includes(id) ? 150 : 300,
    level: 0, ads: id === "board_astral" ? 15 : 0,
    channels: Object.freeze(id === "board_astral"
      ? ["GEMS", "PLAY_PRODUCT", "REWARDED_ADS"] : ["GEMS", "PLAY_PRODUCT"]),
  })]),
));

export const COSMETIC_UNLOCK_PRICING = Object.freeze({
  ...COSMETIC_PRICING,
  // Existing ad-progress offers remain valid; new items use Gems earned from ads.
  dice_pearl_paw: Object.freeze({
    kind: "GEMS", gemPrice: 100, level: 0, ads: 20,
    channels: Object.freeze(["GEMS", "REWARDED_ADS"]),
  }),
});
