# Referral next-update production review

Follow-up: see [REFERRAL_ACTIVATION_STATUS.md](REFERRAL_ACTIVATION_STATUS.md) for the subsequently authorized build/test results, confirmed Play project link, credential-policy blocker and remaining activation gates. Earlier statements below about unexecuted checks describe the original source-only change.

## Release boundary

The publisher reports the first production submission is **currently in review** and is intended for approximately **177 Google Play countries/regions**. This change prepares a subsequent update. Do not halt, replace, edit or promote the current production submission; do not change track availability or upload an artifact as part of this source change. Internal-track validation can be arranged separately by the publisher. No build/test/lint/install/deploy/upload is performed here.

There is no hard-coded referral country allowlist in the API. Store item availability is separately configurable through the catalog's worldwide/allowlist/blocklist fields. Neither code proves the actual Play Console country selections nor guarantees regional legal eligibility, Play billing/identity support, translations or equivalent consumer requirements in every region. Keep application distribution and referral campaign activation separate. Never invent a list of 177 countries from an approximate count.

Play Console Developer account was inspected on October 27, 2026: organization details show **FINWORKAR**, developer name **Rekixo**, and the verified website `https://rekixo.com/`. Next-update notices use that identity without inventing a corporate suffix. Existing game terms are draft-marked; console identity verification is not legal approval for every distribution region. See the activation status for completed tests and remaining release gates.

## Hardening included

- Separate policy, SDK and real-release validation gates, all default OFF; a numeric minimum production versionCode is required for incoming Integrity verification. Campaign dates become immutable after the coordinator first uses them.
- Explicit versioned opt-in before Install Referrer/Integrity SDK calls; existing neutral privacy screening is retained on each device. Children/unknown-age settings cannot start those optional SDK calls. The neutral screen is not independent age verification or parental consent. Guest gameplay/ordinary non-reward sharing stay available.
- New enroll/withdraw endpoints, server participation checks, persistent consent revision cancellation on account switch/withdrawal and local account-deletion cleanup. Google-subject participant history prevents known campaign participants deleting/recreating their account to appear new. It is not a universal device or human identifier.
- Rechecks after asynchronous provider verification prevent a consumed/expired/deleted/withdrawn invitation or campaign from being committed. Duplicate concurrent requests retain one account/subject claim and one campaign wallet grant.
- Account-based quotas plus less restrictive shared-network IP ceilings; an explicit same-origin POST download button prevents cross-site auto-submitted attribution. A qualified inviter's link still permits ordinary Play download without manufacturing another reward claim.
- Durable minimal terminal-match outbox survives normal room cleanup, retries across process restarts and keeps gameplay response dispatch asynchronous. Full match state still follows normal room retention. Pending minimal events expire at campaign end +14 days, with a non-identifying retention-expiry diagnostic; infinite outage recovery is not claimed.
- Fair due-time ordering, leases and exponential backoff for reward delivery; a failing/full-wallet account cannot permanently starve the first 50 queue slots. The five-minute maintenance cron is independent of billing enablement and repairs pending delivery/retention maintenance if an alarm was missed. No duplicate reward on acknowledgement loss.
- Operational readiness reports configuration gates, campaign-date consistency, pending/oldest deliveries and failure counts without exposing credentials or player IDs. These diagnostics are not an assertion that Google credentials or a legal review have been independently verified.
- Native page distinguishes missing invitation, ineligible install, provider outage, verification pending, bound invitation, withdrawal, qualified match, server grant and delayed local-wallet sync; no silent local reward fallback. Foreground polling is bounded and cancelled on pause. Pending verification is throttled and manually retryable; share chooser and navigation remain explicit.
- Added regression test **source** for feature gates, opt-in/withdrawal, wallet atomicity/idempotency, all-owned bundles, refund provenance, immutable dates and outbox restart recovery. Tests were not executed; no passing result is claimed.

## Mandatory operator checks before enabling the next update

1. Confirm the exact Play listing publisher identity, support contact, actual distribution regions and current in-review release details in Play Console. Do not modify that in-review release to validate this feature.
2. Review the optional same-game invite-and-play incentive, Families/mixed-audience eligibility, SDK/provider terms, regional consumer/privacy requirements and required translations. Do not pay for installs, manipulate rankings or require a rating/review. The feature flags are publisher controls, **not approval received from Google**.
3. Finalize and publish `referral-terms.html`, updated public/in-app privacy notices and the existing draft-marked legal terms for the next update. Update the Play Data safety answers for actual deployed processing. Review retention of minimal outbox evidence and participant anti-replay hashes, including deletion/withdrawal handling.
4. Provision the production Play app-signing certificates, linked Integrity Cloud project, service-account credentials and immutable start time. Set `LUDOPAWS_REFERRAL_MIN_VERSION_CODE` to the new, actually validated Play build. Never put secrets into commits or chat. Minimum version checks apply to referred-install attestation, not to a claim that every authenticated inviter runs a particular binary.
5. Use an eligible Google Play **internal testing track** with production package identity/signing, tester licenses/OAuth configuration and actual new eligible accounts/devices. A sideloaded `.dev`/`.qa` build is not a production Integrity bypass. Do not promote or halt any track automatically.
6. Validate Online/Friends natural finishes (friend losing included), no-invite install, bots, forfeits, deadlines/timezones, app/worker restart, consent withdrawal during provider calls, account switching/deletion, duplicate bind/match/status/grant requests, provider 429/5xx, missing credentials, full wallet, all-owned bundle and Play refund after gifting. Validate accessibility/large fonts, unsupported devices, browser/share unavailability and offline recovery.
7. Validate actual SQLite/Cloudflare integration, provider attestation, App Links and oldest supported Android release. The new backend schema is additive to the existing ReferralCampaign class; existing v15 binding/migration must exist on the target Worker. Roll out backend before exposing the compatible client. Ensure old clients cannot bypass new participation or attestation requirements.
8. Load-validate the single campaign coordinator and account-directory hops for expected traffic. Configure Cloudflare alerts for Worker/DO errors, provider quotas/429s, oldest pending delivery and outbox retention expiry. Shard coordination before traffic exceeds validated limits; no unbounded-throughput claim is made.
9. Only after successful external checks set `LUDOPAWS_REFERRAL_POLICY_APPROVED=true`, `LUDOPAWS_REFERRAL_SDK_REVIEW_APPROVED=true`, `LUDOPAWS_REFERRAL_RELEASE_VALIDATED=true` and `LUDOPAWS_REFERRALS_ENABLED=true`. A configuration flag cannot substitute for an actual validation record. Rollback pauses this optional campaign without disabling normal gameplay; already-recorded awards remain pending for safe retry.

## Explicit limits

Play Integrity and Install Referrer are not independent proof of one unique person or a signed server-to-server install receipt. Fresh Google identities on a genuine device can still be abused; device/account lending or collusion is not fully detectable. Device Recall remains a separate Google beta, **not integrated or represented as enabled**. Do not replace it with prohibited fingerprinting, advertising IDs or a hard ban on shared Wi-Fi. Decide on stricter approved abuse controls and fraud-loss thresholds before large-scale promotion.

No production-ready certification is made while the required runtime/provider/load/device/policy validations remain unperformed. No existing submission, review or published service has been altered by this source-only change.

## Official review references

- [Play ratings, reviews and installs policy](https://support.google.com/googleplay/android-developer/answer/9898684)
- [Families data practices](https://support.google.com/googleplay/android-developer/answer/11043825)
- [User data and disclosure](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Standard Integrity request binding](https://developer.android.com/google/play/integrity/standard)
- [Device Recall beta restrictions](https://developer.android.com/google/play/integrity/device-recall)
- [Play country/region distribution](https://support.google.com/googleplay/android-developer/answer/7550024)
