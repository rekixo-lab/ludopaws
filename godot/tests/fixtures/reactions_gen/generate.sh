#!/usr/bin/env bash
# Regenerates tests/fixtures/reactions_golden.json from the UNCHANGED live reaction pipeline
# (read-only) and the snapshot sequence rebuilt by tests/reaction_inputs.gd.
# Usage: tests/fixtures/reactions_gen/generate.sh [live-repo]
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
live="${1:-$HOME/Workspace/ludopaws}"
K="$live/android/app/src/main/java/com/rekixo/ludopaws"
L="$(echo ~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/lib)"
JSON_JAR="$(ls ~/.gradle/caches/modules-2/files-2.1/org.json/json/*/*/json-*.jar | grep -v sources | head -1)"
CP="$L/kotlin-compiler-embeddable-2.0.20.jar:$L/kotlin-stdlib-2.0.20.jar:$L/kotlin-script-runtime-2.0.20.jar:$L/kotlin-reflect-2.0.20.jar:$L/kotlin-daemon-embeddable-2.0.20.jar:$L/trove4j-1.0.20200330.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar:$L/annotations-24.0.1.jar"
out="${TMPDIR:-/tmp}/ludopaws-reactions-gen"
rm -rf "$out"; mkdir -p "$out"
(cd "$root" && flatpak run org.godotengine.Godot --headless --path . --script res://tests/fixtures/reactions_gen/gen_inputs.gd >/dev/null 2>&1)
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn \
  -cp "$L/kotlin-stdlib-2.0.20.jar:$JSON_JAR" -d "$out" \
  "$here/Main.kt" "$K"/game/domain/model/*.kt "$K/feature/characters/domain/model/LudoPawsCharacterModels.kt" \
  "$K"/feature/characters/domain/reaction/*.kt
java -cp "$out:$L/kotlin-stdlib-2.0.20.jar:$JSON_JAR" MainKt "$here/inputs.json" > "$here/../reactions_golden.json"
echo "wrote tests/fixtures/reactions_golden.json"
