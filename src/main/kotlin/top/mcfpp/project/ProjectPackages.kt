package top.mcfpp.project

import top.mcfpp.ProjectConfig
import top.mcfpp.api.library.LibraryCompilerApi
import top.mcfpp.library.mcfpm.McfpmInstalledProjectBridge
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

class PreparedProject internal constructor(
    val definition: ProjectDefinition,
    val manifest: Path,
    val lock: Path,
    val mniLock: Path,
    val classpath: List<Path>,
    private val stage: Path,
    private val locked: Boolean,
) : AutoCloseable {
    fun commit(additional: Map<Path, ByteArray> = emptyMap()) {
        val writes = linkedMapOf<Path, ByteArray>()
        if (!locked) {
            writes[definition.root.resolve("mcfpp.lock")] = Files.readAllBytes(lock)
            if (definition.mniEnabled) writes[definition.root.resolve("mcfpp-mni.lock")] = Files.readAllBytes(mniLock)
        }
        writes.putAll(additional)
        val previous = writes.keys.associateWith { if (Files.exists(it)) Files.readAllBytes(it) else null }
        try {
            writes.forEach { (path, bytes) -> if (previous[path]?.contentEquals(bytes) != true) atomicWrite(path, bytes) }
        } catch (failure: Exception) {
            previous.forEach { (path, bytes) -> runCatching { if (bytes == null) Files.deleteIfExists(path) else atomicWrite(path, bytes) }.exceptionOrNull()?.let(failure::addSuppressed) }
            throw failure
        }
    }

    fun compilerRequest(parent: ClassLoader): Map<String, Any> = McfpmInstalledProjectBridge().prepare(
        definition.file, parent, manifest = manifest, lockfile = lock).request

    override fun close() = deleteTree(stage, definition.root)
}

/** Adapts MCFPP declarations to the generic SDK; owns no dependency resolver. */
class ProjectPackages(private val log: PrintWriter) {
    fun prepare(project: ProjectDefinition, offline: Boolean, locked: Boolean, update: Boolean = false, mode: String = "install"): PreparedProject {
        require(project.mniEnabled || project.packages.mni.isEmpty()) { "MNI dependencies require [mni] enabled = true" }
        Files.createDirectories(project.generated)
        val stage = Files.createTempDirectory(project.generated, "prepare-")
        try {
            val manifest = stage.resolve("mcfpm.toml")
            val lock = stage.resolve("mcfpp.lock")
            val mniLock = stage.resolve("mcfpp-mni.lock")
            for (file in listOf(lock, mniLock)) {
                val committed = project.root.resolve(file.fileName)
                if (Files.exists(committed)) Files.copy(committed, file)
            }
            val library = table(project.values["library"], "library")
            val tool = linkedMapOf<String, Any?>("consumer-profile" to "all", "default-repository" to "afox")
            tool.putAll(table(project.values["mcfpm"], "mcfpm"))
            val metadata = linkedMapOf<String, Any?>("id" to (library["coordinate"] ?: "local:unpublished"),
                "version" to (library["version"] ?: "0.0.0-unpublished"), "license" to "UNLICENSED",
                "minecraft" to (project.values["version"] ?: ProjectConfig().version))
            val dependencies = project.packages.mcfpp.mapValues { (_, value) -> if (value is String) mapOf("version" to value) else value }
            atomicWrite(manifest, toml(mapOf("schema" to 1, "package" to metadata, "tool" to tool, "dependencies" to dependencies)).toByteArray())
            val facade = Class.forName("moe.afox.mcfpm.repository.maven.ProjectPackageApi")
            log.println("Preparing MCFPP dependencies...")
            log.flush()
            @Suppress("UNCHECKED_CAST")
            val result = facade.getMethod("prepare", Map::class.java).invoke(null, mapOf(
                "manifest" to manifest, "lockfile" to lock, "offline" to offline, "locked" to locked, "update" to update)) as Map<String, Any>
            val diagnostics = (result["diagnostics"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
            check(result["success"] == true) { diagnostics.joinToString("\n") { "${it["code"]}: ${it["message"]}" } +
                if (offline) "; run mcfpp install online to restore dependencies" else "" }
            diagnostics.filter { it["severity"] == "warning" }.forEach { log.println("${it["code"]}: ${it["message"]}") }
            atomicWrite(lock, result["lock"] as ByteArray)
            val paths = if (project.mniEnabled) MniBuild(log).execute(project, mniLock, mode, offline, locked, update) else emptyList()
            return PreparedProject(project, manifest, lock, mniLock, paths, stage, locked)
        } catch (failure: Exception) {
            deleteTree(stage, project.root)
            throw failure
        }
    }
}

/** Never follows links or recursively removes a path outside the named project. */
internal fun deleteTree(path: Path, project: Path) {
    if (!Files.exists(path)) return
    val root = project.toRealPath()
    val target = path.toRealPath()
    require(target.startsWith(root) && target != root) { "Refusing to remove a directory outside the project: $path" }
    Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
}
