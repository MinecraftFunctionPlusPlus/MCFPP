package top.mcfpp.optimization

import top.mcfpp.analysis.*

enum class AnalysisKind { FLOW, CALL_GRAPH, EFFECTS, LIVENESS, STORAGE }

/** Analysis ownership is one compilation/module; transformed IR never reuses stale facts. */
class AnalysisManager {
    private val values = mutableMapOf<AnalysisKind, java.util.IdentityHashMap<Any, Any>>()
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(kind: AnalysisKind, owner: Any, compute: () -> T): T =
        values.getOrPut(kind) { java.util.IdentityHashMap() }.getOrPut(owner, compute) as T
    fun invalidate(kinds: Set<AnalysisKind>) { kinds.forEach(values::remove) }
}

interface FunctionPass {
    val id: String
    val requires: Set<AnalysisKind> get() = emptySet()
    val invalidates: Set<AnalysisKind> get() = AnalysisKind.entries.toSet()
    fun run(ir: TypedIR, context: PassContext): TypedIR
}

interface ModulePass {
    val id: String
    val requires: Set<AnalysisKind> get() = emptySet()
    val invalidates: Set<AnalysisKind> get() = AnalysisKind.entries.toSet()
    fun run(module: Map<SymbolId, TypedIR>, context: PassContext): Map<SymbolId, TypedIR>
}

class PassContext(
    val config: OptimizationConfig,
    val initial: FlowFacts = FlowFacts(),
    val module: Map<SymbolId, TypedIR> = emptyMap(),
    val analyses: AnalysisManager = AnalysisManager(),
    val trace: (String, TypedIR, TypedIR) -> Unit = { _, _, _ -> },
) {
    private val summarize by lazy { ReturnTypeAnalysis.summarizer(module) }
    fun flow(ir: TypedIR): FlowAnalysis.Result = analyses.get(AnalysisKind.FLOW, ir) {
        FlowAnalysis.analyze(ir, initial, PrimitiveEvaluation::binary,
            canFoldBranch = { it !is ValueRef.Result || it.instruction !in ir.runtimeValues },
            callSummary = summarize,
            declarations = ir.declarations, boundTypes = ir.boundTypes)
    }
}

class PassManager(private val passes: List<FunctionPass>) {
    fun simplify(input: TypedIR, context: PassContext): TypedIR {
        var ir = input
        do {
            val before = ir
            ir = run(ir, context)
        } while (ir != before)
        return ir
    }

    fun run(input: TypedIR, context: PassContext): TypedIR {
        var ir = input
        for (pass in passes) if (context.config.enabled(pass.id)) {
            val next = pass.run(ir, context)
            val changed = next != ir
            if (changed) context.analyses.invalidate(pass.invalidates)
            context.trace(pass.id, ir, next)
            // Preserve the owner of cached analyses when a pass only rebuilt equal containers.
            if (changed) ir = next
        }
        return ir
    }
}

class ModulePassManager(private val passes: List<ModulePass>) {
    fun run(input: Map<SymbolId, TypedIR>, context: PassContext): Map<SymbolId, TypedIR> {
        var module = input
        for (pass in passes) {
            val next = pass.run(module, context)
            if (next != module) {
                context.analyses.invalidate(pass.invalidates)
                module = next
            }
        }
        return module
    }
}

internal object BindCallEffects : ModulePass {
    override val id = "bind-call-effects"
    override val requires = setOf(AnalysisKind.CALL_GRAPH, AnalysisKind.EFFECTS)
    override fun run(module: Map<SymbolId, TypedIR>, context: PassContext): Map<SymbolId, TypedIR> {
        val effects = context.analyses.get(AnalysisKind.EFFECTS, module) { EffectAnalysis.analyze(module) }
        return module.mapValues { (_, ir) -> EffectAnalysis.bind(ir, module, effects) }
    }
}

internal object IRVerifier {
    fun verify(ir: TypedIR) {
        val blocks = ir.blocks.map { it.id }
        require(blocks.distinct().size == blocks.size && ir.entry in blocks) { "Invalid optimized CFG" }
        val results = ir.blocks.flatMap { it.instructions }.mapNotNull { it.resultId() }
        require(results.distinct().size == results.size) { "Duplicate optimized result ID" }
        val defined = results.toSet()
        for (block in ir.blocks) {
            require(block.terminator.successors().all { it in blocks }) { "Missing optimized block" }
            require(block.instructions.flatMap { it.references() }.all { it in defined }) { "Missing optimized value" }
            val exitUses = when (val exit = block.terminator) {
                is Terminator.Return -> exit.value?.resultReferences().orEmpty()
                is Terminator.Branch -> exit.condition.resultReferences()
                else -> emptySet()
            }
            require(exitUses.all { it in defined }) { "Missing optimized terminator value" }
        }
    }
}
