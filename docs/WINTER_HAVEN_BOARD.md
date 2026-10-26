# Winter Haven

`board_winter_haven` is the third seasonal board, following Autumn Forest and
Blossom Garden. It is a separate free theme; Classic remains the default and
all existing themes retain their artwork.

The independent Canvas renderer uses snow-covered evergreen trees, rounded
snowbanks, muted silver-blue ice tiles and small snowflake ornaments. Broad
snow courtyards remain open for characters. Team-colored rims, home lanes and
finish triangles retain player identity; entry and safe cells keep star markers.
Soft static lighting avoids glare, flashing, snowfall loops or extra animation.

The shared nonuniform board axis, track cells, home lanes, pawn coordinates,
hit testing, footprints, finish ranks and 3D houses are unchanged. Only this new
theme disables Classic habitat artwork and stone-road overlays so they do not
cover the snow and ice finish.

Rendering uses the existing background artwork worker and bounded bitmap cache.
Store previews use the lightweight shared board layers, not live GL scenes.
There are no downloaded images, models, sounds or new loading gates.

Android and server source register the free theme. The shipped-free selection
path makes it available in Store and Settings even with an older wallet snapshot;
selection is device-local without a server round trip. Cross-device server
inventory synchronization requires deployment of the matching server source.
No backend deployment, tests, build or installation was run.
