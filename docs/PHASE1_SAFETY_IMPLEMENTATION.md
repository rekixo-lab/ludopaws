# Phase 1 Safety Implementation — review required

Branch: `fix/phase1-safety-hardening-20261020` • PR #295 (draft).

## Implemented in this branch

- Strict server-side JSON integer validation for MOVE tokenIndex and eventIndex. No null / false / string conversion.
- Authenticated Friend Block / Unblock. Blocking deletes the active friendship, pending requests, accepted/pending invites, and the conversation index. Mutual contact is disabled even if only one party blocked the other. Unblocking does not automatically restore a friendship.
- Authenticated Friend Player and **Message Reports** with server-validated message identity, bounded 10/day report rate, idempotent UUID and public IP limits. Moderation stores the message ID (not message text) and reporter/target metadata; private message content remains in the conversation store.
- Human-controlled moderator decisions can apply time-bounded (1, 24, 168, 720 hour) social suspensions; suspended Friend credentials cannot access directory messaging/invites. This does not currently revoke an already-established realtime game/voice session.
- Admin moderation report queue and decision endpoint: GET `/api/admin/moderation/reports` and POST `/api/admin/moderation/reports/resolve` authenticated by an account session and optional server-side allowlist `LUDOPROOF_MODERATION_ADMIN_ACCOUNT_IDS`. When unset, the public admin endpoints fail closed.
- Report metadata is removed when a related Friend identity is deleted. A Durable Object alarm prunes reports after 90 days, subject to operational testing.
- Android Friends menu offers Block and Report; blocked list includes Unblock.
- Ad controllers re-check UMP consent at display time. Android builds enabling ads require an explicit `LUDOPAWS_AD_SAFETY_REVIEW_APPROVED=true` Gradle property, in addition to their normal ad flags. This is an explicit release-approval control, **not** a declaration of legal compliance.
- Production runtime generator now **forces** `LUDOPROOF_AGE_SAFETY_MODE=LOCKDOWN`; deploy preflight rejects monetization while locked down. Wrangler source config also defaults to LOCKDOWN. The live smoke verifies denial of connected routes instead of running a game while locked. No age header or DOB bypass exists.
- The Android release candidate uses a safe offline-only navigation gate: remote modes explain the temporary restriction and offer local Computer play. It skips automatic Play Games and guest-account bootstrap before a connected release is approved. Android Billing and Ads are blocked during the offline-only gate.
- These release controls intentionally disable multiplayer and paid features. **Do not market this as a complete connected game release.** They provide a safer offline testing candidate, not age verification.

## Not completed (do not claim compliance)

1. Real, independently authorized, session/account-bound age eligibility; Google Play Age Signals and Play Integrity do not themselves provide a server-verifiable DOB assertion. Draft PR #293 remains separate and unmerged.
2. Publisher decision on intended age audience, minors, consent, Families, UMP, ad SDK configuration and privacy/retention terms.
3. In-match user block/report access for non-friends, active-session voice participant controls, live moderation staffing/workflow, appeals and content evidence policies. Blocking an existing player does not forcibly terminate an already running multiplayer room or WebRTC session.
4. Physical-device network and first-launch testing, Play Console internal testing, and production secret review.

## Release steps

- Test server suite: `cd server && npm test`, Android build/unit/lint and instrumentation tests. Review CodeQL.
- Test new report and block APIs using a real authorized Friend token and a separate non-admin account (admin APIs must return 403 or 503).
- Configure `LUDOPROOF_MODERATION_ADMIN_ACCOUNT_IDS` only via trusted Cloudflare runtime configuration, using explicitly approved moderator account IDs; never ship it inside Android.
- Validate how any privacy notice describes report retention, action review and user deletion.
- Release configuration **already forces** `LUDOPROOF_AGE_SAFETY_MODE=LOCKDOWN` while independently authorized eligibility is unavailable. Keep it locked; do not use a manual environment override to simulate an eligibility verifier. Production multiplayer is intentionally unavailable in this candidate.
- Do not enable ads by setting only the Gradle approval flag. Approve Play Console audience, independently appropriate UMP/family/ad SDK controls and real-store testing first.
- Do not merge or deploy this draft until full CI, mobile QA, policy and security gates are completed.
