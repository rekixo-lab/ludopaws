#!/usr/bin/env python3
"""Static-only structural checks for the Ludo Paws v2 Godot project.

Does NOT prove Godot parsing, rules, gameplay, Android export or performance;
run the Godot tests listed in README.md for those.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    "README.md",
    "AGENTS.md",
    "project.godot",
    "scenes/main.tscn",
    "scripts/main.gd",
    "docs/PROTOTYPE_SPEC.md",
    "docs/CODEX_TASK.md",
    "docs/TEST_PLAN.md",
    "docs/TECHNICAL_BOUNDARIES.md",
    "scripts/game/rules/ludo_engine.gd",
    "scripts/game/rules/ludo_rulesets.gd",
    "scripts/game/rules/ludo_bot.gd",
    "scripts/game/local_match.gd",
    "scripts/game/local_match_page.gd",
    "scripts/game/board3d/classic_board_3d.gd",
    "scripts/game/board3d/paw_parts.gdshader",
    "scenes/previews/board_preview.tscn",
    "scenes/previews/robot_match_preview.tscn",
    "tests/run_tests.gd",
    "tests/match_flow_smoke.gd",
    "tests/fixtures/server_rules_golden.json",
    "tests/fixtures/robot_policy_golden.json",
]


def main() -> None:
    missing = [path for path in REQUIRED if not (ROOT / path).is_file()]
    if missing:
        raise SystemExit(f"Missing required files: {', '.join(missing)}")

    project = (ROOT / "project.godot").read_text(encoding="utf-8")
    scene = (ROOT / "scenes/main.tscn").read_text(encoding="utf-8")
    script = (ROOT / "scripts/main.gd").read_text(encoding="utf-8")
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    agents = (ROOT / "AGENTS.md").read_text(encoding="utf-8")

    assert "res://scenes/ui/home/home_screen.tscn" in project
    assert "gl_compatibility" in project
    assert "res://scripts/main.gd" in scene
    assert "extends Node3D" in script
    assert "GRID_SIZE: int = 15" in script
    assert "_build_cat" in script and "_build_panda" in script
    page = (ROOT / "scripts/game/local_match_page.gd").read_text(encoding="utf-8")
    assert "res://scripts/game/board3d/classic_board_3d.gd" in page, "match page must use the 3D board"
    for preview in ("board_preview", "robot_match_preview"):
        text = (ROOT / f"scenes/previews/{preview}.tscn").read_text(encoding="utf-8")
        assert "res://scripts/previews/" in text, f"{preview} scene has no preview script"
    assert "Not yet a playable" not in readme, "README still describes the old scaffold"
    assert "scenes/previews/board_preview.tscn" in readme
    assert "config/release.local.cfg" in agents, "AGENTS.md must keep the no-secrets rule"
    assert "config/*.local.cfg" in (ROOT / ".gitignore").read_text(encoding="utf-8")

    print("PASS: static structure only (run the Godot tests in README.md for runtime checks).")


if __name__ == "__main__":
    main()
