# Classic capsule-road refinement

Classic remains the default with its existing team palette, animal yards, joined
houses, route, safe cells and gameplay. This change refines the walking surface
rather than replacing the board with another seasonal theme.

`ClassicRoadProfile` is the single normalized layout for Canvas previews and the
Classic-only GL road layer. All 72 road/home-lane cells share the same gutter
inset, rounded capsule corners, bevel width and restrained relief height. The
Canvas pass first covers the legacy black grid with a continuous warm recessed
joint bed, avoiding doubled outlines and inconsistent gaps. Neutral cells now
use clean fine-grained stone instead of the mismatched pebble texture that
previously showed around the edges of the opaque GL slabs.

The 3D layer uses real curved silhouette segments, sloping bevel normals and
lower risers for both neutral and team-colored roads. Safe shields/checkmarks
and entry stars are rebuilt above the surface rather than hidden underneath it.
The shared geometry stays inside each existing cell; no input coordinates,
movement timing, footprints, home ranks or rule state changes.

Only Classic enables the new road relief. Other boards retain their previous
road renderers, including the Monsoon rain and mud effects. Existing house and
yard artwork is retained. Canvas uses the same warm team pigments as the GL
meshes rather than independently boosting saturation.

CPU geometry is prepared on the existing texture worker and shared immutably.
GL buffers remain context-local and batched into five surface colors and two
marker batches. There is no new GL context, animation loop or downloaded asset.
The unused pebble texture is no longer prepared for Classic.

No tests, build, installation or PR was run/created, as requested. Real-device
visual checks remain pending for preview/gameplay consistency, narrow-screen
gutters, all perspectives, safe/entry markers and frame pacing.
