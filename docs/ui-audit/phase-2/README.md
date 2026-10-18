# Phase 2 — Shared UI Tokens and Action Icons

## Intent

Provide a small reusable design-token vocabulary and semantic Canvas icons, then validate the glyphs at small and large dp sizes. This is a foundation, not a whole-app visual redesign.

## Added

- `AppUiTokens`: base spacing and shape values; common icon-size, touch-target and stroke-width values. Existing `LudoProofTheme` palette remains the color source of truth.
- `AppIconName`: semantic identifiers for sixteen common action glyphs.
- `AppIconVariant`: `OUTLINE` and `FILLED`; brand art and game/character illustrations remain feature-owned.
- `AppIconView`: normalized Canvas geometry with dp-based stroke compensation, dynamic icon/tint/variant properties, and decorative-child accessibility behavior.
- A rendering instrumentation test draws every icon and variant at 16dp and 32dp.
- The in-game back chevron is the first controlled consumer. Its parent keeps the existing content description, click handler, button frame, position and Home glass background.

## Deliberately unchanged

HomeIconView and FriendsIconView are not migrated in this phase, avoiding a large appearance change before screenshot comparison. Settings, Store, Friends interactions, mascot art, board and 3D scene rendering are unchanged. No game rules, native dice receipt verification, server protocol, session state, billing or ad entitlement behavior is changed.

## Validation gates

- Android unit tests and release lint.
- Debug APK, release APK and release AAB assembly.
- Android emulator instrumentation including `AppIconViewRenderingInstrumentedTest`.
- Review the in-game back arrow screenshot against the previous build at the same density and size. If it looks materially different, revert/adjust the icon geometry before any wider migration.
- Review the PR's GitHub Actions final results before merging.
