import { httpError } from "./crypto.js";

export const DAY_MS = 86_400_000;
export const DAILY_GEMS = 3;
export const SPIN_OUTCOMES = Object.freeze([
  Object.freeze({ gems: 1, percent: 50 }), Object.freeze({ gems: 2, percent: 30 }),
  Object.freeze({ gems: 3, percent: 15 }), Object.freeze({ gems: 5, percent: 5 }),
]);
// No new Play products or changed purchased quantities. Existing renderers only.
export const COLLECTIONS = Object.freeze([
  Object.freeze({ id: "dawn-adventures", title: "Dawn Adventures", items: ["character_horse", "board_dawn_meadow", "dice_pearl_paw"], price: 550, fullPrice: 650 }),
  Object.freeze({ id: "moonlit-friends", title: "Moonlit Friends", items: ["character_cock", "board_moonlit_grove", "dice_pearl_paw"], price: 550, fullPrice: 650 }),
  Object.freeze({ id: "garden-friends", title: "Garden Friends", items: ["character_rabbit", "board_blossom_garden", "dice_pearl_paw"], price: 600, fullPrice: 700 }),
  Object.freeze({ id: "starlight-pals", title: "Starlight Pals", items: ["character_elephant", "board_astral", "dice_pearl_paw"], price: 600, fullPrice: 700 }),
  Object.freeze({ id: "rainy-day", title: "Rainy Day Pals", items: ["character_pig", "board_monsoon_mire", "dice_pearl_paw"], price: 600, fullPrice: 700 }),
]);
export function rewardDay(now = Date.now()) { return Math.floor(now / DAY_MS); }
export function spinReward(random = crypto.getRandomValues.bind(crypto)) {
  // Rejection sampling prevents modulo bias; result is never chosen by the client wheel.
  const sample = new Uint32Array(1);
  do { random(sample); } while (sample[0] >= 4_294_967_200);
  const roll = sample[0] % 100;
  let bound = 0;
  return SPIN_OUTCOMES.find(item => roll < (bound += item.percent)).gems;
}
export function collectionQuote(collection, owned, prices) {
  if (collection.items.some(id => !Number.isSafeInteger(prices[id]) || prices[id] < 0))
    throw httpError(503, "COLLECTION_PRICE_UNAVAILABLE", "Collection prices are temporarily unavailable.");
  const missing = collection.items.filter(id => !owned.includes(id));
  const missingValue = missing.reduce((sum, id) => sum + prices[id], 0);
  return { missing, price: Math.ceil(missingValue * collection.price / collection.fullPrice) };
}
export function createRewardsSchema(sql) {
  sql.exec("CREATE TABLE IF NOT EXISTS engagement_claims (day INTEGER NOT NULL, action TEXT NOT NULL, amount INTEGER NOT NULL, request_id TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL, PRIMARY KEY(day, action))");
  sql.exec("CREATE TABLE IF NOT EXISTS engagement_summary (id INTEGER PRIMARY KEY CHECK(id = 1), daily_claims INTEGER NOT NULL DEFAULT 0)");
  sql.exec("CREATE TABLE IF NOT EXISTS engagement_ad_tickets (day INTEGER PRIMARY KEY, ticket TEXT NOT NULL UNIQUE, expires_at INTEGER NOT NULL)");
  sql.exec("INSERT OR IGNORE INTO engagement_summary (id, daily_claims) VALUES (1, 0)");
}
export function rewardsStatus(sql, now = Date.now()) {
  const day = rewardDay(now);
  const claims = [...sql.exec("SELECT action, amount FROM engagement_claims WHERE day = ?", day)];
  const total = Number([...sql.exec("SELECT daily_claims FROM engagement_summary WHERE id = 1")][0]?.daily_claims ?? 0);
  return { day, resetsAt: (day + 1) * DAY_MS, dailyGems: DAILY_GEMS,
    dailyClaimed: claims.some(row => row.action === "daily"), spinClaimed: claims.some(row => row.action === "spin"),
    spinGems: Number(claims.find(row => row.action === "spin")?.amount ?? 0),
    bonusClaimed: claims.some(row => row.action === "bonus"),
    dailyClaims: total, nextMilestone: (Math.floor(total / 7) + 1) * 7,
    milestoneTier: Math.floor(total / 7), spinOutcomes: SPIN_OUTCOMES,
    featuredCollectionId: COLLECTIONS[new Date(now).getUTCMonth() % COLLECTIONS.length].id,
    maxFreeDailyGems: DAILY_GEMS + 5, expectedFreeDailyGems: 4.8,
  };
}
export function claimReward({ sql, transaction, accountId, body, credit, now = Date.now() }) {
  if (!["daily", "spin"].includes(body.action)) throw httpError(400, "REWARD_ACTION_INVALID", "Choose a daily gift or free spin.");
  const day = rewardDay(now);
  const requestId = String(body.clientRequestId ?? "");
  if (!/^[a-zA-Z0-9-]{16,100}$/.test(requestId)) throw httpError(400, "REWARD_REQUEST_INVALID", "Reward request identity is invalid.");
  let receipt;
  transaction(() => {
    const replay = [...sql.exec("SELECT day, action, amount, request_id FROM engagement_claims WHERE request_id = ?", requestId)][0];
    if (replay) {
      if (replay.action !== body.action || Number(replay.day) !== Number(body.day)) throw httpError(409, "REWARD_REQUEST_REUSED", "Reward request belongs to another claim.");
      receipt = { ...replay, replay: true }; return;
    }
    const prior = [...sql.exec("SELECT day, action, amount, request_id FROM engagement_claims WHERE day = ? AND action = ?", day, body.action)][0];
    if (Number(body.day) !== day) throw httpError(409, "REWARD_DAY_CHANGED", "A new reward day started. Refresh Rewards.");
    if (prior) { receipt = { ...prior, replay: true }; return; }
    const amount = body.action === "daily" ? DAILY_GEMS : spinReward();
    credit({ amount, key: `engagement:${day}:${body.action}`, kind: body.action === "daily" ? "DAILY_GIFT" : "FREE_SPIN", accountId });
    sql.exec("INSERT INTO engagement_claims (day, action, amount, request_id, created_at) VALUES (?, ?, ?, ?, ?)", day, body.action, amount, requestId, now);
    if (body.action === "daily") sql.exec("UPDATE engagement_summary SET daily_claims = daily_claims + 1 WHERE id = 1");
    receipt = { day, action: body.action, amount, request_id: requestId, replay: false };
  });
  return receipt;
}
