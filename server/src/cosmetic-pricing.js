export const PAID_CHARACTER_IDS = Object.freeze([
  "character_cock", "character_pig", "character_elephant", "character_horse", "character_rabbit",
]);

export const PAID_BOARD_IDS = Object.freeze([
  "board_astral", "board_cloud_lagoon", "board_autumn_forest", "board_blossom_garden",
  "board_winter_haven", "board_monsoon_mire",
]);

export const COSMETIC_PRICING = Object.freeze(Object.fromEntries(
  [...PAID_CHARACTER_IDS, ...PAID_BOARD_IDS].map((id) => [id, Object.freeze({
    kind: "GEMS", gemPrice: 300, level: 0, ads: id === "board_astral" ? 15 : 0,
    channels: Object.freeze(id === "board_astral"
      ? ["GEMS", "PLAY_PRODUCT", "REWARDED_ADS"] : ["GEMS", "PLAY_PRODUCT"]),
  })]),
));
