package top.mcfpp.io

import com.alibaba.fastjson2.JSON
import top.mcfpp.LibraryPublicationConfig
import top.mcfpp.Project
import top.mcfpp.library.api.LibraryExport
import top.mcfpp.library.compiler.LibraryExporter
import top.mcfpp.library.compiler.LibraryIdentity
import top.mcfpp.library.compiler.OwnedLibraryResource
import top.mcfpp.library.model.LibraryTarget
import top.mcfpp.library.syntax.PackageKey
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.Locale

object LibBinWriter {
    fun write(path: String) {
        if (path == "null") return
        val config = Project.config
        val publication = config.library ?: LibraryPublicationConfig(
            "local:${config.rootNamespace.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]+"), "-").trim('.', '_', '-').ifEmpty { "default" }}",
            "0.0.0",
        )
        val identity = LibraryIdentity(PackageKey(publication.coordinate, publication.version), publication.classifier)
        val resources = staticResources(config.dataPath, config.name)
        val selection = LibraryExporter.snapshotCurrentProject(identity, LibraryTarget(config.version), resources)
        val destination = Path.of(path).toAbsolutePath().normalize()
        val parent = requireNotNull(destination.parent)
        Files.createDirectories(parent)
        val staging = Files.createTempDirectory(parent, ".mcfpp-export-")
        try {
            // Capture and encode before touching the previous published library.
            val result = LibraryExport.write(selection, staging.resolve("bin.mclib"))
            val contents = selection.resources.associateBy { it.path }
            require(contents.size == selection.resources.size && contents.keys == result.model.resources.map { it.path }.toSet()) {
                "Published resource metadata does not match the owned resource contents"
            }
            for (resource in result.model.resources) {
                val bytes = contents.getValue(resource.path).bytes
                require(resource.byteLength == bytes.size.toULong() &&
                    resource.sha256 == MessageDigest.getInstance("SHA-256").digest(bytes).toList()) {
                    "Published resource digest does not match its contents: ${resource.path}"
                }
            }
            val modules = sortedMapOf<String, MutableSet<String>>()
            for (resource in selection.resources.sortedBy { it.path }) {
                val target = staging.resolve(resource.path).normalize()
                require(target.startsWith(staging)) { "Library resource escapes the publication directory: ${resource.path}" }
                Files.createDirectories(target.parent)
                Files.write(target, resource.bytes)
            }
            for (resource in result.model.resources) {
                val prefix = "datapack/${resource.module}/data/"
                require(resource.path.startsWith(prefix)) { "Library resource has inconsistent module: ${resource.path}" }
                modules.getOrPut(resource.module) { sortedSetOf() }.add(resource.path.removePrefix(prefix).substringBefore('/'))
            }
            val moduleJson = modules.mapValues { (_, namespaces) ->
                linkedMapOf<String, Any>("base" to namespaces.associateWith { it }, "packages" to emptyMap<String, Any>())
            }.ifEmpty {
                sortedMapOf(config.name to linkedMapOf<String, Any>("base" to emptyMap<String, String>(), "packages" to emptyMap<String, Any>()))
            }
            Files.writeString(staging.resolve("module.json"), JSON.toJSONString(moduleJson))
            Files.createDirectories(staging.resolve("datapack"))
            publish(staging, destination, modules.keys + config.name)
        } finally {
            staging.toFile().deleteRecursively()
        }
    }

    private fun staticResources(dataRoot: Path?, module: String): List<OwnedLibraryResource> {
        require(module.isNotBlank() && module != "." && module != ".." && module.none { it == '/' || it == '\\' || it == ':' }) {
            "Project name must be a single module path segment: $module"
        }
        if (dataRoot == null) return emptyList()
        require(Files.isDirectory(dataRoot, NOFOLLOW_LINKS)) { "dataPath must be an existing directory: $dataRoot" }
        return Files.walk(dataRoot).use { files ->
            files.filter { it != dataRoot }.sorted().map { file ->
                require(!Files.isSymbolicLink(file)) { "dataPath must not contain symbolic links: $file" }
                if (Files.isDirectory(file, NOFOLLOW_LINKS)) null else {
                    require(Files.isRegularFile(file, NOFOLLOW_LINKS)) { "Unsupported dataPath entry: $file" }
                    val relative = dataRoot.relativize(file).joinToString("/")
                    OwnedLibraryResource("datapack/$module/data/$relative", Files.readAllBytes(file), module)
                }
            }.filter { it != null }.map { requireNotNull(it) }.toList()
        }
    }

    /** Replace owned modules and metadata; restore their previous versions on failure. */
    private fun publish(staging: Path, destination: Path, currentModules: Set<String>) {
        Files.createDirectories(destination)
        Files.createDirectories(destination.resolve("datapack"))
        val oldManifest = destination.resolve("module.json")
        val previousModules = if (Files.exists(oldManifest)) JSON.parseObject(Files.readString(oldManifest)).keys else emptySet()
        val modules = (currentModules + previousModules).sorted()
        require(modules.all { it.isNotBlank() && it != "." && it != ".." && it.none { c -> c == '/' || c == '\\' || c == ':' } }) {
            "Library module metadata contains an invalid module path"
        }
        val entries = modules.map { "datapack/$it" } + listOf("module.json", "bin.mclib")
        val backup = Files.createTempDirectory(requireNotNull(destination.parent), ".mcfpp-export-backup-")
        val moved = arrayListOf<String>()
        val installed = arrayListOf<String>()
        var committed = false
        try {
            for (name in entries) {
                val target = destination.resolve(name)
                if (Files.exists(target, NOFOLLOW_LINKS)) {
                    Files.createDirectories(backup.resolve(name).parent)
                    Files.move(target, backup.resolve(name), ATOMIC_MOVE)
                    moved += name
                }
                if (Files.exists(staging.resolve(name), NOFOLLOW_LINKS)) {
                    Files.createDirectories(target.parent)
                    Files.move(staging.resolve(name), target, ATOMIC_MOVE, REPLACE_EXISTING)
                    installed += name
                }
            }
            committed = true
        } catch (failure: Exception) {
            for (name in installed.asReversed()) {
                try { Files.move(destination.resolve(name), staging.resolve(name), ATOMIC_MOVE, REPLACE_EXISTING) }
                catch (rollback: Exception) { failure.addSuppressed(rollback) }
            }
            for (name in moved.asReversed()) {
                try { Files.move(backup.resolve(name), destination.resolve(name), ATOMIC_MOVE, REPLACE_EXISTING) }
                catch (rollback: Exception) { failure.addSuppressed(rollback) }
            }
            throw failure
        } finally {
            // Retain recoverable originals if a rollback itself failed.
            if (committed || moved.all { !Files.exists(backup.resolve(it), NOFOLLOW_LINKS) }) backup.toFile().deleteRecursively()
        }
    }
}
