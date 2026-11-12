# Instructions for Codex and all coding agents

You are working in **rekixo-lab/ludopaws-v2**, the **Godot 4 production migration** of the live
Ludo Paws Android app. When finished, this project ships as the Google Play **update** of the live
app (**com.rekixo.ludopaws**). The GitHub repository is **private**.

## Scope (owner decision, 2026-11-10)

Bring everything from the live app (`rekixo-lab/ludopaws`, read locally at `~/Workspace/ludopaws`)
into this project: all gameplay, rules and behaviour; 14 characters, the 8 live boards and 8 live dice (draft/retired items are out of scope); shop,
prices, Google Play Billing, AdMob, rewards; account and online play against the **same live
Cloudflare backend and protocol**. The complete checklist is `docs/PARITY_INVENTORY.md`.

## Hard boundaries

1. **The live repo, live backend, Play Console and AdMob account are read-only.** Never modify,
   deploy or configure them from here.
2. **No secrets in the repository, ever:** no signing/upload keys or keystore passwords, no real
   AdMob App ID or ad unit IDs, no backend URL, tokens, service credentials or player data.
   These live only in the git-ignored `config/release.local.cfg` (see `docs/TECHNICAL_BOUNDARIES.md`).
   Development uses Google's **test** ad unit IDs and local stubs for billing, ads and sign-in.
3. **The owner signs releases.** The repo holds an export preset only; never generate, request
   or store the upload key.
4. **Do not push** to GitHub without the owner's explicit confirmation. Commit on the working
   branch (currently `migration/live-rules-port`), including `.uid` files.
5. **Dice:** outcomes are decided before any animation and never depend on it. Online play shows
   the server's verified outcome. Local dice are a plain local RNG for now (decision 1a); whether
   to port EntroNex must be decided before release. Never claim certified or cryptographic
   fairness for local dice.
6. **Behaviour parity:** behave exactly like the live code. Do not "improve" behaviour; ask first
   when a difference seems necessary.

## UI rule (applies to every phase)

- The UI is **V2's only** (`scripts/ui`, `scenes/ui`, `assets/ui`). No live layouts, activities,
  button/panel/dialog looks, colours, fonts or UI drawables.
- Live `*Ui`, `*Activity`, `*View` files are read only to learn which data, actions, states,
  rules and validations exist.
- Existing V2 screens (Home, lobbies, Match, Results, Details, Shop, Collections, Wallet, Rewards,
  Profile, Badges, Leaderboard, Invite, Settings) get real data and logic; their look stays.
- A live feature without a V2 screen gets a new screen built from the Meadow kit
  (`scripts/ui/kit`, `meadow_controls.gd`, MeadowStyle, VinePageFrame, WoodPlaque, ...) and
  `assets/ui`. Tell the owner in one line which screen you are building before building it.
- Game content comes from live: 3D characters, boards, dice, FX, character portraits.
- Behaviour visuals (countdown ring, legal-move halo, tap hand) are built in V2 style; ask first
  if they would look different from live.

## Engineering approach

- Godot 4.7 stable, GDScript, Compatibility renderer. Small, legible scripts; pure rules and
  geometry separate from rendering, UI, FX and sound. Rendering is never authoritative.
- Port behaviour by reading the live source; prove parity with tests (golden vectors generated
  from the unchanged live code where possible, see `tests/fixtures`).
- Every control works with touch and mouse; keep Reduced Motion working.
- Plugins: official/maintained Godot 4.7 Android plugins for Billing, AdMob and Play Games;
  a small project-owned Android plugin for Credential Manager, in-app update/review, install
  referrer, encrypted storage and old-data migration.

## After each phase

1. Run all tests (`tests/run_tests.gd`, `tests/match_flow_smoke.gd`, new ones), the import check
   and a 120-frame boot (commands in README.md).
2. Save screenshots to `.godot/preview/`.
3. Update `docs/PARITY_INVENTORY.md` and README.md.
4. Commit on the branch (with `.uid` files). Do not push.
5. Tell the owner what to open in Godot (F5/F6) and what they will see.

Flows that only a phone can verify (billing, ads, sign-in, haptics, data migration) run on
desktop stubs and are marked **untested on device** — never reported as green. If a session
stops, resume from `docs/PARITY_INVENTORY.md` and `git log`.
