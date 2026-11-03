package top.mcfpp.io

import top.mcfpp.Project
import top.mcfpp.model.Native
import top.mcfpp.model.scope.GlobalScope
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

/** Link generated commands and resource references into one self-contained datapack. */
internal object DatapackLinker {
    data class Plan(val retained: Map<String, String>, val omitted: Set<String>)
    private val resourceId = Regex("#?[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val dynamicFunction = Regex("\\bfunction\\s+([^\\s]+\\$\\([^\\s]*)")

    fun libraryResources(): Map<String, ByteArray> {
        if (!Project.config.copyImport || Project.modules.isEmpty()) return emptyMap()
        val staging = Files.createTempDirectory("mcfpp-link-")
        return try {
            Project.modules.forEach { it.extract(staging) }
            read(staging)
        } finally {
            Files.walk(staging).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    private fun read(root: Path): Map<String, ByteArray> {
        if (!Files.isDirectory(root)) return emptyMap()
        return Files.walk(root).use { paths -> paths.filter(Files::isRegularFile).sorted().toList()
            .associate { root.relativize(it).joinToString("/") to Files.readAllBytes(it) } }
    }

    private fun id(path: String): String? {
        val parts = path.split('/')
        if (parts.size < 3) return null
        val tag = parts[1] == "tags"
        val name = parts.drop(if (tag) 3 else 2).joinToString("/").substringBeforeLast('.')
        return (if (tag) "#" else "") + parts[0] + ":" + name
    }

    internal fun merge(libraries: Map<String, ByteArray>, generated: Map<String, ByteArray>): Map<String, ByteArray> =
        libraries.toMutableMap().apply {
            generated.forEach { (path, bytes) ->
                val inherited = libraries[path]
                val combined = if (inherited != null && "/tags/" in path && path.endsWith(".json")) runCatching {
                    val lower = com.alibaba.fastjson2.JSON.parseObject(inherited)
                    val upper = com.alibaba.fastjson2.JSON.parseObject(bytes)
                    if (upper.getBooleanValue("replace")) bytes else {
                        val values = (lower.getJSONArray("values").orEmpty() + upper.getJSONArray("values").orEmpty())
                            .distinctBy { com.alibaba.fastjson2.JSON.toJSONString(it) }
                        upper["values"] = values
                        if (lower.getBooleanValue("replace")) upper["replace"] = true
                        com.alibaba.fastjson2.JSON.toJSONBytes(upper)
                    }
                }.getOrNull() else null
                put(path, combined ?: bytes)
            }
        }

    fun resolve(resources: Map<String, ByteArray>, roots: Map<String, String>): Plan {
        val byId = resources.keys.groupBy(::id)
        val retained = linkedMapOf<String, String>()
        val pending = ArrayDeque<Pair<String, String>>()
        roots.forEach { (path, reason) -> pending.add(path to reason) }
        while (pending.isNotEmpty()) {
            val (path, reason) = pending.removeFirst()
            if (path !in resources || retained.putIfAbsent(path, reason) != null) continue
            if (!path.endsWith(".mcfunction") && !path.endsWith(".json")) continue
            val text = resources.getValue(path).toString(Charsets.UTF_8).lineSequence()
                .filterNot { it.trimStart().startsWith('#') }.joinToString("\n")
            resourceId.findAll(text).forEach { match ->
                byId[match.value].orEmpty().forEach { dependency -> pending.add(dependency to "referenced by $path") }
            }
            dynamicFunction.findAll(text).forEach { match ->
                val prefix = match.groupValues[1].substringBefore("$(")
                // An open runtime target cannot be pruned statically. Keep its matching function family.
                resources.keys.filter { it.endsWith(".mcfunction") && id(it)?.startsWith(prefix) == true }
                    .forEach { pending.add(it to "dynamic function target in $path") }
            }
        }
        return Plan(retained, resources.keys - retained.keys)
    }

    /** Demand before startup commands are appended, so initialization cannot make itself reachable. */
    fun runtimeCommands(): String {
        val resources = libraryResources().toMutableMap()
        val roots = linkedMapOf<String, String>()
        for (event in listOf("load", "tick")) {
            val path = "minecraft/tags/function/$event.json"
            if (path in resources) roots[path] = "library lifecycle tag"
        }
        FunctionInventory.all().filterNot { it is Native }.forEach { function ->
            val target = function.namespaceID
            val path = "${target.namespace}/function/${target.identifier}.mcfunction"
            if (function.commands.isNotEmpty()) resources[path] = function.cmdStr.toByteArray()
            if (function.namespace in GlobalScope.localNamespaces) roots[path] = "project function"
        }
        for (tag in GlobalScope.functionTags.values) {
            val path = "${tag.namespace}/tags/function/${tag.identifier}.json"
            resources[path] = merge(resources, mapOf(path to tag.tagJSON.toByteArray())).getValue(path)
            roots[path] = "project event tag"
        }
        val raw = PackAssembly.read(Project.config.dataPath) + PackEntrypoints.resources()
        resources.putAll(merge(resources, raw))
        raw.keys.forEach { roots[it] = "project resource or exported entrypoint" }
        val plan = resolve(resources, roots)
        return plan.retained.keys.filter { it.endsWith(".mcfunction") }
            .joinToString("\n") { resources.getValue(it).toString(Charsets.UTF_8) }
    }

    fun link(data: Path): Plan {
        val generated = read(data)
        val resources = merge(libraryResources(), generated)
        val userResources = Project.config.dataPath?.let(::read).orEmpty().keys
        val roots = generated.keys.filter { path ->
            path in userResources || path in PackEntrypoints.resources() || path.substringBefore('/') in GlobalScope.localNamespaces || "/tags/" in path
        }.associateWith { if (it in userResources) "project resource" else "project function or event tag" }
        val plan = resolve(resources, roots)
        generated.keys.filter { it !in plan.retained }.forEach { Files.delete(data.resolve(it)) }
        plan.retained.keys.forEach { path ->
            val output = data.resolve(path).normalize()
            require(output.startsWith(data.normalize())) { "Invalid datapack resource path: $path" }
            Files.createDirectories(output.parent)
            Files.write(output, resources.getValue(path))
        }
        Files.walk(data).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { directory ->
            if (directory != data && Files.isDirectory(directory)) Files.list(directory).use { if (!it.findAny().isPresent) Files.delete(directory) }
        } }
        IrTextDump.link(plan)
        return plan
    }
}
