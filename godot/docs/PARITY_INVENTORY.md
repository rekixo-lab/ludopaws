# Ludo Paws live → V2 parity inventory

Source of truth: private live repo `~/Workspace/ludopaws` at commit `c300d59a` (2026-11-09), read-only.
Every live Kotlin file under `android/app/src/main/java/com/rekixo/ludopaws` and every
`server/src/*.js` file is named in at least one row; `tests/inventory_coverage.py` checks this.

**Status:** `todo` · `done` (implemented, checked by screenshot/manual) · `tested` (automated test) ·
`device-only` (can only be verified on a phone; reason given) ·
`backend` (runs on the live Cloudflare server; V2 only speaks its protocol) ·
`no-port` (deliberately not ported; reason given — needs owner sign-off).

**UI rule:** live `*Ui`, `*Activity`, `*View`, dialogs, drawables, colours and fonts are read only for data,
actions, states and validations. V2 screens keep the Meadow look. New screens use `scripts/ui/kit`.

Phases: P1 inventory · P2 characters + in-match behaviour · P3 boards · P4 dice · P5 modes/flows ·
P6 economy/shop · P7 billing · P8 ads · P9 account/online · P10 app level.

## Resume here (updated 2026-11-10)

Done: all of C (in-match), D1–D5 (audio), owner Shop/Wallet steps 1–4 (verify, 8 boards, 8 dice,
starter Paws, Shop, Wallet) and Rewards. Desktop store / billing / ads are stubs (untested on device).

- **Next (owner order):** step 5 wiring — Profile / Collections show owned items, Home hero shows
  the selected Paw, Pass & Play per-seat Paw picker (owned only), Collections bundles on Rewards
  (Garden Friends, Starlight Pals, Rainy Day Pals), Shop featured / Game Paws promotion;
  online/friends/team-up use matchStyle boardId/diceId + characterId with I (online not built yet).
  Then step 6 Ads: AdMob plugin config with Google TEST ids (real ids only in
  config/release.local.cfg from release/v11/REWARDED_2_GEMS_ROLLOUT.md), post-win interstitial
  (PostWinInterstitialPolicy + AdBreakPolicy 300 s), remove_ads, family/age privacy, UMP.
- Left in content: MonsoonBoardScene 3D weather (E9); 15 s dice countdown ring with online (F8).
- For every new board: add its colours to `_legal_hint_contrast` and take `board_hints_<id>.png`.
- Game speed: stored as setting `game_speed`, no Settings control yet (J2).

## Verified live store content (2026-11-10, from server + client sources)

- **Characters (14):** free dog, goat, duck, cat · goat_lumi 50 Gems or 10 ads · donkey 75 Gems or
  15 ads · panda, monkey 150 Gems or Play · cock, pig, elephant, horse, rabbit, giraffe 300 Gems or Play.
- **Boards (8):** classic free · astral 300 / Play / 15 ads · cloud_lagoon, autumn_forest,
  blossom_garden, winter_haven, monsoon_mire, sunstone_palace 300 / Play.
- **Dice (8):** classic free · pearl_paw 100 Gems or 20 ads · forest_dew, lagoon_pearl,
  frost_crystal, starlight 100 Gems or Play · ember_forge, prism_royal 150 Gems or Play.
- **Avatars:** 1, 3–9 free (default avatar_4); avatar_2, avatar_10 100 Gems. Live Shop lists NO
  avatars (StoreCosmeticCatalog.avatars is empty); they are chosen outside the Shop.
- **Play products:** remove_ads (ads off + 5 Gems); gems_300 300, gems_1500 1,200, gems_3500 3,000,
  gems_20000 7,500, gems_40000 16,000, gems_200000 35,000; every priced cosmetic in
  COSMETIC_PRICING is a non-consumable product with its own id (character_*, board_*, dice_*;
  pearl_paw is Gems/ads only).
- **Earning:** rewarded ad 2 Gems (SSV), **no daily cap** in updated versions; daily gift 3; free
  spin 1/2/3/5 Gems at 50/30/15/5 %; daily ad bonus 2 once per day (server ticket); milestone every
  7 daily claims. Match Gems only ONLINE/FRIENDS natural finishes: 2p [3,0], 3p [3,2,0],
  4p [3,2,1,0]; TEAM_UP winners 3 each; never Robot/local/bot-filled/forfeit.
- **Collections (Rewards):** shown only when every item is a live shop item → Garden Friends,
  Starlight Pals, Rainy Day Pals (600 of 700; price = missing value × 600/700).
- **Refund:** a revoked Play cosmetic is removed and its category's selection returns to the default.
- **Remote catalog** (/api/store/catalog) may override price/availability; the client still needs
  local content (contentAvailable) and hides unknown/archived items.

## Totals (keep updated)

| Rows | tested | done (some partly) | todo | device-only | backend | no-port | out-of-scope |
|---|---|---|---|---|---|---|---|
| 176 | 46 | 40 | 54 | 12 | 15 | 4 | 5 |

Rows marked "done / todo" are partly finished and count as done here; their todo part is still open.

---

## A. Rules, engine, local play (P1–P5)

| ID | Feature / behaviour | Live sources | Where used (live) | V2 target | Status |
|---|---|---|---|---|---|
| A1 | Authoritative rules CLASSIC_V1–V8, TEAM_UP_V1–V7 (opening six, third six, comeback, stack protection, finish places, team handoff, timeouts) | server: game.js, game-v1-core.js, rulesets.js | every match | scripts/game/rules/ludo_engine.gd, ludo_rulesets.gd | tested (81 golden games) |
| A2 | Path encoding (51 legacy gap, home lane 52–56, home 57) | LudoPathEncoding | all boards/moves | ludo_engine.gd, ludo_bot.gd | tested |
| A3 | Extra turn on 6 / capture / home | LudoExtraTurnPolicy | local + remote | ludo_engine.gd | tested |
| A4 | Comeback (stranded third-roll entry, yard variant) | LudoComebackPolicy | local + remote, hint text | ludo_engine.gd; hint text in match page | tested (rules) / todo (hint UI) |
| A5 | Opening six + single-legal-move auto-move | LudoTurnAutomationPolicy | local + remote | ludo_engine.gd; local_turn_flow.gd single_automatic_token_index + forced single move after the result hold | tested (rules, auto-move timing) |
| A6 | Game modes, routing, match spec | GameMode, GameModeRouting, MatchSpec, GameModeNavigation | mode entry | scripts/ui/preview/meadow_preview.gd routes | todo |
| A7 | Snapshot / player / pending roll / history models + JSON | GameModels | everywhere | engine Dictionary state; online JSON mapping in P9 | todo (online mapping) |
| A8 | Match session abstraction (local/remote) | MatchSession, LocalMatchSession, RemoteMatchSession | match screens | scripts/game/local_match.gd (+ online session P9) | done (local) / todo (remote) |
| A9 | Offline engine: save/restore, perimeter turn order, finish, history | OfflineGameEngine, OfflinePlayerLayout | Robot / Pass & Play | local_match.gd (turnRotation PERIMETER) | tested (turn order) / todo (save checkpoints, history) |
| A10 | Offline Robot move policy | LudoPawsComputerMovePolicy | Robot mode | scripts/game/rules/ludo_bot.gd | tested (4000 Kotlin vectors) |
| A11 | Robot team composition (≥1 CPU, first seat owns device) | RobotTeamComposition | Robot setup | local_match_page.gd start() | todo |
| A12 | Offline snapshot invariants (fail-fast) | OfflineMatchInvariantPolicy | save/restore | local_match.gd from_saved | todo |
| A13 | Quick Match variant (2 paws each, `ludopaws-quick-two-v1`) | QuickMatchPolicy | Pass & Play option | ludo_rulesets.gd + lobby option | todo |
| A14 | Offline result recovery (stats write survives leave/rematch) | OfflineResultRecovery | results/profile stats | local stats store (P5) | done: result_recovery.gd on `ludopaws_pending_local_results_v1` (tested) |
| A15 | Offline character setup persistence | OfflineCharacterSetupStore, OfflineSetupUiState, OfflineCharacterPresentationState | Robot/P&P setup | preview_state.gd + store (P2/P5) | todo |
| A16 | Offline gameplay actions (roll/move, CPU think, stale-decision guard) | OfflineGameActions, OfflineGameActivity, OfflineLudoPawsFeedback | offline match | local_match_page.gd | done (basic) / todo (CPU think timing, stale guard, feedback) |
| A17 | Pass & Play handoff policy | OfflinePassAndPlayHandoffPolicy, OfflinePassAndPlayHandoffUi | P&P | scripts/game/match/pass_and_play_handoff.gd + local_match_page.gd show_handoff (Meadow dialog) | done (tested) |
| A18 | Turn transition feedback (roll with no move explained) | OfflineTurnTransitionFeedbackPolicy, TurnTransition in online | local + remote | match page messages | todo |
| A19 | Rematch from finished match | OfflineRematchPolicy | results | results page Play Again | todo |
| A20 | Exit / save dialog | OfflineExitDialogUi, RemoteExitActions | back during match | match_pages.gd save_dialog | todo |
| A21 | Quick reactions (local animated emoji next to sender) | OfflineQuickReactionPolicy, OfflineQuickReactionUi | offline match | new V2 quick-chat strip (Meadow kit) | todo |
| A22 | Offline setup screens: player count, colours, HUMAN/ROBOT seats, character choice | OfflineSetupScreenUi, OfflineSetupSelectionUi, OfflineCharacterSelectionUi, OfflineSetupArtView, OfflineCommonUi | Robot / P&P lobby | scripts/ui/preview/lobby_pages.gd (data only) | todo |
| A23 | Gameplay screen layout data (player rail, controls, top actions) | OfflineGameplayScreenUi, OfflineGameplayControlsUi, OfflinePlayerRailUi, GameTopActionsUi | match | scripts/ui/match/player_rail.gd, player_card.gd, scripts/game/match/rail_layout.gd, local_match_page.gd (V2 look; docs/MATCH_RAIL_SPEC.md) | done (offline; online/Team Up via mock until I-phase) |
| A24 | Local dice (V2 decision 1a: plain RNG, not EntroNex) | LocalSecretBinding, SeedCommitment | offline rolls | scripts/game/rules/local_dice.gd | tested (range/uniformity) — **decide before release** (EntroNex vs plain RNG) |
| A25 | EntroNex v4 local proof, fairness chain, ruleset bindings, randomness audit | EntroNexV4Local, OfflineFairnessChain, OfflineLudoV3Binding, OfflineLudoV4Binding, OfflineLudoV5Binding, OfflineLudoV6Binding, OfflineRandomnessAudit | offline dice receipts | — | no-port for now (owner decision 1a, 2026-11-10) — **decide before release** |
| A26 | Fair-dice explainer + proof history dialog | FairDiceExplainer, ProofHistoryDialogUi | match menu | online: show server proof (P9); offline: depends on A25 | todo (online) |

## B. Characters (P2)

| ID | Feature / behaviour | Live sources | Where used | V2 target | Status |
|---|---|---|---|---|---|
| B1 | Character definitions, catalog, IDs (14), legacy aliases squirrel→dog, hedgehog→goat, sheep→cat, lion→donkey | CharacterDefinition, CharacterDefinitions, GeneratedCharacterDefinitions, LudoPawsCharacterCatalog, LudoPawsCharacterModels, CharacterRegistry, GeneratedCharacterEntries; content-manifest.json | all | scripts/game/characters/catalog.gd | done (identity, aliases, free set, registry order) / todo (voice/animation set ids) |
| B2 | Packs: starter_paws + paid packs, free set dog/goat/duck/cat | CharacterPackDefinition, CharacterPackDefinitions, StarterPawsPack | shop/selection | catalog.gd | done — live has one pack (starter_paws); paid paws are store items (H1), not packs |
| B3 | Starter Paws assignment + selection store | StarterPawsAssignmentPolicy, CharacterSelectionStore, LudoPawsCharacterRuntime | setup, home | catalog + preview_state | tested (starter_assignment.gd: live policy tests ported; cosmetic_inventory.gd unlock rule; Robot lobby lists only unlocked Paws, matches use the live assignment) / todo (per-seat Paw picker for Pass & Play and mixed robot teams, G) |
| B4 | Character ownership (client inventory, server character-access) | server: character-access.js, ludo-paws-characters.js | equip, online | inventory (P6) | todo |
| B5 | Shared rendering: draw context, renderer interface, surfaces, avatar renderer | CharacterDrawContext, CharacterRenderer, CharacterSurface, CharacterAvatarRenderer | pawns, portraits | paw_draw.gd Pool (+ prepare_head, draw_living_eye, draw_tail), paw_parts shader | tested (dog draw-call parity) |
| B6 | Shared meshes and shader | MeshGeometry, SphereGeometry, CharacterMesh, CharacterShaders, GlProgram | 3D | paw_geometry.gd, paw_parts.gdshaderinc | tested (loft/sphere checksums via dog parity) |
| B7 | Shared motion: hop dynamics, living behaviour, life motion, pet jump, materials, reaction profile | CharacterHopDynamics, CharacterLivingBehavior, LudoPaws3DLifeMotion, Pet3DJump, CharacterMaterials, CharacterReactionProfile | pawns | scripts/game/characters/shared/{pose,hop_dynamics,living_behavior,life_motion,pet_jump,reaction_motion}.gd | tested (hop, life, idle blend via dog parity) / todo (Pet3DJump used by later species) |
| B8 | Dog (geometry, materials, renderer, motion, reactions, leg rig, face, helpers, avatar) | DogDefinition, DogGeometry, DogMaterials, DogRenderer, Dog3DMotion, DogReactionMotion, DogLegRig, DogFaceRenderer, DogDrawHelpers, DogAvatarRenderer | pawn/portrait | scripts/game/characters/dog/* (definition, geometry+materials, motion+reaction, leg rig, face, renderer, avatar) | tested (all 10 files; draw-call parity with live Kotlin) |
| B9 | Goat | GoatDefinition, GoatGeometry, GoatMaterials, GoatRenderer, Goat3DMotion, GoatReactionMotion, GoatLegRig, GoatFaceRenderer, GoatDrawHelpers, GoatAvatarRenderer | pawn/portrait | scripts/game/characters/goat/* | tested (all 10 files; draw-call parity with live Kotlin) |
| B10 | Duck | DuckDefinition, DuckGeometry, DuckMaterials, DuckRenderer, Duck3DMotion, DuckReactionMotion, DuckFaceRenderer, DuckDrawHelpers, DuckAvatarRenderer | pawn/portrait | scripts/game/characters/duck/* | tested (all 9 files; draw-call parity with live Kotlin) |
| B11 | Cat | CatDefinition, CatGeometry, CatMaterials, CatRenderer, Cat3DMotion, CatReactionMotion, CatLegRig, CatFaceRenderer, CatDrawHelpers, CatAvatarRenderer | pawn/portrait | scripts/game/characters/cat/* | tested (all 10 files; draw-call parity incl. limb bend and coat-mapped locals) |
| B12 | Donkey (incl. gait) | DonkeyDefinition, DonkeyGeometry, DonkeyMaterials, DonkeyRenderer, Donkey3DMotion, DonkeyReactionMotion, DonkeyLegRig, DonkeyGait, DonkeyFaceRenderer, DonkeyDrawHelpers, DonkeyAvatarRenderer | pawn/portrait | scripts/game/characters/donkey/* | tested (all 11 files incl. gait; draw-call parity) |
| B13 | Goat Lumi | LumiGoatDefinition, LumiGoatRenderer, LumiGoatReactionMotion | pawn/portrait | scripts/game/characters/goat_lumi/* | tested (all 3 files: rig batches, fur strands, avatar; draw-call parity) |
| B14 | Cock | CockDefinition, CockGeometry, CockRenderer, CockReactionMotion, CockAvatarRenderer | pawn/portrait | scripts/game/characters/cock/* | tested (all 5 files; draw-call parity) |
| B15 | Pig | PigDefinition, PigGeometry, PigRenderer, PigReactionMotion, PigAvatarRenderer | pawn/portrait | scripts/game/characters/pig/* | tested (all 5 files incl. corkscrew tail sweep; draw-call parity) |
| B16 | Elephant | ElephantDefinition, ElephantGeometry, ElephantRenderer, ElephantReactionMotion, ElephantAvatarRenderer | pawn/portrait | scripts/game/characters/elephant/* | tested (all 5 files incl. shader-curled trunk; draw-call parity) |
| B17 | Horse | HorseDefinition, HorseGeometry, HorseRenderer, HorseReactionMotion, HorseAvatarRenderer | pawn/portrait | scripts/game/characters/horse/* | tested (all 5 files; draw-call parity) |
| B18 | Rabbit | RabbitDefinition, RabbitGeometry, RabbitRenderer, RabbitReactionMotion, RabbitAvatarRenderer | pawn/portrait | scripts/game/characters/rabbit/* | tested (all 5 files; draw-call parity) |
| B19 | Panda (incl. posture) | PandaDefinition, PandaRenderer, PandaPosture, PandaReactionMotion, PandaAvatarRenderer | pawn/portrait | scripts/game/characters/panda/* | tested (all 5 files incl. all-fours posture; draw-call parity) |
| B20 | Monkey | MonkeyDefinition, MonkeyRenderer, MonkeyReactionMotion, MonkeyAvatarRenderer | pawn/portrait | scripts/game/characters/monkey/* | tested (all 4 files; draw-call parity) |
| B21 | Giraffe | GiraffeDefinition, GiraffeGeometry, GiraffeRenderer, GiraffeReactionMotion, GiraffeAvatarRenderer | pawn/portrait | scripts/game/characters/giraffe/* | tested (all 5 files; draw-call parity incl. patch split) |
| B22 | Team identity separate from animal colour (base/ring/glow) | LudoPawsTeamIdentityPolicy | pawns, cards | scripts/game/characters/shared/team_identity.gd | done (palette/sigils) / todo (used by species that draw team marks) |
| B23 | 3D character identity per seat | LudoPaws3DCharacterPolicy | match | classic_board_3d.gd (characterId per seat via catalog.resolve_id) | done |
| B24 | Portrait cache + avatar view + player card | LudoPawsCharacterPortraitBitmapCache, LudoPawsCharacterAvatarView, LudoPawsPlayerCardView | setup, rail, profile, shop | scripts/game/characters/character_portrait.gd (+ scenes/previews/characters_preview.tscn) | done (3D portrait, rail player cards) / todo (setup, profile) |
| B25 | Character mastery + profile progression + badges | CharacterMasteryPolicy, ProfileProgression, MatchProgressSummary | profile, badges | hub_pages.gd profile/badges data | todo |
| B26 | Home pets hero scene | HomePetsHeroView | Home | home_screen hero (V2 art stays; live 3D pets only if asked) | todo (ask) |
| B27 | Online character identity contract, policy, account selection | LudoPawsCharacterIdentityContract, OnlineLudoPawsCharacterPolicy, RemoteCharacterAccountSelection | online lobby/match | online client (P9) | todo |
| B28 | Server matchmaking characters + bot characters | server: matchmaking-characters.js, matchmaking-bots.js, match-room-bots.js | online bots | backend | backend |

## C. In-match presentation and behaviour (P2)

| ID | Feature / behaviour | Live sources | Where used | V2 target | Status |
|---|---|---|---|---|---|
| C1 | Board geometry mirror (stretched axes, token centres, yard/finish slots) | LudoPawsFxBoardGeometry, LudoPawsYardGeometry | all | board3d/board_geometry.gd | done |
| C2 | Pawn layout: stacks, radius, road presentation, foot contact | LudoPawsPawnLayout | match | board_geometry.gd stack_placements, road_presentation, silhouette_height; pawn_layer.gd | tested (road foot drop + radius: layout_golden.json from the unchanged live LudoPawsPawnLayout) / done (stack, foot-contact root) |
| C3 | 3D scene renderer + scene view + reactive board shell | LudoPaws3DSceneRenderer, LudoPaws3DSceneView, LudoPawsReactiveBoardView, LudoPawsBoardView, LudoBoardView | match | board3d/classic_board_3d.gd | done (board, houses, 3D animal layer with all 14 species) |
| C4 | Render cadence + display pacer (battery) | LudoPaws3DRenderCadencePolicy, LudoPaws3DDisplayPacer | match | render_cadence.gd (policy + display pacer); classic_board_3d.gd renders the pawn viewport only on paced frames, none while hidden | tested (ports of the live cadence + pacer unit tests) |
| C5 | Pawn animation policy (which transitions animate) | LudoPawsPawnAnimationPolicy | match | pawn_motion_policy.gd (transition, visual steps/cells), classic_board_3d.gd _sync_motion | tested (motion_golden.json: every from→to pair from the unchanged live policy; board test) |
| C6 | Facing policy | LudoPawsPawnFacingPolicy | match | pawn_layer.gd (yaw_for_visible_delta, moving/settling yaw) | done |
| C7 | Capture return placement + animation | LudoPawsCaptureReturnPlacement | match | capture_return_placement.gd, classic_board_3d.gd (waits until contact, shake → pop → curved trip → settle; IDLE body, front yaw, single-pawn size; golden from live + board test) | tested |
| C8 | Home entry (door, walk-in, plaque) | LudoPawsHomeEntryPolicy, LudoPawsCenterHouses3D | match | center_houses.gd, classic_board_3d.gd | done |
| C9 | Reaction motion + situational acting + per-pawn variation | LudoPaws3DReactionMotion, LudoPawsSituationalActing, LudoPawsPawnReactionVariation | match | shared/reaction_motion.gd (sample, variation, situational, observer); classic_board_3d.gd play_reactions | done (pose layers, per-pawn playback, group variation, capture/contact alignment, bystander, reduced-motion/hop/home-entry gating) |
| C10 | Reaction pipeline: moment detector, engine, director, idle policy, feedback ledger, FX policy, sequence tail (six, capture, captured, safe, home, frustrated, third_six, idle, nervous, victory, defeat) | LudoPawsGameMomentDetector, LudoPawsReactionEngine, LudoPawsReactionDirector, LudoPawsIdleReactionPolicy, LudoPawsFeedbackLedger, LudoPawsFxPolicy, SequenceTail | match | scripts/game/reactions/*.gd (+ match_snapshot.gd: offline history/eventIndex like OfflineGameEngine); board idle clock | tested (golden: 3,239 steps from the unchanged live Kotlin pipeline, every moment type) |
| C11 | Legal-move halo/shimmer | LudoPaws3DLegalHaloView | match | V2-style shimmer (ask if different) | done (legal_hints.gd: live sheen, sparkle, tile corner marks, 5+ crowd count; V2 Meadow colours + dark keyline; pulse timing and contrast ≥3:1 per ported board tested; classic board screenshot checked) |
| C12 | Tap hand + first-match coach | LudoPawsTapHandArt, FirstMatchCoachPolicy, FirstMatchCoachUi | first match | V2-style hint (ask) | done (tap hand: live shape and 1.4 s / 3.6 s cadence in V2 wood colours; first-match coach in the status line, tap to hide turns play hints off; policy tested) / todo (first-match flag from the profile store, G) |
| C13 | FX overlay (captures, sixes, safe, home bursts) | LudoPawsGameFxOverlayView, LudoPawsFxPainter | match | fx_painter.gd (every live draw call, golden via a recording canvas stub), fx_overlay.gd (bind/play timeline), drawn on the board overlay | tested (fx_golden.json: 280 effect frames from the unchanged live painter; timeline test) |
| C14 | Yard footprints + habitat + palettes | LudoPawsYardFootprintPolicy, LudoPawsYardHabitatRenderer, LudoPawsYardPalettePolicy | yards | yard_paw_prints.gd on the board chrome layer (below pawns) | done (species paw prints: policy + species mapping tested, artwork + grain tile ported; the habitat + yard palettes belong only to the retired Dawn/Moonlit boards → out of scope) |
| C15 | Board chrome: finish rank badges, footprint overlay | LudoPawsBoardChromeView | match/results | classic_board_3d.gd (_chrome layer: paw prints; ranks on the overlay) | done |
| C16 | Gameplay pacing (dice hold, auto-move delay, capture return, travel, breath) | LudoPawsGameplayPacingPolicy, OnlineGameplayPacingPolicy | match | gameplay_pacing.gd (all three speeds), local_turn_flow.gd (roll delay, result hold, move lock, unlock, CPU think, forced move), board/FX durations follow the speed | tested (live pacing unit tests ported, flow timeline test, smoke) / todo (OnlineGameplayPacingPolicy with online play, I) |
| C17 | Movement sounds per step | LudoPawsMovementSoundPolicy | match | audio (P2) | tested (movement_sound_policy.gd + board movement audio clock: jump per hop, footstep per cell, newest landmark, capture impact at contact) |
| C18 | Result card + result presentation + result art | LudoPawsResultCard, LudoPawsResultPresentation, GameResultArtView | results | match_pages.gd results (V2 look) | done (result_presentation.gd: live title/subtitle/placements on the V2 Results screen, tested; V2 trophy art) / todo (XP line with progression G, Gems line with the wallet H, daily-challenge flag) |
| C19 | Fallback 2D pawn when 3D unavailable | LudoBoardView (drawPawn) | match | classic_board_3d.gd | done |

## D. Audio (P2)

| ID | Feature / behaviour | Live sources | Where used | V2 target | Status |
|---|---|---|---|---|---|
| D1 | Character voices (14 × ~15 clips), voice player, asset player | LudoPawsVoicePlayer, LudoPawsAudioAssetPlayer; audio/sfx/voices/** | reactions | scripts/game/audio + assets (license check Q-Audio) | tested (voices in GameAudio.play_voices: capture pair batch, plan + contact delay, voiceless characters silent, owner fallback (Lumi→goat), procedural call, Animal voices setting; 195 clips copied) |
| D2 | Procedural audio fallbacks | LudoPawsProceduralAudio | reactions | audio | tested (procedural_audio.gd: every SFX/vocal preset equals the live synth, audio_golden.json) |
| D3 | Character + reaction audio profiles | LudoPawsCharacterAudioProfiles, GeneratedCharacterAudioProfiles, LudoPawsReactionAudioProfile | reactions | audio | tested (audio_catalog.gd profiles, reaction_audio_profile.gd: every species × cue plan equals live) |
| D4 | Gameplay SFX (dice, settle, six, third six, capture, safe, home, home lane, yard exit, exact-home miss, fail, victory, defeat, jump) + UI click | LudoPawsAudioCatalog, LudoPawsSoundPool, LudoPawsAudioQueuePolicy, UiClickSoundInstaller, GameSoundFeedback; audio/sfx/** | match, UI | audio bus + players | done (game_audio.gd autoload: catalog takes, rotation, event takes, 8-stream priority pool, Effects setting, every gameplay SFX, offline_feedback.gd once-per-action roll/move sounds, click on every button) / todo (dice-skin sound overrides with F) |
| D5 | Music + ambience | GameMusicController; audio/music, res/raw/ludoproof_theme.mid | app | music player + settings | done (GameAudio music: title theme loop at 0.16, 0.075 under animal calls for 650 ms, Music setting, pause in background; live boards define no own music/ambience, so the per-board paths come with E) |
| D6 | Haptics | LudoPawsHaptics | reactions | Input.vibrate_handheld | device-only (vibration needs a phone) |

## E. Boards (P3)

| ID | Feature / behaviour | Live sources | Where used | V2 target | Status |
|---|---|---|---|---|---|
| E1 | Board registry + definitions (8 live: classic, astral, cloud_lagoon, autumn_forest, blossom_garden, winter_haven, monsoon_mire, sunstone_palace) | BoardDefinition, BoardRegistry, GeneratedBoardDefinitions | shop, equip, match | scripts/game/boards/registry.gd | done (store_catalog.gd BOARD_IDS + board_art.json metadata: surface, neutral, stone-road and monsoon flags; equipped board used by local matches) |
| E2 | Renderer registry/factories | BoardRenderer, BoardRendererRegistry, GeneratedBoardRendererFactories | match | registry.gd | done (board_art_player.gd replays the recorded live Canvas commands: transforms, rect clips, fills/strokes, linear/radial gradients; classic keeps its hand port) |
| E3 | Classic (renderer, habitat, road relief) | ClassicBoardDefinition, ClassicBoardRenderer, ClassicJungleHabitat, ClassicRoadScene | default board | board3d/* | done |
| E4 | Astral | AstralBoardDefinition, AstralBoardRenderer | equip | boards/astral | done (recorded from the unchanged live renderer: assets/boards/board_art.json via tests/fixtures/boards_gen; replayed by board_art_player.gd; screenshot board_hints_<id>.png checked) |
| E5 | Autumn forest | AutumnBoardDefinition, AutumnBoardRenderer | equip | boards/autumn | done (recorded from the unchanged live renderer: assets/boards/board_art.json via tests/fixtures/boards_gen; replayed by board_art_player.gd; screenshot board_hints_<id>.png checked) |
| E6 | Blossom garden | BlossomBoardDefinition, BlossomBoardRenderer | equip | boards/blossom | done (recorded from the unchanged live renderer: assets/boards/board_art.json via tests/fixtures/boards_gen; replayed by board_art_player.gd; screenshot board_hints_<id>.png checked) |
| E7 | Cloud lagoon | CloudLagoonBoardDefinition, CloudLagoonBoardRenderer | equip | boards/lagoon | done (recorded from the unchanged live renderer: assets/boards/board_art.json via tests/fixtures/boards_gen; replayed by board_art_player.gd; screenshot board_hints_<id>.png checked) |
| E9 | Monsoon mire (renderer + 3D weather scene) | MonsoonBoardDefinition, MonsoonBoardRenderer, MonsoonBoardScene | equip | boards/monsoon | done (board art recorded from the live renderer) / todo (MonsoonBoardScene 3D rain/weather layer) |
| E10 | Sunstone palace | SunstonePalaceBoardDefinition, SunstonePalaceBoardRenderer | equip | boards/sunstone | done (recorded from the unchanged live renderer: assets/boards/board_art.json via tests/fixtures/boards_gen; replayed by board_art_player.gd; screenshot board_hints_<id>.png checked) |
| E11 | Winter haven | WinterBoardDefinition, WinterBoardRenderer | equip | boards/winter | done (recorded from the unchanged live renderer: assets/boards/board_art.json via tests/fixtures/boards_gen; replayed by board_art_player.gd; screenshot board_hints_<id>.png checked) |
| E13 | Gameplay backdrop + environment painter | GameplayBackdropDrawable, GameplayBoardEnvironment, GameplayEnvironmentPainter | match background | V2 match page backdrop (game content, not UI chrome — ask) | todo |
| E14 | Stone road overlay for non-classic boards | LudoPawsCenterHouses3D (drawStoneRoads) | other boards | center_houses.gd | done (center_houses.gd stone_road_artwork: live slabs, joints and safe shields on astral + cloud lagoon) |
| E15 | Online board choice (matchStyle.boardId) | OnlineMatchAppearance | online | P9 | todo |
| E16 | Board swap never changes route/safe cells/hit-testing (test) | — | — | tests | tested (board swap leaves pawn positions and hit-testing identical; unknown ids fall back to classic) |

## F. Dice (P4)

| ID | Feature / behaviour | Live sources | Where used | V2 target | Status |
|---|---|---|---|---|---|
| F1 | Dice registry + definitions (8 live: classic, pearl_paw, forest_dew, lagoon_pearl, frost_crystal, starlight, ember_forge, prism_royal) | DiceDefinition, DiceRegistry, GeneratedDiceDefinitions | shop, match | scripts/game/dice/registry.gd | done (store_catalog.gd DICE_IDS, dice_renderers.gd palettes)  |
| F2 | Renderer registry/factories | DiceRenderer, DiceRendererRegistry, GeneratedDiceRendererFactories | match | registry.gd | done (dice_renderers.gd create(); canvas_emitter.gd = Android Canvas look-alike in the recorder format) |
| F3 | Classic dice | ClassicDiceDefinition, ClassicDiceRenderer | default | dice/classic | tested (classic dice: every face/state equals the live renderer, dice_golden.json) |
| F4 | Pearl paw | PearlPawDiceDefinition, PearlPawDiceRenderer | equip | dice/pearl | tested (pearl paw: path clip, gradients, paw face; equals live) |
| F5 | Environment dice (forest, lagoon, frost, starlight) | EnvironmentDiceDefinitions, EnvironmentDiceRenderer | equip | dice/environment | tested (forest, lagoon, frost, starlight: live artwork at decode size + perspective-projected pips; equals live) |
| F6 | Spatial dice (ember forge, prism royal) + motion + surfaces | SpatialDiceDefinitions, SpatialDiceRenderer, SpatialDiceMotion, SpatialDiceSurfaces | equip | dice/spatial | tested (ember forge, prism royal: bevelled mesh, quaternion roll/settle motion, face projection, recorded surface textures; equals live incl. rolling/settling frames) |
| F8 | Dice view, roll animation policy, style palette + team border, turn countdown ring (15 s) | DiceView, DiceRollAnimationPolicy, DiceStylePalettePolicy, DiceTurnCountdownPolicy | match | dice view in match page (countdown ring V2 style — ask) | done (dice_view.gd: equipped live skin, team border, live rolling/settle/attention policies, spatial roll; in the match panel) / todo (15 s turn countdown ring for online play, I) |
| F9 | Outcome decided before animation (test); online shows verified server outcome | OnlineVerifiedDicePresentationPolicy | match | tests + online | todo |
| F10 | Online dice choice (matchStyle.diceId) | OnlineMatchAppearance | online | P9 | todo |

## G. Modes, social and flows (P5)

| ID | Feature / behaviour | Live sources | Where used | V2 target | Status |
|---|---|---|---|---|---|
| G1 | Turn automation: 15 s roll timer, 45 s move timer, missed-turn forfeit (v4+) | LudoTurnAutomationPolicy, DiceTurnCountdownPolicy | online (+ offline?) | engine forfeit_timed_out_turn + match page | tested (engine) / todo (timers UI) |
| G2 | Online auto-move scheduler | OnlineAutoMove, OnlineAutoMoveScheduler | online | P9 | todo |
| G3 | Quick chat (animated emoji assets, 25 webp) | QuickChatUi, QuickChatAnimatedEmojiView; assets/quickchat/** | match | new Meadow quick-chat panel | todo |
| G4 | Family age screen, age slider, access policy, privacy dialog, online safety notice, Play age signals | FamilyAgeScreen, AgeSelectionSlider, AgeAccessPolicy, FamilyPrivacyDialog, FamilyOnlineSafety, PlayAgeSignalsObserver | first Home, online entry | new Meadow screens | todo |
| G5 | Friends: presence, identity, room handoff, list/requests/messages/block/report | FriendPresenceController, FriendIdentityStore, FriendRoomHandoffValidator, FriendsActivity, FriendsPresentationFormatters, FriendsVisualKit | Friends | lobby_pages.gd friends + hub invite | todo |
| G6 | Room links / invites / share links | RoomLink, RoomLinkActivity, GameShareLink | invite, deep link | Invite page + Android intent filter (P10) | todo |
| G7 | Match share card | MatchShareCard | results | results Share | todo |
| G8 | Team Up: matchmaking API/store, cancellation recovery, search requests, team results | TeamUpActivity, TeamMatchmakingApi, TeamMatchmakingStore, TeamCancellationRecovery, TeamSearchRequests | Team Up | lobby team_up | todo |
| G9 | Public matchmaking (search/cancel/status) | PublicMatchmakingActions, PublicMatchmakingStore, OnlineMatchmakingSearchView | Online | lobby online | todo |
| G10 | Leaderboard API, cache, identity | LeaderboardApi, LeaderboardCacheStore, LeaderboardIdentityStore, LeaderboardModels, LeaderboardActivity | Leaderboard | hub leaderboard | todo |
| G11 | Profile store, nickname rules (6–7 chars), stats, purchases list, menu (legal, account) | ProfileStore, ProfileModels, ProfileActivity, ProfileCommonUi, ProfileHeaderUi, ProfileStatsUi, ProfilePurchasesUi, ProfileMenuUi, ProfileMasteryUi | Profile | hub profile | todo |
| G12 | Badges catalog | BadgesActivity, ProfileBadgesUi, PawsBadgeView | Badges | hub badges | todo |
| G13 | Growth: engagement milestones, weekly challenge, prompt gate, metrics | EngagementStore, WeeklyChallengeStore, AutomaticPromptGate, GrowthMetrics, GrowthUi | Home/results | hub rewards/badges | todo |
| G14 | Rating prompt (natural review) | NaturalReviewController, HomeRatingDialogUi, HomeRatingButtonArt | Home | new Meadow rating dialog + Play in-app review | done (code): NaturalReviewController + AutomaticPromptGate; untested on device |
| G15 | Referrals: repository, popup policy, bundle popup, promotion controller, timed offer, reward view | ReferralRepository, ReferralPopupPolicy, ReferralBundlePopup, ReferralPromotionController, TimedFriendshipOffer, ReferralGemRewardView, FriendshipBundleView, ReferralActivity | Home/Store/Invite | Invite page + new Meadow referral popup | todo |
| G16 | Rewards hub: daily gift, reward wheel, paw trail, Home indicator | RewardsActivity, HomeRewardsController, DailyGiftChestView, RewardWheelView, RewardPawTrailView | Rewards | hub rewards + new Meadow daily gift / wheel | done: server rewards; V2 chest / wheel (server-chosen result) / ad bonus card; Garden Friend 7-gift progress (no Gem milestone); Home rewards indicator: todo |
| G17 | Match gem rewards (results) | server: match-gem-rewards.js; /api/wallet/match-reward/status | results | results + wallet (P6) | todo |
| G18 | Home: modes, header, promotion carousel, guide dialog, layout | HomeActivity, HomeModesUi, HomeHeaderUi, HomeHeroUi, HomeLayoutUi, HomePromotionCarousel, HomeGuideDialog, HomeBottomActionsUi, HomeBrandLogoView, HomeGlassChrome, HomeIconView, HomeModeButtonChrome, HomePathCompat, HomeSpaceBackgroundView, HomeHeroArtView, ModeArtView | Home | home_screen (V2 look; data/actions only) | todo |

## H. Economy, shop, billing, ads (P6–P8)

| ID | Feature / behaviour | Live sources | Where used | V2 target | Status |
|---|---|---|---|---|---|
| H1 | Store catalog + cosmetic catalog + offers + models | StoreCatalog, StoreCosmeticCatalog, CosmeticOfferFactory, CosmeticModels, StoreModels, StoreCatalogRepository; server: store-catalog.js, store-catalog-store.js; release/v11/STORE_CATALOG_SCHEMA.json | Shop | scripts/economy/catalog.gd + hub shop | done (store_catalog.gd: live items only, offers, defaults, remote merge; tested); StoreCatalogParser + cache `ludopaws_store_catalog_cache_v1` (parse tests; A71: live catalog read, 0 published items today) |
| H2 | Prices exactly live (incl. remote overrides) | CosmeticPricing; server: cosmetic-pricing.js; release/v11/pricing-plan.json | Shop | catalog.gd + test vs pricing-plan.json | tested (every live price, ad count and channel checked; remote override test) |
| H3 | Smart unlock decision | SmartUnlockPolicy | Shop | catalog.gd | tested (SmartUnlockPolicy decisions) |
| H4 | Gem wallet cache (server snapshot only), inventory, equip sync, preferences | GemWalletStore, CosmeticInventoryStore, LocalEquipmentSync, StorePreferences | Shop/Wallet/Collections | economy stores | done: cosmetic_inventory.gd on the live `ludoproof_wallet` prefs via NativePrefs (account-scoped, revision guard, legacy archive, offline/pending equipment + LocalEquipmentSync, character selection in `ludo_paws_character_selection_v1`); store_service.gd against the live server (A71: live wallet cached) |
| H16 | Gem plans + Remove Ads (live GEMS tab) in the V2 Wallet screen | StoreCatalog.products, StoreActivity (GEMS tab, requestPurchase, restore) | Wallet | shop_pages.gd wallet() | done (7 live plans, live order and titles, Play-localized prices or "Price unavailable", rewarded ad +2, pending orders, restore re-verify; no client transaction history in live) / device-only (real Play checkout) |
| H5 | Cosmetic purchase/select actions (CONTENT_UNAVAILABLE first) | StoreCosmeticActions; /api/wallet/cosmetics/purchase, /api/wallet/cosmetics/select | Shop/Collections | economy actions | tested (store_service.gd: live request shapes for purchase/select/verify; desktop backend applies the live server rules — price guard, balance, ad progress, ownership, token replay; HTTP backend with I) |
| H6 | Purchase confirm UI | StoreContentUi, CosmeticPriceLabels, StoreActionLabels | Shop | new Meadow purchase-confirm dialog | done (shop_pages.gd unlock chooser dialog: Gems / Play / watch ad / claim with live labels, error texts) |
| H7 | Shop catalog presentation data (paws/boards/dice tabs, featured paws, promotion, spotlight, rows) | StoreActivity, StoreTabsUi, StorePawsCatalogUi, StoreBoardCatalogUi, StoreDiceCatalogUi, FeaturedPawsPreview, GamePawsPromotionView, PromotedStoreItemCard, StoreLockedSpotlightView, VirtualCatalogRows, StoreCatalogScrollView, StoreCatalogArtwork, StoreHeaderUi, StoreSurfaceBackgrounds, GemClusterView, GemPackArtworkView, StoreAwningView, PromotionInterest | Shop | hub shop/collections (V2 look) | done (shop_pages.gd in the V2 Shop screen: Paws / Boards / Dice tabs, only live items, 3D portraits / board art / dice skins, Gem + Play prices, ad progress, owned/equipped; shop_paws/boards/dice.png) / todo (featured / Game Paws promotion) |
| H8 | Google Play Billing gateway, pending purchases, verifier, install account id, account safety, fulfillment | GooglePlayBillingGateway, PendingStorePurchaseStore, StorePurchaseVerifier, StoreInstallAccountId, PurchaseAccountSafety, StorePurchaseFulfillment; /api/store/purchases/verify, /api/store/status | Wallet | billing plugin + economy/billing.gd | done (play_billing.gd seam: localized prices from Play, purchase → server verify → acknowledge; desktop stub) / device-only (Play Billing plugin — UNTESTED ON DEVICE) |
| H9 | Remove-ads purchase policy | RemoveAdsPurchasePolicy | Shop/ads | billing.gd | device-only (same) |
| H10 | Billing reconciliation / refunds / ledger | server: google-play-billing.js, billing-reconciliation.js, store-purchase-ledger.js | backend | — | backend |
| H11 | Rewarded ads controller + pending ad reward store + SSV | RewardedAdsController, PendingAdRewardStore; server: admob-ssv.js | Rewards | ads plugin + economy/ads.gd | device-only (real ad fill) |
| H12 | Post-win interstitial + exit gate + ad break session/policy | PostWinInterstitialAdController, PostWinInterstitialExitGate, PostWinInterstitialPolicy, AdBreakSession, AdBreakPolicy | results exit | ads.gd | device-only |
| H13 | Family ads privacy (most restrictive for unknown age), UMP consent | FamilyAdsPrivacy | ads | ads.gd + UMP | device-only |
| H14 | Rare gem artwork (currency art) | RareGemArtwork | wallet/shop | V2 assets/ui gem (UI rule: keep V2) | no-port (UI art; V2 gem icon stays) |
| H15 | Engagement rewards / daily gift / wheel server receipts | server: engagement-rewards.js; /api/rewards, /api/rewards/availability | Rewards | economy/rewards.gd | done (V2 Rewards screen: daily gift 3, free spin 1/2/3/5 at 50/30/15/5 % picked by the server, daily ad bonus 2 once a day, 7-claim milestone; store_service rewards API, desktop stub applies engagement-rewards.js, tested) / todo (Collections bundles on Rewards, HTTP backend with I) |

## I. Account and online (P9)

| ID | Feature / behaviour | Live sources | Where used | V2 target | Status |
|---|---|---|---|---|---|
| I1 | Account repository + session store (guest, google link/login, refresh, logout, delete, recover) | AccountRepository, AccountSessionStore; /api/account/* | everywhere | scripts/game/account/account_service.gd + account.gd (autoload) | done (tests account_contract_smoke 22; live guest on A71). Friend / leaderboard identity restore with B6 |
| I2 | Secure session storage (encrypted) | SecureSessionStore | account | LudoPawsPlatformPlugin.java (live AccountSessionStore unchanged) | done, A71 verified (reload after restart) |
| I3 | Google sign-in (Credential Manager ID token) | ProfileActivity, ProfileHeaderUi (GoogleIdTokenCredential flow) | Profile link/restore | LudoPawsPlatformPlugin.googleSignIn + Profile Manage Account | built; **untested on device** (needs production package + release signing for the OAuth client) |
| I4 | Play Games platform sign-in at startup (no server auth) | StartupPlayGames, PlayGamesStartupState | startup | PlayGamesStartup.java (PGS v2 22.1.0, adult age choice, production package only) | built; **untested on device** (production package only) |
| I5 | HTTP transport + GameApi (command IDs, idempotent retry) | GameHttpTransport, GameApi, RemoteForfeitApi | online | scripts/online/game_api.gd (URL from gitignored config) | todo |
| I6 | Realtime WebSocket client, command channel, reconnect backoff, failure policy | MatchRealtimeClient, MatchCommandChannel, RealtimeReconnectBackoff, RealtimeConnectionFailurePolicy | online | scripts/online/realtime.gd | todo |
| I7 | Pending operations, cached match, snapshot freshness, restore | PendingOperationStore, CachedMatchStore, OnlineSnapshotFreshnessPolicy, OnlineStateRestorePolicy | online | online stores | todo |
| I8 | Online match actions, session actions, response/verification rendering, roll action policy | OnlineMatchActions, OnlineSessionActions, OnlineResponseRenderer, OnlineVerificationRenderer, OnlineRollActionPolicy, OnlineGameUiState | online | online controller + match page | todo |
| I9 | Online screen composition data (lobby, header, status, primitives, art, rail alignment, presentation bridge) | MainActivity, MainActivityScreen, MainActivityRealtimeCallbacks, OnlineLobbyUi, OnlineHeaderUi, OnlineStatusUi, OnlineActionUi, OnlinePrimitives, OnlineLobbyArtView, OnlineBoardRailAlignmentPolicy, OnlineLudoPawsPresentation | Online/Friends/Team Up | lobby_pages.gd + match page (V2 look) | todo |
| I10 | Connectivity monitor | ConnectivityMonitor | online | Godot HTTP/OS signals | todo |
| I11 | Voice chat (WebRTC) | VoiceCandidateBufferPolicy, VoicePeerStatusPolicy | — | — | no-port (live removed mic permission + WebRTC; privacy policy says voice unavailable) |

## J. App level (P10)

| ID | Feature / behaviour | Live sources | Where used | V2 target | Status |
|---|---|---|---|---|---|
| J1 | Startup splash + session coordinator | GameStartupSplashView, StartupSessionCoordinator, LudoProofApplication | launch | Godot boot splash + V2 startup scene (Rekixo branding) | todo |
| J2 | Settings: sound, music, haptics, speed, privacy policy, account deletion, guide | GameSettingsStore, SettingsDialogUi, SettingsVisualKit, SettingsAudioIconView, SettingsSharedWebpDrawable, PrivacyPolicyDialogUi | Settings | hub settings (V2) | done: live GameSettingsStore keys in `ludoproof_game_settings`; Privacy Policy + online deletion links (How to Play / Quick Chat credits: todo) |
| J3 | Hindi + English strings | res/values/strings.xml (33), res/values-hi/strings.xml (30) + in-code strings | all | Godot translations (.po/.csv) | todo |
| J4 | Play in-app update | PlayUpdateCoordinator, UpdateExperiencePolicy | Home | own Android plugin | done (code): live PlayUpdateCoordinator port; untested on device (Play-installed build only) |
| J5 | Install referrer | ReferralRepository (installreferrer) | referrals | own Android plugin | done (code): referral service + install referrer + integrity + V2 referral screen; untested on device |
| J6 | Android package com.rekixo.ludopaws, permissions (INTERNET, NETWORK_STATE, VIBRATE, BILLING; AD_ID/RECORD_AUDIO/AdServices removed), deep links, FileProvider | AndroidManifest.xml, build.gradle.kts | release | export_presets (release preset in gitignored config) | todo — see Q-Package |
| J7 | Migration of old app data (SharedPreferences: selection, settings, wallet cache, saved match, session) | all *Store files | first launch after update | migration plugin/script | done: V2 uses the live SharedPreferences files directly (see RELEASE_READINESS B7 table); A71 kill/resume verified; install -r over the Play app = Phase 3 |
| J8 | Window/immersive, foreground visibility, navigation de-dup, write queue, foreground results | ImmersiveGameWindow, ActivityVisibilityPolicy, PlayerNavigation, LatestWriteQueue, ForegroundResults | app | Godot project settings + small helpers | todo |
| J9 | Asset contract + bitmap loader | LudoPawsAssetContract, LudoPawsBitmapLoader; content/content-manifest.json | assets | Godot import | todo |
| J10 | Live UI kit (plywood, arcade dialogs, app buttons/panels/badges/icons, theme tokens) | AppSurfaceBackgrounds, ArcadeBackdropView, PlywoodTextureDrawable, RecessedPlywoodDrawable, AppActionIconButton, AppBadgeView, AppButton, AppFocusRing, AppIconActionButton, AppPanel, AppSectionHeader, AppTextInput, LaserStatusDotView, PlywoodCloseButton, ProfilePlaceholderView, SelectionPopAnimation, ArcadeDialogs, DialogEvidenceComponents, DialogMetricComponents, DialogShellComponents, PlywoodAlertDialogBuilder, AppIconView, AppUiTokens, LudoProofTheme | live UI | — | no-port (UI rule: V2 Meadow kit replaces it) |
| J11 | Signing key, AdMob IDs, backend URL | build.gradle.kts properties | release | gitignored config only | todo |

## K. Backend (stays on live Cloudflare; V2 uses its protocol)

| ID | Area | Live sources | V2 relation | Status |
|---|---|---|---|---|
| K1 | Worker entry, routing, hardening, API gate, capabilities, health | server: index.js, index-hardening.js, api-gate.js, operational-health.js | API contract for I5 | backend |
| K2 | Accounts, directory, security, deletion | server: index-account.js, account-directory.js, account-security.js, player-account.js | I1 | backend |
| K3 | Match rooms (state, lifecycle, realtime, http, validation, social, team, forfeit, bots, command input, events, location) | server: match-room.js, match-room-state.js, match-room-lifecycle.js, match-room-realtime.js, match-room-http.js, match-room-validation.js, match-room-social.js, match-room-team.js, match-room-forfeit.js, match-room-bots.js, match-command-input.js, match-events.js, match-location.js, index-forfeit.js, index-team.js | I5–I8 | backend |
| K4 | Matchmaking queues | server: matchmaker-queue.js, team-matchmaker-queue.js, matchmaking-bots.js, matchmaking-characters.js | G8, G9 | backend |
| K5 | Friends + social | server: friend-directory.js, friend-directory-http.js, friend-directory-identity.js, friend-directory-schema.js, friend-social.js, index-social.js | G5 | backend |
| K6 | Leaderboard | server: leaderboard-core.js, leaderboard-room.js | G10 | backend |
| K7 | Referrals | server: referral-campaign.js, referral-config.js, referral-integrity.js | G15 | backend |
| K8 | Safety | server: age-access-guard.js, family-safety.js | G4 | backend |
| K9 | Dice service + crypto | server: dice-fast.js, crypto.js | online verified dice | backend |
| K10 | Landing pages | server: room-link-landing.js, share-landing.js | G6, G7 | backend |
| K11 | Store, pricing, billing, ads SSV, rewards, gem rewards | server: store-catalog.js, store-catalog-store.js, cosmetic-pricing.js, google-play-billing.js, billing-reconciliation.js, store-purchase-ledger.js, admob-ssv.js, engagement-rewards.js, match-gem-rewards.js | H1–H15 | backend |
| K12 | Characters access | server: character-access.js, ludo-paws-characters.js | B4 | backend |
| K13 | Rules engine | server: game.js, game-v1-core.js, rulesets.js | A1 (ported, golden-tested) | backend |

## L. Docs and assets read

docs/** (70+ design/phase notes; behaviour specs such as GAMEPLAY_PRESENTATION_BEHAVIOR_SPEC.md, ONLINE_REALTIME.md,
COSMETIC_MONETIZATION.md, board/character notes) are used as references per phase. release/v11 holds pricing,
referral and store-catalog contracts (H1, H2, G15). audio/ (45 MB) feeds D1–D5. android assets: quickchat (25 webp, G3),
ludo_paws/catalog.json (J9). res/raw/ludoproof_theme.mid (D5).

## N. Out of scope (not live)

Owner scope correction (2026-11-10): build only what the live app actually offers. These live
sources exist but are draft, retired or coming-soon (`available: false` on the server,
`contentAvailable = false` or retired in the client), so V2 does not build them.

| ID | Feature / behaviour | Live sources | Where used | V2 target | Status |
|---|---|---|---|---|---|
| E8 | Dawn meadow (meadow grove renderer) | DawnMeadowBoardDefinition, MeadowGroveBoardRenderer | not live | — | out-of-scope |
| E12 | Palette boards (classic renderer + palette): alien, checkers, chess, denim, diwali, moonlit grove, neon, penguin, pirate | AlienBoardDefinition, CheckersBoardDefinition, ChessBoardDefinition, DenimBoardDefinition, DiwaliBoardDefinition, MoonlitGroveBoardDefinition, NeonBoardDefinition, PenguinBoardDefinition, PirateBoardDefinition | not live | — | out-of-scope |
| F7 | Palette skins: pumpkin, diwali, football, cricket, summers, colors, heart | PumpkinDiceDefinition, DiwaliDiceDefinition, FootballDiceDefinition, CricketDiceDefinition, SummersDiceDefinition, ColorsDiceDefinition, HeartDiceDefinition | not live | — | out-of-scope |
| N1 | Character packs other than Starter Paws (safari, farm, cozy, wild, pet, jungle) | player-account.js COSMETICS (available: false) | — | — | out-of-scope |
| N2 | Collections containing retired boards (Dawn Adventures, Moonlit Friends) | engagement-rewards.js COLLECTIONS (hidden by the client because an item is not a shop item) | — | — | out-of-scope |

## O. New V2 features (not in live)

Owner-requested features that the live app does not have. They reuse the real engine and
live logic; they never change live behaviour elsewhere.

| ID | Feature | Built from | Status |
|---|---|---|---|
| O1 | First-time Demo match: after the first-launch age entry, a short real Robot match (Easy) with 3 paws already home and 1 paw each on relative cell 30 (DEMO_START_POSITION); real dice and rules; Hindi + English coach hints (once each); Skip; demo results → Continue → Home; `tutorial_completed` flag; "Play tutorial again" in Settings; no Gems, stats, coach counter or ads | scripts/game/tutorial/demo_match.gd, tutorial_coach.gd; scripts/ui/preview/tutorial_pages.gd; local_match_page.gd / local_turn_flow.gd / board3d reused | tested (start state, 200-seed completion, flags, age bands, UI flow; screenshots .godot/preview/tutorial/) |
| O2 | First-launch age entry screen (live FamilyAgeScreen logic: age 1–100, only the band is stored) as a V2 Meadow screen | scripts/game/safety/age_entry.gd, tutorial_pages.gd age() | tested (bands, required on first launch) |

## Owner decisions (2026-11-10)

- AGENTS.md and docs/TECHNICAL_BOUNDARIES.md updated to the full-migration scope (package com.rekixo.ludopaws, live backend, billing, ads). Secrets/signing key never in the repo.
- The repository is private; nothing is pushed until the owner confirms.
- EntroNex: keep plain local RNG (1a) for now; **decide before release** (rows A24, A25).
- Audio and voices may be brought into V2; the owner checks licences (table M).
- Play Console / AdMob details come at P7/P8; until then Google test IDs and stubs.
- Phone testing via USB/ADB when a phase needs it.
- The owner signs releases; the repo keeps only the export preset.
- C11/C12 (2026-11-10): legal-move shimmer, corner marks, count and tap hand keep the live shapes, timing and behaviour, recoloured in the V2 Meadow palette. The first-match coach text goes in the match status line (tap to hide turns off play hints).

## M. Audio and content asset files (source and licence)

One row per live asset file that V2 will use. "Source" is where the file comes from; the licence column is
filled in by the owner (decision 2026-11-10). Files are copied into V2 in the phase that uses them (D1–D5, G3, J9).

| ID | Live file | Use | Source | Licence | Status |
|---|---|---|---|---|---|
| M1 | `android/app/src/main/assets/ludo_paws/catalog.json` | content catalog | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M2 | `android/app/src/main/assets/quickchat/1f44d.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M3 | `android/app/src/main/assets/quickchat/1f451.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M4 | `android/app/src/main/assets/quickchat/1f4aa.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M5 | `android/app/src/main/assets/quickchat/1f525.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M6 | `android/app/src/main/assets/quickchat/1f602.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M7 | `android/app/src/main/assets/quickchat/1f605.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M8 | `android/app/src/main/assets/quickchat/1f606.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M9 | `android/app/src/main/assets/quickchat/1f608.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M10 | `android/app/src/main/assets/quickchat/1f60e.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M11 | `android/app/src/main/assets/quickchat/1f618.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M12 | `android/app/src/main/assets/quickchat/1f61b.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M13 | `android/app/src/main/assets/quickchat/1f61e.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M14 | `android/app/src/main/assets/quickchat/1f620.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M15 | `android/app/src/main/assets/quickchat/1f621.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M16 | `android/app/src/main/assets/quickchat/1f624.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M17 | `android/app/src/main/assets/quickchat/1f62d.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M18 | `android/app/src/main/assets/quickchat/1f62e.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M19 | `android/app/src/main/assets/quickchat/1f635_200d_1f4ab.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M20 | `android/app/src/main/assets/quickchat/1f914.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M21 | `android/app/src/main/assets/quickchat/1f921.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M22 | `android/app/src/main/assets/quickchat/1f923.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M23 | `android/app/src/main/assets/quickchat/1f926.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M24 | `android/app/src/main/assets/quickchat/1f92d.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M25 | `android/app/src/main/assets/quickchat/1f973.webp` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M26 | `android/app/src/main/assets/quickchat/NOTICE.txt` | quick chat emoji | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M27 | `android/app/src/main/res/raw/ludoproof_theme.mid` | theme (MIDI) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M28 | `audio/ambience/.gitkeep` | ambience | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M29 | `audio/music/.gitkeep` | music | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M30 | `audio/music/Firefly-audio-Title-Ludo-Paws-Ro.mp3` | music | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/music/Firefly-audio-Title-Ludo-Paws-Ro.wav` (the live file is WAV data with an .mp3 name; licence still owner to verify) |
| M31 | `audio/sfx/gameplay/capture/.gitkeep` | gameplay SFX (capture) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M32 | `audio/sfx/gameplay/capture/lp_sfx_capture_01.wav` | gameplay SFX (capture) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/capture/lp_sfx_capture_01.wav` (licence still owner to verify) |
| M33 | `audio/sfx/gameplay/defeat/.gitkeep` | gameplay SFX (defeat) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M34 | `audio/sfx/gameplay/defeat/lp_sfx_defeat_01.wav` | gameplay SFX (defeat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/defeat/lp_sfx_defeat_01.wav` (licence still owner to verify) |
| M35 | `audio/sfx/gameplay/dice/.gitkeep` | gameplay SFX (dice) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M36 | `audio/sfx/gameplay/dice/lp_sfx_dice_roll_01.wav` | gameplay SFX (dice) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/dice/lp_sfx_dice_roll_01.wav` (licence still owner to verify) |
| M37 | `audio/sfx/gameplay/dice/settle/.gitkeep` | gameplay SFX (dice/settle) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M38 | `audio/sfx/gameplay/dice/settle/lp_sfx_dice_settle_01.wav` | gameplay SFX (dice/settle) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/dice/settle/lp_sfx_dice_settle_01.wav` (licence still owner to verify) |
| M39 | `audio/sfx/gameplay/exact_home_miss/.gitkeep` | gameplay SFX (exact_home_miss) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M40 | `audio/sfx/gameplay/exact_home_miss/lp_sfx_exact_home_miss_01.wav` | gameplay SFX (exact_home_miss) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/exact_home_miss/lp_sfx_exact_home_miss_01.wav` (licence still owner to verify) |
| M41 | `audio/sfx/gameplay/fail/.gitkeep` | gameplay SFX (fail) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M42 | `audio/sfx/gameplay/fail/lp_sfx_fail_01.wav` | gameplay SFX (fail) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/fail/lp_sfx_fail_01.wav` (licence still owner to verify) |
| M43 | `audio/sfx/gameplay/home/.gitkeep` | gameplay SFX (home) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M44 | `audio/sfx/gameplay/home/lp_sfx_home_01.wav` | gameplay SFX (home) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/home/lp_sfx_home_01.wav` (licence still owner to verify) |
| M45 | `audio/sfx/gameplay/home_lane/.gitkeep` | gameplay SFX (home_lane) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M46 | `audio/sfx/gameplay/home_lane/lp_sfx_home_lane_01.wav` | gameplay SFX (home_lane) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/home_lane/lp_sfx_home_lane_01.wav` (licence still owner to verify) |
| M47 | `audio/sfx/gameplay/home_lane/lp_sfx_home_lane_02.wav` | gameplay SFX (home_lane) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/home_lane/lp_sfx_home_lane_02.wav` (licence still owner to verify) |
| M48 | `audio/sfx/gameplay/movement/jump/lp_sfx_jump_01.wav` | gameplay SFX (movement/jump) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/movement/jump/lp_sfx_jump_01.wav` (licence still owner to verify) |
| M49 | `audio/sfx/gameplay/safe/.gitkeep` | gameplay SFX (safe) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M50 | `audio/sfx/gameplay/safe/lp_sfx_safe_relief_01.wav` | gameplay SFX (safe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/safe/lp_sfx_safe_relief_01.wav` (licence still owner to verify) |
| M51 | `audio/sfx/gameplay/six/.gitkeep` | gameplay SFX (six) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M52 | `audio/sfx/gameplay/six/lp_sfx_six_01.wav` | gameplay SFX (six) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/six/lp_sfx_six_01.wav` (licence still owner to verify) |
| M53 | `audio/sfx/gameplay/third_six/.gitkeep` | gameplay SFX (third_six) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M54 | `audio/sfx/gameplay/third_six/lp_sfx_third_six_01.wav` | gameplay SFX (third_six) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/third_six/lp_sfx_third_six_01.wav` (licence still owner to verify) |
| M55 | `audio/sfx/gameplay/victory/.gitkeep` | gameplay SFX (victory) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M56 | `audio/sfx/gameplay/victory/lp_sfx_victory_01.wav` | gameplay SFX (victory) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/victory/lp_sfx_victory_01.wav` (licence still owner to verify) |
| M57 | `audio/sfx/gameplay/yard_exit/.gitkeep` | gameplay SFX (yard_exit) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M58 | `audio/sfx/gameplay/yard_exit/lp_sfx_yard_exit_01.wav` | gameplay SFX (yard_exit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/gameplay/yard_exit/lp_sfx_yard_exit_01.wav` (licence still owner to verify) |
| M59 | `audio/sfx/ui/click/.gitkeep` | UI click | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M60 | `audio/sfx/ui/click/lp_sfx_ui_click_01.wav` | UI click | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/ui/click/lp_sfx_ui_click_01.wav` (licence still owner to verify) |
| M61 | `audio/sfx/voices/cat/.gitkeep` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M62 | `audio/sfx/voices/cat/lp_vocal_cat_capture_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_capture_01.wav` (licence still owner to verify) |
| M63 | `audio/sfx/voices/cat/lp_vocal_cat_captured_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_captured_01.wav` (licence still owner to verify) |
| M64 | `audio/sfx/voices/cat/lp_vocal_cat_defeat_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_defeat_01.wav` (licence still owner to verify) |
| M65 | `audio/sfx/voices/cat/lp_vocal_cat_frustrated_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_frustrated_01.wav` (licence still owner to verify) |
| M66 | `audio/sfx/voices/cat/lp_vocal_cat_home_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_home_01.wav` (licence still owner to verify) |
| M67 | `audio/sfx/voices/cat/lp_vocal_cat_home_lane_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_home_lane_01.wav` (licence still owner to verify) |
| M68 | `audio/sfx/voices/cat/lp_vocal_cat_idle_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_idle_01.wav` (licence still owner to verify) |
| M69 | `audio/sfx/voices/cat/lp_vocal_cat_nervous_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_nervous_01.wav` (licence still owner to verify) |
| M70 | `audio/sfx/voices/cat/lp_vocal_cat_proud_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_proud_01.wav` (licence still owner to verify) |
| M71 | `audio/sfx/voices/cat/lp_vocal_cat_safe_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_safe_01.wav` (licence still owner to verify) |
| M72 | `audio/sfx/voices/cat/lp_vocal_cat_six_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_six_01.wav` (licence still owner to verify) |
| M73 | `audio/sfx/voices/cat/lp_vocal_cat_third_six_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_third_six_01.wav` (licence still owner to verify) |
| M74 | `audio/sfx/voices/cat/lp_vocal_cat_victory_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_victory_01.wav` (licence still owner to verify) |
| M75 | `audio/sfx/voices/cat/lp_vocal_cat_yard_exit_01.wav` | character voice (cat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cat/lp_vocal_cat_yard_exit_01.wav` (licence still owner to verify) |
| M76 | `audio/sfx/voices/cock/lp_vocal_cock_capture_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_capture_01.wav` (licence still owner to verify) |
| M77 | `audio/sfx/voices/cock/lp_vocal_cock_captured_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_captured_01.wav` (licence still owner to verify) |
| M78 | `audio/sfx/voices/cock/lp_vocal_cock_defeat_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_defeat_01.wav` (licence still owner to verify) |
| M79 | `audio/sfx/voices/cock/lp_vocal_cock_frustrated_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_frustrated_01.wav` (licence still owner to verify) |
| M80 | `audio/sfx/voices/cock/lp_vocal_cock_home_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_home_01.wav` (licence still owner to verify) |
| M81 | `audio/sfx/voices/cock/lp_vocal_cock_home_lane_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_home_lane_01.wav` (licence still owner to verify) |
| M82 | `audio/sfx/voices/cock/lp_vocal_cock_idle_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_idle_01.wav` (licence still owner to verify) |
| M83 | `audio/sfx/voices/cock/lp_vocal_cock_nervous_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_nervous_01.wav` (licence still owner to verify) |
| M84 | `audio/sfx/voices/cock/lp_vocal_cock_proud_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_proud_01.wav` (licence still owner to verify) |
| M85 | `audio/sfx/voices/cock/lp_vocal_cock_safe_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_safe_01.wav` (licence still owner to verify) |
| M86 | `audio/sfx/voices/cock/lp_vocal_cock_six_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_six_01.wav` (licence still owner to verify) |
| M87 | `audio/sfx/voices/cock/lp_vocal_cock_third_six_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_third_six_01.wav` (licence still owner to verify) |
| M88 | `audio/sfx/voices/cock/lp_vocal_cock_victory_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_victory_01.wav` (licence still owner to verify) |
| M89 | `audio/sfx/voices/cock/lp_vocal_cock_yard_exit_01.wav` | character voice (cock) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/cock/lp_vocal_cock_yard_exit_01.wav` (licence still owner to verify) |
| M90 | `audio/sfx/voices/dog/.gitkeep` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M91 | `audio/sfx/voices/dog/lp_vocal_dog_capture_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_capture_01.wav` (licence still owner to verify) |
| M92 | `audio/sfx/voices/dog/lp_vocal_dog_captured_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_captured_01.wav` (licence still owner to verify) |
| M93 | `audio/sfx/voices/dog/lp_vocal_dog_defeat_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_defeat_01.wav` (licence still owner to verify) |
| M94 | `audio/sfx/voices/dog/lp_vocal_dog_frustrated_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_frustrated_01.wav` (licence still owner to verify) |
| M95 | `audio/sfx/voices/dog/lp_vocal_dog_home_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_home_01.wav` (licence still owner to verify) |
| M96 | `audio/sfx/voices/dog/lp_vocal_dog_home_lane_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_home_lane_01.wav` (licence still owner to verify) |
| M97 | `audio/sfx/voices/dog/lp_vocal_dog_idle_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_idle_01.wav` (licence still owner to verify) |
| M98 | `audio/sfx/voices/dog/lp_vocal_dog_nervous_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_nervous_01.wav` (licence still owner to verify) |
| M99 | `audio/sfx/voices/dog/lp_vocal_dog_proud_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_proud_01.wav` (licence still owner to verify) |
| M100 | `audio/sfx/voices/dog/lp_vocal_dog_safe_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_safe_01.wav` (licence still owner to verify) |
| M101 | `audio/sfx/voices/dog/lp_vocal_dog_six_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_six_01.wav` (licence still owner to verify) |
| M102 | `audio/sfx/voices/dog/lp_vocal_dog_third_six_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_third_six_01.wav` (licence still owner to verify) |
| M103 | `audio/sfx/voices/dog/lp_vocal_dog_victory_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_victory_01.wav` (licence still owner to verify) |
| M104 | `audio/sfx/voices/dog/lp_vocal_dog_yard_exit_01.wav` | character voice (dog) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/dog/lp_vocal_dog_yard_exit_01.wav` (licence still owner to verify) |
| M105 | `audio/sfx/voices/donkey/lp_vocal_donkey_capture_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_capture_01.wav` (licence still owner to verify) |
| M106 | `audio/sfx/voices/donkey/lp_vocal_donkey_captured_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_captured_01.wav` (licence still owner to verify) |
| M107 | `audio/sfx/voices/donkey/lp_vocal_donkey_defeat_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_defeat_01.wav` (licence still owner to verify) |
| M108 | `audio/sfx/voices/donkey/lp_vocal_donkey_frustrated_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_frustrated_01.wav` (licence still owner to verify) |
| M109 | `audio/sfx/voices/donkey/lp_vocal_donkey_home_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_home_01.wav` (licence still owner to verify) |
| M110 | `audio/sfx/voices/donkey/lp_vocal_donkey_home_lane_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_home_lane_01.wav` (licence still owner to verify) |
| M111 | `audio/sfx/voices/donkey/lp_vocal_donkey_idle_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_idle_01.wav` (licence still owner to verify) |
| M112 | `audio/sfx/voices/donkey/lp_vocal_donkey_nervous_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_nervous_01.wav` (licence still owner to verify) |
| M113 | `audio/sfx/voices/donkey/lp_vocal_donkey_proud_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_proud_01.wav` (licence still owner to verify) |
| M114 | `audio/sfx/voices/donkey/lp_vocal_donkey_safe_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_safe_01.wav` (licence still owner to verify) |
| M115 | `audio/sfx/voices/donkey/lp_vocal_donkey_six_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_six_01.wav` (licence still owner to verify) |
| M116 | `audio/sfx/voices/donkey/lp_vocal_donkey_third_six_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_third_six_01.wav` (licence still owner to verify) |
| M117 | `audio/sfx/voices/donkey/lp_vocal_donkey_victory_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_victory_01.wav` (licence still owner to verify) |
| M118 | `audio/sfx/voices/donkey/lp_vocal_donkey_yard_exit_01.wav` | character voice (donkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/donkey/lp_vocal_donkey_yard_exit_01.wav` (licence still owner to verify) |
| M119 | `audio/sfx/voices/duck/.gitkeep` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M120 | `audio/sfx/voices/duck/lp_vocal_duck_capture_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_capture_01.wav` (licence still owner to verify) |
| M121 | `audio/sfx/voices/duck/lp_vocal_duck_captured_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_captured_01.wav` (licence still owner to verify) |
| M122 | `audio/sfx/voices/duck/lp_vocal_duck_defeat_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_defeat_01.wav` (licence still owner to verify) |
| M123 | `audio/sfx/voices/duck/lp_vocal_duck_frustrated_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_frustrated_01.wav` (licence still owner to verify) |
| M124 | `audio/sfx/voices/duck/lp_vocal_duck_home_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_home_01.wav` (licence still owner to verify) |
| M125 | `audio/sfx/voices/duck/lp_vocal_duck_home_lane_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_home_lane_01.wav` (licence still owner to verify) |
| M126 | `audio/sfx/voices/duck/lp_vocal_duck_idle_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_idle_01.wav` (licence still owner to verify) |
| M127 | `audio/sfx/voices/duck/lp_vocal_duck_nervous_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_nervous_01.wav` (licence still owner to verify) |
| M128 | `audio/sfx/voices/duck/lp_vocal_duck_proud_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_proud_01.wav` (licence still owner to verify) |
| M129 | `audio/sfx/voices/duck/lp_vocal_duck_safe_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_safe_01.wav` (licence still owner to verify) |
| M130 | `audio/sfx/voices/duck/lp_vocal_duck_six_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_six_01.wav` (licence still owner to verify) |
| M131 | `audio/sfx/voices/duck/lp_vocal_duck_third_six_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_third_six_01.wav` (licence still owner to verify) |
| M132 | `audio/sfx/voices/duck/lp_vocal_duck_victory_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_victory_01.wav` (licence still owner to verify) |
| M133 | `audio/sfx/voices/duck/lp_vocal_duck_yard_exit_01.wav` | character voice (duck) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/duck/lp_vocal_duck_yard_exit_01.wav` (licence still owner to verify) |
| M134 | `audio/sfx/voices/elephant/lp_vocal_elephant_capture_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_capture_01.wav` (licence still owner to verify) |
| M135 | `audio/sfx/voices/elephant/lp_vocal_elephant_captured_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_captured_01.wav` (licence still owner to verify) |
| M136 | `audio/sfx/voices/elephant/lp_vocal_elephant_defeat_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_defeat_01.wav` (licence still owner to verify) |
| M137 | `audio/sfx/voices/elephant/lp_vocal_elephant_frustrated_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_frustrated_01.wav` (licence still owner to verify) |
| M138 | `audio/sfx/voices/elephant/lp_vocal_elephant_home_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_home_01.wav` (licence still owner to verify) |
| M139 | `audio/sfx/voices/elephant/lp_vocal_elephant_home_lane_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_home_lane_01.wav` (licence still owner to verify) |
| M140 | `audio/sfx/voices/elephant/lp_vocal_elephant_idle_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_idle_01.wav` (licence still owner to verify) |
| M141 | `audio/sfx/voices/elephant/lp_vocal_elephant_nervous_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_nervous_01.wav` (licence still owner to verify) |
| M142 | `audio/sfx/voices/elephant/lp_vocal_elephant_proud_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_proud_01.wav` (licence still owner to verify) |
| M143 | `audio/sfx/voices/elephant/lp_vocal_elephant_safe_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_safe_01.wav` (licence still owner to verify) |
| M144 | `audio/sfx/voices/elephant/lp_vocal_elephant_six_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_six_01.wav` (licence still owner to verify) |
| M145 | `audio/sfx/voices/elephant/lp_vocal_elephant_third_six_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_third_six_01.wav` (licence still owner to verify) |
| M146 | `audio/sfx/voices/elephant/lp_vocal_elephant_victory_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_victory_01.wav` (licence still owner to verify) |
| M147 | `audio/sfx/voices/elephant/lp_vocal_elephant_yard_exit_01.wav` | character voice (elephant) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/elephant/lp_vocal_elephant_yard_exit_01.wav` (licence still owner to verify) |
| M148 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_capture_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_capture_01.wav` (licence still owner to verify) |
| M149 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_captured_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_captured_01.wav` (licence still owner to verify) |
| M150 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_defeat_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_defeat_01.wav` (licence still owner to verify) |
| M151 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_frustrated_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_frustrated_01.wav` (licence still owner to verify) |
| M152 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_home_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_home_01.wav` (licence still owner to verify) |
| M153 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_home_lane_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_home_lane_01.wav` (licence still owner to verify) |
| M154 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_idle_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_idle_01.wav` (licence still owner to verify) |
| M155 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_nervous_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_nervous_01.wav` (licence still owner to verify) |
| M156 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_proud_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_proud_01.wav` (licence still owner to verify) |
| M157 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_safe_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_safe_01.wav` (licence still owner to verify) |
| M158 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_six_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_six_01.wav` (licence still owner to verify) |
| M159 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_third_six_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_third_six_01.wav` (licence still owner to verify) |
| M160 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_victory_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_victory_01.wav` (licence still owner to verify) |
| M161 | `audio/sfx/voices/giraffe/lp_vocal_giraffe_yard_exit_01.wav` | character voice (giraffe) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/giraffe/lp_vocal_giraffe_yard_exit_01.wav` (licence still owner to verify) |
| M162 | `audio/sfx/voices/goat/.gitkeep` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | todo |
| M163 | `audio/sfx/voices/goat/lp_vocal_goat_capture_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_capture_01.wav` (licence still owner to verify) |
| M164 | `audio/sfx/voices/goat/lp_vocal_goat_captured_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_captured_01.wav` (licence still owner to verify) |
| M165 | `audio/sfx/voices/goat/lp_vocal_goat_defeat_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_defeat_01.wav` (licence still owner to verify) |
| M166 | `audio/sfx/voices/goat/lp_vocal_goat_frustrated_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_frustrated_01.wav` (licence still owner to verify) |
| M167 | `audio/sfx/voices/goat/lp_vocal_goat_home_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_home_01.wav` (licence still owner to verify) |
| M168 | `audio/sfx/voices/goat/lp_vocal_goat_home_lane_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_home_lane_01.wav` (licence still owner to verify) |
| M169 | `audio/sfx/voices/goat/lp_vocal_goat_idle_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_idle_01.wav` (licence still owner to verify) |
| M170 | `audio/sfx/voices/goat/lp_vocal_goat_nervous_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_nervous_01.wav` (licence still owner to verify) |
| M171 | `audio/sfx/voices/goat/lp_vocal_goat_proud_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_proud_01.wav` (licence still owner to verify) |
| M172 | `audio/sfx/voices/goat/lp_vocal_goat_safe_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_safe_01.wav` (licence still owner to verify) |
| M173 | `audio/sfx/voices/goat/lp_vocal_goat_six_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_six_01.wav` (licence still owner to verify) |
| M174 | `audio/sfx/voices/goat/lp_vocal_goat_third_six_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_third_six_01.wav` (licence still owner to verify) |
| M175 | `audio/sfx/voices/goat/lp_vocal_goat_victory_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_victory_01.wav` (licence still owner to verify) |
| M176 | `audio/sfx/voices/goat/lp_vocal_goat_yard_exit_01.wav` | character voice (goat) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat/lp_vocal_goat_yard_exit_01.wav` (licence still owner to verify) |
| M177 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_capture_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_capture_01.wav` (licence still owner to verify) |
| M178 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_captured_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_captured_01.wav` (licence still owner to verify) |
| M179 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_defeat_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_defeat_01.wav` (licence still owner to verify) |
| M180 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_frustrated_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_frustrated_01.wav` (licence still owner to verify) |
| M181 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_home_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_home_01.wav` (licence still owner to verify) |
| M182 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_home_lane_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_home_lane_01.wav` (licence still owner to verify) |
| M183 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_idle_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_idle_01.wav` (licence still owner to verify) |
| M184 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_nervous_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_nervous_01.wav` (licence still owner to verify) |
| M185 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_proud_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_proud_01.wav` (licence still owner to verify) |
| M186 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_safe_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_safe_01.wav` (licence still owner to verify) |
| M187 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_six_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_six_01.wav` (licence still owner to verify) |
| M188 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_third_six_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_third_six_01.wav` (licence still owner to verify) |
| M189 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_victory_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_victory_01.wav` (licence still owner to verify) |
| M190 | `audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_yard_exit_01.wav` | character voice (goat_lumi) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_yard_exit_01.wav` (licence still owner to verify) |
| M191 | `audio/sfx/voices/horse/lp_vocal_horse_capture_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_capture_01.wav` (licence still owner to verify) |
| M192 | `audio/sfx/voices/horse/lp_vocal_horse_defeat_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_defeat_01.wav` (licence still owner to verify) |
| M193 | `audio/sfx/voices/horse/lp_vocal_horse_frustrated_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_frustrated_01.wav` (licence still owner to verify) |
| M194 | `audio/sfx/voices/horse/lp_vocal_horse_home_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_home_01.wav` (licence still owner to verify) |
| M195 | `audio/sfx/voices/horse/lp_vocal_horse_home_lane_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_home_lane_01.wav` (licence still owner to verify) |
| M196 | `audio/sfx/voices/horse/lp_vocal_horse_idle_01.wav.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_idle_01.wav` (renamed: live resolves the doubled extension at runtime; licence still owner to verify) |
| M197 | `audio/sfx/voices/horse/lp_vocal_horse_nervous_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_nervous_01.wav` (licence still owner to verify) |
| M198 | `audio/sfx/voices/horse/lp_vocal_horse_proud_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_proud_01.wav` (licence still owner to verify) |
| M199 | `audio/sfx/voices/horse/lp_vocal_horse_safe_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_safe_01.wav` (licence still owner to verify) |
| M200 | `audio/sfx/voices/horse/lp_vocal_horse_six_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_six_01.wav` (licence still owner to verify) |
| M201 | `audio/sfx/voices/horse/lp_vocal_horse_third_six_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_third_six_01.wav` (licence still owner to verify) |
| M202 | `audio/sfx/voices/horse/lp_vocal_horse_victory_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_victory_01.wav` (licence still owner to verify) |
| M203 | `audio/sfx/voices/horse/lp_vocal_horse_yard_exit_01.wav` | character voice (horse) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/horse/lp_vocal_horse_yard_exit_01.wav` (licence still owner to verify) |
| M204 | `audio/sfx/voices/monkey/lp_vocal_monkey_capture_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_capture_01.wav` (licence still owner to verify) |
| M205 | `audio/sfx/voices/monkey/lp_vocal_monkey_captured_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_captured_01.wav` (licence still owner to verify) |
| M206 | `audio/sfx/voices/monkey/lp_vocal_monkey_defeat_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_defeat_01.wav` (licence still owner to verify) |
| M207 | `audio/sfx/voices/monkey/lp_vocal_monkey_frustrated_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_frustrated_01.wav` (licence still owner to verify) |
| M208 | `audio/sfx/voices/monkey/lp_vocal_monkey_home_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_home_01.wav` (licence still owner to verify) |
| M209 | `audio/sfx/voices/monkey/lp_vocal_monkey_home_lane_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_home_lane_01.wav` (licence still owner to verify) |
| M210 | `audio/sfx/voices/monkey/lp_vocal_monkey_idle_01.wav.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_idle_01.wav` (renamed: live resolves the doubled extension at runtime; licence still owner to verify) |
| M211 | `audio/sfx/voices/monkey/lp_vocal_monkey_nervous_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_nervous_01.wav` (licence still owner to verify) |
| M212 | `audio/sfx/voices/monkey/lp_vocal_monkey_proud_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_proud_01.wav` (licence still owner to verify) |
| M213 | `audio/sfx/voices/monkey/lp_vocal_monkey_safe_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_safe_01.wav` (licence still owner to verify) |
| M214 | `audio/sfx/voices/monkey/lp_vocal_monkey_six_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_six_01.wav` (licence still owner to verify) |
| M215 | `audio/sfx/voices/monkey/lp_vocal_monkey_third_six_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_third_six_01.wav` (licence still owner to verify) |
| M216 | `audio/sfx/voices/monkey/lp_vocal_monkey_victory_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_victory_01.wav` (licence still owner to verify) |
| M217 | `audio/sfx/voices/monkey/lp_vocal_monkey_yard_exit_01.wav` | character voice (monkey) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/monkey/lp_vocal_monkey_yard_exit_01.wav` (licence still owner to verify) |
| M218 | `audio/sfx/voices/panda/lp_vocal_panda_capture_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_capture_01.wav` (licence still owner to verify) |
| M219 | `audio/sfx/voices/panda/lp_vocal_panda_captured_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_captured_01.wav` (licence still owner to verify) |
| M220 | `audio/sfx/voices/panda/lp_vocal_panda_defeat_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_defeat_01.wav` (licence still owner to verify) |
| M221 | `audio/sfx/voices/panda/lp_vocal_panda_frustrated_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_frustrated_01.wav` (licence still owner to verify) |
| M222 | `audio/sfx/voices/panda/lp_vocal_panda_home_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_home_01.wav` (licence still owner to verify) |
| M223 | `audio/sfx/voices/panda/lp_vocal_panda_home_lane_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_home_lane_01.wav` (licence still owner to verify) |
| M224 | `audio/sfx/voices/panda/lp_vocal_panda_idle_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_idle_01.wav` (licence still owner to verify) |
| M225 | `audio/sfx/voices/panda/lp_vocal_panda_nervous_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_nervous_01.wav` (licence still owner to verify) |
| M226 | `audio/sfx/voices/panda/lp_vocal_panda_proud_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_proud_01.wav` (licence still owner to verify) |
| M227 | `audio/sfx/voices/panda/lp_vocal_panda_safe_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_safe_01.wav` (licence still owner to verify) |
| M228 | `audio/sfx/voices/panda/lp_vocal_panda_six_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_six_01.wav` (licence still owner to verify) |
| M229 | `audio/sfx/voices/panda/lp_vocal_panda_third_six_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_third_six_01.wav` (licence still owner to verify) |
| M230 | `audio/sfx/voices/panda/lp_vocal_panda_victory_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_victory_01.wav` (licence still owner to verify) |
| M231 | `audio/sfx/voices/panda/lp_vocal_panda_yard_exit_01.wav` | character voice (panda) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/panda/lp_vocal_panda_yard_exit_01.wav` (licence still owner to verify) |
| M232 | `audio/sfx/voices/pig/lp_vocal_pig_capture_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_capture_01.wav` (licence still owner to verify) |
| M233 | `audio/sfx/voices/pig/lp_vocal_pig_captured_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_captured_01.wav` (licence still owner to verify) |
| M234 | `audio/sfx/voices/pig/lp_vocal_pig_defeat_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_defeat_01.wav` (licence still owner to verify) |
| M235 | `audio/sfx/voices/pig/lp_vocal_pig_frustrated_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_frustrated_01.wav` (licence still owner to verify) |
| M236 | `audio/sfx/voices/pig/lp_vocal_pig_home_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_home_01.wav` (licence still owner to verify) |
| M237 | `audio/sfx/voices/pig/lp_vocal_pig_home_lane_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_home_lane_01.wav` (licence still owner to verify) |
| M238 | `audio/sfx/voices/pig/lp_vocal_pig_idle_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_idle_01.wav` (licence still owner to verify) |
| M239 | `audio/sfx/voices/pig/lp_vocal_pig_nervous_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_nervous_01.wav` (licence still owner to verify) |
| M240 | `audio/sfx/voices/pig/lp_vocal_pig_proud_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_proud_01.wav` (licence still owner to verify) |
| M241 | `audio/sfx/voices/pig/lp_vocal_pig_safe_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_safe_01.wav` (licence still owner to verify) |
| M242 | `audio/sfx/voices/pig/lp_vocal_pig_six_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_six_01.wav` (licence still owner to verify) |
| M243 | `audio/sfx/voices/pig/lp_vocal_pig_third_six_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_third_six_01.wav` (licence still owner to verify) |
| M244 | `audio/sfx/voices/pig/lp_vocal_pig_victory_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_victory_01.wav` (licence still owner to verify) |
| M245 | `audio/sfx/voices/pig/lp_vocal_pig_yard_exit_01.wav` | character voice (pig) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/pig/lp_vocal_pig_yard_exit_01.wav` (licence still owner to verify) |
| M246 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_capture_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_capture_01.wav` (licence still owner to verify) |
| M247 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_captured_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_captured_01.wav` (licence still owner to verify) |
| M248 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_defeat_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_defeat_01.wav` (licence still owner to verify) |
| M249 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_frustrated_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_frustrated_01.wav` (licence still owner to verify) |
| M250 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_home_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_home_01.wav` (licence still owner to verify) |
| M251 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_home_lane_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_home_lane_01.wav` (licence still owner to verify) |
| M252 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_idle_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_idle_01.wav` (licence still owner to verify) |
| M253 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_nervous_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_nervous_01.wav` (licence still owner to verify) |
| M254 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_proud_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_proud_01.wav` (licence still owner to verify) |
| M255 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_safe_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_safe_01.wav` (licence still owner to verify) |
| M256 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_six_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_six_01.wav` (licence still owner to verify) |
| M257 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_third_six_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_third_six_01.wav` (licence still owner to verify) |
| M258 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_victory_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_victory_01.wav` (licence still owner to verify) |
| M259 | `audio/sfx/voices/rabbit/lp_vocal_rabbit_yard_exit_01.wav` | character voice (rabbit) | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/audio/sfx/voices/rabbit/lp_vocal_rabbit_yard_exit_01.wav` (licence still owner to verify) |
| M260 | `android/app/src/main/res/drawable-nodpi/dice_forest_dew_3d.png` | forest_dew dice artwork | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/dice/dice_forest_dew_3d.png` at the live decode size (inSampleSize 4) |
| M261 | `android/app/src/main/res/drawable-nodpi/dice_lagoon_pearl_3d.png` | lagoon_pearl dice artwork | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/dice/dice_lagoon_pearl_3d.png` at the live decode size (inSampleSize 4) |
| M262 | `android/app/src/main/res/drawable-nodpi/dice_frost_crystal_3d.png` | frost_crystal dice artwork | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/dice/dice_frost_crystal_3d.png` at the live decode size (inSampleSize 4) |
| M263 | `android/app/src/main/res/drawable-nodpi/dice_starlight_3d.png` | starlight dice artwork | live repo (owner-authored or third-party: unknown) | owner to verify | copied to `assets/dice/dice_starlight_3d.png` at the live decode size (inSampleSize 4) |
