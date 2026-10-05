package top.mcfpp.analysis

import top.mcfpp.command.TargetCapability
import top.mcfpp.type.*

/** Encoding gates use immutable type/shape evidence before the backend emits any command. */
object IRCollectionValidation {
    private fun codec(type: TypeId): String? = when (type) {
        MCFPPBaseType.Int.typeId -> "int"
        MCFPPBaseType.Bool.typeId, MCFPPNBTType.Byte.typeId -> "byte"
        MCFPPNBTType.Long.typeId -> "long"
        MCFPPNBTType.ByteArray.typeId -> "byte_array"
        MCFPPNBTType.IntArray.typeId -> "int_array"
        MCFPPNBTType.LongArray.typeId -> "long_array"
        MCFPPBaseType.String.typeId -> "string"
        is TypeId.Applied -> when (type.constructor) {
            TypeId.Builtin("list"), TypeId.Builtin("ImmutableList") -> "list"
            TypeId.Builtin("dict"), TypeId.Builtin("map") -> "compound"
            else -> null
        }
        is TypeId.Union -> type.alternatives.map(::codec).takeUnless { it.any { part -> part == null } }?.distinct()?.singleOrNull()
        else -> null
    }
    private fun codec(type: TypeKnowledge): String? = when (type) {
        is TypeKnowledge.Exact -> codec(type.type)
        is TypeKnowledge.Candidates -> type.types.map(::codec).takeUnless { it.any { part -> part == null } }?.distinct()?.singleOrNull()
        TypeKnowledge.Unknown -> null
    }

    fun validate(ir: TypedIR, facts: FlowAnalysis.Result, types: Map<TypeId, MCFPPType>, target: TargetCapability): List<String> {
        val diagnostics = mutableListOf<String>()
        fun encoding(block: Int, value: ValueRef): String? = when (value) {
            is ValueRef.Result -> facts.values[block to value.instruction]?.type?.let(::codec) ?: codec(value.type)
            else -> codec(value.type)
        }
        fun elements(state: FlowFacts, place: Place?, type: TypeId): List<String?> {
            val size = place?.let(state::length)
            if (size != null) return (0 until size).map { codec(state.read(place.index(it))?.type ?: TypeKnowledge.Unknown) }
            place?.let { state.read(it.unknownIndex()) }?.type?.let(::codec)?.let { return listOf(it) }
            return listOf((type as? TypeId.Applied)?.arguments?.singleOrNull()?.let(::codec))
        }
        for (block in ir.blocks.filter { it.id in facts.entries }) for ((position, instruction) in block.instructions.withIndex()) {
            val location = when (instruction) {
                is Instruction.Read -> instruction.location
                is Instruction.Write -> instruction.location
                is Instruction.MapMember -> instruction.receiver
                is Instruction.MapProjection -> instruction.receiver
                is Instruction.ListMember -> instruction.receiver
                is Instruction.DictionaryMember -> instruction.receiver
                else -> null
            }
            if (!target.functionMacros && location?.keys?.values?.any { it is ValueRef.Result } == true)
                diagnostics += "Target '${target.version}' cannot access a map value at an unknown key without function macros"
            val beforeAccess = facts.beforeWrites[block.id to position]
            fun payload(value: CompilerValue?): CompilerValue? = if (value is CompilerValue.Typed) payload(value.payload) else value
            fun mapLayout(receiver: Place) {
                val known = payload((beforeAccess?.read(receiver)?.value as? ValueKnowledge.Constant)?.value)
                if (known is CompilerValue.Record && payload(known.fields["entries"]) !is CompilerValue.Sequence)
                    diagnostics += "Map access requires an entries list; as does not convert or initialize the old map layout"
            }
            if (instruction is Instruction.MapMember) mapLayout(instruction.receiver.place)
            if (instruction is Instruction.MapProjection) {
                val receiver = MapFacts.resolve(beforeAccess!!, instruction.receiver)
                mapLayout(receiver)
                if (instruction.dictionary) {
                    val keys = MapFacts.keys(beforeAccess, receiver.field("entries"))
                    if (keys == null) diagnostics += "Map dictionary projection requires known keys: no verified runtime NBT member-name escaping backend is available"
                    else if (keys.any(String::isEmpty)) diagnostics += "Runtime map dictionary projection cannot encode empty member names with the current NBT backend"
                }
            }
            location?.keys?.keys?.forEach { index -> mapLayout(Place(location.place.root, location.place.path.take(index - 1))) }
            if (instruction is Instruction.CaptureIndex && !target.functionMacros)
                diagnostics += "Target '${target.version}' cannot access a dynamic sequence index without function macros"
            val accessed = when (instruction) {
                is Instruction.Read -> instruction.place
                is Instruction.Write -> instruction.place
                is Instruction.DictionaryMember -> instruction.key?.let(instruction.receiver.place::field) ?: instruction.receiver.place
                is Instruction.ListMember -> instruction.receiver.place
                else -> null
            }
            if (!target.emptyNbtPathKeys && accessed?.path?.any { it is PathSegment.Field && it.name.isEmpty() } == true)
                diagnostics += "Target '${target.version}' cannot access an empty dictionary key"
            if (instruction is Instruction.ListMember) {
                val state = facts.beforeWrites.getValue(block.id to position)
                if (instruction.operation.search) {
                    val argument = instruction.argument!!
                    val needle = (argument as? ValueRef.Result)?.let { facts.values[block.id to it.instruction]?.type as? TypeKnowledge.Exact }?.type ?: argument.type
                    if (ListFacts.matchScope(state, instruction, needle) == ListMatchScope.Unknown)
                        diagnostics += "List lookup requires known needle and element types; use an explicit as view"
                }
                if (instruction.index != null) {
                    if (instruction.knownIndex == null && !target.functionMacros)
                        diagnostics += "Target '${target.version}' cannot modify a list at a runtime index without function macros"
                    val size = state.length(instruction.receiver.place)
                    val index = ListFacts.position(instruction.operation, instruction.knownIndex, size)
                    val removed = if (instruction.operation == ListOperation.REMOVE_AT) 1 else 0
                    if (size != null && index != null && index !in 0..(size - removed))
                        diagnostics += "List index ${instruction.knownIndex} is outside ${if (removed == 0) "insertion into" else "length"} $size"
                }
                if (!target.heterogeneousLists && instruction.argument != null && !instruction.operation.search) {
                    val incoming = if (instruction.operation.bulk) elements(state, instruction.argumentPlace, instruction.argument.type)
                        else listOf(encoding(block.id, instruction.argument))
                    val codecs = elements(state, instruction.receiver.place, instruction.type) + incoming
                    if (codecs.any { it == null } || codecs.distinct().size > 1)
                        diagnostics += "Target '${target.version}' requires a proven common NBT encoding for inserted list elements"
                }
            }
            if (target.heterogeneousLists) continue
            if (instruction is Instruction.Construct && instruction.sequence && instruction.parts.isNotEmpty()) {
                val codecs = instruction.parts.values.map { encoding(block.id, it) }
                if (codecs.any { it == null } || codecs.distinct().size != 1)
                    diagnostics += "Target '${target.version}' requires a proven common NBT encoding for list elements"
            }
            if (instruction !is Instruction.Write) continue
            val segment = instruction.place.path.lastOrNull()
            if (segment !is PathSegment.Index && segment != PathSegment.UnknownIndex) continue
            val state = facts.beforeWrites[block.id to position] ?: continue
            val parent = Place(instruction.place.root, instruction.place.path.dropLast(1))
            val actual = (state.read(parent)?.type as? TypeKnowledge.Exact)?.type?.let(types::get)
            val container = actual ?: instruction.containerType?.let(types::get)
            if (container !is MCFPPListType && container !is MCFPPImmutableListType) continue
            val children = state.children(parent).filterKeys { it.path.last() is PathSegment.Index }.values
            val common = if (state.length(parent) != null && children.size == state.length(parent) && children.isNotEmpty()) {
                val codecs = children.map { codec(it.type) }
                if (codecs.any { it == null }) null else codecs.distinct().singleOrNull()
            } else codec((container as MCFPPTypeWithGeneric).generic.single().typeId)
            val written = encoding(block.id, instruction.value)
            if (common == null || written == null || common != written)
                diagnostics += "Target '${target.version}' cannot write an incompatible or unproven NBT encoding to this list"
        }
        return diagnostics.distinct()
    }
}
