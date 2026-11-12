# Performance on Samsung A71 (SM-A715F, Adreno 618, 1080x2400) — release/v2-rc

Measured on the phone with the dev-only probe `scripts/debug/perf_probe.gd` (inactive in
release exports and without `user://perf_probe.cfg`), test package `com.rekixo.ludopaws.v2rc`.
Screenshots: `.godot/preview/perf/` (board before/after, `phone_after/` tour, `scroll_*`).

## Match (4-player Robot, 90 s)

| | Baseline (a8c7f86) | Now |
|---|---|---|
| Recurring stalls | ~2 s nearly every second | none during play |
| Worst frame | 2,962 ms | 869 ms (match-open frame only) |
| Avg fps | 13.6 | 23.8 (board now edge to edge, physical px) |
| Mean p95 frame | 1,711 ms | 70 ms |
| Dice tap → first motion | — | 54 ms avg, 61 ms max |
| Match open | 514 ms | 41 ms (board bake cached) |
| Memory (static) | 106 MB | 93 MB |

Root cause of the stalls: `classic_board_art._draw` (~1 s on the A71) replayed whenever a
page rebuild re-parented the board. Fixed by baking board art once per board and physical
size (memory + disk) and updating the match in place.

**Open:** 60 fps is not reached. With pawn scripting skipped the match still runs at ~24 fps,
and MSAA off (28), a flat shader (31) or a 540 px scene (29) do not fix it: the 3D scene's
~800 per-part draw calls are the limit. Next step: batch pawn parts into MultiMeshes.

## Shop / Wallet

| | Baseline | Now |
|---|---|---|
| Shop → Boards tab | 1,190 ms | 31 ms |
| Shop worst frame | 10,848 ms | 1,143 ms (first board bakes) |

## Scrolling (touch flings, frames while the list moves)

| List | Before fps / max | After fps / max |
|---|---|---|
| Shop Paws | 29.7 / 64 ms | 60.0 / 24 ms |
| Shop Boards | did not scroll from a card | 60.0 / 22 ms |
| Shop Dice | did not scroll from a card | 48.4 / 32 ms |
| Wallet | 60.1 / 22 ms | 60.0 / 20 ms |
| Rewards | did not scroll from a card | 60.0 / 24 ms |
