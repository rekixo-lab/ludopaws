# Ludo Paws Phase 7 — Game moments + player-panel gap closure

Phase 7 completes the shared game-moment detection layer while also closing roadmap gaps discovered during review.

## Roadmap reconciliation

The original migration plan assigns Phase 6 to player character panels and Phase 7 to game-moment detection. PR #91 delivered the first reaction/voice scaffold earlier than that plan, but the existing offline rails were still generic P/CPU badges. To keep the promise that no planned item is skipped, this phase also finishes the missing character-first player rail work before moving to Phase 8.

A later production audit found two Phase 7 behaviors that existed only partially: low rolls were detected but repeated poor-roll frustration was not derived, and the deterministic idle-moment API was not connected to a live presentation timer. The hardening follow-up closes both gaps without introducing any gameplay timer or authority.

## Delivered in this phase

- Pure `LudoPawsGameMomentDetector` shared-contract layer over `MatchSnapshot` diffs.
- Moment types for turn start, roll start, six, low roll, repeated poor-roll streak, yard exit, normal move, capture made, captured victim, safe, home, no legal move, third-six forfeit, meaningful lead change, idle, solo win/loss, and team win/loss.
- Three consecutive outcomes of 1–2 for the same player now derive a presentation-only `POOR_ROLL_STREAK` moment even when other players' turns are interleaved.
- `POOR_ROLL_STREAK` maps to the existing controlled frustration voice/animation path; the reaction director's frustration cooldown remains the playback rate limiter.
- Explicit idle API remains caller-clocked and deterministic. `LudoPawsReactiveBoardView` now schedules it after 12 seconds of unchanged ACTIVE presentation state.
- Duplicate realtime refreshes do not restart the idle clock because scheduling is keyed from stable authoritative-visible state; actual roll, move, turn, pending-roll or result changes do restart it.
- Team Up idle/turn presentation resolves `actingSeat` before `turnSeat`, so the partner who can actually act owns the idle reaction.
- Existing Phase 6 reaction engine consumes the shared moment detector instead of maintaining a second copy of snapshot-diff rules.
- Character-first offline player cards showing selected animal art, player name, turn/team state, and HOME/RACING/YARD progress.
- Phone-safe card/dice sizing for 2/3/4-player rails.
- Unit regression coverage and a dedicated CI gate for poor-roll and idle scheduling behavior.

## Safety boundary

This phase does not modify `OfflineGameEngine`, native dice engine, legal moves, dice derivation, capture rules, safe cells, exact-home rules, winner logic, proof/fairness history, server protocol, Durable Objects, persistence schema, or authoritative turn timers.

Idle scheduling uses only the Android presentation clock. Poor-roll streak detection only reads already-committed history. Neither can affect dice outcomes, CPU decisions, legal moves, network authority, or proof material.

Phase 8 remains responsible for stable reaction keys, reconnect/revision deduplication, cooldowns, queueing, interruption, and deterministic priority/order. The new poor-roll reaction intentionally reuses that existing director rather than creating a second playback authority.
