# Photorealistic environment dice — next update

Four distinct prerendered 3D body sprites replace the flat Canvas body illustrations. These are photo-style assets drawn by the existing Android Canvas dice view, not real-time mesh rendering. The existing roll/settle transform still animates the complete body and its pips together.

| Shop name / existing ID | Material and geometry | Matching environments |
| --- | --- | --- |
| FOREST DEW / `dice_forest_dew` | Chunky oak block with real grain, leaf and recessed dark pips | Autumn Forest, Dawn Meadow, Blossom Garden |
| LAGOON PEARL / `dice_lagoon_pearl` | Thick scalloped mother-of-pearl shell with aqua refractions and raised pearl pips | Cloud Lagoon, Monsoon Mire |
| FROST CRYSTAL / `dice_frost_crystal` | Eight-sided clear blue crystal, broad cut facets and dark diamond inlays | Winter Haven |
| STARLIGHT / `dice_starlight` | Plump amethyst cushion, real gold bezel/crescent and raised golden pips | Astral, Moonlit Grove |

[Artwork reference](environment-dice-preview.svg) uses the actual shipped PNGs and the same face/pip geometry on face five. It is an artwork reference, not an Android screenshot. [Asset paths and full generation prompts](environment-dice-art-prompts.md) record the built-in imagegen workflow.

## Runtime integration

- Each raw sprite is a 1254×1254 RGBA PNG with alpha transparency in `android/app/src/main/res/drawable-nodpi/`. No generated pips are baked into its body: the game draws exactly the resolved face's 1–6 pips at runtime.
- Each material has a calibrated front-face quadrilateral. A cached `Matrix.setPolyToPoly` projects the conventional pip grid and material-specific cavity/pearl/crystal/gold shading onto that face. Pips and artwork share the existing roll transform. No dice outcome, capture/comeback or turn rule changes.
- `DiceRenderer.prepare(Resources)` is called at creation by `DiceRendererRegistry`. Existing classic and Pearl Paw renderers inherit its no-op implementation. Both registry callers in `DiceView` now supply Resources, keeping its existing density-based factory contract intact.
- An application-wide, synchronized four-entry bitmap cache shares each decoded sprite across Shop previews and game views without retaining an Activity. Decode uses `inScaled=false`, `inSampleSize=4` and ARGB_8888: about 314×314 per texture and roughly 1.5 MiB for all four decoded textures. Original PNGs total about 7.8 MiB of source assets. A cold decode occurs once on first use; draw does not decode images or allocate shaders, matrices, paths, paints or pip arrays per frame.
- The existing team outline, attention/rolling pulse and deadline/countdown bounds are preserved. Five percent internal art padding protects the leaf and crescent from the team outline. No new animation timers or reduced-motion exceptions.

## Ownership and release

All four keep their existing IDs, preview faces and 100-Gem prices. Already-owned/selected skins update in place. They are manually selectable on any board; selecting a board does not replace the player's die. No new Play product, ad unlock, automatic ownership grant or collection/referral change. Published server catalog values retain their existing precedence.

The matching server entries from the earlier dice commit still need to ship with the next release before the live backend can unlock these IDs. This artwork revision does not deploy or change the backend.

## Review boundary

Reviewed resource names and alpha metadata, both registry creation call sites, the default prepare hook for existing renderers, manifest/factory compatibility, all six pip coordinate arrays, front-face projection, shader/bitmap caching, canvas save/restore balance, preserved cosmetic IDs/pricing and the asset-based artwork reference. `git diff --check` covers whitespace. No tests, Android build, device installation, CI, merge to main, backend deploy or Play release was run. Compiled/device appearance and frame timing remain unverified.

After the user builds the branch, check Shop → Dice, selection persistence and all six faces during local and online matches. Confirm small-screen pip contrast, first-open smoothness, team colors, online countdown and reduced-motion behavior; also recheck Classic and Pearl Paw. Unlock verification additionally requires the matching backend release.
