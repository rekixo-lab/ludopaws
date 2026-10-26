# First-party Paws showcase, phase one

Home now uses its existing hero allocation for a clipped wordmark and one featured
character card. The original image file is unchanged; a wordmark-only Canvas mask
keeps the old static pets from duplicating the live character. HUD, mode buttons
and footer keep their existing positions. Character cards rotate every 12 seconds
when system animations are enabled. The Explore action opens the Store Paws tab
with that exact character selected for inspection, not equipped or purchased.

The Store Paws tab has one featured detail viewport above the existing lightweight
portrait grid. Tapping a grid character opens its detail rather than automatically
equipping it or starting an ad. The explicit action selects owned characters or
routes the existing content-ready offer through the Store's purchase/reward flow.
Gem purchases require confirmation. Rewarded-ad copy shows verified progress;
unsupported methods and unavailable build ad support cannot trigger unlocks.
No preview grants entitlements or automatically starts an ad or purchase.

Live models reuse the production character renderer and natural character materials
for idle, hop and celebration poses. A static portrait is available immediately;
ES3-capable devices start live rendering 250 ms after attachment/activation so
initial Store refreshes can settle without constructing multiple previews. Rendering is
request-driven at approximately 30 FPS, pauses while offscreen, unfocused or in
the background, and detaches with its view. Home pauses before the Store starts.
Store grid portraits do not become separate live GL scenes. Disabled system
animations retain a static pose and disable automatic item rotation.

This is the character-first stage of the proposed Home + Store showcase. Board
dioramas, mesh-based dice promotions, match-result placements and analytics are
not included. Existing board/dice catalog previews and match screens are unchanged.

No build, tests, lint, installation or PR was run/created, as requested. Device QA
remains pending for compact Home spacing, wordmark masking, transparent GL
composition, scroll clipping, pause/resume, static fallback, repeated navigation,
verified ad progress and successful/failed unlock flows. Performance and purchase
conversion have not been measured.
