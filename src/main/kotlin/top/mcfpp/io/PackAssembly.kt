package top.mcfpp.io

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.api.resources.PackResources
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

/** Assemble in a staging directory. A failed resource check keeps the previous installable packs. */
internal object PackAssembly {
    fun read(root: Path?): Map<String, ByteArray> {
        if (root == null || !Files.isDirectory(root)) return emptyMap()
        return Files.walk(root).use { paths -> paths.filter(Files::isRegularFile).sorted().toList()
            .associate { root.relativize(it).joinToString("/") to Files.readAllBytes(it) } }
    }

    fun mergeData(data: Path) {
        val generated = read(data) + PackEntrypoints.resources().also { entries ->
            entries.keys.forEach { require(!Files.exists(data.resolve(it))) { "Entrypoint conflicts with generated resource: $it" } }
        }
        val user = read(Project.config.dataPath)
        val libraries = DatapackLinker.libraryResources()
        val result = generated.toMutableMap()
        user.forEach { (path, bytes) ->
            val lower = generated[path] ?: libraries[path]
            if (lower != null && "/tags/" in path && path.endsWith(".json")) {
                val upper = JSON.parseObject(bytes)
                val required = JSON.parseObject(lower).getJSONArray("values").orEmpty()
                upper["values"] = (required + upper.getJSONArray("values").orEmpty()).distinctBy(JSON::toJSONString)
                result[path] = JSON.toJSONBytes(upper)
            } else {
                require(lower == null || path in Project.config.resourceOverrides) {
                    "Resource conflicts with compiler output: $path. Rename it or explicitly list it in resourceOverrides."
                }
                require(path !in PackEntrypoints.resources()) { "Cannot replace exported entrypoint: $path" }
                result[path] = bytes
            }
        }
        result.forEach { (relative, bytes) ->
            val file = data.resolve(relative)
            Files.createDirectories(file.parent); Files.write(file, bytes)
        }
    }

    fun validate(data: Path, assets: Path?) {
        val definitions = linkedSetOf<PackResources.Key>()
        val ownNamespaces = linkedSetOf<String>()
        fun collect(root: Path?, client: Boolean) {
            read(root).keys.forEach { relative ->
                val file = root!!.resolve(relative)
                PackResources.definition(root, file, client)?.let { definitions += it; ownNamespaces += it.id().substringBefore(':') }
                if (client && relative.endsWith("/sounds.json")) {
                    definitions += PackResources.sounds(relative.substringBefore('/'), Files.readString(file)).keys
                }
            }
        }
        collect(data, false); collect(assets, true)
        val errors = arrayListOf<String>()
        fun check(root: Path?, client: Boolean) {
            read(root).forEach { (relative, bytes) ->
                if (!relative.endsWith(".json") && !relative.endsWith(".mcfunction")) return@forEach
                val file = root!!.resolve(relative)
                val text = bytes.toString(Charsets.UTF_8)
                if (relative.endsWith(".json")) require(JSON.isValid(text)) { "Invalid JSON resource: $file" }
                val owner = PackResources.definition(root, file, client)
                val refs = if (client && relative.endsWith("/sounds.json")) PackResources.soundReferences(text)
                    else if (owner != null) PackResources.references(text, owner, false) else emptyList()
                refs.filter { !it.optional() && it.key().id().substringBefore(':') != "minecraft" &&
                    it.key().id().substringBefore(':') in ownNamespaces && it.key() !in definitions }.forEach {
                    errors += "$file:${text.take(it.start()).count { c -> c == '\n' } + 1}: missing ${it.key().kind()} ${it.key().id()}"
                }
            }
        }
        // Compiler commands may legitimately address dynamic or externally supplied resources.
        check(Project.config.dataPath, false); check(assets, true)
        require(errors.isEmpty()) { errors.distinct().joinToString("\n") }
    }

    fun resourceMetadata(version: String, description: String): String {
        // Resource and data pack versions are independent. Values follow Mojang release notes.
        val format: Any = when (version) {
            "26.3" -> listOf(97, 1); "26.2" -> listOf(88, 0); "26.1" -> listOf(84, 0)
            "1.21.7", "1.21.8" -> 64; "1.21.6" -> 63; "1.21.5" -> 55; "1.21.4" -> 46
            "1.21.2", "1.21.3" -> 42; "1.21", "1.21.1" -> 34
            "1.20.5", "1.20.6" -> 32; "1.20.3", "1.20.4" -> 22; "1.20.2" -> 18; "1.20", "1.20.1" -> 15
            "1.19.4" -> 13; "1.19.3" -> 12; "1.19", "1.19.1", "1.19.2" -> 9
            "1.18", "1.18.1", "1.18.2" -> 8; "1.17", "1.17.1" -> 7
            "1.16.2", "1.16.3", "1.16.4", "1.16.5" -> 6
            "1.15", "1.15.1", "1.15.2", "1.16", "1.16.1" -> 5
            else -> { require(top.mcfpp.command.TargetCapabilities.forVersion(version) != null) { "Unsupported version: $version" }; 4 }
        }
        val pack = linkedMapOf<String, Any>("description" to description)
        if (format is List<*>) { pack["min_format"] = format; pack["max_format"] = format } else pack["pack_format"] = format
        return JSON.toJSONString(mapOf("pack" to pack))
    }

    fun publish(output: Path, stage: Path, names: List<String>) {
        val backup = Files.createTempDirectory(output, ".mcfpp-previous-")
        val installed = arrayListOf<String>()
        val saved = arrayListOf<String>()
        try {
            names.forEach { name ->
                val destination = output.resolve(name)
                if (Files.exists(destination)) { Files.move(destination, backup.resolve(name)); saved += name }
                if (Files.exists(stage.resolve(name))) { Files.move(stage.resolve(name), destination); installed += name }
            }
        } catch (failure: Exception) {
            var restored = true
            installed.forEach { name -> runCatching { delete(output.resolve(name)) }.onFailure { restored = false; failure.addSuppressed(it) } }
            saved.forEach { name -> runCatching { Files.move(backup.resolve(name), output.resolve(name)) }.onFailure { restored = false; failure.addSuppressed(it) } }
            if (restored) delete(backup)
            throw failure
        }
        delete(backup)
    }

    fun delete(path: Path) {
        if (Files.exists(path)) Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}
