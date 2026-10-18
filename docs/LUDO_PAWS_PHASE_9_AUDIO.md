# Ludo Paws Phase 9 — Production Audio Engine

Phase 9 turns the earlier reaction-audio scaffold into a bounded, low-latency presentation system without changing any Ludo rule, proof material, random outcome, legal move, CPU decision, turn order, capture rule, or winner calculation.

## Audio lanes

Ludo Paws now treats audio as three independent user-facing lanes:

1. **Music** — existing looping background theme controlled by `GameMusicController`.
2. **Game Sounds** — short UI/gameplay effects rendered through one shared `SoundPool`.
3. **Animal Voices** — character reactions, independently switchable from Game Sounds.

`GameMusicController` keeps the existing Android audio-focus ownership and now supports temporary in-app ducking under animal calls / spoken fallback. System focus loss still pauses or ducks music as before.

## Low-latency short audio

`LudoPawsSoundPool` owns a single bounded `SoundPool` with game audio attributes. Samples load lazily; the first requested play is queued until Android reports that the sample is ready. The pending list and maximum stream count are bounded. Playback priorities protect capture, home, six, third-six and end-of-match cues above UI clicks and movement. Low-priority movement requests are coalesced while a sample loads, avoiding bursts of delayed footfalls when rendering resumes.

`GameSoundFeedback` no longer creates a `ToneGenerator` for every click or move. It uses reusable raw clips for click, move, roll, capture, safe, home, victory and defeat cues. Reaction batches emit at most one gameplay SFX based on the highest-priority reaction.

## Animal voices

Starter Paws has packaged, nonverbal synthesized cue assets for Ducky, Nutty, Spike and Woolly. The four starter character slots resolve through their species and use SoundPool for low-latency playback. Playback rate changes by context so a capture, defeat, nervous reaction and victory do not all sound identical.

These starter WAVs are original synthesized placeholders, not third-party recordings. Their stable resource names are the production replacement points for later polished licensed/original recordings. Future species that do not yet have a packaged clip fall back safely to on-device TTS; failure of TTS or a missing voice never affects gameplay.

## Music ducking

Animal playback calls `GameMusicController.duckForVoice(...)`. Music volume temporarily falls while the reaction is audible, then returns automatically. The existing foreground/background and Android audio-focus lifecycle remains authoritative for background music.

## Settings and comfort controls

`GameSettingsStore` keeps its existing preference file for upgrade compatibility and adds defaults for:

- `animalVoicesEnabled = true`
- `hapticsEnabled = true`
- `reducedMotionEnabled = false`

The Settings dialog exposes Music, Game Sounds, Animal Voices, Haptics and Reduced Motion separately. The expanded panel is placed in a `ScrollView` so small phones can reach every control. Reduced Motion is persisted in Phase 9 and becomes an animation-policy input in Phase 10.

## Movement / capture synchronization

Dice rattle and dice settle are separate events: online and offline start the roll rattle when the player action begins, then play the short dice-settle cue when the authoritative / committed result is available. A six can add its own brighter bonus-turn sparkle.

Jump take-off and normal cell landing are different sounds: the jump family is scheduled at visible hop take-off; footfall is scheduled at the cell-arrival tick. Character-specific step folders can override the shared step family without touching movement rules. If rendering resumes after a sound's timing window, missed hop / landing ticks are skipped instead of playing back as a burst.

Capture vocals are scheduled against the attacking pawn's final contact time, derived from the same game-speed and visual-step pacing as the rendered animation. At contact the impact SFX plays; the attacker vocal follows by about 80 ms and the captured-character reaction by about 155 ms. These delays do not change game state or delay authoritative move commitment.

## Haptics

Reaction haptics are preference-gated and presentation-only. Capture/third-six use a stronger short pulse, home/victory use a two-part pattern, six/safe use a light pulse, and idle/nervous reactions do not vibrate. The manifest includes `android.permission.VIBRATE`; no runtime permission prompt is required for this normal permission.

## Safety boundary

Phase 9 intentionally does **not** modify:

- `OfflineGameEngine`
- native dice derivation or receipt code
- server roll/move authority
- legal token calculation
- safe-cell/capture/exact-home rules
- CPU move selection
- winner/team-winner calculation
- server protocol / Durable Objects
- saved match schema

Audio/haptics consume reactions only after Phase 7/8 state-diff, de-duplication and priority scheduling have already decided what presentation event may play.

## CI gate

`scripts/check-ludo-paws-audio.mjs` verifies the source architecture, settings separation, starter raw assets, RIFF/WAV headers, voice ducking, VIBRATE permission, removal of per-event `ToneGenerator`, and the presentation-only safety boundary. The gate runs before the existing Android unit/lint/APK/AAB release checks.
