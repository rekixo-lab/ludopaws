# First-party Paws showcase, phase one

Home places the featured character card below the Shop/Badges/Leaderboard/Remove Ads
shortcuts, followed by the complete original hero artwork and then the mode buttons.
The original image file is unchanged and uses FIT_CENTER with no Canvas mask or crop:
the title, pets, pedestals and die remain visible at a smaller scale. These sections
share a vertical layout rather than overlapping absolute offsets. Short screens can
scroll the content above the reserved fixed footer instead of hiding sections behind
it. Character cards rotate every 12 seconds
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
ES3-capable devices start live rendering 250 ms after attachment/activation and
visibility checks so initial Store refreshes can settle without constructing
multiple previews. A cancellable starter prevents delayed work surviving pauses.
The preview uses a transparent TextureView, not a separate SurfaceView overlay,
so it clips with the scrolling card and does not punch through the fallback.
EGL setup, frame submission and cleanup stay on its worker; failed setup restores
the portrait. SurfaceTexture release is deferred until worker cleanup completes.
Rendering is
request-driven at approximately 30 FPS, pauses while offscreen, unfocused or in
the background, and detaches with its view. Home pauses before the Store starts.
Store grid portraits do not become separate live GL scenes. Disabled system
animations retain a single static frame and disable automatic item rotation.
Animation clocks do not jump across background or offscreen pauses. Old character
frames remain hidden until the replacement's first successfully swapped frame.
Rotating characters discard the prior model's context-local mesh buffers rather
than accumulating every featured model in the live preview's GPU cache.

Detail cards stack vertically below 320 dp or with larger system text. Names
ellipsize instead of clipping, buttons remain 48 dp tall and the narrow Home
variant reduces thumbnail width. Home hides secondary copy at larger font sizes;
the showcase stays below the HUD and above the hero and mode controls. Availability and
ownership are refreshed on return rather than frozen at card construction. A
completed verified ad requirement can still be claimed when new ads are disabled.

Cosmetic prices use the existing drawn Gem artwork inline with their amounts.
Store Paws/Board cards and the unlock chooser use localized Google Play product
prices (including currency symbols) without deriving currency from the phone locale
or hardcoding USD. The detail preview uses the same live price. Missing products
remain price unavailable; Play Console configuration is still required.

This is the character-first stage of the proposed Home + Store showcase. Board
dioramas, mesh-based dice promotions, match-result placements and analytics are
not included. Existing board/dice catalog previews and match screens are unchanged.

No build, tests, lint, installation or PR was run/created, as requested. Device QA
remains pending for compact Home spacing, full hero visibility, localized prices, transparent GL
composition, scroll clipping, pause/resume, static fallback, repeated navigation,
verified ad progress and successful/failed unlock flows. Performance and purchase
conversion have not been measured.
