package top.mcfpp.io

import top.mcfpp.Project
import top.mcfpp.library.api.LibraryImportSession
import top.mcfpp.library.compiler.LibraryIdentity
import top.mcfpp.library.model.LibraryModel
import java.io.InputStream
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/** Sources become closed byte snapshots before the compilation's common binding barrier. */
object LibBinReader {
    class SourceException(val payload: Payload, cause: Throwable) : IllegalStateException(
        "Unable to import ${payload.identity ?: payload.source}: ${cause.message}", cause,
    )
    data class Payload(
        val binary: ByteArray,
        val moduleJson: ByteArray,
        val resources: Map<String, ByteArray>,
        val identity: LibraryIdentity? = null,
        val source: String,
    )

    fun stagePublished(path: Path, session: LibraryImportSession, expectedIdentity: LibraryIdentity? = null): LibraryModel =
        session.stage(path, expectedIdentity)

    /** Convenience for one complete library; production multi-source compilation uses [payload]. */
    fun read(path: String) {
        val source = payload(Path.of(path))
        readSnapshot(source.binary, source.resources)
    }

    fun readFromStream(stream: InputStream) {
        readSnapshot(stream.use { it.readBytes() }, emptyMap())
    }

    private fun readSnapshot(bytes: ByteArray, resources: Map<String, ByteArray>) {
        val existing = Project.libraryImportSession
        val session = existing ?: LibraryImportSession().also { Project.libraryImportSession = it }
        try {
            session.stage(bytes, null, resources)
            if (existing == null) {
                session.bindAll()
                session.commit()
            }
        } catch (failure: Exception) {
            session.close()
            if (Project.libraryImportSession === session) Project.libraryImportSession = null
            throw failure
        }
    }

    fun payload(path: Path): Payload {
        val source = path.toAbsolutePath().normalize()
        require(Files.exists(source)) { "Library source does not exist: $source" }
        return when {
            Files.isDirectory(source) -> directory(source)
            source.fileName.toString().endsWith(".jar", true) || source.fileName.toString().endsWith(".zip", true) -> archive(source)
            source.fileName.toString() == "bin.mclib" -> directory(requireNotNull(source.parent))
            else -> throw IllegalArgumentException("Expected a library directory, bin.mclib, JAR or ZIP: $source")
        }
    }

    fun builtin(loader: ClassLoader, target: String = Project.config.version): Payload {
        require(top.mcfpp.command.TargetCapabilities.forVersion(target) != null) { "Unsupported standard-library target: $target" }
        val targeted = "datapack/targets/$target/bin.mclib"
        val resource = loader.getResource(targeted) ?: requireNotNull(loader.getResource("datapack/bin.mclib")) {
            "Bundled v1 standard library is missing for Minecraft $target; rebuild src/main/mcfpp for this target or configure stdLib"
        }
        val payload = when (resource.protocol) {
            "file" -> directory(Path.of(resource.toURI()).parent)
            "jar" -> {
                val connection = resource.openConnection() as JarURLConnection
                connection.useCaches = false
                archive(Path.of(connection.jarFileURL.toURI()), connection.entryName.removeSuffix("bin.mclib"))
            }
            else -> throw IllegalArgumentException("Unsupported bundled library source: $resource")
        }
        // A root bundle is still checked against the requested Minecraft target by stage().
        return payload.copy(source = "Standard library requested for Minecraft $target from $resource")
    }

    fun embedded(path: Path): Payload? = ZipFile(path.toFile()).use { zip ->
        if (zip.getEntry("bin.mclib") == null && zip.getEntry("datapack/bin.mclib") == null) null else archive(path)
    }

    private fun directory(directory: Path): Payload {
        val binary = directory.resolve("bin.mclib")
        val module = directory.resolve("module.json")
        require(Files.isRegularFile(binary)) { "Library is missing bin.mclib: $directory" }
        require(Files.isRegularFile(module)) { "Library is missing module.json: $directory" }
        val resources = linkedMapOf<String, ByteArray>()
        val canonicalRoot = Files.isDirectory(directory.resolve("datapack"))
        Files.walk(directory).use { files ->
            files.filter { Files.isRegularFile(it) }.forEach { file ->
                val relative = directory.relativize(file).joinToString("/")
                val canonical = resourcePath(relative, allowDirectoryRoot = !canonicalRoot) ?: return@forEach
                require(resources.put(canonical, Files.readAllBytes(file)) == null) { "Duplicate library resource: $canonical" }
            }
        }
        return Payload(Files.readAllBytes(binary), Files.readAllBytes(module), resources, source = directory.toString())
    }

    private fun archive(path: Path, preferredPrefix: String? = null): Payload = ZipFile(path.toFile()).use { zip ->
        val prefix = preferredPrefix ?: if (zip.getEntry("bin.mclib") != null) "" else "datapack/"
        val binary = requireNotNull(zip.getEntry("${prefix}bin.mclib")) { "Library archive is missing bin.mclib: $path" }
        val module = requireNotNull(zip.getEntry("${prefix}module.json")) { "Library archive is missing module.json: $path" }
        val names = hashSetOf<String>()
        val resources = linkedMapOf<String, ByteArray>()
        val entries = zip.entries().asSequence().toList()
        val canonicalRoot = entries.any { it.name.startsWith("${prefix}datapack/") }
        for (entry in entries) {
            require(names.add(entry.name)) { "Duplicate library archive entry: ${entry.name}" }
            val segments = entry.name.removeSuffix("/").split('/')
            require('\\' !in entry.name && ':' !in entry.name && segments.none { it.isEmpty() || it == "." || it == ".." }) {
                "Invalid library archive entry: ${entry.name}"
            }
            if (entry.isDirectory || entry.name == binary.name || entry.name == module.name) continue
            if (!entry.name.startsWith(prefix)) continue
            val relative = entry.name.removePrefix(prefix)
            val canonical = resourcePath(relative, allowDirectoryRoot = prefix.isNotEmpty() && !canonicalRoot) ?: continue
            require(resources.put(canonical, zip.getInputStream(entry).use { it.readBytes() }) == null) {
                "Duplicate normalized library resource: $canonical"
            }
        }
        Payload(zip.getInputStream(binary).use { it.readBytes() }, zip.getInputStream(module).use { it.readBytes() },
            resources, source = path.toString())
    }

    /** Legacy directory roots are normalized here; published archives already use datapack/. */
    private fun resourcePath(name: String, allowDirectoryRoot: Boolean = true): String? {
        val parts = name.split('/')
        val relevant = parts.firstOrNull() == "datapack" && parts.getOrNull(2) == "data" ||
            allowDirectoryRoot && parts.getOrNull(1) == "data"
        if (!relevant) return null
        require('\\' !in name && ':' !in name && parts.none { it.isEmpty() || it == "." || it == ".." }) {
            "Invalid library resource path: $name"
        }
        val canonical = if (parts[0] == "datapack") name else "datapack/$name"
        require(canonical.split('/').size >= 4) { "Invalid library resource path: $name" }
        return canonical
    }
}
