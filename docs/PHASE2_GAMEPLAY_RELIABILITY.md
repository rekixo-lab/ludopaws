# Phase 2 — Gameplay Reliability Acceptance Matrix

Scope: Android + Cloudflare Worker, built on draft Phase 1 PR #295. This phase is separate PR #296 and **must not be merged or deployed independently**.

## Code changes

- Post-win ad entitlement checks may never delay exit indefinitely: when network verification exceeds 1.2 seconds, skip the interstitial and navigate. A one-shot latch makes network completion, timeout and Activity destruction mutually exclusive.
- WebRTC UI says **Connected** only when actual `PeerConnectionState.CONNECTED` has been observed, rather than when a peer object is allocated.
- Candidate queue waiting for remote SDP is capped at 64 per peer; overflow discards the oldest signaling candidates to prevent unbounded memory consumption. TURN support requires a separately approved, credential-limited provider.
- Regression tests verify Android socket-to-REST fallback preserves roll/move idempotency keys, refuses to repeat authoritative socket rejections, and Team Up server retries cannot create a second roll.
- Simulated Friend match voice socket dropout/rejoin tests verify stale participants disappear from `VOICE_PEERS` and unauthorized Online rooms cannot use Friend voice signaling.

## Automated validation

Run the following for the **exact commit** before merge:

1. `cd server && npm test` — server realtime, idempotency, Team Up and voice signaling tests.
2. `cd android && ./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleRelease :app:bundleRelease`.
3. CI Android emulator instrumentation and release-readiness workflow.
4. CodeQL and Cloudflare Worker dry-run.
5. Verify the PR head CI commit equals the review/merge commit. No assumptions based on a previously green branch.

## Real Android device acceptance (must be performed before multiplayer release)

| Scenario | Test setup | Must observe |
|---|---|---|
| 2-player Online | Two physical Android phones, different accounts, Wi-Fi/mobile | Single authoritative roll, legal move, matched revisions and winner |
| 4-player Online | Four devices, different networks | No duplicated or missing turns/captures, identical player-state revisions |
| Team Up | Four devices; alternating team turns | Team ownership remains correct after disconnect/reconnect |
| Friends invite | Two accounts plus rejected/expired invite | Unauthorized join rejected and accepted invitations connect once |
| WebSocket lost ACK | Drop incoming WebSocket acknowledgment after server applies roll | REST fallback reuses exact request ID; one roll, one revision |
| Packet loss / jitter | Simulated 5–20% packet loss, 150–500 ms latency, 30-second network drop | UI reports recovering; no extra move/dice, reconnect restores latest state |
| Background / foreground | Home button and screen lock during move and voice call | No old socket falsely reconnects; current state refreshes, voice rejoin explicit |
| Wi-Fi to mobile | Switch network mid-match on both Android versions | Reconnect and state compare; no lost wallet or match identity |
| Post-win exit | Delayed/blocked entitlement response (30s artificial server delay) | Exit occurs within ~1.2s plus UI scheduling; no late ad appears after navigation |
| Ad entitlement | Wallet contains adsRemoved=true, false and invalid ownership | Only verified entitled state permits display; expired request always skips ads |
| Voice restricted NAT | Mobile provider and enterprise firewall, with explicit permission | Status never falsely says Connected; unavailable TURN is reported transparently |
| Old APK protocol | Obsolete `/roll/commit` and `/roll/reveal` requests | HTTP 410 `ROLL_PROTOCOL_UPGRADE_REQUIRED`; never a second dice result |
| Crash / ANR | Android 10, 13, 16; low-memory and app rotation | No Activity leak, no stuck loading, stable return to home |
| Social safety | Friend Block / Report, ongoing voice, nonfriend match | Do not re-enable social/multiplayer until Phase 1 remaining gates are met |

## Release blockers independent of green CI

- Phase 1 still **forces offline-only age-safety lockdown** and its trustworthy server age-eligibility binding remains unimplemented. Phase 2 code tests can run against fake rooms/emulators, but do not imply live public multiplayer has been authorized or smoke-tested.
- Real-device two/four-player gameplay tests and Google Play internal-track review cannot be certified from GitHub Actions alone.
- Active voice peer blocking, TURN relay operations, parental/UGC moderation policy and old-client update UX must be reviewed before a public multiplayer launch.
- Always verify publisher-controlled pricing, age, privacy, consent and rollout settings independently.

## Rollback

Keep Phase 1 as the base branch. A Phase 2 regression is rolled back by reverting the isolated Phase 2 merge/commit without removing the Phase 1 safety gate. Never disable age-safety lockdown merely to make a release smoke test pass.
