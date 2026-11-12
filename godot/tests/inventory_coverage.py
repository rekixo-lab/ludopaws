#!/usr/bin/env python3
"""Checks that docs/PARITY_INVENTORY.md names every live source file.

Usage: python3 tests/inventory_coverage.py [path-to-live-repo]
Skips (exit 0) when the private live repo is not available on this machine.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIVE = Path(sys.argv[1] if len(sys.argv) > 1 else Path.home() / "Workspace/ludopaws")


def main() -> None:
    if not LIVE.is_dir():
        print(f"SKIP: live repo not found at {LIVE}")
        return
    text = (ROOT / "docs/PARITY_INVENTORY.md").read_text(encoding="utf-8")
    words = set(re.findall(r"[A-Za-z0-9_.-]+", text))
    kotlin = sorted((LIVE / "android/app/src/main/java/com/rekixo/ludopaws").rglob("*.kt"))
    server = sorted((LIVE / "server/src").glob("*.js"))
    missing = [p.stem for p in kotlin if p.stem not in words]
    missing += [p.name for p in server if p.name not in words]
    print(f"live files: {len(kotlin)} kotlin + {len(server)} server")
    if missing:
        raise SystemExit("Not in inventory: " + ", ".join(missing))
    print("PASS: every live source file is named in docs/PARITY_INVENTORY.md")


if __name__ == "__main__":
    main()
