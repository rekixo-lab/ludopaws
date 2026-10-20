# Phase 4 — Retention, Onboarding, Accessibility and Responsible Growth

## Baseline

Ludo Paws already has a character identity catalog, animal cosmetics, gameplay effects, audio, level/XP, six badges and a Profile badge collection. Phase 4 extends those existing systems instead of duplicating them or changing fair dice / gameplay rules.

## Implemented

- Nine **deterministic, cosmetic-only** achievement badges: the existing six keep their original IDs and unlock thresholds; Couch Champion, Returning Paws and Game Night add inclusive offline/sustained-completion goals. Numeric progress is clamped (never negative or above the goal).
- Dedicated Badges screen shows numeric progress and explicit accessibility descriptions. Profile's compact preview shows only four relevant badges instead of squeezing nine tiles into the old six-wide row.
- First-run Welcome guide on Home (only for profiles with no completed game), optional with "Not now", "Got it", and "Practice with Robot"; first-run state stores a single local boolean with no account ID, DOB, location or network call. Settings provides a repeatable How to Play entry.
- Reduce motion preference in Settings: decorative selection-button press animations are skipped; completion handlers still run. Android system-level disabled animator setting is honored. Essential dice and pawn state presentation is unaffected.
- Achievements grant no currency, do not require daily app check-ins, do not change purchases, no rating prompts, no ads, no child profiling, no social pressure or notifications.
- Existing character selection, dice/audio/animations, XP and purchases remain as-is. No invasive rewrites to the authoritative engine.

## Nonfunctional release checks

- Android unit tests cover badge thresholds/progress and reduced-motion defaults.
- CI must pass on the exact Phase 4 head, including Android lint/APK/AAB, emulator instrumentation, server tests, CodeQL where its trigger applies, release readiness and Cloudflare dry-run.
- Existing Phase 1 age lockdown and Phase 2 reconnect/voice/monetization protections must remain intact; no independent merge to main or production deployment.
- Real-device first-run landscape/portrait/font scaling, TalkBack navigation, reduced-motion transitions, game result persistence and account switches require manual signoff.
- Do not treat a badge count as retained users, crash-free sessions or match-completion rate. Reliable live product telemetry would need separate privacy and age-policy approval; no analytics SDKs or user identifiers are added.

## Rollback

Revert Phase 4 branch/PR independently of the stacked Phase 3, Phase 2 and Phase 1 branches.
