# Google Play Billing — Production Readiness and Release Checklist

> **Current internal candidate: Family V2 / RC3 (20 October 2026).** The publisher authorized configured Play Billing, child-directed ads and optional Google account linking/restore. Voice and free-text remain removed. No startup/gameplay age entry. Follow [current release status](production-readiness/RELEASE_STATUS.hi.md) for build/deploy evidence and physical/Play acceptance; older release instructions below are historical where they conflict.

**Scope:** This checklist validates Google Play **one-time in-app products** only. It does not enable deployment or change feature flags. Keep the one-time `remove_ads` SKU semantics unchanged.

## 1. Supported product IDs

These IDs are hard-coded in the server verifier and must match products provisioned in Play Console exactly:

| Product ID | Type | Server fulfillment |
| --- | --- | --- |
| `remove_ads` | One-time, non-consumable | Persistent Remove Ads entitlement (legacy-compatible behavior) |
| `gems_300` | One-time, consumable | 300 Gems |
| `gems_1500` | One-time, consumable | 1,200 Gems |
| `gems_3500` | One-time, consumable | 3,000 Gems |
| `gems_20000` | One-time, consumable | 7,500 Gems |
| `gems_40000` | One-time, consumable | 16,000 Gems |
| `gems_200000` | One-time, consumable | 35,000 Gems |

Do not infer a purchase SKU from a display label or create a different ID in Play Console. Verify that each product exists, has the correct product type, is active/available for the test region, and has a price configured. Only show prices returned by Google Play as checkout prices.

## 2. Configure the Google Play Developer API identity

1. In Google Cloud Console, select the project that owns the service account, enable **Google Play Developer API**, and create/identify the service account.
2. In Play Console, open **Users and permissions**, invite that exact service-account email, and grant the Play Billing permissions needed to view and manage financial data/orders.
3. Generate/download a service-account key only if one is not already available. Never commit the JSON key, private key, or credentials to Git, Android resources, Gradle properties committed to source, or chat.

Official setup guide: https://developers.google.com/android-publisher/getting_started

## 3. Configure runtime values without exposing secrets

In the Cloudflare dashboard, open the `ludoproof-game-api` Worker and add/update the secret:

- `GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY`: the service-account JSON's `private_key` PEM value, including the BEGIN/END PRIVATE KEY lines.

The production deploy job declares `environment: production`. In GitHub, open **Settings → Environments → production → Environment variables** and set these values there (not only under **Settings → Secrets and variables → Actions → Variables**). When a variable exists at both scopes, the `production` environment value takes precedence for this job:

- `GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL`: exact service-account email.
- `GOOGLE_PLAY_BILLING_ENABLED`: keep `false` until service-account access, the secret, exact Play Console SKUs, pricing/availability and license testers are ready. Set it to `true` for the controlled internal-test window so the server can verify test purchases.
- `LUDOPROOF_BILLING_ENABLED`: same value as `GOOGLE_PLAY_BILLING_ENABLED`; use it to build the Billing-enabled internal-test candidate. Do not publish that candidate to a public track until it passes the test gate.

The production workflow checks that the Cloudflare private-key secret exists, that the email is correctly formatted, and that the Android/Worker flags match. These preflight checks do **not** prove that Google accepts the key or that the service account has permission to read/consume/acknowledge the products. The credential remains a Cloudflare secret; do not add it as a repository variable or print it in logs.

Official permission/setup guide: https://developers.google.com/android-publisher/getting_started

## 4. Build an explicitly Billing-enabled test candidate

Billing is deliberately disabled by default in Gradle. Keep the default disabled; enable it only for the candidate being tested.

From PowerShell:

```powershell
Set-Location D:\dev\ludoproof\android

.\gradlew.bat -PLUDOPROOF_BILLING_ENABLED=true :app:testDebugUnitTest
.\gradlew.bat -PLUDOPROOF_BILLING_ENABLED=true :app:lintRelease
.\gradlew.bat -PLUDOPROOF_BILLING_ENABLED=true :app:assembleRelease :app:bundleRelease
```

Before uploading an AAB to a track, verify the release signing configuration and inspect the package ID: `com.rekixo.ludopaws`. A CI artifact is not necessarily signed with the Play upload key.

## 5. Test using Play Console's internal test track and license testers

Use a Google account registered as a license tester. Install the candidate from the Play test-track opt-in link and confirm the Play Store account on the test device is the intended tester. Do not use a real-paying account for initial validation.

Required checks:

- Product query: all expected SKUs appear with Google Play localized prices; missing/unavailable SKUs are not offered for purchase.
- Consumable purchase: buy `gems_300`; the balance changes by exactly 300 after server verification.
- Idempotency: repeat the verification/relaunch/reconnect path for the same purchase token; the wallet must not receive the Gems twice.
- Non-consumable purchase: buy `remove_ads`; server verification must acknowledge it and the account must receive the existing Remove Ads entitlement.
- Reconciliation: restore/query purchases after force-stop and restart; legitimate prior non-consumable purchases must be recoverable without granting a purchase to a different account.
- Pending and cancelled payment: never grant Gems or Remove Ads until Google confirms `PURCHASED`.
- Error paths: disable network or make the API unreachable during verification; do not grant unverified content. A retry must recover safely without a duplicate grant.
- SKU mismatch / wrong account: verification must fail closed and must not grant value.
- Verify that consumable products are consumed and non-consumable products are acknowledged server-side. Google requires purchases to be processed/acknowledged within its specified window.

Google's billing test guide: https://developer.android.com/google/play/billing/test

Purchase-processing guidance: https://developer.android.com/google/play/billing/integrate

## 6. Controlled deployment and release verification

This repository currently has a manual production Worker deployment workflow, not a separate staging Worker. Treat enabling the backend flag as a live configuration change and use it only for a controlled internal-test window.

1. Confirm source/CI checks pass on the exact commit. Confirm every SKU, regional price/availability, Play Developer API permission, Cloudflare private-key secret, and license-tester account is ready.
2. Build and sign the Billing-enabled AAB with the command in section 4. Upload it to **Internal testing only** and make sure the opt-in tester list is restricted to your intended test accounts. Do not promote it to any public track yet.
3. Set both GitHub production environment variables `GOOGLE_PLAY_BILLING_ENABLED=true` and `LUDOPROOF_BILLING_ENABLED=true`. Then go to **Actions → LudoProof Cloudflare Production → Run workflow**, select the reviewed commit on `main`, and run it. Merging a PR alone does not deploy. If the workflow fails, leave the feature disabled and investigate before retrying.
4. Confirm the workflow's production readiness, store-status and live game-flow checks pass. From PowerShell, run:

   ```powershell
   Invoke-RestMethod "https://api.ludopaws.rekixo.com/ready" | ConvertTo-Json -Depth 8
   Invoke-RestMethod "https://api.ludopaws.rekixo.com/api/store/status" | ConvertTo-Json -Depth 8
   ```

   Verify `checks.playBillingEnabled = true` and `billingAvailable = true`. With the diagnostics PR deployed, inspect `billingChecks` for the first false condition. Those fields are booleans only; the service-account email and private key are never returned.
5. Install the Billing-enabled AAB from the internal test track (not a sideloaded build for the final acceptance run) and execute every test in section 5 against the deployed API: product query, successful consumable and non-consumable purchases, wallet/entitlement delivery, repeat/retry/idempotency, force-stop/relaunch restore, pending/cancelled transactions, wrong-account rejection, and server consume/acknowledge.
6. If any purchase test fails, keep the app off public tracks, set both flags back to `false`, run the production workflow again to close checkout, then correct the failed gate and retest. Only after every test succeeds should this candidate be promoted through a staged release. Keep the Android/Worker Billing flags aligned and monitor verification failures, refunds and support reports after rollout.

## Release gate

Do not describe Billing as production-ready merely because the flag is true or `/ready` returns 200. Release acceptance requires: matching Play Console SKUs, service-account authorization against the real Developer API, a Billing-enabled signed candidate, a successful license-tester purchase and restore, no double fulfillment, server consume/acknowledge success, passing CI, and an explicitly run successful production deployment.

This checklist covers one-time purchases only.
