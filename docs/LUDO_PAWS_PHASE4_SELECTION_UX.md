# Ludo Paws Phase 4 — Starter Pack Selection UX

Phase 4 makes the Phase 3 character domain selectable in local play without changing Ludo rules, offline fairness history, native dice material, or server contracts.

## Starter Paws UX

The local setup screen contains a **CHOOSE YOUR PAW** panel using the four Phase 2 fallback assets and Phase 3 catalog identities:

- Ducky (`duck`)
- Nutty (`squirrel`)
- Spike (`hedgehog`)
- Woolly (`sheep`)

The fallback drawable is always available. Final authored WebP art can replace it in later visual phases without changing character IDs or saved selections.

## Assignment rules

### Computer mode

- Slot 0 is the human player and is the only directly selectable slot.
- The human's selected animal is also the device-wide preferred Starter Paws character.
- CPU slots are assigned the remaining Starter Paws animals deterministically in catalog order.
- Active local players never receive duplicate Starter Paws characters.

### Pass & Play

- Every active local player gets a dedicated P1/P2/P3/P4 selector.
- Character assignments remain unique while four Starter Paws characters are available.
- Selecting an animal that another player already owns swaps the two assignments rather than silently creating a duplicate.
- Changing player count between 2, 3, and 4 preserves valid choices and fills any new slots deterministically.

## Persistence split

`CharacterSelectionStore` remains the device-wide preferred character store introduced in Phase 3.

`OfflineCharacterSetupStore` is a separate versioned presentation store for local setup. It persists:

- per-mode local character assignments;
- the selected Pass & Play character slot;
- the active local match's mode, player count, preferred board color, and character IDs.

The active assignment is saved immediately before `LocalMatchSession.start(...)`. This gives Phase 5 a stable source for board rendering and resume behavior without placing cosmetic character metadata inside the authoritative offline engine state.

Corrupt, stale, duplicate, or unsupported IDs are normalized through `StarterPawsAssignmentPolicy` to safe Starter Paws values.

## Gameplay isolation

Character selection is cosmetic/presentation state only. Phase 4 deliberately does not modify:

- `OfflineGameEngine` start or saved-state schema;
- `LocalMatchSession` gameplay contract;
- dice derivation or native dice inputs;
- legal moves, turn order, capture, safe-cell, home, CPU decisions, or winner logic;
- online match/player schema;
- Cloudflare Durable Objects or proof identifiers.

## Phase 4 exit gate

Phase 4 is complete when all of the following hold:

1. Computer mode lets the human choose one Starter Paws character and assigns unique CPU characters automatically.
2. Pass & Play lets each active player select a unique Starter Paws character, with duplicate requests resolved by swapping.
3. Character choices survive setup recreation/app relaunch through versioned persistence and invalid data repairs safely.
4. Starting a local match saves an active character assignment for the next rendering phase.
5. Player-count changes stay valid for 2–4 players.
6. Character selection unit tests, static selection-UX gate, Android tests, lint, APK/AAB builds, server CI, and CodeQL are green before merge.
