# Codex Task — Build the first playable Godot comparison

**Repository:** https://github.com/rekixo-lab/ludopaws-v2
**Source reference (read-only, if authorized):** https://github.com/rekixo-lab/ludopaws
**Current status:** visual scaffold only; project.godot and main scene exist. Complete the playable milestone, not merely a mockup.

## Instructions

You are implementing an **independent Godot 4 / GDScript prototype** so we can decide whether Ludo Paws is worth migrating from its existing Native Kotlin/OpenGL code. Read AGENTS.md and every document under docs/ before writing.

1. Inspect and open the existing Godot project. Keep the Cat/Panda + board visual scaffold working while refactoring it into reusable scenes/modules.
2. Implement an accurate 15×15 Ludo board, two opposite seats: **Cat/Red** and **Panda/Yellow**. Four pawns each. Match the original board/path geometry as closely as read-only source access permits. Document any deviation.
3. Implement full local two-human Ludo turns and legal moves as a **deterministic, pure testable rules module**: six-to-exit, extra turn, three consecutive sixes, capture/safe squares, exact finish, invalid moves, winner/restart. Outcome calculation must NEVER be controlled by animation.
4. Make both characters attractive, recognizable **3D animals** with idle, hop, roll reaction, capture and win animations. Existing Kotlin procedural OpenGL renderers cannot simply be pasted into Godot; rebuild a Godot-friendly model/scene. Bring live assets in only as the owner approves (licences checked by the owner). Licensed-safe procedurally generated geometry is allowed.
5. Premium but efficient visuals: tabletop and board shading, soft lighting, shadows, animation-timed particles, readable die faces, distinct legal pawn highlight, sound/haptics if locally available. Add a low-effects setting and reduce-motion support.
6. Complete touch-friendly portrait UI; also validate landscape and desktop. Prevent multi-tap races and obstructed board cells. No online backend/API connection.
7. Make automated Godot headless rule tests and a runnable smoke scene/test. Run static validator, Godot import/parse, headless tests and an interactive match. Recheck after each module, then run all checks again.
8. If an Android toolchain exists, install matching export templates and produce a **debug-only** APK under the unique app id **com.rekixo.ludopaws.v2prototype**; never publish or use the real signing identity. If tooling is unavailable, report the exact blocker and steps.
9. Provide screenshots/video or precise observations, measured device FPS/frame time and memory when possible, and a short comparison of what Godot improves versus the old Kotlin/OpenGL implementation.
10. Commit code and docs to this repo (prefer a focused PR). Do not modify the old repo or Cloudflare. Clearly distinguish tested features from untested plans.

## Final delivery format

- **What now works:** actual playable controls and two animal animations.
- **What changed:** files and concise architecture map.
- **Tests:** explicit commands, PASS/FAIL and device tested.
- **Risks/gaps:** exact known issues.
- **How to run:** desktop, Godot, and Android if exported.
- **Comparison:** observed advantages/disadvantages only; do not invent benchmarks.

Acceptance criteria: docs/TEST_PLAN.md P0. Do not stop at a README update or a screenshot-only mockup; complete a genuinely playable match.
