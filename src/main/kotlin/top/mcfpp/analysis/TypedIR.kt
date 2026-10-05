package top.mcfpp.analysis

import top.mcfpp.type.TypeId

/** Basic blocks and explicit reads/writes, with no dependency on Var implementation classes. */
data class TypedIR(val entry: Int, val blocks: List<BasicBlock>, val runtimeValues: Set<Int> = emptySet(),
                   val parameters: List<SymbolId> = emptyList(), val externalRoots: Set<SymbolId> = emptySet())
data class BasicBlock(val id: Int, val instructions: List<Instruction>, val terminator: Terminator)

sealed interface Instruction {
    data class Read(val result: Int, val place: Place, val type: TypeId, val location: Location = Location(place)) : Instruction
    data class Write(val place: Place, val value: ValueRef, val containerType: TypeId? = null, val location: Location = Location(place)) : Instruction
    data class CaptureIndex(val result: Int, val value: ValueRef, val container: Location) : Instruction
    data class CaptureKey(val result: Int, val value: ValueRef) : Instruction
    data class MapMember(val operation: MapOperation, val receiver: Location, val type: TypeId,
                         val key: ValueRef? = null, val argument: ValueRef? = null,
                         val result: Int? = null, val resultPlace: Place? = null) : Instruction
    data class MapProjection(val result: Int, val place: Place, val receiver: Location,
                             val type: TypeId, val dictionary: Boolean) : Instruction
    data class ListMember(val operation: ListOperation, val receiver: Location, val type: TypeId,
                          val argument: ValueRef? = null, val argumentPlace: Place? = null,
                          val index: ValueRef? = null, val knownIndex: Int? = null,
                          val result: Int? = null, val resultPlace: Place? = null) : Instruction
    data class DictionaryMember(val operation: DictionaryOperation, val receiver: Location, val type: TypeId,
                                val argument: ValueRef? = null, val key: String? = null,
                                val result: Int? = null, val resultPlace: Place? = null) : Instruction {
        val effect: Effect get() = when (operation) {
            DictionaryOperation.CONTAINS_KEY -> Effect.Pure
            DictionaryOperation.REMOVE -> Effect.Writes(setOf(key?.let(receiver.place::field) ?: receiver.place))
            else -> Effect.Writes(setOf(receiver.place))
        }
    }
    data class Binary(val result: Int, val operation: String, val left: ValueRef, val right: ValueRef, val type: TypeId) : Instruction
    data class Promote(val result: Int, val value: ValueRef, val type: TypeId) : Instruction
    data class Convert(val result: Int, val value: ValueRef, val type: TypeId) : Instruction
    data class View(val result: Int, val value: ValueRef.TypedView) : Instruction
    data class Construct(val result: Int, val place: Place, val type: TypeId, val parts: Map<PathSegment, ValueRef>, val sequence: Boolean) : Instruction
    data class Call(val result: Int?, val declaration: SymbolId, val arguments: List<ValueRef>, val effect: Effect,
                    val returnType: TypeId? = null, val argumentPlaces: List<Place?> = emptyList(),
                    val parameterTypes: List<TypeId> = emptyList(), val staticParameters: Set<Int> = emptySet(),
                    val provisional: Boolean = false, val resultPlace: Place? = null,
                    val argumentLocations: List<Location?> = emptyList()) : Instruction
    data class RawCommand(val command: String) : Instruction
}
sealed interface Terminator {
    data class Jump(val block: Int) : Terminator
    data class Branch(val condition: ValueRef, val whenTrue: Int, val whenFalse: Int) : Terminator
    data class Return(val value: ValueRef?) : Terminator
    object Unreachable : Terminator
}
sealed interface Effect {
    object Pure : Effect
    object ReadsRuntime : Effect
    data class Writes(val places: Set<Place>) : Effect
    object Unknown : Effect
}

/** Conservative forward fixed point, including backedges and unreachable predecessor filtering. */
object FlowAnalysis {
    data class Snapshot(val place: Place, val facts: FlowFacts)
    data class Result(val entries: Map<Int, FlowFacts>, val exits: Map<Int, FlowFacts>, val values: Map<Pair<Int, Int>, ValueFacts>,
                      val lengths: Map<Pair<Int, Int>, Int>, val beforeWrites: Map<Pair<Int, Int>, FlowFacts>,
                      val returns: Map<Int, Snapshot>)

    fun analyze(ir: TypedIR, initial: FlowFacts = FlowFacts(), evaluator: (String, CompilerValue, CompilerValue) -> CompilerValue? = { _, _, _ -> null }, canFoldBranch: (ValueRef) -> Boolean = { it !is ValueRef.Result || it.instruction !in ir.runtimeValues },
                callSummary: (Instruction.Call, List<Snapshot>) -> ReturnTypeAnalysis.Summary = { call, _ -> ReturnTypeAnalysis.unknown(call) },
                foldQueries: Boolean = true): Result {
        require(ir.blocks.map { it.id }.distinct().size == ir.blocks.size)
        val blocks = ir.blocks.associateBy { it.id }
        require(ir.entry in blocks)
        val entries = mutableMapOf(ir.entry to initial.fork())
        val exits = mutableMapOf<Int, FlowFacts>()
        val resultFacts = mutableMapOf<Pair<Int, Int>, ValueFacts>()
        val resultLengths = mutableMapOf<Pair<Int, Int>, Int>()
        val beforeWrites = mutableMapOf<Pair<Int, Int>, FlowFacts>()
        val returns = mutableMapOf<Int, Snapshot>()
        val pending = ArrayDeque<Int>().apply { add(ir.entry) }
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val block = blocks.getValue(id)
            val state = entries.getValue(id).fork()
            val values = mutableMapOf<Int, ValueFacts>()
            val origins = mutableMapOf<Int, Place>()
            val snapshots = mutableMapOf<Int, Snapshot>()
            fun origin(ref: ValueRef): Place? = when (ref) {
                is ValueRef.Read -> ref.place
                is ValueRef.Result -> origins[ref.instruction]
                is ValueRef.TypedView -> ref.place
                else -> null
            }
            fun capture(place: Place) = Snapshot(Place(place.root), FlowFacts().apply { copyFrom(state, place, Place(place.root)) })
            fun snapshot(ref: ValueRef): Snapshot? = when (ref) {
                is ValueRef.Result -> snapshots[ref.instruction]
                is ValueRef.Read -> capture(ref.place)
                is ValueRef.TypedView -> snapshot(ref.source)
                else -> null
            }
            fun value(ref: ValueRef): ValueFacts = when (ref) {
                is ValueRef.Constant -> ValueFacts(TypeKnowledge.Exact(ref.type), ValueKnowledge.Constant(ref.value))
                is ValueRef.Read -> state.read(ref.place) ?: ValueFacts(TypeKnowledge.Exact(ref.type), ValueKnowledge.Unknown, ValueState.UNINITIALIZED)
                is ValueRef.Result -> values[ref.instruction] ?: ValueFacts(TypeKnowledge.Exact(ref.type), ValueKnowledge.Unknown)
                is ValueRef.TypedView -> ValueFacts(TypeKnowledge.Exact(ref.type), ValueKnowledge.Unknown)
            }
            fun evidence(ref: ValueRef): Snapshot {
                // A scalar literal has no storage identity. This root exists only inside its isolated snapshot.
                val source = snapshot(ref) ?: Snapshot(Place(SymbolId(0)), FlowFacts())
                val facts = source.facts.withoutValues()
                facts.refine(source.place, value(ref).copy(value = ValueKnowledge.Unknown))
                return Snapshot(source.place, facts)
            }
            for ((position, instruction) in block.instructions.withIndex()) when (instruction) {
                is Instruction.Read -> {
                    if (instruction.location.keys.isNotEmpty()) beforeWrites[id to position] = state.fork()
                    val resolved = MapFacts.resolve(state, instruction.location)
                    val existing = state.read(resolved)
                    values[instruction.result] = existing ?: ValueFacts(
                        if (instruction.type in setOf(top.mcfpp.type.MCFPPBaseType.Any.typeId, top.mcfpp.type.MCFPPBaseType.Object.typeId)) TypeKnowledge.Unknown else TypeKnowledge.Exact(instruction.type),
                        ValueKnowledge.Unknown, state.read(Place(instruction.place.root))?.state ?: ValueState.UNINITIALIZED)
                    origins[instruction.result] = resolved
                    snapshots[instruction.result] = capture(resolved)
                    state.length(resolved)?.let { resultLengths[id to instruction.result] = it }
                        ?: resultLengths.remove(id to instruction.result)
                }
                is Instruction.Write -> {
                    if (instruction.place.path.isNotEmpty()) beforeWrites[id to position] = state.fork()
                    val source = origin(instruction.value)
                    val frozen = snapshot(instruction.value) ?: source?.let { capture(it) }
                    val written = value(instruction.value)
                    val resolved = MapFacts.resolve(state, instruction.location)
                    state.forgetDescendants(resolved)
                    state.write(resolved, written)
                    if (source != null) state.copyFrom(frozen!!.facts, frozen.place, resolved, includeRoot = false)
                }
                is Instruction.CaptureIndex -> values[instruction.result] = value(instruction.value)
                is Instruction.CaptureKey -> values[instruction.result] = value(instruction.value)
                is Instruction.MapProjection -> {
                    beforeWrites[id to position] = state.fork()
                    values[instruction.result] = MapFacts.project(state, instruction)
                    origins[instruction.result] = instruction.place
                    snapshots[instruction.result] = capture(instruction.place)
                    state.length(instruction.place)?.let { resultLengths[id to instruction.result] = it }
                        ?: resultLengths.remove(id to instruction.result)
                }
                is Instruction.MapMember -> {
                    beforeWrites[id to position] = state.fork()
                    val key = instruction.key?.let { (value(it).value as? ValueKnowledge.Constant)?.value }?.let(MapFacts::text)
                    val receiver = MapFacts.resolve(state, instruction.receiver)
                    if (instruction.operation.query) {
                        val entries = receiver.field("entries")
                        val size = state.length(entries)
                        val known = when (instruction.operation) {
                            MapOperation.SIZE -> size?.let { CompilerValue.Integral(it.toLong()) }
                            MapOperation.IS_EMPTY -> size?.let { CompilerValue.Bool(it == 0) }
                            else -> key?.let { name -> MapFacts.keys(state, entries)?.let { CompilerValue.Bool(name in it) } }
                        }?.takeIf { foldQueries }
                        val type = if (instruction.operation == MapOperation.SIZE) top.mcfpp.type.MCFPPBaseType.Int.typeId else top.mcfpp.type.MCFPPBaseType.Bool.typeId
                        val fact = ValueFacts(TypeKnowledge.Exact(type), known?.let(ValueKnowledge::Constant) ?: ValueKnowledge.Unknown)
                        values[instruction.result!!] = fact
                        instruction.resultPlace?.let { state.write(it, fact); origins[instruction.result] = it }
                    } else {
                        val source = instruction.argument?.let(::snapshot)
                        MapFacts.edit(state, instruction, key, instruction.argument?.let(::value), source?.facts, source?.place)
                    }
                }
                is Instruction.ListMember -> {
                    beforeWrites[id to position] = state.fork()
                    val source = instruction.argument?.let(::snapshot)
                    val argument = instruction.argument?.let(::value)
                    if (instruction.operation.search) {
                        val found = ListFacts.find(state, instruction, argument!!)
                        if (instruction.operation == ListOperation.REMOVE) {
                            if (found != -1) ListFacts.edit(state, instruction.copy(operation = ListOperation.REMOVE_AT,
                                argument = null, argumentPlace = null, knownIndex = found), null, null, null)
                        } else {
                            val contains = instruction.operation == ListOperation.CONTAINS
                            val type = if (contains) top.mcfpp.type.MCFPPBaseType.Bool.typeId else top.mcfpp.type.MCFPPBaseType.Int.typeId
                            val known = found?.takeIf { foldQueries }?.let {
                                if (contains) CompilerValue.Bool(it >= 0) else CompilerValue.Integral(it.toLong())
                            }
                            val fact = ValueFacts(TypeKnowledge.Exact(type), known?.let(ValueKnowledge::Constant) ?: ValueKnowledge.Unknown)
                            values[instruction.result!!] = fact
                            instruction.resultPlace?.let { state.write(it, fact); origins[instruction.result] = it }
                        }
                    } else ListFacts.edit(state, instruction, source?.facts, source?.place, argument)
                }
                is Instruction.DictionaryMember -> {
                    val place = instruction.receiver.place
                    when (instruction.operation) {
                        DictionaryOperation.CLEAR -> {
                            state.forgetDescendants(place)
                            state.write(place, ValueFacts(TypeKnowledge.Exact(instruction.type),
                                ValueKnowledge.Constant(CompilerValue.Typed(instruction.type, CompilerValue.Record(emptyMap())))))
                        }
                        DictionaryOperation.REMOVE -> {
                            val target = instruction.key?.let(place::field) ?: place
                            state.forgetDescendants(target)
                            state.write(target, ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown, ValueState.UNINITIALIZED))
                        }
                        DictionaryOperation.MERGE -> {
                            val source = snapshot(instruction.argument!!)
                            if (source != null) DictionaryFacts.merge(state, place, source.facts, source.place)
                            else {
                                state.forgetDescendants(place)
                                state.write(place, ValueFacts(TypeKnowledge.Exact(instruction.type), ValueKnowledge.Unknown))
                            }
                        }
                        DictionaryOperation.CONTAINS_KEY -> {
                            val fact = ValueFacts(TypeKnowledge.Exact(top.mcfpp.type.MCFPPBaseType.Bool.typeId), ValueKnowledge.Unknown)
                            values[instruction.result!!] = fact
                            instruction.resultPlace?.let { state.write(it, fact); origins[instruction.result] = it }
                        }
                    }
                }
                is Instruction.Construct -> {
                    val parts = instruction.parts.mapValues { value(it.value) }
                    val capturedParts = instruction.parts.mapValues { snapshot(it.value) }
                    val constants = parts.mapValues { (segment, fact) ->
                        val constant = (fact.value as? ValueKnowledge.Constant)?.value
                        constant?.let { if (it is CompilerValue.Typed) it else CompilerValue.Typed(
                            (fact.type as? TypeKnowledge.Exact)?.type ?: instruction.parts.getValue(segment).type, it) }
                    }
                    val constant = if (constants.values.any { it == null }) null else if (instruction.sequence)
                        CompilerValue.Sequence(constants.values.map { it!! }) else CompilerValue.Record(constants.mapKeys { (it.key as PathSegment.Field).name }.mapValues { it.value!! })
                    val fact = ValueFacts(TypeKnowledge.Exact(instruction.type), constant?.let { ValueKnowledge.Constant(CompilerValue.Typed(instruction.type, it)) }
                        ?: ValueKnowledge.Partial(parts.mapValues { it.value.value }.filterValues { it != ValueKnowledge.Unknown }))
                    state.forgetDescendants(instruction.place)
                    state.write(instruction.place, fact)
                    for ((segment, part) in parts) {
                        val child = Place(instruction.place.root, instruction.place.path + segment)
                        state.write(child, part)
                        val source = origin(instruction.parts.getValue(segment))
                        if (source != null) capturedParts.getValue(segment)!!.let { state.copyFrom(it.facts, it.place, child, includeRoot = false) }
                    }
                    // Seeding children must not invalidate the newly built complete root snapshot.
                    state.refine(instruction.place, fact)
                    if (instruction.sequence) state.setLength(instruction.place, instruction.parts.size)
                    values[instruction.result] = fact
                    origins[instruction.result] = instruction.place
                    snapshots[instruction.result] = capture(instruction.place)
                }
                is Instruction.Call -> {
                    val summary = callSummary(instruction, instruction.arguments.map(::evidence))
                    val returned = summary.type
                    val written = summary.writes
                    when (val effect = instruction.effect) {
                        is Effect.Writes -> effect.places.forEach { place ->
                            state.forgetDescendants(place)
                            state.write(place, (state.read(place) ?: ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown))
                                .copy(type = written[place] ?: TypeKnowledge.Unknown, value = ValueKnowledge.Unknown))
                            state.copyFrom(summary.outputs, place, place, includeRoot = false)
                        }
                        Effect.Unknown -> state.barrier()
                        else -> Unit
                    }
                    for ((place, type) in written) {
                        if (PathSegment.UnknownIndex in place.path) continue
                        val affected = instruction.effect == Effect.Unknown || (instruction.effect as? Effect.Writes)?.places?.any {
                            it.path.size <= place.path.size && it.overlaps(place)
                        } == true
                        if (affected) state.refine(place, (state.read(place) ?: ValueFacts(type, ValueKnowledge.Unknown)).copy(type = type))
                    }
                    instruction.resultPlace?.let {
                        state.forgetDescendants(it)
                        state.write(it, ValueFacts(returned, ValueKnowledge.Unknown))
                        summary.returned?.let { source -> state.copyFrom(source.facts, source.place, it, includeRoot = false) }
                    }
                    instruction.resultPlace?.let { place -> instruction.result?.let {
                        origins[it] = place
                        snapshots[it] = capture(place)
                    } }
                    instruction.result?.let { result ->
                        values[result] = ValueFacts(returned, ValueKnowledge.Unknown)
                        instruction.resultPlace?.let(state::length)?.let { resultLengths[id to result] = it }
                            ?: resultLengths.remove(id to result)
                    }
                }
                is Instruction.RawCommand -> state.barrier()
                is Instruction.View -> {
                    values[instruction.result] = value(instruction.value)
                    origins[instruction.result] = instruction.value.place
                    snapshot(instruction.value)?.let { snapshots[instruction.result] = it }
                }
                is Instruction.Binary -> {
                    val leftFact = value(instruction.left)
                    val rightFact = value(instruction.right)
                    // Only any exposes actual-type knowledge. Object and concrete signatures
                    // retain their static operators even when the payload type is known.
                    fun operandType(ref: ValueRef, fact: ValueFacts): TypeId? =
                        if (ref.type == top.mcfpp.type.MCFPPBaseType.Any.typeId) (fact.type as? TypeKnowledge.Exact)?.type else ref.type
                    val leftType = operandType(instruction.left, leftFact)
                    val rightType = operandType(instruction.right, rightFact)
                    val type = if (leftType != null && rightType != null)
                        top.mcfpp.type.TypeRelations.resolveOperator(instruction.operation, leftType, rightType) else null
                    val left = (leftFact.value as? ValueKnowledge.Constant)?.value
                    val right = (rightFact.value as? ValueKnowledge.Constant)?.value
                    val folded = if (left != null && right != null) evaluator(instruction.operation, left, right) else null
                    values[instruction.result] = ValueFacts(type?.let { TypeKnowledge.Exact(it) } ?: TypeKnowledge.Unknown,
                        if (type != null && folded != null) ValueKnowledge.Constant(folded) else ValueKnowledge.Unknown)
                }
                is Instruction.Promote -> values[instruction.result] = ValueFacts(TypeKnowledge.Exact(instruction.type), ValueKnowledge.Unknown)
                is Instruction.Convert -> values[instruction.result] = ValueFacts(TypeKnowledge.Exact(instruction.type), ValueKnowledge.Unknown)
            }
            values.forEach { (result, fact) -> resultFacts[id to result] = fact }
            (block.terminator as? Terminator.Return)?.value?.let { returns[id] = evidence(it) }
            if (exits[id] == state) continue
            exits[id] = state
            val successors = when (val terminator = block.terminator) {
                is Terminator.Jump -> listOf(terminator.block)
                is Terminator.Branch -> when (if (canFoldBranch(terminator.condition)) (value(terminator.condition).value as? ValueKnowledge.Constant)?.value else null) {
                    CompilerValue.Bool(true) -> listOf(terminator.whenTrue)
                    CompilerValue.Bool(false) -> listOf(terminator.whenFalse)
                    else -> listOf(terminator.whenTrue, terminator.whenFalse)
                }
                else -> emptyList()
            }
            for (successor in successors) {
                require(successor in blocks) { "Missing basic block $successor" }
                val joined = entries[successor]?.join(state) ?: state.fork()
                if (entries[successor] != joined) {
                    entries[successor] = joined
                    pending.add(successor)
                }
            }
        }
        return Result(entries, exits, resultFacts, resultLengths, beforeWrites, returns)
    }
}
