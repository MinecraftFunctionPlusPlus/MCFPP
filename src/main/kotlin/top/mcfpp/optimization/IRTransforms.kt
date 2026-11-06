package top.mcfpp.optimization

import top.mcfpp.analysis.*

/** One traversal shared by rewrites and hygienic inlining, including captured addresses. */
internal open class IRTransform {
    open fun symbol(value: SymbolId): SymbolId = value
    open fun result(value: Int): Int = value
    open fun block(value: Int): Int = value
    open fun declaration(value: Symbol): Symbol = value.copy(id = symbol(value.id))
    fun place(value: Place) = Place(symbol(value.root), value.path)
    fun location(value: Location): Location = Location(place(value.place), value.indices.mapValues { result(it.value) },
        value.keys.mapValues { ref(it.value) }, value.frameOffset, value.captured.mapValues { ref(it.value) },
        value.predicates, value.capturedLocations.mapValues { location(it.value) })
    open fun ref(value: ValueRef): ValueRef = when (value) {
        is ValueRef.Constant -> value
        is ValueRef.Read -> value.copy(place = place(value.place))
        is ValueRef.Result -> value.copy(instruction = result(value.instruction))
        is ValueRef.TypedView -> value.copy(source = ref(value.source), place = place(value.place), location = location(value.location))
    }
    fun effect(value: Effect): Effect = if (value is Effect.Writes)
        Effect.Writes(value.places.map(::place).toSet(), value.contents.map(::place).toSet()) else value
    fun instruction(i: Instruction): Instruction = when (i) {
        is Instruction.Declare -> i.copy(symbol = declaration(i.symbol))
        is Instruction.Read -> i.copy(result = result(i.result), place = place(i.place), location = location(i.location))
        is Instruction.Write -> i.copy(place = place(i.place), value = ref(i.value), location = location(i.location), declaration = i.declaration?.let(::declaration))
        is Instruction.Binary -> i.copy(result = result(i.result), left = ref(i.left), right = ref(i.right))
        is Instruction.Promote -> i.copy(result = result(i.result), value = ref(i.value))
        is Instruction.Convert -> i.copy(result = result(i.result), value = ref(i.value), place = i.place?.let(::place))
        is Instruction.View -> i.copy(result = result(i.result), value = ref(i.value) as ValueRef.TypedView)
        is Instruction.Construct -> i.copy(result = result(i.result), place = place(i.place), parts = i.parts.mapValues { ref(it.value) })
        is Instruction.Call -> i.copy(result = i.result?.let(::result), arguments = i.arguments.map(::ref), effect = effect(i.effect), resultPlace = i.resultPlace?.let(::place))
        is Instruction.CaptureIndex -> i.copy(result = result(i.result), value = ref(i.value), container = location(i.container))
        is Instruction.CaptureKey -> i.copy(result = result(i.result), value = ref(i.value))
        is Instruction.MapProjection -> i.copy(result = result(i.result), place = place(i.place), receiver = location(i.receiver))
        is Instruction.MapMember -> i.copy(receiver = location(i.receiver), key = i.key?.let(::ref), argument = i.argument?.let(::ref),
            result = i.result?.let(::result), resultPlace = i.resultPlace?.let(::place), declaration = i.declaration?.let(::declaration))
        is Instruction.ListMember -> i.copy(receiver = location(i.receiver), argument = i.argument?.let(::ref), argumentPlace = i.argumentPlace?.let(::place),
            index = i.index?.let(::ref), result = i.result?.let(::result), resultPlace = i.resultPlace?.let(::place), declaration = i.declaration?.let(::declaration))
        is Instruction.DictionaryMember -> i.copy(receiver = location(i.receiver), argument = i.argument?.let(::ref),
            result = i.result?.let(::result), resultPlace = i.resultPlace?.let(::place), declaration = i.declaration?.let(::declaration))
        is Instruction.RawCommand -> i
        is Instruction.Print -> i.copy(value = ref(i.value))
    }
    fun terminator(t: Terminator): Terminator = when (t) {
        is Terminator.Jump -> t.copy(block = block(t.block))
        is Terminator.Branch -> t.copy(condition = ref(t.condition), whenTrue = block(t.whenTrue), whenFalse = block(t.whenFalse))
        is Terminator.Return -> t.copy(value = t.value?.let(::ref))
        Terminator.Unreachable -> t
    }
}

internal fun Instruction.resultId(): Int? = when (this) {
    is Instruction.Read -> result
    is Instruction.Binary -> result
    is Instruction.Promote -> result
    is Instruction.Convert -> result
    is Instruction.View -> result
    is Instruction.Construct -> result
    is Instruction.Call -> result
    is Instruction.CaptureIndex -> result
    is Instruction.CaptureKey -> result
    is Instruction.MapProjection -> result
    is Instruction.MapMember -> result
    is Instruction.ListMember -> result
    is Instruction.DictionaryMember -> result
    else -> null
}

internal fun Instruction.references(): Set<Int> {
    val ids = linkedSetOf<Int>()
    object : IRTransform() {
        override fun ref(value: ValueRef): ValueRef {
            if (value is ValueRef.Result) ids.add(value.instruction)
            return super.ref(value)
        }
        override fun result(value: Int) = value // Destination IDs are not uses.
    }.instruction(this)
    // Captured index IDs are encoded directly in Location rather than ValueRef.
    fun capture(location: Location) { ids.addAll(location.indices.values); location.capturedLocations.values.forEach(::capture) }
    when (this) {
        is Instruction.Read -> capture(location)
        is Instruction.Write -> capture(location)
        is Instruction.CaptureIndex -> capture(container)
        is Instruction.MapProjection -> capture(receiver)
        is Instruction.MapMember -> capture(receiver)
        is Instruction.ListMember -> capture(receiver)
        is Instruction.DictionaryMember -> capture(receiver)
        else -> Unit
    }
    return ids
}

internal fun Terminator.successors(): List<Int> = when (this) {
    is Terminator.Jump -> listOf(block)
    is Terminator.Branch -> listOf(whenTrue, whenFalse)
    else -> emptyList()
}

internal fun ValueRef.resultReferences(): Set<Int> = when (this) {
    is ValueRef.Result -> setOf(instruction)
    is ValueRef.TypedView -> source.resultReferences() + location.indices.values +
        (location.keys.values + location.captured.values).flatMap { it.resultReferences() }
    else -> emptySet()
}
