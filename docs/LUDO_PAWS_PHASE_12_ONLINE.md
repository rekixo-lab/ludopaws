# Ludo Paws Phase 12 — Online Character Integration

Phase 12 carries the selected Starter Paws animal through every remote match path while keeping character identity outside authoritative Ludo and native dice receipt and fairness-history state.

## Covered remote modes

- Online direct create/join
- Public 2-player and 4-player matchmaking
- Friends private rooms and accepted friend joins
- Team Up 2 vs 2 matchmaking

The Android app primes the persisted character selection at process startup. Remote API clients then attach the selected `characterId` to create/join/search requests without requiring each screen to duplicate character-selection logic.

## Server contract

The server accepts only the current Starter Paws IDs: `duck`, `squirrel`, `hedgehog`, and `sheep`. Missing character identity defaults to `duck`, which keeps older installed clients and previously queued sessions compatible. Unsupported request values fail with `INVALID_CHARACTER_ID`.

MatchRoom persists the cosmetic character beside each player. Public state returns `characterId`, allowing every connected client to render the same animal for every seat. Public and Team Up matchmaking preserve the value through queue, assignment, status, deterministic room materialization, and idempotent create/join replay.

## Fairness boundary

Character identity is deliberately excluded from `authoritativeStateForRandomness`. It cannot change:

- native roll receipt or proof digest
- dice outcomes
- legal token indexes
- CPU or player move decisions
- safe-cell and capture rules
- extra turns or third-six forfeits
- turn order or Team Up handoff
- exact-home or winner calculation

Changing a cosmetic animal therefore cannot change the mathematical game transcript.

## Android presentation

Remote `PlayerSnapshot` now carries `characterId`. `OnlineLudoPawsCharacterPolicy` validates server values against the local catalog and falls back to the Starter Paws default for old or malformed persisted room data.

The existing remote activity keeps its legacy board synchronized as a fallback, while `OnlineLudoPawsPresentation` owns the visible character layer:

- `LudoPawsReactiveBoardView` renders animal pawns and existing reaction FX.
- A multiplayer character rail renders `LudoPawsPlayerCardView` for every seat.
- Local perspective is retained for the board.
- Team Up uses `actingSeat` for the visible active character.
- Initial/resumed state binding is silent.
- Roll/move/reaction feedback uses stable per-match keys so realtime refresh or reconnect cannot replay the same SFX/haptic event.
- Presentation host state is owned by the Activity view hierarchy instead of a process-global Activity map.

## Compatibility and recovery

Old clients that omit `characterId` receive `duck`. Old stored match state without character metadata remains readable because public rendering applies the same fallback. Invalid remote sessions clear both the legacy board and the Ludo Paws presentation layer before returning to the lobby or closing the mode screen.

## Verification

Phase 12 adds:

- server unit coverage for validation, legacy fallback, public-state exposure, and proof-state isolation;
- Android unit coverage for seat-to-character resolution and fallback behavior;
- `scripts/check-ludo-paws-online-integration.mjs` as a CI contract gate.

The phase is mergeable only after server tests, Android unit tests, release lint/build/bundle gates, Ludo Paws gates, and CodeQL are green.
