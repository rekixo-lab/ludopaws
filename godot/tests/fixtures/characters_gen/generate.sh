#!/usr/bin/env bash
# Regenerates tests/fixtures/characters/<species>_golden.json from the UNCHANGED live Kotlin
# character sources (read-only) plus the pure-JVM stubs in this folder.
# Usage: tests/fixtures/characters_gen/generate.sh dog [live-repo]
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
species="$1"
live="${2:-$HOME/Workspace/ludopaws}"
K="$live/android/app/src/main/java/com/rekixo/ludopaws"
L="$(echo ~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/lib)"
CP="$L/kotlin-compiler-embeddable-2.0.20.jar:$L/kotlin-stdlib-2.0.20.jar:$L/kotlin-script-runtime-2.0.20.jar:$L/kotlin-reflect-2.0.20.jar:$L/kotlin-daemon-embeddable-2.0.20.jar:$L/trove4j-1.0.20200330.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar:$L/annotations-24.0.1.jar"
out="${TMPDIR:-/tmp}/ludopaws-characters-gen"
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
  grep -q "content.$name\." "$here/Species.kt" || continue
  for f in "$dir"*.kt; do

    content+=("$f")
  done
done
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn \
  -cp "$L/kotlin-stdlib-2.0.20.jar" -d "$out" \
  "$here/Main.kt" "$here/Species.kt" "$here/AndroidMatrixStub.kt" "$here/ReactionPoseStub.kt" "$here/AndroidGraphicsStub.kt" "${shared[@]}" "${content[@]}"
mkdir -p "$here/../characters"
java -cp "$out:$L/kotlin-stdlib-2.0.20.jar" MainKt "$species" > "$here/../characters/${species}_golden.json"
echo "wrote tests/fixtures/characters/${species}_golden.json"
