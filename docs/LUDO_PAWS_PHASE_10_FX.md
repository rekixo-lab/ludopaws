# Ludo Paws Phase 10 — Animation and FX

Phase 10 turns the Phase 8/9 reaction stream into a richer presentation layer without moving any gameplay authority out of the existing engines.

## Layers

`LudoPawsReactiveBoardView` now composes three visual layers in order:

1. `LudoPawsBoardView` — authoritative board presentation, hit testing, token positions and the existing move animation.
2. `LudoPawsGameFxOverlayView` — paw trails, capture impact/return, safe glow, home stars, angry/nervous/sad particles and victory confetti.
3. `LudoPawsCharacterReactionOverlayView` — a larger equipped-animal reaction near the player's home corner with bounce, shake, wobble, slump, pulse and a lightweight emotion badge.

The Phase 9 voice/SFX/haptic player remains independent of these layers.

## Transition FX

Snapshot-to-snapshot token deltas are read only after the engine has already committed them. The FX overlay recognizes normal movement, home arrival and the important capture reset (`position >= 0 -> -1`). The captured pawn gets a dedicated ghost/paw return-to-yard animation rather than disappearing instantly from the effect layer.

The existing board still owns real token placement and interaction; the transition overlay never writes state.

## Reaction FX

The existing `AnimationCue` contract drives presentation:

- `IDLE` — subtle breathing/blink-style pulse.
- `EXCITED` / `HAPPY` — bounce and burst.
- `CAPTURE` — attack/celebration burst.
- `CAPTURED` — impact plus captured-token return transition.
- `SAFE` — layered cyan shield rings.
- `HOME` — gold celebration and center stars.
- `ANGRY` — comic lightning/shake.
- `NERVOUS` — wobble/orbit effect.
- `SAD` / `DEFEAT` — slump and tear/drop particles.
- `VICTORY` — character celebration plus board confetti.

## Reduced Motion

`LudoPawsFxPolicy` is a pure domain-level policy used by both overlays. With Reduced Motion enabled:

- spatial translation is disabled;
- rotation/shake is disabled;
- dense confetti is disabled;
- particle counts are sharply reduced;
- animations become short fades/highlights (<= 220 ms);
- capture return becomes a destination flash rather than a moving pawn ghost.

Important game feedback remains visible even when motion is minimized.

## Integrity boundary

Phase 10 must not influence:

- dice/randomness or native dice derivation;
- legal moves or CPU choice;
- captures, safe cells, exact-home logic or turn advancement;
- match winner/team winner calculation;
- proof/fairness transcripts;
- backend protocol, Durable Objects or saved-game schema.

All Phase 10 state is disposable presentation state derived from already-authoritative snapshots and Phase 8 reactions.

## Verification

CI runs `scripts/check-ludo-paws-animation-fx.mjs`, Android unit tests (including `LudoPawsFxPolicyTest`), release lint, debug/release builds and Android 16 packaging checks. CodeQL remains required before merge.
