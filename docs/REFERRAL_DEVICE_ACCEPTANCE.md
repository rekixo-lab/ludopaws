# Referral device acceptance — operator checklist

Code-level verification is separate from a successful real Play referral. No staging environment, legacy implementation, debug bypass, fake provider verdict or local reward fallback is required or introduced by this checklist. Public activation remains OFF until the publisher's actual review and release gates are satisfied. An operational-health pass does not satisfy those gates.

## Automated checks

Run backend tests with the repository's Node 22 runtime:

```sh
cd server
npm test
cd ..
node --test scripts/referral-runtime-config.test.mjs scripts/referral-health-check.test.mjs scripts/cloudflare-runtime-config.test.mjs scripts/cloudflare-deploy-preflight.test.mjs
node scripts/security-check.mjs
node scripts/check-account-deletion-pages.mjs
node scripts/check-pricing-policy.mjs
```

After deploying the matching backend revision, run the read-only aggregate health check:

```sh
node scripts/referral-health-check.mjs
node scripts/referral-health-check.mjs --require-active
```

The ordinary check permits an intentionally inactive, otherwise healthy campaign. `--require-active` also fails when disabled; neither command changes configuration or grants rewards. Both fail for unavailable delivery metrics, failed deliveries or pending rewards older than 15 minutes, including while a campaign is paused. API health now includes aggregate queue diagnostics even when new participation is disabled. A health-fetch failure must not take ordinary gameplay offline merely because an optional campaign is paused. This is on-demand/deployment verification, not a created or scheduled monitor. The publisher explicitly deferred monitoring setup.

## Physical checks, to be recorded later

1. Confirm the actual Play-installed package, version and app-signing certificate. `adb shell dumpsys package com.rekixo.ludopaws` and `adb shell pm get-app-links com.rekixo.ludopaws` provide local evidence. Do not uninstall an existing app or clear its data merely to obtain a green result. The existing manually installed tester cannot be retroactively attributed to a later invitation.
2. While activation is OFF, verify the honest unavailable state, disabled reward verification, working ordinary sharing/navigation and no reward grant. Declining optional verification or using a restricted privacy category must not start Install Referrer or Integrity SDK calls or restrict ordinary gameplay.
3. A positive referral case requires a separately authorized, genuinely eligible live campaign and two real participants: an inviter and a genuinely new friend who uses the invitation download button before installing. Do not mark `LUDOPAWS_REFERRAL_RELEASE_VALIDATED` true just to make this test available. If pre-activation end-to-end validation cannot be performed under the approved release procedure, record it as unperformed rather than manufacturing evidence or adding a bypass. Staging was explicitly declined.
4. Record successful real Google licensing, app/certificate/version and device verdict checks, then server-confirmed binding before the match. Do not store tokens, Google identities, emails, private keys, raw click tokens or account credentials in screenshots, logs or this repository.
5. Finish a natural Online or Friends match: no bots/forfeits, at least three minutes, at least one legal move by the new friend, all four winner tokens home. Test the friend losing as well as winning. Only the inviter receives Pearl Paw Dice, Blossom Garden Board, Rabbit and 100 Gems. Existing cosmetics and selections remain intact; the friend gets no referral reward.
6. Verify safe retries/restarts, repeated refresh/match delivery and all-owned bundles: exactly one 100-Gem grant, no duplicate entitlements and no local fallback. With specifically identified disposable test accounts, cover consent withdrawal/account switching, provider unavailability, full-wallet delayed delivery and account deletion. Never delete ordinary player accounts as a test.
7. Check minimum supported Android (API 28), accessibility/large fonts, share/browser unavailability and offline recovery. Record actual results; QA instrumentation, synthetic SQLite load and provider mocks are supporting checks, not substitutes for these observations.

## Launch record

Keep a dated record of the validated source/artifact hash and versionCode, Play app-signing fingerprint, device/OS, approved terms version, SDK/data review, actual invite/install/verification/match/reward results, duplicate-delivery outcome and reviewer decision. Leave unperformed fields explicitly unperformed. Confirm the immutable campaign start and minimum eligible versionCode only for the approved launch. Complete next-update Data safety/region decisions without modifying the currently in-review submission; the implementation worksheet is in `REFERRAL_DATA_SAFETY_HANDOFF.md`.

Configuration/health output is not Google policy approval, legal certification, proof of one unique human or an unlimited-capacity guarantee. Live capacity, provider quotas and alerts require their own operational decisions before broad promotion.
