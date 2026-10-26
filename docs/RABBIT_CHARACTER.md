# Rabbit character

`rabbit` (display name **Rabbit**) is a curious, free character in
`starter_paws`, after Horse. Catalog and renderer registration supply Store
selection, saved selection, offline setup and the shared 3D portrait.

The procedural model has warm cream fur, long ears with pink inner skin,
a white belly, fluffy tail, large hind paws, small front paws, whiskers,
a twitching pink nose and two small front teeth. It has no collar, bib,
tag or team-colored accessories. The clean-character rule in
`ELEPHANT_CHARACTER.md` also applies to Rabbit and its fallback avatar.

Rabbit has its own idle, hop, home and reaction profiles. The shared hop
dynamics control path travel and landing; front and rear paws tuck separately,
ears fold and recover, and head, mouth and tail react to game cues. Empty yard
slots use a rabbit footprint with long rear pads and smaller front pads.
Movement reuses the existing soft paw sound.

Voice uses a quiet procedural snuffle at 55% of the normal reaction volume.
Optional authored overrides use
`audio/sfx/voices/rabbit/lp_vocal_rabbit_<cue>_01.wav` (or `.ogg`), following the
numbered-variant convention. The raw primitive override is
`lp_vocal_rabbit_snuffle`.

The server source accepts `rabbit`. Until matching server deployment, outgoing
Android online requests use the existing Dog compatibility identity; local
Rabbit selection is retained. Remote opponents see Dog through that path.
Remove the Rabbit request fallback with the matching server rollout. No backend
was deployed as part of this change.

No tests, build or installation was run, as requested.
