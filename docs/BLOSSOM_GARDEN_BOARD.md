# Blossom Garden

`board_blossom_garden` is the second seasonal board: a spring garden offered
free in Store and Settings after Autumn Forest. All existing boards remain
available and Classic stays the default.

Its independent Canvas renderer uses pink five-petal blossoms, fresh green
vines, light garden terraces and pale ceramic paths. Broad neutral courtyards
stay open for the characters. Team-colored terrace rims and home-lane tiles
preserve player identity. Decorative flowers stay around the yard edges;
entry and safe cells keep recognizable star markers. Daylight is static and
soft, with no falling-petal animation, flashing light or extra render loop.

The existing nonuniform logical axis, track, home lanes, entry/safe positions,
finish triangles, hit testing and pawn coordinates stay intact. Footprints,
finish ranks and 3D houses continue through the shared gameplay layers.
Classic yard artwork and the Classic 3D stone-road overlay are disabled only
for this new theme so they do not cover its flowers or ceramic tile finish.

Artwork uses the existing background worker and byte-bounded cache. Store
thumbnails reuse the shared base board and cached artwork without live GL
scenes or new downloaded assets.

Android and server source both register the free board. Family/offline selection
uses the existing free-cosmetic path. Connected accounts require the matching
server deployment for ownership/equipment; no backend was deployed here.

No tests, build or installation was run, as requested.
