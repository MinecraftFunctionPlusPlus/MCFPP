# Browser compiler (TeaVM)

This Gradle module compiles the existing MCFPP single-file kernel to WasmGC using
TeaVM 0.16.0 and JDK 25. The website remains one VitePress/Vue application in MCFPPAPI.

```powershell
.\gradlew.bat :browser:buildWasmGC
# In ../MCFPPAPI:
pnpm playground:prepare
pnpm docs:dev
```

The generated files are in `browser/build/generated/teavm/wasm-gc/`.
`playground:prepare` copies the Wasm module, its matching loader and debugging files,
and records their sizes and SHA-256 hashes. Ordinary website builds do not need Java.

## What is shared

`BrowserCompiler.compile` calls `PlaygroundCompilerApi` and the normal parser,
semantic passes, optimizer and datapack generator. The first compile includes library
decoding, native binding and baseline capture. Later requests reuse that baseline,
with separate source/output directories and restoration of mutable state.

The entire bundled standard library for the website's fixed Minecraft 1.21.8 target is
embedded, including its runtime files and generated import plan. Other Minecraft
targets, external JAR plugins and the desktop package manager are not browser inputs.

## Platform boundary

- `BrowserResources` includes the generated resource inventory at build time.
- `BrowserReflection` retains native entry points, annotation/parser constructors and
  the fields needed by rollback journals. TeaVM's generated annotation internals are
  excluded; their native Wasm structs cannot be exposed as ordinary Java fields.
- The compiler itself reads the embedded resource inventory and uses portable annotation
  codecs. JVM and Wasm follow the same library preparation and request cleanup path.
- `GeneratedNativeRoots` is generated from the bundled descriptors and built-in annotation
  codecs. Direct class literals replace dynamic plugin/provider discovery.
- `BrowserPlugin` supplies the missing JDK methods and preserves useful reflection errors.
- `json/` handles the compiler's JSON values without Fastjson's dynamic bytecode,
  MethodHandle or Unsafe machinery. Cross-adapter signatures use the original API
  types so TeaVM preserves a single class identity.
- `jdk/` and `BrowserJdk` supply the small set of missing class-library operations.
  SHA-256 uses Bouncy Castle's portable primitive and still verifies the library's
  original resource hashes and import-plan fingerprint.
- Field annotation copies use portable snapshots; Java object-stream compatibility was removed.

This branch targets only the Playground. The full JVM application, desktop dependencies,
external MNI/library integration and release tasks remain on `kotlin-latest` at `079f4c3f`.

## Reproduce measurements

```powershell
.\gradlew.bat :browser:benchmarkKernel
```

`benchmarks/cases.json` supplies the common browser/native sources. The JVM task writes
`browser/build/reports/browser/native-results.json`, including detached files,
diagnostics, wall time and internal phase times. Run browser acceptance against the
same prepared Wasm artifact; compare file paths and contents, including JSON formatting.
Initialization and subsequent DSB execution are recorded separately from compilation.
The fixtures also verify that source-level custom MNI declarations fail with a diagnostic,
publish no files, and leave the next compilation usable.
Measured results and the accepted artifact hash are recorded in [PERFORMANCE.md](PERFORMANCE.md).

Primary references: [Gradle configuration](https://teavm.org/docs/tooling/gradle.html),
[WasmGC loader](https://teavm.org/docs/wasm-gc-backend/loader.html),
[reflection and resource policies](https://teavm.org/docs/runtime/compiler-extensions.html).
