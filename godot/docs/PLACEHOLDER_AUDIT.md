# Placeholder / sample audit (2026-11-11, release/v2-rc)

Search: "Sample", "Preview", "placeholder", "Coming soon", hard-coded names, fake scores,
`randi` room codes, `UI.animal(...)` stand-in art, notice-only buttons — in `scripts/` and
`scenes/`. Live sources read from `~/Workspace/ludopaws` (Kotlin client + `server/`).

**Blocker for every server-backed row:** V2 has no backend URL locally
(`config/release.local.cfg` absent — only `release.example.cfg`), no HTTP client and no
account sign-in (live: Credential Manager / Play Games → server session). Those rows show
honest **sign-in required / offline** states instead of fake data until the owner provides
the backend URL (git-ignored config) and a test account. They are **untested on device**.

| Screen | file:line (before) | What it showed | Live source | V2 now |
|---|---|---|---|---|
| Profile | hub_pages.gd:127 | `UI.animal(0)` sample illustration | ProfileHeaderUi: "One portrait only: the persisted in-game Paw selection" (LudoPawsCharacterAvatarView) | equipped Paw 3D portrait in the woodland frame |
| Profile stats | hub_pages.gd (~186) | fixed "0 / 0 / — / 0" | ProfileStore.recordCompletedMatch (local, per account) + ProfileProgression | ported local profile store: played, won, win rate, best streak, XP / level from finished matches |
| Badges | hub_pages.gd:258 | "Sample badge progress", 6 earned / 6 locked hard-coded | BadgesActivity + ProfileStore progress (local) | progress-driven badges from the profile store (earned / locked from real stats); empty state when nothing earned |
| Weekly Challenge | hub_pages.gd:564 | "0 / 3 · Challenge preview" | WeeklyChallengeStore (device-local; Monday 00:00 UTC weeks; 3 rotating challenges) | ported store, fed by finished matches: title, goal, progress, reset time, collection badges |
| Collections | hub_pages.gd:568, 590 | `UI.animal(1)` art, "Collection preview" dialog | server `engagement-rewards.js` (COLLECTIONS, collectionQuote) via `/engagement` | sign-in required state (server-only); no fake progress |
| Leaderboard | hub_pages.gd:387-435 | PineRaccoon / BunnyAce / DeerDash podium, OtterLuck… list, "Sample rankings" | `/api/leaderboard` (LeaderboardApi, LeaderboardIdentityStore, LeaderboardCacheStore) | Global / Friends tabs with loading / empty / sign-in states; no fake rows |
| Friends | lobby_pages.gd:194-217 | FoxRider / BunnyPop / BearBuddy / RaccoonPlay, "● Sample contact", sample art, notice-only Invite | `/api/friends/*`, FriendsActivity, FriendIdentityStore, FriendPresenceController | empty / sign-in state + Invite Friends; no fake contacts |
| Friends room | lobby_pages.gd:223 | `"LP%04d" % randi` room code | server creates `LP` + 8 chars (`/api/matches`, `/invite`) | no local codes: Create / Join need the online service (sign-in state) |
| Room screen | lobby_pages.gd:246-264 | `UI.animal(0)`, "Players: 1 / 4 · Preview", "Join Room Preview" | `/invite/join-check`, `/invite/respond`, match events | removed sample; room screen only reachable with a real room |
| Online lobby | lobby_pages.gd:34, 55-56 | "● Matchmaking preview", "This is a UI preview… sample match" | `/api/matchmaking/search` | sign-in / offline state; Find Match does not open a sample match |
| Invite | hub_pages.gd (~446) | "Not linked" code + notice | RoomLink / GameShareLink, `/invite` | sign-in required state (no fake code) |
| Online match (no local game) | match_pages.gd:85-234 | sample animals, "Roll Preview", "Visual preview", "Preview Match Results", "+3 Gems · Preview", "This sample is running locally…" | online match via server events | unreachable without a real online match (all entry points need sign-in) |
| Lakeside guests | lobby_pages.gd `_lakeside` | animated animal illustration | — (decor) | kept: scenery, not presented as players |

## Routes → header (meadow_preview.gd)

| Route(s) | Header | Back | Gear |
|---|---|---|---|
| rewards, shop, wallet, **collections**, **results** | moonlit ProfileHeader + title plaque | wood back, left | wood gear, right (same size) |
| profile, badges, leaderboard, invite, settings, room, details | VinePageFrame + ProfileHeader | wood back | — (Settings itself / profile pages) |
| online, team_up, friends, robot, pass_and_play | brand + mode plaque (MODE_LOBBIES) | wood back | — |
| match | brand + mode plaque, board header | wood back, left | wood gear, right |
| age (first launch) | brand + WoodPlaque | — | — |

Before this pass Collections and Results used the old brand sign + round back button (wrong
place / background, gear misplaced). Both now use the standard moonlit header.

## Status after this pass (2026-11-11)

**Real now (device-local, live behaviour):** Profile portrait (= selected Paw), name default
"Guest User", stats, win rate, best streak, XP / level, Paw mastery; 9 badges with real
progress; Weekly Challenge (progress, goal, reset time, collection badges); Collections
progress and prices from owned items (live COLLECTIONS + collectionQuote); Home Continue Match
only for a real local game; match player cards (two centred lines). All verified by
`tests/real_data_smoke.gd` and run_tests; phone screenshots in `.godot/preview/real_data/`.

**No sample anymore, but waiting for the online service (untested on device):** friends list
and presence, friend rooms (create / join, server code LP + 8), invite link / code, global and
friends leaderboard, online and Team Up matchmaking, buying a collection, account sign-in and
server-side profile. They show "Online service not connected" (or "Sign in to continue" once
a backend URL is configured) with Retry — never fake names, codes or matches.

**Needed from the owner to make those real:**
1. `config/release.local.cfg` with `[backend] base_url` (stays git-ignored, never committed).
2. A test account (or a staging backend) that may be used by a development build.
3. The account sign-in route to build first (live uses Credential Manager / Play Games
   sign-in -> server session); Play Games / Credential Manager need the Android plugins and
   a phone, so they are verified on device only.
