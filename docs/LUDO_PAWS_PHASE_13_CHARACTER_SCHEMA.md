# Ludo Paws Phase 13 — Online Character Identity Schema

Phase 12 introduced the remote `characterId` plumbing because online character rendering could not work without it. Phase 13 freezes and hardens that cosmetic wire contract so the migration roadmap remains complete without duplicating or destabilizing the already-green online integration.

## Stable wire contract

- Wire field: `characterId`
- Character identity schema version: `1`
- Current allowlisted IDs: `duck`, `squirrel`, `hedgehog`, `sheep`
- Legacy/default identity: `duck`

The server owns request validation. Missing or blank identity from an older client resolves to `duck`. A new request that supplies an unsupported ID fails with `INVALID_CHARACTER_ID` instead of silently persisting unknown data.

Persisted legacy room state is handled more leniently: stale or missing stored values are rendered as `duck` so old matches remain readable after deployment.

## Android compatibility

`LudoPawsCharacterIdentityContract` is the Android-side source of truth for the stable wire name, schema version and display fallback. `characterId` remains optional in `PlayerSnapshot`, so state from an older server is still parseable. Remote presentation resolves missing, blank, mixed-case or unknown values through one deterministic policy before selecting artwork.

## Fairness boundary

Character metadata remains presentation-only. It is excluded from `authoritativeStateForRandomness()` and therefore cannot change:

- native dice commitments, receipt digests or fairness-history records;
- dice outcomes or event indexes;
- legal token indexes or move validation;
- captures, safe cells, exact-home, extra turns or third-six behavior;
- turn order, Team Up handoff or winner calculation.

The Android package ID, `ludoproof` protocol/ruleset identifiers, Cloudflare bindings, Durable Object classes/migrations and persisted gameplay schema are unchanged.

## Phase 13 compatibility tests

The phase locks the following cases:

1. Android and server agree on the `characterId` wire field and schema version 1.
2. Old create/join requests that omit character identity still produce a playable room using `duck`.
3. Legacy persisted character values never make a room unreadable.
4. Unsupported new-client character IDs are rejected at the request boundary.
5. Android rendering safely falls back for missing, blank, mixed-case and unknown remote identity.
6. Changing a character leaves authoritative proof/randomness state byte-for-byte equivalent at the game-model level.

`scripts/check-ludo-paws-character-schema.mjs` is the Phase 13 static CI gate. Full server tests, Android unit tests, lint/build/bundle gates and CodeQL must remain green before merge.
