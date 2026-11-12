#!/usr/bin/env bash
# Regenerates tests/fixtures/fx_golden.json: every draw call of the UNCHANGED live
# LudoPawsFxPainter (read-only), recorded through the JVM canvas stub in this folder.
# Usage: tests/fixtures/fx_gen/generate.sh [live-repo]
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
live="${1:-$HOME/Workspace/ludopaws}"
K="$live/android/app/src/main/java/com/rekixo/ludopaws"
L="$(echo ~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/lib)"
JSON_JAR="$(ls ~/.gradle/caches/modules-2/files-2.1/org.json/json/*/*/json-*.jar | grep -v sources | head -1)"
CP="$L/kotlin-compiler-embeddable-2.0.20.jar:$L/kotlin-stdlib-2.0.20.jar:$L/kotlin-script-runtime-2.0.20.jar:$L/kotlin-reflect-2.0.20.jar:$L/kotlin-daemon-embeddable-2.0.20.jar:$L/trove4j-1.0.20200330.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar:$L/annotations-24.0.1.jar"
out="${TMPDIR:-/tmp}/ludopaws-fx-gen"
rm -rf "$out"; mkdir -p "$out"
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn \
  -cp "$L/kotlin-stdlib-2.0.20.jar:$JSON_JAR" -d "$out" \
  "$here"/*.kt "$K"/game/domain/model/*.kt "$K/feature/characters/domain/model/LudoPawsCharacterModels.kt" \
  "$K/game/ui/components/LudoPawsFxPainter.kt" "$K/game/ui/components/LudoPawsFxBoardGeometry.kt"
java -cp "$out:$L/kotlin-stdlib-2.0.20.jar:$JSON_JAR" com.rekixo.ludopaws.MainKt > "$here/../fx_golden.json"
echo "wrote tests/fixtures/fx_golden.json"
