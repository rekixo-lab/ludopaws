#!/usr/bin/env bash
# Regenerates tests/fixtures/dice_golden.json: the UNCHANGED live dice renderers (read-only),
# recorded through the JVM canvas recorder in this folder (characters_gen stubs for the rest).
# Usage: tests/fixtures/dice_gen/generate.sh [live-repo]
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
live="${1:-$HOME/Workspace/ludopaws}"
gen="$here/../characters_gen"
K="$live/android/app/src/main/java/com/rekixo/ludopaws"
L="$(echo ~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/lib)"
CP="$L/kotlin-compiler-embeddable-2.0.20.jar:$L/kotlin-stdlib-2.0.20.jar:$L/kotlin-script-runtime-2.0.20.jar:$L/kotlin-reflect-2.0.20.jar:$L/kotlin-daemon-embeddable-2.0.20.jar:$L/trove4j-1.0.20200330.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar:$L/annotations-24.0.1.jar"
out="${TMPDIR:-/tmp}/ludopaws-dice-gen"
rm -rf "$out"; mkdir -p "$out"
shared=(
  "$K/feature/characters/rendering/CharacterRenderer.kt" "$K/feature/characters/rendering/CharacterDrawContext.kt"
  "$K/feature/characters/rendering/CharacterSurface.kt" "$K/feature/characters/domain/model/LudoPawsCharacterModels.kt"
  "$K/feature/characters/shared/CharacterHopDynamics.kt" "$K/feature/characters/shared/CharacterLivingBehavior.kt"
  "$K/feature/characters/shared/CharacterMaterials.kt" "$K/feature/characters/shared/CharacterReactionProfile.kt"
  "$K/feature/characters/shared/LudoPaws3DLifeMotion.kt" "$K/feature/characters/shared/Pet3DJump.kt"
  "$K/rendering/meshes/MeshGeometry.kt" "$K/rendering/meshes/SphereGeometry.kt"
  "$K/game/ui/components/LudoPawsTeamIdentityPolicy.kt"
  "$K/feature/characters/domain/model/CharacterDefinition.kt"
  "$K/feature/characters/rendering/CharacterAvatarRenderer.kt"
)
# Every species compiled so far (Species.kt references them all), avatars against the graphics stub.
content=()
for dir in "$K"/feature/characters/content/*/; do
  name="$(basename "$dir")"
  grep -q "content.$name\." "$gen/Species.kt" || continue
  for f in "$dir"*.kt; do

    content+=("$f")
  done
done
JSON_JAR="$(ls ~/.gradle/caches/modules-2/files-2.1/org.json/json/*/*/json-*.jar | grep -v sources | head -1)"
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn \
  -cp "$L/kotlin-stdlib-2.0.20.jar:$JSON_JAR" -d "$out" \
  "$here/Main.kt" "$here/PacingStub.kt" "$here/../boards_gen/AndroidGraphicsRecorder.kt" "$here/../boards_gen/AndroidUtilStub.kt" "$here/../boards_gen/AndroidResStub.kt" "$here/../boards_gen/RStub.kt" "$gen/AndroidMatrixStub.kt" "$gen/ReactionPoseStub.kt" "${shared[@]}" "${content[@]}" \
  "$K"/game/domain/model/*.kt "$K/game/ui/components/LudoPawsYardPalettePolicy.kt" \
  "$K"/feature/store/domain/model/*.kt "$K/feature/store/domain/CosmeticOfferFactory.kt" \
  "$K"/feature/dice/domain/*.kt "$K"/feature/dice/rendering/*.kt "$K"/feature/dice/content/*/*.kt \
  "$K/game/ui/components/DiceRollAnimationPolicy.kt"
java -cp "$out:$L/kotlin-stdlib-2.0.20.jar:$JSON_JAR" MainKt > "$here/../dice_golden.json"
echo "wrote tests/fixtures/dice_golden.json"
