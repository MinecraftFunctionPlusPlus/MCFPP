package top.mcfpp.debug

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.Project
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.command.Command
import top.mcfpp.io.FunctionInventory
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.InternalFunction
import top.mcfpp.model.function.NoStackFunction
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

data class DebugVariable(val name: String, val type: String, val kind: String,
    val source: String? = null, val path: String? = null, val player: String? = null,
    val objective: String? = null, val value: String? = null, val savedPath: String? = null) : Serializable

data class DebugExpansion(val name: String, val file: String, val line: Int, val column: Int) : Serializable

data class DebugPoint(val file: String, val line: Int, val column: Int, val endLine: Int,
    val endColumn: Int, val statement: String, val variables: List<DebugVariable>,
    val expansion: List<DebugExpansion> = emptyList(), val scope: String? = null,
    val logicalFunction: String? = null) : Serializable

/** Immutable source facts captured before output rendering. No parser objects enter the sidecar. */
object DebugMetadata {
    fun source(function: Function, context: ParserRuleContext, variables: List<DebugVariable> = emptyList()): DebugPoint? {
        if (!Project.config.debugInfo) return null
        val file = function.declarationFile ?: MCFPPFile.currFile ?: return null
        val path = file.toPath().toAbsolutePath().normalize()
        if (!Files.isRegularFile(path)) return null
        val first = context.start ?: return null
        val last = context.stop ?: first
        val scope = generateSequence(context as ParserRuleContext?) { it.parent as? ParserRuleContext }
            .firstOrNull { it is top.mcfpp.antlr.mcfppParser.BlockContext } ?: context
        val logical = generateSequence(function) { current ->
            if (current is NoStackFunction || current is InternalFunction) current.parent.firstOrNull() else null
        }.last()
        return DebugPoint(path.toString(), first.line, first.charPositionInLine + 1, last.line,
            last.charPositionInLine + (last.text?.length ?: 0) + 1, "${path}:${first.startIndex}", variables,
            scope = "$path:${scope.start.startIndex}", logicalFunction = logical.namespaceID.toString())
    }

    fun variable(name: String, type: String, layout: StorageLayout?, savedPath: String? = null): DebugVariable = when (layout) {
        is StorageLayout.Scoreboard -> DebugVariable(name, type, "score", player = layout.player, objective = layout.objective, savedPath = savedPath)
        is StorageLayout.Nbt -> DebugVariable(name, type, "nbt", source = layout.source, path = layout.path, savedPath = savedPath)
        else -> DebugVariable(name, type, "unavailable", value = "No readable runtime location")
    }

    fun capture(): DebugPoint? {
        if (!Project.config.debugInfo) return null
        val function = Function.currFunction
        val statement = Project.ctx.firstOrNull { it is top.mcfpp.antlr.mcfppParser.StatementContext }
            ?: return null
        val variables = top.mcfpp.analysis.StorageAccess.visibleValues(Function.currField).filter { !it.isTemp && !it.identifier.startsWith('$') }.map { value ->
            val binding = value.storageBinding
            val layout = if (binding == null) null else if (binding.place == binding.data.root) binding.data.layout
                else StorageLayout.Nbt(binding.path.source.toCommand().toString(), binding.path.pathToCommandPart().toString())
            val physical = if (layout is StorageLayout.Nbt && binding != null)
                StorageLayout.Nbt(binding.path.source.toCommand().toString(), binding.path.pathToCommandPart().toString()) else layout
            val name = value.symbol?.name ?: value.identifier
            val constant = if (value.isConst) top.mcfpp.analysis.StorageAccess.snapshot(value) else null
            if (constant != null) DebugVariable(name, value.type.typeName, "constant",
                value = constantJson(constant))
            else variable(name, value.type.typeName, physical, binding?.path?.toCommandPart()?.toString())
        }.distinctBy { it.name }
        return source(function, statement, variables)
    }

    internal fun expanded(site: top.mcfpp.mni.expand.PlanSite): DebugPoint? {
        val current = capture()
        val span = site.generatedFrom?.takeIf { runCatching { Files.isRegularFile(Path.of(it.file)) }.getOrDefault(false) } ?: site.callSite
        if (!runCatching { Files.isRegularFile(Path.of(span.file)) }.getOrDefault(false)) return current
        val file = Path.of(span.file).toAbsolutePath().normalize().toString()
        val statement = current?.takeIf { it.file == file && span.startLine >= it.line && span.endLine <= it.endLine }?.statement
            ?: "$file:${span.startOffset}"
        return DebugPoint(file, span.startLine, span.startColumn, span.endLine, span.endColumn,
            statement, current?.variables.orEmpty(), site.frames.map { DebugExpansion(it.macroName, it.callSite.file, it.callSite.startLine, it.callSite.startColumn) },
            current?.scope, current?.logicalFunction)
    }

    fun hash(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    fun constantJson(value: CompilerValue): String {
        fun data(value: CompilerValue): Any? = when (value) {
            CompilerValue.NullValue -> null
            is CompilerValue.Integral -> value.value
            is CompilerValue.FloatBits -> Float.fromBits(value.bits)
            is CompilerValue.DoubleBits -> Double.fromBits(value.bits)
            is CompilerValue.Bool -> value.value
            is CompilerValue.Text -> value.value
            is CompilerValue.TypeValue -> value.id.toString()
            is CompilerValue.Nbt -> runCatching { JSON.parse(value.snbt) }.getOrElse { value.snbt }
            is CompilerValue.Sequence -> value.elements.map(::data)
            is CompilerValue.Record -> value.fields.mapValues { data(it.value) }
            is CompilerValue.Typed -> data(value.payload)
        }
        return JSON.toJSONString(data(value))
    }

    fun write(output: Path) {
        if (!Project.config.debugInfo) return
        val pack = output.resolve(Project.config.name)
        val sources = linkedMapOf<String, String>()
        val functions = linkedMapOf<String, Any>()
        val macroPoints = linkedMapOf<String, DebugPoint>()
        for (function in FunctionInventory.all()) {
            val id = function.namespaceID.toString()
            val relative = "data/${function.namespaceID.namespace}/function/${function.namespaceID.identifier}.mcfunction"
            val file = pack.resolve(relative)
            if (!Files.isRegularFile(file)) continue
            val matchesOutput = Files.readString(file).replace("\r\n", "\n").trimEnd() == function.cmdStr.replace("\r\n", "\n").trimEnd()
            val entries = mutableListOf<Map<String, Any>>()
            var line = 1
            var previous: String? = null
            for (command in function.commands) {
                if (!matchesOutput) break // A resource override replaced this compiler body during publication.
                val point = command.debugPoint
                for (text in command.toString().split('\n')) {
                    if (point != null && text.isNotBlank() && !text.trimStart().startsWith('#')) {
                        entries.add(mapOf("line" to line, "entry" to (previous != point.statement), "point" to point))
                        previous = point.statement
                        if (Files.isRegularFile(Path.of(point.file))) sources[point.file] = hash(Path.of(point.file))
                        Regex("\\bfunction\\s+(mcfpp:dynamic/[a-zA-Z0-9_./-]+)").find(text)?.groupValues?.get(1)?.let { macroPoints[it] = point }
                    }
                    line++
                }
            }
            functions[id] = mapOf("file" to relative, "hash" to hash(file), "name" to function.identifier,
                "entry" to (function.ownerType == Function.Companion.OwnerType.NONE && function.normalParams.isEmpty() && function !is NoStackFunction && function !is InternalFunction),
                "helper" to (function is NoStackFunction || function is InternalFunction), "locations" to entries,
                "calls" to Regex("\\bfunction\\s+([a-z0-9_.-]+:[a-zA-Z0-9_./-]+)").findAll(Files.readString(file)).map { it.groupValues[1] }.distinct().toList())
        }
        for ((id, point) in macroPoints) {
            val relative = "data/mcfpp/function/${id.substringAfter(':')}.mcfunction"
            val file = pack.resolve(relative)
            if (!Files.isRegularFile(file)) continue
            functions[id] = mapOf("file" to relative, "hash" to hash(file), "name" to id, "helper" to true,
                "locations" to Files.readAllLines(file).mapIndexedNotNull { index, text ->
                    if (text.isBlank() || text.startsWith('#')) null else mapOf("line" to index + 1, "entry" to false, "point" to point)
                })
        }
        val artifacts = Files.walk(pack).use { paths -> paths.filter { Files.isRegularFile(it) }.sorted().toList()
            .associate { pack.relativize(it).toString().replace('\\', '/') to hash(it) } }
        val data = mapOf("schema" to 1, "version" to Project.config.version, "pack" to pack.toAbsolutePath().normalize().toString(),
            "sources" to sources, "functions" to functions, "artifacts" to artifacts)
        Files.write(output.resolve("mcfpp.debug.json"), JSON.toJSONBytes(data, JSONWriter.Feature.PrettyFormat))
    }
}
