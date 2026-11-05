package top.mcfpp.library.api

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.io.DatapackCreator
import top.mcfpp.mni.expand.CompilerStateJournal
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

/**
 * A browser/JVM single-file workspace. The immutable protocol and prepared library declarations
 * survive requests. A deep compiler baseline and an import-directory baseline jointly restore
 * every retained mutable library object; source files and output paths are always request-owned.
 * Only the bundled Minecraft 1.21.8 library is accepted by this host.
 */
class PlaygroundCompilerKernel private constructor(
    private val directory: Path,
    private val minecraftVersion: String,
    private val namespace: String,
) : AutoCloseable {
    private var closed = false
    private var started = false
    private var nextRequest = 0L
    private var baseline: CompilerStateJournal? = null
    private var restoreImports: (() -> Unit)? = null
    private var settings: Settings? = null

    private class Settings {
        private val debug = CompileSettings.isDebug
        private val ignore = CompileSettings.ignoreStdLib
        private val library = CompileSettings.isLib
        private val inline = CompileSettings.maxWhileInline
        private val print = CompileSettings.printAll
        private val level = LogProcessor.level
        fun restore() {
            CompileSettings.isDebug = debug; CompileSettings.ignoreStdLib = ignore
            CompileSettings.isLib = library; CompileSettings.maxWhileInline = inline
            CompileSettings.printAll = print; LogProcessor.level = level
        }
    }

    private fun configuration(root: Path) = ProjectConfig(
        version = minecraftVersion, rootNamespace = namespace, name = "playground", root = root,
        sourcePath = root.resolve("src/main/mcfpp"), targetPath = root.resolve("build"), noMcLib = true,
    )

    private fun prepare() {
        settings = Settings()
        started = true
        CompileSettings.isDebug = false
        CompileSettings.ignoreStdLib = false
        CompileSettings.isLib = false
        CompileSettings.maxWhileInline = 32
        CompileSettings.printAll = false
        LogProcessor.level = LogLevel.ERROR
        Project.config = configuration(directory.resolve("library"))
        Project.init()
        Project.prepareLibraryKernel()
        check(Project.errorCount == 0) { "Unable to prepare the built-in standard library" }
        // Capture private caches first: stable native metadata can enroll additional type views.
        restoreImports = requireNotNull(Project.libraryImportSession).checkpointForReuse()
        baseline = CompilerStateJournal.checkpoint()
    }

    /** Keep the deep model and its auxiliary directories at the same baseline generation. */
    private fun restoreBaseline() {
        requireNotNull(baseline).restore()
        requireNotNull(restoreImports).invoke()
    }

    @Synchronized
    private fun compile(source: String): Map<String, Any> {
        check(!closed) { "The playground compiler kernel is closed" }
        val startedAt = System.nanoTime()
        var preparationNanos = 0L
        var restorationNanos = 0L
        var compilationNanos = 0L
        var errors = 0
        val failures = arrayListOf<Map<String, Any>>()
        val files = arrayListOf<Map<String, String>>()
        val root = directory.resolve("request-${++nextRequest}")
        val capture = LogProcessor.beginDiagnosticCapture()
        val diagnostics: List<Map<String, Any>>
        try {
            try {
                if (baseline == null) {
                    val mark = System.nanoTime()
                    prepare()
                    preparationNanos = System.nanoTime() - mark
                }
                // Each invocation owns its paths. A failure cannot publish stale output.
                Files.createDirectory(root)
                Project.beginKernelCompilation(configuration(root), source)
                val mark = System.nanoTime()
                Project.indexType()
                if (Project.errorCount == 0) Project.resolveField()
                if (Project.errorCount == 0) Project.runAnnotation()
                if (Project.errorCount == 0) Project.compile()
                if (Project.errorCount == 0) Project.optimization()
                if (Project.errorCount == 0) {
                    Project.ctx.clear()
                    Project.compileStage = Project.CompileStage.GEN_DATAPACK
                    DatapackCreator.createDatapack(root.resolve("build").toString())
                    Project.stageProcessor[Project.CompileStage.GEN_DATAPACK.ordinal].forEach { it() }
                    if (Project.errorCount == 0) readFiles(root.resolve("build/playground"), files)
                }
                compilationNanos = System.nanoTime() - mark
                errors = Project.errorCount
            } catch (failure: Exception) {
                errors++
                failures += failureDiagnostic(failure)
            } catch (failure: LinkageError) {
                errors++
                failures += failureDiagnostic(failure)
            } finally {
                try {
                    if (baseline != null) {
                        val mark = System.nanoTime()
                        restoreBaseline()
                        restorationNanos = System.nanoTime() - mark
                    } else close()
                } catch (failure: Exception) {
                    errors++
                    failures += failureDiagnostic(failure)
                    runCatching { close() }.exceptionOrNull()?.let { failures += failureDiagnostic(it) }
                } finally {
                    runCatching { deleteRequest(root) }.exceptionOrNull()?.let {
                        errors++
                        failures += failureDiagnostic(it)
                    }
                }
            }
            val publication = capture.seal()
            diagnostics = publication.diagnostics.filter { it.level != LogLevel.DEBUG }.map { event ->
                val result = linkedMapOf<String, Any>("code" to "MCFPP_COMPILER", "severity" to when (event.level) {
                    LogLevel.ERROR -> "error"; LogLevel.WARN -> "warning"; else -> "info"
                }, "message" to event.message)
                event.source?.file?.let { result["entry"] = it }
                Collections.unmodifiableMap(result)
            } + failures
        } finally { capture.close() }
        val success = errors == 0 && diagnostics.none { it["severity"] == "error" }
        return Collections.unmodifiableMap(mapOf(
            "success" to success, "kernelUsable" to !closed,
            "compilerErrors" to errors, "loadedLibraries" to if (baseline != null) 1 else 0,
            "diagnostics" to Collections.unmodifiableList(diagnostics),
            "files" to Collections.unmodifiableList(if (success) files else emptyList()),
            "timings" to mapOf("preparationNanos" to preparationNanos, "compilationNanos" to compilationNanos,
                "restorationNanos" to restorationNanos, "totalNanos" to (System.nanoTime() - startedAt)),
        ))
    }

    private fun readFiles(root: Path, result: MutableList<Map<String, String>>) {
        Files.walk(root).use { stream -> stream.filter { Files.isRegularFile(it) }.sorted().forEach { path ->
            val relative = root.relativize(path).joinToString("/")
            if (relative.endsWith(".mcfunction") || relative.endsWith(".json") || relative.endsWith(".mcmeta"))
                result += Collections.unmodifiableMap(mapOf("path" to relative, "content" to Files.readString(path)))
        } }
    }

    private fun deleteRequest(root: Path) {
        check(root.parent == directory && root.fileName.toString().startsWith("request-"))
        if (Files.exists(root)) Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }

    /** Exposes only counts internally, so regression checks can detect index retention. */
    internal fun retainedStateCounts(): List<Int> = Project.libraryImportSession?.retainedStateCounts().orEmpty()

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try { if (started) Project.closeCompilation() }
        finally {
            baseline = null; restoreImports = null
            settings?.restore(); settings = null
            if (active === this) active = null
        }
    }

    companion object {
        // Project is process-global, even in the native benchmark. One kernel owns it at a time.
        private var active: PlaygroundCompilerKernel? = null

        @JvmStatic
        @Synchronized
        fun open(directory: Path, minecraftVersion: String, namespace: String): AutoCloseable {
            require(minecraftVersion == "1.21.8") { "The Playground supports Minecraft 1.21.8" }
            require(namespace.matches(Regex("[a-z0-9_.-]+"))) { "Invalid namespace: $namespace" }
            val root = directory.toAbsolutePath().normalize()
            require(!Files.exists(root)) { "A compiler kernel requires a new workspace directory: $root" }
            val kernel = PlaygroundCompilerKernel(root, minecraftVersion, namespace)
            check(active == null) { "The MCFPP compiler is busy with another kernel" }
            active = kernel
            try { Files.createDirectories(root) } catch (failure: Throwable) { kernel.close(); throw failure }
            return kernel
        }

        @JvmStatic
        fun compileSource(kernel: AutoCloseable, source: String): Map<String, Any> {
            require(kernel is PlaygroundCompilerKernel) { "Kernel was not created by PlaygroundCompilerApi.openKernel" }
            return kernel.compile(source)
        }

        private fun failureDiagnostic(failure: Throwable): Map<String, Any> = Collections.unmodifiableMap(mapOf(
            "code" to "MCFPP_COMPILATION_FAILED", "severity" to "error",
            "message" to (failure.message ?: failure.javaClass.name),
        ))
    }
}
