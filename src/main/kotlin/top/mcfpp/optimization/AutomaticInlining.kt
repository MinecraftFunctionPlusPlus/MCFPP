package top.mcfpp.optimization

import top.mcfpp.analysis.*

/** Cost providers can be replaced by a measured backend simulator without changing this pass. */
fun interface InlineCostEstimator {
    fun cost(ir: TypedIR, model: CommandCostModel): Double
}

internal class AutomaticInlining(
    private val graph: Map<SymbolId, TypedIR>,
    private val observable: Set<SymbolId>,
    private val estimator: InlineCostEstimator,
    private val displayNames: Map<SymbolId, String> = emptyMap(),
) : FunctionPass {
    override val id = PassIds.INLINE
    override val requires = setOf(AnalysisKind.CALL_GRAPH, AnalysisKind.EFFECTS)
    override fun run(ir: TypedIR, context: PassContext): TypedIR {
        val analysis = ProgramAnalysis(graph)
        val prepared = mutableMapOf<SymbolId, TypedIR>()
        fun eligible(body: TypedIR) = body.blocks.none { block -> block.instructions.any {
            it is Instruction.RawCommand && EffectAnalysis.rawCommandEffect(it.command) == Effect.Unknown
        } }
        fun optimize(input: TypedIR, localContext: PassContext): TypedIR {
            var current = input
            // Every inserted callee is prepared bottom-up. Recursive SCCs are never unfolded.
            val sites = input.blocks.flatMap { b -> b.instructions.filterIsInstance<Instruction.Call>().map { b.id to it } }
            for ((_, call) in sites) {
                if (current.blocks.none { call in it.instructions }) continue
                val callee = graph[call.declaration] ?: continue
                if (call.provisional || call.declaration in analysis.recursive || !eligible(callee)) continue
                val body = prepared.getOrPut(call.declaration) {
                    val initial = FlowFacts().apply { callee.parameters.forEach { parameter ->
                        val type = callee.declarations.getValue(parameter).declaredType
                        write(Place(parameter), ValueFacts(if (type in setOf(top.mcfpp.type.MCFPPBaseType.Any.typeId,
                            top.mcfpp.type.MCFPPBaseType.Object.typeId)) TypeKnowledge.Unknown else TypeKnowledge.Exact(type), ValueKnowledge.Unknown))
                    } }
                    optimize(callee, PassContext(localContext.config, initial, localContext.module, trace = localContext.trace))
                }
                val trial = splice(current, call, body)
                val cleaned = PassManager(scalarPasses(observable + input.parameters)).simplify(trial, localContext)
                val before = estimator.cost(current, localContext.config.costs)
                val after = estimator.cost(cleaned, localContext.config.costs)
                localContext.trace("${PassIds.INLINE}: ${displayNames[call.declaration] ?: "declaration ${call.declaration.value}"} " +
                    "estimated=$before->$after ${if (after < before) "accepted" else "retained"}", current, cleaned)
                if (after < before) current = cleaned
            }
            return current
        }
        return optimize(ir, context)
    }

    private fun splice(caller: TypedIR, call: Instruction.Call, callee: TypedIR): TypedIR {
        val site = caller.blocks.first { call in it.instructions }
        val position = site.instructions.indexOf(call)
        var nextBlock = (caller.blocks.maxOfOrNull { it.id } ?: 0) + 1
        var nextResult = (caller.blocks.flatMap { it.instructions }.mapNotNull { it.resultId() }.maxOrNull() ?: -1) + 1
        val blocks = callee.blocks.associate { it.id to nextBlock++ }
        val continuation = nextBlock
        val results = callee.blocks.flatMap { it.instructions }.mapNotNull { it.resultId() }.associateWith { nextResult++ }
        val symbols = callee.declarations.filterKeys { it !in callee.externalRoots }.mapValues { (_, symbol) ->
            val id = SymbolId.fresh()
            symbol.copy(id = id, name = "\$inline_${id.value}_${symbol.name}")
        }
        val rewrite = object : IRTransform() {
            override fun symbol(value: SymbolId) = symbols[value]?.id ?: value
            override fun declaration(value: Symbol) = symbols[value.id] ?: value
            override fun result(value: Int) = results.getValue(value)
            override fun block(value: Int) = blocks.getValue(value)
        }
        val returnSymbol = call.returnType?.takeIf { call.result != null }?.let {
            Symbol(SymbolId.fresh(), "\$inline_return_${call.result}", it, mutable = true)
        }
        val returnPlace = call.resultPlace ?: returnSymbol?.let { Place(it.id) }
        val prefix = site.instructions.take(position)
        val suffix = site.instructions.drop(position + 1)
        val prefixResults = prefix.mapNotNull { it.resultId() }.toSet()
        val capturedTypes = linkedMapOf<Int, top.mcfpp.type.TypeId>()
        val collect = object : IRTransform() {
            override fun ref(value: ValueRef): ValueRef {
                if (value is ValueRef.Result && value.instruction in prefixResults) capturedTypes[value.instruction] = value.type
                return super.ref(value)
            }
        }
        suffix.forEach(collect::instruction)
        collect.terminator(site.terminator)
        val captures = capturedTypes.mapValues { (id, type) ->
            Symbol(SymbolId.fresh(), "\$inline_capture_${nextResult++}_$id", type, mutable = true)
        }
        val reloads = captures.mapValues { nextResult++ }
        val resumeRewrite = object : IRTransform() {
            override fun result(value: Int) = reloads[value] ?: value
        }
        val prelude = callee.parameters.mapIndexed { index, parameter ->
            val symbol = symbols.getValue(parameter)
            Instruction.Write(Place(symbol.id), call.arguments[index], declaration = symbol)
        }
        val cloned = callee.blocks.map { original ->
            val instructions = original.instructions.map(rewrite::instruction).toMutableList()
            val exit = original.terminator
            val terminator = if (exit is Terminator.Return) {
                if (exit.value != null && returnPlace != null) instructions.add(Instruction.Write(returnPlace, rewrite.ref(exit.value)))
                Terminator.Jump(continuation)
            } else rewrite.terminator(exit)
            BasicBlock(blocks.getValue(original.id), instructions, terminator)
        }
        val resume = buildList {
            captures.forEach { (id, symbol) -> add(Instruction.Read(reloads.getValue(id), Place(symbol.id), symbol.declaredType)) }
            if (call.result != null) add(Instruction.Read(call.result, returnPlace!!, call.returnType!!))
            addAll(suffix.map(resumeRewrite::instruction))
        }
        return caller.copy(
            blocks = caller.blocks.map { if (it.id == site.id) it.copy(instructions = prefix + captures.map { (id, symbol) ->
                Instruction.Write(Place(symbol.id), ValueRef.Result(symbol.declaredType, id), declaration = symbol)
            } + prelude,
                terminator = Terminator.Jump(blocks.getValue(callee.entry))) else it } + cloned + BasicBlock(continuation, resume, resumeRewrite.terminator(site.terminator)),
            declarations = caller.declarations + symbols.values.associateBy { it.id } +
                listOfNotNull(returnSymbol).associateBy { it.id } + captures.values.associateBy { it.id } +
                caller.declarations.filterKeys { it == call.resultPlace?.root }.mapValues { it.value.copy(mutable = true, forceRuntime = false) },
            boundTypes = caller.boundTypes + callee.boundTypes,
            runtimeValues = caller.runtimeValues + callee.runtimeValues.mapNotNull(results::get),
            externalRoots = caller.externalRoots + callee.externalRoots,
        )
    }
}
