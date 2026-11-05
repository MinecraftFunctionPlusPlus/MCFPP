# Kotlin/JVM MNI compatibility example

This example builds a small Kotlin provider JAR, loads it through the project's configured `jars` list and `.mcfpp` `injectedBy` directive, then calls its legacy `@MNIFunction` entries from MCFPP source. It demonstrates a top-level file facade, an `object` provider with `@JvmStatic`, a companion provider with `@JvmStatic`, read-only and ordinary arguments, `ValueWrapper` results, a caller declaration, and the `NativeCallContext` alternative.

## Build and run

Use JDK 25. The compiler, standalone provider and generated MNI projects use Kotlin/JVM 2.4.10, matching the Kotlin runtime supplied by the Mcfpm SDK. Both the JVM toolchain and bytecode target are 25. MNI API and DSL design prioritizes Kotlin; Java can call the generated JVM methods.

From the repository root, build the MCFPP runtime JAR first, then the provider JAR:

```powershell
.\gradlew shadowJar
.\gradlew -p examples/kotlin-mni jar
```

If the runtime JAR is elsewhere, pass its path as `-PmcfppApiJar=...` to the second command. The provider build uses that JAR as `compileOnly`; it deliberately does not bundle MCFPP classes or Kotlin runtime classes, which must come from the host compiler's class loader.

Run the sample from its own directory so the relative paths in `mcfpp.toml` and its `jars` entry resolve consistently:

```powershell
Set-Location examples/kotlin-mni
$env:JAVA_HOME = 'C:\path\to\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -jar ../../build/libs/MCFPP-0.1.0.jar build --project mcfpp.toml
```

The project source calls `topLevelDouble(21)`, `objectSum<5>(8)`, `objectPing()`, and `objectContextProbe(13)`. MCFPP readonly arguments go in angle brackets before the ordinary argument list, so `objectSum<5>(8)` binds `5` as the readonly `constant` and `8` as the normal `value`. `companionCallerProbe` is registered to show caller ABI metadata; a caller-bearing native is only callable where MCFPP supplies a matching receiver. Output is written under `project/build`.

| MCFPP entry | Kotlin declaration | ABI shape |
|---|---|---|
| `objectPing()` | object `@JvmStatic` | no arguments, JVM `void` |
| `topLevelDouble(value)` | top-level file facade | ordinary argument and `ValueWrapper` result |
| `objectSum<constant>(value)` | object `@JvmStatic` | read-only plus ordinary argument and `ValueWrapper` result |
| `companionCallerProbe` | companion `@JvmStatic` | caller argument |
| `objectContextProbe(value)` | object `@JvmStatic` | one `NativeCallContext` parameter |

## Provider ABI rules

The configured class loader loads each named provider class from the configured JAR. Use the exact JVM class name. Kotlin top-level declarations normally live in a generated file-facade class; this sample stabilizes that name with `@file:JvmName("KotlinTopLevelProvider")`. Object and companion members are instance members at the Kotlin language level; `@JvmStatic` emits a static JVM bridge that the current MNI reflection loader can invoke. The wrapper requires public static methods, so ordinary instance methods and private methods are not provider entries. [Kotlin/JVM static interop](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-methods).

Legacy `@MNIFunction` accepts either a single `NativeCallContext` parameter or the wrapper ABI described by its annotation metadata: read-only arguments, ordinary arguments, optional caller, then a `ValueWrapper` output for non-void returns. Read-only and normal arguments must be MCFPP `Var` types; each output wrapper must be `top.mcfpp.util.ValueWrapper`. A method returning JVM `void` is required even when the MCFPP result is non-void: write the MCFPP result through `ValueWrapper`. `NativeCallContext` is an alternative single-parameter entry point; its annotation still describes the MCFPP-visible arguments and return type.

Kotlin default parameter values do not automatically become MCFPP defaults. Java callers see the full Kotlin signature unless the function opts into generated overloads with `@JvmOverloads`; MCFPP registration uses only the explicit `@MNIFunction` metadata and the reflected JVM signature. Likewise, `suspend` functions have coroutine calling semantics and do not match the supported synchronous wrapper ABI; the loader rejects such a signature. [Kotlin default argument interop](https://kotlinlang.org/docs/java-to-kotlin-interop.html#overloads-generation), [Kotlin suspending functions](https://kotlinlang.org/spec/asynchronous-programming-with-coroutines.html).

This runnable example uses the legacy native API only. Macro descriptors have a separate registration and expansion path; these examples do not claim that a Kotlin `@MNIMacro` body can be executed as a native function.

## Validation

From the repository root, build the runtime and example provider JAR using the commands above. Then run the compiler from the example directory with `java -jar ../../build/libs/MCFPP-0.1.0.jar build --project mcfpp.toml`. The compiler reads the configured provider JAR and the `.mcfpp` directives, then compiles the sample project. The legacy configured-JAR integration check is `MniConfiguredJarInjectionTest.sourceDirectiveLoadsOnlyConfiguredJarProvidersDiscoversMacrosAndInvokesLegacyMni`.

With Java 21 selected through `JAVA_HOME`, the runtime JAR and provider JAR built successfully and the CLI exited with code 0. The CLI used OpenJDK 21.0.12.1, loaded all three provider classes, and compiled the sample with zero MCFPP errors and warnings. It generated `project/build/mcfpp/data/example.kotlin/function/main.mcfunction`. This confirms provider registration and compilation of the sample calls; it does not claim that the datapack was run in Minecraft. The exact CLI command, Java version, JAR hashes, exit code, compiler log, and generated-file listing are archived under `build/inheritance-mni/kotlin-example-results/attempt3/`.
