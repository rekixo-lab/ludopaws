# Ludo Paws character domain

Phase 3 introduces the character identity layer used by later selection, board rendering, reactions, audio, animation, online cosmetics and store phases.

## Core rule

An animal character is **not** a Ludo color. Character identity and player color remain separate concerns.

A player can therefore be assigned a red/green/yellow/blue gameplay color independently from the equipped animal. Later rendering phases should communicate the player color through the pawn base/ring/glow rather than recoloring the animal itself.

## Domain model

The character layer defines:

- `AnimalSpecies` — the planned Ludo Paws species roster, including the Starter Paws species and the remaining animals already planned for later packs.
- `AnimalPersonality` — presentation personality only. It may influence reactions/animation/audio selection later, but it must never affect dice, legal moves, CPU strength, captures or winner logic.
- `VoiceCue` and `VoiceSet` — stable identifiers for later species/personality voice playback.
- `AnimationCue` and `AnimationSet` — stable identifiers for later reaction animation playback.
- `AnimalCharacter` — character identity, display name, species, personality, voice set, animation set and fallback art identity.
- `AnimalPack` — a stable ordered group of characters.
- `CharacterSelection` — persisted pack + character choice.

## Starter Paws

The first pack is intentionally small so the complete gameplay loop can be polished before adding the rest of the roster:

| Character ID | Display name | Species | Personality |
| --- | --- | --- | --- |
| `duck` | Ducky | DUCK | CHEERFUL |
| `squirrel` | Nutty | SQUIRREL | MISCHIEVOUS |
| `hedgehog` | Spike | HEDGEHOG | SHY |
| `sheep` | Woolly | SHEEP | GENTLE |

Default selection is `starter_paws / duck`.

## Catalog safety

`LudoPawsCharacterCatalog` validates its own references at initialization:

- unique character/pack/voice/animation IDs;
- exactly one starter pack;
- valid pack -> character references;
- valid character -> voice/animation references;
- voice-set species must match character species;
- default selection must remain valid.

The Phase 2 asset manifest is enriched with matching character-domain metadata. CI runs `scripts/check-ludo-paws-character-domain.mjs` so the machine-readable asset catalog cannot silently drift from the Phase 3 character contract.

## Persistence

`CharacterSelectionStore` uses a new, dedicated namespace:

`ludo_paws_character_selection_v1`

This intentionally does **not** rename or reuse legacy gameplay/proof/session storage. Stored pack and character IDs are validated through the catalog every time they are loaded. Unknown/corrupt combinations and unsupported schema versions repair to the safe default selection instead of propagating invalid state.

## Compatibility boundary

Phase 3 does not change:

- Android `applicationId` / package identity;
- Cloudflare Worker or Durable Object bindings;
- match schemas;
- online player state;
- native dice receipt/fairness-history/ruleset identifiers;
- dice or legal gameplay behavior;
- CPU move scoring;
- store ownership logic;
- character selection UI.

Those remain owned by their later roadmap phases.
