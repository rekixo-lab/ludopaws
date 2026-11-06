# Full next-update branch source recheck

Scope: all 90 files changed by the four feature commits from baseline `4f15ac2e` through `a19c4622` on `codex/next-update-comeback-bonus`, followed by a review of the fixes below. No tests, Gradle/build, app/device execution, CI, deployment, merge or Play release were run. Unrelated untracked local screenshots/audit directories are excluded from the commit.

## Findings fixed

- **Rewards account ownership:** quote confirmation now captures its originating account. Daily/spin/collection actions and bonus-ticket requests check that account again at the repository boundary. A background account switch cannot silently direct an old action to the newly current account. Native review eligibility is likewise checked again after Play's asynchronous response.
- **Midnight and week rollover:** an open Rewards hub refreshes when its displayed reward/week window ends. It defers while a spin/ad/request is active, clears stale server status before retrying, and automatically refreshes after the server rejects an old day. An offline failure does not leave a one-second stale-status request loop. An already detached wheel is not asked to deliver an animation completion callback.
- **Current server responses:** Rewards previously captured a wallet before awaiting collection catalog lookups. It now rechecks the active session and takes the wallet/ownership/quotes together after all catalog awaits, covering concurrent purchases, logout and deletion without changing atomic claim/purchase receipts.
- **Offline share artwork:** engine snapshots contain token state but no presentation character IDs. The share action now explicitly attaches the actual per-seat animal selection before drawing the card, instead of falling back to Dog for every local result. It still uses the latest snapshot after rematch.
- **Quick Match saves:** restore now applies the same full canonical invariant policy as live session transitions. Quick Match additionally requires exactly two players and both inactive slots at home; current comeback counter count/range is checked. Valid legacy rulesets keep their existing rules and proof material.
- **Ad callbacks:** removed a duplicated rewarded-ad quiet-period update. Dismiss/failure/exception completion is delivered at most once, while a legitimately late earned callback remains independent. Late callbacks for locally deleted accounts do not recreate pending reward hints.
- **Prompt ownership:** a release from a stale/non-owning prompt no longer clears or quiets another prompt's reservation.
- **Older match continuity:** standard-v6 saved/remote matches retain the trusted turn timer and single-move automation alongside v7, rather than being omitted from the version allowlists.

## Review coverage

Reviewed comeback capture/stranded detection, two missed rolls/third entry, unchanged dice, extra-turn exclusion, normal sixes, partner handoffs, client/server version negotiation, bots/auto moves, proof binding, pending moves and save restore. Reviewed completed-result eligibility in local/remote modes, Exit/Back/rematch callbacks, Remove Ads ownership, load/retry/expiry and app-wide cooldown. Reviewed daily/spin receipts, SQL uniqueness/transactions, bonus tickets and signed SSV, delayed recovery, wallet revisions, account ownership/deletion, collection missing-item prices, unchanged purchased quantities, new board registrations/manifest/renderers, local Quick Match setup/rematch, review/referral/saved-item budget, share provider grants/inputs, link safety/host approval/current-match preservation, browser fallback and weekly deduplication.

Re-read every fix and its callers after editing. Parsed all 21 changed JavaScript/XML/JSON source/config files (`node --check`, XML/JSON parsing) and reviewed diff whitespace with `git diff --check`. These are source/structure checks only. Existing test source was read; no tests were executed or added for this pass.

## Remaining release evidence

Device-build follow-up: the user's local Kotlin compile exposed an incorrect `ReviewManagerFactory` import (`com.google.android.play.review`). Inspected the pinned review 2.0.2 AAR's `classes.jar` directly: `ReviewManagerFactory`, `ReviewManager` and `ReviewInfo` are in `com.google.android.play.core.review`. Corrected the import and rechecked all review imports; no dependency/version change or local tests/build was performed. The reported downstream task type/method errors originate from the unresolved factory. A fresh user-side build is still needed to confirm compilation.

Kotlin compilation, dependencies, real ad inventory/consent/SSV behavior, device sharing targets, cold/warm verified links, accessibility/layout, gameplay/proof conformance, save recovery, midnight/concurrency and backend/client integration still require an explicitly authorized build/test/device pass before release. This source-only recheck does not certify runtime success or a bug-free branch. Existing detailed handoffs: `next-update-comeback-bonus.md`, `next-update-post-match-ads.md`, `next-update-growth-recheck.md`, `next-update-sharing-weekly-recheck.md`.
