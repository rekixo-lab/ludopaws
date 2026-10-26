# First-party Paws showcase, phase one

Home uses the original hero allocation as a two-page carousel: complete FIT_CENTER
hero first, then the shared centered Store promotion. Width, height, mode controls
and footer offsets remain unchanged. The source artwork is unchanged.

Each Home entry starts on the hero. After 2.4 seconds the hero slides left while
the promotion slides in from the right. The promotion stays for 10 seconds, then
the hero returns for 7 seconds before the next automatic slide. Slides last 420 ms.
Horizontal finger drags snap to a page; vertical movement is not treated as a
page swipe. Touch/manual navigation defers the next automatic slide for 20 seconds.
Indicators and accessibility scroll actions expose both pages. Disabled system
animations disable automatic sliding but retain immediate manual page changes.
Pausing, losing focus, hiding or detaching stops automatic work and live previews.
Only the settled visible promotion page starts its renderer. Home Explore opens
the corresponding Store tab and, for characters, that exact character's detail.

Active offline and online matches place one compact Paws promotion between Back
and Settings, starting in the existing 52 dp header row. It grows downwards into
available empty space up to 132 dp, with a proportionally larger portrait and text.
It stops 8 dp above the nearest board/player-rail bounds. It has 8 dp clearance
from each fixed side button. Setup and matchmaking retain their titles rather than
showing the promotion. No board, player-rail, name, profile or dice geometry changes.
The card clips its artwork to its own bounds. If there is insufficient space or
the card intersects the board/player rails on a short viewport, only the promotion
becomes transparent and non-interactive; gameplay is never moved to make room.

The promoted artwork is geometrically centered between equal-width text columns:
category/name on the left, ownership/unlock amount and Store action on the right.
Characters use the Store's live 3D idle/hop/celebration renderer, not the previous
static portrait bob. Gameplay is capped at roughly 15 FPS, Home at 20 FPS and Store
at 30 FPS. Static portraits remain the immediate/failure fallback.
Promotion characters start their hop after three seconds so short Home slides
include a noticeable pose change; the Store's existing animation timing is retained.
One live preview is reused per card, and hidden, overlapping or unfocused cards stop requesting frames.
Content-ready boards share the same center slot and their actual layered previews.
Non-default dice can use the same slot when their shipped content becomes available;
unavailable Coming Soon assets are never advertised. Characters, boards and supported
dice interleave, with only content-ready, currently unowned items eligible. Owned
or unlocked assets never appear in Home/gameplay promotions. Item rotation is every 12 seconds
in gameplay and 7 seconds on Home; disabled animations stop automatic item rotation.
Inventory/account-wallet preference updates refresh eligibility immediately, as do
activation, focus return and item rotation. The current eligible item is preserved
when possible. Tap rechecks ownership before opening Store. Store catalog browsing
and owned-item selection are unchanged.

When no locked assets remain, the gameplay promotion becomes non-interactive and
invisible. Home keeps only the hero, hides pager indicators and disables manual and
automatic promotion paging instead of showing a blank or already-owned offer.
Inventory listeners are removed on detachment; refund/relock updates can restore
eligible promotions without changing board or player controls.
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
uses artwork that grows with the available header space, balanced side text and
two-line ellipsized names. Setup titles retain their original 52 dp row. Availability and
ownership are refreshed on return rather than frozen at card construction. A
completed verified ad requirement can still be claimed when new ads are disabled.

Cosmetic prices use the existing drawn Gem artwork inline with their amounts.
Store Paws/Board cards and the unlock chooser use localized Google Play product
prices (including currency symbols) without deriving currency from the phone locale
or hardcoding USD. The detail preview uses the same live price. Missing products
remain price unavailable; Play Console configuration is still required.

This is the Home-carousel + gameplay-header + Store showcase. New board dioramas,
new premium dice content, match-result placements and analytics are
not included. Existing board/dice catalog previews and gameplay controls are unchanged.

No build, tests, lint, installation or PR was run/created, as requested. Device QA
remains pending for compact header spacing, overlap suppression, touch pass-through,
Home auto/manual sliding and touch delays, full hero visibility, localized prices, transparent GL
composition, scroll clipping, pause/resume, static fallback, repeated navigation,
verified ad progress and successful/failed unlock flows. Performance and purchase
conversion have not been measured.
