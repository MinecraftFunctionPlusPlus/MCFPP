package top.mcfpp.analysis

import top.mcfpp.type.TypeId
import top.mcfpp.type.MCFPPBaseType

/** Propagates type evidence across calls, without evaluating an ordinary function with argument values. */
object ReturnTypeAnalysis {
    private val erased = setOf(MCFPPBaseType.Any.typeId, MCFPPBaseType.Object.typeId)
    private fun concrete(type: TypeId?) = type?.takeUnless { it in erased }?.let(TypeKnowledge::Exact) ?: TypeKnowledge.Unknown
    private fun element(type: TypeId?, path: List<PathSegment>): TypeKnowledge {
        var current = type
        repeat(path.size) {
            val container = current as? TypeId.Applied ?: return TypeKnowledge.Unknown
            if (container.constructor !in setOf(TypeId.Builtin("list"), TypeId.Builtin("ImmutableList"), TypeId.Builtin("dict"))) return TypeKnowledge.Unknown
            current = container.arguments.single()
        }
        return concrete(current)
    }
    fun knowledge(declaration: SymbolId, functions: Map<SymbolId, TypedIR>, arguments: List<TypeKnowledge>,
                  visiting: Set<SymbolId> = emptySet()): TypeKnowledge {
        val ir = functions[declaration] ?: return TypeKnowledge.Unknown
        val facts = analyze(declaration, functions, arguments, visiting) ?: return TypeKnowledge.Unknown
        val returned = ir.blocks.mapNotNull { block ->
            val exit = facts.exits[block.id] ?: return@mapNotNull null
            val value = (block.terminator as? Terminator.Return)?.value ?: return@mapNotNull null
            when (value) {
                is ValueRef.Read -> exit.read(value.place)?.type ?: TypeKnowledge.Unknown
                is ValueRef.Result -> facts.values[block.id to value.instruction]?.type ?: TypeKnowledge.Unknown
                else -> TypeKnowledge.Exact(value.type)
            }
        }
        return returned.reduceOrNull(TypeKnowledge::join) ?: TypeKnowledge.Unknown
    }

    private fun analyze(declaration: SymbolId, functions: Map<SymbolId, TypedIR>, arguments: List<TypeKnowledge>,
                        visiting: Set<SymbolId>): FlowAnalysis.Result? {
        if (declaration in visiting) return null
        val ir = functions[declaration] ?: return null
        val initial = FlowFacts()
        ir.parameters.forEachIndexed { index, parameter ->
            initial.write(Place(parameter), ValueFacts(arguments.getOrNull(index) ?: TypeKnowledge.Unknown, ValueKnowledge.Unknown))
        }
        return FlowAnalysis.analyze(ir, initial, PrimitiveEvaluation::binary,
            callKnowledge = { call, types -> callKnowledge(call, functions, types, visiting + declaration) },
            callWrites = { call, types -> callWrites(call, functions, types, visiting + declaration) })
    }

    fun callKnowledge(call: Instruction.Call, functions: Map<SymbolId, TypedIR>, arguments: List<TypeKnowledge>,
                      visiting: Set<SymbolId> = emptySet()): TypeKnowledge =
        call.returnType?.takeUnless { it == top.mcfpp.type.MCFPPBaseType.Any.typeId || it == top.mcfpp.type.MCFPPBaseType.Object.typeId }
            ?.let(TypeKnowledge::Exact) ?: if (call.provisional) TypeKnowledge.Unknown else knowledge(call.declaration, functions, arguments, visiting)

    fun callWrites(call: Instruction.Call, functions: Map<SymbolId, TypedIR>, arguments: List<TypeKnowledge>,
                   visiting: Set<SymbolId> = emptySet()): Map<Place, TypeKnowledge> {
        val ir = functions[call.declaration]
        val analysis = if (call.staticParameters.isNotEmpty() && call.effect != Effect.Pure)
            analyze(call.declaration, functions, arguments, visiting) else null
        val exits = ir?.blocks?.filter { it.terminator is Terminator.Return }?.mapNotNull { analysis?.exits?.get(it.id) }.orEmpty()
        val joined = exits.reduceOrNull { a, b -> a.join(b) }
        val written = linkedMapOf<Place, TypeKnowledge>()
        for (index in call.staticParameters) {
            val place = call.argumentPlaces.getOrNull(index) ?: continue
            val parameter = ir?.parameters?.getOrNull(index)
            val declared = call.parameterTypes.getOrNull(index)
            val type = if (declared != null && declared !in setOf(top.mcfpp.type.MCFPPBaseType.Any.typeId, top.mcfpp.type.MCFPPBaseType.Object.typeId))
                TypeKnowledge.Exact(declared) else parameter?.let { joined?.knownTypes()?.get(Place(it)) } ?: TypeKnowledge.Unknown
            written[place] = type
            if (parameter != null) joined?.knownTypes()?.forEach { (source, knowledge) ->
                if (source.root == parameter && source.path.isNotEmpty()) {
                    // An unknown range summarizes possible assignments; the caller joins it with its existing elements.
                    val assigned = if (PathSegment.UnknownIndex in source.path && call.effect is Effect.Writes) {
                        ir.blocks.filter { it.id in analysis!!.entries }.flatMap { it.instructions }
                            .filterIsInstance<Instruction.Write>().filter { it.place == source }
                            .map { concrete(it.value.type) }.reduceOrNull(TypeKnowledge::join) ?: element(declared, source.path)
                    } else knowledge
                    written[Place(place.root, place.path + source.path)] = assigned
                }
            }
        }
        return written
    }
}
