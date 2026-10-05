package top.mcfpp.analysis

import top.mcfpp.type.TypeId

enum class DictionaryOperation { CLEAR, REMOVE, MERGE, CONTAINS_KEY }

/** Merge frozen source evidence with the live receiver, following NBT's recursive compound merge. */
object DictionaryFacts {
    private fun payload(value: CompilerValue?): CompilerValue? =
        if (value is CompilerValue.Typed) payload(value.payload) else value

    private fun dictionary(type: TypeKnowledge): Boolean =
        ((type as? TypeKnowledge.Exact)?.type as? TypeId.Applied)?.constructor == TypeId.Builtin("dict")

    fun merge(state: FlowFacts, destination: Place, source: FlowFacts, from: Place) {
        val incoming = source.read(from) ?: ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown)
        val old = state.read(destination)
        val complete = payload((incoming.value as? ValueKnowledge.Constant)?.value) is CompilerValue.Record
        val root = ValueFacts(old?.type?.takeIf(::dictionary) ?: incoming.type, ValueKnowledge.Unknown)
        // Unknown input may contain additional keys and overwrite any existing field.
        if (!complete || old?.type?.let(::dictionary) != true) {
            state.forgetDescendants(destination)
            state.write(destination, root)
        }
        for ((child, fact) in source.children(from)) {
            if (fact.state != ValueState.INITIALIZED) continue
            val target = Place(destination.root, destination.path + child.path.last())
            if (dictionary(fact.type)) merge(state, target, source, child)
            else {
                state.forgetDescendants(target)
                state.write(target, fact)
                state.copyFrom(source, child, target, includeRoot = false)
            }
        }
        // A selected unknown index cannot prove a fact about every element in its range.
        if (PathSegment.UnknownIndex !in destination.path) state.refine(destination, root)
    }
}
