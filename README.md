# Ludo Paws

> **Current status and release boundaries:** see [CURRENT_STATUS.md](docs/CURRENT_STATUS.md). The dated record confirms publisher-approved referral activation on 27 October 2026; older OFF/pending sections are historical. The last verified Play candidate was rc10 internal, while rc9 was in production review. Do not infer current public availability or change that in-review submission. Multiplayer and fixed quick-chat are supported; free-text chat and voice are disabled. Provider configuration, exact-version validation and publisher/legal responsibility remain separate.

Audit fixes and their validation requirements are tracked in [AUDIT_HARDENING.md](docs/AUDIT_HARDENING.md). Local development uses the single [main-app Android runner](scripts/android-local-testing.mjs); backend tests use Node 22, selected by `.node-version`.

**Publisher / parent brand:** Rekixo (`https://rekixo.com`)

**Final Android application ID:** `com.rekixo.ludopaws`

**Game website target:** `https://ludopaws.rekixo.com`

**API hostname:** `https://api.ludopaws.rekixo.com` (the new online protocol requires a coordinated backend and Android release).

**Publisher / parent brand:** Rekixo  
**Repository:** `ludopaws`

Rekixo is the publisher/parent brand for Ludo Paws. The intended public-domain layout is `rekixo.com` for the company site, `ludopaws.rekixo.com` for the game site, and `api.ludopaws.rekixo.com` for the game API. The dedicated API hostname has historical deployment checks, but the previous lockdown smoke only verified denied routes. It is not evidence that this family candidate has passed enabled live gameplay. The game website/account host is a separate publisher-gated static-site cutover: it is not considered published until policy approval, Pages/DNS/TLS checks, and public deletion/privacy flows have passed. Do not attach or alter unrelated Rekixo/AR3D Workers or routes.

Branding/repository names are separate from compatibility-sensitive runtime identifiers. Keep the existing Android application ID, Cloudflare Worker and Durable Object names, persisted storage names, proof/ruleset identifiers, and protocol values unchanged until a separately tested migration plan explicitly authorizes changes. Repository rename does not require renaming those runtime identifiers.


**Ludo Paws** is the consumer-facing name of this Android Ludo game. The working store title is **Ludo Paws: Talking Animals** and the product tagline is **Roll. Race. Roar!**

Online, Friends and Team Up use **server-authoritative cryptographic dice** generated inside the match Durable Object. Robot and Pass & Play keep their existing local EntroNex v4 derivation. Online receipts record the server result and do not independently attest fairness. See [online transport and rollout](docs/ONLINE_REALTIME.md).

> Migration compatibility note: Android application identity and account/wallet bindings remain stable. The online command protocol and receipt-archive migration are described in [online transport and rollout](docs/ONLINE_REALTIME.md).

The repository contains:

- `android/` — native Kotlin Android client;
- `server/` — authoritative Cloudflare Worker + Durable Object match backend;
- `docs/` — protocol and architecture notes.

## Core rule

The Android app does not decide the dice result.

For every online roll:

```text
Android command (request ID + expected event index)
        |
        v
Authenticated match WebSocket (pooled HTTPS fallback)
        |
        v
Match Durable Object: random seed + rejection sampling + legal rules
        |
        v
One persisted result + receipt and direct authoritative snapshot broadcast
```

A transport retry keeps the same logical request and returns its recorded result. Dice animations and pawn previews are presentation only; they cannot choose an outcome or commit a move. The old two-call commit/reveal endpoints return an upgrade error. Other players receive the compact board snapshot over their existing socket.

## Playable v1 rules

- 2–4 players
- 4 tokens each
- roll 6 to leave yard
- exact roll required to reach home
- extra turn on 6
- extra turn on capture
- third consecutive 6 forfeits the roll and passes the turn
- safe cells cannot be captured
- server validates every move

Blockade/stack restrictions are not yet part of ruleset v1.

## Android

Final Play Store application ID:

```text
com.rekixo.ludopaws
```

The current Kotlin source namespace remains `com.rekixo.ludopaws` in this first safe identity change; Google Play uses the application ID above.

The Android MVP now supports:

- create/join/share match and waiting-room player display;
- a native interactive 15×15 Ludo board;
- server-authoritative token positions and legal-token highlighting;
- tap-to-move only for server-returned legal tokens;
- presentation-only dice animation whose final face is the verified server outcome;
- turn/winner banners and authoritative dice results;
- compact last-roll information;
- persisted match/player session with the bearer token encrypted by Android Keystore and AAD-bound to its match/player metadata;
- persisted command IDs for retry-safe rolls and moves;
- cleartext network traffic blocked and app backup disabled for sensitive local game state;
- foreground-only automatic state sync so multiplayer turns, moves, verified rolls, and winner state refresh without manual polling;
- 2–4 player offline pass-and-play using the same Ludo rules and exact v4 outcome/Natural World derivation locally, with a persisted local audit map and explicit no-remote-attestation labeling.

The current API URL compiled into the app is:

```text
https://api.ludopaws.rekixo.com
```

## Local receipt-window compatibility

New Computer and Pass & Play matches use the local-only `ludoproof-standard-v6` ruleset. It keeps the existing gameplay and frozen EntroNex v4 derivation, while explicitly binding a 100-receipt history window into the ruleset digest. Existing v3/v5 saves retain their original ruleset and derivation; their history is not silently truncated in memory. Online rulesets are unchanged. Offline proof generation and verification run on a worker; applying the result requires the same match revision and foreground lifecycle generation.

## Offline v4 engine

Offline play preserves the **frozen EntroNex v4 mathematical derivation** entirely on the Android device. Online matches use the separate server dice protocol.

Each offline roll:

1. freezes the local pre-roll Ludo state;
2. computes the same actor, game-state, and ruleset SHA-256 bindings used by the online Worker;
3. locks outcomes to `[1,2,3,4,5,6]`;
4. uses the same compatibility-preserved Natural World profile:
   - `cellsPerOutcome = 16`
   - `timelineTicks = 512`
   - `epochCount = 8`
   - `probeCount = 3`
5. generates two fresh local 256-bit CSPRNG seeds;
6. computes the v4 client/server commitments;
7. derives the outcome with HKDF-SHA-256 + the domain-separated HMAC stream + 48-bit rejection sampling;
8. builds the same balanced 96-cell Natural World, probes, transforms, witness, field digest and world digest;
9. computes the same v4 proof digest;
10. recomputes the result locally and fails closed if the local proof does not verify before applying the Ludo roll.

The Android unit suite includes the frozen cross-language EntroNex v4 conformance vector. The expected outcome, transcript digest, field digest, world digest and proof digest must match exactly.

**Trust boundary:** offline and online have separate randomness and authority models. In offline mode both entropy seeds are generated by the same device and there is no remote EntroNex commitment attestation or server authority. The UI therefore labels it as **local v4 derivation / local recomputation**, not as a remotely attested EntroNex roll.

The last offline roll keeps a local audit record and exposes the derived Natural World map so the selected sample cell and digests can be inspected after the roll.

## Backend

The isolated Cloudflare Worker name is intentionally compatibility-preserved:

```text
ludoproof-game-api
```

The backend uses SQLite-backed Durable Objects for authoritative matches, rate limiting, social identity, purchase redemption, and account state. The account/store-related bindings include:

```text
LUDOPROOF_ACCOUNT_DIRECTORY -> AccountDirectory
LUDOPROOF_PLAYER_ACCOUNTS -> PlayerAccount
LUDOPROOF_STORE_PURCHASES -> StorePurchaseLedger
LUDOPROOF_API_GATE -> ApiGate
```

`ApiGate` provides per-client, per-scope request throttling without storing raw client IP addresses. `AccountDirectory` maps Google identities and linked Friend/leaderboard IDs to stable account UUIDs; `PlayerAccount` stores sessions, wallet balances, inventory, and the transaction ledger within the account's Durable Object.

Existing EntroNex, Rekixo, AR3D, domains, DNS, and Workers are not modified by this project.

### Required server configuration

The retry-safe session HMAC is also a Cloudflare secret:

```text
LUDOPROOF_SESSION_HMAC_KEY
```

During automated deployment, an operator-supplied HMAC is used when provided. Otherwise the workflow preserves an existing Cloudflare secret and, on the first deployment only, generates a cryptographically random 48-byte value directly in the deployment channel without printing it.

**Never put runtime secrets in Android source, BuildConfig, the repository, or chat.**

The account API also requires the public Google OAuth **Web application client ID** as a Worker variable:

```text
GOOGLE_SIGN_IN_WEB_CLIENT_ID
```

Set it under the Worker’s Variables and Secrets settings. Do not use the numeric Play Games Services project ID or the Android OAuth client ID here. The value must match the Google web client ID provided to Gradle as `GOOGLE_SIGN_IN_WEB_CLIENT_ID`. The ID is public configuration, not a private key.

The account/store Worker uses SQLite-backed Durable Objects `AccountDirectory` and `PlayerAccount`. Both bindings must be deployed before `/ready` can succeed. Sign-in and server-owned wallet operations fail closed while the account bindings or session HMAC secret are missing.

Online gameplay has no external dice service binding, provider token, trust pin or receipt archive. The migration removes the obsolete `MatchReceiptArchive` Durable Object class. Offline Android proof code remains in place.

## Cloudflare Git deployment

Create a new Cloudflare Worker from this repository only.

Use:

```text
Worker name: ludoproof-game-api
Production branch: main
Root directory: /server
Build command: (empty)
Deploy command: npx --yes wrangler@4.135.0 deploy
```

Keep the Worker name `ludoproof-game-api` and its Durable Object identifiers unchanged. The dedicated Ludo Paws API domain `api.ludopaws.rekixo.com` is now the branded API endpoint validated by production smoke checks; retain the `workers.dev` hostname as a rollback/test fallback. Do not attach or alter unrelated Rekixo/AR3D domains, Workers, or routes.

## Local checks

Server:

```bash
cd server
npm test
npx --yes wrangler@4.135.0 deploy --dry-run
```

Android:

```bash
gradle -p android :app:testDebugUnitTest
gradle -p android :app:lintRelease
gradle -p android :app:assembleDebug
gradle -p android :app:assembleRelease
gradle -p android :app:bundleRelease
```

GitHub Actions gates server tests, Wrangler dry-run, Android JVM unit tests, release lint, debug/release APK builds, release AAB bundling, SBOM generation, and SHA-256 release evidence automatically. CI release artifacts are intentionally not store-signed; upload/store signing remains an external release-key operation.

Deployment readiness can be checked with:

```text
GET /ready
```

It returns HTTP 503 until the match and account Durable Object bindings, the retry-safe session HMAC key and Google OAuth web client ID are all available. Google Play Billing and AdMob SSV are separately reported as unavailable until their own explicit server configuration is enabled.

## Security boundary

Gameplay match session tokens remain random bearer credentials stored only as hashes in the match Durable Object. Account API access tokens are separate, HMAC-signed, 15-minute tokens bound to an account UUID and session UUID; refresh tokens are random, stored only as hashes in the account Durable Object, and rotated with an idempotent retry key. The Android app stores account tokens and the guest-recovery request ID encrypted with Android Keystore AES-GCM.

The Google Play service-account private key exists only on the server. The wallet's authoritative state is server-side; local preferences are a cache and are never used to grant currency or paid inventory.

Each online receipt binds its request ID, match, player, event index, previous board digest and ruleset digest. Its revealed seed lets diagnostics reproduce the sampled result, but a server-generated receipt cannot prove the server did not choose a different seed.

## Important status

This is a release candidate. Online dice receipts and local offline proofs must not be represented as independent certification. The new Worker and Android client must be released together; do not deploy only one side of the protocol change.

See [online transport and rollout](docs/ONLINE_REALTIME.md) for validation and remaining device measurements. Earlier architecture/proof documents describe the previous protocol and are historical until revised.

## Google Play Games sign-in

The Android profile uses Google Play Games Services v2 for native player sign-in. To enable it:

1. Create/link the Android game in Play Console's Play Games Services setup and use the numeric **Games Services project ID** (not the OAuth client ID).
2. Add the app's package name (`com.rekixo.ludopaws`) and the SHA-1 signing certificate fingerprints for every build distributed to testers or players. The debug build needs its debug-key SHA-1; store builds need the app-signing certificate SHA-1.
3. Provide the project ID to Gradle without committing it to source:

   ```text
   PLAY_GAMES_PROJECT_ID=123456789012
   ```

   Set this property in the local `android/gradle.properties`, CI Gradle properties, or pass `-PPLAY_GAMES_PROJECT_ID=123456789012` to Gradle. A value of `0` is used by default so builds remain possible before Play Console setup; the profile will show that configuration is required and will not start sign-in.
4. Add tester accounts and publish the Play Games Services configuration to a testing track before testing authentication.

The Profile screen checks the Play Games authentication state when opened, offers a native sign-in action, and displays the signed-in Play Games player name. This authenticates the player with Play Games Services on the device; it does **not** upload local profile statistics or provide cross-device profile sync.

## Ludo Paws account and cloud wallet

Ludo Paws accounts use a server-issued UUID. A new installation may create a guest account; on the Profile screen, **Save this account to Google** links that same account to a Google identity, and **Restore an account saved to Google** restores only an account that was previously linked. Account linking does not merge two existing accounts. The app stores the short-lived access token, refresh token, session ID, and guest-recovery request under Android Keystore AES-GCM encryption.

To enable this sign-in flow:

1. Create an OAuth 2.0 **Web application** client ID for the Google sign-in backend audience. Configure the correct Android package and signing-certificate fingerprints in Google Cloud Console as required by the Android Credential Manager setup.
2. Set the same Web client ID in Android Gradle properties or CI as `GOOGLE_SIGN_IN_WEB_CLIENT_ID`, and in the Cloudflare Worker variable `GOOGLE_SIGN_IN_WEB_CLIENT_ID`. Do not place an OAuth client secret or any signing key in the Android app.
3. Set `LUDOPROOF_SESSION_HMAC_KEY` as a Cloudflare Worker secret (minimum 32 bytes; the deployment setup creates a random value if none exists). It is used to sign 15-minute access tokens and make refresh rotation retry-safe.
4. Deploy the `v10-account-directory` and `v11-player-accounts` Durable Object migrations before enabling the account flow. Keep `/ready` unhealthy until the bindings and Google Web client ID are configured.

The account Worker owns profile display name, gem balance, Remove Ads entitlement, cosmetic inventory, selected cosmetics, and verified rewarded-ad progress. Wallet reads come from the authenticated backend and the local SharedPreferences values are only a display cache. Client-side callbacks cannot add gems, increment ad progress, or subtract gems for a cosmetic purchase. Store operations use idempotency keys and server-side transactions. Restoring a Google-linked account recovers account-linked Friend and leaderboard credentials without merging a different local guest identity.

**Pre-launch migration note:** earlier notes reported no production users or real purchase transactions; reconfirm the actual current state before rollout and preserve existing entitlements. Old device-only gem balances, cosmetic progress, and local Remove Ads flags are still treated as unverified local/test data: before the first server wallet snapshot, the app archives and warns about them but does not import them into the server wallet. Old Play purchase records from development/testing are not automatically rebound to a new account. Do not reset existing customer data based on these historical notes; create fresh server-authoritative state only for genuinely new accounts. If test purchases are created later, verify and clear them under Google Play test procedures rather than carrying them into production. Never restore paid currency from the untrusted archive.

Local match history and profile progression statistics remain device-local and are scoped to the active account; this does **not** automatically upload all historical stats. Play Games Services sign-in remains a separate feature from the Ludo Paws account identity.

## Production configuration source of truth

The production deploy workflow materializes the following **non-secret GitHub Actions repository Variables** into `server/wrangler.json` in its ephemeral runner before dry-run/deploy. This is intentional: Wrangler treats its config as the source of truth for ordinary vars and may override vars configured only in the Cloudflare dashboard. The deployed values must therefore be reviewed in the repository's Actions Variables, not only in Cloudflare's dashboard.

Required before any production Worker deployment:

- `GOOGLE_SIGN_IN_WEB_CLIENT_ID`: the Google **Web** OAuth client ID used by Android and the account-deletion site.
- `LUDOPROOF_ACCOUNT_WEB_ORIGIN`: target origin `https://ludopaws.rekixo.com` (use only after the branded site is configured and approved).
- Keep `GOOGLE_PLAY_BILLING_ENABLED=false` and `LUDOPROOF_ADMOB_SSV_ENABLED=false` until each integration has passed internal-track end-to-end tests.

Optional store variables, which remain fail-closed unless enabled explicitly:

- `GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL`: set only after the Play Console service account exists. Its private key is a **Cloudflare Worker secret** named `GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY`, never a GitHub Variable, source file, Android build property, or chat message.
- `GOOGLE_PLAY_BILLING_ENABLED`: Worker-side checkout flag; default false.
- `LUDOPROOF_ADMOB_SSV_ENABLED`, `LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID`, `LUDOPROOF_ADMOB_REWARD_ITEM=gems`: server-side rewarded-ad configuration; default disabled, configured reward fixed at five gems.

The signed Android release workflow reads these repository Variables: `GOOGLE_SIGN_IN_WEB_CLIENT_ID`, `LUDOPROOF_API_BASE_URL` (optional; defaults to the production Worker), `LUDOPROOF_BILLING_ENABLED`, `LUDOPROOF_ADS_ENABLED`, `ADMOB_APP_ID`, `ADMOB_REWARDED_AD_UNIT_ID`, and `PLAY_GAMES_PROJECT_ID` (optional). The Android Billing/Ads flags must match their Worker counterparts; the workflow checks `/ready` and `/api/store/status` before building the signed artifacts. Ads remain disabled unless you deliberately configure matching real IDs on both sides. Do not set a flag to true until the associated Google/AdMob setup and end-to-end tests are finished.

## Google Play Billing

Digital gems and Remove Ads purchases use the native Google Play Billing Library. Guest accounts can open checkout directly; optional Google backup and local age/privacy selections do not gate purchases. The payment sheet handles payment authorization. A client purchase callback only produces a pending transaction; the Worker verifies it through the Google Play Developer API, checks the obfuscated account ID, and reserves/redeems the purchase token through a Durable Object ledger. Only after verification does the per-account wallet transaction grant gems or set Remove Ads. Pending purchase tokens are encrypted with Android Keystore AES-GCM in private, backup-disabled app storage for retry; older plaintext recovery records are migrated in place. The canonical balance and inventory are held by the server and can be restored on another device after the same Google-linked account is restored.

The checkout intentionally stays disabled until both Play Console products and the verification service are ready. Before enabling it:

1. Create each one-time in-app product in Play Console using the IDs in `android/app/src/main/java/com/rekixo/ludopaws/feature/store/domain/StoreCatalog.kt`: `remove_ads`, `gems_300`, `gems_1500`, `gems_3500`, `gems_20000`, `gems_40000`, and `gems_200000`. Configure the displayed prices and quantities to match the catalog. Publish the products to an internal testing track and add tester accounts.
2. In Google Cloud Console, enable the Google Play Developer API and create a service account. Grant that account the Play Console permissions needed to view financial/order data and manage in-app purchase orders.
3. Set the service account email as a Worker variable named `GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL`. Store its private key only as a Cloudflare Worker secret:

   ```text
   cd server
   npx --yes wrangler@4.135.0 secret put GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY
   ```

   Paste the PEM private key value when prompted. Never put the private key in Android, `wrangler.json`, source control, build arguments, or chat. The Google Play purchase verifier targets `com.rekixo.ludopaws`.
4. Deploy the Worker so the `LUDOPROOF_STORE_PURCHASES` Durable Object binding/migration is active. In Cloudflare Worker settings, add the non-secret variable `GOOGLE_PLAY_BILLING_ENABLED=false` while configuring the app and service account.
5. For internal testing only, set `GOOGLE_PLAY_BILLING_ENABLED=true` in Cloudflare and build with `-PLUDOPROOF_BILLING_ENABLED=true`. Check `GET /api/store/status`; `billingAvailable` must be `true`, and complete a tester purchase/restore/refund test before public release. The Android build flag defaults to `false`; the Worker must independently report billing enabled before checkout is opened.

Google Play remains responsible for purchase UI and payment collection. The backend service-account credentials, product activation, release signing, and real tester purchase remain external setup steps; do not sell products before all those settings are verified. The authoritative wallet/inventory is cloud-backed by the player account Durable Object. A purchase is scoped to the account whose hashed ID Google Play receives; recovery must use the same Google-linked Ludo Paws account. A guest account that was never linked to Google is tied to its original local recovery secret and should not be assumed recoverable after reinstall or device loss. Historical match stats are still device-local.

## Google AdMob rewarded ads

The Store supports opt-in rewarded ads through Google Mobile Ads and UMP consent. Reward grants are server-verified using AdMob server-side verification (SSV): the SDK attaches the active account UUID and bounded JSON custom data to the reward, AdMob calls `/api/admob/ssv`, the Worker verifies the Google signature, configured ad unit, reward fields and timestamp, and the account wallet applies the unique transaction ID only once. The client callback itself never grants gems or unlock progress. The app includes an Ad privacy options entry on the Store's Gems tab.

Ads are disabled by default. The build uses Google's official sample App ID and rewarded-ad unit ID only as harmless defaults; the sample IDs do not load ads unless the explicit test flags are enabled for a debug build. Configure the following Gradle properties in `android/gradle.properties` or supply them on the Gradle command line:

```text
ADMOB_APP_ID=ca-app-pub-xxxxxxxxxxxxxxxx~xxxxxxxxxx
ADMOB_REWARDED_AD_UNIT_ID=ca-app-pub-xxxxxxxxxxxxxxxx/xxxxxxxxxx
LUDOPROOF_ADS_ENABLED=true
LUDOPROOF_ADS_USE_TEST_IDS=false
```

Before enabling ads:

1. Register `com.rekixo.ludopaws` in AdMob and create a rewarded ad unit. Add the real App ID and rewarded ad-unit ID above (these IDs are not secrets).
2. Publish the applicable privacy/consent messages in the AdMob Privacy & messaging settings. Verify the UMP consent and privacy-options flow in the internal test track.
3. FAMILY_V1 keeps advertising disabled. FAMILY_V2 permits reviewed advertising only with LUDOPAWS_AD_SAFETY_REVIEW_APPROVED=true and matching Android/Worker configuration; use the release-status document for the current integration acceptance gates.
4. Only enable production ads with your real IDs and `LUDOPROOF_ADS_USE_TEST_IDS=false`; the build rejects enabling ads with the default sample IDs in this mode.
5. In the AdMob rewarded ad unit's server-side verification/reward settings, set the reward amount to **5** and reward item to **gems**. In Cloudflare, set `LUDOPROOF_ADMOB_SSV_ENABLED=true`, `LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID` to the exact real rewarded ad unit, and `LUDOPROOF_ADMOB_REWARD_ITEM=gems`. Keep SSV disabled until the public HTTPS callback `https://api.ludopaws.rekixo.com/api/admob/ssv` is configured in AdMob and an end-to-end test confirms that signed callbacks reach the matching account. The Worker independently requires amount `5`, item `gems`, the expected ad unit, a recent timestamp, and a valid AdMob signature before crediting anything.

A missing or mismatched SSV configuration disables server reward fulfillment. The configured reward is **5 gems**: an ad watched from the Gems tab grants 5 gems, and an ad watched toward an eligible cosmetic grants 5 gems plus one server-tracked cosmetic-progress step. The client callback only refreshes the server wallet; it does not grant either reward locally. Before enabling production ads, run end-to-end tests for both paths, callback delays, duplicate SSV transactions, and a mismatched ad unit/reward configuration. Follow Google AdMob's consent, child-directed treatment, and Families-policy requirements for the app's actual audience before serving production ads.
