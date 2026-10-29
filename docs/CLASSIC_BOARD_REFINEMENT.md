# Classic capsule-road refinement

Classic remains the default, with the same ID, team identities, joined houses,
route, safe cells and gameplay. Its environment is now a lush tropical animal
habitat: mossy ground, soft earthen clearings, bamboo, ferns, palm canopies and
weathered rocks. This is an original code-generated jungle, not copied map art.

`ClassicJungleHabitat` owns all four yards. It replaces the old colored turf and
sand enclosure rather than painting greenery under the old opaque yard artwork.
The interior around the four canonical animal positions stays open. Team identity
uses small edge accents and the existing colored lanes/roofs; character materials
remain natural. Soft directional shading and deterministic ground textures are
baked once by the existing background artwork worker. In gameplay, low-poly
palms add actual lit 3D leaf ridges and trunks at the edges, below the animals.

`ClassicRoadProfile` is the single normalized layout for Canvas previews and the
Classic-only GL road layer. All 72 road/home-lane cells share the same gutter
inset, rounded capsule corners, bevel width and restrained relief height. The
Canvas pass first covers the legacy black grid with a continuous moss-earth recessed
joint bed, avoiding doubled outlines and inconsistent gaps. Neutral cells now
use clean fine-grained stone instead of the mismatched pebble texture that
previously showed around the edges of the opaque GL slabs.

The 3D layer uses real curved silhouette segments, sloping bevel normals and
lower risers for both neutral and team-colored roads. Safe shields/checkmarks
and entry stars are rebuilt above the surface rather than hidden underneath it.
The shared geometry stays inside each existing cell; no input coordinates,
movement timing, footprints, home ranks or rule state changes.

Gutter inset is reduced from 7.5% to 2.6% of the short cell dimension, about 65%
less straight-edge spacing. Shallower risers, smaller corner cutouts and narrower
bevels also reduce the large empty pockets between capsules. Canvas and GL use
the same profile, including neutral weathered olive-stone faces.

Only Classic enables the new road relief. Other boards retain their previous
road renderers, including the Monsoon rain and mud effects. Existing center-house
artwork is retained. Canvas uses the same team pigments as the GL
meshes rather than independently boosting saturation.

CPU geometry is prepared on the existing texture worker and shared immutably.
GL buffers remain context-local and batched into five surface colors, two marker
batches and three jungle foliage/trunk batches. There is no new GL context,
animation loop or downloaded asset.
The unused pebble texture is no longer prepared for Classic.

No tests, build, installation or PR was run/created, as requested. Real-device
visual checks remain pending for preview/gameplay consistency, narrow-screen
gutters, all perspectives, safe/entry markers and frame pacing.
