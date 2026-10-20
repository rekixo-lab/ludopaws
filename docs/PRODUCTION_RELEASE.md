# Ludo Paws Production Release Checklist

> **Current internal candidate: Family V2 / RC3 (20 October 2026).** The publisher authorized configured Play Billing, child-directed ads and optional Google account linking/restore. Voice and free-text remain removed. No startup/gameplay age entry. Follow [current release status](production-readiness/RELEASE_STATUS.hi.md) for build/deploy evidence and physical/Play acceptance; older release instructions below are historical where they conflict.

LudoProof is a production-oriented release candidate. The game backend is authoritative and the Android client cannot select the dice outcome, event index, legal move, capture result, turn advancement, or winner.

## Final publisher identity

- Publisher / parent brand: **Rekixo** (`https://rekixo.com`).
- First-release Android application ID: `com.rekixo.ludopaws`.
- Game website target: `https://ludopaws.rekixo.com`.
- API hostname: `https://api.ludopaws.rekixo.com`.
- The dedicated API hostname passed HTTPS health, readiness, store-status, and live game-flow checks in Cloudflare production deployment run [#50](https://github.com/rajkumarlahare/ludopaws/actions/runs/38352312575) on 13 October 2026. Keep the `workers.dev` host available as rollback/test fallback, but do not use it silently as the Android default. The game website/account hostname is a separate publisher-gated static-site cutover; keep it gated until publisher/legal approval, hosting/DNS/TLS, Privacy Policy, Terms of Service, Pricing & In-App Purchase Policy, public account-deletion routes, and Google sign-in/support configuration are approved and live.
- No production deploy, Billing activation, Ads activation, or release during this migration.

## Release gates

A release must not be promoted unless all of these are true:

1. GitHub Actions is green for the exact release commit.
2. Server unit/integration tests pass.
3. Wrangler production dry-run passes.
4. Android JVM unit tests and `lintRelease` pass.
5. Android debug/release APK variants and the release AAB assemble successfully.
6. SHA-256 release evidence is generated for the exact release artifacts.
7. Cloudflare `GET /ready` returns HTTP 200.
8. `ENTRONEX_API_TOKEN` and `LUDOPROOF_SESSION_HMAC_KEY` exist only as Cloudflare Secrets.
9. The pinned EntroNex Ed25519 key ID, tenant ID, fingerprint, and public key match the independently verified trust material.
10. The Android APK/AAB contains no EntroNex customer credential or signing secret.
11. The game Worker remains isolated from Rekixo/AR3D routes.
12. A real-device multiplayer smoke test covers create, join, start, commit, reveal, move, reconnect, timeout recovery, win, and proof/history display.
13. The final store/upload-signed Android artifact is signed outside the repository with the controlled release key.
14. A fresh-install real-device test verifies guest creation/recovery, Google link/restore, account switching, server wallet/inventory, and a two-device multiplayer reconnect/proof flow.
15. Account deletion and guest delete-only code are implemented in `main` (PRs #231–#237). Do not release until the public legal website (Privacy Policy, Terms of Service, Pricing & In-App Purchase Policy, and account deletion) is published after publisher/legal approval, the Google-linked and guest-code request paths work against the deployed Worker, retries are tested end-to-end across all Durable Objects, guest-uninstall support/ownership verification is active, match receipt/history retention or anonymization is decided, and Play Console's account-deletion URL and Data safety answers are finalized. Track all remaining acceptance criteria in [issue #232](https://github.com/rajkumarlahare/ludoproof/issues/232).
16. Verify the Privacy Policy, Terms of Service, Pricing & In-App Purchase Policy, Google Play Data safety declarations, intended age/child-directed treatment, consent behavior, content rating, store listing, internal test track, and monitored support contact.
17. Keep Google Play Billing disabled until the exact product IDs and displayed quantities/prices match between the Android catalog, backend verifier and Play Console; the service account has only the required Play Console permissions; and licensed internal tests pass for purchase, acknowledgement/consumption, restore, duplicate/retry and refund behavior.
18. Keep AdMob disabled until real App/Rewarded-unit IDs, UMP consent/privacy options and the signed SSV callback are configured and tested end-to-end. With the configured single reward unit, every successful callback must grant 5 gems; cosmetic-targeted ads additionally increment eligible cosmetic progress. Client callbacks must never grant value locally, and duplicate SSV transaction IDs must not duplicate rewards.

## Configuration source of truth

The production deployment now prepares a complete runtime-variable set in the workflow runner before Wrangler dry-run/deploy. Set the required non-secret repository Actions Variables `GOOGLE_SIGN_IN_WEB_CLIENT_ID` and `LUDOPROOF_ACCOUNT_WEB_ORIGIN=https://ludopaws.rekixo.com` first. Optional runtime variables include `GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL`, `GOOGLE_PLAY_BILLING_ENABLED`, `LUDOPROOF_ADMOB_SSV_ENABLED`, `LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID`, and `LUDOPROOF_ADMOB_REWARD_ITEM=gems`. Missing feature flags default to false; an invalid OAuth ID/origin or enabled feature without its required configuration blocks deployment.

Wrangler configuration is authoritative for plain vars, so do not rely on dashboard-only values surviving deployment. The deployment workflow injects these non-secret values into the ephemeral config; it does not commit runtime IDs or credentials. Keep `GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY` in Cloudflare Worker Secrets only, and enable Billing only after that secret exists and internal purchase/restore/refund tests pass. Signed Android release generation uses the same OAuth ID and requires the Android Billing/Ads flags to match the Worker flags and live `/api/store/status`.

## Cloudflare

Worker:

```text
ludoproof-game-api
```

Durable Objects:

```text
LUDOPROOF_MATCHES -> MatchRoom
LUDOPROOF_API_GATE -> ApiGate
LUDOPROOF_LEADERBOARD -> LeaderboardRoom
LUDOPROOF_MATCHMAKER -> MatchmakerQueue
LUDOPROOF_FRIENDS -> FriendDirectory
LUDOPROOF_RECEIPTS -> MatchReceiptArchive
LUDOPROOF_TEAM_MATCHMAKER -> TeamMatchmakerQueue
LUDOPROOF_FRIEND_CONVERSATIONS -> FriendConversation
LUDOPROOF_FRIEND_RECENT -> FriendRecentRoom
LUDOPROOF_STORE_PURCHASES -> StorePurchaseLedger
LUDOPROOF_ACCOUNT_DIRECTORY -> AccountDirectory
LUDOPROOF_PLAYER_ACCOUNTS -> PlayerAccount
```

Internal Worker service binding:

```text
ENTRONEX_SERVICE -> entronex-v4-eval
```

Production LudoProof-to-EntroNex traffic uses this Cloudflare Service Binding instead of routing through the public `workers.dev` hostname. The public base URL remains configured as the canonical request URL and as the local/non-Cloudflare fallback.

The rate gate is sharded by a one-way hash of the Cloudflare client IP plus request scope. Raw client IP addresses are not stored in match state or limiter state.

Source-controlled non-secret variables/trust material:

```text
ENTRONEX_BASE_URL=https://entronex-v4-eval.ai-8f3.workers.dev
ENTRONEX_SIGNING_KEY_ID=cf-v4-eval-sign-1
ENTRONEX_TENANT_ID=cloudflare_v4_eval
ENTRONEX_SIGNING_KEY_FINGERPRINT=1fe248e8ee9129fcd13c1ee96c1cd7d162a610e9b1df3037a4ec48935f2d43c4
ENTRONEX_SIGNING_PUBLIC_KEY_PEM_B64=<pinned in server/wrangler.json>
GOOGLE_SIGN_IN_WEB_CLIENT_ID=<configured in Cloudflare Worker; not a secret>
```

The public pin comes from the EntroNex live public-trust export workflow, which recomputes SHA-256 over the Ed25519 SPKI bytes before publishing the bundle.

Required server-only Cloudflare secrets at runtime:

```text
ENTRONEX_API_TOKEN
LUDOPROOF_SESSION_HMAC_KEY
```

The deploy workflow can generate `LUDOPROOF_SESSION_HMAC_KEY` directly in Cloudflare on first deployment and preserves it on later deployments when the operator does not supply a GitHub copy. Never commit, log, paste, or embed either runtime secret in the Android app.

Readiness endpoint:

```text
GET /ready
```

The endpoint fails closed with HTTP 503 until the match-storage and rate-gate bindings, account directory and player-account Durable Objects, the shared Google OAuth Web client ID variable (`GOOGLE_SIGN_IN_WEB_CLIENT_ID`), retry-safe session HMAC key, EntroNex server credential, pinned EntroNex signing identity, and upstream EntroNex health check are all ready. Billing and AdMob rewards are separately gated and should remain disabled until their own end-to-end tests pass.

## Pre-launch account and store notes

Earlier pre-launch notes reported no production users or real purchases. Reconfirm the current state before rollout; do not assume production storage is empty or reset existing entitlements. New accounts should begin with server-authoritative empty wallet state. Old device-local gem balances, cosmetics progress, and Remove Ads flags remain unverified test/cache data and must not be automatically imported as paid currency or entitlements.

The account deletion API has an authenticated `POST /api/account/delete` route and Profile UI confirmation. A guest-only account can also register a separate random 256-bit delete-only code through its active session; only the credential hash is stored server-side. The app stores that code inside its Android Keystore-encrypted session and displays it on request so the owner can save it before uninstalling. The public page source at `docs/account-deletion.html` accepts that code only for deletion of an account that remains unlinked to Google; it cannot create a session or recover account data. Google-linked accounts must use Google identity verification. If a guest has already uninstalled without saving a code, the publisher-support route still needs a monitored contact and an approved ownership-verification process. The GitHub Pages workflow (`.github/workflows/account-deletion-pages.yml`) remains publisher-gated and has not been published. Server-side cleanup covers the account directory, wallet/session/inventory/transaction rows, purchase-ledger references, linked Friend/social records, and leaderboard profile; deletion tombstones prevent account/recovery IDs from being silently recreated. After server confirmation the app clears account-scoped local profile, wallet, identity, and pending Play-purchase caches.

The authenticated deletion implementation and guest delete-only code are merged into `main`. This does not mean the public website has been published or the Worker has been deployed. Before launch, validate both deletion paths against the deployed Worker and hosted page, test retries across real Durable Object bindings in a staging/production-like environment, and document the publisher-approved match receipt/history retention or anonymization policy. Existing failure-injection tests use fakes/in-memory Durable Objects and do not replace hosted end-to-end validation. Keep [issue #232](https://github.com/rajkumarlahare/ludoproof/issues/232) open until every acceptance criterion is satisfied.

Google sign-in requires the same public Web OAuth Client ID in the Android Gradle property and Cloudflare Worker variable `GOOGLE_SIGN_IN_WEB_CLIENT_ID`; do not put an OAuth client secret in Android. The Android package is `com.rekixo.ludopaws`.

The public landing page source is `docs/landing-page.html`; the Terms of Service draft is `docs/terms-of-service.html`; the pricing/in-app purchase policy draft is `docs/pricing.html`; the deletion page source is `docs/account-deletion.html`; and the privacy policy draft is `docs/privacy-policy.html`. `.github/workflows/account-deletion-pages.yml` publishes these pages through GitHub Pages only from `main` after the Privacy Policy, Pricing Policy, and Terms of Service review markers plus publisher release gates have been cleared. The Pages job is skipped until the publisher explicitly sets `LUDOPROOF_POLICY_RELEASE_APPROVED=true`. After opt-in, it still refuses to publish while `POLICY_RELEASE_REVIEW_REQUIRED`, `PRICING_RELEASE_REVIEW_REQUIRED`, or `TERMS_RELEASE_REVIEW_REQUIRED` remains, or the OAuth Client ID, monitored support email, or web origin is missing/invalid. This keeps routine main-branch CI green without accidentally publishing an unreviewed policy. CORS is fail-closed until Cloudflare variable `LUDOPROOF_ACCOUNT_WEB_ORIGIN` is explicitly set to `https://ludopaws.rekixo.com`; there is no fallback origin. The guest-only/uninstalled-app support route is not considered usable until the publisher verifies the support process and the deployed page.

Billing and rewarded ads remain opt-in integrations. At source level, Android `LUDOPROOF_BILLING_ENABLED`, `LUDOPROOF_ADS_ENABLED`, and `LUDOPROOF_INTERSTITIAL_ADS_ENABLED` all default to `false`; malformed Gradle boolean values also fail closed to `false`. The signed-release workflow treats omitted flags as `false` and requires Android/Worker Billing and Ads flags to agree. Keep billing/ads build flags and server enablement off until the exact Play product catalog/service account and AdMob production unit/SSV callback have passed internal end-to-end tests.

## Android release security

The release candidate:

- blocks cleartext network traffic;
- disables Android backup for local game state;
- encrypts the player bearer token with an Android Keystore AES-GCM key and binds its match/player metadata with GCM AAD;
- encrypts the pending unrevealed client seed with Android Keystore and binds its match/commitment metadata with GCM AAD;
- limits API response size;
- enables release shrinking and resource shrinking;
- runs JVM unit tests and release lint in CI;
- builds release APK/AAB candidates and records SHA-256 evidence in CI;
- refreshes multiplayer state automatically only while the Activity is in the foreground.

The release signing key must be managed outside this repository. Never commit a keystore or signing password. CI artifacts are release candidates, not store-signed production packages; final upload/store signing is an external controlled step.

## Match lifecycle

Waiting and active matches expire after seven days without a game mutation.

Finished matches expire after 24 hours.

Reads do not extend the lifetime. Real game mutations schedule a new Durable Object alarm.

## Randomness invariants

Do not add any of the following:

- anti-repeat logic;
- anti-streak logic;
- recent-result weighting;
- reroll-until-different behavior;
- replacement rolls after network failure;
- client-selected event indexes.

Repeated dice results are valid IID outcomes.

The same logical event is identified by:

```text
applicationId + matchId + eventType + eventIndex
```

A retry must resume the same pending round.

## Offline randomness parity

Offline mode must not use a separate simplified dice path.

The Android offline engine is locked to:

```text
algorithm = entronex-v4-dual-commit-hkdf-sha256-context-bound
outcomes = [1,2,3,4,5,6]
cellsPerOutcome = 16
timelineTicks = 512
epochCount = 8
probeCount = 3
ruleset = ludoproof-standard-v1
```

It must preserve the same v4 commitment prefixes, context/config/event-binding digests, transcript construction, HKDF labels, HMAC stream namespace, 48-bit rejection sampling, balanced Natural World layout, probe motion, logical transforms, witness swap, field/world digests, and proof digest.

A frozen cross-language EntroNex candidate vector is asserted by Android JVM tests. The offline engine must fail closed if local v4 recomputation does not reproduce the generated proof before the outcome is applied.

The trust claim remains narrower than online mode: offline has **algorithmic parity**, not remote authority parity. Both offline seeds are generated on the same device; there is no remote EntroNex signed commitment/proof attestation.
## Ruleset

Current ruleset:

```text
ludoproof-standard-v1
```

Do not silently add blockade/stack behavior or otherwise change gameplay semantics under this ID. A rules change requires a reviewed new ruleset identifier and corresponding tests.

## Cryptographic claim boundary

EntroNex v4 remains an evaluation candidate pending independent cryptographic review.

Therefore the game may describe a roll as mathematically verified against the current EntroNex v4 proof implementation, but must not claim that the system is independently audited, certified, gambling-certified, or production-certified by a third party.

Independent review remains a release requirement for high-assurance or regulated use.


## Automated Cloudflare production deployment

The repository contains a gated production workflow:

```text
.github/workflows/deploy-cloudflare.yml
```

Production deployment requires an explicit `workflow_dispatch` from `main`. Pushes/merges and `CLOUDFLARE_DEPLOY_ENABLED` do not trigger this workflow. Complete the current family release gates before dispatching it.

The production GitHub environment needs only one externally supplied secret for a clean first deployment:

```text
CLOUDFLARE_API_TOKEN
```

`ENTRONEX_API_TOKEN` is optional as an operator-supplied synchronized rotation value. When it is absent, the workflow checks the secret names on `entronex-v4-eval` and `ludoproof-game-api`. If both are absent it generates one masked customer credential in GitHub Actions and installs the same value on both Workers after all repository tests and the Wrangler dry-run pass. If both already exist it preserves them. If only one exists, deployment fails closed instead of guessing or silently rotating the trust relationship.

The Cloudflare account ID is non-secret and pinned in the workflow for the existing account. The EntroNex public signing trust is pinned in `server/wrangler.json`. `LUDOPROOF_SESSION_HMAC_KEY` may optionally be supplied as a GitHub secret; otherwise the workflow preserves the existing Cloudflare value or securely generates it on first deployment.

The workflow validates configuration without printing secret values, runs the repository security gate and server tests, performs a Wrangler production dry-run, deploys the Worker, synchronizes or preserves the EntroNex credential, preserves or creates the session HMAC, and only completes successfully after both `/health` and `/ready` pass.

Only the manual `workflow_dispatch` path is enabled. Keep the existing production Worker unchanged until publisher approval, staging validation and the pinned signing identity have been verified.


### EntroNex signing-key rotation

A changed EntroNex signing key must not be accepted automatically. Run the EntroNex `EntroNex v4 Public Trust Export` workflow, verify that its published fingerprint matches the intended Cloudflare evaluation Worker, review the new public key bundle, update the pinned values in `server/wrangler.json`, and let the repository security gate verify the reviewed pin before deployment.


### GitHub-only bootstrap

A local clone is not required for production bootstrap. The workflow can create the first EntroNex evaluation customer token entirely inside GitHub Actions using the production `CLOUDFLARE_API_TOKEN`. The generated value is masked before it is written to either Worker and is not uploaded as an artifact or printed to logs.


### EntroNex transport

Production must keep `ENTRONEX_SERVICE -> entronex-v4-eval` configured. Cloudflare Service Bindings invoke the target Worker directly within the same account and remove public `workers.dev` reachability from the production dependency path. Local tests and non-Cloudflare tooling retain the public URL fallback.


### Automated live game smoke

Every production deployment runs `scripts/production-game-smoke.mjs` after `/ready` succeeds. The smoke creates an isolated two-player match through the public production API, joins the second player, starts the match, performs one or more real EntroNex v4 commit/reveal rolls, validates the returned proof contract, and performs a legal move when the sampled dice sequence makes one available.

The smoke never logs player bearer tokens or client seeds. The temporary match follows the normal match-retention policy and expires automatically. A deployment is considered successful only after readiness and this live game-flow smoke both complete.
