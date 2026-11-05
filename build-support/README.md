# Release packing

Run `./gradlew release` with JDK 25. The result is staged in `build/releases/0.1.0` with SHA-256 checksums and a source snapshot. This task prioritizes artifact size; its compression tools are build dependencies and are absent from the shipped runtime.

Quick tasks remain available: `shadowJar`, `shadowDistZip`, `shadowDistTar`, `installShadowDist`, `releaseSources` and `verifyDistribution`. They do not run `ReleasePackager`.

The release pipeline:

1. `releaseShadowJar` minimizes dependencies using main classes as roots. Kotlin reflection, the package-manager facade, NBT reflection, Log4j and the Nashorn service retain their required runtime dependencies. Existing quick Shadow configuration is independent.
2. `releaseJar` uses the JDK's `JarFile.versionedStream()` with version 25 to retain exactly the active multi-release classes and resources. It splits every `datapack/` resource into deterministic content-defined chunks and shares identical chunks across targets. An index retains every original path, size and chunk sequence. The runtime reads the selected target through `BuiltinResourcePack`; ordinary unpacked resources remain supported. ICU resources are removed only if the minimized JAR contains no ICU classes. Service descriptors retain providers whose classes remain in the JAR.
3. ZIP entries use the smallest result among storage, JDK DEFLATE levels 1–9 with all three strategies, and Zopfli with 30 iterations and unlimited block splitting. The result uses standard ZIP/JAR records and preserves Unix permissions, the selected class bytes, Kotlin metadata and licenses. Compression results are cached by content and encoder version under `build/release-compression-cache` and verified on reuse.
4. `releaseZip` packages the CLI for Windows. `releaseTar` produces a solid `.tar.xz` for Unix. API sources are provided separately; `releaseSourceArchive` captures the complete workspace source as `.tar.xz`, including uncommitted files and these build tools.

The packer verifies every encoded ZIP stream, every output entry, all original standard-library files and each XZ archive by decompression before replacing an output. This checks losslessness; CLI acceptance must still exercise the final archive. Targeted decoder checks can be run with `./gradlew test --tests top.mcfpp.test.BuiltinResourcePackTest`.

Dependency-root configuration follows the public `sourceSetsClassesDirs` property in [Shadow 9.2.2](https://github.com/GradleUp/shadow/blob/9.2.2/src/main/kotlin/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.kt). Raw standard ZIP output uses [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/zip/ZipArchiveOutputStream.html). Compression uses [JZopfli](https://github.com/LucCappellaro/jzopfli) and [XZ for Java](https://tukaani.org/xz/java.html); versions are pinned in the build script.

Multi-release selection uses the [JDK 25 JarFile API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/jar/JarFile.html#versionedStream()). The selected entry bytes are copied without transforming bytecode.
