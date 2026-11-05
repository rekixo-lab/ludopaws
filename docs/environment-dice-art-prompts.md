# Photorealistic dice art — assets and prompts

Generated with the built-in **imagegen** tool, one transparent image per material. The original selected outputs were copied unchanged into the project. No Python image editing, API fallback or API key was used. Runtime Android code adds the resolved pips; the generated images intentionally have blank front faces. The source PNGs are 1254×1254 with real alpha transparency.

## Final saved assets

- [Forest Dew](../android/app/src/main/res/drawable-nodpi/dice_forest_dew_3d.png)
- [Lagoon Pearl](../android/app/src/main/res/drawable-nodpi/dice_lagoon_pearl_3d.png)
- [Frost Crystal](../android/app/src/main/res/drawable-nodpi/dice_frost_crystal_3d.png)
- [Starlight](../android/app/src/main/res/drawable-nodpi/dice_starlight_3d.png)

## Prompt set

Each full prompt was the following shared template, replacing `{subject}` with the exact material-specific paragraph below. `transparent_background` was set to `true` for each call.

```text
Use case: product-mockup. Asset type: finished photorealistic 3D mobile board-game dice body sprite, single object. Subject: {subject}
Photorealistic premium studio macro product render, physically based materials, realistic dimensional geometry, ray-traced reflections, ambient occlusion, soft key light from upper left, believable self-shadow and small highlights. It must look like a photograph of a tangible expensive physical object, NOT a painting, illustration, flat vector, cartoon or icon.
Camera/composition: square image, isolated centered single object, transparent background. Almost straight-on orthographic view with a slight elevated 3/4 angle showing just enough top and RIGHT side to prove true thickness. Main front face faces the viewer almost straight, flat broad unobstructed and approximately square, about 65% image width; its four corners approximately at (18%,28%), (79%,24%), (79%,86%), (18%,90%). Whole object within 8%-94% of image. Do not crop. Consistent studio lighting. Fine details must remain legible at small game size.
CRITICAL: the main face is COMPLETELY BLANK smooth material: absolutely NO pips, dots, holes, numerals, paw marks, symbols, text or engraving on the central face. Runtime game code adds the correct pips; leave ample clear center. Do NOT show pips or numbers on top or side faces either. Ornament only at edge/crown as specified. No scenery, ground plane, background, labels, watermark or extra objects. True alpha transparency outside the object. This is a finished game asset, not a concept sheet.
```

### Forest Dew subject

```text
A chunky real hand-carved warm honey oak wooden dice block, softened squared corners, real end grain, pores and satin wood finish. A tiny sculpted fresh green leaf hugs the top-left rim, never across the main face. Solid thickness, carved dark bark-colored back bevel. DISTINCT silhouette: sturdy square wood block.
```

### Lagoon Pearl subject

```text
An exquisite thick scalloped seashell-shaped dice block, an organic softly scalloped squared front with four subtle shell lobes, made from real iridescent pale aqua mother-of-pearl and translucent turquoise resin. Champagne pearl bevel. Very subtle shell ribs confined to outer rim. DISTINCT silhouette: softly scalloped shell block, NOT regular cube.
```

### Frost Crystal subject

```text
A substantial clear ice blue cut crystal dice, octagonal front face (clipped square corners), broad sharply beveled facets with internal refraction, realistic dispersion and frosty tiny edge imperfections. Clean pale frosted-glass central face. DISTINCT silhouette: hard octagonal cut gemstone block.
```

### Starlight subject

```text
An exquisite midnight violet obsidian and polished black amethyst dice, plump cushion-square front, broad extremely rounded corners, dark luminous glass depth. Fine real brushed golden bezel following the front edge, tiny sculpted gold crescent moon attached to upper crown without covering main face. DISTINCT silhouette: pillowy round cushion-square jewel block.
```
