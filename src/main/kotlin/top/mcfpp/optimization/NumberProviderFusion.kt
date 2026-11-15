package top.mcfpp.optimization

import top.mcfpp.analysis.*
import top.mcfpp.command.FloatProviders
import top.mcfpp.type.MCFPPBaseType

/**
 * Plan native provider trees after scalar optimization and inlining have finished.
 * Keep SSA definitions for flow/type analysis; lowering omits the selected materializations
 * and expands those definitions at their sole use. Reads still produce immutable snapshots.
 * Native nested providers propagate non-finite intermediates until the enclosing evaluation.
 */
internal class NumberProviderFusion : FunctionPass {
    override val id = PassIds.PROVIDERS

    override fun run(ir: TypedIR, context: PassContext): TypedIR {
        if (!FloatProviders.enabled) return ir
        val float = MCFPPBaseType.Float.typeId
        val arithmetic = setOf("+", "-", "*", "/", "%")
        val comparisons = setOf("==", "!=", "<", "<=", ">", ">=")
        // Include uses outside the candidate block and in terminators. A shared result
        // remains materialized, even when both uses belong to the same consumer.
        val uses = ir.blocks.flatMap { block ->
            block.instructions.flatMap { it.references() } + when (val exit = block.terminator) {
                is Terminator.Return -> exit.value?.resultReferences().orEmpty()
                is Terminator.Branch -> exit.condition.resultReferences()
                else -> emptySet()
            }
        }.groupingBy { it }.eachCount()
        val fused = linkedSetOf<Int>()
        for (block in ir.blocks) {
            val available = mutableMapOf<Int, Instruction.Binary>()
            for (instruction in block.instructions) when (instruction) {
                is Instruction.Binary -> {
                    if (instruction.left.type == float && instruction.right.type == float &&
                        instruction.operation in arithmetic + comparisons) {
                        for (operand in listOf(instruction.left, instruction.right)) {
                            val result = (operand as? ValueRef.Result)?.instruction ?: continue
                            if (uses[result] == 1 && instruction.left != instruction.right && result in available)
                                fused.add(result)
                        }
                        if (instruction.type == float && instruction.operation in arithmetic)
                            available[instruction.result] = instruction
                    }
                }
                is Instruction.Declare, is Instruction.Read, is Instruction.Promote -> Unit
                // Preserve calls, writes, address captures and opaque commands as boundaries.
                else -> available.clear()
            }
        }
        return ir.copy(fusedFloatResults = fused)
    }
}
