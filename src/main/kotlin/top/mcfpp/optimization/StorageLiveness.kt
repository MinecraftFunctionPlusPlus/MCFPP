package top.mcfpp.optimization

import top.mcfpp.analysis.*

internal object StorageLiveness {
    fun atCalls(ir: TypedIR): Map<Pair<Int, Int>, Set<Place>> {
        val entries = ir.blocks.associate { it.id to emptySet<Place>() }.toMutableMap()
        val calls = mutableMapOf<Pair<Int, Int>, Set<Place>>()
        do {
            var changed = false
            for (block in ir.blocks.asReversed()) {
                val live = block.terminator.successors().flatMap { entries[it].orEmpty() }.toMutableSet()
                for ((index, instruction) in block.instructions.withIndex().reversed()) {
                    when (instruction) {
                        is Instruction.Call -> calls[block.id to index] = live.toSet()
                        is Instruction.Read -> live.add(instruction.place)
                        is Instruction.Write -> live.remove(instruction.place)
                        is Instruction.RawCommand -> live.addAll(ir.declarations.keys.map(::Place))
                        is Instruction.View -> live.add(instruction.value.place)
                        else -> Unit
                    }
                }
                if (entries[block.id] != live) { entries[block.id] = live; changed = true }
            }
        } while (changed)
        return calls
    }
}
