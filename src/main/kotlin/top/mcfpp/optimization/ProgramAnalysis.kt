package top.mcfpp.optimization

import top.mcfpp.analysis.*

data class FunctionEffects(
    val reads: Set<Place> = emptySet(),
    val writes: Set<Place> = emptySet(),
    val observable: Boolean = false,
    val unknown: Boolean = false,
    val mayReenter: Boolean = false,
)

/** Fixed points on actual bodies, separate from the front-end's no-external-writes summary. */
class ProgramAnalysis(val functions: Map<SymbolId, TypedIR>) {
    val calls = functions.mapValues { (_, ir) -> ir.blocks.flatMap { it.instructions }
        .filterIsInstance<Instruction.Call>().map { it.declaration }.toSet() }
    val recursive: Set<SymbolId>
    val effects: Map<SymbolId, FunctionEffects>
    init {
        val cycles = linkedSetOf<SymbolId>()
        val components = graphComponents(functions.keys) { calls[it].orEmpty() }
        components.filter { it.size > 1 || it.single() in calls[it.single()].orEmpty() }.forEach(cycles::addAll)
        recursive = cycles
        val summaries = functions.mapValues { (id, ir) ->
            val instructions = ir.blocks.flatMap { it.instructions }
            val raw = instructions.filterIsInstance<Instruction.RawCommand>()
            FunctionEffects(instructions.filterIsInstance<Instruction.Read>().map { it.place }.toSet(),
                instructions.filterIsInstance<Instruction.Write>().map { it.place }.toSet(),
                raw.isNotEmpty(), raw.any { EffectAnalysis.rawCommandEffect(it.command) == Effect.Unknown } || calls[id].orEmpty().any { it !in functions },
                id in cycles)
        }.toMutableMap()
        val callers = functions.keys.associateWith { linkedSetOf<SymbolId>() }
        calls.forEach { (id, targets) -> targets.forEach { callers[it]?.add(id) } }
        val pending = ArrayDeque(functions.keys)
        val queued = functions.keys.toMutableSet()
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            queued.remove(id)
            var summary = summaries.getValue(id)
            val reads = summary.reads.toMutableSet()
            val writes = summary.writes.toMutableSet()
            for (target in calls[id].orEmpty()) {
                val callee = summaries[target] ?: FunctionEffects(observable = true, unknown = true, mayReenter = true)
                reads.addAll(callee.reads)
                writes.addAll(callee.writes)
                summary = FunctionEffects(reads, writes,
                    summary.observable || callee.observable, summary.unknown || callee.unknown,
                    summary.mayReenter || callee.mayReenter || callee.unknown)
            }
            if (summary != summaries[id]) {
                summaries[id] = summary
                callers.getValue(id).forEach { if (queued.add(it)) pending.add(it) }
            }
        }
        effects = summaries
    }
    fun reaches(from: SymbolId, target: SymbolId): Boolean {
        val seen = hashSetOf<SymbolId>()
        fun visit(id: SymbolId): Boolean = id == target || seen.add(id) && calls[id].orEmpty().any(::visit)
        return visit(from)
    }
}
