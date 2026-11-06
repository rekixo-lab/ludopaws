# Rare 3D Gem currency artwork

The cyan game currency now uses one photorealistic, prerendered 3D brilliant-cut gemstone with a visible crown, girdle thickness, pointed pavilion, transparent blue depth, dark facets and restrained white reflections. The material keeps the existing cyan identity. Its visual value comes from the cut, clarity and depth; no new rarity tier, reward eligibility, wallet balance, exchange value, price, ad condition or billing product is introduced.

## Final saved assets

Built-in **imagegen** generated the single Gem and edited the referral bucket using the existing bucket as edit target and the new Gem as material/cut reference. Selected PNGs were copied unchanged to the project; original assets were preserved. No API fallback or Python image editing was used.

- [Single Gem](../android/app/src/main/res/drawable-nodpi/currency_rare_gem_3d.png)
- [Matching referral bucket](../android/app/src/main/res/drawable-nodpi/referral_rare_gem_bucket_3d.png)

Both are 1254×1254 RGBA PNGs with actual alpha transparency. `RareGemArtwork` decodes them with `inScaled=false`, `inSampleSize=4` and ARGB_8888 into a synchronized, two-entry shared bitmap cache. Their combined decoded texture memory is approximately 0.75 MiB; the raw PNGs total about 3.5 MiB. Cache ownership retains no Activity. First-use decoding and real-device frame timing remain unmeasured.

## Updated surfaces

- Home balance pill and Rewards action use the new single-Gem art through `HomeIconView`.
- Shop balance, Gem purchase packs, rewarded-ad/Remove Ads cards, and Rewards wallet/gift/spin artwork use `GemClusterView`. Its existing 1–5 placements and named `gemCount` API are preserved. Size changes update reusable bounds; drawing shares the source texture instead of generating a new full-size bitmap for every view.
- Cosmetic price labels use independent, explicitly bounded 12dp `BitmapDrawable` instances over the same shared Gem bitmap. Amounts, suffixes, span positions and baseline alignment are preserved. No temporary View/raster is needed to create each price icon.
- Both referral reward views use the matching new bucket through the shared cache. Reward amounts, conditions and copy are preserved.

Artwork reflections are baked into the sprite. No continuous animation, timer or reduced-motion exception was added. This is 3D-style rendered artwork in the existing native 2D UI, not a new live mesh renderer.

## Source review boundary

Reviewed the complete shared loader, both Home Gem call sites, all `GemClusterView` consumers and 1–5 placements, size-change bounds, price-span construction, both bucket callers, new resource IDs and PNG alpha metadata. Resource references point to the workspace copies. No application code references the legacy `referral_gem_bucket_3d` asset. Existing balance/reward/billing logic is untouched. `git diff --check` covers whitespace.

No tests, build, app installation, CI, backend deploy, merge to main or Play release was run. Android rendering is not device-verified. After the user builds the update branch, check Home, Shop → Gems, cosmetic prices, Rewards wallet/gifts/spin and Friendship Rewards. Check tiny 12–26dp icons against light/dark panels, all five pack sizes, larger text settings and low-memory device behavior.

## Final prompt: single Gem

```text
Use case: product-mockup. Asset type: final transparent mobile game currency sprite, ONE precious gemstone.
Primary request: an exceptionally rare, extremely valuable natural vivid cyan-blue diamond, a tangible believable real three-dimensional cut jewel. Keep the game currency's cyan/turquoise identity, but make it look like a museum-grade collector's gemstone photographed by a high-end jewelry photographer.
Shape: a beautifully proportioned wide brilliant-cut diamond viewed in a very clear elevated THREE-QUARTER SIDE VIEW, showing a broad polygonal crown/table, substantial visible girdle thickness, and a deep pointed pavilion BELOW. This is NOT a flat top-down jewel. Numerous precise jewel-cut facets form physically correct volume; distinguish top face, angled side facets and pointed lower body. A memorable strong readable diamond silhouette at 16–32 pixels.
Material: exceptionally clear transparent blue crystal, rich cyan/turquoise interior, deep royal-blue shadow facets, believable light refracting THROUGH the stone, a few very restrained spectral fire glints on edges, crisp polished surfaces, small sharp white studio highlights. Subtle internal depth. Realistic refraction, Fresnel reflections and dispersion. Rare and expensive because of the exquisite cut and clarity, not because of a crown or frame.
Lighting: sophisticated studio softbox upper-left with one narrow right rim light, restrained high dynamic range, realistic dark/light facet contrast. Some truly dark blue facets for depth, some pale icy-cyan facets; do not wash the whole gemstone white. Physically based ray-traced product render that looks like a photograph of a real 3D gem, not plastic, jelly, a painted illustration, a vector icon, cartoon or low-poly flat shape.
Composition: one single isolated gemstone, centered square image, gemstone occupies 84% of the frame, complete uncut silhouette, approximately 9% safety padding, point downward, broad crown above center, only slight clockwise yaw. Genuine alpha transparency everywhere outside the gemstone. No ground, background, backdrop, giant glow, floating sparkles, star shapes, pedestal, jewelry setting, gold frame, rings, hands, labels, words, numbers, watermark or extra gems. Keep small-size game icon readability. The stone should feel genuinely rare, real and valuable.
```

## Final prompt: matching referral bucket

`transparent_background=true`; image 1 was the original `referral_gem_bucket_3d.png`, image 2 was the generated single Gem.

```text
Use case: precise-object-edit. Asset type: final transparent 3D mobile-game referral reward artwork.
Image 1 is the EDIT TARGET: the existing wooden bucket full of blue gems.
Image 2 is the MATERIAL AND CUT REFERENCE for all gemstones.
Replace every gemstone in and around the bucket with exceptionally rare, valuable cyan-blue brilliant-cut diamonds matching Image 2: broad polygonal crown, distinct real girdle thickness, deep pointed pavilion, exceptionally clear transparent blue crystal, rich cyan interior, royal-blue dark facets, accurate physically based refraction, crisp polished facets and restrained white studio highlights. Each should look like a tangible genuine rare jewel. Avoid the old painted/star-shaped shine. Match believable different rotations and partial occlusion; retain the overall abundant reward composition.
Preserve the bucket identity, its shape, warm wood, gold/brass hoops, rivets, handle, framing, and transparent background. Upgrade its material realism coherently to natural finely grained oak and brushed metal with believable reflections. Product-photographic premium 3D ray-traced quality throughout, same upper-left studio lighting as the reference. Object isolated centered, whole bucket, handle and loose gems fully inside square image, safety margin. NO text, number, labels, watermark, giant glow, floating stars, scenery or background. Preserve actual alpha transparency. This is a finished in-game asset, not a painting, cartoon or flat icon.
```
