package top.mcfpp.analysis

import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.TypeId

enum class ListOperation(val bulk: Boolean = false) {
    CLEAR, APPEND, PREPEND, APPEND_ALL(true), PREPEND_ALL(true), INSERT, REMOVE_AT,
    INDEX_OF, LAST_INDEX_OF, CONTAINS, REMOVE;

    val query: Boolean get() = this == INDEX_OF || this == LAST_INDEX_OF || this == CONTAINS
    val search: Boolean get() = query || this == REMOVE
}

sealed interface ListMatchScope {
    object All : ListMatchScope
    data class Indices(val values: List<Int>) : ListMatchScope
    object Unknown : ListMatchScope
}

/** List splices move complete element subtrees, including nested collection lengths. */
object ListFacts {
    fun matchScope(state: FlowFacts, instruction: Instruction.ListMember, needle: TypeId?): ListMatchScope {
        if (needle == null || needle == MCFPPBaseType.Any.typeId || needle == MCFPPBaseType.Object.typeId) return ListMatchScope.Unknown
        val generic = (instruction.type as TypeId.Applied).arguments.single()
        if (generic != MCFPPBaseType.Any.typeId && generic != MCFPPBaseType.Object.typeId) return ListMatchScope.All
        val place = instruction.receiver.place
        val size = state.length(place)
        if (size != null) {
            val types = (0 until size).map { (state.read(place.index(it))?.type as? TypeKnowledge.Exact)?.type ?: return ListMatchScope.Unknown }
            return ListMatchScope.Indices(types.indices.filter { types[it] == needle })
        }
        val common = (state.read(place.unknownIndex())?.type as? TypeKnowledge.Exact)?.type ?: return ListMatchScope.Unknown
        return if (common == needle) ListMatchScope.All else ListMatchScope.Indices(emptyList())
    }

    fun find(state: FlowFacts, instruction: Instruction.ListMember, needle: ValueFacts): Int? {
        fun constant(fact: ValueFacts?): CompilerValue? {
            if (fact?.state != ValueState.INITIALIZED) return null
            val value = (fact.value as? ValueKnowledge.Constant)?.value ?: return null
            return if (value is CompilerValue.Typed) value else (fact.type as? TypeKnowledge.Exact)?.type?.let { CompilerValue.Typed(it, value) }
        }
        val place = instruction.receiver.place
        val size = state.length(place) ?: return null
        val elements = (0 until size).map { constant(state.read(place.index(it))) ?: return null }
        return ListValues.indexOf(elements, constant(needle) ?: return null, instruction.operation == ListOperation.LAST_INDEX_OF)
    }

    fun position(operation: ListOperation, index: Int?, size: Int?): Int? = when (operation) {
        ListOperation.APPEND, ListOperation.APPEND_ALL -> size
        ListOperation.PREPEND, ListOperation.PREPEND_ALL, ListOperation.CLEAR -> 0
        else -> index?.let { if (it >= 0) it else size?.plus(it)?.plus(if (operation == ListOperation.INSERT) 1 else 0) }
    }

    private fun element(state: FlowFacts, place: Place, type: TypeId): TypeKnowledge =
        state.read(place.unknownIndex())?.type ?: (type as? TypeId.Applied)?.arguments?.single()
            ?.takeUnless { it == MCFPPBaseType.Any.typeId || it == MCFPPBaseType.Object.typeId }
            ?.let(TypeKnowledge::Exact) ?: TypeKnowledge.Unknown

    fun edit(state: FlowFacts, instruction: Instruction.ListMember, source: FlowFacts?, from: Place?, value: ValueFacts?) {
        val place = instruction.receiver.place
        val old = state.fork()
        val root = ValueFacts(old.read(place)?.type ?: TypeKnowledge.Exact(instruction.type), ValueKnowledge.Unknown)
        val size = old.length(place)
        val start = position(instruction.operation, instruction.knownIndex, size)
        val added = when {
            instruction.operation == ListOperation.CLEAR || instruction.operation == ListOperation.REMOVE_AT -> 0
            instruction.operation.bulk -> from?.let { source?.length(it) }
            else -> 1
        }
        if (instruction.operation.bulk && added == 0) return
        val previous = element(old, place, instruction.type)
        val incoming = if (added == 0) null else if (instruction.operation.bulk && source != null && from != null)
            element(source, from, instruction.argument!!.type) else value?.type
        state.forgetDescendants(place)
        state.write(place, root)
        // One selected list cannot establish new element facts for every list in an unknown range.
        if (PathSegment.UnknownIndex in place.path) return
        if (instruction.operation == ListOperation.CLEAR) {
            state.setLength(place, 0)
            state.refine(place, root.copy(value = ValueKnowledge.Constant(CompilerValue.Typed(instruction.type, CompilerValue.Sequence(emptyList())))))
            return
        }
        fun copy(facts: FlowFacts?, origin: Place?, target: Place, fallback: ValueFacts) {
            if (facts != null && origin != null) state.copyFrom(facts, origin, target)
            if (state.read(target) == null) state.refine(target, fallback)
        }
        if (size != null && start != null && added != null) {
            val removed = if (instruction.operation == ListOperation.REMOVE_AT) 1 else 0
            // Bounds errors are reported before command generation; avoid manufacturing an invalid shape meanwhile.
            if (start !in 0..(size - removed)) return
            for (index in 0 until size) {
                if (index in start until start + removed) continue
                val target = if (index < start) index else index - removed + added
                copy(old, place.index(index), place.index(target), ValueFacts(previous, ValueKnowledge.Unknown))
            }
            repeat(added) { index ->
                copy(source, if (instruction.operation.bulk) from?.index(index) else from, place.index(start + index),
                    if (instruction.operation.bulk) ValueFacts(incoming ?: TypeKnowledge.Unknown, ValueKnowledge.Unknown) else value!!)
            }
            state.setLength(place, size - removed + added)
        } else {
            // Appending leaves every existing position intact, even after the loop loses its exact length.
            if (instruction.operation == ListOperation.APPEND || instruction.operation == ListOperation.APPEND_ALL)
                state.copyFrom(old, place, place, includeRoot = false)
            val possible = if (incoming == null) previous else if (size == 0) incoming else previous.join(incoming)
            state.refine(place.unknownIndex(), ValueFacts(possible, ValueKnowledge.Unknown))
        }
    }
}
