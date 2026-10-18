# Ludo Paws Phase 8 — Reaction Director

Phase 8 adds a presentation-only reaction director between the pure game-moment/reaction derivation layer and visual/voice playback.

## Stable reaction identity

Every derived reaction carries a stable key built from:

`matchId + eventIndex + momentType + playerId + tokenIndex`

The director remembers consumed keys with a bounded cache so a realtime re-render, reconnect replay, or repeated snapshot cannot replay the same reaction indefinitely.

## Cooldowns

Low-value/noisy reactions are throttled by player and cue. Idle, nervous, frustrated, six and safe reactions have presentation cooldowns. Capture, captured, home, third-six, victory and defeat are not dropped by cooldown policy.

## Queue and interruption policy

Reactions from the same authoritative event are grouped into one playback batch. Batches are ordered deterministically by priority and event index. A lower-priority batch waits while the current playback window is active. Capture-class reactions can interrupt lower-priority playback, and victory-class reactions always win priority.

The queue and consumed-key stores are bounded to prevent unbounded memory growth.

## Capture pairing

When one move produces both `CAPTURE_MADE` and `TOKEN_CAPTURED`, the reaction adapter aligns the captured reaction to the attacker's authoritative move event. This keeps the attacker and victim reactions in the same playback batch even though the victim token itself has no move-history row.

## Safety boundary

The director never rolls dice, changes legal moves, chooses CPU moves, changes turn order, modifies proof material, writes match state, changes winners, or touches native dice derivation. It consumes presentation reactions only.

## Exit gate

Phase 8 is complete only when:

- stable-key deduplication is tested;
- cooldown suppression is tested;
- queue drain ordering is tested;
- capture-class interruption is tested;
- same-event win/loss batching is tested;
- the Phase 8 CI gate passes;
- Android unit tests/lint/build gates pass;
- CodeQL is green.
