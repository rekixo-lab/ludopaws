# Ludo Paws Phase 15 — Social, Fair Dice UX, accessibility and hardening

## Scope

Phase 15 closes the current Ludo Paws migration roadmap without changing game authority, randomness, legal moves, proof algorithms, package identity, deployed Worker identity, or persisted gameplay contracts.

## Consumer-facing Fair Dice UX

The home quick action now says **FAIR DICE** instead of exposing the internal **PROOFS** label first.

The Fair Dice dialog is intentionally layered:

1. a plain-language explanation that the UI cannot simply choose the displayed dice outcome;
2. a short online explanation;
3. a short offline explanation;
4. advanced audit vocabulary for players who want native dice-engine, rejection-sampling and receipt-context details.

The detailed proof information remains available. Only the order and consumer-facing terminology changed.

## Social character identity

Profile now includes a dedicated **YOUR PAW** panel driven by the real `CharacterSelectionStore` and `LudoPawsCharacterCatalog`.

The panel shows:

- the equipped animal portrait;
- character display name;
- pack name;
- species and personality;
- an explicit statement that personality is presentation-only;
- a direct **OPEN PAWS STORE** action.

When returning from Store, Profile re-renders on `onRestart()` so a newly equipped character is reflected immediately.

## Paws Store deep link

`StoreActivity` accepts a validated `EXTRA_INITIAL_TAB` value and resolves it only against `StoreTab.entries`.

Profile uses that contract to open `StoreTab.PAWS` directly. Unknown or missing values fall back to `GEMS`.

## Accessibility

The equipped character portrait has a meaningful content description including the selected character, species and personality. The Paws Store button has an explicit accessibility label and a 50dp touch target.

Existing gameplay character cards already expose state-rich content descriptions. Existing settings continue to provide animal voices, haptics and reduced-motion controls.

## Compatibility boundary

Phase 15 deliberately preserves:

- Android `applicationId = "com.rekixo.ludopaws"`;
- Cloudflare Worker name `ludoproof-game-api`;
- existing `LUDOPROOF_*` bindings and Durable Object migrations;
- current native dice receipt/fairness-history identifiers and native derivation;
- server-authoritative dice, legal move, capture, safe-cell, exact-home, turn, reconnect and winner behavior;
- existing local/remote match compatibility.

The new character/profile presentation is cosmetic and cannot influence dice or legal gameplay decisions.

## Verification

Phase 15 adds:

- `FairDiceExplainerTest` for friendly-vs-advanced proof copy ordering and terminology;
- `scripts/check-ludo-paws-phase15.mjs` for Fair Dice UX, profile character identity, accessibility hooks, Paws Store deep-link validation, and compatibility-sensitive identifier preservation;
- the Phase 15 gate to the Android CI job before release lint/build artifacts.

The normal server tests, Android unit tests, release lint, debug/release APK builds, AAB build, CodeQL and release evidence jobs remain the merge gate.
