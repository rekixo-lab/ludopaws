#!/usr/bin/env bash
# Runs every headless check and fails on a non-zero exit OR any Godot script error in the log
# (a GDScript error only aborts the current function, so exit codes alone can miss it).
# Usage: tests/run_all.sh [godot-command]   (default: flatpak Godot, else `godot`)
set -uo pipefail
cd "$(dirname "$0")/.."
if [ $# -gt 0 ]; then GODOT=("$@")
elif command -v flatpak >/dev/null && flatpak info org.godotengine.Godot >/dev/null 2>&1; then GODOT=(flatpak run org.godotengine.Godot)
else GODOT=(godot); fi
status=0
run() {
  local name="$1"; shift
  local log
  log="$("${GODOT[@]}" "$@" 2>&1)"; local code=$?
  if [ $code -ne 0 ] || grep -Eq 'SCRIPT ERROR|Parse Error|Failed to load script|ERROR:.*parser' <<<"$log"; then
    echo "FAIL: $name (exit $code)"; grep -E 'SCRIPT ERROR|Parse Error|Failed to load|FAIL|at: ' <<<"$log" | head -20; status=1
  else
    echo "ok: $name — $(grep -E 'checks|failures' <<<"$log" | tail -1)"
  fi
}
run "import" --headless --editor --path . --quit
run "rules + parity" --headless --path . --script res://tests/run_tests.gd
run "UI match flow" --headless --path . --script res://tests/match_flow_smoke.gd
run "scroll layout" --headless --path . --script res://tests/scroll_layout_smoke.gd
run "tap hints" --headless --path . --script res://tests/tap_hint_smoke.gd
run "setup" --headless --path . --script res://tests/setup_smoke.gd
run "real data" --headless --path . --script res://tests/real_data_smoke.gd
run "launch screen" --headless --path . --script res://tests/launch_smoke.gd
run "pass & play banner" --headless --path . --script res://tests/pass_play_banner_smoke.gd
run "compile all scripts" --headless --path . --script res://tests/compile_all.gd
run "online contract" --headless --path . --script res://tests/online_contract_smoke.gd
run "account contract" --headless --path . --script res://tests/account_contract_smoke.gd
run "rewards ui" --headless --path . --script res://tests/rewards_ui_smoke.gd
run "growth + ads rules" --headless --path . --script res://tests/growth_smoke.gd
run "120-frame boot" --headless --path . --quit-after 120
GODOT="${GODOT[*]}" tests/release_pack_check.sh || status=1
python3 scripts/validate_project.py || status=1
python3 tests/inventory_coverage.py || status=1
exit $status
