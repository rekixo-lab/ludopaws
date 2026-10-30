# Living character behavior and independent pawns

Price changes and publishing remain PENDING. This work changes local presentation only.

## Findings

The existing roster already had anatomical reaction profiles and per-pawn life instances. However, idle base animation differed only by a 173 ms phase offset; match-wide victory/defeat assigned identical cues and start times to every token. Idle appendage channels were not faded when a reaction took over. Shop previews and board pawns used the same models but had no shared expanded behavior layer.

## Implemented

1. Add a bounded secondary behavior profile for every shipped character. Gestures include quiet holds and smoothly eased listening, glancing/sniffing and anatomical flourishes. Reuse the existing blink, gaze, breathing and per-pawn life clock; no extra rendering thread or geometry.
2. Give each token a stable independent idle duration and gesture sequence. Seeds are local presentation data, not gameplay randomness.
3. Apply the behavior layer to match pawns and live Shop/promotion previews. Cached static avatar images remain still.
4. On group victory, distribute spin/cheer/excited gestures; on group defeat, retain sad/defeat semantics. Offset starts by 0/90/180/270 ms and vary durations and anatomical accents. The assignment is deterministic and keyed by the event.
5. Keep token-specific capture/home cues and their contact timestamps. Do not duplicate voices or change the audio scheduler. Delayed cues cannot play before their scheduled start or suppress idle early.
6. Fade idle ear/tail/wing/arm channels when an action takes over. Preserve whole-turn yaw, movement path, foot-contact channels, hit testing, authoritative state and adaptive render cadence.
7. Clamp Panda's existing posture easing to [0, 1]; regression testing exposed a small floating-point overshoot near a held endpoint.

| Character | Added anatomical emphasis |
| --- | --- |
| Dog | Curious head/neck glance, listening ears, tail flourish |
| Goat | Head dip, ear listening, beard/tail movement |
| Lumi Goat | Slower curious glance, larger listening-ear gesture, tail |
| Duck | Bill/neck glance and wing flourish |
| Cat | Head glance, ear attention, slower tail flourish |
| Donkey | Long-ear listening and tail movement |
| Cock | Neck peck/attention and wing gesture |
| Pig | Sniffing head/neck, ears and curled tail |
| Elephant | Slower head attention, ear fan and trunk/tail flourish |
| Horse | Neck attention, ears and tail |
| Rabbit | Quick sniff/listening ears and small tail |
| Panda | Gentle head/arm gestures alongside its four-paw/standing cycle |
| Monkey | Arm and tail flourishes alongside its head-scratch gesture |

These are procedural stylized gestures, not new recorded animation clips or sound assets.

## Research and application

[Unity's official animation blending guidance](https://docs.unity.com/en-us/engine/6000.6/manual/animation-section/animation-mecanim/animation-animator-controller/animation-state-machines/animation-blend-trees/class-blend-tree) distinguishes transitions from blending and explains aligning normalized foot-contact timing. This project uses its native Kotlin/OpenGL renderer, so the application here is smooth additive secondary motion while leaving the existing shared hop/contact timeline authoritative. Per-pawn stagger, emotional variants and quiet holds are project design decisions rather than claims that the documentation prescribes these specific values.

## Validation

- Kotlin app and test compilation succeeded.
- 55 focused Android unit tests passed, zero skipped/failures: anatomical profile coverage, deterministic independent pawn gestures, group cue/stagger choices, token-specific contact timing, finite accents across all 13 characters/cues/variants, full-spin endpoints, idle transitions, hop continuity, Panda/Monkey registration, Panda stance, reaction engine/director, render cadence and catalog checks.
- Content registry, character schema/domain/store, animation/FX, audio, game moments, offline integration, pawn rendering, quick chat, reaction director and situational reaction source gates passed.
- No APK packaging/install, lint sweep, physical-device animation acceptance, production deployment or publishing performed for this change.

## Device acceptance still needed

Use four identical characters in CPU/Pass & Play and an online match. Observe independent quiet holds and gestures; trigger a six, capture, home arrival, victory and defeat. Confirm emotional consistency, staggered result gestures, exact capture contact, no feet sliding or clipping, and acceptable frame pacing. Check all 13 characters in live Shop previews. Real-device visual quality and performance remain unverified until these checks are performed.
