package top.mcfpp.optimization

import top.mcfpp.analysis.*
import top.mcfpp.type.MCFPPBaseType

/** Rebuild scalar runtime dependencies after inlining; semantic IR keeps its original markers. */
internal object RuntimeValues {
    private val scalars get() = setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId)

    fun scalarRoot(location: Location, declarations: Map<SymbolId, Symbol>): Boolean =
        declarations[location.place.root]?.declaredType in scalars && location.place.path.isEmpty() &&
            location.frameOffset == 0 && location.indices.isEmpty() && location.keys.isEmpty() &&
            location.captured.isEmpty() && location.capturedLocations.isEmpty()

    fun analyze(ir: TypedIR): Set<Int> {
        val forced = ir.declarations.values.filter { it.forceRuntime }.map { it.id }.toSet() + ir.externalRoots
        val blocks = ir.blocks.associateBy { it.id }
        val entries = mutableMapOf(ir.entry to (forced + ir.parameters))
        val pending = ArrayDeque<Int>().apply { add(ir.entry) }
        val results = mutableMapOf<Int, Set<Int>>()
        while (pending.isNotEmpty()) {
            val block = blocks.getValue(pending.removeFirst())
            val storage = entries.getValue(block.id).toMutableSet()
            val values = mutableMapOf<Int, Boolean>()
            fun runtime(value: ValueRef): Boolean = when (value) {
                is ValueRef.Constant -> false
                is ValueRef.Result -> values[value.instruction] ?: true
                is ValueRef.Read -> value.place.root in storage
                is ValueRef.TypedView -> true // Views retain representation and captured-address barriers.
            }
            for (instruction in block.instructions) {
                val result = instruction.resultId()
                if (result != null) values[result] = when (instruction) {
                    is Instruction.Read -> instruction.place.root in storage ||
                        (!scalarRoot(instruction.location, ir.declarations) && result in ir.runtimeValues)
                    is Instruction.Binary -> runtime(instruction.left) || runtime(instruction.right) ||
                        ((instruction.left.type !in scalars || instruction.right.type !in scalars) && result in ir.runtimeValues)
                    is Instruction.Call -> true
                    else -> result in ir.runtimeValues || instruction.references().any { values[it] != false }
                }
                when (instruction) {
                    is Instruction.Declare -> if (instruction.symbol.id !in forced) storage.remove(instruction.symbol.id)
                    is Instruction.Write -> {
                        val root = instruction.place.root
                        if (root in forced || instruction.place.path.isNotEmpty() || runtime(instruction.value)) storage.add(root)
                        else storage.remove(root)
                    }
                    is Instruction.Call -> {
                        when (val effect = instruction.effect) {
                            Effect.Unknown -> storage.addAll(ir.declarations.keys)
                            is Effect.Writes -> storage.addAll(effect.places.map { it.root })
                            else -> Unit
                        }
                        instruction.resultPlace?.let { storage.add(it.root) }
                    }
                    is Instruction.RawCommand -> if (EffectAnalysis.rawCommandEffect(instruction.command) == Effect.Unknown)
                        storage.addAll(ir.declarations.keys)
                    is Instruction.MapMember -> if (!instruction.operation.query) storage.add(instruction.receiver.place.root)
                    is Instruction.ListMember -> if (!instruction.operation.query) storage.add(instruction.receiver.place.root)
                    is Instruction.DictionaryMember -> if (instruction.operation != DictionaryOperation.CONTAINS_KEY)
                        storage.add(instruction.receiver.place.root)
                    else -> Unit
                }
            }
            results[block.id] = values.filterValues { it }.keys
            for (successor in block.terminator.successors()) {
                val before = entries[successor]
                val joined = before.orEmpty() + storage
                if (before == null || before != joined) {
                    entries[successor] = joined
                    pending.addLast(successor)
                }
            }
        }
        val unreachable = ir.blocks.filter { it.id !in entries }.flatMap { it.instructions }.mapNotNull { it.resultId() }.toSet()
        return results.values.flatten().toSet() + ir.runtimeValues.intersect(unreachable)
    }
}
