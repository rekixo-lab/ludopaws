# Phase 1 Safety Implementation — review required

Branch: `fix/phase1-safety-hardening-20261020` • PR #295 (draft).

## Implemented in this branch

- Strict server-side JSON integer validation for MOVE tokenIndex and eventIndex. No null / false / string conversion.
- Authenticated Friend Block / Unblock. Blocking deletes the active friendship, pending requests, accepted/pending invites, and the conversation index. Mutual contact is disabled even if only one party blocked the other. Unblocking does not automatically restore a friendship.
- Authenticated Friend Reports, enumerated reasons, idempotent UUID, 10 reports per reporter per rolling day and public API/IP rate limits. Only report metadata is stored, not message content.
- Admin moderation report queue and decision endpoint: GET `/api/admin/moderation/reports` and POST `/api/admin/moderation/reports/resolve` authenticated by an account session and optional server-side allowlist `LUDOPROOF_MODERATION_ADMIN_ACCOUNT_IDS`. When unset, the public admin endpoints fail closed.
- Report metadata is removed when a related Friend identity is deleted. A Durable Object alarm prunes reports after 90 days, subject to operational testing.
- Android Friends menu offers Block and Report; blocked list includes Unblock.
- Ad controllers re-check UMP consent at display time. Android builds enabling ads require an explicit `LUDOPAWS_AD_SAFETY_REVIEW_APPROVED=true` Gradle property, in addition to their normal ad flags. This is an explicit release-approval control, **not** a declaration of legal compliance.
- Server-only emergency switch `LUDOPROOF_AGE_SAFETY_MODE=LOCKDOWN` protects external online, social, wallet, purchases and reward routes. No client age header or DOB can bypass it. The switch does not verify age.

## Not completed (do not claim compliance)

1. Real, independently authorized, session/account-bound age eligibility; Google Play Age Signals and Play Integrity do not themselves provide a server-verifiable DOB assertion. Draft PR #293 remains separate and unmerged.
2. Publisher decision on intended age audience, minors, consent, Families, UMP, ad SDK configuration and privacy/retention terms.
3. In-match user block/report access for non-friends, voice participant controls, live moderation staffing/workflow, appeals and content evidence policies. Blocking an existing player does not forcibly terminate an already running multiplayer room or WebRTC session.
4. Physical-device network and first-launch testing, Play Console internal testing, and production secret review.

## Release steps

- Test server suite: `cd server && npm test`, Android build/unit/lint and instrumentation tests. Review CodeQL.
- Test new report and block APIs using a real authorized Friend token and a separate non-admin account (admin APIs must return 403 or 503).
- Configure `LUDOPROOF_MODERATION_ADMIN_ACCOUNT_IDS` only via trusted Cloudflare runtime configuration, using explicitly approved moderator account IDs; never ship it inside Android.
- Validate how any privacy notice describes report retention, action review and user deletion.
- To **disable connected features while age eligibility remains unverified**, activate `LUDOPROOF_AGE_SAFETY_MODE=LOCKDOWN` server-side. This is not an adult authorization substitute and will intentionally disable restricted online paths.
- Do not enable ads by setting only the Gradle approval flag. Approve Play Console audience, independently appropriate UMP/family/ad SDK controls and real-store testing first.
- Do not merge or deploy this draft until full CI, mobile QA, policy and security gates are completed.
