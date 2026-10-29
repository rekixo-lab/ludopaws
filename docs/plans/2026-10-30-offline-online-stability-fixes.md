# Offline / online stability fixes — 30 October 2026

Branch: `codex/ludopaws-next-updates`. Follow-up to the 29 October behavior audit. This is source stabilization; payment phase 9, final Play/device acceptance phase 10 and live price changes remain separate.

## Changes mapped to the audit

| Finding | Implemented behavior |
|---|---|
| F1 — Team Up exit | Back and the toolbar leave action finish locally without waiting for cancellation. Cancellation intent and the existing request ID are durable and account-scoped. Home can reconcile explicit pending cancellations when connected. Unknown cancellation responses retain the ticket. |
| F1 — cancellation/assignment race | A `MATCHED` cancel response is not treated as a complete match: the client persists an assignment-pending state and obtains the full authoritative assignment from status. Already-finished verified matches can open their results. Background recovery never navigates or discards a matched assignment. A late cancellation response cannot clear a newer search. Pending/queued search creation is registered before dispatch: cancellation and status polling cannot overtake it, including across Activity recreation. In-flight creation settles before saved cancellation is reconciled. |
| F2 — late foreground callbacks | Team Up and Friends defer UI/navigation results while stopped, discard them on destruction and check account ownership on foreground delivery. Friends resets its cached UI identity when the active account changes. |
| F3 — owned offline appearance | A verified-owned, shipped Board/Dice can be equipped locally without buying it again or calling the server first. The equipment override is account-scoped, durable and reconciled separately. It never adds owned IDs, Gems, payment receipts or ad progress. Old acknowledgements cannot erase a newer local selection. Account deletion clears the new local preference. |
| F4 — stale metadata | `contentAvailable` continues to describe local renderer readiness. A separate `purchaseAvailable=false` prevents cached offers spending value. Cached metadata loads before session bootstrap, so expired credentials do not prevent offline discovery. New unlock requests still require fresh offers/server validation. |
| F5 — Team Up connectivity | The label/indicator reflect validated connectivity. Offline search dispatch is blocked; saved tickets remain, polling pauses and resumes on reconnect. Poll failures back off. |
| F6 — Shop recovery | A foreground reconnect refreshes catalog/status/wallet/Play readiness and pending reward recovery. Refreshes coalesce. Tabs, scroll, spotlight position and selected product survive. Reconnect itself never opens a payment sheet or shows an ad. Offline purchase/ad taps explain the connection requirement immediately. |
| F7 — secondary screens | Profile, Leaderboard and referral status refresh on connectivity return. A reconnect during an existing refresh schedules one follow-up refresh. Leaderboard cache and callbacks remain account-scoped. |
| F8 — ordinary sharing | Offline primary Share uses the normal game link. If reward-link creation fails, ordinary sharing remains available with explicit copy that sharing alone does not grant Gems. |
| F9 — Home clarity | Home displays a compact offline indicator and explains online prerequisites at remote-mode entry while keeping offline modes available. |
| Promotional UI follow-up | Offline/stale offers keep their actual artwork and show connection-specific action copy instead of implying a reward can be verified offline. Local Board changes also refresh their visual environment. |

## Safety boundaries

- New ownership, currency spending, purchase fulfillment and verified ad rewards remain server-authoritative.
- Local appearance selection accepts only cached-owned, locally renderable Board/Dice (or already-shipped free content). Account switching does not carry paid ownership to another account.
- A stale catalog never becomes a fresh purchasable offer merely because its artwork is available.
- A cancellation only clears the matching saved request after a recognized server cancellation/idle result. A matched assignment is retained for foreground reconciliation.
- No production backend deployment, Play upload, real checkout, real ad reward claim or live price update is part of these changes.

## Verification

- Full local Android unit suite: 548 tests passed, zero failures/errors/skips, including new foreground-delivery regression tests.
- New Android instrumentation cases compile: owned appearance/account isolation, cancellation persistence/new-request protection, matched cancellation transition and immediate Back navigation.
- Debug lint completed without errors (561 warnings and one hint remain). The existing warning count is not claimed to be zero.
- Repository script tests: 64 passed.
- Source gates: 29 checks passed, including offline/online integration, animal-store availability, pricing policy and repository security.
- Final cancellation-protocol edits are recompiled/retested before push; isolated emulator CI is requested on the pushed source. The handoff message records its actual result/run URL.

## Physical-device acceptance still required

ADB previously reported the Samsung debugging transport offline. This does not establish the phone's internet state. Automated tests must run only on an isolated emulator; these tests clear fixture account/wallet preferences and must not be pointed at a player's normal installation.

On the phone, check airplane-mode cold launch, CPU/Pass & Play interruption/restoration, offline owned Board/Dice changes, Shop reconnect without leaving its tab, pending ad/payment verification recovery, Team Up Back while disconnected, cancellation racing a match assignment, backgrounding while a match response arrives, and offline/plain Share. Real Google Play checkout/SSV acceptance remains in the final release plan.
