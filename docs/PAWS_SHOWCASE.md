# First-party Paws showcase, phase one

Home has no promotional card. Its original fixed menu layout and full hero image
are restored: 350/388 dp maximum width, 272/302 dp image allocation, FIT_CENTER,
with the original mode and footer offsets. The source artwork is unchanged.

Active offline and online matches place one compact Paws promotion between Back
and Settings, starting in the existing 52 dp header row. It grows downwards into
available empty space up to 132 dp, with a proportionally larger portrait and text.
It stops 8 dp above the nearest board/player-rail bounds. It has 8 dp clearance
from each fixed side button. Setup and matchmaking retain their titles rather than
showing the promotion. No board, player-rail, name, profile or dice geometry changes.
The card clips its artwork to its own bounds. If there is insufficient space or
the card intersects the board/player rails on a short viewport, only the promotion
becomes transparent and non-interactive; gameplay is never moved to make room.

The match variant uses the shared cached 3D portrait, not a second live EGL scene.
A subtle native bob/tilt and 12-second character rotation run only while visible
and focused, honoring disabled animations. They stop on hiding or detachment.
Tapping this promotion only shows an after-match Store reminder. It never opens
another Activity, pauses the match, starts an ad or launches a purchase.

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
the background, and detaches with its view.
Store grid portraits do not become separate live GL scenes. Disabled system
animations retain a single static frame and disable automatic item rotation.
Animation clocks do not jump across background or offscreen pauses. Old character
frames remain hidden until the replacement's first successfully swapped frame.
Rotating characters discard the prior model's context-local mesh buffers rather
than accumulating every featured model in the live preview's GPU cache.

Detail cards stack vertically below 320 dp or with larger system text. Names
ellipsize instead of clipping and buttons remain 48 dp tall. The match header
uses a portrait that grows from 44 dp with the available header space and single-line
ellipsized labels. Setup titles retain their original 52 dp row. Availability and
ownership are refreshed on return rather than frozen at card construction. A
completed verified ad requirement can still be claimed when new ads are disabled.

Cosmetic prices use the existing drawn Gem artwork inline with their amounts.
Store Paws/Board cards and the unlock chooser use localized Google Play product
prices (including currency symbols) without deriving currency from the phone locale
or hardcoding USD. The detail preview uses the same live price. Missing products
remain price unavailable; Play Console configuration is still required.

This is the character-first stage of the gameplay-header + Store showcase. Board
dioramas, mesh-based dice promotions, match-result placements and analytics are
not included. Existing board/dice catalog previews and gameplay controls are unchanged.

No build, tests, lint, installation or PR was run/created, as requested. Device QA
remains pending for compact header spacing, overlap suppression, touch pass-through,
restored Home hero visibility, localized prices, transparent GL
composition, scroll clipping, pause/resume, static fallback, repeated navigation,
verified ad progress and successful/failed unlock flows. Performance and purchase
conversion have not been measured.
