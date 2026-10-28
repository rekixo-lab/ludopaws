/**
 * Authoritative reward plan. Only natural, fully-ranked human finishes earn
 * spendable Gems. Local/CPU, bot-filled and forfeit matches never mint currency.
 * No client-supplied winner, placement or balance is accepted here.
 */
export const GEM_REWARD_TABLE = Object.freeze({
  2: Object.freeze([3, 0]),
  3: Object.freeze([3, 2, 0]),
  4: Object.freeze([3, 2, 1, 0]),
});

export function plannedMatchGemRewards(state) {
  if (state?.status !== "FINISHED" || !Array.isArray(state.players) ||
      state.players.some(p => p.playerType === "BOT" || Number.isSafeInteger(p.forfeitedAt))) {
    return [];
  }
  const count = state.players.length;
  if (state.matchMode === "TEAM_UP") {
    if (count !== 4 || !["A", "B"].includes(state.winnerTeamId)) return [];
    const winners = state.players.filter((p, seat) =>
      (p.teamId ?? state.teamAssignments?.[seat]) === state.winnerTeamId);
    if (winners.length !== 2 || !winners.every(p => p.tokens?.length === 4 && p.tokens.every(t => t === 57))) return [];
    return winners.map(player => ({
      playerId: player.playerId, profileId: player.profileId ?? null,
      friendId: player.friendId ?? null, mode: "TEAM_UP",
      playerCount: 4, rank: 1, amount: 3,
    }));
  }
  if (!["ONLINE", "FRIENDS"].includes(state.matchMode)) return [];
  if (state.matchMode === "ONLINE" && ![2, 4].includes(count)) return [];
  if (!(count in GEM_REWARD_TABLE) || state.rulesetKey !== "CLASSIC_V6") return [];
  const order = state.finishOrderPlayerIds;
  const ids = state.players.map(player => player.playerId);
  if (!Array.isArray(order) || order.length !== count ||
      new Set(order).size !== count || !order.every(id => ids.includes(id)) ||
      order[0] !== state.winnerPlayerId) return [];
  const payout = GEM_REWARD_TABLE[count];
  return order.flatMap((id, index) => payout[index] > 0 ? [{
    playerId: id, profileId: state.players.find(p => p.playerId === id)?.profileId ?? null,
    friendId: state.players.find(p => p.playerId === id)?.friendId ?? null,
    mode: state.matchMode, playerCount: count,
    rank: index + 1, amount: payout[index],
  }] : []);
}
