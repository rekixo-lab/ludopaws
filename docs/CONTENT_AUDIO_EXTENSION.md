# Content/audio extension status (2026-10-28)

The stable character, board, dice and purchase IDs remain unchanged. The audio
system now has a registered profile for each shipped character, including a
Donkey/Panda/Monkey with no automatic voice fallback and Lumi Goat's shared Goat fallback. All shipped characters now accept their own authored voice folders; see `audio/START_HERE.md`.

## Adding character recordings

- Create character definition, renderer and avatar under its content folder.
- Register these plus voice/step profile together in `content/content-manifest.json`
  and run `node scripts/generate-content-registries.mjs --write`.
  CI verifies generated definition, rendering and audio registry parity.
- Optional vocals live at `audio/sfx/voices/<id>/lp_vocal_<id>_<cue>_01.wav`.
  The other supported variant is `_02`. Missing takes use existing fallback.
- Optional steps live at
  `audio/sfx/gameplay/movement/step/<id>/lp_sfx_step_<id>_01.wav`
  (up to `_03`). Custom sounds are preferred; old shared hoof/paw/web
  families and procedural output remain the fallback.

## Board and dice audio

- A board can set `BoardDefinition.backgroundMusicPaths` to a list of packaged
  `audio/music/` asset filenames. Otherwise the old default soundtrack plays.
  A selected board change re-evaluates music and discards stale preparation.
- A dice definition can set `rollSoundPaths` and `settleSoundPaths` to
  `audio/sfx/` filenames. The selected skin wins when a clip is present;
  missing clips fall back to the classic authored roll/settle, then raw audio.
- The audio system never chooses a dice outcome or edits match/proof state.
- Each board may optionally set `ambientSoundPaths` to files under
  `audio/ambience/<board-id>/`. This is an independent, quiet looping
  `MediaPlayer` layer mixed below the music. The Music setting controls both;
  sound effects and animal voices remain separate. Empty/missing paths mean
  no ambience, not another board's ambient clip. Background, focus loss and
  rapid board changes discard old playback/preparation.

## Tools

```sh
node scripts/generate-content-registries.mjs --write
node scripts/generate-content-registries.mjs --check
node --test scripts/generate-content-registries.test.mjs
```


```sh
node scripts/ludopaws-content-audio-tools.mjs validate
node scripts/ludopaws-content-audio-tools.mjs report
node scripts/ludopaws-content-audio-tools.mjs draft character fox
node scripts/ludopaws-content-audio-tools.mjs draft board board_forest
node scripts/ludopaws-content-audio-tools.mjs draft dice dice_forest
node --test scripts/ludopaws-content-audio-tools.test.mjs
```

Drafts are documentation/scaffolds only. They do not automatically create a
renderer, make a cosmetic available, grant ownership or alter prices.

## Compatibility and release

Do not change saved IDs, local pack associations, purchase verification or
server availability while adding authored sound. Run Android unit tests, lint,
the standard CI content gates and real-device audio timing checks before
turning a future cosmetic on.
