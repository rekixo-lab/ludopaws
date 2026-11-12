# Robot / Pass & Play setup — live behaviour (verified from code)

Read 2026-11-11 from the unchanged live repo: `feature/offline/presentation/setup/`
(OfflineSetupScreenUi, OfflineSetupSelectionUi, OfflineCharacterSelectionUi),
`feature/offline/data/local/OfflineCharacterSetupStore.kt`,
`feature/offline/domain/engine/OfflinePlayerLayout.kt`, `RobotTeamComposition.kt`,
`feature/characters/domain/selection/StarterPawsAssignmentPolicy.kt` and the tests
`OfflinePlayerLayoutTest`, `StarterPawsAssignmentPolicyTest`,
`scripts/check-ludo-paws-selection-ux.mjs`.

## Sections and order
Both modes show the same three flat sections, then PLAY:
1. **CHOOSE YOUR PAWS** (Robot) / **CHOOSE YOUR PAW** (Pass & Play): a row of seat tabs
   (Robot: `YOU`, `H2…` for extra human seats, `CPU 1/2/3`; Pass & Play: `P1…P4`), each tab
   showing `● seat-colour dot`, slot name and the seat's Paw name; then a horizontal row of all
   **14 starter-pack Paws** (every character belongs to `starter_paws`) as 3D portrait cards
   with name and a subtitle (personality, or the unlock label when locked) and the seat's
   team badge on the portrait.
2. **SELECT PLAYERS**: 2 / 3 / 4 tiles.
3. **CHOOSE YOUR COLOR**: 4 colour tiles (no dialog), status line `YOUR COLOR: X`
   (Robot) or `PLAYER n COLOR: X` (Pass & Play).
Robot's title sits in the fixed top bar; selections scroll below it.

## Robot mode
- **Human / robot mix exists**: `RobotTeamComposition` — 1 … count−1 humans (always ≥ 1 CPU).
  Selecting a new player count starts at 1 human; **re-tapping the selected tile cycles**
  the mix. Tile label `n HUMAN(S) / m ROBOT(S)` with `↻` when the selected tile can cycle.
- Paws: every seat (including CPUs) can be chosen by the user; only **unlocked** Paws; all
  unique; the human (seat 0) keeps their preference first. A CPU seat can't take a human's
  Paw (toast "This paw belongs to a human player. Choose another robot paw."). Picking a Paw
  owned by another seat swaps the two. Choosing seat 0's Paw also updates the selected
  character (CharacterSelectionStore).
- Colour: the picker always controls the human (seat 0); CPU colours are
  `OfflinePlayerLayout.colorsFor(count, yourColour)`: 2 players = opposite corner, 3–4 =
  walk round the board from your colour. Default colour **BLUE**.
- **Difficulty: not in live's offline setup** (no difficulty anywhere in `feature/offline`).

## Pass & Play
- All seats human; P1–P4 tabs select the seat the Paw / colour pickers apply to.
- Paws unique by default; picking another seat's Paw swaps the two, nothing else changes.
- `selectColorForSeat`: a free colour changes only this seat; a taken colour swaps
  **exactly two seats**, others untouched; picking the current colour is a no-op; **no
  dialog**. `normalizeColorsForSeats` never rewrites a valid saved colour.
- Player names: not editable in live's setup (seats are P1–P4).

## Locked / paid Paws
Shown in the row like the others with a `🔒 …` subtitle from SmartUnlockPolicy
(`🔒 300 Gems · Unlock`, `🔒 BUY`, `🔒 WATCH AD`, `🔒 UNLOCK`, `🔒 GET GEMS`, `🔒 LOCKED`).
Tapping one never assigns it: it opens the **Store on the Paws tab, featuring that Paw and
starting its smart unlock**. No in-place preview popup.

## Persistence and rematch
`OfflineCharacterSetupStore` (prefs `ludo_paws_offline_character_setup_v1`, schema-versioned):
per mode the player count, selected slot, Paw ids, colours by seat and human count; saved on
every selection (not only on PLAY); loading repairs invalid / legacy / locked ids through
`StarterPawsAssignmentPolicy.normalize` and colours through `colorsFor` /
`normalizeColorsForSeats`. PLAY also saves the active assignment; rematch reuses it.

## Team badge
`LudoPawsCharacterAvatarView(showTeamBadge = true)`: the portrait carries a badge in the
seat's colour (`LudoPawsTeamIdentityPolicy`).

## V2 (this branch) vs live
Brought from live: three-part selection (Paws per seat, players, colour), 14 Paws with
unlock labels, unique Paws with automatic swap, CPU-can't-take-human rule, human/robot mix
with re-tap cycling, `colorsFor` / `selectColorForSeat` / `normalizeColorsForSeats`, saved
setup per mode with repair, BLUE default, team badges, Paw choice drives the match.

Deliberately different (V2 / owner's brief):
- Compact one-screen layout: players row (+ mix), 2-per-row seat chips, horizontal Paw
  carousel, colour dots, Start Game right below them.
- No difficulty selector: live has none, and V2's Normal and Hard played identically, so it
  was removed (2026-11-11, owner). Robots always use the live policy; only the Demo keeps its
  gentle robot.
- **Locked Paw preview popup** (rotating 3D animal, price, "Unlock in Shop" → Shop on that
  Paw with its unlock chooser, Close) instead of jumping straight to the Store.
- Pass & Play **names** editable via a small pencil (V2 feature; live has P1–P4 only).
- The CPU-taking-a-human-Paw message is a short notice instead of an Android toast.
