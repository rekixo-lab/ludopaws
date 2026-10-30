# Ludo Paws reaction and audio system

This document is the designer/developer contract for animal reactions. Gameplay authority, dice proof, move legality and turn order are deliberately outside this system.

## Goals

- Dog, Goat, Duck and Cat react differently while remaining readable and non-annoying.
- Characters make short non-verbal animal-style vocals. They do not speak gameplay sentences.
- Normal movement stays quiet: physical step sounds are separate from animal vocals.
- Every packaged sound can be replaced without changing Kotlin gameplay code.
- Missing custom audio never breaks a match: a copyright-safe procedural fallback is used.
- Offline and online modes consume the same situation/reaction model.

## Layers

1. `LudoPawsGameMomentDetector` derives immutable gameplay situations from snapshot diffs.
2. `LudoPawsReactionEngine` maps situations to semantic vocal + animation cues.
3. `LudoPawsReactionDirector` handles dedupe, cooldown, priority and interruption.
4. `LudoPawsReactionAudioProfile` chooses character vocal family, frequency, volume, rate and timing.
5. `LudoPawsAudioAssetPlayer` resolves authorable repository audio, packaged `res/raw` compatibility assets and voice fallbacks.
6. `LudoPawsProceduralAudio` supplies the no-asset fallback.
7. `GameSoundFeedback` owns physical/game SFX; `LudoPawsVoicePlayer` owns animal vocals.
8. Mode dispatchers own exactly-once SFX/haptics. The board owns visual reactions + animal vocals.

## Character personalities

| Character | Personality | Motion/vocal direction |
| --- | --- | --- |
| Dog | Playful | springy, friendly yips, short whines, tail-energy |
| Goat | Curious | head/ear movement, quirky bleats, hoof feel |
| Duck | Cheerful | body bob/wing energy, very short quacks |
| Cat | Sassy | controlled movement, chirps/trills/purrs, restrained upset sounds |

Do not add aggressive dog growls, long repetitive quacks, human speech, mocking insults or long vocal loops.

## Supported situations

`TURN_STARTED`, `ROLL_STARTED`, `SIX_ROLLED`, `LOW_ROLL`, `POOR_ROLL_STREAK`, `TOKEN_LEFT_YARD`, `TOKEN_MOVED`, `ONLY_LEGAL_MOVE`, `CAPTURE_MADE`, `TOKEN_CAPTURED`, `SAFE_REACHED`, `HOME_LANE_ENTERED`, `HOME_REACHED`, `EXACT_HOME_MISS`, `TOKEN_THREATENED`, `NO_LEGAL_MOVE`, `THIRD_SIX_FORFEIT`, `PLAYER_LEADING`, `IDLE_WAITING`, match/team win and loss.

Important semantic rules:

- `PLAYER_LEADING` is proud/confident, not nervous.
- `TOKEN_THREATENED` is only emitted on unsafe main-track cells and ignores team partners.
- `EXACT_HOME_MISS` is separate from generic no-legal-move frustration.
- `ONLY_LEGAL_MOVE` is visual/physical feedback only; it does not force an extra character vocal.
- Idle vocal eligibility starts after 24 seconds of unchanged meaningful state and is further probability/cooldown controlled.

## Drop-in animal vocal names

**Preferred source folder:** `audio/sfx/voices/<character>/`

Use `lp_vocal_<character>_<cue>_<variant>.wav` or `.ogg`.

Examples:

- `audio/sfx/voices/dog/lp_vocal_dog_six_01.wav`
- `audio/sfx/voices/dog/lp_vocal_dog_capture_02.wav`
- `audio/sfx/voices/goat/lp_vocal_goat_captured_01.ogg`
- `audio/sfx/voices/duck/lp_vocal_duck_home_01.wav`
- `audio/sfx/voices/cat/lp_vocal_cat_frustrated_02.ogg`

Current authored character IDs: `dog`, `goat`, `duck`, `cat`, `donkey`, `goat_lumi`, `cock`, `pig`, `elephant`, `horse`, `rabbit`, `panda`, `monkey`. See `audio/START_HERE.md` for ready paste folders and prompts. Current vocal cue basenames: `six`, `yard_exit`, `capture`, `captured`, `safe`, `home_lane`, `home`, `frustrated`, `third_six`, `idle`, `nervous`, `proud`, `victory`, `defeat`. `silent` intentionally has no audio file.

Two variants (`01`, `02`) are currently requested by the reaction profile. The selected variant is deterministic per reaction key, so reconnect/re-render cannot randomly change the choice. If a root audio file is present, it is preferred. Existing `res/raw/lp_vocal_<character>_<cue>_<variant>` resources remain a compatibility fallback.

### Replacing a vocal

To replace Dog capture, add `audio/sfx/voices/dog/lp_vocal_dog_capture_01.wav` and optionally `audio/sfx/voices/dog/lp_vocal_dog_capture_02.wav`, then rebuild. Gradle mirrors root audio files into APK assets; no Kotlin/gameplay change is needed.

## Drop-in physical SFX names

Use the root audio folders and numbered names in `audio/README.md`. The key distinction is:

- Hop take-off: `audio/sfx/gameplay/movement/jump/lp_sfx_jump_01.wav`
- Generic cell landing: `audio/sfx/gameplay/movement/step/lp_sfx_step_01.wav`
- Optional species landing: `audio/sfx/gameplay/movement/step/<character>/lp_sfx_step_<character>_01.wav`

The movement renderer triggers jump at hop take-off and footfall at the visual cell arrival. A late render frame does not cause missed landing sounds to fire in a burst. Character-specific footfalls resolve before the shared family and legacy raw/procedural fallback. Dice roll starts with a rattle and ends with an independent settle tick on the revealed verified face. Exact-home misses use their own soft downward spring instead of sharing the generic no-legal-move cue.

Other families: `lp_sfx_ui_click`, `lp_sfx_dice_roll`, `lp_sfx_dice_settle`, `lp_sfx_exact_home_miss`, `lp_sfx_six`, `lp_sfx_yard_exit`, `lp_sfx_capture_impact`, `lp_sfx_safe_shimmer`, `lp_sfx_home_lane`, `lp_sfx_home_sparkle`, `lp_sfx_fail_soft`, `lp_sfx_third_six`, `lp_sfx_victory_sting` and `lp_sfx_defeat_sting`.

Optional numbered physical SFX variants rotate when multiple actual assets exist. Catalog paths are slots; missing audio still uses the established fallback.

## Vocal frequency policy

The defaults intentionally avoid chatter:

| Cue | Approximate vocal frequency |
| --- | ---: |
| Normal move / turn / roll | 0% |
| Six | 68% |
| Yard exit | 48% |
| Safe | 36% |
| Home-lane entry | 24% |
| Capture / captured | 100% |
| Home | 100% |
| Poor-roll streak | 100% |
| Generic no legal move | 34% |
| Exact-home miss | 48% |
| Threatened | 45% |
| Proud/leading | 18% |
| Idle (after timing eligibility) | 28% |
| Third six | 100% |
| Victory / defeat | 100% |

Frequency uses a stable hash of the reaction key rather than `Random`, making playback deterministic and testable.

## Capture timing

Capture is deliberately readable as one comic beat:

1. the attacker completes its visible movement to the committed destination,
2. physical capture impact SFX plays on the contact frame,
3. attacker vocal follows approximately 80 ms later,
4. captured pawn vocal follows approximately 155 ms after contact.

Contact delay is computed from the configured game speed and visible move length, so a long move does not make the attacker vocal precede its capture impact. The vocal player caps a capture batch at two animal vocals.

## Settings

- `Game Sounds` controls physical/game SFX.
- `Animal Voices` controls non-verbal animal vocals.
- `Haptics` controls vibration.
- `Reduced Motion` suppresses or simplifies visual motion only; it does not change gameplay.

## Adding another character later

1. Add the character and `VoiceSet`/`AnimationSet` in `LudoPawsCharacterCatalog`.
2. Map its species/personality in the audio/reaction profile.
3. Add optional `lp_vocal_<new_character>_<cue>_01/_02` files.
4. Add/extend its 3D motion profile.
5. Add unit tests for fallback + high-priority situations.

Do not place game-rule decisions in audio, animation, haptics or reaction classes.
