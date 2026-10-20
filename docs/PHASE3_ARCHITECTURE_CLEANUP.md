# Phase 3 — Architecture Cleanup and Release Contracts

## Scope and integration order

This refactor is stacked on **Phase 2 PR #296** (which is stacked on Phase 1 PR #295).
It is not safe to merge Phase 3 directly into `main`, since both Phase 1 and Phase 2 remain draft and Phase 1 intentionally keeps production social/multiplayer in offline-only age-safety lockdown. Do not deploy this branch.

## Android dependency map

```text
MainActivity : ComponentActivity
  ├── buildOnlineMatchScreen() [MainActivityScreen.kt]
  │     ├── OnlineLudoPawsPresentation and board renderer
  │     └── shared UI/background/navigation controls
  ├── createRealtimeClient() [MainActivityRealtimeCallbacks.kt]
  │     ├── MatchRealtimeClient (socket, voice signals, revisions)
  │     ├── RemoteMatchSession + GameApi (command transport)
  │     └── main-thread state/revision reconciliation on the Activity
  ├── OnlineGameStateHolder, SecureSessionStore, CachedMatchStore
  ├── PostWinInterstitialExitGate (timeout and one-shot navigation)
  └── FriendsVoiceChatSession (explicit onStop/onDestroy teardown)

FriendsActivity : Activity
  ├── FriendRoomHandoffValidator.kt (authoritative token/session checks)
  ├── FriendsPresentationFormatters.kt (relative time + JSON projections)
  ├── FriendIdentityStore + ProfileStore + SecureSessionStore
  └── GameApi (FriendDirectory HTTP API)
```

The Activity still owns its ActivityResult/permissions, Android lifecycle, UI-thread scheduling, and network executors. The extraction does **not** introduce a new lifecycle owner or background network work.

## Backend dependency map

```text
Worker router/index.js
  ├── MatchRoom (Durable Object / match-room.js)
  │     ├── match-room-state.js (public projections, roll receipts, ruleset hash)
  │     ├── match-room-validation.js (match-mode and player-count input validation)
  │     ├── match-room-http.js (JSON body and wire-error responses)
  │     ├── game.js (authoritative engine / turn rules)
  │     ├── match-events.js (compact roll history)
  │     └── crypto.js (tokens, request IDs, hashes)
  └── FriendDirectory (Durable Object / friend-directory.js)
        ├── friend-directory-schema.js (existing SQL DDL in original order)
        ├── friend-directory-identity.js (IDs, normalized pair, public presence)
        ├── friend-directory-http.js (16 KiB strict HTTP request contract)
        └── crypto.js (authentication and SHA-256 stable IDs)
```

The core dice, move authorization, idempotency, durable serialization, WebSocket signaling and account-safety gates remain in their existing authoritative flows.

## Behavior-preservation invariants

1. `MatchRoom` keeps the `rulesetHashForState` named export. Input and JSON-error contracts remain endpoint-specific.
2. `FriendDirectory` schema statements execute in the same sequence inside `blockConcurrencyWhile`. This refactor adds **no** SQL migrations, DB resets or data conversions.
3. Friend IDs, room IDs and derived request/token hashes remain byte-for-byte compatible.
4. Android realtime listener callbacks are posted back onto the same main Handler, guarded by `canRenderUi` / `screenStarted`, and are owned by the same Activity.
5. Android date labels retain prior edge conditions; injectable clock is only used in tests.
6. Friends session handoff still requires authoritative room, known player ID, matching mode, and valid player token.
7. Match screen view construction still uses the same UI primitives and event handlers; no change to the layout or insets contract was intended.
8. Do not remove or bypass Phase 1 offline-only lockdown, UMP gating, social moderation or Phase 2 duplicate-move/reconnect protections.
9. No new feature flags, SDKs, environment variables or public API endpoints are introduced.

## CI and acceptance criteria

- Run `cd server && npm test` plus the repository security checks, Cloudflare Worker dry-run, CodeQL, Android tests, lint, debug/release assemble and bundle, and Android instrumentation **on the final PR head**.
- Review `server/test/phase3-module-boundaries.test.js` (new contract tests) and `FriendsPresentationFormattingTest.kt`.
- Compare the full stacked diff to Phase 2 to avoid accidentally reintroducing PR #293 age-signals draft implementation.
- For actual release signoff, replay the Phase 2 real-device matrix (`docs/PHASE2_GAMEPLAY_RELIABILITY.md`), including 2P/4P/Team Up, auth, reconnect, voice, background/foreground and ad-entitlement timeouts.
- Green CI alone does not certify Play compliance, client/server account-bound age eligibility or live 2–4-device multiplayer. Keep Phase 1/2/3 outside production until those separate gates close.

## Architecture risks still monitored

The remaining Activity/Durable Object classes are still sizable. This first behavior-preserving split isolates presentation, callbacks, SQL schema and deterministic domain helpers, rather than attempting to move the stateful turn engine, raw durable storage transactions or WebRTC ownership in one unreviewable PR. Continue modularization only when equivalent regression tests cover each proposed stateful extraction. Smaller modules are not worth altered wire contracts or increased game-session risk.

## Rollback

Revert this Phase 3 branch/PR without modifying the existing Phase 2 branch or Phase 1 safety lockdown. Never force-push or merge this phase into `main` independently.
