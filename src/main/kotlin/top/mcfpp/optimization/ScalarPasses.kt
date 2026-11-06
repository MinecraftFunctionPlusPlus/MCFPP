package top.mcfpp.optimization

import top.mcfpp.analysis.*
import top.mcfpp.type.MCFPPBaseType

internal class ConstantPropagation : FunctionPass {
    override val id = PassIds.CONSTANTS
    override val requires = setOf(AnalysisKind.FLOW)
    private fun scalar(value: CompilerValue): Boolean = when (value) {
        is CompilerValue.Typed -> scalar(value.payload)
        is CompilerValue.Record, is CompilerValue.Sequence, is CompilerValue.Nbt -> false
        else -> true
    }
    override fun run(ir: TypedIR, context: PassContext): TypedIR {
        val normalized = ir.copy(runtimeValues = RuntimeValues.analyze(ir))
        val facts = context.flow(normalized)
        return normalized.copy(blocks = normalized.blocks.map { block ->
            val rewrite = object : IRTransform() {
                override fun ref(value: ValueRef): ValueRef {
                    if (value is ValueRef.Result && value.instruction !in normalized.runtimeValues) {
                        val fact = facts.values[block.id to value.instruction]
                        val constant = (fact?.value as? ValueKnowledge.Constant)?.value
                        // Aggregate result references also carry captured storage/layout evidence.
                        // Replacing them with payloads would detach typed views from that evidence.
                        if (constant != null && scalar(constant) && fact.state == ValueState.INITIALIZED)
                            return ValueRef.Constant(value.type, constant)
                    }
                    return super.ref(value)
                }
            }
            block.copy(instructions = block.instructions.map(rewrite::instruction), terminator = rewrite.terminator(block.terminator))
        })
    }
}

internal class AlgebraicSimplification : FunctionPass {
    override val id = PassIds.ALGEBRA
    override fun run(ir: TypedIR, context: PassContext): TypedIR {
        val reassociated = ir.copy(blocks = ir.blocks.map { block ->
            val definitions = mutableMapOf<Int, Instruction.Binary>()
            block.copy(instructions = block.instructions.map instruction@ { original ->
                if (original !is Instruction.Binary || original.type != MCFPPBaseType.Int.typeId) return@instruction original
                var current = original
                if (current.operation in setOf("+", "*") && current.left is ValueRef.Constant && current.right !is ValueRef.Constant)
                    current = current.copy(left = current.right, right = current.left)
                val inner = (current.left as? ValueRef.Result)?.let { definitions[it.instruction] }
                val leftConstant = (inner?.right as? ValueRef.Constant)?.value as? CompilerValue.Integral
                val rightConstant = (current.right as? ValueRef.Constant)?.value as? CompilerValue.Integral
                if (inner != null && leftConstant != null && rightConstant != null) {
                    val combined = when {
                        inner.operation in setOf("+", "-") && current.operation in setOf("+", "-") -> {
                            val left = if (inner.operation == "-") PrimitiveEvaluation.unary("-", leftConstant)!! else leftConstant
                            val right = if (current.operation == "-") PrimitiveEvaluation.unary("-", rightConstant)!! else rightConstant
                            PrimitiveEvaluation.binary("+", left, right)?.let { "+" to it }
                        }
                        inner.operation == "*" && current.operation == "*" -> PrimitiveEvaluation.binary("*", leftConstant, rightConstant)?.let { "*" to it }
                        else -> null
                    }
                    if (combined != null) current = current.copy(operation = combined.first, left = inner.left,
                        right = ValueRef.Constant(current.type, combined.second))
                }
                definitions[current.result] = current
                current
            })
        })
        return substituteBlocks(reassociated) { instruction ->
            val scalars = setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId)
            if (instruction !is Instruction.Binary || instruction.type !in scalars ||
                instruction.left.type !in scalars || instruction.right.type !in scalars) null
            else {
                fun integral(value: ValueRef, number: Long) = ((value as? ValueRef.Constant)?.value as? CompilerValue.Integral)?.value == number
                fun boolean(value: ValueRef, flag: Boolean) = ((value as? ValueRef.Constant)?.value as? CompilerValue.Bool)?.value == flag
                when (instruction.operation) {
                    "+" -> when { integral(instruction.right, 0) -> instruction.left; integral(instruction.left, 0) -> instruction.right; else -> null }
                    "-" -> when { integral(instruction.right, 0) -> instruction.left; instruction.left == instruction.right -> ValueRef.Constant(instruction.type, CompilerValue.Integral(0)); else -> null }
                    "*" -> when { integral(instruction.right, 0) || integral(instruction.left, 0) -> ValueRef.Constant(instruction.type, CompilerValue.Integral(0));
                        integral(instruction.right, 1) -> instruction.left; integral(instruction.left, 1) -> instruction.right; else -> null }
                    "&&" -> when { boolean(instruction.right, false) || boolean(instruction.left, false) -> ValueRef.Constant(instruction.type, CompilerValue.Bool(false));
                        boolean(instruction.right, true) || instruction.left == instruction.right -> instruction.left; boolean(instruction.left, true) -> instruction.right; else -> null }
                    "||" -> when { boolean(instruction.right, true) || boolean(instruction.left, true) -> ValueRef.Constant(instruction.type, CompilerValue.Bool(true));
                        boolean(instruction.right, false) || instruction.left == instruction.right -> instruction.left; boolean(instruction.left, false) -> instruction.right; else -> null }
                    "==", "<=", ">=" -> if (instruction.left == instruction.right) ValueRef.Constant(instruction.type, CompilerValue.Bool(true)) else null
                    "!=", "<", ">" -> if (instruction.left == instruction.right) ValueRef.Constant(instruction.type, CompilerValue.Bool(false)) else null
                    else -> null
                }
            }
        }
    }
}

/** Substitutions only name immutable result snapshots, never a mutable storage read. */
private fun substituteBlocks(ir: TypedIR, replacement: (Instruction) -> ValueRef?): TypedIR {
    val mappings = mutableMapOf<Int, ValueRef>()
    val transform = object : IRTransform() {
        override fun ref(value: ValueRef): ValueRef = if (value is ValueRef.Result)
            mappings[value.instruction]?.let(::ref) ?: value else super.ref(value)
    }
    val blocks = ir.blocks.map { block ->
        val instructions = block.instructions.mapNotNull { old ->
            val instruction = transform.instruction(old)
            val value = replacement(instruction)
            if (value != null) {
                mappings[instruction.resultId()!!] = value; null
            } else instruction
        }
        block.copy(instructions = instructions, terminator = transform.terminator(block.terminator))
    }
    return ir.copy(blocks = blocks)
}

internal class CopyPropagation : FunctionPass {
    override val id = PassIds.COPIES
    override fun run(ir: TypedIR, context: PassContext): TypedIR {
        return ir.copy(blocks = ir.blocks.map { block ->
            val written = mutableMapOf<Place, ValueRef>()
            val replacements = mutableMapOf<Int, ValueRef>()
            val rewrite = object : IRTransform() {
                override fun ref(value: ValueRef): ValueRef = if (value is ValueRef.Result)
                    replacements[value.instruction] ?: value else super.ref(value)
            }
            val instructions = block.instructions.mapNotNull { original ->
                val i = rewrite.instruction(original)
                when (i) {
                    is Instruction.Write -> {
                        written.keys.removeAll { it.overlaps(i.place) }
                        if (RuntimeValues.scalarRoot(i.location, ir.declarations) && ir.declarations[i.place.root]?.forceRuntime != true &&
                            (i.value is ValueRef.Constant || i.value is ValueRef.Result) &&
                            i.value.type in setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId)) written[i.place] = i.value
                    }
                    is Instruction.Read -> {
                        val source = written[i.place]
                        if (source != null && source.type == i.type) {
                            replacements[i.result] = source; return@mapNotNull null
                        }
                    }
                    is Instruction.Declare -> written.keys.removeAll { it.root == i.symbol.id }
                    is Instruction.RawCommand -> if (EffectAnalysis.rawCommandEffect(i.command) == Effect.Unknown) written.clear()
                    is Instruction.Call -> when (val effect = i.effect) {
                        Effect.Unknown -> written.clear()
                        is Effect.Writes -> written.keys.removeAll { place -> effect.places.any { it.overlaps(place) } }
                        else -> Unit
                    }
                    is Instruction.Binary, is Instruction.Promote, is Instruction.Convert, is Instruction.CaptureKey -> Unit
                    else -> written.clear()
                }
                i
            }
            block.copy(instructions = instructions, terminator = rewrite.terminator(block.terminator))
        })
    }
}

internal class CommonSubexpressions : FunctionPass {
    override val id = PassIds.COMMON
    override fun run(ir: TypedIR, context: PassContext) = ir.copy(blocks = ir.blocks.map { block ->
        val expressions = mutableMapOf<Any, ValueRef>()
        val reads = mutableMapOf<Pair<Location, top.mcfpp.type.TypeId>, ValueRef>()
        val replacements = mutableMapOf<Int, ValueRef>()
        val rewrite = object : IRTransform() {
            override fun ref(value: ValueRef): ValueRef = if (value is ValueRef.Result)
                replacements[value.instruction] ?: value else super.ref(value)
        }
        val instructions = block.instructions.mapNotNull { original ->
            val i = rewrite.instruction(original)
            val key: Any? = when (i) {
                is Instruction.Binary -> listOf(i.operation, i.type, i.left, i.right)
                else -> null
            }
            val existing = if (i is Instruction.Read) reads[i.location to i.type] else key?.let(expressions::get)
            val result = i.resultId()
            if (existing != null && result != null) { replacements[result] = existing; return@mapNotNull null }
            if (i is Instruction.Read) reads[i.location to i.type] = ValueRef.Result(i.type, i.result)
            if (key != null && i is Instruction.Binary) expressions[key] = ValueRef.Result(i.type, i.result)
            when (i) {
                is Instruction.Write -> reads.keys.removeAll { it.first.place.overlaps(i.place) }
                is Instruction.Call, is Instruction.RawCommand, is Instruction.MapMember, is Instruction.ListMember,
                is Instruction.DictionaryMember -> reads.clear()
                else -> Unit
            }
            i
        }
        block.copy(instructions = instructions, terminator = rewrite.terminator(block.terminator))
    })
}

internal class ControlFlowSimplification : FunctionPass {
    override val id = PassIds.CONTROL
    override fun run(ir: TypedIR, context: PassContext): TypedIR {
        var blocks = ir.blocks.map { block ->
            val t = block.terminator
            val known = ((t as? Terminator.Branch)?.condition as? ValueRef.Constant)?.value as? CompilerValue.Bool
            block.copy(terminator = if (known == null) t else Terminator.Jump(if (known.value) (t as Terminator.Branch).whenTrue else (t as Terminator.Branch).whenFalse))
        }.associateBy { it.id }.toMutableMap()
        val reachable = linkedSetOf<Int>()
        fun visit(id: Int) { if (reachable.add(id)) blocks.getValue(id).terminator.successors().forEach(::visit) }
        visit(ir.entry)
        blocks.keys.retainAll(reachable)
        // Maintain predecessors as blocks disappear. Instruction chunks concatenate in O(1),
        // then flatten once, so even a reverse-ordered chain does not copy growing suffixes.
        class Chunk(val instructions: List<Instruction>?, val left: Chunk? = null, val right: Chunk? = null)
        val chunks = blocks.mapValues { Chunk(it.value.instructions) }.toMutableMap()
        val predecessors = blocks.keys.associateWith { linkedSetOf<Int>() }
        blocks.values.forEach { b -> b.terminator.successors().forEach { predecessors.getValue(it).add(b.id) } }
        val pending = ArrayDeque(blocks.keys)
        while (pending.isNotEmpty()) {
            val candidate = blocks[pending.removeFirst()] ?: continue
            val targetId = (candidate.terminator as? Terminator.Jump)?.block ?: continue
            if (targetId == candidate.id || targetId == ir.entry || predecessors.getValue(targetId).size != 1) continue
            val target = blocks.getValue(targetId)
            chunks[candidate.id] = Chunk(null, chunks.getValue(candidate.id), chunks.remove(targetId)!!)
            blocks[candidate.id] = candidate.copy(instructions = emptyList(), terminator = target.terminator)
            blocks.remove(targetId)
            target.terminator.successors().forEach { successor ->
                predecessors.getValue(successor).remove(targetId)
                predecessors.getValue(successor).add(candidate.id)
            }
            pending.add(candidate.id)
        }
        return ir.copy(blocks = blocks.values.map { block ->
            val instructions = arrayListOf<Instruction>()
            val work = ArrayDeque<Chunk>().apply { add(chunks.getValue(block.id)) }
            while (work.isNotEmpty()) {
                val chunk = work.removeLast()
                if (chunk.instructions != null) instructions.addAll(chunk.instructions)
                else { work.add(chunk.right!!); work.add(chunk.left!!) }
            }
            block.copy(instructions = instructions)
        })
    }
}

internal class DeadValues(private val observable: Set<SymbolId>) : FunctionPass {
    override val id = PassIds.DEAD
    override val requires = setOf(AnalysisKind.LIVENESS)
    override fun run(ir: TypedIR, context: PassContext): TypedIR {
        val definitions = ir.blocks.flatMap { it.instructions }.mapNotNull { i -> i.resultId()?.let { it to i } }.toMap()
        val uses = hashMapOf<Int, Int>()
        ir.blocks.forEach { block ->
            val references = block.instructions.flatMap { it.references() } + when (val t = block.terminator) {
                is Terminator.Return -> t.value?.resultReferences().orEmpty()
                is Terminator.Branch -> t.condition.resultReferences()
                else -> emptySet()
            }
            references.forEach { uses[it] = uses.getOrDefault(it, 0) + 1 }
        }
        fun removable(i: Instruction) = i is Instruction.Binary && i.operation !in setOf("/", "%") ||
            i is Instruction.Read || i is Instruction.Promote || i is Instruction.ListMember && i.operation.query
        val pending = ArrayDeque(definitions.keys.filter { uses.getOrDefault(it, 0) == 0 && removable(definitions.getValue(it)) })
        val removed = hashSetOf<Int>()
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!removed.add(id)) continue
            definitions.getValue(id).references().forEach { used ->
                val remaining = uses.getValue(used) - 1
                uses[used] = remaining
                if (remaining == 0 && definitions[used]?.let(::removable) == true) pending.add(used)
            }
        }
        val current = ir.copy(blocks = ir.blocks.map { b -> b.copy(instructions = b.instructions.filter { it.resultId() !in removed }) })
        // Backwards storage liveness removes only proven dead, non-escaping scalar stores.
        val incoming = current.blocks.associate { it.id to emptySet<Place>() }.toMutableMap()
        val kept = mutableMapOf<Int, List<Instruction>>()
        do {
            var changed = false
            for (block in current.blocks.asReversed()) {
                val live = block.terminator.successors().flatMap { incoming[it].orEmpty() }.toMutableSet().apply {
                    if (block.terminator is Terminator.Return) addAll(observable.map(::Place))
                }
                val instructions = mutableListOf<Instruction>()
                for (i in block.instructions.asReversed()) {
                    if (i is Instruction.Write && i.place.path.isEmpty() && i.place.root !in ir.externalRoots &&
                        ir.declarations[i.place.root]?.forceRuntime != true &&
                        i.value.type in setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId)) {
                        if (live.none { it.overlaps(i.place) }) continue
                        live.remove(i.place)
                    }
                    if (i is Instruction.Read) live.add(i.place)
                    if (i is Instruction.RawCommand && EffectAnalysis.rawCommandEffect(i.command) == Effect.Unknown ||
                        i is Instruction.Call || i is Instruction.View || i is Instruction.Construct ||
                        i is Instruction.ListMember || i is Instruction.MapMember || i is Instruction.DictionaryMember)
                        live.addAll(current.declarations.keys.map(::Place))
                    instructions.add(i)
                }
                kept[block.id] = instructions.asReversed()
                if (incoming[block.id] != live) { incoming[block.id] = live; changed = true }
            }
        } while (changed)
        return DeadLocalLists.run(current.copy(blocks = current.blocks.map { it.copy(instructions = kept.getValue(it.id)) }))
    }
}

internal fun scalarPasses(observable: Set<SymbolId>) = listOf(ConstantPropagation(), AlgebraicSimplification(),
    CopyPropagation(), CommonSubexpressions(), ControlFlowSimplification(), DeadValues(observable))
