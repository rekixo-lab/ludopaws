# Ludo Paws Phase 14 — Animal Store & Progression

Phase 14 makes animal packs a first-class cosmetic/store concept without allowing cosmetics or monetization to influence authoritative Ludo gameplay.

## What ships in this phase

- `CosmeticCategory.CHARACTER_PACK` and a dedicated **PAWS** store tab.
- Stable pack-store IDs separate from gameplay character IDs.
- Starter Paws as the free, content-ready default character pack.
- Progression support for free, level, gem, verified rewarded-ad and verified event unlock channels.
- Independent persistence for the selected character-pack cosmetic and verified event entitlements.
- Store-to-character-selection synchronization: equipping a content-ready pack updates the existing `CharacterSelectionStore` while preserving the currently selected character when it already belongs to that pack.
- A fail-closed `contentAvailable` gate. A future pack cannot spend gems, consume ad progress, or become equipped before its complete character-domain content is registered.

## Current pack offers

| Store offer | Unlock path | Content state |
| --- | --- | --- |
| Starter Paws | Free | Ready |
| Safari Paws | Level 8 | Reserved / coming soon |
| Farm Paws | 300 gems | Reserved / coming soon |
| Cozy Paws | 10 verified rewarded ads | Reserved / coming soon |
| Wild Paws | Verified event | Reserved / coming soon |
| Pet Paws | 450 gems | Reserved / coming soon |

Reserved offers intentionally do not create fake characters or charge value. They hold stable progression/store IDs until their full art, reactions, animation and voice content is authored and registered. Once a pack becomes content-ready, its store entry must link to a real `AnimalPack` in `LudoPawsCharacterCatalog` before selection is allowed.

## Pack completeness contract

A content-ready animal pack is not just a skin. It must resolve to an `AnimalPack` whose characters each have:

- valid character/species/personality metadata;
- renderable character artwork or safe fallback artwork;
- a registered voice set;
- a registered animation set;
- the shared game-moment/reaction behavior from Phases 7–10.

The Starter Paws pack already satisfies this contract with Ducky, Nutty, Spike and Woolly.

## Purchase and reward integrity

- Unavailable packs are rejected before any gem debit occurs.
- Rewarded-ad progress is accepted only through the existing verified reward entry point and is not advanced for unavailable pack content.
- Event packs use a separate verified-entitlement store; merely tapping the UI cannot grant an event pack.
- Google Play gem purchases retain the existing backend-verification boundary. This phase does not grant gems or paid entitlements directly from an unverified Play response.
- Free/default ownership remains deterministic and additive with existing board/dice/avatar ownership.

## Gameplay/fairness boundary

This phase does not alter:

- native dice randomness or receipt state;
- dice outcomes or event indexes;
- legal moves, capture, safe-cell, exact-home, extra-turn or third-six rules;
- turn order, Team Up semantics or winner calculation;
- server/Durable Object gameplay persistence;
- Android package identity or internal `ludoproof` compatibility identifiers.

Character-pack ownership and selection are cosmetic presentation state only.

## Verification

`scripts/check-ludo-paws-animal-store.mjs` locks the Phase 14 contract in CI. Unit coverage verifies Starter Paws completeness, all planned unlock channels, event-key requirements and the fail-closed status of future packs. Full Android unit tests, lint, APK/AAB builds, server regression tests and CodeQL must be green before merge.
