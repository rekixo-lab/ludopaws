# Match Player Rail + per-player Dice — live behaviour spec

Source: the unchanged live app (`~/Workspace/ludopaws`, read-only), verified 2026-11-10 from
`OfflinePlayerRailUi.kt`, `OfflineGameplayScreenUi.kt`, `OfflineGameplayControlsUi.kt`,
`OfflinePassAndPlayHandoffUi.kt` / `…Policy.kt`, `OfflinePlayerLayout.kt`,
`OnlineLudoPawsPresentation.kt` (addPlayerSlot), `OnlineBoardRailAlignmentPolicy.kt`,
`LudoPawsPlayerCardView.kt`, `DiceView.kt`, `DiceRollAnimationPolicy.kt`,
`DiceTurnCountdownPolicy.kt`, `DiceStylePalettePolicy.kt` and
`docs/GAMEPLAY_PRESENTATION_BEHAVIOR_SPEC.md` §3, §4.1–4.6. `TurnBoardReveal.kt` does not exist
in the live repo and `docs/live_reference/` has no screenshots, so neither was used.
Where the presentation spec (a target document) and the shipped code differ, **the code wins**.

## 1. Screen order (top → bottom)

1. top actions (back / settings),
2. **top player rail** (min height 78 dp; 70 dp on compact width),
3. the square board (fixed size; rails never shrink it),
4. **bottom player rail**,
5. the action panel: turn line (`YOUR TURN`, `<Name> • TURN`, `… • THINKING`, `MATCH COMPLETE`),
   info line (`DICE n • k CHOICES • h/4 HOME`), status / coach line.

Each rail is two equal-width halves (left slot, right slot). Online rails are additionally
translated so each rail sits a fixed gap above / below the board (`OnlineBoardRailAlignmentPolicy`:
`top = boardTop − gap − anchorTop − anchorHeight`, `bottom = boardTop + boardHeight + gap − anchorTop`).

## 2. Which card sits where

Slots: `TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT` = slot index 0, 1, 2, 3.
Colour index: RED 0, GREEN 1, YELLOW 2, BLUE 3.

- **Offline (Robot, Pass & Play, every player count):** `slot = (colourIndex + rotation) % 4` with
  `rotation = (3 − colourIndex(firstPlayer.color)) % 4` — the same quarter-turns the board is rotated by.
  So the **first player (the local human) is always BOTTOM_LEFT**, and every card sits in the
  screen corner of its own yard after board rotation (cards line up with their yard/colour).
- **Online / Friends, 3–4 players:** the same yard-corner rule, rotated by the local player's colour.
- **Online / Friends, 2 players:** the local player is BOTTOM_LEFT and the opponent **TOP_RIGHT**
  (diagonal, regardless of colour).
- **2 players** therefore use one card per rail (diagonal); 3 players leave one corner empty;
  4 players fill all corners.

## 3. Card contents (`LudoPawsPlayerCardView`)

- Width 116 dp (104 compact) offline, 112 dp online; rounded 15 dp; gradient ink → deep ink
  (lighter when active); **stroke in the player's colour**, 3 dp when active, 2 dp otherwise
  (the active state never replaces the team colour); elevation 7 dp active / 2 dp idle.
- 50 dp equipped-animal portrait with a ring in the player's colour (highlighted when active).
  Cards on the right half mirror (portrait on the outer edge, `portraitOnEnd`).
- Name (`"<name> • BOT"` for bots; offline CPUs named `Robot n`).
- Team line only in Team Up: `A · YOU`, `A · PARTNER`, else `TEAM B` (A gold, B aqua).
- State line: `<Animal> • <turn label>` — `OUT` (forfeited), `BOT TURN`, `CPU TURN`, `YOUR TURN`
  (active local), `TURN` (active remote), `WAITING`; gold when active, aqua otherwise.
- Progress line: `ALL PAWS HOME`, `h/4 HOME • r MOVING`, or `h/4 HOME • READY`.

## 4. Dice placement and visibility

- Each slot has its own dice control (58 dp; 54 dp compact), beside the card **on the inner side**:
  left slots `card | dice`, right slots `dice | card` (dice toward the board centre).
- **Only the active player's dice is visible**; all other players' dice slots are `GONE`.
- The dice carries the player's colour as its border (`DiceStylePalettePolicy`: equipped skin
  palette with `borderColor = team colour`). Online uses `matchStyle.diceId`.
- Active player = the current turn seat, **except while a committed move is still presenting**
  (move lock): then the previous roller stays active and keeps showing the resolved face,
  non-interactive, until the presentation lock releases; only then does the next player's dice
  appear.
- Tap: the dice itself is the button. Enabled only for a human (not CPU / not remote) active
  player and only when not presentation-locked; online additionally needs the online roll
  action to be allowed. Disabled dice stay fully opaque (readable while disabled).
- Robot turn: the robot's dice appears in its slot, shows the roll (rolling → settle) and is not
  tappable; content description "… is thinking".
- Online opponent: its dice appears in its slot showing the server-verified outcome, not tappable.

## 5. Dice animation (`DiceView`, `DiceRollAnimationPolicy`)

- Press (tap) → `startRolling`; rolling frames: faces cycle every 52 ms, press scale
  `1 − (1 − min(t/75,1))·0.10`, hop/tumble, border pulse; duplicate taps are ignored while
  rolling (`if (rolling) return`, control disabled by the flow).
- Result → `beginSettle`: 450 ms settle with bounce and wobble from the last tumble angle; the
  face is the authoritative outcome decided before the animation.
- Attention (actionable dice): 900 ms breathing pulse (+7 % scale).
- Spatial dice skins render their own 3D roll.
- Reduced Motion (§10 of the spec): no tumble/translation — face transition and the result only.
- Six: short excited emphasis on the active card (reaction EXCITED); third six: penalty reaction.
- **Countdown:** only online, with a trusted server timer contract (15 s): a team-coloured ring
  traced along the dice outline (perimeter skins) or a bar under the dice (environment / spatial
  skins); track alpha 62, progress 255. Offline has no turn timer.

## 6. Pass & Play handoff

`OfflinePassAndPlayHandoffPolicy`: only in Pass & Play, only for a fresh ACTIVE→ACTIVE change of
turn seat with no pending roll (never on first render, resume, extra turns, CPU mode or a finished
match). Shows a modal: `PASS THE PHONE`, the next player's name, `<Colour> paws • your turn`,
and `READY` (not cancellable).

## 7. Team Up

Same rail; cards show the team line (`A · YOU` / `A · PARTNER` / `TEAM B`). Only the active
player's dice is visible; a partner's or opponent's dice is non-interactive.

## 8. V2 implementation notes (look only)

V2 keeps live layout and behaviour; frames, fonts, colours and buttons come from the Meadow kit.
Deliberate V2 differences are listed in README → "Match rail".

Implemented in `scripts/game/match/rail_layout.gd` (slots, active seat), `pass_and_play_handoff.gd`
(§6 policy), `scripts/ui/match/player_rail.gd` + `player_card.gd` (component) and
`scripts/game/local_match_page.gd` (wiring). Tests: `tests/run_tests.gd` `_match_rail` (corners for
every rotation and 2/3/4 players, online slots, active-seat lock, visibility/inner side, input
gating, double tap, outcome fidelity, handoff policy) and `tests/match_flow_smoke.gd` (rails never
cover the board; Pass & Play handoff shown and dismissed).
