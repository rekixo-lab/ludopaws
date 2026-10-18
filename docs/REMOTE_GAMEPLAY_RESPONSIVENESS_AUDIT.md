# Remote Gameplay Responsiveness Audit

Date: 2026-10-16
Scope: Android remote gameplay (Online, Friends, Team Up) compared with local Robot / Pass & Play.

## Confirmed architecture

- Local gameplay uses `LocalMatchSession` / `OfflineGameEngine` and renders roll/move state immediately after the local engine applies it.
- Remote gameplay uses server-authoritative MatchSnapshot state. rollVerifiedDice() starts local dice animation and tactile feedback, then awaits one native POST roll request. The authoritative match response settles the die.
- Remote moves show a presentation-only pawn preview, then await the authoritative `POST /move` response; the preview duration is deducted from the remaining animation hold to avoid replaying the entire animation duration.
- Team Up action owner is `actingSeat ?: turnSeat`; it must not be collapsed to `turnSeat` because partner handoff is a supported action transition.
- The shared `LudoPawsGameplayPacingPolicy` already centralizes result holds and movement/capture timing. These values must not be changed to paper over network latency.

## Confirmed latency / consistency risks

1. **Multiple state reconciliation triggers.** WebSocket `STATE_CHANGED` events schedule a state GET with a 120 ms debounce. A separate polling runnable also requests state every 30 seconds while realtime is connected and every 3 seconds while recovering; connection success starts another immediate GET. Mutation requests themselves already return authoritative state. This is not proof that each roll sends multiple GETs, but it creates avoidable contention and complicates freshness.
2. **Unconditional snapshot application.** `applyResponse()` parses the response and immediately replaces `currentState` without first comparing the snapshot's revision to the already-applied authoritative revision. A delayed response can therefore render an older state and cause incorrect animation/turn presentation.
3. **Single-request native roll.** One native request handles roll validation, secure random generation and persistence in the match room. UI must start immediately, but should never fake the result or loosen the proof boundary.
4. **Additional input locks.** Online input is blocked during network requests and until presentation animation completes. This is correct in principle, but all paths that settle/fail/reconcile must release the lock consistently and must not extend it by replaying a completed hold.
5. **Robot / Pass & Play baseline should be preserved.** No edits to local engine/game rules or its pacing values are in scope for this remote responsiveness issue.

## Safe optimization plan

- Add a pure authoritative snapshot freshness policy based on match identity, source, and monotonically increasing server revision. Cache snapshots are display-only; they must never replace an authoritative snapshot or enable actions. Drop older authoritative responses before running animation, banners, turn controls, cached writes, or pending-secret reconciliation.
- Coalesce state refreshes using an in-flight/dirty mechanism: if another refresh is requested while one is running, record that a follow-up may be needed instead of queuing duplicate GETs behind a gameplay mutation. On a completed authoritative mutation response, satisfy pending refresh demand when its revision is current.
- Keep realtime reconnect and bounded recovery polling. When connected, use realtime revisions as the primary invalidation signal and keep a slower safety poll; do not remove recovery fallback without measurements.
- Add stage timing telemetry in debug/test builds: local tap, commit RTT, reveal RTT, authoritative response application, dice settle completion, move RTT, animation unlock. Do not log client seeds, bearer tokens, proof secrets or personal content.
- Add tests for stale revision rejection, cache-vs-authoritative precedence, duplicated refresh coalescing, failed refresh recovery, roll recovery after process/network interruption, no-legal-move, third six, extra turn/capture, and Team Up partner handoff.
- Run Android unit tests, lint and APK/AAB builds plus server `npm test`; compare on a physical device with stable Wi-Fi and simulated high latency before choosing further timing changes.

## Deployment implication

The initially identified risks are primarily in Android request orchestration and presentation-state reconciliation. No server logic change is justified by this review alone. Backend changes should be considered only if stage measurements or server tests show slow native roll or persistence handlers or a protocol defect. If only Android files are changed, an Android app rebuild/release is required; a Worker deploy is not.
