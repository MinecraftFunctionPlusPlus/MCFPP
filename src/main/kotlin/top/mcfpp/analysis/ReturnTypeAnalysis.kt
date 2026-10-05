package top.mcfpp.analysis

import top.mcfpp.type.TypeId
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.TypeRelations

/** Propagates type and shape evidence across calls, without ordinary argument or return values. */
object ReturnTypeAnalysis {
    data class Summary(val type: TypeKnowledge, val writes: Map<Place, TypeKnowledge>,
                       val returned: FlowAnalysis.Snapshot? = null, val outputs: FlowFacts = FlowFacts(),
                       val canReturn: Boolean = true)
    private val erased = setOf(MCFPPBaseType.Any.typeId, MCFPPBaseType.Object.typeId)
    private fun concrete(type: TypeId?) = type?.takeUnless { it in erased }?.let(TypeKnowledge::Exact) ?: TypeKnowledge.Unknown
    fun unknown(call: Instruction.Call) = Summary(concrete(call.returnType), call.staticParameters.mapNotNull { index ->
        call.argumentPlaces.getOrNull(index)?.let { it to concrete(call.parameterTypes.getOrNull(index)) }
    }.toMap())
    private fun element(type: TypeId?, path: List<PathSegment>): TypeKnowledge {
        var current = type
        repeat(path.size) {
            val arrayElement = current?.let(TypeRelations::arrayElementType)
            if (arrayElement != null) { current = arrayElement.typeId; return@repeat }
            val container = current as? TypeId.Applied ?: return TypeKnowledge.Unknown
            if (container.constructor !in setOf(TypeId.Builtin("list"), TypeId.Builtin("ImmutableList"), TypeId.Builtin("dict"))) return TypeKnowledge.Unknown
            current = container.arguments.single()
        }
        return concrete(current)
    }
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

    private data class Outcome(val returned: FlowAnalysis.Snapshot?, val outputs: FlowFacts, val canReturn: Boolean,
                               val reachable: Set<Int> = emptySet()) {
        fun join(other: Outcome): Outcome {
            if (!canReturn) return other
            if (!other.canReturn) return this
            val result = if (returned == null || other.returned == null) null else
                FlowAnalysis.Snapshot(returned.place, shape(returned.facts.join(other.returned.facts)))
            return Outcome(result, shape(outputs.join(other.outputs)), true, reachable + other.reachable)
        }
    }

    private class Solver(val functions: Map<SymbolId, TypedIR>) {
        private class Frame(val initial: FlowFacts) {
            var recursive = false
            var outcome = Outcome(null, FlowFacts(), false)
        }
        private val active = mutableMapOf<SymbolId, Frame>()

        fun analyze(declaration: SymbolId, arguments: List<FlowAnalysis.Snapshot>): Outcome? {
            val ir = functions[declaration] ?: return null
            val initial = FlowFacts()
            ir.parameters.forEachIndexed { index, parameter ->
                arguments.getOrNull(index)?.let { initial.copyFrom(shape(it.facts), it.place, Place(parameter)) }
            }
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
                    val outputs = FlowFacts()
                    exits.reduceOrNull(FlowFacts::join)?.let { joined ->
                        ir.externalRoots.forEach { outputs.copyFrom(shape(joined), Place(it), Place(it)) }
                    }
                    val next = frame.outcome.join(Outcome(returned(analysis), outputs, exits.isNotEmpty(), analysis.entries.keys))
                    if (!frame.recursive || next == frame.outcome) return next
                    frame.outcome = next
                }
            } finally { active.remove(declaration) }
        }

        fun summarize(call: Instruction.Call, arguments: List<FlowAnalysis.Snapshot>): Summary {
            val ir = functions[call.declaration] ?: return unknown(call)
            val outcome = if (call.provisional) null else analyze(call.declaration, arguments)
            if (outcome == null) return unknown(call)
            val result = outcome.returned
            val type = if (call.returnType in erased) result?.facts?.read(result.place)?.type ?: TypeKnowledge.Unknown else concrete(call.returnType)
            val joined = outcome.outputs
            val written = linkedMapOf<Place, TypeKnowledge>()
            val outputs = FlowFacts()
            for (index in call.staticParameters) {
                val place = call.argumentPlaces.getOrNull(index) ?: continue
                val parameter = ir.parameters.getOrNull(index)
                val declared = call.parameterTypes.getOrNull(index)
                val assignedType = if (declared != null && declared !in erased)
                    TypeKnowledge.Exact(declared) else parameter?.let { joined.knownTypes()[Place(it)] } ?: TypeKnowledge.Unknown
                written[place] = assignedType
                if (parameter != null) outputs.copyFrom(joined, Place(parameter), place)
                if (parameter != null) joined.knownTypes().forEach { (source, knowledge) ->
                    if (source.root == parameter && source.path.isNotEmpty()) {
                        // An unknown range summarizes possible assignments; the caller joins it with its existing elements.
                        val assigned = if (PathSegment.UnknownIndex in source.path && call.effect is Effect.Writes) {
                            ir.blocks.filter { it.id in outcome.reachable }.flatMap { it.instructions }
                                .filterIsInstance<Instruction.Write>().filter { it.place == source }
                                .map { concrete(it.value.type) }.reduceOrNull(TypeKnowledge::join) ?: element(declared, source.path)
                        } else knowledge
                        written[Place(place.root, place.path + source.path)] = assigned
                    }
                }
            }
            return Summary(type, written, result, outputs, outcome.canReturn)
        }
    }
}
