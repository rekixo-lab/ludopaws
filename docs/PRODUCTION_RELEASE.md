# Ludo Paws Production Release Checklist

This checklist covers release configuration and validation. It does not assert that the game has been independently audited or certified.

## Before release

- [ ] Confirm current Android application ID and version code/name.
- [ ] Run the full GitHub Actions CI suite on the candidate commit.
- [ ] Pass server tests and the Wrangler production dry run.
- [ ] Pass Android JVM tests, Release lint and Debug APK assembly.
- [ ] Generate and inspect release SBOM/hash evidence required by the release workflow.
- [ ] Confirm the Cloudflare route, Durable Object migrations, match storage, rate-limit bindings, leaderboard, matchmaking and Friends bindings.
- [ ] Confirm LUDOPROOF_SESSION_HMAC_KEY is configured as a Cloudflare secret and is never bundled into the APK.
- [ ] Confirm Google OAuth web client ID and account web origin.
- [ ] Keep Google Play Billing and AdMob reward features disabled unless their credentials, console setup and end-to-end tests are ready.
- [ ] Keep production deployment as a manual dispatch from main.

## Native gameplay smoke test

After an explicitly authorized deployment, verify that the public API:
1. returns healthy/readiness responses;
2. creates two isolated test players and an active match;
3. accepts one native POST roll request;
4. returns an outcome between 1 and 6 and a native receipt with matching match/event context;
5. persists the roll once and replays duplicate requests idempotently;
6. permits only an authoritative legal move to consume the roll;
7. preserves turn order and online state across WebSocket refresh/reconnect;
8. keeps Friends, Team Up and Quick Chat functional.

Do not call a smoke test successful unless every assertion passed against the exact deployed commit. A dry run validates packaging/configuration only; it does not test production latency.

## Performance acceptance

Capture end-to-end timings for roll request, state persistence, realtime state propagation, move request and reconnect recovery on real devices over Wi-Fi and mobile networks. Compare median and p95 latency, error rates and duplicate mutation behavior before declaring the game smooth. Do not state a latency guarantee without measured evidence.

## Signing and upload

The CI AAB may not be signed with the Play Store release key. Use the authorized release-key procedure, inspect the final manifest and permissions, then upload only after device testing, policy review and release approval.
