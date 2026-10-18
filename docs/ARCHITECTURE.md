# Ludo Paws Architecture

Ludo Paws is split into a native Android client and an authoritative Cloudflare Worker/Durable Object backend.

Android client → Ludo Paws API Worker → authoritative match Durable Object → persisted match state, history and native dice receipt → state response/realtime event → Android rendering.

## Online authority

The match room is authoritative for player seats, turn order, event-index allocation, dice outcomes, legal token moves, captures, safe cells, extra turns, three consecutive six handling, winner determination, reconnects and duplicate-request behavior.

## Native roll lifecycle

1. Android creates a fresh client seed and a domain-separated commitment.
2. The API validates the request and commitment, applies rate limiting, and routes one request to the match room.
3. The room authenticates the active player and checks turn and pending-roll state inside its serialized mutation queue.
4. The room obtains secure random bytes from the Worker runtime and maps them to a uniform face 1–6 with rejection sampling.
5. The native receipt binds the face to the match, event, player, ruleset/state hashes and a receipt digest.
6. The same authoritative transition applies the Ludo rules and persists the outcome/history before returning state.
7. Retries with the same commitment replay the stored result rather than generating a replacement outcome.

No external dice API, third-party dice worker, bearer token or upstream health check is part of the game runtime.

## Offline authority

Robot and Pass & Play use Android's native secure-random engine, game rules and a local receipt. Since the device controls its own state, a local receipt is an integrity/replay aid rather than independent remote proof.

## Rules

Rules remain explicit and versioned: 2–4 players, four tokens per player, six to leave the yard, exact roll for home, extra turns on six and capture, third-consecutive-six forfeiture, and the configured safe-cell list. A material rules change should be tested and released as an intentional ruleset update.
