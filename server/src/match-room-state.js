// Match state projection and public roll receipt formatting.
import { sha256Hex, canonicalJson } from "./crypto.js";
import { rulesetForState, publicState } from "./game.js";
import { compactRollHistory } from "./match-events.js";

export async function rulesetHashForState(state) {
  return sha256Hex(
    "ludopaws:game-ruleset:v1:" +
      canonicalJson(
        rulesetForState(state),
      ),
  );
}

export function rollResponse(state, receipt, replayed) {
  const pending = state.pendingRoll;
  const awaitingMove = pending?.status === "RESOLVED" &&
    pending.eventIndex === receipt.eventIndex && pending.playerId === receipt.playerId;
  return {
    ...receipt,
    replayed,
    awaitingMove,
    legalTokenIndexes: awaitingMove ? [...pending.legalTokenIndexes] : [],
    state: publicStateWithHistory(state),
  };
}

export function publicStateWithHistory(state) {
  return { ...publicState(state), ...compactRollHistory(state) };
}
