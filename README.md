# MCFPP Browser Playground

This branch (`teavm-playground`) builds the single-file browser compiler used by
[MCFPPAPI](../MCFPPAPI). TeaVM 0.16.0 compiles the Kotlin/Java compiler to WasmGC.
The full desktop compiler is preserved on `kotlin-latest` at `079f4c3f`.

## Build and run

Install JDK 25, then build the compiler:

```powershell
.\gradlew.bat :browser:buildWasmGC
```

In the adjacent MCFPPAPI repository:

```powershell
pnpm playground:prepare
pnpm playground:typecheck
pnpm docs:dev
```

Open `/zh/playground.html`. The page is part of the VitePress site. Compilation
runs inside a dedicated Web Worker; DSB executes the generated datapack in another Worker.

## Scope

- One `main.mcfpp`, namespace `playground`, Minecraft 1.21.8.
- Complete bundled standard library for that target, with build-time symbol/signature/
  inheritance indexes and a generated catalog of native class literals.
- A reusable kernel retains the library baseline and restores mutable state after every
  request. Each request owns its source/output directory and returns detached diagnostics/files.
- Desktop CLI, project configuration loading, package installation, external library/JAR
  loading, custom source MNI declarations and desktop release/install tasks have been removed.
- Ordinary language imports still resolve namespaces inside the bundled standard library.

## Verification

```powershell
.\gradlew.bat test :browser:buildWasmGC
.\gradlew.bat :browser:benchmarkKernel
```

The branch keeps protocol and algorithm unit checks. Desktop, external-provider and
multi-target integration suites remain in the original branch. Browser integration is checked
using `browser/benchmarks/cases.json`: the native benchmark and the website acceptance script
compile the same sources and compare actual files, error recovery and DSB output.

See [browser/README.md](browser/README.md) for the platform adapters, artifact locations and
measurement procedure. Keep performance measurements separate from an active Gradle build.
