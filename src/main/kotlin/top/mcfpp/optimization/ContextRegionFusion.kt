package top.mcfpp.optimization

import top.mcfpp.Project
import top.mcfpp.core.lang.context.ContextComponents
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope

/** Immutable provenance survives the release of lexical context borrows after compilation. */
internal data class ContextRegionInfo(
    val body: String,
    val parent: ContextComponents,
    val child: ContextComponents,
    val capturesValues: Boolean,
    val localReturn: Boolean,
)

/** Context regions are lifted before command cleanup, and lowered only after frame planning. */
internal sealed interface ContextIR {
    data class Leaf(val instruction: MinecraftInstruction) : ContextIR
    data class Execute(val clauses: List<String>, val body: ContextIR) : ContextIR
    data class Region(val entry: MinecraftInstruction.Call, val info: ContextRegionInfo) : ContextIR
}

/** Shared conservative frame observations for ordinary calls and generated macro bodies. */
internal class MinecraftFrameObservations(functions: Map<String, List<MinecraftInstruction>>) {
    val macroLines = Project.macroFunction.mapValues { (_, body) -> body.lines() }
    val bodies = macroLines.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { (_, lines) ->
        lines.map { MinecraftLowering.lower(it.trimStart().removePrefix("$")) }
    } + functions
    private val calls = bodies.mapValues { (_, body) -> body.flatMap(ContextRegionFusion::calls) }

    fun observers(storage: String, ignore: (MinecraftInstruction) -> Boolean): Set<String> {
        val observed = bodies.filterValues { body -> body.any {
            !ignore(it) && (storage in it.render() || "predicate" in MinecraftLowering.tokens(it.render()))
        } }.keys.toMutableSet()
        do {
            val changed = calls.filter { (id, targets) -> id !in observed && targets.any { it !in bodies || it in observed } }
                .keys.let { observed.addAll(it) }
        } while (changed)
        return observed
    }

    fun frameIndependentCalls(): Set<String> = bodies.keys - observers("stack_frame") { it is MinecraftInstruction.Frame }
}

internal data class ContextFusionResult(
    val functions: Map<String, List<MinecraftInstruction>>,
    val macros: Map<String, String>,
)

internal object ContextRegionFusion {
    fun run(functions: List<Function>, input: Map<String, List<MinecraftInstruction>>): ContextFusionResult {
        val regions = functions.mapNotNull { f -> f.contextRegion?.let { f.namespaceID.toString() to it } }.toMap()
        if (regions.isEmpty()) return ContextFusionResult(input, Project.macroFunction.toMap())
        val observations = MinecraftFrameObservations(input)
        val program = observations.bodies
        // A saved slot contains lexical frame depths. Keep every ancestor frame on a path
        // that can read one, including reads hidden in ordinary functions and macros.
        val contextObserved = observations.observers("mcfpp:context", ::contextFrame)
        val systemObserved = observations.observers("stack_frame") { it is MinecraftInstruction.Frame }
        val frames = Project.config.optimization.enabled(PassIds.FRAMES)
        val planned = program.mapValues { (id, body) ->
            val region = regions[id]
            if (!frames || region == null) body else body.filterNot {
                id !in contextObserved && contextFrame(it) ||
                    id !in systemObserved && !region.capturesValues && it is MinecraftInstruction.Frame
            }
        }
        val output = linkedMapOf<String, List<MinecraftInstruction>>()
        val active = hashSetOf<String>()
        fun lift(i: MinecraftInstruction): ContextIR = when (i) {
            is MinecraftInstruction.Execute -> ContextIR.Execute(i.clauses, lift(i.body))
            is MinecraftInstruction.Call -> regions[i.target]?.takeIf { i.arguments.isEmpty() && !i.returning }
                ?.let { ContextIR.Region(i, it) } ?: ContextIR.Leaf(i)
            else -> ContextIR.Leaf(i)
        }
        lateinit var optimize: (String) -> List<MinecraftInstruction>
        fun lower(node: ContextIR, macro: Boolean): MinecraftInstruction = when (node) {
            is ContextIR.Leaf -> node.instruction
            is ContextIR.Execute -> {
                val body = lower(node.body, macro)
                // Store boundaries can observe a fork's result. Only append clauses after
                // context modifiers/conditions, never after an outer result/success store.
                if (body is MinecraftInstruction.Execute && "store" !in node.clauses)
                    MinecraftInstruction.Execute(node.clauses + body.clauses, body.body)
                else MinecraftInstruction.Execute(node.clauses, body)
            }
            is ContextIR.Region -> {
                val info = node.info
                if (!frames || info.capturesValues || info.body !in input || info.body in active ||
                    node.entry.target in contextObserved || node.entry.target in systemObserved) node.entry
                else {
                    val body = optimize(info.body).filterNot { it is MinecraftInstruction.Comment }
                    // A literal $(...) in an ordinary command must not become a macro
                    // parameter when its callback is inserted into a dynamic entry.
                    if (!info.localReturn && body.size == 1 && !returns(body.single()) &&
                        (!macro || "$(" !in body.single().render())) body.single()
                    else MinecraftInstruction.Call(info.body)
                }
            }
        }
        optimize = { id ->
            output[id] ?: if (!active.add(id)) planned.getValue(id) else {
                val macroLines = observations.macroLines[id.removePrefix("mcfpp:dynamic/")]
                val result = planned.getValue(id).mapIndexed { index, instruction ->
                    val macro = macroLines?.get(index)?.trimStart()?.startsWith('$') == true
                    lower(lift(instruction), macro)
                }
                active.remove(id)
                output[id] = result
                result
            }
        }
        program.keys.forEach { optimize(it) }
        val macros = observations.macroLines.mapValues { (id, lines) ->
            output.getValue("mcfpp:dynamic/$id").mapIndexed { index, instruction ->
                (if (lines[index].trimStart().startsWith('$')) "$" else "") + instruction.render()
            }.joinToString("\n")
        }
        return ContextFusionResult(output.filterKeys { it in input }, macros)
    }

    private fun returns(i: MinecraftInstruction): Boolean = when (i) {
        is MinecraftInstruction.Call -> i.returning
        is MinecraftInstruction.Execute -> returns(i.body)
        else -> i.render().trimStart().startsWith("return ")
    }

    private fun contextFrame(i: MinecraftInstruction): Boolean = i.render() in setOf(
        "execute unless data storage mcfpp:context frames run data modify storage mcfpp:context frames set value []",
        "data modify storage mcfpp:context frames prepend value {}",
        "data remove storage mcfpp:context frames[0]")

    internal fun calls(i: MinecraftInstruction): List<String> {
        if (i is MinecraftInstruction.Comment) return emptyList()
        // Includes execute if/unless function and opaque return-run commands.
        val tokens = MinecraftLowering.tokens(i.render())
        return tokens.zipWithNext().filter { it.first == "function" }.map { it.second }
    }

    /** Only explicitly marked compiler helpers may disappear. User functions remain roots. */
    fun prune(functions: List<Function>, output: Map<String, List<MinecraftInstruction>>) {
        val candidates = functions.filter { it.contextGenerated }.associateBy { it.namespaceID.toString() }
        val live = functions.filterNot { it.contextGenerated }.mapTo(hashSetOf()) { it.namespaceID.toString() }
        val pending = ArrayDeque(live)
        // Include opaque references (macros, raw function commands and resource arguments).
        fun references(text: String) = candidates.keys.filter { it in text }
        Project.macroFunction.values.flatMap(::references).forEach { if (live.add(it)) pending.add(it) }
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            output[id].orEmpty().flatMap { references(it.render()) }.forEach { if (live.add(it)) pending.add(it) }
        }
        val removed = candidates.filterKeys { it !in live }.values.toSet()
        val namespaces = GlobalScope.localNamespaces.values + GlobalScope.stdNamespaces.values + GlobalScope.libNamespaces.values
        removed.forEach { f -> namespaces.forEach { it.scope.removeFunction(f) } }
        functions.forEach { f ->
            f.parent.removeAll(removed)
            f.child.removeAll(removed)
            f.innerFunction.removeAll(removed)
        }
        val kept = candidates.filterKeys { it in live }
        val macroCalls = Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { (_, body) ->
            body.lines().flatMap { calls(MinecraftLowering.lower(it.trimStart().removePrefix("$"))) }
        }
        functions.filterNot { it in removed }.forEach { f ->
            val visited = hashSetOf<String>()
            fun connect(target: String) {
                if (!visited.add(target)) return
                val child = kept[target]
                if (child != null) {
                    if (child !in f.child) f.child.add(child)
                    if (f !in child.parent) child.parent.add(f)
                } else macroCalls[target].orEmpty().forEach(::connect)
            }
            output[f.namespaceID.toString()].orEmpty().flatMap(::calls).forEach(::connect)
        }
    }
}

/** Remove unobserved native CommandResult payloads while retaining the native operation. */
internal object DeadCommandResults {
    fun run(input: List<MinecraftInstruction>, registered: Set<String>, frameIndependentCalls: Set<String>): List<MinecraftInstruction> {
        if (registered.isEmpty()) return input
        val roots = registered.toMutableSet()
        fun address(i: MinecraftInstruction.Data) = i.target.render()
        fun source(i: MinecraftInstruction.Data): String? = if (i.operation == "modify" &&
            i.suffix.take(2) == listOf("set", "from") && i.suffix.size == 5) i.suffix.drop(2).joinToString(" ") else null
        fun temporary(i: MinecraftInstruction.Data) = i.target is MinecraftLocation.FrameSlot &&
            Regex("\\.temp_[0-9]+").matches(i.target.path)
        do {
            val changed = input.filterIsInstance<MinecraftInstruction.Data>().filter { temporary(it) && source(it) in roots }
                .map(::address).let { roots.addAll(it) }
        } while (changed)
        fun mentioned(text: String, root: String): Boolean = Regex(Regex.escape(root) + "(?=$|[.\\[\\s])").containsMatchIn(text)
        fun store(i: MinecraftInstruction): String? {
            if (i !is MinecraftInstruction.Execute) return null
            return registered.firstOrNull { root -> i.clauses == MinecraftLowering.tokens(
                "store result $root.result int 1 store success $root.success byte 1") }
        }
        val live = hashSetOf<String>()
        val copies = mutableListOf<Pair<String, String>>()
        for (i in input) {
            if (ContextRegionFusion.calls(i).any { it !in frameIndependentCalls }) live += roots
            val native = store(i)
            val copy = (i as? MinecraftInstruction.Data)?.let(::source)
            when {
                native != null -> roots.filterTo(live) { mentioned((i as MinecraftInstruction.Execute).body.render(), it) }
                i is MinecraftInstruction.Data && address(i) in roots && copy in roots -> copies += address(i) to copy!!
                i is MinecraftInstruction.Data && address(i) in registered && i.operation == "modify" && i.suffix.take(2) == listOf("set", "value") -> Unit
                i is MinecraftInstruction.Frame || i is MinecraftInstruction.Comment -> Unit
                else -> {
                    roots.filterTo(live) { mentioned(i.render(), it) }
                    // Whole-frame reads expose all of its fields.
                    if (MinecraftLowering.tokens(i.render()).any { it == "stack_frame" || it == "stack_frame[0]" }) live += roots
                }
            }
        }
        do {
            val changed = copies.filter { it.first in live }.map { it.second }.let { live.addAll(it) }
        } while (changed)
        val dead = roots - live
        return input.mapNotNull { i -> when {
            store(i) in dead -> (i as MinecraftInstruction.Execute).body
            i is MinecraftInstruction.Data && address(i) in dead -> null
            else -> i
        } }
    }
}
