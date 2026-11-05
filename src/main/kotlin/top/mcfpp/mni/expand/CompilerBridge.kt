package top.mcfpp.mni.expand

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.Var
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.api.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import java.util.Collections
import java.util.IdentityHashMap

/** Compiler-owned consumer of detached plans. All live objects remain behind this boundary. */
internal class CompilerBridge(val bindings: CompilerSourceBindings) {
    private var prepared: CompilerPlanEmitter? = null

    /** Reserve names before the transaction takes its publication snapshot. */
    fun prepare(plan: LoweringPlan): CompilerJournalParticipant {
        bindings.requireCurrent()
        bindings.requireDomain(plan.domain)
        require(plan.target == bindings.loweringTarget) { "Lowering target differs from the captured compiler target" }
        val name = bindings.session.reserveCompilerName("lowered_plan", "backend")
        val blockNames = plan.blocks.associate { it.id to bindings.session.reserveCompilerName("block_${it.id.index}", "block") }
        val emitter = CompilerPlanEmitter(bindings, plan, name, blockNames)
        prepared = emitter
        /** Function roots are resolved after production helper bindings are enrolled. */
        fun proofRoots(): List<Any> = arrayListOf<Any>(bindings.target).apply {
            plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.Call>().forEach {
                add(bindings.function(it.function.key))
            }
        }
        return object : CompilerJournalParticipant {
            override val domains = CompilerDomain.entries.toSet()
            override fun prepare(): PreparedCompilerCommit {
                // A standalone participant owns its proof check and journal. A cumulative
                // production backend proves once before opening the one outer journal.
                bindings.requireCurrent()
                val roots = proofRoots()
                return CompilerStateJournal.checkpoint(roots).prepare { emitter.generate() }
            }
            override fun generation(): (() -> Unit)? = {
                proofRoots()
                emitter.generate()
            }
        }
    }

    /** The adapter is initialized by preparation and becomes readable when its commit is applied. */
    fun result(): Var<*>? = checkNotNull(prepared) { "No lowering plan has been prepared" }.result
}

/** Stable call-site specialization of a helper whose REF parameters name captured caller addresses. */
internal fun CompilerPlanEmitter.specializeHelper(
    helper: CompilerHelperBinding,
    substitutions: Map<top.mcfpp.mni.api.SymbolId, CompilerPlanLocation>,
    externalFrames: Map<ExecutionContextId, PlanExecutionFrame>,
    externalExecutors: Map<ExecutionContextId, ExecutionContextId>,
    callSiteFrame: PlanExecutionFrame?
): Function {
    val plan = helper.plan ?: fail("Helper '${helper.key}' has no detached plan")
    return bindings.specializing(helper.key) {
        val name = bindings.session.reserveCompilerName("helper_ref", "backend")
        val namespace = namespace(name.substringBefore(':'))
        val function = NoStackFunction(name.substringAfter(':'), bindings.target).apply {
            this.namespace = name.substringBefore(':')
            bodyCompiled = true
        }
        function.returnType = helper.function.returnType
        helper.function.normalParams.forEach { parameter ->
            function.normalParams.add(
                FunctionParam(
                    parameter.type,
                    parameter.identifier,
                    function,
                    parameter.hasDefault,
                    parameter.isReadOnly
                )
            )
        }
        require(namespace.scope.addFunction(function, false)) { "Specialized helper '${name}' collides with a published function" }
        val blocks = plan.blocks.associate {
            it.id to bindings.session.reserveCompilerName("helper_ref_block_${it.id.index}", "block")
        }
        val returnSlot = if (helper.function.returnType == MCFPPPrivateType.Void) null
        else NBTPath.stack.intIndex(0).memberIndex("return").toCommandPart().analyze()
        CompilerPlanEmitter(
            bindings, plan, name, blocks, function, returnSlot, substitutions,
            externalFrames, externalExecutors, callSiteFrame, executorBinding(callSiteFrame)
        ).generate()
        function
    }
}

private fun CompilerPlanEmitter.namespace(identifier: String): Namespace {
    val namespaces = GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values
    return namespaces.firstOrNull { it.identifier == identifier }
        ?: GlobalScope.localNamespaces.getOrPut(identifier) { Namespace(identifier) }
}

/**
 * An executor identity qualified by its plan/arena domain. Plan-local component indices are not
 * comparable across a helper plan and its caller plan: equal indices can name different executors.
 * External frames therefore keep their canonical arena binding instead of a borrowed index.
 */
private sealed interface ExecutorKey {
    data class Local(val component: PlanContextId) : ExecutorKey
    data class External(val binding: ExecutionContextId) : ExecutorKey
}

/** Shared production emission state; ordinary/native call lowering can consume these same slots. */
internal class CompilerPlanEmitter(
    val bindings: CompilerSourceBindings,
    val plan: LoweringPlan,
    private val reserved: String,
    private val blockNames: Map<PlanBlockId, String>,
    private val subject: Function = bindings.target,
    private val resultSlot: String? = null,
    private val substitutions: Map<top.mcfpp.mni.api.SymbolId, CompilerPlanLocation> = emptyMap(),
    reboundFrames: Map<ExecutionContextId, PlanExecutionFrame> = emptyMap(),
    reboundExecutors: Map<ExecutionContextId, ExecutionContextId> = emptyMap(),
    private val ambientFrame: PlanExecutionFrame? = null,
    private val ambientExecutor: ExecutionContextId? = null
) {
    private var generated = false
    private val token = reserved.substringAfter("mni/").substringBefore('/')
    val workspace: NBTPath = NBTPath.stack.intIndex(0).memberIndex("\$mni").memberIndex(token)
    val values = CompilerPlanValues(this)
    val locations = CompilerPlanLocations(this)
    private var sequence = 0
    private var macroSequence = 0
    private val functions = linkedMapOf<PlanBlockId, Function>()
    private val externalFrames = LinkedHashMap<ExecutionContextId, PlanExecutionFrame>().apply { putAll(reboundFrames) }
    private val externalExecutors = LinkedHashMap<ExecutionContextId, ExecutionContextId>().apply { putAll(reboundExecutors) }
    private val externalFrameValues = Collections.newSetFromMap(IdentityHashMap<PlanExecutionFrame, Boolean>()).apply {
        addAll(externalFrames.values)
    }
    /** A component's binding is the canonical identity that survives another plan's local index. */
    private val componentBindings = plan.executionFrames.associate { it.id to it.binding }
    private val rootExecutorMap: Pair<PlanContextId, ExecutorKey>? =
        plan.executionFrames.firstOrNull { it.binding == plan.target.executionContext }
            ?.let { root -> ambientExecutor?.let { root.executor to ExecutorKey.External(it) } }
    val goto: String = score("goto")
    val contexts = ContextCommandEmitter(this)
    var result: Var<*>? = null
        private set
    var site: PlanSite = plan.blocks.first().terminator.site
        private set
    var currentBlock: PlanBasicBlock? = null
        private set

    fun fail(message: String): Nothing = throw CodeModelException(
        site.generatedFrom ?: site.callSite,
        "$message (expanded at ${site.callSite}; ${site.frames.joinToString()})"
    )
    fun type(type: PlanType): MCFPPType = bindings.compilerType(type)
    fun substitution(symbol: top.mcfpp.mni.api.SymbolId): CompilerPlanLocation? = substitutions[symbol]
    fun path(name: String): NBTPath = workspace.memberIndex(name)
    fun address(path: NBTPath): String = path.toCommandPart().analyze()
    fun slot(name: String): String = address(path(name))
    fun scratch(hint: String = "scratch"): String = slot("${hint}_${sequence++}")
    fun score(hint: String = "score"): String = "#${token.take(20)}_${hint}_${sequence++} mcfpp_default"
    fun blockFunction(id: PlanBlockId): Function = functions[id] ?: fail("Lowering edge refers to an unknown basic block")

    /** The frame in which the current operation runs, including a helper call-site rebind. */
    fun currentFrame(): PlanExecutionFrame? = currentBlock?.executionFrame ?: ambientFrame

    /** Frames from the caller plan and a specialized body's rebound root are explicit capabilities. */
    fun locationFrame(id: ExecutionContextId?): PlanExecutionFrame? {
        if (id == null) return null
        externalFrames[id]?.let { return it }
        return plan.executionFrames.firstOrNull { it.binding == id }
    }

    /**
     * Helper bodies execute in their caller's executor. A rebound root maps the body's root
     * executor component to the caller's canonical binding, never to a caller-local index.
     */
    private fun effectiveExecutor(frame: PlanExecutionFrame?): ExecutorKey? {
        if (frame == null) return null
        if (frame === ambientFrame) {
            val binding = ambientExecutor
                ?: fail("The caller's ambient execution frame has no canonical executor")
            return ExecutorKey.External(binding)
        }
        if (externalFrameValues.contains(frame)) {
            val binding = externalExecutors[frame.binding]
                ?: fail("An external execution frame has no canonical executor binding")
            return ExecutorKey.External(binding)
        }
        val local = ExecutorKey.Local(frame.executor)
        val root = rootExecutorMap ?: return local
        return if (frame.executor == root.first) root.second else local
    }

    private fun effectiveComponent(component: PlanContextId): ExecutorKey {
        val root = rootExecutorMap ?: return ExecutorKey.Local(component)
        return if (component == root.first) root.second else ExecutorKey.Local(component)
    }

    /**
     * Canonical arena binding for a frame, used when another plan is specialized against it.
     * Local root components must first be rebound to the caller identity of this helper body.
     */
    internal fun executorBinding(frame: PlanExecutionFrame?): ExecutionContextId? {
        val key = effectiveExecutor(frame) ?: return null
        return when (key) {
            is ExecutorKey.External -> key.binding
            is ExecutorKey.Local -> componentBindings[key.component]
                ?: fail("An execution frame component has no canonical binding")
        }
    }

    fun sameExecutor(first: PlanExecutionFrame?, second: PlanExecutionFrame?): Boolean {
        if (first == null || second == null) return false
        return effectiveExecutor(first) == effectiveExecutor(second)
    }

    fun requireSameExecutor(frame: PlanExecutionFrame?, description: String) {
        val current = currentFrame() ?: fail("$description requires an execution frame at its use site")
        if (!sameExecutor(frame, current)) fail("$description cannot cross executeAs without a stable identity")
    }

    /** Fixed storage/score addresses keep their metadata but do not depend on the executor. */
    fun contextSensitive(location: CompilerPlanLocation): Boolean {
        val layout = location.layout
        return when (layout) {
            is StorageLayout.Scoreboard -> layout.player.startsWith('@')
            is StorageLayout.LegacyFloat -> layout.components.any { it.player.startsWith('@') }
            null -> location.address?.startsWith("@") == true
            else -> location.address?.startsWith("storage ") != true
        }
    }

    private fun currentExecutor(): ExecutorKey? = effectiveExecutor(currentFrame())

    fun requireCurrentEntity(entity: PlanEntity, description: String) {
        val current = currentExecutor()
            ?: fail("$description requires an execution frame at its use site")
        if (effectiveComponent(entity.executor) != current)
            fail("$description must name the current executor, not a different executeAs identity")
    }

    /** Dynamic path operands already live in immutable-per-capture frame slots. */
    fun emit(command: String) {
        if (command.contains("\$(")) {
            val key = "mni/$token/macro_${macroSequence++}"
            Project.macroFunction[key] = "\$$command"
            emitDebugCommand("function mcfpp:dynamic/$key with ${slot("addresses")}")
        } else emitDebugCommand(command)
    }
    /**
     * Emit a command whose `$(` text is literal SNBT data, not a macro parameter. Only call this
     * for fixed destinations that cannot contain a runtime macro operand; dynamic addresses and
     * runtime string helper templates must keep using [emit].
     */
    fun emitLiteral(command: String) {
        emitDebugCommand(command)
    }
    private fun emitDebugCommand(text: String) {
        val command = Command(text)
        if (Project.config.debugInfo) {
            command.debugPoint = top.mcfpp.debug.DebugMetadata.expanded(site)
            command.debugPointCaptured = true
        }
        Function.addCommand(command)
    }
    fun emit(command: Command) = emit(command.analyze())

    fun adapter(type: PlanType, name: String, destination: NBTPath): Var<*> {
        val compilerType = type(type)
        val symbol = Symbol(top.mcfpp.analysis.SymbolId.fresh(), name, compilerType.typeId, true, forceRuntime = true)
        val root = Place(symbol.id)
        val data = StoredData(root, destination)
        data.types[compilerType.typeId] = compilerType
        data.declarations[root] = symbol
        data.facts.write(root, ValueFacts(TypeKnowledge.Exact(compilerType.typeId), ValueKnowledge.Unknown))
        return StorageAccess.adapter(compilerType, name, StorageBinding(data, root, destination, trustConstants = false)).also {
            it.symbol = symbol
        }
    }

    fun generate() {
        check(!generated) { "A lowering plan may be emitted only once" }
        generated = true
        values.analyze()
        if (TargetCapabilities.forVersion(Project.config.version)?.functionReturnRun != true)
            fail("The selected command target does not support lowering plan control flow")
        val target = subject
        val namespaces = GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values
        val namespaceName = blockNames.values.first().substringBefore(':')
        val namespace = namespaces.firstOrNull { it.identifier == namespaceName }
            ?: GlobalScope.localNamespaces.getOrPut(namespaceName) { Namespace(namespaceName) }
        for (block in plan.blocks) {
            val resource = blockNames.getValue(block.id)
            val function = NoStackFunction(resource.substringAfter(':'), target).apply {
                this.namespace = resource.substringBefore(':')
                bodyCompiled = true
            }
            namespace.scope.addFunction(function, false)
            functions[block.id] = function
        }
        if (plan.entry !in functions) fail("Lowering plan entry is missing")
        val bodyEntries = plan.contextRegions.map { it.body }.toSet()
        val previous = Function.currFunction
        try {
            Function.currFunction = target
            // NBT compound child nodes create missing parents while setting this private slot.
            emit("data modify ${address(workspace)} set value {addresses:{}}")
            locations.initializeSources()
            emit("scoreboard players set $goto 0")
            if (resultSlot == null) emit("function ${functions.getValue(plan.entry).namespaceID}")
            else emit("return run function ${functions.getValue(plan.entry).namespaceID}")
            target.child.add(functions.getValue(plan.entry))
            for (block in plan.blocks) {
                currentBlock = block
                Function.currFunction = functions.getValue(block.id)
                // A context body may be entered once per selected entity. Once a nonlocal edge
                // records its target, further entity invocations must not execute the body again.
                if (block.id in bodyEntries) emit("execute unless score $goto matches 0 run return 0")
                else emit("scoreboard players set $goto 0")
                for (operation in block.operations) {
                    site = operation.site
                    values.enter(operation)
                    when (operation) {
                        is PlanOperation.CaptureLocation -> locations.capture(operation.result, operation.location)
                        is PlanOperation.CaptureScore -> locations.captureScore(operation.result, operation.address)
                        is PlanOperation.Read -> values.read(operation.result, locations.get(operation.location))
                        is PlanOperation.Write -> locations.write(locations.get(operation.location), operation.value)
                        is PlanOperation.DeclareLocal -> locations.declare(operation.location, operation.initialValue)
                        is PlanOperation.Literal -> values.literal(operation.result, operation.value)
                        is PlanOperation.Unary -> values.unary(operation)
                        is PlanOperation.Binary -> values.binary(operation)
                        is PlanOperation.Convert -> values.convert(operation)
                        is PlanOperation.Field -> values.field(operation)
                        is PlanOperation.Index -> values.index(operation)
                        is PlanOperation.Move -> values.copy(operation.result, operation.value)
                        is PlanOperation.Call -> call(operation)
                        is PlanOperation.BlockMatches -> contexts.blockMatches(operation)
                    }
                }
                site = block.terminator.site
                values.enterExit(block.id)
                when (val exit = block.terminator) {
                    is PlanTerminator.Jump -> {
                        if (exit.unwind.regions.isEmpty()) jump(exit.target) else contexts.jump(exit.target)
                    }
                    is PlanTerminator.Branch -> contexts.branch(exit)
                    is PlanTerminator.Exit -> {
                        exit.value?.let { if (!values.compilerOnly(it)) values.copyTo(resultSlot ?: slot("result"), it) }
                        // A nonlocal exit records a plan-wide sentinel and unwinds every enclosing
                        // context before the generated function is allowed to return.
                        if (exit.unwind.regions.isNotEmpty()) emit("scoreboard players set $goto -1")
                        // This returns only the generated plan function, including CALLBACK exits.
                        emit("return 0")
                    }
                    is PlanTerminator.Unreachable -> emit("return 0")
                    is PlanTerminator.ContextEnter -> contexts.enter(
                        plan.contextRegions.getOrNull(exit.region.index)
                            ?: fail("Execution context region is not declared")
                    )
                    is PlanTerminator.ContextNext -> {
                        plan.contextRegions.getOrNull(exit.region.index)
                            ?: fail("Execution context region is not declared")
                        contexts.next()
                    }
                }
            }
            locations.finishCompilerWrites()
            result = plan.resultType?.let { resultType ->
                values.resultConstant()?.let { StorageAccess.literal(type(resultType), it, "mni_result", values.compilerTypes(it)) }
                    ?: if (type(resultType).hasRuntimeRepresentation) adapter(resultType, "mni_result", path("result"))
                    else fail("Compiler-only result does not have a unique closed value")
            }
            result?.let { values.attachReferences(values.resultReferences(), it) }
        } finally {
            Function.currFunction = previous
        }
    }

    internal fun edge(id: PlanBlockId) {
        val next = functions[id] ?: fail("Lowering edge refers to an unknown basic block")
        Function.currFunction.child.add(next)
    }
    internal fun jump(id: PlanBlockId) {
        edge(id)
        emit("return run function ${functions.getValue(id).namespaceID}")
    }
    internal fun recordGoto(id: PlanBlockId) {
        edge(id)
        // Block id zero is a valid target; zero is reserved for "no pending nonlocal edge".
        emit("scoreboard players set $goto ${id.index + 1}")
    }
    /** Explicit integration point for B02/B03; never execute a partial/default-completing call. */
    internal fun call(operation: PlanOperation.Call): Unit = calls.call(operation)

    private val calls = CompilerPlanCalls(this)
}
