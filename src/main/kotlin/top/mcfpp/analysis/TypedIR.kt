package top.mcfpp.analysis

import top.mcfpp.type.TypeId

/** Basic blocks and explicit reads/writes, with no dependency on Var implementation classes. */
data class TypedIR(val entry: Int, val blocks: List<BasicBlock>, val runtimeValues: Set<Int> = emptySet())
data class BasicBlock(val id: Int, val instructions: List<Instruction>, val terminator: Terminator)

sealed interface Instruction {
    data class Read(val result: Int, val place: Place, val type: TypeId) : Instruction
    data class Write(val place: Place, val value: ValueRef) : Instruction
    data class Binary(val result: Int, val operation: String, val left: ValueRef, val right: ValueRef, val type: TypeId) : Instruction
    data class Promote(val result: Int, val value: ValueRef, val type: TypeId) : Instruction
    data class Convert(val result: Int, val value: ValueRef, val type: TypeId) : Instruction
    data class View(val result: Int, val value: ValueRef.TypedView) : Instruction
    data class Call(val result: Int?, val declaration: SymbolId, val arguments: List<ValueRef>, val effect: Effect) : Instruction
    data class RawCommand(val command: String) : Instruction
}
sealed interface Terminator {
    data class Jump(val block: Int) : Terminator
    data class Branch(val condition: ValueRef, val whenTrue: Int, val whenFalse: Int) : Terminator
    data class Return(val value: ValueRef?) : Terminator
    object Unreachable : Terminator
}
sealed interface Effect {
    object Pure : Effect
    object ReadsRuntime : Effect
    data class Writes(val places: Set<Place>) : Effect
    object Unknown : Effect
}

/** Conservative forward fixed point, including backedges and unreachable predecessor filtering. */
object FlowAnalysis {
    data class Result(val entries: Map<Int, FlowFacts>, val exits: Map<Int, FlowFacts>, val values: Map<Pair<Int, Int>, ValueFacts>)

    fun analyze(ir: TypedIR, initial: FlowFacts = FlowFacts(), evaluator: (String, CompilerValue, CompilerValue) -> CompilerValue? = { _, _, _ -> null }, canFoldBranch: (ValueRef) -> Boolean = { it !is ValueRef.Result || it.instruction !in ir.runtimeValues }): Result {
        require(ir.blocks.map { it.id }.distinct().size == ir.blocks.size)
        val blocks = ir.blocks.associateBy { it.id }
        require(ir.entry in blocks)
        val entries = mutableMapOf(ir.entry to initial.fork())
        val exits = mutableMapOf<Int, FlowFacts>()
        val resultFacts = mutableMapOf<Pair<Int, Int>, ValueFacts>()
        val pending = ArrayDeque<Int>().apply { add(ir.entry) }
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val block = blocks.getValue(id)
            val state = entries.getValue(id).fork()
            val values = mutableMapOf<Int, ValueFacts>()
            fun value(ref: ValueRef): ValueFacts = when (ref) {
                is ValueRef.Constant -> ValueFacts(TypeKnowledge.Exact(ref.type), ValueKnowledge.Constant(ref.value))
                is ValueRef.Read -> state.read(ref.place) ?: ValueFacts(TypeKnowledge.Exact(ref.type), ValueKnowledge.Unknown, ValueState.UNINITIALIZED)
                is ValueRef.Result -> values[ref.instruction] ?: ValueFacts(TypeKnowledge.Exact(ref.type), ValueKnowledge.Unknown)
                is ValueRef.TypedView -> ValueFacts(TypeKnowledge.Exact(ref.type), ValueKnowledge.Unknown)
            }
            for (instruction in block.instructions) when (instruction) {
                is Instruction.Read -> values[instruction.result] = value(ValueRef.Read(instruction.type, instruction.place))
                is Instruction.Write -> state.write(instruction.place, value(instruction.value))
                is Instruction.Call -> when (val effect = instruction.effect) {
                    is Effect.Writes -> effect.places.forEach(state::invalidate)
                    Effect.Unknown -> state.barrier()
                    else -> Unit
                }
                is Instruction.RawCommand -> state.barrier()
                is Instruction.View -> values[instruction.result] = value(instruction.value)
                is Instruction.Binary -> {
                    val leftFact = value(instruction.left)
                    val rightFact = value(instruction.right)
                    // Only any exposes actual-type knowledge. Object and concrete signatures
                    // retain their static operators even when the payload type is known.
                    fun operandType(ref: ValueRef, fact: ValueFacts): TypeId? =
                        if (ref.type == top.mcfpp.type.MCFPPBaseType.Any.typeId) (fact.type as? TypeKnowledge.Exact)?.type else ref.type
                    val leftType = operandType(instruction.left, leftFact)
                    val rightType = operandType(instruction.right, rightFact)
                    val type = if (leftType != null && rightType != null)
                        top.mcfpp.type.TypeRelations.resolveOperator(instruction.operation, leftType, rightType) else null
                    val left = (leftFact.value as? ValueKnowledge.Constant)?.value
                    val right = (rightFact.value as? ValueKnowledge.Constant)?.value
                    val folded = if (left != null && right != null) evaluator(instruction.operation, left, right) else null
                    values[instruction.result] = ValueFacts(type?.let { TypeKnowledge.Exact(it) } ?: TypeKnowledge.Unknown,
                        if (type != null && folded != null) ValueKnowledge.Constant(folded) else ValueKnowledge.Unknown)
                }
                is Instruction.Promote -> values[instruction.result] = ValueFacts(TypeKnowledge.Exact(instruction.type), ValueKnowledge.Unknown)
                is Instruction.Convert -> values[instruction.result] = ValueFacts(TypeKnowledge.Exact(instruction.type), ValueKnowledge.Unknown)
            }
            values.forEach { (result, fact) -> resultFacts[id to result] = fact }
            if (exits[id] == state) continue
            exits[id] = state
            val successors = when (val terminator = block.terminator) {
                is Terminator.Jump -> listOf(terminator.block)
                is Terminator.Branch -> when (if (canFoldBranch(terminator.condition)) (value(terminator.condition).value as? ValueKnowledge.Constant)?.value else null) {
                    CompilerValue.Bool(true) -> listOf(terminator.whenTrue)
                    CompilerValue.Bool(false) -> listOf(terminator.whenFalse)
                    else -> listOf(terminator.whenTrue, terminator.whenFalse)
                }
                else -> emptyList()
            }
            for (successor in successors) {
                require(successor in blocks) { "Missing basic block $successor" }
                val joined = entries[successor]?.join(state) ?: state.fork()
                if (entries[successor] != joined) {
                    entries[successor] = joined
                    pending.add(successor)
                }
            }
        }
        return Result(entries, exits, resultFacts)
    }
}
