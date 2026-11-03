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
        val stack = mutableListOf<SymbolId>()
        val indices = hashMapOf<SymbolId, Int>()
        val low = hashMapOf<SymbolId, Int>()
        var next = 0
        fun visit(id: SymbolId) {
            indices[id] = next; low[id] = next++
            stack.add(id)
            for (target in calls[id].orEmpty().filter { it in functions }) {
                if (target !in indices) { visit(target); low[id] = minOf(low.getValue(id), low.getValue(target)) }
                else if (target in stack) low[id] = minOf(low.getValue(id), indices.getValue(target))
            }
            if (low[id] == indices[id]) {
                val component = mutableListOf<SymbolId>()
                do { val member = stack.removeAt(stack.lastIndex); component.add(member) } while (member != id)
                if (component.size > 1 || id in calls[id].orEmpty()) cycles.addAll(component)
            }
        }
        functions.keys.filter { it !in indices }.forEach { if (it !in indices) visit(it) }
        recursive = cycles
        val summaries = functions.mapValues { (id, ir) ->
            val instructions = ir.blocks.flatMap { it.instructions }
            val raw = instructions.filterIsInstance<Instruction.RawCommand>()
            FunctionEffects(instructions.filterIsInstance<Instruction.Read>().map { it.place }.toSet(),
                instructions.filterIsInstance<Instruction.Write>().map { it.place }.toSet(),
                raw.isNotEmpty(), raw.any { EffectAnalysis.rawCommandEffect(it.command) == Effect.Unknown } || calls[id].orEmpty().any { it !in functions },
                id in cycles)
        }.toMutableMap()
        do {
            var changed = false
            for (id in functions.keys) {
                var summary = summaries.getValue(id)
                for (target in calls[id].orEmpty()) {
                    val callee = summaries[target] ?: FunctionEffects(observable = true, unknown = true, mayReenter = true)
                    summary = FunctionEffects(summary.reads + callee.reads, summary.writes + callee.writes,
                        summary.observable || callee.observable, summary.unknown || callee.unknown,
                        summary.mayReenter || callee.mayReenter || callee.unknown)
                }
                if (summary != summaries[id]) { summaries[id] = summary; changed = true }
            }
        } while (changed)
        effects = summaries
    }
    fun reaches(from: SymbolId, target: SymbolId): Boolean {
        val seen = hashSetOf<SymbolId>()
        fun visit(id: SymbolId): Boolean = id == target || seen.add(id) && calls[id].orEmpty().any(::visit)
        return visit(from)
    }
}
