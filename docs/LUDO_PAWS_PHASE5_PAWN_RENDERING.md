# Ludo Paws Phase 5 — Animal Pawn Rendering

## Goal

Render the selected Starter Paws characters on the local Ludo board without changing authoritative gameplay, native dice randomness, receipt material, saved-match schema, dice outcomes, legal moves, captures, or winners.

## Runtime contract

- `LudoBoardView` remains the proven board/touch surface.
- `LudoPawsBoardView` wraps that surface and adds a presentation-only animal overlay.
- Phase 4 `OfflineCharacterSetupStore.loadActive()` is the only source for local cosmetic assignments.
- Character ids are mapped by player seat and resolved through `LudoPawsCharacterCatalog`.
- Missing, stale, or unresolved character art falls back safely to the existing generic pawn already drawn by `LudoBoardView`.
- Online gameplay is unchanged in this phase; remote character identity remains a later server-contract phase.

## Visual behavior

Starter characters Ducky, Nutty, Spike and Woolly use their existing fallback vector art. Each animal is rendered inside a strong player-color ring so character identity and Ludo color remain independently readable.

Legal local tokens receive a gold/white halo. The animal overlay follows the same board perspective and move-step timing as the base pawn animation. Yard pawns render slightly larger than track pawns. Shared cells reduce animal radius deterministically while preserving the existing four-slot token offsets so stacked pieces remain readable and base-board touch hit-testing stays aligned.

## Safety and compatibility

Phase 5 does not add character fields to `MatchSnapshot`, `PlayerSnapshot`, `OfflineGameEngine`, or proof/history payloads. Cosmetic rendering can therefore fail independently without affecting match validity. If no valid active Phase 4 assignment exists for the current mode/player count, the overlay draws nothing and the original generic pawns remain visible.

## Verification

CI runs `scripts/check-ludo-paws-pawn-rendering.mjs`, Android unit tests including `LudoPawsPawnLayoutTest`, release lint, debug/release APK assembly, AAB assembly, Android 16 target verification, and CodeQL.
