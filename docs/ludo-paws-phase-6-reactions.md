# Ludo Paws Phase 6 — Behavior Reactions

Phase 6 makes Starter Paws react to actual Ludo events without moving any game authority into the character layer.

## Reactions

The pure `LudoPawsReactionEngine` compares the previous and current `MatchSnapshot` and emits presentation-only cues for:

- six rolled;
- third consecutive six / lost turn;
- opponent capture;
- being captured and returned to the yard;
- landing on a safe cell;
- reaching home;
- roll with no legal move;
- victory;
- defeat.

The detector never calls `roll`, `move`, random generation, proof APIs, persistence, or mutation code.

## Visual behavior

`LudoPawsReactiveBoardView` wraps the proven Phase 5 board. The original board remains responsible for geometry, token selection and animal pawn rendering. A transparent reaction layer draws short pulse/glow/spark effects around the affected pawn. It uses the same 15x15 Ludo geometry and the same player perspective rotation.

## Voice behavior

`LudoPawsVoicePlayer` uses Android's on-device `TextToSpeech` service for short character reactions. The four Starter Paws receive different pitch/rate profiles from their existing personalities and species-flavored phrases such as Quack, Chirp, Snuffle and Baa.

Voice playback:

- obeys the existing Sound setting;
- selects only the highest-priority simultaneous reaction to avoid overlapping speech;
- fails silent if TTS is unavailable;
- is presentation-only and has no effect on game results.

This is the runtime voice/reaction foundation. Recorded animal voice packs can later replace or augment TTS without changing the reaction engine contract.

## Safety boundary

Unchanged by Phase 6:

- native dice/randomness derivation;
- dice outcome rules;
- legal moves;
- capture rules and safe-cell rules;
- turn advancement and third-six handling;
- winner determination;
- proof/history/fairness material;
- saved-match schema;
- online protocol and Cloudflare API.

## Verification

CI includes a Phase 6 static gate plus Android unit tests, release lint, debug/release APK builds, AAB build, Android 16 target verification and CodeQL.
