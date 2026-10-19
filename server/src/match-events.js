/** Public presentation needs only the last roll, without internal request receipts. */
export function compactRollHistory(state) {
  const event = state.history?.at(-1);
  const lastRoll = event ? {
    eventIndex: event.eventIndex,
    eventId: event.eventId,
    playerId: event.playerId,
    outcome: event.outcome ?? null,
    randomOutcome: event.randomOutcome ?? null,
    effectiveOutcome: event.effectiveOutcome ?? event.outcome ?? null,
    openingRollApplied: Boolean(event.openingRollApplied),
    proofDigest: event.proofDigest ?? null,
    moveTokenIndex: event.moveTokenIndex ?? null,
    captures: event.captures ?? 0,
    extraTurn: Boolean(event.extraTurn),
    status: event.status ?? "RESOLVED",
  } : null;
  return { history: lastRoll ? [lastRoll] : [], lastRoll };
}

// Internal events support legal-move replay and lifecycle bookkeeping. Online
// gameplay no longer constructs the removed external fairness proof chain.
export function recordedRollEvent(_history, event) {
  return { ...event };
}
