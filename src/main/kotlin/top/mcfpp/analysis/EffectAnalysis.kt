package top.mcfpp.analysis

/** Least fixed point of external writes. Local copies do not escape through ordinary parameters. */
object EffectAnalysis {
    private class ActualEffects(
        val function: top.mcfpp.model.function.Function,
        val freshReceiver: Boolean,
        val parent: ActualEffects?
    ) {
        val locals = linkedSetOf<SymbolId>()
        val external = linkedSetOf<SymbolId>()
        var effect: Effect = Effect.Pure
    }
    private var actual: ActualEffects? = null

    /** Effect fields are immutable; their host-supplied sets may still be mutable and shared. */
    internal class EffectGraphSnapshot internal constructor(
        private val restoreState: () -> Unit,
        private val recapture: (List<Effect>) -> EffectGraphSnapshot
    ) {
        fun restore() = restoreState()
        fun captureCurrent(additionalEffects: List<Effect> = emptyList()): EffectGraphSnapshot = recapture(additionalEffects)
    }

    internal fun checkpointEffectGraph(effects: List<Effect>): EffectGraphSnapshot = captureEffectGraph(emptyList(), effects)

    private fun captureEffectGraph(retained: List<Effect>, additional: List<Effect>): EffectGraphSnapshot {
        check(additional.javaClass == java.util.ArrayList::class.java || additional.javaClass.name in effectRootLists) {
            "Effect journal cannot capture roots ${additional.javaClass.name}"
        }
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Effect, Boolean>())
        val roots = (retained + additional).filter { seen.add(it) }
        val containers = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        val restores = arrayListOf<() -> Unit>()
        fun savePlaces(places: Set<Place>) {
            if (!containers.add(places)) return
            check(places.javaClass in mutableEffectSets || places.javaClass.name in immutableSets) {
                "Effect journal cannot capture places ${places.javaClass.name}"
            }
            // Inspect only a known container. A Java generic lie must not reach addAll/hashCode.
            val items = (places as Set<*>).toList()
            for (item in items) {
                check(item is Place) { "Effect journal requires immutable Place elements" }
                // Place copies its list, but erased Java generics can still introduce host elements.
                // Restore rehashes each Place, so validate without invoking any segment callbacks.
                check((item.path as List<*>).all {
                    it is PathSegment.Field || it is PathSegment.Index || it === PathSegment.UnknownIndex
                }) { "Effect journal requires supported immutable Place path segments" }
            }
            if (places.javaClass in mutableEffectSets) {
                @Suppress("UNCHECKED_CAST") val saved = items as List<Place>
                @Suppress("UNCHECKED_CAST") val mutable = places as MutableSet<Place>
                restores += { mutable.clear(); mutable.addAll(saved) }
            }
        }
        for (effect in roots) when (effect) {
            is Effect.Writes -> { savePlaces(effect.places); savePlaces(effect.contents) }
            Effect.Pure, Effect.ReadsRuntime, Effect.Unknown -> Unit
        }
        // Retain the Effect roots themselves: replacing the live summary must not detach its sets.
        // Place owns immutable path/segment copies, so no adapter or compiler object is cloned here.
        return EffectGraphSnapshot({ restores.forEach { it() } }, { captureEffectGraph(roots, it) })
    }

    /** Opaque capability: callers can enroll functions, but cannot change active effect frames. */
    internal class JournalSnapshot internal constructor(
        val functions: List<top.mcfpp.model.function.Function>,
        private val restoreState: () -> Unit,
        private val recapture: () -> JournalSnapshot
    ) {
        fun restore() = restoreState()
        fun captureCurrent(): JournalSnapshot = recapture()
    }

    internal fun checkpointActualEffects(): JournalSnapshot = captureActualEffects(emptyList(), null)

    private fun captureActualEffects(retained: List<ActualEffects>, retainedGraph: EffectGraphSnapshot?): JournalSnapshot {
        val active = actual
        val pending = ArrayDeque<ActualEffects>().apply {
            addAll(retained)
            active?.let(::addLast)
        }
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<ActualEffects, Boolean>())
        val frames = arrayListOf<ActualEffects>()
        val restores = arrayListOf<() -> Unit>()
        val effects = arrayListOf<Effect>()
        while (pending.isNotEmpty()) {
            val frame = pending.removeFirst()
            if (!seen.add(frame)) continue
            frames.add(frame)
            frame.parent?.let(pending::addLast)
            val locals = frame.locals.toList()
            val external = frame.external.toList()
            val effect = frame.effect
            effects.add(effect)
            restores += {
                frame.locals.clear(); frame.locals.addAll(locals)
                frame.external.clear(); frame.external.addAll(external)
                frame.effect = effect
            }
        }
        val graph = retainedGraph?.captureCurrent(effects) ?: checkpointEffectGraph(effects)
        return JournalSnapshot(java.util.Collections.unmodifiableList(frames.map { it.function }), {
            restores.forEach { it() }
            graph.restore()
            actual = active
        }, { captureActualEffects(frames, graph) })
    }

    private val mutableEffectSets = setOf<Class<*>>(java.util.HashSet::class.java, java.util.LinkedHashSet::class.java)
    private val effectRootLists = top.mcfpp.util.CollectionImplementations.immutableListNames +
        top.mcfpp.util.CollectionImplementations.fixedList.name
    private val immutableSets = top.mcfpp.util.CollectionImplementations.immutableSetNames

    /** Collect only the body actually being emitted; recursive prototypes remain unproved. */
    fun <T> withActualEffects(function: top.mcfpp.model.function.Function, freshReceiver: Boolean = false, block: () -> T): T {
        if (function.bodyCompiled || function.bodyBeingCompiled) return block()
        val previous = actual
        val errors = top.mcfpp.Project.errorCount
        val context = ActualEffects(function, freshReceiver, previous)
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
            if (root == place.root) return false
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

    /** Map the completed callee summary through its receiver binding. */
    fun recordCall(callee: top.mcfpp.model.function.Function,
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
        val callers = functions.keys.associateWith { linkedSetOf<SymbolId>() }
        functions.forEach { (id, ir) -> ir.blocks.forEach { block ->
            block.instructions.filterIsInstance<Instruction.Call>().forEach { callers[it.declaration]?.add(id) }
        } }
        val pending = ArrayDeque(functions.keys)
        val queued = functions.keys.toMutableSet()
        while (pending.isNotEmpty()) {
                val id = pending.removeFirst()
                queued.remove(id)
                val ir = functions.getValue(id)
                var effect: Effect = Effect.Pure
                val bound = bind(ir, functions, effects)
                val reachable = FlowAnalysis.analyze(bound, evaluator = PrimitiveEvaluation::binary).entries.keys
                for (block in bound.blocks.filter { it.id in reachable }) for (instruction in block.instructions) {
                    val observed = when (instruction) {
                        is Instruction.RawCommand -> rawCommandEffect(instruction.command)
                        is Instruction.Print -> Effect.ReadsRuntime
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
                        writes(observed.places.filter { it.root in ir.externalRoots }.toSet(),
                            observed.contents.filter { it.root in ir.externalRoots }.toSet()) else observed
                    effect = join(effect, external)
                }
                effect = join(effects.getValue(id), effect)
                if (effects[id] != effect) {
                    effects[id] = effect
                    callers.getValue(id).forEach { if (queued.add(it)) pending.add(it) }
                }
        }
        return effects.toMap()
    }

    fun callEffect(call: Instruction.Call, functions: Map<SymbolId, TypedIR>, effects: Map<SymbolId, Effect>): Effect {
        if (call.provisional) return Effect.Unknown
        if (call.declaration !in functions) return call.effect
        return effects[call.declaration] ?: Effect.Unknown
    }

    fun bind(ir: TypedIR, functions: Map<SymbolId, TypedIR>, effects: Map<SymbolId, Effect>): TypedIR {
        var changed = false
        val blocks = ir.blocks.map { block ->
            var blockChanged = false
            val instructions = block.instructions.map { instruction ->
                if (instruction is Instruction.Call) {
                    val effect = callEffect(instruction, functions, effects)
                    if (effect != instruction.effect) {
                        blockChanged = true
                        instruction.copy(effect = effect)
                    } else instruction
                } else instruction
            }
            if (blockChanged) { changed = true; block.copy(instructions = instructions) } else block
        }
        return if (changed) ir.copy(blocks = blocks) else ir
    }

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
