# Ludo Paws v2 — Godot Prototype

> STATUS: **Playable local Ludo prototype.** Robot and Pass & Play matches run on the live
> Ludo Paws rules (ported and parity-tested), on a port of the live classic board, from the
> Moonlit Meadow UI through to the results screen. Animal characters, the 8 live dice, online
> play and an Android device run are still to come (see [What is left](#what-is-left)).

This project is the **Godot migration of the live Ludo Paws app** and will ship as its Play Store update. The live
Android repository (rekixo-lab/ludopaws), backend, Play Console and AdMob account are read-only from here.
Scope, boundaries and the UI rule are in `AGENTS.md`.

## What is built

| Area | Where | Notes |
|---|---|---|
| Rules engine | `scripts/game/rules/ludo_engine.gd`, `ludo_rulesets.gd` | Port of the live server engine: CLASSIC_V1–V8 and TEAM_UP_V1–V7 (opening six, third-six forfeit, comeback, stack protection, finish places, team handoff). |
| Offline Robot | `scripts/game/rules/ludo_bot.gd` | Port of the live Android offline `LudoPawsComputerMovePolicy` (deterministic). Easy is a V2-only wrapper. |
| Local turn order | `scripts/game/local_match.gd` | Local matches pass the turn around the board perimeter, like the live offline engine. |
| Local dice | `scripts/game/rules/local_dice.gd` | Plain local RNG. **Not** server-authoritative, cryptographic or certified; dice never depend on animation. |
| Classic board | `scripts/game/board3d/` | Port of the live board stack: baked 2D artwork (jungle yards, stone road cells, safe shields, entry stars), an orthographic 3D layer (raised road relief, palms, centre houses, opening doors, garden, WINNER/2ND plaques with party lights), step-by-step pawn movement, capture return, home entry and finish badges. Pawns use the live app's 2D fallback pawn until the animals are ported. |
| 3D characters | `scripts/game/characters/` | Shared framework (pose, hop dynamics, life motion, living behaviour, reaction layers, team identity, 3D portrait) and **Dog, Goat, Duck, Cat, Donkey, Lumi Goat, Cock, Pig, Elephant, Horse, Rabbit, Panda, Monkey, Giraffe** (14/14) ported from the live Kotlin renderer, proven by draw-call parity tests. All 14 species stand on the board as 3D animals with hop/settle/home motion, facing and legal-move breathing, and react to game moments (sixes, captures, safe squares, home, frustration, threats, idle, victory/defeat) through the live reaction pipeline (`scripts/game/reactions/`). |
| Match screen | `scripts/game/local_match_page.gd` | The Moonlit Meadow match page now hosts the 3D classic board; one board instance is kept per match. |
| UI | `scenes/ui`, `scripts/ui` | Moonlit Meadow Home, lobbies, match, results, settings (incl. Reduced Motion). |

## What to open in Godot

Install **Godot 4.7.2 stable** (standard edition) and open the folder containing `project.godot`.

| Open | Press | You will see |
|---|---|---|
| (project) | **F5** | Home (`scenes/ui/home/home_screen.tscn`, the main scene). Tap **Play Robot** or **Pass & Play** → **Start Game**: the 3D classic board. Tap **Roll Dice**, then tap a highlighted pawn (or its **Paw N** button) to move; captures send pawns home; the first player home wins and the results screen opens. |
| `scenes/previews/board_preview.tscn` | open it, or **F6** | The 3D classic board in a mid-game position, drawn straight in the editor viewport (`@tool`). Change **Perspective** in the Inspector to rotate it. |
| `scenes/previews/robot_match_preview.tscn` | open it, or **F6** | Robot vs Robot on the 3D board, playing itself (also in the editor viewport; turn off **Run In Editor** to pause it there). Shows the turn, roll and the last moves; restarts after each match. Inspector: player count, think time. |
| `scenes/previews/characters_preview.tscn` | open it, or **F6** | All 14 characters idling (unported ones say "pending"), then the selected character playing IDLE, HOP, HOME and every reaction cue. Inspector: **Character Id**, **Team**. |
| `scenes/main.tscn` | **F6** | The original procedural placeholder (stylised Cat/Panda, preview die). It is **not** the game; online/team sample screens still embed it. The real board is `scripts/game/board3d/classic_board_3d.gd`. |

Settings → Accessibility → **Reduced Motion** turns off pawn step and capture animations.

## Running the tests

From the project folder, the one-shot check (fails on any non-zero exit **or** any Godot script error in the log):

```sh
tests/run_all.sh
```

Or individually (flatpak users: replace `godot` with `flatpak run org.godotengine.Godot`):

```sh
# Rules, live-server parity, Robot policy parity, artwork RNG parity, character draw-call parity (exit 0 = pass)
godot --headless --path . --script res://tests/run_tests.gd
# Full UI flow: Home -> Pass & Play and Robot matches -> results screen
godot --headless --path . --script res://tests/match_flow_smoke.gd
# Import / parser check and a 120-frame boot of the main scene
godot --headless --path . --import
godot --headless --path . --quit-after 120
# Static structure check
python3 scripts/validate_project.py
# Every live source file is named in docs/PARITY_INVENTORY.md (needs the private live repo; skips otherwise)
python3 tests/inventory_coverage.py
```

Screenshots (need a display, not `--headless`), written to `.godot/preview/`:

```sh
godot --path . --script res://tests/board_preview.gd -- res://.godot/preview
godot --path . --script res://tests/capture_previews.gd -- res://.godot/preview
godot --path . --script res://tests/match_flow_smoke.gd -- res://.godot/preview
```

Parity fixtures: `tests/fixtures/server_rules_golden.json` (81 games / 23,067 steps from the live
server engine, regenerated with `gen-server-golden.mjs`) and `tests/fixtures/robot_policy_golden.json`
(4,000 cases from the unchanged live Kotlin Robot policy; see `tests/fixtures/robot_golden_gen/`).
Character fixtures `tests/fixtures/characters/<id>_golden.json` record every part the unchanged live Kotlin
renderer draws (matrix, colour, surface, mesh), its poses, reactions and life motion; regenerate with
`tests/fixtures/characters_gen/generate.sh <id>` (needs the private live repo and the Gradle Kotlin jars).
`tests/fixtures/reactions_golden.json` records the live reaction pipeline (moments, reactions, director,
idle clock, feedback ledger, FX plans) over 3,239 snapshots of seeded local matches that
`tests/reaction_inputs.gd` rebuilds; regenerate with `tests/fixtures/reactions_gen/generate.sh`.
`tests/fixtures/capture_return_golden.json` samples the live captured-pawn route; regenerate with
`tests/fixtures/capture_return_gen/generate.sh`.
`tests/fixtures/layout_golden.json` samples the live road foot drop and pawn radius; regenerate with
`tests/fixtures/layout_gen/generate.sh`.
`tests/fixtures/motion_golden.json` records which token changes the live app animates; regenerate with
`tests/fixtures/motion_gen/generate.sh`.
`tests/fixtures/fx_golden.json` records every draw call of the live FX painter; regenerate with
`tests/fixtures/fx_gen/generate.sh`.

**Continuous integration:** [Static structure check](https://github.com/rekixo-lab/ludopaws-v2/actions/workflows/static-scaffold-check.yml) and [Godot 4.7.2 headless smoke](https://github.com/rekixo-lab/ludopaws-v2/actions/workflows/godot-smoke.yml).

## New V2 feature (not in live): first-time Demo match

On the very first launch the app asks the player's age (live age screening: only an age band is
stored), then starts a short **Demo match** against an Easy robot: both sides start with three paws
already home and one paw on the track (`DEMO_START_POSITION` = 30 in
`scripts/game/tutorial/demo_match.gd`). Dice and rules are the real ones. Coach hints (Hindi +
English) appear once each in the status line; **Skip** is always available; finishing or skipping
saves `tutorial_completed` in `user://tutorial.cfg`. Settings → Tutorial → **Play tutorial again**
replays it without changing the flag. The demo awards no Gems and counts toward no stats or ads.

To see the first launch again, delete `user://tutorial.cfg` (and `user://family_privacy_v1.cfg`
for the age screen) — in Godot: Project → Open User Data Folder.

## Match rail (live layout, V2 look)

The match screen uses live's player rails: a card per player in its yard's screen corner (first
player bottom-left), each with its own dice on the card's inner side; only the active player's
dice is shown and only an active local human can tap it. Robot and remote dice roll in their own
slot. Pass & Play shows **PASS THE PHONE / name / "<Colour> paws • your turn" / READY**.
Spec: `docs/MATCH_RAIL_SPEC.md`. Screenshots: `tests/match_rail_preview.gd` →
`.godot/preview/match_rail/`.

Deliberate V2 differences from live:
- Rail height reserves the ~60 dp card only (live 78/70 dp also holds a quick-chat footer V2 lacks).
- Cards may widen into free rail space (up to +45 %) so text is not cut; ellipsis if still too long.
- The handoff prompt appears after the previous move finishes presenting (live raises it on the
  snapshot change, under the animation).
- Online and Team Up rails are proven with mock models until online play is built.
- The old Roll button / Paw buttons row is gone (live has none: the dice is the button).

## What is left

Where the migration stopped and the next step: **"Resume here"** at the top of
[docs/PARITY_INVENTORY.md](docs/PARITY_INVENTORY.md).

The complete live → V2 checklist (every feature, its live sources, V2 target and status) is
[docs/PARITY_INVENTORY.md](docs/PARITY_INVENTORY.md). Summary:

1. Legal-move shimmer, tap hand/coach, pacing details, audio and the result card.
2. **8 live boards and 8 live dice**, the dice roll animation, Shop, Wallet, billing and ads.
3. Offline-only **Quick Match** variant (two paws each).
4. **Online client** against the existing backend; the endpoint lives only in the git-ignored `config/release.local.cfg`, never in the repository.
5. **Android**: debug export with the prototype package ID and a real-device run (performance, memory, battery). Not done yet.

## Folders

| Path | Purpose |
|---|---|
| project.godot | Godot project entry (main scene: Home) |
| scenes/ui, scripts/ui | Moonlit Meadow UI |
| scripts/game | Rules engine, local match model, match page |
| scripts/game/board3d | Classic board (2D art, 3D relief/houses, shader, geometry) |
| scenes/previews, scripts/previews | Editor preview scenes |
| tests | Rule tests, UI smoke test, screenshot scripts, parity fixtures |
| scenes/main.tscn, scripts/main.gd | Original placeholder scaffold (kept, not the game) |
| AGENTS.md, docs/ | Standing instructions, spec, test plan, boundaries |

UI assets: `assets/ui/backgrounds` (scenery and panel art), `assets/ui/brand` (title plaque, font,
Home illustration), `assets/ui/icons` (SVG glyphs), `assets/ui/illustrations` (screen art).

## Android identity and release

The release export uses the live package ID **com.rekixo.ludopaws** so the app installs as an update of the
live game. The owner signs releases with the existing upload key (Play App Signing); no key, password, real
AdMob ID or backend URL is ever committed — they live in the git-ignored `config/release.local.cfg`
(template: `config/release.example.cfg`). Development builds use Google's test ad IDs and local stubs.
See `docs/TECHNICAL_BOUNDARIES.md`.

## Release gate

Release only when every row of `docs/PARITY_INVENTORY.md` is done/tested (or device-only and verified on a
phone, with its reason written), the EntroNex decision is made, and the owner has compared both apps on the
same Android devices (gameplay feel, frame time, memory, battery, cold start, APK size).
