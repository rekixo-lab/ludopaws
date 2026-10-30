# Character audio drop-in preparation

Price and publishing remain PENDING. No production deployment or generated audio is included.

## Ready folders

`audio/START_HERE.md` links to all 13 characters. Each `audio/sfx/voices/<id>/README.md` provides 14 situation prompts, first/second-take names, duration targets and existing recordings. Each `audio/sfx/gameplay/movement/step/<id>/README.md` provides a separate footfall prompt and three optional take names.

There are 182 vocal prompts, 364 optional vocal filename slots and 39 optional footfall filename slots. These slots are documented, not empty WAV files. Existing 77 WAV/OGG/MP3 files were checked by SHA-256 and remain byte-for-byte unchanged. Normal build asset sync packages real recordings; README files are not audio assets.

## Loading changes

- Every shipped ID accepts its own authored vocal folder. The existing `voiceAssetOwnerId` now defines fallback ownership, rather than restricting which character can supply an authored override.
- Lumi Goat prefers `goat_lumi` recordings. Missing cues retain the old Goat recordings/raw/procedural fallback.
- Donkey, Panda and Monkey prefer their own recordings and have no borrowed vocal fallback. Missing or unplayable authored clips remain silent; absent clips do not schedule a music duck.
- Donkey's old SILENT-only voice set now accepts supported cues, with actual playback guarded by authored-file availability.
- The folder resolver keeps longest-ID matching so `goat_lumi` cannot accidentally resolve into `goat`. Existing duplicate filename suffix support, variant selection, settings, probability, dedupe, cooldown and capture timing remain in place.
- The content-audio validator accepts authored recordings for all shipped IDs, including authored-only characters and shared-fallback characters, while rejecting unregistered folders/cues.

## Verification

- Kotlin app/test compilation and 26 focused Android unit tests passed, zero failures/skips.
- Content/audio tooling tests passed, including own-recording folder acceptance for Donkey and Lumi, with rejection of an unknown character folder.
- Registry consistency, character domain and audio source gates passed.
- All 13 folder guides were checked against the runtime VoiceCue enum and manifest. All voice and footstep filenames were checked; existing recording hashes were checked.
- No APK install, actual Firefly generation or physical-device audio listening test was performed. New recordings need rebuilding/installing and listening acceptance after the user supplies the audio.
