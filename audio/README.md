# Ludo Paws authored audio

This folder is the source of truth for replaceable production audio. Drop real WAV or OGG files into SFX/voice folders, and WAV, OGG, or MP3 files into `audio/music/`. Android Gradle mirrors these files into APK assets at build time; do not copy them manually into `res/raw`.

Do not add fake, silent, or zero-byte WAVs. Empty folders use .gitkeep until an authored sound is ready.

## Background music

Folder: `audio/music/`.

Put background-music files directly in this folder. The loader supports legacy `ludoproof_theme (number).wav` / `.ogg` names and the current `Firefly-audio-Title-Ludo-Paws-Ro.mp3` theme. The Firefly track is selected ahead of legacy numbered tracks, and the app falls back to `res/raw/ludoproof_theme` only when no authored track can be opened.

Important: do not leave filenames containing spaces or parentheses in `android/app/src/main/res/raw/`; Android resource filenames must be resource-safe. Move those BGM files into `audio/music/` without renaming them. Gradle packages this folder as APK assets automatically.

## Windows duplicate suffixes are supported

For SFX and animal voices, files such as `lp_sfx_capture_01 (1).wav` and `lp_vocal_dog_capture_01 (2).wav` are discovered automatically alongside their canonical filename slots. Keep the added suffixes; do not rename audio files just to remove `(1)`, `(2)`, etc. Put them in the matching folder under `audio/sfx/`.

## Movement: keep jumps and footfalls separate

### Jump / hop take-off
Folder: audio/sfx/gameplay/movement/jump/
- lp_sfx_jump_01.wav — already present; used at a real hop take-off.
- lp_sfx_jump_02.wav
- lp_sfx_jump_03.wav

### Cell landing / footfall
Folder: audio/sfx/gameplay/movement/step/
- lp_sfx_step_01.wav
- lp_sfx_step_02.wav
- lp_sfx_step_03.wav

Optional character overrides:
- audio/sfx/gameplay/movement/step/dog/lp_sfx_step_dog_01.wav
- audio/sfx/gameplay/movement/step/goat/lp_sfx_step_goat_01.wav
- audio/sfx/gameplay/movement/step/duck/lp_sfx_step_duck_01.wav
- audio/sfx/gameplay/movement/step/cat/lp_sfx_step_cat_01.wav

For each character, _02.wav and _03.wav variants are supported. Character-specific footfalls are tried first, then the shared step family, then legacy raw/procedural fallbacks. A jump file is never used as an ordinary cell landing.

Sound direction: footfalls should be short and soft (roughly 40–75 ms); springy jump take-off should be distinct (roughly 90–160 ms). Dice rattle is the roll-start texture; dice settle is a brief tick when the committed/verified face appears. Keep the settle quieter than the rattle and avoid long tails that overlap with the six sparkle. These are mix/design targets, not strict file validators.

## Physical gameplay and UI effects

For each family, add the exact first filename shown. Optional _02.wav / _03.wav variants are supported and rotate when present.

| Folder | First filename | Use |
| --- | --- | --- |
| audio/sfx/ui/click/ | lp_sfx_ui_click_01.wav | UI click / tap |
| audio/sfx/gameplay/dice/ | lp_sfx_dice_roll_01.wav | Rolling rattle |
| audio/sfx/gameplay/dice/settle/ | lp_sfx_dice_settle_01.wav | Short landing tick when the verified face appears |
| audio/sfx/gameplay/six/ | lp_sfx_six_01.wav | Six / bonus-turn sparkle |
| audio/sfx/gameplay/yard_exit/ | lp_sfx_yard_exit_01.wav | Token launch from yard |
| audio/sfx/gameplay/capture/ | lp_sfx_capture_01.wav | Capture contact impact |
| audio/sfx/gameplay/safe/ | lp_sfx_safe_relief_01.wav | Safe-cell arrival accent |
| audio/sfx/gameplay/home_lane/ | lp_sfx_home_lane_01.wav | First home-lane entry |
| audio/sfx/gameplay/home/ | lp_sfx_home_01.wav | Home arrival |
| audio/sfx/gameplay/fail/ | lp_sfx_fail_01.wav | Soft no-legal-move / poor-roll accent |
| audio/sfx/gameplay/exact_home_miss/ | lp_sfx_exact_home_miss_01.wav | Separate gentle descending spring for a roll short of home |
| audio/sfx/gameplay/third_six/ | lp_sfx_third_six_01.wav | Third six forfeiture |
| audio/sfx/gameplay/victory/ | lp_sfx_victory_01.wav | Match / team win |
| audio/sfx/gameplay/defeat/ | lp_sfx_defeat_01.wav | Match / team loss |

## Character animal voices

Root-level authored character voices are supported under audio/sfx/voices/<character>/.

Use exact cue names and numbered variants, for example:
- audio/sfx/voices/dog/lp_vocal_dog_capture_01.wav
- audio/sfx/voices/cat/lp_vocal_cat_captured_01.ogg
- audio/sfx/voices/duck/lp_vocal_duck_home_01.wav
- audio/sfx/voices/goat/lp_vocal_goat_six_01.ogg

Supported authored character IDs: dog, goat, duck, cat, donkey, goat_lumi, cock, pig, elephant, horse, rabbit, panda, monkey. Each character prefers its own folder. Lumi uses Goat as fallback when its own cue is missing; Donkey/Panda/Monkey stay silent without their own authored cue. See `audio/START_HERE.md` and each character folder README for all prompts and filenames. The reaction profile currently requests voice variants _01 and _02. Use short, non-verbal, original/licensed character takes. Normal movement and routine turn-start moments should remain vocally quiet; capture, home, third-six and final results may get a stronger reaction.

Existing Android res/raw voice names remain a compatibility fallback. If a matching root audio file exists, it is preferred.

## Technical export targets

- WAV: PCM 16-bit, mono, 44.1 kHz or 48 kHz is the preferred source for physical effects.
- OGG or WAV is accepted for animal voices.
- Trim leading silence; use a clean transient and controlled tail.
- No digital clipping or unnecessarily aggressive loudness normalization.
- Keep physical SFX, animal vocals and background music as separate assets/lanes.
- A filename in this guide is a slot, not a claim that the sound file already exists.

For per-character audio profiles and optional dice/board sound overrides, see `docs/CONTENT_AUDIO_EXTENSION.md`.

## Board ambience (independent of music)

Optional looping ambient assets for a board live in `audio/ambience/<board-id>/`,
for example `audio/ambience/board_monsoon_mire/rain_loop.ogg`. Add its exact
path to that board's `BoardDefinition.ambientSoundPaths`. WAV/OGG/MP3 files
are packaged automatically by Gradle. This lane runs at a quiet level alongside
background music and follows the Music toggle, Android audio focus, foreground
and board selection. Missing assets mean silence for that lane. Never use
unlicensed third-party recordings; long clips should be optimized/compressed.
