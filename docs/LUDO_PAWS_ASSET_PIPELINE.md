# Ludo Paws animal asset pipeline

This document is the production contract for all Ludo Paws character art and character audio. It intentionally defines asset handling before the character-domain and gameplay phases so later features can consume stable names without hard-coded ad-hoc paths.

## Scope

Phase 2 owns asset conventions, validation, fallback art, lazy decoding and memory budgets. It does **not** change Ludo rules, proof bindings, online schemas, character selection, store ownership or reaction behavior.

The first logical pack is `starter_paws` with four starter characters:

- `duck`
- `squirrel`
- `hedgehog`
- `sheep`

The Phase 2 fallback vectors keep the pack renderable before final authored WebP art is committed. Phase 4 replaces/fills the production art slots with the polished starter artwork. Phase 9 fills the production voice slots with approved OGG clips.

## Stable IDs and paths

IDs use lowercase ASCII snake case only:

```text
[a-z][a-z0-9_]{1,31}
```

Runtime production assets live under the Android `assets` namespace:

```text
ludo_paws/characters/<character_id>/pawn.webp
ludo_paws/characters/<character_id>/portrait.webp
ludo_paws/characters/<character_id>/full_body.webp
ludo_paws/characters/<character_id>/expressions/<expression>.webp
ludo_paws/audio/<character_id>/<cue>_<variant>.ogg
```

The catalog is:

```text
android/app/src/main/assets/ludo_paws/catalog.json
```

Do not encode Ludo player color into animal artwork. Player/team color is a presentation layer applied around the natural animal art.

## Visual slots

Every production-ready character must have:

| Slot | Target canvas | Purpose | Max compressed size |
| --- | ---: | --- | ---: |
| pawn | 256×256 | track/yard token face | 120 KiB |
| portrait | 512×512 | player cards/reactions | 260 KiB |
| full_body | 768×768 | store/result/selection | 480 KiB |
| optional expression | 512×512 | expression override | 260 KiB |

Requirements:

- transparent background;
- square canvas;
- subject centered with safe padding;
- no text, border, baked player-color ring or background panel;
- natural animal colors preserved;
- WebP preferred for production raster art;
- no upscaling at runtime.

Expression images are optional because Phase 10 can use transforms/overlays for blink, bounce, shake, squash/stretch, glow and similar reactions. If expression overrides are supplied, use stable names such as `happy`, `sad`, `angry`, `nervous`, `excited`, `victory`, `defeat`.

## Audio slots

Short animal voices/SFX use OGG and stable cue names. Recommended cues are:

```text
six
capture
captured
safe
home
frustrated
third_six
idle
nervous
victory
defeat
```

Naming example:

```text
ludo_paws/audio/lion/capture_01.ogg
ludo_paws/audio/lion/capture_02.ogg
```

Limits per clip:

- OGG container;
- <= 2.5 seconds for normal reactions;
- <= 180 KiB compressed;
- normalized without clipping;
- no copyrighted music/dialogue sampled from third parties;
- silence at start/end trimmed so SoundPool playback feels immediate.

Music remains managed by the existing lifecycle-aware music controller and is not part of the character pack.

## Memory policy

Do not preload every pack.

At match time load only:

1. the characters visible in the current match;
2. the visual slot currently needed by the screen;
3. voice clips needed by the active character/reaction set.

Decoded-memory limits:

- default single bitmap decode guard: 3 MiB;
- target active character-art working set: <= 12 MiB;
- release references when the owning screen/game session closes;
- never retain full-body art for normal board play;
- prefer pawn art on the board and portrait art in character panels.

`LudoPawsBitmapLoader` performs bounds-first decoding and power-of-two downsampling. Missing or invalid production raster art must fall back to the catalog's Android drawable instead of crashing the match.

## Catalog states

Each character declares independent production readiness for visuals and audio. Phase 2 ships safe fallbacks and reserves production paths. A later phase flips readiness only when the referenced files exist and pass the validator.

This prevents temporary/missing art from becoming a release crash while still keeping final file names stable.

## Validation

Run:

```bash
node scripts/check-ludo-paws-assets.mjs
```

The gate checks:

- catalog JSON schema/version;
- unique pack and character IDs;
- exact production path conventions;
- fallback drawable existence;
- allowed expression/cue names;
- production file existence once readiness is true;
- WebP/OGG extensions and compressed-size limits;
- path traversal/unsafe names;
- active-pack memory budget values.

The CI Android job runs this gate before unit tests/builds.

## Adding a new character

1. Choose a permanent lowercase ID.
2. Add its catalog entry with a fallback drawable.
3. Export transparent WebP pawn, portrait and full-body art to the exact reserved paths.
4. Add only required expression overrides.
5. Add approved OGG voice clips using the cue/variant naming contract.
6. Mark the relevant readiness flag true.
7. Run the asset validator and Android tests.
8. Review on a small phone and a low-memory device before release.

## Integrity boundary

Asset selection, decoding, animation and audio are presentation-only. They must never feed native dice inputs, receipt digests, dice outcomes, legal-move logic, CPU move authority or winner calculation.