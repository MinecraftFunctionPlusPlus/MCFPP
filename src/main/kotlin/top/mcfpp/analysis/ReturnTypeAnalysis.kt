package top.mcfpp.analysis

/** Propagates type evidence across calls, without evaluating an ordinary function with argument values. */
object ReturnTypeAnalysis {
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
        val analysis = if (call.staticParameters.any { call.parameterTypes.getOrNull(it) in
                setOf(top.mcfpp.type.MCFPPBaseType.Any.typeId, top.mcfpp.type.MCFPPBaseType.Object.typeId) })
            analyze(call.declaration, functions, arguments, visiting) else null
        val exits = ir?.blocks?.filter { it.terminator is Terminator.Return }?.mapNotNull { analysis?.exits?.get(it.id) }.orEmpty()
        return call.staticParameters.mapNotNull { index ->
            val place = call.argumentPlaces.getOrNull(index) ?: return@mapNotNull null
            val declared = call.parameterTypes.getOrNull(index)
            val type = if (declared != null && declared !in setOf(top.mcfpp.type.MCFPPBaseType.Any.typeId, top.mcfpp.type.MCFPPBaseType.Object.typeId))
                TypeKnowledge.Exact(declared) else {
                val parameter = ir?.parameters?.getOrNull(index)
                exits.map { exit -> parameter?.let { exit.read(Place(it))?.type } ?: TypeKnowledge.Unknown }
                    .reduceOrNull(TypeKnowledge::join) ?: TypeKnowledge.Unknown
            }
            place to type
        }.toMap()
    }
}
