package top.mcfpp.analysis

import top.mcfpp.type.TypeId
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.TypeRelations

/** Propagates type and shape evidence across calls, without ordinary argument or return values. */
object ReturnTypeAnalysis {
    data class Summary(val type: TypeKnowledge, val writes: Map<Place, TypeKnowledge>,
                       val returned: FlowAnalysis.Snapshot? = null, val outputs: FlowFacts = FlowFacts())
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
    fun knowledge(declaration: SymbolId, functions: Map<SymbolId, TypedIR>, arguments: List<TypeKnowledge>,
                  visiting: Set<SymbolId> = emptySet()): TypeKnowledge {
        val snapshots = arguments.map { type ->
            val root = Place(SymbolId(0))
            FlowAnalysis.Snapshot(root, FlowFacts().apply { write(root, ValueFacts(type, ValueKnowledge.Unknown)) })
        }
        val result = analyze(declaration, functions, snapshots, visiting)?.let(::returned)
        return result?.facts?.read(result.place)?.type ?: TypeKnowledge.Unknown
    }

    private fun returned(analysis: FlowAnalysis.Result): FlowAnalysis.Snapshot? {
        val results = analysis.returns.values
        val root = results.firstOrNull()?.place ?: return null
        val facts = results.map { result -> FlowFacts().apply { copyFrom(result.facts, result.place, root) } }
            .reduce(FlowFacts::join)
        return FlowAnalysis.Snapshot(root, facts.withoutValues())
    }

    private fun analyze(declaration: SymbolId, functions: Map<SymbolId, TypedIR>, arguments: List<FlowAnalysis.Snapshot>,
                       visiting: Set<SymbolId>): FlowAnalysis.Result? {
        if (declaration in visiting) return null
        val ir = functions[declaration] ?: return null
        val initial = FlowFacts()
        ir.parameters.forEachIndexed { index, parameter ->
            val argument = arguments.getOrNull(index)
            if (argument == null) initial.write(Place(parameter), ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown))
            else initial.copyFrom(argument.facts.withoutValues(), argument.place, Place(parameter))
        }
        return FlowAnalysis.analyze(ir, initial, PrimitiveEvaluation::binary,
            callSummary = { call, shapes -> summarize(call, functions, shapes, visiting + declaration) })
    }

    fun summarize(call: Instruction.Call, functions: Map<SymbolId, TypedIR>, arguments: List<FlowAnalysis.Snapshot>,
                  visiting: Set<SymbolId> = emptySet()): Summary {
        val ir = functions[call.declaration] ?: return unknown(call)
        val analysis = if (call.provisional) null else analyze(call.declaration, functions, arguments, visiting)
        if (analysis == null) return unknown(call)
        val result = returned(analysis)
        val type = if (call.returnType in erased) result?.facts?.read(result.place)?.type ?: TypeKnowledge.Unknown else concrete(call.returnType)
        val exits = ir.blocks.filter { it.terminator is Terminator.Return }.mapNotNull { analysis.exits[it.id] }
        val joined = exits.reduceOrNull { a, b -> a.join(b) }
        val written = linkedMapOf<Place, TypeKnowledge>()
        val outputs = FlowFacts()
        for (index in call.staticParameters) {
            val place = call.argumentPlaces.getOrNull(index) ?: continue
            val parameter = ir.parameters.getOrNull(index)
            val declared = call.parameterTypes.getOrNull(index)
            val assignedType = if (declared != null && declared !in erased)
                TypeKnowledge.Exact(declared) else parameter?.let { joined?.knownTypes()?.get(Place(it)) } ?: TypeKnowledge.Unknown
            written[place] = assignedType
            if (parameter != null && joined != null) outputs.copyFrom(joined.withoutValues(), Place(parameter), place)
            if (parameter != null) joined?.knownTypes()?.forEach { (source, knowledge) ->
                if (source.root == parameter && source.path.isNotEmpty()) {
                    // An unknown range summarizes possible assignments; the caller joins it with its existing elements.
                    val assigned = if (PathSegment.UnknownIndex in source.path && call.effect is Effect.Writes) {
                        ir.blocks.filter { it.id in analysis.entries }.flatMap { it.instructions }
                            .filterIsInstance<Instruction.Write>().filter { it.place == source }
                            .map { concrete(it.value.type) }.reduceOrNull(TypeKnowledge::join) ?: element(declared, source.path)
                    } else knowledge
                    written[Place(place.root, place.path + source.path)] = assigned
                }
            }
        }
        return Summary(type, written, result, outputs)
    }
}
