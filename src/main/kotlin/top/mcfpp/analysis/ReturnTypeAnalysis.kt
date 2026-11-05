package top.mcfpp.analysis

import top.mcfpp.type.TypeId
import top.mcfpp.type.MCFPPBaseType

/** Propagates type and shape evidence across calls, without ordinary argument or return values. */
object ReturnTypeAnalysis {
    data class Summary(val type: TypeKnowledge, val returned: FlowAnalysis.Snapshot? = null,
                       val canReturn: Boolean = true)
    private val erased = setOf(MCFPPBaseType.Any.typeId, MCFPPBaseType.Object.typeId)
    private fun concrete(type: TypeId?) = type?.takeUnless { it in erased }?.let(TypeKnowledge::Exact) ?: TypeKnowledge.Unknown
    fun unknown(call: Instruction.Call) = Summary(concrete(call.returnType))
    fun knowledge(declaration: SymbolId, functions: Map<SymbolId, TypedIR>, arguments: List<TypeKnowledge>): TypeKnowledge {
        val snapshots = arguments.map { type ->
            val root = Place(SymbolId(0))
            FlowAnalysis.Snapshot(root, FlowFacts().apply { write(root, ValueFacts(type, ValueKnowledge.Unknown)) })
        }
        val result = Solver(functions).analyze(declaration, snapshots)?.returned
        return result?.facts?.read(result.place)?.type ?: TypeKnowledge.Unknown
    }

    private fun returned(analysis: FlowAnalysis.Result): FlowAnalysis.Snapshot? {
        val results = analysis.returns.values
        if (results.isEmpty()) return null
        val root = Place(SymbolId(0))
        val facts = results.map { result -> FlowFacts().apply { copyFrom(result.facts, result.place, root) } }
            .reduce(FlowFacts::join)
        return FlowAnalysis.Snapshot(root, shape(facts))
    }

    /** Missing children must not grow indefinitely around a recursive result. */
    private fun shape(facts: FlowFacts) = facts.withoutValues(initializedOnly = true)

    fun summarize(call: Instruction.Call, functions: Map<SymbolId, TypedIR>, arguments: List<FlowAnalysis.Snapshot>): Summary =
        Solver(functions).summarize(call, arguments)

    /** Owned by one immutable module view, never shared across compilation sessions. */
    fun summarizer(functions: Map<SymbolId, TypedIR>): (Instruction.Call, List<FlowAnalysis.Snapshot>) -> Summary {
        val solver = Solver(functions)
        return solver::summarize
    }

    private data class Outcome(val returned: FlowAnalysis.Snapshot?, val canReturn: Boolean) {
        fun join(other: Outcome): Outcome {
            if (!canReturn) return other
            if (!other.canReturn) return this
            val result = if (returned == null || other.returned == null) null else
                FlowAnalysis.Snapshot(returned.place, shape(returned.facts.join(other.returned.facts)))
            return Outcome(result, true)
        }
    }

    private class Solver(val functions: Map<SymbolId, TypedIR>) {
        private class Frame(val initial: FlowFacts) {
            var recursive = false
            var outcome = Outcome(null, false)
        }
        private val active = mutableMapOf<SymbolId, Frame>()
        private data class Input(val declaration: SymbolId, val facts: FlowFacts)
        private val completed = hashMapOf<Input, Outcome>()
        private val cacheable = buildSet {
            val calls = functions.mapValues { (_, ir) -> ir.blocks.flatMap { it.instructions }
                .filterIsInstance<Instruction.Call>().map { it.declaration } }
            for (component in graphComponents(functions.keys) { calls[it].orEmpty() }) {
                val id = component.singleOrNull() ?: continue
                if (id !in calls[id].orEmpty() && calls[id].orEmpty().all { it !in functions || it in this }) add(id)
            }
        }

        fun analyze(declaration: SymbolId, arguments: List<FlowAnalysis.Snapshot>): Outcome? {
            val ir = functions[declaration] ?: return null
            val initial = FlowFacts()
            ir.parameters.forEachIndexed { index, parameter ->
                arguments.getOrNull(index)?.let { initial.copyFrom(shape(it.facts), it.place, Place(parameter)) }
            }
            val key = if (declaration in cacheable) Input(declaration, initial) else null
            key?.let { completed[it]?.let { result -> return result } }
            active[declaration]?.let { frame ->
                // Different recursive input shapes need a separate context analysis. Do not reuse a proof
                // for another payload, and never specialize recursion on ordinary argument values.
                if (frame.initial != initial) return null
                frame.recursive = true
                return frame.outcome
            }
            val frame = Frame(initial)
            active[declaration] = frame
            try {
                while (true) {
                    val analysis = FlowAnalysis.analyze(ir, initial, PrimitiveEvaluation::binary,
                        callSummary = ::summarize)
                    val exits = ir.blocks.filter { it.terminator is Terminator.Return }
                        .mapNotNull { analysis.exits[it.id]?.takeIf(FlowFacts::reachable) }
                    val next = frame.outcome.join(Outcome(returned(analysis), exits.isNotEmpty()))
                    if (!frame.recursive || next == frame.outcome) {
                        if (key != null) completed[key] = next
                        return next
                    }
                    frame.outcome = next
                }
            } finally { active.remove(declaration) }
        }

        fun summarize(call: Instruction.Call, arguments: List<FlowAnalysis.Snapshot>): Summary {
            if (call.declaration !in functions) return unknown(call)
            val outcome = if (call.provisional) null else analyze(call.declaration, arguments)
            if (outcome == null) return unknown(call)
            val result = outcome.returned
            val type = if (call.returnType in erased) result?.facts?.read(result.place)?.type ?: TypeKnowledge.Unknown else concrete(call.returnType)
            return Summary(type, result, outcome.canReturn)
        }
    }
}
