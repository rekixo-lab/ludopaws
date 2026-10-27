# Cloud Lagoon

`board_cloud_lagoon` is a free, separately selectable board in Store and
Settings. It appears immediately after Classic. Classic remains the default;
Astral Garden remains available as a separate design.

Cloud Lagoon uses a pale mint water background, round floating islands,
warm pearl surfaces, pastel team lanes, small cloud forms, leaf details and
gentle wave ribbons. Broad matte fills and soft lower shadows replace hard
bevels and metallic trim. The light direction is fixed and the artwork has
no flashing, moving glow or high-frequency texture. The warm pearl color
replaces pure white across the large play surfaces.

The circular islands fit around the existing four yard pawn positions.
The nonuniform 15-index layout, 52-cell path, 20 home-lane cells, four entry
positions, four other safe cells and four finish triangles retain their
existing coordinates. Entry arrows follow the existing travel direction;
ring-and-check marks indicate the other safe cells. Pawn positions, input,
rules, footprint overlays, finish ranks and shared 3D houses stay with the
existing board host.

This is a separate Canvas renderer rather than a recolored Astral renderer.
The normal background artwork worker and bounded bitmap cache compose it once
per size/style. Store previews use the shared base board and cached artwork
with a local theme override, without starting live 3D scenes. Previewing does
not change saved equipment. The Classic yard
habitat overlay is disabled only for this theme so the islands remain visible.

Android and server source register the free board. Family/offline selection
uses the existing free-cosmetic path. Connected account ownership/equipment
requires deployment of the matching server source. No backend was deployed.

No build, tests or installation was run, as requested.
