# Resume here (Phase 2, updated 2026-11-11)

Owner answers (Phase 1): scope = all blockers B1-B9 and majors M1-M9 before release; dice =
offline plain crypto RNG (rejection sampling), online = server dice; versionCode 17 /
versionName 2.0.0; config from the live repo (public values in git-ignored
`config/release.local.cfg`, CI from GitHub Variables; secrets never in client / repo).
Order: B2 -> B1 -> B3 -> B7 -> B6 -> B4 -> B5 -> B9, M1 early, other majors in between.
STOP before: any push / PR / GitHub branch, first release-workflow run, merge to main, or a
decision affecting players' data / money / account not settled by live code.

Progress:
- [x] **B2 server client** (commit 7517eaa): `scripts/game/online/{game_api,http_transport,
  match_realtime_client,online_status}.gd`; tests `online_contract_smoke`, `compile_all`;
  live read-only check OK on desktop and A71 (`tests/live_readonly_probe.gd`, probe scenario
  `net`). Export: live permissions, min SDK 28 / target 36, local config packed.
  Still to do inside B2: connectivity monitor (Android NetworkCallback via the project
  plugin, B7), pending operations / cached match (with B6).
- [x] **B1 account / session** (commits f128475 + this B1 commit):
  - Session: `LudoPawsPlatformPlugin.java` = live AccountSessionStore unchanged (same prefs /
    Keystore alias / AES-GCM / AAD), so the Kotlin app's session is read as-is after the
    update; `account_service.gd` = AccountRepository (guest, refresh, link, restore, identity
    recovery, guest deletion code, delete, logout). A71: live guest session, reload after
    restart, wallet / catalog / profile authorized.
  - Google sign-in: Credential Manager (`googleSignIn`, GetSignInWithGoogleOption with the
    server nonce, live versions credentials 1.6.0 / googleid 1.1.1) and Play Games v2 22.1.0
    (`PlayGamesStartup.java`, init before the engine only after an adult age choice, live
    manifest entries). Public ids taken from the shipped v16 build into the git-ignored
    config; setup script writes the Play Games id resource. **Untested on device**: both
    need the production package + release signing (OAuth / PGS clients).
  - Age choice now lives in the live `family_privacy_v1` prefs on Android (survives update).
  - Autoload `Account` + Profile: real status line, Manage Account (guest deletion code,
    Save / Restore with Google, Play Games, delete account, online deletion page, privacy
    policy — M8 partly); `online_status.signed_in()` reads the real session.
  - Left for later blockers: Friend / leaderboard identity restore and link after Google
    (B6 stores; restore stays "needs retry" when the server returns one, like live),
    per-account profile scoping `ludoproof_profile` (B7), per-account cache cleanup on
    deletion (B3 / B4 / B6 / B7).
- [x] **B3 wallet (server authority)** (this commit):
  - `scripts/game/platform/native_prefs.gd` + plugin `prefsRead / prefsWrite`: V2 reads and
    writes the live app's own SharedPreferences files with the live keys and types
    (allowlist: `ludoproof_wallet`, `ludo_paws_character_selection_v1`,
    `ludopaws_store_catalog_cache_v1`; never the session / identity stores). Data from the
    Kotlin app is therefore used as-is after the update (no conversion step).
  - `cosmetic_inventory.gd` = live GemWalletStore + CosmeticInventoryStore +
    CharacterSelectionStore rules; `store_service.gd` = live wallet / purchase / select /
    verify / rewards / collections / catalog calls through the account session (guest on
    demand). DesktopBackend is now only the headless / no-config stand-in with
    server-shaped responses.
  - Live StartupSessionCoordinator in the `Account` autoload + Home status line; Home Gems
    bar shows the wallet; Shop / Wallet / Rewards refresh from the server when opened.
  - A71 (read-only): live wallet GET cached in `ludoproof_wallet` (account match, revision),
    catalog parsed (0 published items today), rewards status with 3 collections. Purchases,
    claims and collections were NOT exercised against the live server (owner rule).
- [x] **B7 data migration** (this commit). Approach: no copy step. V2 reads and writes the
  live app's own SharedPreferences files with the live keys (plugin allowlist), so each
  player's data is simply there after the update:
  | Live file | V2 owner | Notes |
  |---|---|---|
  | ludoproof_account_session_v1 (+ Keystore) | account_service (plugin, B1) | encrypted, unchanged |
  | family_privacy_v1 | age_entry.gd | band + policy only |
  | ludoproof_wallet, ludo_paws_character_selection_v1, ludopaws_store_catalog_cache_v1 | cosmetic_inventory / store_service (B3) | |
  | ludoproof_game_settings | game_settings.gd | music, sound, voices, haptics, quick chat, speed, calm motion, play hints |
  | ludoproof_profile | profile_store.gd | per-account JSON, legacy scope, restore / deletion rules |
  | weekly_challenges_v1 | weekly_challenge_store.gd | per owner |
  | ludo_paws_offline_character_setup_v1 | offline_setup.gd | setup + active setup |
  | ludoproof_computer_game, ludoproof_offline_game | saved_match_store.gd (M2) | live checkpoint shape; live saves resume without the EntroNex proof (V2 dice = plain RNG) |
  | ludopaws_pending_local_results_v1 | result_recovery.gd | retried at launch |
  Still owned by later blockers: pending_ad_rewards_v1 + ludopaws_ad_breaks (B5), pending Play
  purchases + StoreInstallAccountId (B4), friend / leaderboard identity, caches, online
  sessions, matchmaking stores (B6). Small growth stores not yet ported: engagement_v1,
  reward_requests_v1, rewards_discovery, growth_counts_v1, automatic_prompts_v2,
  referral_popup_v1, friendship_attribution, saved_promotion_interest_v1,
  ludopaws_update_experience_v1, ludopaws_family_notice_v1 (with M6 / referrals).
  Fixed on the way: Settings game speed never reached matches (wrong key); "Auto Roll Dice"
  (not live, no effect) removed; live Animal voices / Play hints / Quick chat switches and
  Privacy links added; streak day uses the local date like live; recorded-match cap 250.
  A71: Robot match checkpoint in `ludoproof_computer_game`, app killed, relaunch shows
  Continue Match, match resumes with every pawn in place. **The real update test over the
  Play-installed app (install -r) is Phase 3.**
- [~] **B6 online — in progress.** Done: `platform/keystore.gd` + plugin
  keystoreEncrypt/Decrypt (live aliases only: player session, friend identity, leaderboard
  identity, pending Play purchases); `online/player_session_store.gd` = live
  SecureSessionStore + pending forfeits (`ludoproof_secure_session`,
  `ludoproof_pending_forfeits`, live AAD strings). **Next, in order:**
  1. [x] `online/identity_store.gd` = live Leaderboard + Friend identity stores
     (`ludoproof_leaderboard_identity`, `ludoproof_friend_identity_v1`, live aliases / AAD);
     `online/leaderboard_service.gd` (register once, GET /api/leaderboard?limit=50, cache
     `ludoproof_leaderboard_cache` per account) + Leaderboard screen with real rows, "You"
     row, offline saved ranking. Fake-server contract tested; NOT run against the live
     server (registering a profile writes server data; owner allowed read-only leaderboard
     checks only). The V2-only "Friends" leaderboard filter was removed (no live source).
  2. [x] `online/match_envelope.gd` = live GameJson strict snapshot validation (tested).
     [x] `online/online_match_controller.gd`: public matchmaking (ticket store, 1.5 s poll,
     validated MATCHED only), seat capture, strict apply + revision guard, replay-safe
     roll / move ids (`ludoproof_pending_operation`), 3 s / 30 s state poll, realtime,
     RemoteCharacterAccountSelection, leaderboard wallet link. Fake-server tested.
     [x] Online lobby Find Match / Cancel wired; `online/online_turn_flow.gd` drives the V2
     match screen from validated server snapshots (my seat at the bottom, "You", my single
     legal move automatic, no local dice / robots). Tested with a fake controller.
     **Untested against the live server** (would place a test client into real public
     matches: needs the owner's OK).
     [x] open seat resumes at launch (Home Continue · Online), live leave dialog + server
     forfeit queue (POST /start {forfeit}, 3 checks 900 ms apart, terminal codes, ranked
     result only after FINISHED), finished-match recording (ranked Online, Paw mastery,
     weekly) and the match gem reward wallet refresh. Fake-server tested (account 35).
     Not yet: CachedMatchStore (offline view of the last online state), quick chat (M7),
     online results presentation differences, EngagementStore. (search -> status poll -> MATCHED
     validation -> captureSession -> realtime), RemoteCharacterAccountSelection.
  3. Online match screen on server state (V2 engine state schema = server schema):
     OnlineMatchActions (roll / move commands with clientRequestId + expectedEventIndex),
     OnlineSessionActions, RemoteExitActions + RemoteForfeitApi, realtime client wiring,
     PendingOperationStore, CachedMatchStore, OnlineAutoMoveScheduler, pacing.
  4. [x] `online/friends_service.gd` + Friends / Room screens (Friend ID, add, requests,
     invites, friends list with Play / menu, blocked, private room 2-4 with host Start,
     leave). Join-by-code is not a live feature: Join Room points to invites / links.
     Fake-server tested; not run on the live server. [ ] messages / chat (FriendsActivity
     chat, 5 s refresh), recent players row.
     (old note) FriendIdentityStore + Friends screen (register, snapshot, requests, invites, rooms,
     messages, presence heartbeat), FriendRoomHandoffValidator, RoomLink / GameShareLink.
  5. [x] Team Up: account-bound ticket `ludoproof_team_matchmaking`, /api/team-matchmaking/
     search|status|cancel (2 s poll, FINALIZING / IDLE), validated 4-player TEAM_UP room ->
     TEAM_UP seat; lobby Find Team / Cancel. Fixed: server snapshots now render with the
     engine ruleset key for their rulesetId (was falling back to V1 presentation rules).
     Fake-server tested (account 42).
  5b. [x] Room links + sharing: `online/room_link.gd` (live RoomLink / GameShareLink rules),
     `.RoomLinkAlias` with the live filters (https api.ludopaws.rekixo.com/play/ autoVerify +
     ludopaws://), plugin takeLaunchUri / shareText; Home shows "Play together" over
     Friends; room Share Room Link; Invite shares the live game link. A71: link opened the
     card. NOTE: opening Friends on the A71 registered a Friend ID for the test install on
     the live server (live behaviour; identity for the test device only).
  6. Referrals (ReferralRepository, popup policy) and the remaining growth stores.
  6. Referrals moved to M6 (they need Install Referrer).
- [x] **B4 billing** (code done, **untested on device**: needs the production package,
  Play listing and the owner's test track; no real purchases were made). Play Billing
  9.1.0 (live version) inside the project plugin (`BillingBridge.java`) instead of a
  third-party Godot billing plugin, so the library version matches live / Play policy
  (deviation from AGENTS.md "official plugins" — owner to confirm). Pending one-time
  purchases, auto reconnection, obfuscated billing account id; the server verifies and
  consumes / acknowledges (the client never does). `play_billing.gd`: live processPurchases
  rules, live encrypted PendingStorePurchaseStore (`pending_play_purchases` in
  ludoproof_wallet, v1. url-safe format, live alias / AAD), recovery on store open
  (same account only), restore, account deletion cleanup. Enabled only with
  billing_enabled in config / CI and the server's billingAvailable. APK permissions now
  equal v16 (incl. BILLING; USE_BIOMETRIC / USE_FINGERPRINT come from credentials as in v16).
- [x] **B5 ads** (this commit). Mobile Ads 25.5.0 + UMP 4.0.0 (live versions) in the
  project plugin (`AdsBridge.java`) with live FamilyAdsPrivacy (child-directed, max rating
  G, consent under age, npa=1); manifest like live (init provider removed, AD_ID /
  ADSERVICES / RECORD_AUDIO stripped; test builds use Google's TEST App ID, release setup
  `LUDOPAWS_RELEASE=1` uses the config / CI App ID). `ads_service.gd`: rewarded flow
  (server rewardedAdsV2Available, account + wallet check, SSV user id + custom data,
  live PendingAdRewardStore hints in `pending_ad_rewards_v1`, confirmed from
  creditedAdRequestIds), daily ad bonus with the server ticket, post-win interstitial
  (AdBreakPolicy 1 match / 5 min / 90 s rewarded quiet, `ludopaws_ad_breaks`, exit gate
  with a fresh adsRemoved answer), privacy options in Settings, deletion cleanup.
  A71 with TEST units: consent ready 3.0 s, SDK init, rewarded test ad loaded 6.1 s (not
  shown). Real ad delivery / SSV crediting **untested on device** (production package +
  real units + server SSV).
- [x] **B9 release workflow — prepared, STOPPED before install / first run.**
  `tools/release/android-godot-release-bundle.yml` for the production repo
  (`.github/workflows/`, Godot project at `godot/`): manual dispatch from main in the
  `production` environment (like live), the same secrets (LUDOPROOF_ANDROID_KEYSTORE_BASE64,
  _STORE_PASSWORD, _KEY_ALIAS, _KEY_PASSWORD) and variables; Godot 4.7.2 + templates,
  tests, release config written from Variables (deleted after the build), unsigned AAB
  export, jarsigner with separate store / key passwords, checks: v16 certificate
  SHA-256 CC:0F:7F:77:…:4B:B5:66, package, versionCode, restricted permissions; uploads
  the signed AAB + SHA-256 (no Play upload). Release preset versionCode 17 / 2.0.0.
  Local dry run (no signing): release AAB com.rekixo.ludopaws 17 / 2.0.0, min 28 /
  target 36, permissions = v16, arm64-v8a + armeabi-v7a.
  **Needs the owner:** OK to open the production-repo branch / PR, then the first run.
- [x] Rewards / Collections / match result rework (2026-11-11): `rewards_page.gd` (summary
  strip with server reset countdown, Daily Gift chest, 8-segment Free Spin wheel that only
  lands on a segment showing the server's amount, odds from server spinOutcomes, Daily Ad
  Bonus states; each card updates alone), compact collection Get pill, end-of-match result
  overlay on the match screen (`match_result_overlay.gd`, no route change, appears after
  the celebration + 1.4 s, fits without scroll, Back = Home, Details sheet, post-win ad rule
  kept). Tests: rewards_ui_smoke (17), match_flow_smoke overlay checks. A71 screenshots in
  `.godot/preview/rewards_results/` taken on a stand-in build (backend URL blank) so no
  live wallet claim was made.
- [x] **M6** (2026-11-12): project plugin `PlayCoreBridge.java` with the live versions
  (app-update 2.1.0, review 2.0.2, installreferrer 2.2, integrity 1.6.0);
  `growth/update_coordinator.gd` (live PlayUpdateCoordinator + UpdateExperiencePolicy,
  `ludopaws_update_experience_v1`), `growth/prompt_gate.gd` (AutomaticPromptGate,
  `automatic_prompts_v2`), `growth/engagement_store.gd` (`engagement_v1`),
  `growth/natural_review.gd` (NaturalReviewController), `growth/referral_service.gd`
  (ReferralRepository + popup policy, `friendship_attribution`, `referral_popup_v1`); Home
  sheets in V2 style (update / What's New / restart / referral popup) only on Home, adult band
  for review + referral like live; Invite page = live referral screen. Post-win ad once per
  match. Audit before the change: `docs/PLAY_SDK_AUDIT.md`. Release export excludes
  scripts/debug, previews and preview scenes (perf facade `scripts/game/perf.gd`, DevHooks
  autoload starts the probe only in debug builds); `tests/release_pack_check.sh`.
  Device-only flows: `docs/INTERNAL_TESTING_CHECKLIST.md` (untested on device). A71 boot
  check of this build still pending (device was not connected).
- [ ] Majors M1, M3-M5, M7, M9; referrals + install referrer (M6); Phase 3 migration test
  (install -r over the Play app on the A71).

Device: Samsung A71 over wireless ADB (`adb mdns services` + `adb connect`; Godot's export
restarts the adb server). Test package com.rekixo.ludopaws.v2rc only; production app
com.rekixo.ludopaws is installed from Play and must not be touched until the migration test
(install -r, never uninstall). Build: `tools/android/setup_build_template.sh` then
`--export-debug "Ludo Paws v2 Android"`.

---

# Release readiness — Godot V2 as the Play update of com.rekixo.ludopaws

Phase 1 audit, 2026-11-11, branch `release/v2-rc` (worktree `ludopaws-v2-release`), against
the live repo `~/Workspace/ludopaws` (read only) and the last Play release **v16 (versionCode
16, versionName 1.0.16, source c300d59)** from `~/Downloads/ludopaws-v16-signed-aab`.

Severity: **blocker** = must not ship (players lose data, money or a feature they use);
**major** = visible regression or policy risk; **minor** = polish.

## 1. Verdict

**Not releasable yet.** The offline game (Robot, Pass & Play, Demo, setup, match, results,
shop / wallet UI, rewards UI, profile, badges, weekly challenge, branding, launch) is in
good shape and covered by 9 automated suites plus A71 device runs. Everything that talks to
the live server or Google Play services does not exist in V2 yet, and live players depend on
it: their account, wallet, owned items, purchases, ads, online play, friends, leaderboard.
Shipping now would take those away from every existing player.

## 2. Blockers

| # | Area | What is missing / wrong | Live source | Fix plan |
|---|---|---|---|---|
| B1 | Account / sign-in | No account system: no guest/Google session, no session refresh, no secure storage (inventory I1–I4) | AccountRepository, AccountSessionStore (encrypted), Credential Manager, Play Games | Project Android plugin (Credential Manager + EncryptedSharedPreferences), GDScript account service against the same server endpoints |
| B2 | Server client | No HTTP transport / GameApi, no WebSocket realtime, no pending-operation queue, no connectivity monitor (I5–I10) | GameApi, realtime client, PendingOperations | GDScript HTTPRequest + WebSocketPeer client with live command ids, idempotent retry, reconnect backoff |
| B3 | Wallet / ownership authority | `store_service.gd` uses `DesktopBackend`: gems, unlocks, daily gift, spin and ad rewards are applied **locally**. A real player's server wallet and owned items are never loaded | server store / rewards endpoints | Replace with the server backend (same rules, server authoritative); local copy only as cache |
| B4 | Play Billing | `play_billing.gd` is a stub (no products, "Price unavailable"); no purchase / acknowledge / consume / restore on device (H1–H10) | billing 9.1.0, purchase verification endpoint | Official Godot Play Billing plugin (v9-compatible), verify on server before consume |
| B5 | Ads | `ads_service.gd` is a stub: no rewarded ads, no post-win interstitial, no SSV, no UMP consent, no family / age ad restrictions (H11–H13) | play-services-ads 25.5.0, UMP 4.0.0, live ad policies | Official Godot AdMob plugin + UMP; live AdBreak / family policies already ported in logic |
| B6 | Online play | Online, Team Up, Friends rooms, Quick Match matchmaking, invites, room links: UI only, show "service not connected" (G2, G5–G9, I8–I9, A13) | server match rooms / matchmaking / friends | Build on B1+B2 with the live protocol (realtime events, verified dice outcome) |
| B7 | Data migration | Nothing reads the live app's SharedPreferences / encrypted session / files after the update (J7). Players would lose settings, Paw selection, offline setup, saved match, profile stats, weekly progress, pending ad rewards / purchases, age choice | live prefs namespaces (Phase 3 inventory) | One-shot Android migration plugin + GDScript importer, crash-safe |
| B8 | Dice fairness decision | AGENTS.md: local dice are a plain RNG; EntroNex port must be **decided before release** (owner) | live EntroNex local dice | Owner decision; port if required |
| B9 | Release signing & config | No release config wiring (AdMob app id / units, backend URL, sign-in client id) from a git-ignored file or CI secrets; release export never built or signed with the production key | live workflows + GitHub Secrets/Variables | Phase 2 config loader + Phase 6 workflow |

## 3. Majors

| # | Area | Issue | Fix plan |
|---|---|---|---|
| M1 | Match performance | A71: ~24 fps in a 4-player match (target ~60). Cause measured: the 3D scene's ~800 per-part draw calls; scrolling and dice tap already meet targets | Batch pawn parts into MultiMeshes per mesh/material; re-measure on the A71 |
| M2 | Saved match / resume | A match lives only in memory; live persists the offline match (save on exit, result recovery A14 / A20). Closing the app loses it | Persist the engine state + result recovery store like live |
| M3 | Quick Match variant | `ludopaws-quick-two-v1` (2 paws each) not ported (A13) | Port ruleset + entry point |
| M4 | Haptics | Settings switch exists; only the turn banner vibrates. Live haptic feedback on dice / moves not ported (D6) | Port live haptic events (device test) |
| M5 | Hindi strings | Live ships Hindi + English (J3); V2 has a language option but no translations | Port strings tables |
| M6 | In-app update / review / install referrer | Not built (J4, J5, G14) | Project Android plugin (Play Core) |
| M7 | Quick chat / reactions | Live animated emoji quick chat (G3, A21, 25 webp assets) not ported | Port after online |
| M8 | Settings parity | Privacy policy link, account deletion entry, etc. (J2) not wired | Port with account |
| M9 | Inventory accuracy | `PARITY_INVENTORY.md` has stale "todo" rows for work already done (A11, A15, A22, B25, G12, G13, G16, J1) | Refresh during Phase 2 |

## 4. Minors

- Perf probe autoload ships (inactive without its flag file); exclude it / gate it out of release exports.
- Preview scenes (`scenes/previews`, `scripts/previews`) and `scripts/ui/preview/` naming ship in the pack; exclude previews from release export.
- Feature graphic uses DejaVu Serif for the title (V2's UI serif); confirm the look.
- Profile "Leaderboard" button: its coin glyph draws as a white square on the A71 (seen 2026-11-11; button code unchanged by B1).
- Home "Continue Match" card now hidden without a saved game: the Home layout keeps its gap.

## 5. Android / Play requirements (current vs needed)

| Item | Live v16 | V2 now | Needed |
|---|---|---|---|
| applicationId | com.rekixo.ludopaws | test: …ludopaws.v2rc; release preset: com.rekixo.ludopaws | ✓ preset ready |
| versionCode / name | 16 / 1.0.16 | preset 5 / 2.0.0 (placeholder) | ≥ 17; versionName from owner |
| minSdk / targetSdk | 28 / 36 | template 24 / 36 | set minSdk 28 (no players dropped) |
| ABI | (AAB) | arm64 + armv7 in test | AAB with arm64-v8a (+ armv7 if live had it) |
| 16 KB pages | — | NDK 29 template | verify alignment in CI (Phase 6) |
| Permissions | INTERNET, ACCESS_NETWORK_STATE, BILLING, VIBRATE; ad-id / topics / RECORD_AUDIO removed | Godot defaults | match live exactly |
| Backup | allowBackup=false | template default | allowBackup=false |
| Billing / Ads / UMP | 9.1.0 / 25.5.0 / 4.0.0 | none | plugins at these (or newer) versions |

## 6. What was verified (evidence)

Automated (desktop, all passing): rules + live parity (235 checks), UI match flow, scroll
layout (83), tap hints (40), setup (20), real data (38), launch screen (14), Pass & Play
banner (13), 120-frame boot, import check.

On the Samsung A71 (test package), during this release work: Robot 2/3/4, Pass & Play 2/3/4
(banner + READY setting), Demo, setup pages, Shop tabs / Wallet / Rewards scrolling, Profile /
Badges / Weekly / Collections / Leaderboard / Invite / Friends states, age entry, tap hand,
single launch screen (cold-start recording), performance probe runs. Screenshots under
`.godot/preview/`.

**Untested on device / not testable yet:** everything in B1–B7, haptics, Android 9–11 launch
path, themed icon and wallpaper variants (owner's phone settings not changed).

## 7. Decisions needed from the owner

1. Release scope: ship only after B1–B9 (full parity) — recommended — or another plan.
2. EntroNex for local dice (B8).
3. versionName for versionCode ≥ 17.
4. Backend access for development (URL in git-ignored config) and a test account / Play test track for billing and ads.
