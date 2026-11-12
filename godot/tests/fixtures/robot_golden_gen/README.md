# Robot policy golden vectors

`robot_policy_golden.json` is produced by the live Android offline Robot policy
(`LudoPawsComputerMovePolicy.kt`), compiled unchanged together with
`LudoPathEncoding.kt` from the private live repo. Only this harness (`Main.kt`) and the
field-compatible model stubs (`Stubs.kt`) live here; the live sources are not copied.

Regenerate (paths assume the Gradle 8.11.1 bundled Kotlin 2.0.20 jars):

    K=~/Workspace/ludopaws/android/app/src/main/java/com/rekixo/ludopaws
    L=~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/lib
    java -cp "$L/kotlin-compiler-embeddable-2.0.20.jar:$L/kotlin-stdlib-2.0.20.jar:$L/kotlin-script-runtime-2.0.20.jar:$L/kotlin-reflect-2.0.20.jar:$L/kotlin-daemon-embeddable-2.0.20.jar:$L/trove4j-1.0.20200330.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar:$L/annotations-24.0.1.jar" \
      org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp $L/kotlin-stdlib-2.0.20.jar -d out \
      Main.kt Stubs.kt $K/feature/offline/domain/ai/LudoPawsComputerMovePolicy.kt $K/game/domain/model/LudoPathEncoding.kt
    java -cp "out:$L/kotlin-stdlib-2.0.20.jar" MainKt > ../robot_policy_golden.json
