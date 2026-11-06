package top.mcfpp.optimization

import top.mcfpp.analysis.*
import top.mcfpp.type.TypeId

/** Remove whole private list allocations whose contents never reach an observable operation.
 * Storage roots form a conservative dependency graph across all blocks: keeping one read keeps
 * every write to that root. This deliberately avoids relying on statement order or alias guesses.
 * Calls, unknown commands, captured addresses and indexed edits remain observation barriers.
 */
internal object DeadLocalLists {
    fun run(ir: TypedIR): TypedIR {
        val privateRoots = ir.declarations.values.filter { symbol ->
            val type = symbol.declaredType as? TypeId.Applied
            type?.constructor == TypeId.Builtin("list") && !symbol.forceRuntime &&
                symbol.id !in ir.parameters && symbol.id !in ir.externalRoots
        }.mapTo(hashSetOf()) { it.id }
        if (privateRoots.isEmpty()) return ir

        fun root(i: Instruction): SymbolId? = when (i) {
            is Instruction.Declare -> i.symbol.id
            is Instruction.Construct -> i.place.root
            is Instruction.Read -> i.place.root
            is Instruction.Write -> i.place.root
            is Instruction.ListMember -> i.receiver.place.root
            else -> null
        }
        fun whole(location: Location) = location.place.path.isEmpty() && location.frameOffset == 0 &&
            location.indices.isEmpty() && location.keys.isEmpty() && location.captured.isEmpty() && location.capturedLocations.isEmpty()
        fun candidate(i: Instruction): Boolean = root(i) in privateRoots && when (i) {
            is Instruction.Declare, is Instruction.Construct, is Instruction.Read -> true
            is Instruction.Write -> whole(i.location)
            is Instruction.ListMember -> whole(i.receiver) && (i.operation.query || i.operation in setOf(
                ListOperation.APPEND, ListOperation.APPEND_ALL, ListOperation.PREPEND, ListOperation.PREPEND_ALL, ListOperation.CLEAR))
            else -> false
        }

        val instructions = ir.blocks.flatMap { it.instructions }
        val producers = instructions.withIndex().mapNotNull { (index, i) -> i.resultId()?.let { it to index } }.toMap()
        val storage = instructions.indices.filter { root(instructions[it]) in privateRoots }.groupBy { root(instructions[it])!! }
        val retained = hashSetOf<Int>()
        val liveRoots = hashSetOf<SymbolId>()
        val pending = ArrayDeque<Int>()
        fun keep(index: Int) { if (retained.add(index)) pending.addLast(index) }
        fun observe(root: SymbolId) { if (liveRoots.add(root)) storage[root].orEmpty().forEach(::keep) }
        val uses = object : IRTransform() {
            override fun ref(value: ValueRef): ValueRef {
                when (value) {
                    is ValueRef.Result -> producers[value.instruction]?.let(::keep)
                    is ValueRef.Read -> observe(value.place.root)
                    is ValueRef.TypedView -> observe(value.place.root)
                    else -> Unit
                }
                return super.ref(value)
            }
        }
        instructions.indices.filter { !candidate(instructions[it]) }.forEach(::keep)
        ir.blocks.forEach { uses.terminator(it.terminator) }
        while (pending.isNotEmpty()) {
            val instruction = instructions[pending.removeFirst()]
            uses.instruction(instruction)
            instruction.references().forEach { producers[it]?.let(::keep) }
            root(instruction)?.let(::observe)
            when (instruction) {
                is Instruction.Call -> privateRoots.forEach(::observe)
                is Instruction.RawCommand -> if (EffectAnalysis.rawCommandEffect(instruction.command) == Effect.Unknown) privateRoots.forEach(::observe)
                is Instruction.View -> observe(instruction.value.place.root)
                is Instruction.CaptureIndex -> observe(instruction.container.place.root)
                else -> Unit
            }
        }
        var index = 0
        val blocks = ir.blocks.map { block -> block.copy(instructions = block.instructions.filter { retained.contains(index++) }) }
        return ir.copy(blocks = blocks, declarations = ir.declarations.filterKeys { it !in privateRoots || it in liveRoots })
    }
}
