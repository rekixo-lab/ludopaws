# Ludo Paws v2 — Godot Gameplay Prototype Specification

## Intent

A **visual + usability comparison**, not a production port. The initial app should feel like Ludo Paws, but avoid moving the live infrastructure. Prioritize a **playable local 2-player match**, attractive moving 3D animals and responsive input.

## Players, characters, and board

- **Cat = Red**, **Panda = Yellow**; local pass-and-play, two humans, one device, no AI/online in milestone 1.
- **15×15 Ludo board**. Keep canonical cross, color-coded yards, connected outer circuit, colored home lanes, center finish and safe cells. Render clearly at phone size.
- **Four animal pawns per player**, individually selectable. Animal appearance should remain recognizable at small board scale; use clear colored bases/halos and a selected-pawn marker. Avoid visual duplicates under a 3D pawn.
- Place player yards diagonally opposite for the two-player test; adjust the final path/start cells to the existing game's canonical board mapping after verification.
- Separate the board cell/path model from artwork, camera and hit testing. All 3D pawns must match the actual path cells.

## Rule behavior — initial local parity target

- Each token starts at **yard = -1**. Rolling **6** permits entry to its owner's start square, with path progress **0**.
- A token then advances by its rolled value along its color-relative path. Finish must be **exact**; target relative progress is **57** (the existing app's home sentinel). A move that overshoots is illegal.
- Roll 6 = an extra turn. Capture on a non-safe cell = an extra turn. Third consecutive 6 forfeits the roll/turn (verify exact UI timing against source).
- Tokens cannot be captured in safe cells. Captured pawns return to their yard. When a roll offers several legal pawns, require the player to select one.
- If no move is legal, visibly pass the turn. When all four pawns of a player reach home, display win state and offer Restart.
- No blockade/stack restrictions in the first ruleset unless source verification proves otherwise.
- Build **pure rules tests** for entry, path conversion, safe cells, capture, turn transfer, three 6s, exact home and game over.
- The original product intentionally contains a first-roll-six behavior in some flows. **Do not silently copy this bias**: make it an explicitly labeled optional test mode only after verifying scope/conditions. Default basic comparison to ordinary local random rolls, and never label the local prototype cryptographically certified. Do not redesign the original dice system in this experiment.

## Player experience

1. Open directly into a refined local board and show two animal portraits/names.
2. A clear turn indicator shows who must act.
3. Press an animated die to roll (one roll per turn; block double taps while pending).
4. Legal pawns receive a subtle, high-contrast pulse; nonlegal pawns remain quiet.
5. Tap a pawn; it follows the exact path using short 3D hops, synchronized audio/haptic feedback where available.
6. Capture: brief impact/sparkle then captured pawn moves home. Landing on a safe square gets a distinct gentle reaction.
7. Home: short celebration. Win: 3D animal reaction, readable winner UI, Replay button.
8. Reduced motion keeps outcomes and board accessibility intact; rotate/reconnect/reopen must not corrupt state.

## Art direction

- Premium, bright but not visually cluttered: polished tabletop, soft shadows, light specular highlights, tasteful particles, readable paths and strong team colors.
- Cat and Panda should be *recognizable 3D animals*, not abstract circles alone. Procedural meshes are acceptable at first; improve ears/eyes/body/legs and distinct idle/hop/victory silhouettes.
- Use a **single coordinated animation clock** where possible. Avoid detached paws, floating faces, unexpected snaps or excessive bloom. Support low-end Android devices.
- Include optional camera angle/profile controls for comparison. Default angle must not hide legal moves or the 15×15 geometry.

## Responsive layout

- Start with **portrait smartphone** and support landscape/tablet/desktop.
- Keep main board fully visible with clear active turn, die and pawns. Do not let HUD cover board touch targets.
- Mouse click and mobile tap must both work. Clear disabled/working feedback; scalable UI.

## Architecture target

- res://scenes/gameplay/main.tscn — game root.
- res://scenes/board/board.tscn — board visuals & hit testing.
- res://scenes/characters/cat.tscn and panda.tscn — reusable 3D animated models.
- res://scripts/game/rules.gd — deterministic rules without UI.
- res://scripts/game/board_path.gd — canonical path geometry.
- res://scripts/game/match_controller.gd — turns and event flow.
- res://tests/ — locally executable deterministic tests (Godot headless).
- This proposed structure is a target; Codex may choose equivalent clear names.

## Out of scope

Cloudflare and Durable Objects, sign-in, purchases, rewards, ads, matchmaking, online dice/proof systems, production migration, Play Store signing and release, all 14 animals, all board skins. **Only Cat + Panda and local pass-and-play.**
