# Autumn Forest

`board_autumn_forest` is the first seasonal board, offered free in Store and
Settings immediately after Classic. Existing boards and the Classic default
remain available without changes to their appearance or equipment.

The separate Canvas renderer uses copper, russet and burgundy leaves, small
branches, warm earthen clearings with organic edges, and wooden track planks
with restrained grain. Team-colored rims and home lanes retain player identity.
Lighting is static and diffuse; there is no falling-leaf loop or flashing glow.
The central yard areas remain open for the animals.

The board uses the existing nonuniform logical axis, 52 track cells, 20 home
lane cells, entry/safe positions and four finish triangles. Input, movement,
footprint overlays, finish ranks and 3D houses stay with shared gameplay code.

Autumn opts out of the Classic 3D stone-road overlay so its wooden roads remain
visible in gameplay. This option defaults to enabled for every existing board.
The scene carries the option through state updates and reloads it when equipment
changes or the view attaches; no per-frame account reads are needed.

Artwork uses the existing background preparation and byte-bounded board cache.
Store thumbnails reuse the shared base board and cached artwork, without adding
live GL scenes, external images or another asset-loading gate.

Android and server source register the free board. Family/offline selection uses
the existing free-cosmetic path. Connected accounts require matching server
deployment for ownership/equipment synchronization; no backend was deployed.

No tests, build or installation was run, as requested.
