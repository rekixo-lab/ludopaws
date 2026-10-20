# Ludo Paws — Phase 1–4 combined code and production release audit
Date: 2026-10-20

## Scope and immutable integration order

Baseline `main`: `da08eeb8a8bb8fe3ff2ca9a3b30e406464824099`.
Stacked reviewed changes: #295 (Phase 1 security) → #296 (Phase 2 reliability) → #297 (Phase 3 modular architecture) → #298 (Phase 4 retention/accessibility). Combined changes from baseline: 37 commits across 66 files before audit fixes.

The present final-audit branch is a **cumulative verification branch**. It must not be merged into `main` just because CI is green; it intentionally includes a lockdown release candidate and is not a fully verified multiplayer release.

## Findings and remediation

### SEC-01 (fixed in audit branch): fail-open safety guard on absent environment variable

The deployed outermost Worker had:
```js
if (env?.LUDOPROOF_AGE_SAFETY_MODE !== "LOCKDOWN") return null;
```
A missing, mistyped or accidentally changed runtime var would bypass the age-safety guard entirely even though there is no approved, server-verifiable age authorization protocol. In a safety-sensitive release this is fail-open.

**Resolution:** Always deny restricted match/matchmaking, Friend/social/voice proxy, leaderboard, wallet, store purchases and SSV routes regardless of missing/malformed flag. Account recovery, deletion, login, health and readiness remain reachable. The audit tests exercise both `ageSafetyBlockResponse` and the actual outer `index-account.js` Worker fetch with malformed/missing flags, forged age headers and bearer tokens.

**Future unlock is forbidden** until a distinct, reviewed trusted account/session-bound eligibility authority is implemented and independently tested. A build flag, DOB input, client assertion, Play Age Signals value or unbound Integrity token is insufficient.

### PRIV-02 (fixed in audit branch): unused permissions in offline Android package

The original Android manifest declared `RECORD_AUDIO` and `com.google.android.gms.permission.AD_ID` even though the locked-down Android build cannot access online voice, advertising or monetization. The manifest now has variant-controlled merger removal rules. Debug and release CI steps inspect the **actual packaged APK** with aapt and fail if either permission reappears. The signed-release workflow includes the same assertion. A future approved connected release must reconfigure these permissions only after privacy/consent review.

## Review coverage and CI evidence

- Source-level review covered the combined diff and source of the phase-owned Worker routes, age guard, user moderation, friend conversation, match input validation, state/wire modules, Android mode routing, realtime voice and retry policies, exit ad gate, badges, onboarding, settings, and deployment configuration.
- Separate source-pattern checks inspected the principal modified Kotlin and JavaScript files for TODO/FIXME, temporary bypasses, obvious sensitive logging and silent catch patterns; a serial promise tail was found to deliberately swallow **only the stored queue-chain rejection** so subsequent operations can continue. This pattern is not itself a bypass.
- Existing extensive test suites cover match roll idempotency, dual transport replay, Team Up, match-stale receipt protection, Friend block/report/evidence, account deletion, rate limiting, offline policy, animation/audio and Android lifecycle.
- Earlier green final-head CI: Phase 1 `39346957036`, Phase 2 `39349352147`, Phase 3 `39353508058`, Phase 4 `39355871353`. Their green status is **not a substitute for tests on this cumulative audit head**.
- Audit head must independently pass the full CI workflow, Release Readiness and cumulative CodeQL. CodeQL Kotlin may not run on PR events and must not be assumed green if only JS CodeQL runs.
- Review all relevant account, matchmaking, product, moderation and privacy policies again before publishing; unit tests cannot certify those external obligations.

## Release go/no-go matrix

| Gate | State | How to close |
| --- | --- | --- |
| Combined automated CI on final audit SHA | Await audit PR checks | CI must be green on exact SHA |
| Production Cloudflare config and secret readiness | Config checks automated; live environment unverified | Run approved preflight and /health + /ready + smoke with reviewer |
| Signed AAB + Play Console internal build | NOT VERIFIED | Use production signing keys, upload to internal track, validate version and device install |
| Trusted server-issued account-bound age access | NOT IMPLEMENTED | Independently designed and reviewed authorization service |
| Live multiplayer voice/nonfriend moderation and immediate session/token revocation | NOT IMPLEMENTED / NOT VERIFIED | Enforce live-game revocation and tests before enabling social |
| Real Android devices 2P/4P/Team Up + poor networks, TURN/NAT, reconnect and lifecycle | NOT VERIFIED | Sign manual real-device test matrix |
| Account deletion, UGC, Data Safety, ads/billing, target audience and notices | Publisher legal/policy review required | Play Console checklist and independent policy approval |
| Crash-free, D1/D7 retention, match completion rate | Unavailable prelaunch | Privacy-reviewed instrumentation after staged release |

## Safer launch options

1. An **offline-only** signed candidate can be considered for internal testing once the Android package, privacy declarations and store metadata pass independent tests; remote modes and paid features deliberately remain disabled.
2. A **full online multiplayer production launch is NO-GO** until account-bound age eligibility, live moderation/revocation and device/policy signoff exist.
3. No background deployment or merge to `main` was authorized by an automated test result; require release-owner review and a tested rollback plan.

## Regression / rollback

Revert the cumulative audit hardening separately if unexpected issues emerge; never unlock connected features simply to make smoke tests pass. A production release must use an exact tagged commit, release signing, checked permissions, and a documented rollback.
