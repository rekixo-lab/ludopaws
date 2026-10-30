# Ludo Paws audio replacement guide

This is the designer-facing map for changing game audio without editing gameplay code.

## Source-of-truth audio folder

Authored production SFX and animal voices live under the repository-root audio/ folder. Android Gradle mirrors supported .wav and .ogg files into APK assets automatically. Add or replace the source file here; do not manually copy it into Android res/raw.

Physical SFX resolution order:
1. Existing authored repository audio asset.
2. Existing raw resource compatibility fallback.
3. Built-in generated situation-specific fallback.

Animal voice resolution order:
1. Character-specific audio under audio/sfx/voices/<character>/.
2. Existing named Android res/raw character cue.
3. Packaged species vocal primitive or generated non-verbal fallback.

## Movement and jump

Jump take-off:
- audio/sfx/gameplay/movement/jump/lp_sfx_jump_01.wav
- Optional variants: lp_sfx_jump_02.wav and lp_sfx_jump_03.wav

Normal cell landing/footfall:
- audio/sfx/gameplay/movement/step/lp_sfx_step_01.wav
- Optional variants: lp_sfx_step_02.wav and lp_sfx_step_03.wav

Character-specific landing overrides:
- audio/sfx/gameplay/movement/step/dog/lp_sfx_step_dog_01.wav
- audio/sfx/gameplay/movement/step/goat/lp_sfx_step_goat_01.wav
- audio/sfx/gameplay/movement/step/duck/lp_sfx_step_duck_01.wav
- audio/sfx/gameplay/movement/step/cat/lp_sfx_step_cat_01.wav

Character-specific folders support _02.wav and _03.wav variants. If the character override is missing, the shared step family is attempted before existing raw/procedural fallback. The jump asset is now separate and is only played at a visible hop take-off; it is not used for every board cell.

## Other physical SFX folders

- audio/sfx/ui/click/ — lp_sfx_ui_click_01.wav
- audio/sfx/gameplay/dice/ — lp_sfx_dice_roll_01.wav (roll-start rattle)
- audio/sfx/gameplay/dice/settle/ — lp_sfx_dice_settle_01.wav (short landing tick when the verified result appears)
- audio/sfx/gameplay/six/ — lp_sfx_six_01.wav
- audio/sfx/gameplay/yard_exit/ — lp_sfx_yard_exit_01.wav
- audio/sfx/gameplay/capture/ — lp_sfx_capture_01.wav
- audio/sfx/gameplay/safe/ — lp_sfx_safe_relief_01.wav
- audio/sfx/gameplay/home_lane/ — lp_sfx_home_lane_01.wav
- audio/sfx/gameplay/home/ — lp_sfx_home_01.wav
- audio/sfx/gameplay/fail/ — lp_sfx_fail_01.wav (generic no-legal-move / poor-roll accent)
- audio/sfx/gameplay/exact_home_miss/ — lp_sfx_exact_home_miss_01.wav (separate downward spring when the roll is short of home)
- audio/sfx/gameplay/third_six/ — lp_sfx_third_six_01.wav
- audio/sfx/gameplay/victory/ — lp_sfx_victory_01.wav
- audio/sfx/gameplay/defeat/ — lp_sfx_defeat_01.wav

Optional _02.wav and _03.wav takes are rotated when present. Folders with no real authored file keep using the established fallback; a catalog path alone does not mean its file already exists.

## Character animal voices

Use audio/sfx/voices/<character>/lp_vocal_<character>_<cue>_<variant>.wav or .ogg.

Characters: dog, goat, duck, cat, donkey, goat_lumi, cock, pig, elephant, horse, rabbit, panda, monkey. Each own folder is supported; see `audio/START_HERE.md` for per-character prompts and filenames. Lumi retains Goat fallback, while Donkey/Panda/Monkey stay silent when their own recording is missing.

Example files:
- audio/sfx/voices/dog/lp_vocal_dog_capture_01.wav
- audio/sfx/voices/dog/lp_vocal_dog_captured_01.wav
- audio/sfx/voices/goat/lp_vocal_goat_safe_01.ogg
- audio/sfx/voices/duck/lp_vocal_duck_home_01.wav
- audio/sfx/voices/cat/lp_vocal_cat_frustrated_01.ogg

Current reaction profile looks for numbered voice takes 01 and 02. Keep voices non-verbal, short, and character-specific. The Android raw-resource naming convention remains supported as a fallback for existing packaged cues.

## Mix / export rules

- Physical effects: WAV PCM 16-bit mono, 44.1 kHz or 48 kHz is preferred.
- Footfall: roughly 40–75 ms, short and soft.
- Jump take-off: roughly 90–160 ms, a distinct transient from the landing.
- Avoid clipping, long leading silence and excessive reverb.
- Keep movement SFX, major gameplay accents, animal voices and background music separate.
- Use original or properly licensed recordings.

Replacing these audio files does not require changing gameplay rules, dice proof, legal token selection, turn order or move authority.