package top.mcfpp.analysis

/** Least fixed point of external writes. Local copies do not escape through ordinary parameters. */
object EffectAnalysis {
    fun analyze(functions: Map<SymbolId, TypedIR>): Map<SymbolId, Effect> {
        val effects = functions.keys.associateWith<SymbolId, Effect> { Effect.Pure }.toMutableMap()
        // An acyclic path cannot be longer than all path segments in the graph together.
        // Recursive composition beyond that bound widens to the external root.
        val pathBound = functions.values.sumOf { ir -> ir.blocks.sumOf { block -> block.instructions.sumOf { instruction ->
            when (instruction) {
                is Instruction.Write -> instruction.place.path.size
                is Instruction.Call -> instruction.argumentPlaces.sumOf { it?.path?.size ?: 0 }
                else -> 0
            }
        } } }
        var changed: Boolean
        do {
            changed = false
            for ((id, ir) in functions) {
                var effect: Effect = Effect.Pure
                val bound = bind(ir, functions, effects)
                val reachable = FlowAnalysis.analyze(bound, evaluator = PrimitiveEvaluation::binary).entries.keys
                for (block in bound.blocks.filter { it.id in reachable }) for (instruction in block.instructions) {
                    val observed = when (instruction) {
                        is Instruction.RawCommand -> Effect.Unknown
                        is Instruction.Write -> Effect.Writes(setOf(instruction.place))
                        is Instruction.Call -> callEffect(instruction, functions, effects)
                        else -> Effect.Pure
                    }
                    val external = if (observed is Effect.Writes)
                        writes(observed.places.filter { it.root in ir.externalRoots }.map {
                            if (it.path.size > pathBound) Place(it.root) else it
                        }.toSet()) else observed
                    effect = join(effect, external)
                }
                effect = join(effects.getValue(id), effect)
                if (effects[id] != effect) { effects[id] = effect; changed = true }
            }
        } while (changed)
        return effects.toMap()
    }

    fun callEffect(call: Instruction.Call, functions: Map<SymbolId, TypedIR>, effects: Map<SymbolId, Effect>): Effect {
        if (call.provisional) return Effect.Unknown
        val target = functions[call.declaration] ?: return call.effect
        val effect = effects[call.declaration] ?: return Effect.Unknown
        if (effect !is Effect.Writes) return effect
        val mapped = effect.places.map { place ->
            val parameter = target.parameters.indexOf(place.root)
            if (parameter < 0) place else {
                val argument = call.argumentPlaces.getOrNull(parameter) ?: return Effect.Unknown
                Place(argument.root, argument.path + place.path)
            }
        }.toSet()
        return writes(mapped)
    }

    fun bind(ir: TypedIR, functions: Map<SymbolId, TypedIR>, effects: Map<SymbolId, Effect>) = ir.copy(
        blocks = ir.blocks.map { block -> block.copy(instructions = block.instructions.map { instruction ->
            if (instruction is Instruction.Call) instruction.copy(effect = callEffect(instruction, functions, effects)) else instruction
        }) })

    private fun writes(places: Set<Place>): Effect {
        val roots = places.filter { candidate -> places.none { ancestor -> ancestor != candidate && ancestor.root == candidate.root &&
            ancestor.path.size <= candidate.path.size && ancestor.path.zip(candidate.path).all { (left, right) -> left == right || left == PathSegment.UnknownIndex } } }.toSet()
        return if (roots.isEmpty()) Effect.Pure else Effect.Writes(roots)
    }
    private fun join(left: Effect, right: Effect): Effect = when {
        left == Effect.Unknown || right == Effect.Unknown -> Effect.Unknown
        left is Effect.Writes || right is Effect.Writes -> writes((left as? Effect.Writes)?.places.orEmpty() + (right as? Effect.Writes)?.places.orEmpty())
        left == Effect.ReadsRuntime || right == Effect.ReadsRuntime -> Effect.ReadsRuntime
        else -> Effect.Pure
    }

}
