package top.mcfpp.optimization

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.command.Comment
import top.mcfpp.io.FunctionInventory
import top.mcfpp.io.IrTextDump
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType

internal object OptimizationPipeline {
    private val scalars get() = setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId)

    fun conventions(graph: Map<SymbolId, TypedIR>, functions: Collection<Function>) {
        val config = Project.config.optimization
        if (!config.enabled(PassIds.FRAMES)) return
        val analysis = ProgramAnalysis(graph)
        val scalar = graph.filter { (id, ir) -> id !in analysis.recursive && analysis.effects[id]?.unknown == false &&
            ir.declarations.values.all { it.declaredType in scalars } && ir.blocks.all { block -> block.instructions.all {
                it is Instruction.Declare || it is Instruction.Read || it is Instruction.Write || it is Instruction.Binary ||
                    it is Instruction.Call || it is Instruction.RawCommand && EffectAnalysis.rawCommandEffect(it.command) != Effect.Unknown
            } } }.keys.toMutableSet()
        do {
            val changed = scalar.removeAll { id -> analysis.calls[id].orEmpty().any { it !in scalar } }
        } while (changed)
        for (function in functions) if (function.declarationId in graph) {
            function.optimizedRegisters = analysis.effects[function.declarationId]?.unknown == false
            function.optimizedScalar = function.declarationId in scalar &&
                (function.returnType.typeId in scalars || function.returnType == MCFPPPrivateType.Void)
            function.optimizedStaticFrame = if (!function.optimizedScalar && function.declarationId !in analysis.recursive &&
                analysis.effects[function.declarationId]?.unknown == false) function.namespaceID.toString() else null
        }
    }

    fun staticFrame(function: Function): NBTPath? = function.optimizedStaticFrame?.let {
        NBTPath(StorageSource("mcfpp:frames")).memberIndex(StorageAccess.quotedKey(it))
    }

    /** Compact score-holder tokens isolate overloads and hygienically cloned declarations. */
    fun scoreName(function: Function, name: String, symbol: SymbolId? = null): String = if (function.optimizedRegisters) {
        val owner = java.lang.Long.toHexString(function.declarationId.value)
        if (name == "\$return") "r_$owner" else "v_${owner}_${java.lang.Long.toHexString(requireNotNull(symbol).value)}"
    } else function.prefix + name

    fun optimize(function: Function, ir: TypedIR, graph: Map<SymbolId, TypedIR>, initial: FlowFacts,
                 observable: Set<SymbolId>): TypedIR {
        val config = Project.config.optimization.let {
            if (top.mcfpp.CompileSettings.foldIRConstants) it else it.copy(disabledPasses = it.disabledPasses + PassIds.CONSTANTS)
        }
        val analyses = AnalysisManager()
        val names = if (Project.config.irOutputPath == null) emptyMap() else
            FunctionInventory.all().associate { it.declarationId to it.namespaceID.toString() }
        val module = ModulePassManager(listOf(BindCallEffects)).run(graph, PassContext(config, analyses = analyses))
        val context = PassContext(config, initial, module, analyses, trace = { id, before, after ->
            if (Project.config.irOutputPath != null) function.optimizationTrace += buildString {
                appendLine("pass $id | changed=${before != after}")
                appendLine("before\n${IrTextDump.render(before, names)}")
                appendLine("after\n${IrTextDump.render(after, names)}")
            }
        })
        val manager = PassManager(scalarPasses(observable))
        var result = manager.simplify(ir, context)
        if (config.enabled(PassIds.INLINE, 2)) {
            val pass = AutomaticInlining(module, observable, TypedMinecraftCost(module), names)
            val before = result
            result = pass.run(result, context)
            if (result != before) context.analyses.invalidate(pass.invalidates)
            context.trace(pass.id, before, result)
            result = manager.simplify(result, context)
        }
        IRVerifier.verify(result)
        function.optimizedIR = result
        return result
    }

    /** Last common boundary, including legacy bodies, generated wrappers and macro functions. */
    fun finish(cleanup: Boolean = true) {
        while (true) {
            val functions = FunctionInventory.all()
            val pending = functions.filter { it.pendingBackend != null }
            if (pending.isEmpty()) break
            // Validation completes for the module before deferred bodies are emitted. Inlining
            // may have cloned a forward target before its diagnostic was published; inspect the
            // semantic call graph so that this cannot lower an invalid body into its callers.
            val invalid = functions.filter { it.backendValid == false }.mapTo(hashSetOf()) { it.declarationId }
            if (invalid.isNotEmpty()) do {
                var changed = false
                for (function in functions) if (function.declarationId !in invalid && function.typedIR?.blocks?.any { block ->
                    block.instructions.any { it is Instruction.Call && it.declaration in invalid }
                } == true) changed = invalid.add(function.declarationId) || changed
            } while (changed)
            pending.forEach { function ->
                val generate = function.pendingBackend!!
                function.pendingBackend = null
                if (function.declarationId !in invalid) generate()
            }
        }
        for (function in FunctionInventory.all()) {
            val input = function.commands.map { MinecraftLowering.lower(it.analyze()) }
            val planned = if (cleanup && Project.config.optimization.enabled(PassIds.FRAMES)) MinecraftFramePlanning.run(input) else input
            val output = if (cleanup && Project.config.optimization.enabled(PassIds.COMMANDS)) MinecraftCleanup.run(planned) else planned
            function.minecraftIR = output
            // Retain Comment instances so comment-level filtering still happens at publication.
            val comments = function.commands.filterIsInstance<Comment>().associateBy { it.toString() }
            function.commands.clear()
            output.forEach { function.commands.add(comments[it.render()] ?: Command(it.render())) }
        }
        Project.macroFunction.replaceAll { _, body ->
            val ir = body.lines().map(MinecraftLowering::lower)
            (if (cleanup && Project.config.optimization.enabled(PassIds.COMMANDS)) MinecraftCleanup.run(ir) else ir).joinToString("\n") { it.render() }
        }
    }
}

/** Provisional lowering uses the same Minecraft cost classifier as emitted command IR. */
internal class TypedMinecraftCost(private val functions: Map<SymbolId, TypedIR>) : InlineCostEstimator {
    private val targets = FunctionInventory.all().associateBy { it.declarationId }
    override fun cost(ir: TypedIR, model: CommandCostModel): Double {
        val estimator = MinecraftCostEstimator(model)
        val activeCalls = hashSetOf<SymbolId>()
        lateinit var estimate: (TypedIR) -> Double
        estimate = { body ->
            val macroFunction = body.blocks.any { block -> block.instructions.any {
                it is Instruction.RawCommand && it.command.trimStart().startsWith('$')
            } }
            fun ordinary(weight: Double = model.command, count: Int = 1) =
                count * (weight + if (macroFunction) model.macroFunctionCommand else 0.0)
            fun command(text: String) = estimator.cost(MinecraftLowering.lower(text)) +
                if (macroFunction && !text.trimStart().startsWith('$')) model.macroFunctionCommand else 0.0
            fun instruction(i: Instruction): Double = when (i) {
            is Instruction.Declare -> 0.0
            is Instruction.Read -> if (i.type in setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId)) command("scoreboard players operation result temporary = source temporary") else ordinary(model.storage)
            is Instruction.Write -> if (i.value.type in setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId)) ordinary() else ordinary(model.storage)
            is Instruction.Binary -> ordinary(count = if (i.operation in setOf("+", "-", "*", "&&")) 2 else 3)
            is Instruction.Call -> {
                val callee = functions[i.declaration]
                val body = if (callee != null && activeCalls.add(i.declaration)) {
                    val cost = estimate(callee)
                    activeCalls.remove(i.declaration)
                    cost
                } else 0.0
                val target = targets[i.declaration]
                val transfers = if (target?.optimizedScalar == true)
                    ordinary(count = i.arguments.size + if (i.result != null) 1 else 0)
                else ordinary(model.storage, i.arguments.size * 2 + (if (i.result != null) 3 else 0) +
                    (if (target?.optimizedStaticFrame == null) 2 else 0))
                ordinary() + transfers + body
            }
            is Instruction.RawCommand -> command(i.command)
            else -> ordinary(model.storage)
        }
            val blocks = body.blocks.associateBy { it.id }
            fun reaches(from: Int, target: Int, seen: MutableSet<Int>): Boolean =
                from == target || seen.add(from) && blocks.getValue(from).terminator.successors().any { reaches(it, target, seen) }
            val loopBlocks = blocks.keys.filter { id -> blocks.getValue(id).terminator.successors().any { reaches(it, id, hashSetOf()) } }.toSet()
            val active = hashSetOf<Int>()
            fun path(id: Int): Double {
                if (!active.add(id)) return 0.0 // The unknown iteration count is never guessed.
                val block = blocks.getValue(id)
                val own = if (id in loopBlocks) 0.0 else block.instructions.sumOf(::instruction)
                val returned = (block.terminator as? Terminator.Return)?.value
                val exitCost = if (returned == null || id in loopBlocks) 0.0 else
                    if (returned.type in setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId)) ordinary() else ordinary(model.storage)
                val dispatch = if (id in loopBlocks) 0.0 else when (block.terminator) {
                    is Terminator.Jump -> ordinary()
                    is Terminator.Branch -> ordinary(count = 2)
                    is Terminator.Return -> if (returned == null) 0.0 else ordinary()
                    else -> 0.0
                }
                val tail = block.terminator.successors().maxOfOrNull(::path) ?: 0.0
                active.remove(id)
                return own + tail + exitCost + dispatch
            }
            path(body.entry)
        }
        return estimate(ir)
    }
}
