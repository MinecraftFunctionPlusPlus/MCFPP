package top.mcfpp.project

import com.alibaba.fastjson2.JSON
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** User-owned declarations. Generated files are always derived from this snapshot. */
data class ProjectDefinition(val file: Path, val values: Map<String, Any?>, val packages: PackageDeclarations) {
    val root: Path get() = file.parent
    val generated: Path get() = root.resolve(".mcfpp")
    val mni: Map<String, Any?> get() = table(values["mni"], "mni")
    val mniEnabled: Boolean get() = mni["enabled"] as? Boolean ?: false
    val output: Path get() = root.resolve(values["root"] as? String ?: "").resolve(values["targetPath"] as? String ?: "build").normalize()

    init {
        require(!output.startsWith(generated) && output.startsWith(root) && output != root) { "targetPath must be a directory below the project root, outside .mcfpp" }
        val toolchain = table(values["toolchain"], "toolchain")["version"]
        require(toolchain == null || toolchain == TOOLCHAIN) { "Unsupported toolchain '$toolchain'; this distribution provides $TOOLCHAIN" }
        require(mni["enabled"] == null || mni["enabled"] is Boolean) { "mni.enabled must be boolean" }
        require(mni.keys.all { it in setOf("enabled", "extensions", "plugins", "repositories") }) { "Unsupported mni configuration item" }
        for (key in listOf("extensions", "repositories")) {
            require(mni[key] == null || (mni[key] as? List<*>)?.all { it is String && it.isNotBlank() } == true) { "mni.$key must be an array of strings" }
        }
    }

    fun enableMni(): String {
        val next = LinkedHashMap(values)
        next["mni"] = LinkedHashMap(mni).apply { put("enabled", true) }
        return toml(next)
    }

    companion object {
        const val TOOLCHAIN = "java21-v1"

        fun find(start: Path, explicit: Path? = null): Path {
            if (explicit != null) {
                val path = start.resolve(explicit).toAbsolutePath().normalize()
                return if (Files.isDirectory(path)) path.resolve("mcfpp.toml") else path
            }
            var cursor: Path? = start.toAbsolutePath().normalize()
            while (cursor != null) {
                val file = cursor.resolve("mcfpp.toml")
                if (Files.isRegularFile(file)) return file
                cursor = cursor.parent
            }
            error("No mcfpp.toml found; run mcfpp init")
        }

        fun load(file: Path): ProjectDefinition {
            val absolute = file.toAbsolutePath().normalize()
            return ProjectDefinition(absolute, readToml(absolute), PackageDeclarations.read(absolute.parent.resolve("mcfpp-packages.json")))
        }

        fun readToml(file: Path): Map<String, Any?> {
            require(file.fileName.toString().endsWith(".toml")) { "Project configuration must be mcfpp.toml; JSON project files are no longer supported" }
            val parsed = Toml.parse(file)
            require(!parsed.hasErrors()) { parsed.errors().joinToString("\n") { "$file: $it" } }
            require(parsed.getLong("schema") == 1L) { "$file: schema must be 1" }
            return table(convert(parsed), "project")
        }

        private fun convert(value: Any?): Any? = when (value) {
            is TomlTable -> value.keySet().associateWith { convert(value.get(listOf(it))) }
            is TomlArray -> (0 until value.size()).map { convert(value.get(it)) }
            else -> value
        }
    }
}

data class MniDependency(val id: String, val configuration: String = "implementation") {
    val module: String get() = id.substringBeforeLast(':')
    init {
        require(id.split(':').size == 3 && id.split(':').all { it.isNotBlank() && it.none(Char::isWhitespace) }) { "MNI dependency must be group:artifact:version: $id" }
        require(configuration.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) { "Invalid MNI configuration: $configuration" }
    }
    fun json(): Any = if (configuration == "implementation") id else mapOf("id" to id, "configuration" to configuration)
}

data class PackageDeclarations(val mcfpp: Map<String, Any?> = emptyMap(), val mni: List<MniDependency> = emptyList()) {
    init {
        require(mni.map { it.module to it.configuration }.distinct().size == mni.size) { "Duplicate MNI dependency/configuration" }
        mcfpp.forEach { (id, spec) ->
            require(id.matches(Regex("[a-z0-9][a-z0-9._-]*:[a-z0-9][a-z0-9._-]*"))) { "MCFPP dependency requires a complete group:name: $id" }
            require(spec is String || spec is Map<*, *>) { "Invalid dependency: $id" }
            if (spec is Map<*, *>) {
                require(spec.keys.all { it in setOf("version", "features", "optional") } && spec["version"] is String) { "Invalid dependency options: $id" }
                require(spec["features"] == null || (spec["features"] as? List<*>)?.all { it is String } == true) { "$id.features must be strings" }
                require(spec["optional"] == null || spec["optional"] is Boolean) { "$id.optional must be boolean" }
            }
        }
    }
    fun bytes(): ByteArray = JSON.toJSONString(linkedMapOf("schema" to 1, "mni" to mni.map { it.json() }, "mcfpp" to mcfpp),
        com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat).plus("\n").toByteArray()

    companion object {
        fun read(file: Path): PackageDeclarations {
            if (!Files.exists(file)) return PackageDeclarations()
            val text = Files.readString(file)
            JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text).use { parser ->
                require(parser.nextToken() != null) { "$file: empty dependency document" }
                parser.skipChildren()
                require(parser.nextToken() == null) { "$file: unexpected trailing JSON" }
            }
            val value = JSON.parseObject(text)
            require(value["schema"] == 1 && value.keys.all { it in setOf("schema", "mni", "mcfpp") }) { "$file: expected dependency schema 1" }
            val mni = value["mni"] ?: emptyList<Any>()
            require(mni is List<*>) { "$file: mni must be an array" }
            return PackageDeclarations(table(value["mcfpp"], "mcfpp"), mni.map { entry ->
                when (entry) {
                    is String -> MniDependency(entry)
                    is Map<*, *> -> {
                        require(entry.keys.all { it in setOf("id", "configuration") }) { "$file: unknown MNI dependency option" }
                        MniDependency(entry["id"] as? String ?: error("MNI id must be a string"),
                            entry["configuration"] as? String ?: if (entry.containsKey("configuration")) error("configuration must be a string") else "implementation")
                    }
                    else -> error("$file: MNI dependency must be a coordinate or object")
                }
            })
        }
    }
}

internal fun table(value: Any?, label: String): Map<String, Any?> {
    if (value == null) return emptyMap()
    require(value is Map<*, *> && value.keys.all { it is String }) { "$label must be a table/object" }
    @Suppress("UNCHECKED_CAST") return value as Map<String, Any?>
}

internal fun quote(value: String): String = JSON.toJSONString(value)

/** Deterministic writer for the project and SDK tables; never stores absolute generated paths. */
internal fun toml(values: Map<String, Any?>): String = buildString {
    fun scalar(value: Any?): String = when (value) {
        is String -> quote(value)
        is Boolean, is Number -> value.toString()
        is List<*> -> value.joinToString(", ", "[", "]") { scalar(it) }
        else -> error("Cannot encode TOML value: $value")
    }
    fun section(map: Map<String, Any?>, path: List<String>) {
        if (path.isNotEmpty()) appendLine("\n[${path.joinToString(".") { quote(it) }}]")
        map.filterValues { it !is Map<*, *> && it != null }.forEach { (key, value) -> appendLine("${quote(key)} = ${scalar(value)}") }
        map.filterValues { it is Map<*, *> }.forEach { (key, value) -> section(table(value, key), path + key) }
    }
    section(values, emptyList())
}

internal fun atomicWrite(path: Path, bytes: ByteArray) {
    Files.createDirectories(path.toAbsolutePath().parent)
    val temp = Files.createTempFile(path.toAbsolutePath().parent, ".mcfpp-write-", ".tmp")
    try {
        Files.write(temp, bytes)
        try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING) }
    } finally { Files.deleteIfExists(temp) }
}
