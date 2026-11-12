# Prototype Validation and Exit Criteria

## P0 — must pass before calling the Godot prototype playable

- [ ] Godot 4 stable opens project.godot and runs main scene with no parser/runtime errors.
- [ ] 15×15 board is visually correct, fully visible and tappable, with Cat Red/Panda Yellow.
- [ ] Two people can start, play and finish a complete match using **four pawns each**.
- [ ] Dice generates a visible 1–6 outcome; roll and selection inputs are disabled appropriately.
- [ ] Six enters a pawn from yard; multiple legal pawn choices are selectable; blocked/invalid move cannot be committed.
- [ ] Travel and color-relative cell path are correct on all 52 outer squares and home lane.
- [ ] Safe square prevents capture; non-safe capture returns pawn to yard and awards correct extra turn.
- [ ] Extra turn on 6, third consecutive six behavior and no-legal-move behavior are correct.
- [ ] Exact home requirement and win detection work.
- [ ] Dice animation has no influence on RNG or legality.
- [ ] Cat and Panda are distinguishable 3D figures with idle, moving, capture and victory reactions.
- [ ] Mouse + touch controls work; HUD doesn't cover token targets on portrait screen.
- [ ] Rule tests run in Godot headless and pass.
- [ ] Final rerun of all tests documented; no production repo, keys or live API modified.

## P1 — preferred for visual evaluation

- [ ] Soft, optimized lighting, consistent shadows, particles, satisfying move/capture feedback.
- [ ] Reduced motion and low effects settings; no critical action conveyed through color only.
- [ ] Landscape and smaller Android screens tested.
- [ ] Save/reload or pause/resume does not create illegal moves.
- [ ] Android debug build installs side-by-side with production using unique app ID.
- [ ] FPS/frame times, memory, cold-start time, binary size and battery usage recorded on at least one real Android device.
- [ ] Side-by-side visual/video comparison using similar turns and same device/settings.

## Validation commands

Run in repository root after Godot has been installed:

~~~sh
python3 scripts/validate_project.py
godot --headless --editor --path . --quit
godot --path .
# Once tests/run_tests.gd is implemented:
godot --headless --path . --script res://tests/run_tests.gd
~~~

Use godot4 if that is the executable name on the machine. These are commands to run, **not assertions that they have already passed**. GitHub Actions currently runs **static scaffold validation** and **Godot 4.7.2 headless import / 10-frame preview smoke checks**; these verify loading/parsing, **NOT** complete gameplay, animation quality, physical device performance or Android export. Codex must add rules tests and perform actual playable/device checks.

## Decision: should Ludo Paws migrate?

Only decide after a playable Godot demo exists. Compare against the existing Android game on the same devices, recording actual evidence. Migration is not automatic. If Godot provides better visual productivity but worse battery/memory/startup, keep the live Kotlin app and consider selective visual upgrades.
