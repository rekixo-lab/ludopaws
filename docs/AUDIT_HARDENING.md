# Audit hardening — 27 October 2026

Audited baseline: `16117b823348d5bdc89cc8666ffcaa7b2be3871c`.
This change implements the actionable source fixes from the 24-finding audit.
“Implemented” below refers to code/tests, not an assertion that a commit has
been deployed or that every phone/country has passed acceptance.

| Finding | Disposition and evidence |
|---|---|
| A01 | Purchase ledger persists provider-verified delivery authorization and arms a Durable Object alarm **before** consume/acknowledge. A binding-only wallet delivery path uses the same idempotency key without requiring a surviving device session. Alarm retries survive eviction/wallet outage; refund/deletion guards remain. Tests cover interrupted completion, recreation, logout, duplicate delivery, refund and deletion. No raw Play token or device session is stored on the server. |
| A02 | Publisher requests invalidate only their rejected cached token on 401 and refresh once. A second 401 fails boundedly; 403 never causes a token-refresh loop. Refund reconciliation uses the same helper. |
| A03 | Concurrent billing token refreshes share a credential-scoped promise; failures release the promise. Invalid/empty tokens and invalid lifetimes are rejected, cache lifetime is capped. Eight-way concurrency regression test requires one OAuth call. |
| A04 | Started-Activity tracking with a short background debounce replaces resumed-Activity counting. Account-switch/privacy gates are retained. Pure lifecycle tests cover overlapping navigation, recreation, actual background and returning. |
| A05 | Offline checkpoints use a latest-write-per-mode queue. One running write plus at most one newest pending write per mode replaces unbounded historical writes. Clear and save share ordering. |
| A06 | Online cache uses the same bounded queue; new/clear intent replaces stale pending work. Existing in-memory snapshot and authoritative-server boundaries remain. Burst, clear and write-failure tests cover queue behaviour. |
| A07 | Store coalesces refreshes per frame, skips equal rendering state, retains its window/root and same-tab scroll. Profile skips equal state and replaces identity/status sections while retaining the scroll/stats tree when stats are unchanged. Changed catalog contents may still require catalog rebuilding; a wholesale UI rewrite was intentionally avoided. |
| A08 | The Activity window callback observes taps/keyboard activation without replacing any View listener. The repeated global-layout full-tree scan is removed. Native click/gesture processing remains delegated. |
| A09 | Quick-chat animation additionally observes aggregated visibility and the system animator preference. Detach/generation guards remain. |
| A10 | Slow API requests emit aggregate route-family/timing events, without path IDs/query strings. Global object keys are retained to avoid splitting existing identities/queues. SQL benchmark and query-plan evidence added. Actual distributed capacity and regional p95/p99 still require representative deployment measurements; no unsupported capacity number or automatic sharding claim. |
| A11 | Repeat-safe indexes added for nonce/challenge/history expiry, challenge account lookup, referral account/time lookup, pending awards and delivery due time. Real SQLite EXPLAIN tests verify index usage. |
| A12 | `/health/operations` distinguishes config from delivery/provider health, returns 503 on trouble, checks backlog, recent Integrity quota/provider failures and 24-hour refund freshness. Fifteen-minute best-effort GitHub probes feed this status into the existing scoped Worker 5xx monitor; GitHub failures also follow repository notification preferences. No paid monitor purchased or new alert recipient introduced. |
| A13 | Signed releases reject versions below the live referral minimum; missing active-campaign version information fails closed. Read-only Console recheck confirmed production version 9 still in review and version 10 available internally. Public availability of an eligible version remains an external rollout requirement, not bypassed by a flag. Existing minimum/campaign dates and in-review track are unchanged. |
| A14 | Removed the unreachable RemoteModeEntryActivity and its manifest/import/test listing, plus the unwired turn-feedback policy and tests that only exercised unused code. TeamUp routing and active gameplay presentation remain. |
| A15 | Removed the unused Play Age Signals request/SDK. Retained local-only disabled-feature explanations and family/privacy gates. No age-sharing prompt was enabled. |
| A16 | Hidden proof details are no longer formatted on every update. Server receipts, rulesets and the active offline cryptographic engine remain. |
| A17 | All declared resolvable app configurations are locked; Gradle SHA-256 artifact/metadata verification is enabled. Resolved runtime SBOMs are checked against OSV in CI, signed release and weekly checks. A real Guava advisory match triggered an update to patched 33.7.2-android. Other build/SDK versions are retained where compatible and not shown to require a migration; the deprecated Kotlin compiler-options DSL was updated. |
| A18 | `.node-version` selects Node 22; backend npm pretest gives a clear error on a different major. Validation uses Node 22. |
| A19 | README links the current-status entry point and dated activation evidence. Historic records remain labelled/history-preserving; obsolete RC2 gate wording removed. Existing storage/binding/protocol names are not cosmetically renamed. |
| A20 | Popup close target increased to 48dp. The entire offer is scrollable, including heading/actions, so large text does not leave unreachable content. Popup/primary share introduction/navigation copy moved to resources with Hindi translations and English fallback. Added an isolated emulator test for 200% text/RTL/small viewport. This is not a claim that the entire game's legacy English copy is translated into every rollout language. |
| A21 | Added bounded aggregate Integrity verified/rejected/provider/quota counters, retained for one hour and reported over 15 minutes; no player/device identifiers. Existing self-invite/replay/subject/deletion/match qualification tests remain. Genuine multi-account/device farming is a residual anti-abuse risk, not something Integrity alone can eliminate. |
| A22 | Added failure/recovery/lifecycle/queue/query-plan/advisory/release gate tests. Future signing requires complete CI including isolated emulator checks on the same commit. Physical-device purchases/refunds, thermal/network measurements and multi-country acceptance must not be fabricated from these tests. |
| A23 | Applied and read back main protection: GitHub Actions `server`, `android`, `windows-runner-script` checks required, strict up-to-date checking and linear history; admins included, force pushes/deletion disabled, no PR/review requirement. Release/deploy remain explicit manual workflows. |
| A24 | Duplicate launcher bitmap replaced with a resource alias (same artwork); catalog tests moved from runtime source directory to `server/test`, preserving discovery/imports. |

## Purchase delivery contract

`RESERVED` alone never authorizes background credit. Only a successful Google
purchase validation records `verifiedAt` plus owner/hash/product information and
arms delivery. Publisher consume/acknowledge follows. The ordinary request still
credits immediately; `/delivered` cancels the redundant alarm. If the request
vanishes, the alarm applies the identical wallet transaction independently.
Retrying an older fulfilled ledger also arms the new delivery path lazily.

If finalizing with Google fails after a paid purchase was verified, the durable
credit is still recoverable; a later Google refund/revocation is handled by the
existing reconciler. This does not store provider tokens, override refunds,
recreate deleted accounts, or retroactively discover already-lost historical
purchases that were never recorded by this new delivery path.

## Reproducible validation

```bash
# Use Node 22 from .node-version for backend/script tests.
cd server
npm test
cd ..
node --test scripts/*.test.mjs
node scripts/security-check.mjs
node scripts/android-release-readiness-check.mjs

# Local synthetic SQL only; never contacts deployed storage.
node scripts/benchmark-storage.mjs 10000

cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease \
  :app:bundleRelease :app:writeReleaseDependencyInventory --max-workers=2
cd ..
node scripts/generate-sbom.mjs
node scripts/check-dependency-advisories.mjs

# Isolated CI emulator, without connecting a real phone or production API.
gh workflow run ci.yml --ref <reviewed-branch> -f run_android_instrumentation=true
```

The SQL microbenchmark covers only in-memory lookup cost. At 10,000 synthetic
rows, an example run changed the nonce-expiry count query's p95 from ~0.887ms
to ~0.100ms and EXPLAIN changed from a full scan to a covering-index search.
These numbers are not app FPS, store-open speed, or deployed Worker capacity.

CI also checks GitHub workflow semantics with checksum-pinned actionlint. The
emulator AVD uses disk-backed runner temporary storage, not a potentially small
RAM-backed `/tmp`; unrelated hosted tools are cleaned only if space is low.

## Dependency updates

Normal builds verify existing checksums and strict version locks. Do not solve
a mismatch by switching verification off. After reviewing a deliberate update,
bootstrap expected new entries with:

```bash
cd android
./gradlew --write-locks --write-verification-metadata sha256 \
  :app:resolveAndLockDependencies :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease
```

Review the metadata/lock diff and the official publisher/advisory information,
then run the normal strict build without the write flags. Bootstrap trusts the
resolved publisher artifacts; it is not independent proof of their origin.
OSV matches are a release gate, while a clean result covers only packages and
advisories indexed at that time, not all SDK internals or unknown vulnerabilities.

Guava advisory: [GHSA-xxph-c9ww-hj94](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94).
Its presence did not establish a reachable unsafe-deserialization attack in this
game. [OSV querybatch API](https://google.github.io/osv.dev/post-v1-querybatch/)
is queried using public Maven coordinates only.

## Remaining acceptance boundaries

No new Play upload/track change, physical-device install/reset, legal approval,
real customer payment, regional load exercise or unique-human guarantee is
implied. English fallback remains for untranslated legacy UI. Deploying backend
fixes does not ship the changed Android UI; that belongs in the next reviewed
app update. These boundaries must remain visible in the completion report.
