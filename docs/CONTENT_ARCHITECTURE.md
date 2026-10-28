# Adding characters, boards and dice

Content lives under `android/app/src/main/java/com/rekixo/ludopaws/feature/`.
Activities host screens. Adding an animal or cosmetic does not require a new
Activity, GL context, game board implementation or dice outcome generator.

## Characters

Each animal has its own `characters/content/<id>/` directory:

- `<Animal>Definition.kt`: stable identity, explicit pack membership, voice and
  animation metadata, store display order, durations and model baselines.
- `<Animal>Renderer.kt`: 3D anatomy and mapping from its timeline to `CharacterPose`.
- `<Animal>3DMotion.kt`: pure idle, hop and home timeline.
- `<Animal>ReactionMotion.kt`: species-specific reaction profile.
- `<Animal>Materials.kt`: coat, eye and material colors.
- `<Animal>AvatarRenderer.kt`: synchronous Canvas fallback for loading/unavailable portraits.

Register the stable ID, definition, renderer, avatar and audio profile together in
`content/content-manifest.json`; run
`node scripts/generate-content-registries.mjs --write`. This generates the
character definition and renderer entries along with audio binding. CI runs
`--check`, validates declarations and fails on stale generated files. The
registry checks that every definition has exactly one renderer entry.
Gameplay, portrait generation and avatar fallback resolve the same character ID.

Every definition has an explicit `packId`. Register new packs in
`CharacterPackDefinitions.kt`, with descriptors under `characters/content/packs/`.
The catalog builds membership from definitions. Paid animals must use their paid
pack ID, not `starter_paws`. Registering a renderer does not grant ownership or
make an unavailable store offer available. Existing purchases are pack-based;
individual character purchases are a separate feature, not enabled here.

## Boards

Each theme has `boards/content/<id>/<Name>BoardDefinition.kt`, containing its
existing offer and visual palette. Register its definition and renderer key in
`content/content-manifest.json` and run the generator.
Store offers and preview palettes derive from these definitions. Existing draft
boards remain unavailable.

Palette themes reuse `boards/content/classic/ClassicBoardRenderer.kt`. For new
chrome, implement `boards/rendering/BoardRenderer`, register the factory
under `boardRenderers` in the manifest, and set the definition's `rendererKey`. Instances
are created per view so mutable Paint and texture caches are not shared.

`LudoBoardView` still owns the logical route, touch mapping and base cells.
`LudoPawsBoardChromeView` hosts the visual renderer, then draws footprints and
finish ranks above it. Cosmetics must not change safe cells, token positions,
hit testing or game rules. Different logical geometry is a gameplay feature.

## Dice

Each style has `dice/content/<id>/<Name>DiceDefinition.kt`, containing its offer
and palette factory. Register it under `dice` in the content manifest, then
run the generator. Each factory
returns separate color arrays; the team border override remains supported.

Palette skins reuse `dice/content/classic/ClassicDiceRenderer.kt`. For different
art, implement `dice/rendering/DiceRenderer`, add its factory to
`diceRenderers` in the manifest, and reference its key from the definition.
`DiceView` owns input, roll/settle timing, countdown and transforms. Renderers
receive a resolved face and only draw it; they cannot choose roll outcomes.

## Shared rendering and compatibility

`LudoPaws3DSceneRenderer` coordinates pawn positions, facing, shadows and timing.
Animal anatomy and species motion switches have moved out of it.
`CharacterDrawContext` holds per-scene head transforms and exposes shared mesh
drawing. Shared life motion and materials live in `characters/shared/`.
Mesh and GLSL code live in `rendering/meshes/` and `rendering/shaders/`.

Existing character, board, dice and pack IDs are preserved. Legacy character
aliases still resolve. Unknown local IDs use known visual defaults; unknown
store content is not automatically purchasable. Remote price overrides,
ownership and purchase verification retain the existing store/account paths.
The legacy species enum remains for footprint/compatibility callers; the scene
and reaction selection resolve full character IDs.

## Verification

`ContentRegistryTest` checks catalog/renderer coverage, pack membership, legacy
fallbacks, finite timelines, renderer availability and palette isolation.
Existing motion, store, geometry and dice tests remain in place.

Source gates:

```
node scripts/check-ludo-paws-pawn-rendering.mjs
node scripts/check-ludo-paws-animation-fx.mjs
```

Build the QA variant and run the unit suite with `LUDOPAWS_QA_BUILD=true`.
The existing portrait and dice visibility instrumentation tests exercise real
GLES portraits and Canvas drawing on a connected device. Compilation alone
cannot verify driver behavior or the visual quality of new art.

## Manifest authoring

`content/content-manifest.json` is the **registration source of truth** for
character definitions/renderers/avatars/audio, board definitions/renderers,
and dice definitions/renderers. Its seven generated Kotlin files are checked
in for Android Studio builds without a Node dependency. Generation is
explicit, and CI verifies the generated files are current. New store/offer
registration and backend pricing/availability still require separate review;
manifest changes alone never enable purchases or change saved IDs.

```sh
node scripts/generate-content-registries.mjs --write
node scripts/generate-content-registries.mjs --check
node --test scripts/generate-content-registries.test.mjs
```
