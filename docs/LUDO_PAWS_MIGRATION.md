# Ludo Paws migration plan

## Approved publisher identity

- Parent/publisher brand: **Rekixo**.
- Android application ID for the first Play Store release: `com.rekixo.ludopaws`.
- Game website target: `https://ludopaws.rekixo.com`.
- API target: `https://api.ludopaws.rekixo.com`; do not switch clients until Cloudflare Custom Domain, TLS, health/readiness, and game-flow tests pass.
- The Android application ID, Gradle namespace, and Kotlin/Java source packages now use com.rekixo.ludopaws. The existing Cloudflare Worker name, Durable Object bindings, storage keys, and proof/ruleset identifiers remain unchanged for compatibility.


### Android package migration record (13 October 2026)

The Android application ID, Gradle namespace, Kotlin/Java source package declarations, and source directory roots use com.rekixo.ludopaws. Internal class names such as LudoProofApplication and LudoProofTheme remain unchanged; backend Worker names, storage keys, and fairness/proof protocol identifiers are not part of this package rename.

## Phase 0 baseline

- Source repository: `rajkumarlahare/ludoproof`
- Frozen source commit: `c93396169b9178a0e1a5f96894000509ab20e3a9`
- Migration branch: `feat/ludo-paws-phase-0-baseline`
- Product brand: **Ludo Paws**
- Working store title: **Ludo Paws: Talking Animals**
- Tagline: **Roll. Race. Roar!**

This baseline intentionally separates user-visible branding from identifiers that currently participate in package identity, storage, deployed Cloudflare resources, compatibility, or proof/fairness behavior.

## Safety guardrails

The following must not be changed by a cosmetic/branding phase without a dedicated compatibility migration and tests:

- Android `applicationId` is now approved as `com.rekixo.ludopaws`; the Gradle namespace, Kotlin/Java package declarations, and source directory roots now use com.rekixo.ludopaws.
- Existing SharedPreferences/storage namespaces and persisted-state keys.
- Cloudflare Worker/Durable Object bindings such as `LUDOPROOF_MATCHES`, `LUDOPROOF_API_GATE`, `LUDOPROOF_LEADERBOARD`, matchmaking, friends, receipts, team matchmaking, and social bindings.
- Existing Durable Object migration tags/classes.
- Proof/randomness identifiers and transcript material, including current `ludoproof` application/protocol identifiers and ruleset IDs.
- native secure dice receipts, replay handling, fairness history and verification.
- Server-authoritative legal move, capture, safe-cell, exact-home, turn, timeout/reconnect, and winner semantics.
- Existing local/remote match compatibility unless a dedicated schema version is introduced.

Characters, reactions, animation, audio, cosmetics, and presentation must never influence dice outcomes or legal gameplay decisions.

## Current architecture inventory

The current tree is feature-first and already contains the foundations needed for Ludo Paws:

- Core app/lifecycle, network, shared UI/theme/dialogs.
- Shared game models, board, dice, and result art.
- Home, online, offline, proof, settings, store, profile, friends, leaderboard, mode routing, and Team Up features.
- Offline game engine with native SecureRandom dice and local receipt/history checks.
- Online Cloudflare authoritative match state, matchmaking, friends/social, leaderboard, receipts, Team Up, realtime state, and proof integration.
- Google Play billing/cosmetic inventory infrastructure.
- CI, CodeQL, Cloudflare deployment, and offline-v4 conformance workflows.

### Important latest-main delta

The frozen baseline already includes a lifecycle-aware `GameMusicController`, a real background music resource, redesigned settings controls, Privacy Policy UI, and settings contract tests. Therefore the later Ludo Paws audio phase must **extend** the existing music lifecycle/audio-focus implementation instead of replacing it blindly.

## Naming classification

### User-visible names to migrate to Ludo Paws

Examples include:

- Android launcher label and visible app title.
- Home/store/profile/friends/leaderboard/setup/game/result/dialog copy.
- Share subject/text and chooser labels.
- Privacy Policy visible product references.
- Marketing-facing documentation and future store listing copy.
- Future icon/splash/logo resources.

### Internal compatibility names to preserve initially

Examples include:

- `com.rekixo.ludopaws` package/application identity.
- `LudoProofApplication`, `LudoProofTheme`, and other internal class names unless renaming is done as a separate mechanical refactor.
- `LUDOPROOF_*` Worker/Durable Object bindings.
- Existing API base URL/config property names.
- Existing ruleset/proof/fairness identifiers.
- Existing local persistence keys.
- Existing deployment/service names.

Internal renames are not required for the player to experience a complete Ludo Paws brand and can create unnecessary compatibility risk.

## 16-phase implementation roadmap

### Phase 0 — Baseline freeze and migration audit

Freeze exact source SHA, classify visible vs compatibility-sensitive names, preserve proof/server/storage contracts, and record the implementation roadmap. No gameplay behavior changes.

### Phase 1 — Ludo Paws brand foundation

Replace user-visible LudoProof branding with Ludo Paws, centralize user-facing strings/resources where practical, update share/privacy/launcher/UI copy, and establish brand constants without changing proof/server identity.

### Phase 2 — Animal asset production system

Define production asset conventions for pawn faces, portraits, full-body art, expressions, WebP optimization, short OGG SFX/voices, lazy loading, and memory limits. Ship one complete starter asset pack before scaling.

### Phase 3 — Character domain system

Introduce `AnimalCharacter`, species, personality, `AnimalPack`, voice-set and animation-set definitions plus safe persisted selection. Keep character identity separate from Ludo color identity.

### Phase 4 — Starter pack and selection UX

Implement one polished starter pack end-to-end, selection UI, persisted choice, Pass & Play per-player selection, CPU/default assignment, and safe fallback behavior.

### Phase 5 — Animal pawn rendering

Replace generic board pawn rendering with compact animal-face/head pawns plus color rings, shadows, legal-move/current-turn states, yard sizing, overlap handling, and preserved hit testing/perspective behavior.

### Phase 6 — Player character panels

Add responsive player portrait rails/cards with name, color/team, turn state, progress, network state where relevant, and reaction display areas for 2/3/4-player modes.

### Phase 7 — Game moment detection engine

Create a pure state-diff/event layer shared by offline and online. Events include turn start, roll/six, bad-roll streak, yard exit, move, capture made/captured, safe reached, home reached, no legal move, third-six forfeit, lead pressure, idle, win/loss, and team results.

### Phase 8 — Reaction director, priority and deduplication

Generate stable reaction keys (`matchId + eventIndex + reactionType + playerId + tokenIndex` where applicable), deduplicate realtime/reconnect repeats, add cooldowns, queueing, interruption, and deterministic priority/order.

### Phase 9 — Production audio engine

Extend the existing music/audio-focus system with low-latency game SFX/animal voices (SoundPool where appropriate), voice/SFX/music separation, haptics/reduced-motion settings, lifecycle safety, and species/personality-specific voice sets.

### Phase 10 — Animation and FX engine

Add dedicated FX/reaction overlays instead of bloating `LudoBoardView`; implement idle/blink, six bounce, happy/angry/sad/nervous reactions, safe glow, home stars, paw trails, capture impact, capture return-to-yard, victory/confetti, and defeat sequences.

### Phase 11 — Complete offline integration

Finish Computer and Pass & Play with the same character/reaction/audio/FX stack; verify six/exit/capture/safe/home/third-six/no-move/CPU/win/loss/resume behavior. Character personality remains presentation-only and cannot bias CPU/dice rules.

### Phase 12 — Online and realtime integration

Feed authoritative online `MatchSnapshot` updates through the same moment/reaction engine, handle reconnect/revision updates/pending rolls/opponent actions/result transitions, and prove duplicate-free reactions.

### Phase 13 — Online character identity schema

Add backward-compatible cosmetic identity metadata such as `equippedCharacterId`, validate/fallback server-side, preserve old clients, and keep cosmetic metadata out of proof/randomness state unless a reviewed protocol revision explicitly requires it.

### Phase 14 — Animal store and progression

Add `CHARACTER_PACK` (or equivalent) to cosmetics/inventory/store UI. Support free/level/gem/reward/event packs while preserving purchase integrity. A character pack includes its art, reactions, animation set, and voice set.

### Phase 15 — Social, Fair Dice UX, accessibility and production hardening

Show equipped animals in Friends/Profile/Team Up where useful, present the existing proof system as friendly **Verified Fair Dice** with advanced proof details available, finish accessibility/responsiveness/performance/memory/audio lifecycle/backward-compatibility QA, and pass release gates.

## Required behavior matrix

The finished system must cover at least:

| Game moment | Visual behavior | Audio behavior |
| --- | --- | --- |
| Roll 6 | excited jump/face | species/personality excitement |
| Capture made | confident attack/celebration | roar/laugh/tease style reaction |
| Character captured | hit/shake/sad or angry state; return sequence | whimper/growl/squeak style reaction |
| Safe cell reached | relief/glow | short relief sound |
| Home reached | stars/celebration | home celebration |
| Repeated poor rolls | controlled frustration (cooldown) | short frustrated reaction |
| Third consecutive six | shocked/annoyed comic reaction | disappointment cue |
| Idle player | subtle idle behavior only after cooldown | optional low-frequency impatient cue |
| Opponent materially ahead | worried/nervous expression | optional nervous cue |
| Win/team win | victory sequence/confetti | victory voice/SFX |
| Loss/team loss | defeat/funny-sad sequence | defeat reaction |

## Testing requirements

Every implementation phase must preserve a green baseline before advancing. New tests will be added for:

- reaction event derivation;
- event ordering and priority;
- realtime/reconnect deduplication;
- capture made + captured pairing;
- capture return animation state;
- home + final-win ordering;
- six/extra-turn and third-six behavior;
- no-legal-move transitions;
- offline/online event parity;
- CPU mode reaction parity;
- Team Up result behavior;
- settings and voice/SFX/music combinations;
- lifecycle/background/restore behavior;
- asset fallback/corrupt selection;
- online old-client/default-character compatibility;
- store acquisition/selection persistence;
- low-memory/resource cleanup.

## Phase execution rule

For every phase:

1. Create/update an isolated feature branch.
2. Make only the phase-owned changes plus required compatibility scaffolding.
3. Run/build the relevant Android and/or server tests.
4. Run regression/conformance checks for proof/server-sensitive areas when touched.
5. Review the diff for accidental protocol/storage/package renames.
6. Merge only after the phase exit gate is satisfied.

The migration must never trade game integrity for visual polish.