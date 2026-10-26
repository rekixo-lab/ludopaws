# Monsoon Mire

`board_monsoon_mire` is a separate free rainy-season theme. Older boards remain
unchanged and Classic stays the default.

## Live 3D presentation

The Canvas artwork provides earthy courtyards, muddy ruts, reeds, wet stepping
tiles and readable team lanes. In gameplay the existing OpenGL scene adds actual
lit mesh geometry: raised mud banks, glossy puddle surfaces and bevelled road
rims. These are geometry with normals and depth, not a photo or animated bitmap.

Sixteen staggered raindrops fall toward eight puddles and their muddy banks.
Each landing produces a short 3D splash crown and expanding, fading elliptical
ripple; bank impacts use earthy mud-colored splashes. The loop is stylized and
gentle, with no lightning flashes, downloaded video, new audio or physics system.
Rain impacts are ambient visual events, not changes to pawn movement or rules.

Only this theme enables the extra scene. Meshes are created once per renderer and
reuse the existing GL program, context and buffers. Static terrain is batched into
three draws; animated effects use at most 32 additional draws, with no per-frame
mesh creation, preference reads or texture uploads. Visibility/pause/cleanup use
the existing scene lifecycle. Disabling system animators retains static 3D relief
but disables rain and impact motion when the style reloads.

Effects rotate with the board, stay within the yard regions and render underneath
characters and houses. Track geometry, safe-cell stars, home lanes, hit testing,
proof state and gameplay remain shared. Classic habitat and stone-road overlays
are disabled only for this theme.

Store thumbnails deliberately remain lightweight static previews, not additional
live GL scenes. Gameplay supplies the animated 3D layer. Free selection works in
Settings and Store without waiting for an older server wallet. Matching server
source is registered for later deployment; no deployment was performed.

No tests, builds or installations were run, as requested. Device checks remain:
all four perspectives, visible splash timing, pawn/marker readability, repeated
theme switches, pause/resume, disabled system animations and frame pacing on the
target phone. Performance has not been measured on a real device.
