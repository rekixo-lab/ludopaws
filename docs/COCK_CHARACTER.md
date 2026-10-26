# Cock character

`cock` (display name **Cock**) is a proud rooster in the free `starter_paws`
roster, after Lumi Goat. Catalog registration supplies the Store card, saved
selection, offline setup and shared 3D portrait without a separate screen.

The procedural model has a red comb and wattles, golden neck feathers, a
chestnut body, green tail feathers, pointed beak and separate clawed toes.
Its team bib uses the player's Ludo color. The renderer supplies idle, hop,
home, wing, tail and beak motion; the reaction profile covers every game cue.
Geometry is cached through the existing shared scene renderer.

Voice uses procedural crow and cluck presets. Optional authored overrides use
`audio/sfx/voices/cock/lp_vocal_cock_<cue>_01.wav` (or `.ogg`) and the existing
numbered-variant convention. Raw resources can also override the
`lp_vocal_cock_crow` and `lp_vocal_cock_cluck` primitives.

The server source accepts and preserves `cock`. Until the matching server
rollout, Android requests use `duck` as the compatibility identity, following
the existing Donkey/Lumi request fallback policy. The local saved selection
remains `cock`; remote opponents see Duck through that fallback. Remove the
`cock` request fallback only with the matching server rollout.

No build, tests, preview app or deployment was run for this addition, as requested.
