# Online transport and coordinated rollout

Online, Friends and Team Up share the same authoritative command and presentation path. Robot and Pass & Play retain their local engine and EntroNex v4 derivation.

## Command contract

`POST /api/matches/{matchId}/roll` accepts a UUID `clientRequestId` and integer `expectedEventIndex` from the current snapshot. The authenticated match room generates a fresh cryptographic seed, samples an unbiased die by rejection sampling, applies the existing rules and saves the result with its retry receipt in one state write. There is no external dice Worker or shared provider queue.

The existing authenticated `/events` WebSocket also accepts:

```json
{
  "type": "command",
  "requestId": "a UUID for reply correlation",
  "action": "ROLL",
  "body": { "clientRequestId": "the same UUID", "expectedEventIndex": 0 }
}
```

`MOVE` uses `tokenIndex`, `eventIndex` and `clientRequestId`. Replies are `{type:"command_result",requestId,statusCode,response}`. Socket commands enter the same authorization, mutation queue and room-local rate limits as HTTPS commands. The actor comes from the authenticated socket's server-owned player tag.

Android shares one OkHttp connection pool across REST, realtime and remote-exit calls. When a socket acknowledgement is lost or the connection drops, HTTPS fallback sends the identical command body. The latest 64 roll receipts survive room eviction/restart. A retry outside that retained window is rejected by its old expected event index rather than creating another roll. Move retries are scoped to the original event and token.

`SYNC` and `STATE_CHANGED` carry compact public snapshots. Android applies them directly through its existing schema validation, revision checks and animation event keys. The other player no longer needs a separate GET after every revision. Periodic state requests remain as a recovery/watchdog mechanism, not a prerequisite for normal actions. Cached boards remain presentation-only.

Only the latest roll is exposed in public history; internal receipts, seeds, bearer credentials and authorization hashes are excluded from broadcasts. The caller can receive its own reproducible server receipt. This establishes internal consistency and does not independently prove the server did not choose another seed.

## Preserved behavior

- V4 opening bonus, capture, safe-cell, extra-turn and third-six rules.
- Team assignment, teammate handoff, stack protection and team forfeiture.
- 15-second roll deadline, 45-second move deadline and four-miss forfeiture.
- Matchmaking, legal server bot moves, Friends invitations and optional voice/chat.
- Existing local animation pacing and optimistic pawn preview with authoritative reconciliation.

New rooms default to V4. Older pure rule definitions and the remaining lifecycle/team/social layers stay available to preserve tested behavior; this change removes the remote service hops, proof archive and obsolete recovery layers. It does not replace the entire rule engine or all account/social routing.

Match placement uses a best-effort location hint from trusted Cloudflare request geography, including matchmaking-created rooms. A validated `LUDOPROOF_MATCH_LOCATION_HINT` can override it. No geographic region is hardcoded as the audience default.

## Validation on 19 October 2026

- Server suite: 238 tests passed, including all three modes, concurrent duplicates, restart replay, failure after durable save, delayed retries after retention, authorization, bots and lifecycle deadlines.
- Android unit tests, instrumentation-test compilation, debug APK assembly and release lint passed. TLS/MockWebServer tests cover shared connections, WebSocket commands, acknowledgement loss, errors, snapshot identity and terminal shutdown.
- Repository security, policy/source checks and Worker dry run passed.
- The production smoke script passed against local Cloudflare `workerd`: profile authentication, single-call roll, exact retry, legal move and private Friends invite/join/start.

A local TCP proxy delayed traffic 150 ms in each direction, simulating a 300 ms RTT. Real Worker HTTP and WebSocket handlers were exercised, including authenticated Friends joins and four-seat Team Up. One sample per mode gave:

| Mode | Own roll snapshot | Opponent roll snapshot | Command acknowledgement | Public snapshot bytes |
| --- | ---: | ---: | ---: | ---: |
| Online | 329 ms | 329 ms | 479 ms | 1,985 |
| Friends | 320 ms | 320 ms | 474 ms | 1,982 |
| Team Up | 320 ms | 320 ms | 469 ms | 2,556 |

Both roll and move retries over HTTPS preserved the committed result and revision. These are local simulated-network checks, excluding connection setup. The proxy also serializes delayed chunks, so acknowledgement timings are not production latency estimates. No before/after production percentile, FPS measurement or physical-phone result is claimed. No phone was connected for device testing.

## Rollout

The Android client and Worker must be released together. The old `/roll/commit` and `/roll/reveal` routes return HTTP 410; old clients cannot use the new dice flow. Start fresh test matches after the upgrade. Historical proof documents describe the previous protocol.

The `v13-remove-online-receipts` migration deletes the obsolete `MatchReceiptArchive` Durable Object class. Its external dice service binding, provider secrets/trust variables, receipt route, archival code and deployment synchronization have been removed. Account, wallet and social bindings remain.

The deployment workflow still requires an explicit operator dispatch on `main`. Changes are prepared for review and have not been deployed by this task. Before release, test two physical phones in Online, Friends and Team Up under packet loss, Wi-Fi/mobile handoff, background/resume and slow networks, and measure action-to-snapshot p50/p95 on the intended production regions.
