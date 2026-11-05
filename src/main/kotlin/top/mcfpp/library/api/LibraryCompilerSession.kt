package top.mcfpp.library.api

import top.mcfpp.CompileSettings
import top.mcfpp.CompilerOptions
import top.mcfpp.Project
import top.mcfpp.api.library.LibraryCompilerApi
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinReader
import top.mcfpp.library.compiler.LibraryIdentity
import top.mcfpp.library.compiler.ManagedNativeBindings
import top.mcfpp.library.format.LibraryFormatException
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.ZipFile

/**
 * Holds the compiler for one project compilation requested through [LibraryCompilerApi].
 * Create a session with [openSession], then execute it with [compileSession].
 *
 * The request contains `apiVersion = 1` and a `configuration` Path.
 * Supply lists named `lockedPackages`, `libraries`, `plugins`, `resourcePacks`, and `datapacks`, using empty lists where appropriate.
 *
 * Locked packages contain `coordinate`, `version`, and a list of `dependencies` with those same identity fields.
 * Libraries contain identity fields, `classifier`, `sha256`, `binary`, `moduleJson`, and a map of `resources` to byte arrays.
 * Other artifacts contain identity fields, `classifier`, `sha256`, and a file `path`.
 * Digests use 32-byte SHA-256 arrays. An optional `hostClassLoader` supplies the parent loader for plugins.
 */
class LibraryCompilerSession private constructor(
    private var request: CompilerRequest?,
    private var options: CompilerOptions?,
) : AutoCloseable {
    private var used = false
    private var closed = false
    private var started = false
    private var pluginLoader: URLClassLoader? = null
    private var savedSettings: SavedSettings? = null

    @Synchronized
    private fun execute(): Map<String, Any> {
        check(!closed && !used) { "A compiler session can compile only once" }
        used = true
        started = true
        return try {
            executeOnce(requireNotNull(request))
        } finally {
            close()
        }
    }

    private fun executeOnce(input: CompilerRequest): Map<String, Any> {
        savedSettings = SavedSettings()
        val capture = LogProcessor.beginDiagnosticCapture()
        try {
        val failures = arrayListOf<Map<String, Any>>()
        val outputs = arrayListOf<Map<String, Any>>()
        var loadedLibraries = 0
        var loadedModules = 0
        try {
            CompileSettings.isDebug = false
            CompileSettings.ignoreStdLib = false
            CompileSettings.isLib = false
            CompileSettings.maxWhileInline = 32
            CompileSettings.printAll = false
            LogProcessor.level = options?.level ?: LogLevel.DEBUG
            Project.errorCount = 0
            Project.warningCount = 0
            val compilerOptions = options ?: CompilerOptions().also { it.file = input.configuration.toFile() }
            require(compilerOptions.file.toPath().toAbsolutePath().normalize() == input.configuration) {
                "Compiler options reference a different configuration"
            }
            val config = Project.readConfig(compilerOptions)
            if (Project.errorCount == 0) {
                Project.config = config
                Project.compileStage = Project.CompileStage.PRE_INIT
                Project.stageProcessor[Project.CompileStage.PRE_INIT.ordinal].forEach { it() }
                val startupErrors = Project.errorCount
                val startupWarnings = Project.warningCount
                Project.init()
                Project.errorCount += startupErrors
                Project.warningCount += startupWarnings
                val bindings = plugins(input)
                Project.classLoader = pluginLoader ?: input.hostClassLoader
                Project.libraryNativeBindings = bindings.native
                Project.librarySourceProviders = bindings.owners
                if (Project.errorCount == 0 && Project.checkConfig()) {
                    Project.readProject(input.libraries)
                    loadedLibraries = Project.loadedLibraryCount
                    loadedModules = Project.modules.size
                    if (Project.errorCount == 0) Project.indexType()
                    if (Project.errorCount == 0) Project.resolveField()
                    if (Project.errorCount == 0) Project.runAnnotation()
                    if (Project.errorCount == 0) Project.compile()
                    if (Project.errorCount == 0) Project.optimization()
                    if (Project.errorCount == 0) {
                        val target = requireNotNull(config.targetPath).toAbsolutePath().normalize()
                        if (!config.noMcLib) {
                            Project.genIndex()
                            if (Project.errorCount == 0) {
                                require(Files.isRegularFile(target.resolve("bin.mclib")) && Files.isRegularFile(target.resolve("module.json"))) {
                                    "Library export did not produce bin.mclib and module.json at $target"
                                }
                                outputs += output("compiler.mcfpp.library", target)
                            }
                        }
                        Project.ctx.clear()
                        if (Project.errorCount == 0 && !config.noDatapack) {
                            Project.compileStage = Project.CompileStage.GEN_DATAPACK
                            DatapackCreator.createDatapack(target.toString())
                            Project.stageProcessor[Project.CompileStage.GEN_DATAPACK.ordinal].forEach { it() }
                            if (Project.errorCount == 0) outputs += output("minecraft.datapack", target.resolve(config.name))
                        }
                        if (Project.errorCount == 0) {
                            input.resourcePacks.forEach { outputs += output("minecraft.resourcepack", it.path) }
                            input.datapacks.forEach { outputs += output("minecraft.datapack", it.path) }
                        }
                        if (CompileSettings.printAll) GlobalScope.printAll()
                    }
                }
            }
        } catch (failure: Exception) {
            LogProcessor.recordProjectError()
            failures += diagnostic(failure)
        } catch (failure: LinkageError) {
            LogProcessor.recordProjectError()
            failures += diagnostic(failure)
        }
        val publication = capture.seal()
        val diagnostics = publication.diagnostics.filter { it.level != LogLevel.DEBUG }.map { event ->
            val result = linkedMapOf<String, Any>("code" to "MCFPP_COMPILER", "severity" to when (event.level) {
                LogLevel.ERROR -> "error"
                LogLevel.WARN -> "warning"
                else -> "info"
            }, "message" to event.message)
            event.source?.file?.let { result["entry"] = it }
            Collections.unmodifiableMap(result)
        }.toMutableList()
        diagnostics += failures
        var errors = Project.errorCount
        try {
            publication.publishLogs()
        } finally {
            try {
                close()
            } catch (failure: Exception) {
                errors++
                diagnostics += diagnostic(failure)
            }
        }
        if (errors > 0 && diagnostics.none { it["severity"] == "error" }) {
            diagnostics += Collections.unmodifiableMap(mapOf("code" to "MCFPP_COMPILER", "severity" to "error", "message" to "Compilation failed with $errors compiler error(s)"))
        }
        return Collections.unmodifiableMap(mapOf(
            "success" to (errors == 0 && diagnostics.none { it["severity"] == "error" }),
            "loadedLibraries" to loadedLibraries, "loadedModules" to loadedModules,
            "compilerErrors" to errors,
            "diagnostics" to Collections.unmodifiableList(diagnostics),
            "outputs" to Collections.unmodifiableList(outputs),
        ))
        } finally {
            capture.close()
        }
    }

    private fun plugins(input: CompilerRequest): PluginBindings {
        (input.plugins + input.resourcePacks + input.datapacks).forEach(::verifyArtifact)
        val owners = linkedMapOf<String, PackageKey>()
        input.plugins.forEach { artifact -> ZipFile(artifact.path.toFile()).use { zip ->
            require(zip.entries().asSequence().none { entry ->
                val name = entry.name.removePrefix("/")
                (name.startsWith("top/mcfpp/") || Regex("META-INF/versions/[0-9]+/top/mcfpp/.*").matches(name)) && name.endsWith(".class")
            }) { "Plugin bundles compiler/API classes: ${artifact.path}" }
            zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".class") }.forEach classEntry@ { entry ->
                val logical = entry.name.replace(Regex("^META-INF/versions/[0-9]+/"), "")
                if (logical == "module-info.class") return@classEntry
                val name = logical.removeSuffix(".class").replace('/', '.')
                val previous = owners.put(name, artifact.identity)
                require(previous == null || previous == artifact.identity) { "Plugin owner $name is provided by different packages" }
            }
        } }
        if (input.plugins.isNotEmpty()) pluginLoader = URLClassLoader(input.plugins.map { it.path.toUri().toURL() }.toTypedArray(), input.hostClassLoader)
        val providers = input.plugins.associate { it.path.toRealPath() to it.identity }
        val packages = input.plugins.mapTo(hashSetOf()) { it.identity }
        val managed = object : ManagedNativeBindings {
            private val locations = hashMapOf<String, PackageKey?>()

            override fun loader(provider: PackageKey?): ClassLoader {
                if (provider == null) return Project.classLoader
                require(provider in packages) { "No trusted installed plugin provides $provider" }
                return requireNotNull(pluginLoader)
            }
            override fun provider(owner: java.lang.Class<*>): PackageKey? {
                if (providers.isEmpty()) return null
                val source = owner.protectionDomain?.codeSource?.location ?: return null
                if (source.protocol != "file") return null
                val location = source.toExternalForm()
                if (locations.containsKey(location)) return locations[location]
                return providers[Path.of(source.toURI()).toRealPath()].also { locations[location] = it }
            }
        }
        return PluginBindings(managed, Collections.unmodifiableMap(owners))
    }

    private fun verifyArtifact(artifact: Artifact) {
        require(Files.isRegularFile(artifact.path)) { "Installed artifact is missing: ${artifact.identity} at ${artifact.path}; run mcfpm install" }
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(artifact.path).use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().contentEquals(artifact.sha256)) { "Installed artifact checksum changed: ${artifact.identity}; run mcfpm install" }
    }

    @Synchronized
    /** Releases compilation state and plugin files. Repeated calls keep the session closed. */
    override fun close() {
        if (closed) return
        closed = true
        val failures = arrayListOf<Throwable>()
        try {
            if (started) runCatching { Project.closeCompilation() }.exceptionOrNull()?.let(failures::add)
            pluginLoader?.let { runCatching { it.close() }.exceptionOrNull()?.let(failures::add) }
            savedSettings?.restore()
        } finally {
            pluginLoader = null; savedSettings = null; request = null; options = null
            release(this)
        }
        if (failures.isNotEmpty()) throw IllegalStateException("Unable to close compiler session", failures.first()).also {
            failures.drop(1).forEach(it::addSuppressed)
        }
    }

    private fun diagnostic(failure: Throwable): Map<String, Any> {
        val chain = generateSequence(failure) { it.cause }.toList()
        val format = chain.filterIsInstance<LibraryFormatException>().firstOrNull()
        val imported = chain.filterIsInstance<LibraryImportException>().firstOrNull()
        val source = chain.filterIsInstance<LibBinReader.SourceException>().firstOrNull()?.payload
        val result = linkedMapOf<String, Any>("code" to (format?.code ?: imported?.code ?: "MCFPP_COMPILATION_FAILED"),
            "severity" to "error", "message" to (failure.message ?: failure.javaClass.name))
        source?.identity?.let {
            result["coordinate"] = it.library.coordinate; result["version"] = it.library.version; result["classifier"] = it.classifier
        }
        if (format != null) {
            result["entry"] = "bin.mclib"; result["byteOffset"] = format.byteOffset
            format.sectionId?.let { result["section"] = it }
            format.recordIndex?.let { result["record"] = it }
        }
        return Collections.unmodifiableMap(result)
    }

    private fun output(kind: String, path: Path): Map<String, Any> = Collections.unmodifiableMap(mapOf("kind" to kind, "path" to path))

    private data class Artifact(val identity: PackageKey, val classifier: String, val path: Path, val sha256: ByteArray)
    private data class PluginBindings(val native: ManagedNativeBindings, val owners: Map<String, PackageKey>)
    private data class CompilerRequest(val configuration: Path, val libraries: List<LibBinReader.Payload>, val plugins: List<Artifact>,
        val resourcePacks: List<Artifact>, val datapacks: List<Artifact>, val hostClassLoader: ClassLoader)

    private class SavedSettings {
        val debug = CompileSettings.isDebug
        val stdlib = CompileSettings.ignoreStdLib
        val library = CompileSettings.isLib
        val inline = CompileSettings.maxWhileInline
        val print = CompileSettings.printAll
        val level = LogProcessor.level
        fun restore() {
            CompileSettings.isDebug = debug; CompileSettings.ignoreStdLib = stdlib; CompileSettings.isLib = library
            CompileSettings.maxWhileInline = inline; CompileSettings.printAll = print; LogProcessor.level = level
        }
    }

    companion object {
        private var active: AutoCloseable? = null

        /** Copies and validates [request], then reserves the compiler for one compilation. */
        @JvmStatic
        fun openSession(request: Map<String, Any>): AutoCloseable = openWithOptions(request, null)

        /** Opens a session with CLI [options] applied to the configuration in [request]. */
        @Synchronized
        fun openWithOptions(request: Map<String, Any>, options: CompilerOptions?): AutoCloseable {
            check(active == null) { "The MCFPP compiler is busy with another session" }
            return LibraryCompilerSession(parse(snapshotMap(request, "request")), options).also(::reserve)
        }

        /** Compiles an unused session from [openSession] and closes it. Returns the result map described by [LibraryCompilerApi.compile]. */
        @JvmStatic
        fun compileSession(session: AutoCloseable): Map<String, Any> {
            require(session is LibraryCompilerSession) { "Session was not created by LibraryCompilerApi.openSession" }
            return session.execute()
        }

        @Synchronized
        internal fun release(session: AutoCloseable) { if (active === session) active = null }

        /** Project is process-global. A prepared kernel owns the same lease as a one-shot host. */
        @Synchronized
        internal fun reserve(session: AutoCloseable) {
            check(active == null) { "The MCFPP compiler is busy with another session" }
            active = session
        }

        private fun parse(input: Map<String, Any>): CompilerRequest {
            require(input["apiVersion"] == 1) { "Unsupported compiler request ABI: ${input["apiVersion"]}" }
            val configuration = (input["configuration"] as? Path)?.toAbsolutePath()?.normalize()
                ?: throw IllegalArgumentException("configuration must be a Path")
            require(Files.isRegularFile(configuration)) { "MCFPP configuration does not exist: $configuration" }
            val host = if ("hostClassLoader" in input) input["hostClassLoader"] as? ClassLoader
                ?: throw IllegalArgumentException("hostClassLoader must be a ClassLoader") else LibraryCompilerApi::class.java.classLoader
            val sharesApi = runCatching { host.loadClass(LibraryCompilerApi::class.java.name) === LibraryCompilerApi::class.java }
                .getOrElse { throw IllegalArgumentException("hostClassLoader cannot see the compiler API", it) }
            require(sharesApi) { "hostClassLoader does not share the compiler API identity" }
            val packages = maps(input["lockedPackages"], "lockedPackages")
            val locked = linkedMapOf<String, String>()
            packages.forEach { pkg ->
                val coordinate = text(pkg, "coordinate")
                require(locked.put(coordinate, text(pkg, "version")) == null) { "Duplicate locked package coordinate: $coordinate" }
            }
            packages.forEach { pkg -> maps(pkg["dependencies"], "lockedPackage.dependencies").forEach { dep ->
                require(locked[text(dep, "coordinate")] == text(dep, "version")) { "Missing or inconsistent locked dependency" }
            } }
            fun identity(value: Map<String, Any>): PackageKey {
                val coordinate = text(value, "coordinate"); val version = text(value, "version")
                require(locked[coordinate] == version) { "Missing or inconsistent lock metadata for $coordinate@$version" }
                return PackageKey(coordinate, version)
            }
            val seen = hashSetOf<LibraryIdentity>()
            val libraries = maps(input["libraries"], "libraries").map { lib ->
                val id = LibraryIdentity(identity(lib), text(lib, "classifier"))
                require(seen.add(id)) { "Duplicate installed library payload: $id" }
                digest(lib)
                val resources = readMap(lib["resources"], "library.resources").mapValues { (entry, bytes) ->
                    require(bytes is ByteArray) { "Library resource $entry must be a byte array" }; bytes
                }
                LibBinReader.Payload(lib["binary"] as? ByteArray ?: throw IllegalArgumentException("library.binary must be a byte array"),
                    lib["moduleJson"] as? ByteArray ?: throw IllegalArgumentException("library.moduleJson must be a byte array"),
                    resources, id, "${id.library.coordinate}@${id.library.version}#${id.classifier}")
            }
            val positions = libraries.mapIndexed { index, lib -> requireNotNull(lib.identity).library.coordinate to index }.toMap()
            libraries.forEachIndexed { index, lib ->
                val pkg = packages.single { it["coordinate"] == requireNotNull(lib.identity).library.coordinate }
                maps(pkg["dependencies"], "lockedPackage.dependencies").forEach { dep ->
                    require(positions[text(dep, "coordinate")]?.let { it < index } != false) { "Installed libraries are not in locked dependency order" }
                }
            }
            fun artifacts(key: String): List<Artifact> {
                val identities = hashSetOf<Pair<PackageKey, String>>()
                return maps(input[key], key).map { value ->
                val identity = identity(value); val classifier = text(value, "classifier")
                require(identities.add(identity to classifier)) { "Duplicate $key artifact: $identity#$classifier" }
                Artifact(identity, classifier,
                    (value["path"] as? Path)?.toAbsolutePath()?.normalize() ?: throw IllegalArgumentException("$key.path must be a Path"), digest(value))
                }
            }
            return CompilerRequest(configuration, libraries, artifacts("plugins"), artifacts("resourcePacks"), artifacts("datapacks"), host)
        }

        private fun text(value: Map<String, Any>, key: String): String = (value[key] as? String)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("$key must be a nonempty String")
        private fun digest(value: Map<String, Any>): ByteArray = (value["sha256"] as? ByteArray)?.also {
            require(it.size == 32) { "sha256 must contain 32 bytes" }
        } ?: throw IllegalArgumentException("sha256 must be a byte array")
        private fun maps(value: Any?, location: String): List<Map<String, Any>> {
            require(value is List<*>) { "$location must be a List" }
            return value.mapIndexed { index, entry -> readMap(entry, "$location[$index]") }
        }
        @Suppress("UNCHECKED_CAST")
        private fun readMap(value: Any?, location: String): Map<String, Any> {
            require(value is Map<*, *> && value.keys.all { it is String }) { "$location must be a Map with String keys" }
            return value as Map<String, Any>
        }
        private fun snapshotMap(value: Any?, location: String): Map<String, Any> = Collections.unmodifiableMap(
            readMap(value, location).mapValues { (key, entry) -> snapshot(entry, "$location.$key") })
        private fun snapshot(value: Any?, location: String): Any = when (value) {
            is String, is Int, is Long, is Boolean, is Path, is ClassLoader -> value
            is ByteArray -> value.copyOf()
            is Map<*, *> -> snapshotMap(value, location)
            is List<*> -> Collections.unmodifiableList(value.mapIndexed { index, entry -> snapshot(entry, "$location[$index]") })
            else -> throw IllegalArgumentException("$location contains a non-JDK ABI value: ${value?.javaClass?.name ?: "null"}")
        }
    }
}
