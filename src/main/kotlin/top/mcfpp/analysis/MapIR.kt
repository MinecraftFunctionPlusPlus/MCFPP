package top.mcfpp.analysis

import top.mcfpp.type.*

enum class MapOperation {
    PUT, CLEAR, REMOVE, MERGE, CONTAINS_KEY, SIZE, IS_EMPTY;
    val query get() = this == CONTAINS_KEY || this == SIZE || this == IS_EMPTY
}

/** Map facts use the same physical entry paths as snapshots and aliases. */
object MapFacts {
    val rowType = MCFPPDictType(MCFPPBaseType.Any).typeId
    val entriesType = MCFPPListType(MCFPPDictType(MCFPPBaseType.Any)).typeId
    fun text(value: CompilerValue?): String? = when (value) {
        is CompilerValue.Typed -> text(value.payload)
        is CompilerValue.Text -> value.value
        else -> null
    }
    fun keys(state: FlowFacts, entries: Place): List<String>? {
        return (0 until (state.length(entries) ?: return null)).map {
            text((state.read(entries.index(it).field("key"))?.value as? ValueKnowledge.Constant)?.value) ?: return null
        }
    }
    fun resolve(state: FlowFacts, location: Location): Place {
        var place = Place(location.place.root)
        location.place.path.forEachIndexed { index, segment ->
            val key = (location.keys[index] as? ValueRef.Constant)?.value?.let(::text)
            val found = key?.let { keys(state, place)?.indexOf(it) }?.takeIf { it >= 0 }
            place = Place(place.root, place.path + (found?.let(PathSegment::Index) ?: segment))
        }
        return place
    }
    fun valueType(state: FlowFacts, receiver: Place, type: TypeId): TypeKnowledge {
        state.read(receiver.field("entries").unknownIndex().field("value"))?.let { return it.type }
        val element = (type as TypeId.Applied).arguments.single()
        return if (element in setOf(MCFPPBaseType.Any.typeId, MCFPPBaseType.Object.typeId)) TypeKnowledge.Unknown else TypeKnowledge.Exact(element)
    }
    fun project(state: FlowFacts, instruction: Instruction.MapProjection): ValueFacts {
        val source = state.fork()
        val entries = resolve(source, instruction.receiver).field("entries")
        val destination = instruction.place
        val size = source.length(entries)
        val names = keys(source, entries)
        val parts = linkedMapOf<PathSegment, CompilerValue>()
        state.forgetDescendants(destination)
        val root = ValueFacts(TypeKnowledge.Exact(instruction.type), ValueKnowledge.Unknown)
        state.write(destination, root)
        if (!instruction.dictionary && size != null) state.setLength(destination, size)
        if (size != null && (!instruction.dictionary || names != null)) for (index in 0 until size) {
            val from = entries.index(index).field(if (instruction.dictionary) "value" else "key")
            val segment = if (instruction.dictionary) PathSegment.Field(names!![index]) else PathSegment.Index(index)
            val generic = (instruction.type as TypeId.Applied).arguments.single()
            val declared = if (generic in setOf(MCFPPBaseType.Any.typeId, MCFPPBaseType.Object.typeId)) TypeKnowledge.Unknown else TypeKnowledge.Exact(generic)
            val fact = source.read(from) ?: ValueFacts(declared, ValueKnowledge.Unknown)
            val child = Place(destination.root, destination.path + segment)
            state.write(child, fact)
            state.copyFrom(source, from, child, includeRoot = false)
            (fact.value as? ValueKnowledge.Constant)?.value?.let {
                parts[segment] = if (it is CompilerValue.Typed) it else CompilerValue.Typed((fact.type as? TypeKnowledge.Exact)?.type ?: generic, it)
            }
        }
        val complete = size != null && parts.size == size && (!instruction.dictionary || names != null)
        val constant = if (!complete) null else if (instruction.dictionary)
            CompilerValue.Record(parts.mapKeys { (it.key as PathSegment.Field).name }) else CompilerValue.Sequence(parts.values.toList())
        val result = root.copy(value = constant?.let { ValueKnowledge.Constant(CompilerValue.Typed(instruction.type, it)) } ?: ValueKnowledge.Unknown)
        state.refine(destination, result)
        return result
    }
    private fun invalidate(state: FlowFacts, receiver: Place, possible: TypeKnowledge) {
        val entries = receiver.field("entries")
        state.forgetDescendants(entries)
        state.write(entries, ValueFacts(TypeKnowledge.Exact(entriesType), ValueKnowledge.Unknown))
        if (PathSegment.UnknownIndex in receiver.path) return
        state.initialize(entries.unknownIndex().field("key"), ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.String.typeId), ValueKnowledge.Unknown))
        state.initialize(entries.unknownIndex().field("value"), ValueFacts(possible, ValueKnowledge.Unknown))
    }
    private fun put(state: FlowFacts, receiver: Place, type: TypeId, key: String?, value: ValueFacts,
                    source: FlowFacts?, from: Place?) {
        val entries = receiver.field("entries")
        val names = keys(state, entries)
        if (key == null || names == null || PathSegment.UnknownIndex in receiver.path) {
            val possible = if (state.length(entries) == 0) value.type else valueType(state, receiver, type).join(value.type)
            invalidate(state, receiver, possible)
            return
        }
        val index = names.indexOf(key).takeIf { it >= 0 } ?: names.size
        val row = entries.index(index)
        state.write(row, ValueFacts(TypeKnowledge.Exact(rowType), ValueKnowledge.Unknown))
        state.write(row.field("key"), ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.String.typeId), ValueKnowledge.Constant(CompilerValue.Text(key))))
        state.forgetDescendants(row.field("value"))
        state.write(row.field("value"), value)
        if (source != null && from != null) state.copyFrom(source, from, row.field("value"), includeRoot = false)
        state.setLength(entries, maxOf(names.size, index + 1))
    }
    fun edit(state: FlowFacts, instruction: Instruction.MapMember, key: String?, argument: ValueFacts?,
             source: FlowFacts?, from: Place?) {
        val receiver = resolve(state, instruction.receiver)
        val entries = receiver.field("entries")
        when (instruction.operation) {
            MapOperation.PUT -> put(state, receiver, instruction.type, key, argument!!, source, from)
            MapOperation.CLEAR -> ListFacts.edit(state, Instruction.ListMember(ListOperation.CLEAR, Location(entries), entriesType), null, null, null)
            MapOperation.REMOVE -> {
                val names = keys(state, entries)
                if (key != null && names != null) {
                    val index = names.indexOf(key)
                    if (index >= 0) ListFacts.edit(state, Instruction.ListMember(ListOperation.REMOVE_AT,
                        Location(entries), entriesType, knownIndex = index), null, null, null)
                } else invalidate(state, receiver, valueType(state, receiver, instruction.type))
            }
            MapOperation.MERGE -> {
                val names = if (source != null && from != null) keys(source, from.field("entries")) else null
                if (names != null) names.forEachIndexed { index, name ->
                    val child = from!!.field("entries").index(index).field("value")
                    put(state, receiver, instruction.type, name, source!!.read(child) ?: ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown), source, child)
                } else {
                    val incoming = if (source != null && from != null) valueType(source, from, instruction.type)
                        else (instruction.type as TypeId.Applied).arguments.single().let {
                            if (it == MCFPPBaseType.Any.typeId || it == MCFPPBaseType.Object.typeId) TypeKnowledge.Unknown else TypeKnowledge.Exact(it)
                        }
                    invalidate(state, receiver, if (state.length(entries) == 0) incoming else valueType(state, receiver, instruction.type).join(incoming))
                }
            }
            else -> Unit
        }
    }
}
