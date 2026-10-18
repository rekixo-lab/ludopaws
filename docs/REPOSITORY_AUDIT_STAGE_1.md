# Repository Audit — Stage 1

**Date:** 2026-10-15  
**Repository:** `rajkumarlahare/ludopaws`  
**Audit branch:** `dev/workbench` (PR #271 targets `main`)  
**Purpose:** Source/dependency review before any production deployment, public release, or monetization activation.

## Scope and baseline

The original branch comparison was `main` at `ddf00c6b121319ca407448bca6885c7afd4813f3` against `dev/workbench` at `d2aa3f00a300d22bd4db292fb01a6328dc148a71`: 113 commits ahead, zero behind, with 39 changed files in PR #271 at that point. The review covered the PR diff and key implementation/configuration paths for Android identity and manifest/resources; local and online/offline gameplay contracts; store catalog and cosmetic renderer availability; wallet, Billing, and AdMob SSV; account/session and deletion routes; Durable Object bindings/migrations; domain configuration; workflows and tests.

No production Worker was deployed, no public website/legal page was published, no Play Store release was made, and Billing/Ads were not enabled by this audit.

## Findings register

### AUD-01 — Consumer-brand check referenced the pre-migration Android source tree
- **Paths:** `scripts/check-ludo-paws-brand.mjs`; `android/app/src/main/AndroidManifest.xml`; `android/app/src/main/res/mipmap-anydpi-v26/ic_ludo_paws_launcher.xml`
- **Risk:** The check scanned obsolete `com/ludoproof/game` paths and required drawable icon references that no longer matched the Manifest, causing a false CI failure and failing to scan the actual Rekixo namespace.
- **Disposition:** Fixed on `dev/workbench`: scan the `com/rekixo/ludopaws` source tree, check the current mipmap launcher references, and verify the adaptive icon resource exists.
- **Verification:** The Android consumer-brand gate passed on a subsequent CI run; the latest full CI must still pass before this stage is closed.

### AUD-02 — Existing dice cosmetics were changed to locked placeholders
- **Paths:** `android/app/src/main/java/com/rekixo/ludopaws/feature/store/domain/StoreCosmeticCatalog.kt`; `android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/DiceStylePalettePolicy.kt`; `android/app/src/test/java/com/rekixo/ludopaws/feature/store/StoreCatalogParserTest.kt`; `android/app/src/test/java/com/rekixo/ludopaws/feature/store/domain/StoreCosmeticCatalogTest.kt`
- **Risk:** `dice_pumpkin` had been made unavailable although it was a free, owned, renderer-supported style. The same change marked `dice_heart` unavailable despite a supported palette and catalog-parser expectation. The regression caused two Android unit-test failures.
- **Disposition:** Restored the pre-existing dice catalog block from `main` to `dev/workbench`, preserving the supported styles and existing IDs rather than weakening assertions.
- **Verification:** The next full Android unit-test run must confirm both failures are resolved; the final CI result is authoritative.

### AUD-03 — GitHub Actions artifact quota blocked otherwise successful server validation
- **Paths:** `.github/workflows/ci.yml`; `scripts/generate-sbom.mjs`
- **Risk:** SBOM generation and server tests passed, but artifact upload failed because the GitHub Actions storage quota had been reached. This made the PR workflow fail for an external storage-capacity reason.
- **Disposition:** PR validation tolerates artifact-upload failure only for pull-request events and reduces evidence retention periods. Push/release uploads remain strict. This is a CI availability mitigation, **not proof that artifacts uploaded successfully**.
- **Verification:** The server job passed with this workflow change. Release/push runs still require available artifact storage to preserve release evidence.

### AUD-04 — PR-run concurrency used GitHub's synthetic merge ref
- **Path:** `.github/workflows/ci.yml`
- **Risk:** An older, long-running emulator job delayed validation of the newer PR head.
- **Disposition:** CI concurrency is keyed to the PR source branch, so updates to a PR supersede prior validation on that source branch.
- **Verification:** A run on the latest audit commit must complete successfully; stale runs may be cancelled by concurrency.

### AUD-05 — Store purchase IDs and fulfillment must remain consistent
- **Paths:** Android Billing gateway and purchase verifier; `server/src/google-play-billing.js`; store purchase tests and `docs/GOOGLE_PLAY_ONE_TIME_BILLING_SETUP.md`
- **Risk:** A mismatch between app product IDs, Google Play configuration, server verification, and the purchase ledger can cause a valid purchase to fail or an invalid purchase to be granted.
- **Disposition:** Keep the current one-time purchase contract aligned across Android, Worker verification, Play Console products, and tests. Preserve the existing product IDs; never trust a client-reported price or purchase result.
- **Verification:** Repository tests cover the implementation contract. Live Play Console configuration and a signed internal-test purchase still require external acceptance testing.

### AUD-06 — Public privacy/deletion pages are correctly gated, but not approved for publication
- **Paths:** `docs/privacy-policy.html`; `docs/account-deletion.html`; `docs/landing-page.html`; `.github/workflows/account-deletion-pages.yml`; `scripts/check-account-deletion-pages.mjs`
- **Risk:** The privacy policy retains `POLICY_RELEASE_REVIEW_REQUIRED`. The Pages workflow also requires explicit publisher approval, a valid Google Web OAuth client ID, a monitored support email, and the exact account-site origin.
- **Disposition:** Keep publication gated. Confirm the policy against actual data flows and define retention/deletion treatment for match receipts/proofs, purchase records, social records, and account identifiers before approval.
- **Verification:** Account-deletion source checks passed. That check is not a substitute for a deployed end-to-end deletion test or publisher/legal approval.

### AUD-07 — Live production configuration is not verified by repository tests
- **Paths:** `server/wrangler.json`; `.github/workflows/deploy-cloudflare.yml`; `.github/workflows/android-signed-release.yml`; `scripts/cloudflare-deploy-preflight.mjs`; `scripts/production-game-smoke.mjs`
- **Risk:** Static tests and the Wrangler dry-run validate source/config shape, not current Cloudflare secrets, live DNS/TLS/CORS, Google Play Console products and service-account permissions, AdMob console setup, or a live signed production flow. The native dice implementation still requires internal testing and must not be represented as independently audited or certified.
- **Disposition:** Do not infer production readiness from CI alone. Verify live configuration and acceptance-test flows under a separate approved release procedure; do not dispatch production deployment as part of this audit.
- **Verification:** Security checks, runtime-config/preflight tests, account-deletion source checks, server tests, and Worker dry-run passed on the audited CI revision.

### AUD-08 — CodeQL is skipped for this private repository by workflow policy
- **Path:** `.github/workflows/codeql.yml`
- **Risk:** Both JavaScript and Kotlin CodeQL jobs are skipped unless the repository is public or `LUDOPROOF_CODEQL_PRIVATE=true`. The custom security gate still runs in CI, but the run is not a CodeQL result.
- **Disposition:** Confirm private-repository CodeQL eligibility/licensing and configuration before treating CodeQL as a required security check.
- **Verification:** Inspect the CodeQL workflow run conclusion separately; a skipped run must not be reported as a pass.

### AUD-09 — Stable identifiers and persistence boundaries must remain migration-protected
- **Paths:** `android/app/build.gradle.kts`; `server/wrangler.json`; `server/src/account-security.js`; `server/src/account-directory.js`; `server/src/player-account.js`; `server/src/store-purchase-ledger.js`; `server/src/store-catalog-store.js`; `server/src/index-forfeit.js`
- **Risk:** The system combines account/session tokens, guest recovery/deletion tombstones, wallet/purchase ledgers, catalog revisions/snapshots, match receipts, proof identifiers, and named Durable Objects. Casual renaming or storage-shape changes can break existing users or deployed state.
- **Disposition:** Preserve `com.rekixo.ludopaws`, existing Worker/DO binding names, SKU IDs, ruleset/proof identifiers, and storage keys unless a separately tested migration is approved.
- **Verification:** The security gate, server tests, release-readiness gate, and Worker dry-run cover a subset of these invariants; live legacy-state compatibility still requires deployment-compatible migration testing.

## Gate summary

- **Source fixes made on this branch:** outdated brand gate and dice-catalog regression.
- **CI workflow fixes made on this branch:** artifact-quota handling for PR validation and PR-source-branch concurrency.
- **Not green by definition until separately resolved:** publisher/legal approval and public deletion verification, live Cloudflare/Play Console/AdMob configuration, artifact availability for push/release evidence, and CodeQL eligibility.
- **Release restriction:** A green CI is necessary but not sufficient for production release. Keep production deployment and monetization opt-in flags disabled until the applicable external and end-to-end gates pass.
