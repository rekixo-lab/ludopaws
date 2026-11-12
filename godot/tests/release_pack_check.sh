#!/usr/bin/env bash
# Release export hygiene: the release preset's pack has no debug / preview / test files, and the
# packed config has only public values (real ad units, no test units, no secret-like keys).
#   tests/release_pack_check.sh
set -uo pipefail
GODOT="${GODOT:-flatpak run org.godotengine.Godot}"
OUT=build/release_check.pck
mkdir -p build
$GODOT --headless --path . --export-pack "Ludo Paws Release (com.rekixo.ludopaws)" "$OUT" > /dev/null 2>&1
[ -s "$OUT" ] || { echo "FAIL: release pack was not exported"; exit 1; }
fail=0
LIST=build/release_check.strings
strings "$OUT" > "$LIST"
for banned in "scripts/debug/" "scripts/previews/" "scenes/previews/" "res://tests/" "tools/android"; do
  if grep -q "$banned" "$LIST"; then echo "FAIL: release pack contains $banned"; fail=1; fi
done
grep -q "scripts/game/perf.gd" "$LIST" || { echo "FAIL: release pack misses the perf facade"; fail=1; }
CFG=config/release.local.cfg
if [ -f "$CFG" ]; then
  if grep -qiE "password|secret|keystore|private_key|token=" "$CFG"; then echo "FAIL: config has secret-like keys"; fail=1; fi
  if grep -E "unit_id|app_id" "$CFG" | grep -q "3940256099942544"; then echo "FAIL: config uses Google TEST ad ids"; fail=1; fi
fi
rm -f "$OUT" "$LIST"
[ $fail -eq 0 ] && echo "Release pack check: ok"
exit $fail
