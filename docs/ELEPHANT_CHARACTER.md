# Elephant character and clean character styling

## Styling rule

All current and future characters use their natural anatomy and coat colors.
Do not add team-colored collars, bibs, bands, necklaces, badges or hanging tags
to identify a player. Team identity belongs to the existing board/team UI.
The rule applies to gameplay models, shared 3D portraits and fallback avatars.
The old collar meshes, tag materials and shared bib drawing helper are removed.

## Elephant

`elephant` (display name **Elephant**) is a gentle, free character in
`starter_paws`, after Pig. Catalog and renderer registration feed the existing
Store, saved selection, offline setup and shared portrait infrastructure.

The procedural model has grey skin, broad ears with muted natural inner skin,
a six-segment tapered trunk, small curved ivory tusks, four thick legs with
rounded feet and toenails, and a thin tufted tail. There are no accessories.
Ear flaps, trunk curls, tail sways, gaze, blinks and mouth movement respond to
idle, hop, home and all game reaction cues. Shared hop dynamics retain the
normal travel and landing contract. Empty yard slots use a rounded elephant
footprint with three nail impressions; movement reuses the padded foot sound.

Voice uses procedural trumpet and rumble presets. Optional authored overrides
use `audio/sfx/voices/elephant/lp_vocal_elephant_<cue>_01.wav` (or `.ogg`), with
the standard numbered variants. Raw primitive overrides use
`lp_vocal_elephant_trumpet` and `lp_vocal_elephant_rumble`.

The server source accepts `elephant`. Until matching server deployment,
Android uses the existing `dog` compatibility identity for outgoing online
requests; the local Elephant selection is retained. Remote opponents see Dog
through this compatibility path. Remove the Elephant request fallback with
the matching server rollout. No server deployment is included here.

No tests, build or installation was run, as requested.
