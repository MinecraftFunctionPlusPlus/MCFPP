package top.mcfpp.optimization

import top.mcfpp.analysis.*

/** 调用前后仍活跃的 IR 临时结果，仅供寄存器保存决策使用，不提供初始化证明。 */
internal object ResultLiveness {
    /** Only code-generation liveness: initialization and value evidence remain in FlowFacts. */
    fun atCalls(ir: TypedIR, reachableBlocks: Set<Int>): Map<Pair<Int, Int>, Set<Int>> {
        fun references(value: ValueRef): Set<Int> = when (value) {
            is ValueRef.Result -> setOf(value.instruction)
            is ValueRef.TypedView -> references(value.source) + locationReferences(value.location)
            else -> emptySet()
        }
        fun instructionReferences(instruction: Instruction): Set<Int> = when (instruction) {
            is Instruction.Declare -> emptySet()
            is Instruction.Read -> locationReferences(instruction.location)
            is Instruction.Write -> references(instruction.value) + locationReferences(instruction.location)
            is Instruction.CaptureIndex -> references(instruction.value) + locationReferences(instruction.container)
            is Instruction.CaptureKey -> references(instruction.value)
            is Instruction.Binary -> references(instruction.left) + references(instruction.right)
            is Instruction.Promote -> references(instruction.value)
            is Instruction.Convert -> references(instruction.value)
            is Instruction.View -> references(instruction.value)
            is Instruction.Construct -> instruction.parts.values.flatMap { references(it) }.toSet()
            is Instruction.Call -> instruction.arguments.flatMap { references(it) }.toSet()
            is Instruction.ListMember -> locationReferences(instruction.receiver) +
                listOfNotNull(instruction.argument, instruction.index).flatMap { references(it) }
            is Instruction.DictionaryMember -> locationReferences(instruction.receiver) +
                instruction.argument?.let(::references).orEmpty()
            is Instruction.MapMember -> locationReferences(instruction.receiver) +
                listOfNotNull(instruction.key, instruction.argument).flatMap { references(it) }
            is Instruction.MapProjection -> locationReferences(instruction.receiver)
            is Instruction.RawCommand -> emptySet()
        }
        val blocks = ir.blocks.filter { it.id in reachableBlocks }
        val incoming = blocks.associate { it.id to emptySet<Int>() }.toMutableMap()
        val calls = mutableMapOf<Pair<Int, Int>, Set<Int>>()
        do {
            var changed = false
            for (block in blocks.asReversed()) {
                val live = when (val exit = block.terminator) {
                    is Terminator.Jump -> incoming[exit.block].orEmpty()
                    is Terminator.Branch -> incoming[exit.whenTrue].orEmpty() + incoming[exit.whenFalse].orEmpty() + references(exit.condition)
                    is Terminator.Return -> exit.value?.let(::references).orEmpty()
                    Terminator.Unreachable -> emptySet()
                }.toMutableSet()
                for ((position, instruction) in block.instructions.withIndex().reversed()) {
                    if (instruction is Instruction.Call) calls[block.id to position] = live - setOfNotNull(instruction.result)
                    instruction.resultId()?.let(live::remove)
                    live.addAll(instructionReferences(instruction))
                }
                if (incoming[block.id] != live) { incoming[block.id] = live; changed = true }
            }
        } while (changed)
        return calls
    }
    private fun locationReferences(location: Location): Set<Int> = location.indices.values.toSet() +
        (location.keys.values + location.captured.values).flatMap { value ->
            fun ids(reference: ValueRef): Set<Int> = when (reference) {
                is ValueRef.Result -> setOf(reference.instruction)
                is ValueRef.TypedView -> ids(reference.source) + locationReferences(reference.location)
                else -> emptySet()
            }
            ids(value)
        }
}
