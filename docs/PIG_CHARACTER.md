# Pig character

`pig` (display name **Pig**) is a playful, free character in `starter_paws`,
after Cock. Its definition and renderer registration feed Store selection,
offline setup, saved selections and shared 3D portraits.

The code-drawn model has a rounded pink body, broad snout with two nostrils,
folded ears with darker inner surfaces, blush cheeks, four short legs with
split hooves, and a curled tail. Its bib follows the player's team color.
Idle, hop and home motion share the standard travel and landing dynamics.
All game reaction cues have Pig-specific ear, head, tail and mouth motion.
The existing hoof footprint family and hoof movement sound are reused.

Voice uses procedural oink and squeal presets. Authored cue overrides can be
placed at `audio/sfx/voices/pig/lp_vocal_pig_<cue>_01.wav` (or `.ogg`), following
the existing numbered-variant convention. Raw primitive overrides use
`lp_vocal_pig_oink` and `lp_vocal_pig_squeal`.

The server source accepts and preserves `pig`. Following the existing rollout
policy, Android requests send `dog` until the corresponding server is deployed;
the local Pig selection is retained. Remote opponents see Dog through that
compatibility path. Remove the Pig request fallback with the matching server
rollout. No deployment is included here.

No build, tests or installation was run, as requested.
