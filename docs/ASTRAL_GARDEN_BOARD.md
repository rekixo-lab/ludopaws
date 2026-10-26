# Astral Garden

`board_astral` is a free, separately selectable board in Store and Settings.
Classic remains the default. Astral Garden uses midnight enamel courtyards,
brass botanical inlays, constellation accents, jewel-colored home lanes and
ivory track tiles. All artwork is drawn in Canvas code through the existing
board renderer registry; no image download or extra asset is needed.

The renderer mirrors the existing nonuniform logical axis: six yard indices
span one third of the board, and three road indices span the next third.
Track, entry stars, safe stars and finish triangles remain at their existing
coordinates. Pawn positions, hit testing, rules and the four 3D finish houses
continue to use shared gameplay code. Yard footprints and finish ranks remain
above the cached artwork. Classic's garden overlay is disabled only for this
board so that it does not cover the new courtyards.

Gameplay uses the existing background artwork worker and bounded bitmap cache.
The Store uses the full shared gameplay view for each passive miniature, with
an explicit view-local theme override so previews stay accurate independently
of the equipped board. Previewing never changes saved equipment.

Android and the server source both register the free board. Offline selection
uses the existing free-cosmetic path. Connected accounts require deployment of
the matching server source before ownership and equipment can be synchronized;
no backend was deployed as part of this change.

No build, tests or installation was run, as requested.
