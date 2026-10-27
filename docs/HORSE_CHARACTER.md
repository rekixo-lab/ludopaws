# Horse character

`horse` (display name **Horse**) is a brave, free character in `starter_paws`,
after Elephant. Its definition and rendering registration feed the existing
Store, offline setup, saved selections and shared 3D portraits.

The procedural horse has a chestnut body, long neck and muzzle, short pointed
ears, a dark flowing mane and full tail, solid hooves, dark lower legs and a
natural white facial blaze. It has no collar, bib, tag, saddle or team-colored
accessories, following the clean-character rule in `ELEPHANT_CHARACTER.md`.
Horse uses its own geometry, face, motion and reactions. Its four articulated
legs reuse the existing equine analytic leg solver, while shared hop dynamics
retain the normal travel and landing contract. Empty yard slots use a solid
hoof imprint; movement uses the existing hoof sound.

Voice uses procedural neigh and snort presets. Optional authored overrides use
`audio/sfx/voices/horse/lp_vocal_horse_<cue>_01.wav` (or `.ogg`), following the
numbered-variant convention. Raw primitive overrides use
`lp_vocal_horse_neigh` and `lp_vocal_horse_snort`.

The server source accepts `horse`. Until matching server deployment, outgoing
Android online requests use the existing Dog compatibility identity; the local
Horse selection is retained. Remote opponents see Dog through that path.
Remove the Horse request fallback with the matching server rollout. No backend
was deployed as part of this change.

No tests, build or installation was run, as requested.
