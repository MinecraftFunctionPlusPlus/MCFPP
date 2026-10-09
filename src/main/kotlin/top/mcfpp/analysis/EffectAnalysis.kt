package top.mcfpp.analysis

/** Least fixed point of external writes. Local copies do not escape through ordinary parameters. */
object EffectAnalysis {
    private class ActualEffects(val function: top.mcfpp.model.function.Function, val freshReceiver: Boolean) {
        val locals = linkedSetOf<SymbolId>()
        val external = linkedSetOf<SymbolId>()
        var effect: Effect = Effect.Pure
    }
    private var actual: ActualEffects? = null

    /** Collect only the body actually being emitted; recursive prototypes remain unproved. */
    fun <T> withActualEffects(function: top.mcfpp.model.function.Function, freshReceiver: Boolean = false, block: () -> T): T {
        if (function.bodyCompiled || function.bodyBeingCompiled) return block()
        val previous = actual
        val errors = top.mcfpp.Project.errorCount
        val context = ActualEffects(function, freshReceiver)
        val own = function.scope.allVars.toSet()
        for (value in StorageAccess.visibleValues(function.scope)) {
            val root = StorageAccess.ensure(value).place.root
            if (value in own) context.locals.add(root) else context.external.add(root)
        }
        actual = context
        return try {
            val result = block()
            if (function.typedIR == null) function.runtimeEffect =
                if (function.bodyCompiled && !function.bodyBeingCompiled && top.mcfpp.Project.errorCount == errors) context.effect else Effect.Unknown
            result
        } finally { actual = previous }
    }

    internal fun declared(place: Place) { actual?.locals?.add(place.root) }

    private fun external(context: ActualEffects, place: Place): Boolean? {
        for (parameter in context.function.normalParams) {
            val root = context.function.scope.getVar(parameter.identifier)?.storageBinding?.place?.root ?: continue
            if (root == place.root) return parameter.isStatic
        }
        val receiver = context.function.scope.getVar("this")?.storageBinding?.place?.root
        if (receiver == place.root) return !context.freshReceiver
        if (place.root in context.external) return true
        if (place.root in context.locals) return false
        return null
    }

    internal fun recordWrite(place: Place, contents: Boolean = false) = recordEffect(
        Effect.Writes(setOf(place), if (contents) setOf(place) else emptySet()))

    fun recordEffect(effect: Effect) {
        val context = actual ?: return
        val observed = if (effect is Effect.Writes) {
            val places = linkedSetOf<Place>()
            for (place in effect.places) when (external(context, place)) {
                true -> places.add(place)
                false -> Unit
                null -> { context.effect = Effect.Unknown; return }
            }
            writes(places, effect.contents.intersect(places))
        } else effect
        context.effect = join(context.effect, observed)
    }

    /** Map the completed callee summary through its real parameter and receiver bindings. */
    fun recordCall(callee: top.mcfpp.model.function.Function, arguments: List<top.mcfpp.core.lang.Var<*>>,
                   receiver: top.mcfpp.core.lang.Var<*>? = null): Effect {
        val effect = if (callee is top.mcfpp.model.function.NativeFunction) {
            val method = callee.javaMethod
            when {
                method.isAnnotationPresent(top.mcfpp.mni.annotation.NoExternalWrites::class.java) ||
                    method.declaringClass.isAnnotationPresent(top.mcfpp.mni.annotation.NoExternalWrites::class.java) -> Effect.ReadsRuntime
                method.isAnnotationPresent(top.mcfpp.mni.annotation.WritesReceiver::class.java) -> receiver?.storageBinding?.place
                    ?.let { Effect.Writes(setOf(it), setOf(it)) } ?: Effect.Unknown
                else -> Effect.Unknown
            }
        } else if (!callee.bodyCompiled || callee.bodyBeingCompiled) Effect.Unknown else callee.runtimeEffect
        if (effect !is Effect.Writes || callee is top.mcfpp.model.function.NativeFunction) {
            recordEffect(effect); return effect
        }
        fun mapped(place: Place): Place? {
            for ((index, parameter) in callee.normalParams.withIndex()) {
                val formal = callee.scope.getVar(parameter.identifier)?.storageBinding?.place ?: continue
                if (formal.root == place.root) {
                    if (!parameter.isStatic) return null
                    val argument = arguments.getOrNull(index)?.storageBinding?.place ?: return null
                    return Place(argument.root, argument.path + place.path.drop(formal.path.size))
                }
            }
            val formal = callee.scope.getVar("this")?.storageBinding?.place
            if (formal != null && formal.root == place.root) {
                val argument = receiver?.storageBinding?.place ?: return null
                return Place(argument.root, argument.path + place.path.drop(formal.path.size))
            }
            return place
        }
        val mappedPlaces = effect.places.map { mapped(it) ?: run { recordEffect(Effect.Unknown); return Effect.Unknown } }.toSet()
        return writes(mappedPlaces, effect.contents.mapNotNull(::mapped).toSet()).also(::recordEffect)
    }
    /** A literal say observes values but cannot mutate compiler storage. */
    fun rawCommandEffect(command: String): Effect {
        val literal = command.trimStart()
        return if ('$' !in literal && '\n' !in literal && '\r' !in literal &&
            (literal == "say" || literal.startsWith("say "))) Effect.ReadsRuntime else Effect.Unknown
    }
    fun analyze(functions: Map<SymbolId, TypedIR>): Map<SymbolId, Effect> {
        val effects = functions.keys.associateWith<SymbolId, Effect> { Effect.Pure }.toMutableMap()
        // An acyclic path cannot be longer than all path segments in the graph together.
        // Recursive composition beyond that bound widens to the external root.
        val pathBound = functions.values.sumOf { ir -> ir.blocks.sumOf { block -> block.instructions.sumOf { instruction ->
            when (instruction) {
                is Instruction.Write -> instruction.place.path.size
                is Instruction.DictionaryMember -> instruction.receiver.place.path.size + 1
                is Instruction.ListMember -> instruction.receiver.place.path.size
                is Instruction.MapMember -> instruction.receiver.place.path.size + 1
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
                        is Instruction.RawCommand -> rawCommandEffect(instruction.command)
                        is Instruction.Write -> Effect.Writes(setOf(instruction.place))
                        is Instruction.DictionaryMember -> instruction.effect
                        is Instruction.ListMember -> if (instruction.operation.query) Effect.Pure else Effect.Writes(setOf(instruction.receiver.place), setOf(instruction.receiver.place))
                        is Instruction.MapMember -> if (instruction.operation.query) Effect.Pure else {
                            val contents = instruction.receiver.place.field("entries")
                            Effect.Writes(setOf(contents), setOf(contents))
                        }
                        is Instruction.Call -> callEffect(instruction, functions, effects)
                        else -> Effect.Pure
                    }
                    val external = if (observed is Effect.Writes)
                        writes(observed.places.filter { it.root in ir.externalRoots }.map {
                            if (it.path.size > pathBound) Place(it.root) else it
                        }.toSet(), observed.contents.filter { it.root in ir.externalRoots && it.path.size <= pathBound }.toSet()) else observed
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
        fun mapPlace(place: Place): Place? {
            val parameter = target.parameters.indexOf(place.root)
            if (parameter < 0) return place
            val argument = call.argumentPlaces.getOrNull(parameter) ?: return null
            return Place(argument.root, argument.path + place.path)
        }
        val mapped = effect.places.map { mapPlace(it) ?: return Effect.Unknown }.toSet()
        return writes(mapped, effect.contents.mapNotNull(::mapPlace).toSet())
    }

    fun bind(ir: TypedIR, functions: Map<SymbolId, TypedIR>, effects: Map<SymbolId, Effect>) = ir.copy(
        blocks = ir.blocks.map { block -> block.copy(instructions = block.instructions.map { instruction ->
            if (instruction is Instruction.Call) instruction.copy(effect = callEffect(instruction, functions, effects)) else instruction
        }) })

    private fun writes(places: Set<Place>, contents: Set<Place> = emptySet()): Effect {
        val roots = places.filter { candidate -> places.none { ancestor -> ancestor != candidate && ancestor.root == candidate.root &&
            ancestor.path.size <= candidate.path.size && ancestor.path.zip(candidate.path).all { (left, right) -> left == right || left == PathSegment.UnknownIndex } } }.toSet()
        return if (roots.isEmpty()) Effect.Pure else Effect.Writes(roots, roots.filter { root ->
            root in contents && places.none { it !in contents && it.path.size <= root.path.size && it.overlaps(root) }
        }.toSet())
    }
    private fun join(left: Effect, right: Effect): Effect = when {
        left == Effect.Unknown || right == Effect.Unknown -> Effect.Unknown
        left is Effect.Writes || right is Effect.Writes -> {
            val a = left as? Effect.Writes
            val b = right as? Effect.Writes
            val places = a?.places.orEmpty() + b?.places.orEmpty()
            val rebindings = (a?.places.orEmpty() - a?.contents.orEmpty()) + (b?.places.orEmpty() - b?.contents.orEmpty())
            writes(places, (a?.contents.orEmpty() + b?.contents.orEmpty()).filter { content ->
                rebindings.none { it.path.size <= content.path.size && it.overlaps(content) }
            }.toSet())
        }
        left == Effect.ReadsRuntime || right == Effect.ReadsRuntime -> Effect.ReadsRuntime
        else -> Effect.Pure
    }

}
