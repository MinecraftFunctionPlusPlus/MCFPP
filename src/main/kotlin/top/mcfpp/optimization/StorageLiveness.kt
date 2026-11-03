package top.mcfpp.optimization

import top.mcfpp.analysis.*

internal object StorageLiveness {
    fun atCalls(ir: TypedIR): Map<Pair<Int, Int>, Set<Place>> {
        val entries = ir.blocks.associate { it.id to emptySet<Place>() }.toMutableMap()
        val calls = mutableMapOf<Pair<Int, Int>, Set<Place>>()
        do {
            var changed = false
            for (block in ir.blocks.asReversed()) {
                var live = block.terminator.successors().flatMap { entries[it].orEmpty() }.toSet()
                for ((index, instruction) in block.instructions.withIndex().reversed()) {
                    when (instruction) {
                        is Instruction.Call -> calls[block.id to index] = live
                        is Instruction.Read -> live = live + instruction.place
                        is Instruction.Write -> live = live.filterNot { it == instruction.place }.toSet()
                        is Instruction.RawCommand -> live = live + ir.declarations.keys.map(::Place)
                        is Instruction.View -> live = live + instruction.value.place
                        else -> Unit
                    }
                }
                if (entries[block.id] != live) { entries[block.id] = live; changed = true }
            }
        } while (changed)
        return calls
    }
}
