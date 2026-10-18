# Ludo Paws Phase 11 — Complete Offline Integration

Phase 11 connects the existing Ludo Paws character/reaction/audio/haptics/FX stack to both local game modes without changing authoritative Ludo state.

## Scope

- Computer mode and Pass & Play use the same committed-feedback path.
- Human and CPU dice rolls play the roll SFX when the visual roll starts.
- Successful committed roll transitions can add six/no-move/third-six reactions and haptics.
- Successful committed moves choose one priority SFX: normal move, safe, capture, home, victory, or defeat.
- Reaction haptics respect the existing `hapticsEnabled` setting.
- Board voice and animation remain driven by `LudoPawsReactionEngine` through `LudoPawsReactiveBoardView`.
- A resumed saved match performs a silent initial bind: historical events are not replayed as new feedback.
- Missing or mismatched presentation-only character preferences are deterministically repaired from Starter Paws and persisted again.
- The repaired character list is cached per match so the board and player rails use the same seat-to-character mapping.

## Resume behavior

Offline gameplay state and character cosmetics intentionally remain separate. If a saved Ludo match survives while the cosmetic preference record is unavailable or incompatible, Phase 11 does not reject or alter the match. Instead it rebuilds a valid Starter Paws assignment for the current mode/player count and continues.

In Computer mode, a resumed CPU turn is still scheduled by the existing `computerActionRevision` guard. If a saved CPU roll is already pending, the CPU continues with the committed legal-token set instead of rolling again.

## Feedback ordering

Feedback is emitted only after a successful `session.roll()` or `session.move()` call. Re-rendering, activity recreation, and initial resume binds do not dispatch committed action feedback.

Priority for move-result SFX is:

1. Victory
2. Defeat
3. Capture / Captured
4. Home
5. Safe
6. Normal move

A six receives a result chime after the normal roll sound. Third-six and no-legal-move events keep their reaction/voice/haptic behavior without inventing an additional game SFX.

## Trust boundary

Phase 11 does **not** modify:

- native SecureRandom dice engine and local receipt;
- legal move calculation;
- CPU token scoring/selection;
- capture or safe-cell rules;
- exact-home logic;
- turn/extra-turn/third-six rules;
- winner calculation;
- native receipt and local audit history;
- backend protocol or Durable Objects;
- offline saved-match schema.

All new behavior is disposable presentation state derived from snapshots that the existing offline engine has already committed.
