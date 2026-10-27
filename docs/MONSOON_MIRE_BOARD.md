# Monsoon Mire

`board_monsoon_mire` is a separate free rainy-season theme. Older boards remain
unchanged and Classic stays the default.

## Live 3D presentation

The Canvas artwork provides earthy courtyards, muddy ruts, reeds, wet stepping
tiles and readable team lanes. In gameplay the existing OpenGL scene adds actual
lit mesh geometry: raised mud banks, glossy puddle surfaces and bevelled road
rims. These are geometry with normals and depth, not a photo or animated bitmap.

Sixty-four staggered rain emitters cover an eight-by-eight grid across the whole
board, including lanes, yards and center. Impact positions change each cycle;
some drops also target the eight original puddles. The original Monsoon base
artwork, colors, tile finish and eight puddles are retained. No permanent road
stains or extra static puddles are added: rain, splashes and fading movement
prints are transient effects layered over the original theme.
Each landing produces a short 3D splash crown and expanding, fading elliptical
ripple. Surface classification chooses water ripples, smaller wet-stone splashes
or earthy mud-colored splashes. The loop is stylized and
gentle, with no lightning flashes, downloaded video, new audio or physics system.
Rain impacts are ambient visual events, not changes to pawn movement or rules.

Only this theme enables the extra scene. Meshes are created once per renderer and
reuse the existing GL program, context and buffers. Static terrain is batched into
three draws. Fixed pools cap rain at 64 emitters, trails at 48 prints and movement
impacts at 12; oldest entries are recycled. Glyph and splash meshes are shared,
with no per-frame mesh creation, preference reads or texture uploads. Visibility/pause/cleanup use
the existing scene lifecycle. Disabling system animators retains static 3D relief
but disables rain and impact motion when the style reloads.

Rain uses translucent blue-grey, volumetric tapered drops instead of opaque pale
triangles. A board-water material reduces white specular glare without changing
character eye materials or the original puddle artwork. Wider, longer drops use
a 1.4-second cycle and a longer airborne portion. Double fine ripple rings,
rounded splash crowns and a batched six-droplet outward spray make impacts more
legible. Mud impacts retain earthy colors. This makes rainfall more noticeable without
altering the base theme, adding a fog filter or covering characters with effects.

Terrain and footprints render beneath architecture. Rain and splashes render
above architecture but underneath characters so the center also receives rain.
All positions rotate with the board. Track geometry, safe-cell stars, home lanes, hit testing,
proof state and gameplay remain shared. Classic habitat and stone-road overlays
are disabled only for this theme.

## Movement traces

The active forward route supplies actual cell contacts, step timing and species
travel/lift samples. Each landed step leaves a pair of oriented mud prints and a
550 ms surface-specific impact. Airborne travel, idle poses, teleports, snapshot
refreshes and flying capture returns do not generate dotted connecting trails.
Print glyphs distinguish paws, split hooves and bird toes; a smear glyph is
available for turtle contacts. Each print holds for two seconds, then fades over
four seconds. Trails use canonical board coordinates, so resizing and perspective
rotation do not move them onto another route. Match changes, leaving the theme,
renderer release and disabled system animation clear transient traces.

Store thumbnails deliberately remain lightweight static previews, not additional
live GL scenes. Gameplay supplies the animated 3D layer. Free selection works in
Settings and Store without waiting for an older server wallet. Matching server
source is registered for later deployment; no deployment was performed.

No tests, builds or installations were run, as requested. Device checks remain:
all four perspectives, full-board rain coverage, splash timing, contact-aligned
prints and six-second fade, pawn/marker readability, repeated
theme switches, pause/resume, disabled system animations and frame pacing on the
target phone. Performance has not been measured on a real device.
