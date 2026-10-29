# Offline / online behavior audit — 29 October 2026

Reviewed source: `ec3fe2ca`, branch `codex/ludopaws-next-updates`.

## Evidence and limits

This is a source-level flow/lifecycle audit with targeted executable policy tests. It is **not** a completed real-device network-toggle acceptance test and does not claim that every line of the project or every layout size has been exercised.

- Android targeted unit tests: **143 passed in 33 suites**, zero failed/errors/skipped. Selection: `*Offline*`, `*Online*`, `*Startup*`, `*StoreCatalogParserTest`, `*PlayAgeSignalsOfflineEntryTest`.
- Offline integration and online integration source gates: both passed.
- ADB inspection: Samsung SM-A715F at `192.168.1.2:38399` is reported **offline by ADB**. This describes the debugging connection, not the phone's actual internet state. No Wi-Fi/mobile data setting was changed and no account, purchase, reward or match was created.
- Earlier passing emulator results are historical evidence; they do not establish current airplane-mode/reconnect UI correctness.
- No application fixes, Play release, backend deployment or live price changes were made in this audit. Payment phase 9 and release phase 10 remain pending.

## Screen / feature coverage

| Surface | Expected/current offline behavior from code | Online / recovery behavior | Assessment |
|---|---|---|---|
| Native launch / game splash | Local artwork, readiness grace and local watchdog allow entry without waiting indefinitely for account/PGS. | Guest bootstrap can finish asynchronously; explicit retry exists. | Good separation; physical cold launch still needs acceptance. |
| Age/privacy popup / Play Games | Neutral age selection is local; PGS is optional and checks have a timeout. | After eligible privacy choice, SDK bootstrap can recreate Home once and read automatic authentication status. | Does not make offline play depend on Google sign-in; first-launch/recreation should be visually tested. |
| Home | Local navigation, portraits, hero and promotions can render without downloading artwork. | Connectivity monitor updates state and startup banner. | Actual connectivity TextView is hidden; remote actions remain discoverable without a clear offline explanation. |
| Robot / Pass & Play setup and match | Local engine, local randomness/proofs, local checkpoint and result recovery. Offline roll work runs off main; foreground generation prevents old results being applied to a later screen. | No network is required for normal dice/moves/CPU decisions. Stop pauses actions; start resumes from snapshot. | Core offline design is sound in inspected paths; local-storage failures and process-kill restoration still need device tests. |
| Online lobby / public matchmaking / match | Validated connectivity disables network mutations; cached state is display-only. Loss clears action authority. | Reconnect refreshes authoritative state and reconnects realtime. Server turn clocks continue offline and copy explicitly warns about turn expiry. | Good safety behavior; test late responses, flaky Wi-Fi, stale snapshot and repeated reconnect on device. |
| Team Up | Static ONLINE indicator; search can start without a connectivity guard. Back with an active ticket waits for server cancellation. | Two-second polling, request serialization, persisted ticket and assignment checks exist. | Highest-priority UI/recovery weaknesses; see F1, F2, F5. |
| Friends / private invites | Offline status is explicit; runRequest refuses network-dependent actions. | Connectivity return triggers sync; periodic refresh resumes. | Better than Team Up, but in-flight callbacks can still navigate after onStop (F2). |
| Shop catalog / Paws / Board / Dice | Built-in assets and last wallet allow discovery; shipped-free selection has a local path. | Catalog/status refresh on entry, explicit selected-purchase retry, verified fulfillment. | Owned paid selection and cached availability need redesign (F3/F4). |
| Rewarded ads / purchases | New reward requests require server availability/session. No unverified Gems or entitlement should be granted. | Pending purchase/ad records and owner checks support recovery; queued ad load has a timeout and pending show is cancelled on leaving Shop. | Correct verification boundary; lacks unified immediate offline UI and reconnect refresh. Real Google checkout/SSV is not validated here. |
| Share / friendship rewards | Plain Play Store link sharing uses ACTION_SEND and is available independently of reward status. | Campaign/verification is server backed, has request generation guards and manual refresh. | Main Share button can still enter server invitation flow using an older online snapshot after net loss (F8). |
| Profile / Badges / leaderboard | Profile and badges primarily use local data. Leaderboard can show account-scoped last saved ranking with cached copy. | Profile refreshes on resume; ranking refreshes on entry/manual Refresh. | Reconnect while staying on screen is inconsistent (F7). |
| Settings / audio / appearance | Local audio preferences work; free cosmetic selection is local. | Owned nonfree Board/Dice selection calls server. | Same paid-selection limitation and stale-content filtering as Shop. |
| Rating / privacy links / voice | Store/web links are external destinations; their ability to load offline belongs to the external app. | Voice is explicitly unavailable in family edition; fixed quick reactions are the available path. | No claim of offline web/review loading or enabled voice; these are not gameplay connectivity bugs. |

## Findings and concrete remedies

### F1 — High: Team Up Back can depend on a successful network cancellation

**Trigger:** Start/restore a Team Up search, lose internet, then press Back. `onBackPressed` calls `cancelSearch(exitAfterCancellation=true)`. `finish()` exists only inside the successful non-MATCHED cancellation branch; failure only shows a message. This can prevent returning through in-app Back while offline or repeatedly wait for request failure.

**Source:** [TeamUpActivity](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/team/presentation/TeamUpActivity.kt:844) (Back handler at line 112).

**Fix:** Provide immediate local “Leave screen” while retaining an owner-bound durable pending cancellation. Reconcile that ticket once connected, handle cancellation-vs-assignment races authoritatively, and never silently create a second search. Test offline before cancellation, disconnect during cancellation, and server assignment winning the race.

### F2 — High: in-flight Team Up / Friends callbacks can act after leaving the foreground

`onStop` stops polling, but already submitted `runRequest` callbacks check only `isFinishing/isDestroyed`. A stopped Activity can pass both checks. A MATCHED response can then reach `startActivity`/`openMatchedGame` while another screen or app is foreground. Whether Android permits the visible launch depends on OS state; the stale navigation attempt is present in code.

**Source:** [Team Up callback](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/team/presentation/TeamUpActivity.kt:1018), [Friends callback](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/friends/presentation/FriendsActivity.kt:1639). Team assignment opens a match at TeamUpActivity line 967.

**Fix:** Add a lifecycle generation/started guard to UI results and navigation. Persist a verified assignment without navigating when stopped; consume it once, on the next valid foreground entry. Do not discard a real server match.

### F3 — High usability limitation: an already-owned nonfree Board/Dice cannot be switched purely offline

The local selection shortcut accepts only shipped **free** cosmetics. Already-owned nonfree items route to `accountRepository.selectCosmetic` in Shop and Settings. Consequently ownership cached on the device does not provide a local selection-only path; offline selection fails even though the artwork is bundled. This is a design limitation demonstrated by code, not proof that the purchase itself is lost.

**Source:** [Shop selection](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/StoreActivity.kt:960), [Settings selection](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/settings/presentation/SettingsDialogUi.kt:569).

**Fix:** Separate verified ownership from equipped appearance. Permit an account-scoped local equip for an already-owned, locally renderable item; queue only the equipment preference for reconciliation. Keep buying, currency debit and entitlement issuance server-only. Test account switch, expired credentials, restored wallet and conflicting server preference.

### F4 — High: stale catalog status is conflated with missing content, affecting owned/free UI

On refresh failure with a cached catalog, every cached item is copied with `contentAvailable=false`. The same flag is then used by `selectedId`, free-item ownership/selection checks, Settings filtering and promotion candidate filters. For entries present in that cache, a temporary network failure can therefore make shipped/owned artwork appear unavailable, reject a selection, fall back to a default appearance or remove a spotlight. The original safety purpose—prevent stale offers from spending value—is valid, but the flag also disables local rendering/use.

A second path exists: StoreActivity calls `ensureGuestSession()` before repository refresh. If session creation/refresh fails first, the repository's cached fallback is never reached. A valid cached session and an expired/missing session can therefore produce different offline catalogs.

**Source:** [Catalog fallback](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/store/data/StoreCatalogRepository.kt:237), [Inventory selection](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/store/data/local/CosmeticInventoryStore.kt:83), [Shop lifecycle/bootstrap](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/StoreActivity.kt:237) (catalog refresh at line 399).

**Fix:** Separate renderer readiness, offer freshness/purchasability and confirmed ownership. Load last-known catalog metadata before attempting session/network refresh. Stale offers must not spend Gems or launch an unverified purchase, while verified-owned shipped content remains usable.

### F5 — Medium: Team Up falsely labels the screen ONLINE and silently polls through disconnection

The connection label is a literal `ONLINE`; no ConnectivityMonitor exists in TeamUpActivity. `pollSearch` suppresses errors, while the displayed search timer can continue. Starting a search also stores a ticket and shows “Searching for 3 real players” before a successful network request.

**Source:** [Team Up ONLINE label](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/team/presentation/TeamUpActivity.kt:221), [Team Up poll](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/team/presentation/TeamUpActivity.kt:829).

**Fix:** Use the validated connectivity monitor, show Offline/Reconnecting/Searching distinctly, pause poll dispatch while offline, retain the same request ID, and resume reconciliation on connectivity return. Clearly distinguish a local intent from server-confirmed queue membership.

### F6 — Medium: Shop lacks passive connectivity-return recovery

Shop has manual retry improvements: clicking the selected product rechecks availability, catalog status is tappable, and pending reward status has retry. However it does not observe network return like Friends/MainActivity. Remaining on an initially offline Shop can leave stale catalog/status/Play price state until an explicit action or lifecycle restart. Do not describe this as requiring Shop to be closed: manual retry paths already exist.

**Source:** [Shop lifecycle/bootstrap](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/StoreActivity.kt:237); explicit purchase retry at StoreActivity line 487.

**Fix:** On foreground offline→online transition, coalesce catalog/status/wallet/Play-product/reward refresh. Preserve tab, scroll, current spotlight and selected product; never automatically open an ad/payment sheet.

### F7 — Medium: Profile and leaderboard do not recover uniformly on connection return

Profile refresh is tied to onResume; leaderboard refresh runs onCreate and manual Refresh. Neither screen observes connectivity. Thus a user staying on a page after turning internet back on may continue to see a failed status or cached data until manual/lifecycle retry. The leaderboard does correctly disclose cached rankings and scope its cache to the current account.

**Source:** [Profile refresh](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/ProfileActivity.kt:470), [Leaderboard refresh](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/leaderboard/presentation/LeaderboardActivity.kt:123).

**Fix:** Add foreground reconnect refresh with a single in-flight request and account generation checks; retain cached content plus a clear saved timestamp while retrying.

### F8 — Medium: the main Share button can fail offline despite plain sharing being possible

If the screen loaded an enabled, Google-linked campaign status while online, then loses internet, `act()` can take the enrolled/invitation server branch. On failure it only sets progress text. It does not fall back to ordinary link sharing. The separate “Share Play Store link” button remains usable, and initial no-status/loading states already choose ordinary share correctly.

**Source:** [Primary Share action](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/referrals/ReferralActivity.kt:399) (failure handler at line 432).

**Fix:** Treat ordinary sharing and reward invitation generation as separate explicit actions, or detect offline/server failure and offer plain sharing while explaining that an unverified invitation will not earn a reward. Never suggest sharing alone grants 100 Gems.

### F9 — Medium UI inconsistency: Home tracks connectivity but hides the indicator

Home updates `connectivityText`, but it is created `GONE` and excluded from accessibility. Remote mode entry checks build capability and safety notice, not current connectivity; subsequent Main/Friends pages handle the offline condition. As a result Home can offer Online/Team Up actions without an immediate contextual offline explanation. This is an interaction clarity issue, not evidence that offline server actions are accepted.

**Source:** [Hidden connection status](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/home/presentation/components/HomeHeaderUi.kt:75), [Remote mode entry](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/core/navigation/GameModeNavigation.kt:10).

**Fix:** Show a compact offline/reconnecting status when relevant. Keep offline modes available and explain online prerequisites at the entry action; preserve routes for cached/resume discovery rather than indiscriminately disabling the whole UI.

### UX follow-up — promotional Watch Ad / unlock actions should explain offline requirements

Home/Shop promotions select locally known locked items without a connectivity-specific state. New rewarded requests first perform server status/session calls. Offline clicks can therefore wait for a connection error instead of immediately explaining the requirement. Cached ads alone cannot bypass the game's verified-reward flow.

**Source:** [Reward request](/home/rekixo/Documents/ChatGPT/Ludopaws 2/android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/StoreActivity.kt:604); GamePawsPromotionView and StoreLockedSpotlightView.

**Fix:** Keep product discovery/artwork, but label a network-dependent action clearly and provide a deliberate retry. This is a UX recommendation; it does not require granting rewards offline or hiding the user's products.

## Recommended implementation order

1. Team Up offline exit and foreground callback guards (F1/F2); extend the same navigation guard to Friends.
2. Split catalog freshness from asset readiness, then add verified-owned local equipment (F4 before F3). No change to entitlement verification.
3. Shared foreground connectivity recovery for Team Up/Shop, then Profile/leaderboard (F5/F6/F7).
4. Ordinary Share fallback and consistent Home/promotion offline copy (F8/F9/UX follow-up).
5. Re-check all changed paths once and run focused regression tests, then isolated emulator lifecycle tests.
6. Physical-device acceptance: fresh airplane-mode launch; warm disconnect; reconnect without leaving each page; CPU and Pass & Play interruptions; expired session + owned cosmetics; captive portal; server unreachable while Android says validated; disconnect during matchmaking/checkout/reward verification; background while MATCHED arrives; kill/relaunch with saved match and pending reward. Never use real payments/rewards to simulate unverified grants.

## Release interpretation

143 passing policy tests and two passing source gates support the inspected engine/authority behavior. They do **not** prove that every offline UI transition is correct. The above findings require implementation and then device verification. This audit intentionally makes no guarantee about the live v9 payment issue, which remains in the separate payment phase.
