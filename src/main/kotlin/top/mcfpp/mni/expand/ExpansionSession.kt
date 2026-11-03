package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

internal data class ExpansionSite(val span: SourceSpan, val canonicalMacroSignature: String, val configurationKey: String = "") {
    init {
        require(canonicalMacroSignature.isNotBlank()) { "A macro site requires its canonical signature" }
    }
    internal fun logicalKey(): String = listOf(
        span.file.replace('\\', '/'),
        span.startOffset.toString(),
        span.endOffset.toString(),
        canonicalMacroSignature,
        configurationKey
    ).joinToString("") { "${it.length}:$it" }
}

/** One instance belongs to one compilation unit; names and budget ownership survive its root calls. */
internal class ExpansionEnvironment(
    val types: TypeService,
    functions: List<FunctionRef<*>> = emptyList(),
    inlineTargets: List<FunctionRef<*>> = emptyList(),
    val configurationKey: String = "",
    val namespace: String = "mcfpp",
    val minecraftFactory: ((CodeBuilder) -> MinecraftBuilder)? = null
) {
    val functions = immutableList(functions)
    val inlineTargets = immutableList(inlineTargets)
    private val epoch = SessionEpoch.current()
    private var compilationBudget: ExpansionBudget? = null
    private val occupiedInstances = linkedMapOf<String, MutableSet<Int>>()
    init {
        require(namespace.matches(Regex("[a-z0-9_.-]+"))) { "Invalid macro resource namespace" }
    }

    internal fun check(site: SourceSpan) {
        if (epoch != SessionEpoch.current()) throw ExpansionFailure(ExpansionDiagnostic(site, "Expansion environment belongs to an earlier Project.init"))
    }
    internal fun bind(budget: ExpansionBudget, site: SourceSpan) {
        check(site)
        check(compilationBudget == null || compilationBudget === budget) { "A compilation unit must share one expansion budget" }
        compilationBudget = budget
    }
    internal fun reserve(site: ExpansionSite): InstanceLease {
        check(site.span)
        val key = site.logicalKey()
        val occupied = occupiedInstances.getOrPut(key) { linkedSetOf() }
        var index = 0
        while (index in occupied) index++
        occupied += index
        return InstanceLease(this, key, index)
    }
    internal fun release(key: String, index: Int) {
        occupiedInstances[key]?.remove(index)
    }
}

internal class InstanceLease(private val environment: ExpansionEnvironment, private val key: String, val index: Int) {
    private var committed = false
    private var released = false
    fun commit() {
        check(!released)
        committed = true
    }
    fun release() {
        if (!committed && !released) {
            environment.release(key, index)
            released = true
        }
    }
}

/**
 * Project.init advances this epoch directly instead of relying on a mutable stage-callback list.
 * ProjectStateJournal can restore callback lists transactionally, which must not be able to remove
 * the lifecycle hook after it has been installed once.
 */
private object SessionEpoch {
    private var generation = 0L
    private val roots = arrayListOf<ExpansionSession>()
    fun current(): Long = generation
    fun add(session: ExpansionSession) {
        roots += session
    }
    fun remove(session: ExpansionSession) {
        roots.remove(session)
    }
    fun invalidate() {
        generation++
        roots.toList().asReversed().forEach { it.invalidate() }
    }
}

/** Explicit Project.init lifecycle entry: invalidate every expansion owned by the previous epoch. */
internal fun invalidateExpansionSessions() = SessionEpoch.invalidate()

internal class StagedExpansion internal constructor(val code: PrevalidatedCode, val sessionId: SessionId)

/** Host services are revoked independently of compiler-owned, adopted immutable trees. */
internal class ExpansionSession private constructor(
    val site: ExpansionSite,
    val budget: ExpansionBudget,
    val environment: ExpansionEnvironment,
    val transaction: CompilerTransaction,
    private val parent: ExpansionSession?,
    private val childOrdinal: Int?
) : AutoCloseable {
    constructor(site: ExpansionSite, budget: ExpansionBudget, environment: ExpansionEnvironment, transaction: CompilerTransaction) :
        this(site, budget, environment, transaction, null, null)

    private enum class Status { ACTIVE, STAGED, ADOPTED, COMMITTED, CLOSED, FAILED }
    private var status = Status.ACTIVE
    private val root: ExpansionSession = parent?.root ?: this
    val id = SessionId(nextId())
    private val access: BuilderAccess = parent?.access ?: BuilderAccess()
    private val instance: InstanceLease?
    private val path: String
    private val children = arrayListOf<ExpansionSession>()
    private val childOrdinals = linkedSetOf<Int>()
    private val bindings = arrayListOf<Ref<*>>()
    private val globals = arrayListOf<Ref<*>>()
    private val functions = linkedMapOf<FunctionKey, FunctionRef<*>>()
    private val inlineTargets = linkedMapOf<FunctionKey, FunctionRef<*>>()
    private val types = linkedMapOf<TypeKey, TypeRef<*>>()
    private var fieldCapabilities: MutableSet<FieldKey>? = parent?.fieldCapabilities?.toMutableSet()
    private val sourceProofs = arrayListOf<() -> Unit>()
    private var nameOrdinal = 0
    private var staged: StagedExpansion? = null
    private val frame: BudgetFrame
    val arena: CodeArena
    val origin: Origin
    private val context: MacroContext
    private val guardedTypes: TypeService get() = typeService
    private val typeService: TypeService = object : TypeService {
        private fun <T> use(block: () -> TypeRef<T>): TypeRef<T> = construct { block().also(::registerType) }
        override fun intType(): TypeRef<IntT> = use { environment.types.intType() }
        override fun boolType(): TypeRef<BoolT> = use { environment.types.boolType() }
        override fun floatType(): TypeRef<FloatT> = use { environment.types.floatType() }
        override fun stringType(): TypeRef<StringT> = use { environment.types.stringType() }
        override fun voidType(): TypeRef<VoidT> = use { environment.types.voidType() }
        override fun lookup(name: String): TypeRef<*> = construct { environment.types.lookup(name).also(::registerType) }
        override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = construct {
            environment.types.function(name, argumentTypes).also(::registerFunction)
        }
    }

    init {
        var reserved: InstanceLease? = null
        try {
            environment.bind(budget, site.span)
            reserved = if (parent == null) environment.reserve(site) else null
            instance = reserved
            path = (parent?.path?.plus("/child$childOrdinal/") ?: "instance${instance!!.index}/") + site.logicalKey()
            frame = budget.enter(ExpansionFrame(site.canonicalMacroSignature, site.span))
        } catch (failure: Throwable) {
            reserved?.release()
            transaction.discard()
            throw failure
        }
        origin = Origin(site.span, expansionFrames = budget.frames().map { ExpansionOriginFrame(it.macroSignature, it.callSite) })
        val scope = ScopeId(id, 0)
        arena = if (parent == null) CodeArena(id, scope, ::account) else parent.arena.childArena(id, scope)
        try {
            environment.functions.forEach(::registerFunction)
            environment.inlineTargets.forEach(::registerInlineTarget)
            listOf(
                environment.types.intType(),
                environment.types.boolType(),
                environment.types.floatType(),
                environment.types.stringType(),
                environment.types.voidType()
            ).forEach(::registerType)
            context = Context()
            if (parent == null) SessionEpoch.add(this)
        } catch (failure: Throwable) {
            arena.close()
            frame.cancel()
            instance?.release()
            transaction.discard()
            throw failure
        }
    }

    private fun arenaLive() {
        environment.check(site.span)
        if (status in setOf(Status.CLOSED, Status.FAILED, Status.COMMITTED) || root.status in setOf(Status.CLOSED, Status.FAILED, Status.COMMITTED))
            throw ExpansionFailure(ExpansionDiagnostic(site.span, "Expansion session is closed", budget.frames()))
    }
    private fun requireSourceProofs() {
        parent?.requireSourceProofs()
        sourceProofs.forEach { it() }
    }

    /** Compiler-only proofs cover original declarations, layouts and addresses, never author state. */
    internal fun registerSourceProof(proof: () -> Unit) {
        arenaLive()
        proof()
        sourceProofs.add(proof)
    }
    internal fun rootExecutionContext(): ExecutionContextFrame {
        arenaLive()
        return access.rootContext(arena, site.span)
    }
    internal fun registerFieldCapabilities(fields: Set<FieldKey>) {
        arenaLive()
        val capabilities = fieldCapabilities ?: linkedSetOf<FieldKey>().also { fieldCapabilities = it }
        capabilities.addAll(fields)
    }
    private fun host() {
        arenaLive()
        if (status != Status.ACTIVE) throw ExpansionFailure(ExpansionDiagnostic(site.span, "Macro construction context is closed", budget.frames()))
    }
    private fun account(count: Int, origin: Origin) {
        arenaLive()
        try {
            budget.allocate(count, origin)
        } catch (failure: ExpansionFailure) {
            root.fail(failure.diagnostic, failure)
        }
    }

    private inline fun <T> construct(operation: () -> T): T {
        host()
        try {
            return operation()
        } catch (failure: Throwable) {
            val diagnostic = when (failure) {
                is ExpansionFailure -> failure.diagnostic
                is CodeModelException -> ExpansionDiagnostic(failure.source, failure.message ?: "Invalid generated code", budget.frames())
                else -> ExpansionDiagnostic(site.span, "Macro construction failed: ${failure.message ?: failure.javaClass.simpleName}", budget.frames())
            }
            root.fail(diagnostic, failure)
        }
    }

    fun context(): MacroContext {
        host()
        return context
    }
    fun registerBinding(reference: Ref<*>, global: Boolean = false) {
        arenaLive()
        val (owner, location) = when (reference) {
            is ImmutableRef<*> -> reference.metadata to reference.location
            is ImmutableLocal<*> -> reference.metadata to reference.location
            is ImmutableMinecraftScoreRef -> throw CodeModelException(reference.source(), "Unsupported reference node '${reference.javaClass.name}'")
        }
        require(owner.sessionId == id) { "Binding belongs to another session" }
        if (global) {
            require(location.executionContextId == null && owner.executionContextId == null) { "A context-bound location cannot be a global helper capture" }
            require(owner.scopeId == arena.rootScope && reference !is Local<*>) { "A local cannot be registered as a global helper capture" }
            globals += reference
        }
        bindings += reference
        registerType(reference.type())
    }
    fun registerFunction(function: FunctionRef<*>) {
        arenaLive()
        functions.putIfAbsent((function as ImmutableFunctionRef<*>).key, function)
        function.parameters().forEach { registerType(it.type()) }
        function.readonlyParameters().forEach { registerType(it.type()) }
        registerType(function.returnType())
    }
    fun registerInlineTarget(function: FunctionRef<*>) {
        arenaLive()
        inlineTargets.putIfAbsent((function as ImmutableFunctionRef<*>).key, function)
    }
    private fun registerType(type: TypeRef<*>) {
        types.putIfAbsent((type as ImmutableTypeRef<*>).key, type)
    }

    private fun knownFunctions(): List<FunctionRef<*>> = (functions.values + transaction.allHelpers().map { it.function }).distinctBy { (it as ImmutableFunctionRef<*>).key }
    fun validationEnvironment(): ValidationEnvironment {
        arenaLive()
        requireSourceProofs()
        return ValidationEnvironment(
            arena,
            bindings,
            knownFunctions(),
            inlineTargets.values.toList(),
            types = types.values.toList(),
            fieldCapabilities = fieldCapabilities
        )
    }

    fun child(site: ExpansionSite): ExpansionSession {
        arenaLive()
        check(status in setOf(Status.ACTIVE, Status.STAGED)) { "An adopted session cannot start host construction" }
        check(children.none { it.status in setOf(Status.ACTIVE, Status.STAGED) }) { "Finish the active child expansion first" }
        var ordinal = 0
        while (ordinal in childOrdinals) ordinal++
        childOrdinals += ordinal
        try {
            return ExpansionSession(site, budget, environment, transaction.child(), this, ordinal).also { children += it }
        } catch (failure: Throwable) {
            childOrdinals.remove(ordinal)
            val diagnostic = if (failure is ExpansionFailure) failure.diagnostic
            else ExpansionDiagnostic(site.span, failure.message ?: "Child expansion failed", budget.frames())
            root.fail(diagnostic, failure)
        }
    }

    fun stage(code: Code): StagedExpansion {
        host()
        check(children.none { it.status in setOf(Status.ACTIVE, Status.STAGED) }) { "A child expansion is still active" }
        try {
            if (code !is Expr<*> && code !is Block) throw CodeModelException(code.source(), "A macro result must be an expression or block")
            val pre = CodeValidator().validate(code, validationEnvironment(), ValidationPhase.PRE_EXPANSION) as PrevalidatedCode
            return StagedExpansion(pre, id).also {
                staged = it
                status = Status.STAGED
            }
        } catch (failure: Throwable) {
            val source = (failure as? CodeModelException)?.source ?: site.span
            root.fail(ExpansionDiagnostic(source, failure.message ?: "Invalid macro result", budget.frames()), failure)
        }
    }

    private fun final(code: ValidatedCode): ValidatedCode {
        arenaLive()
        check(status == Status.STAGED) { "Stage the generated result before completing expansion" }
        val original = requireNotNull(staged).code.code
        when {
            original is Expr<*> && code.code is Expr<*> ->
                require(original.type().sameType((code.code as Expr<*>).type())) { "Expansion changed the result's actual type" }
            original is Block && code.code is Block -> Unit
            else -> error("Expansion changed the result's code kind")
        }
        return CodeValidator().validate(code.code, validationEnvironment(), ValidationPhase.FINAL) as ValidatedCode
    }

    /** Child state is adopted without exposing a commit operation to macro authors. */
    fun acceptChild(code: ValidatedCode) {
        require(parent != null) { "A root session must complete with a lowering plan" }
        final(code)
        check(children.none { it.status in setOf(Status.ACTIVE, Status.STAGED) }) { "A child expansion is still active" }
        frame.requireCurrent()
        transaction.merge()
        functions.forEach { (key, value) -> parent.functions.putIfAbsent(key, value) }
        inlineTargets.forEach { (key, value) -> parent.inlineTargets.putIfAbsent(key, value) }
        types.forEach { (key, value) -> parent.types.putIfAbsent(key, value) }
        fieldCapabilities?.let { parent.registerFieldCapabilities(it) }
        parent.sourceProofs.addAll(sourceProofs)
        status = Status.ADOPTED
        frame.close()
    }

    /** Explicit export is required even after the child's artifacts have been adopted. */
    fun <C : Code> importChild(
        child: ExpansionSession,
        code: C,
        targetScope: ScopeId = arena.rootScope,
        externalBindings: Map<SymbolId, Ref<*>> = emptyMap(),
        contextBindings: Map<ExecutionContextId, ExecutionContextFrame> = emptyMap()
    ): C {
        arenaLive()
        require(child.parent === this && child.status == Status.ADOPTED) { "Only an adopted direct child can export code" }
        return arena.importChildTree(child.arena, code, targetScope, origin, externalBindings, contextBindings)
    }

    /**
     * Export an adopted child result while keeping each node's own origin. The nested expander must
     * not overwrite the call-site spans its child already recorded.
     */
    internal fun <C : Code> importChildResult(
        child: ExpansionSession,
        code: C,
        targetScope: ScopeId,
        externalBindings: Map<SymbolId, Ref<*>> = emptyMap(),
        contextBindings: Map<ExecutionContextId, ExecutionContextFrame> = emptyMap()
    ): C {
        arenaLive()
        require(child.parent === this && child.status == Status.ADOPTED) { "Only an adopted direct child can export code" }
        return arena.importMacroChildTree(child.arena, code, targetScope, null, externalBindings, contextBindings)
    }

    /** Helpers staged by this session still need recursive macro expansion before final validation. */
    internal fun stagedHelpers(): List<StagedHelper> {
        arenaLive()
        return transaction.snapshot().helpers
    }

    internal fun finalizeExpandedHelper(helper: StagedHelper, code: Block) {
        arenaLive()
        requireSourceProofs()
        if (helper.finalCode != null) return
        require(helper.code.environment.arena === arena) { "A nested helper is finalized by its own session" }
        val previous = helper.code.environment
        val environment = ValidationEnvironment(
            previous.arena,
            previous.externalBindings,
            (previous.functions + knownFunctions()).distinctBy { (it as ImmutableFunctionRef<*>).key },
            previous.inlineTargets,
            previous.resultRegion,
            previous.types,
            previous.fieldCapabilities
        )
        val final = CodeValidator().validate(code, environment, ValidationPhase.FINAL) as ValidatedCode
        transaction.finalizeHelper(helper.function, final)
    }

    /** The expander replaces each helper body before lowering. This convenience accepts already expanded helpers. */
    fun finalizeHelpers() {
        arenaLive()
        requireSourceProofs()
        transaction.snapshot().helpers.forEach { helper ->
            if (helper.finalCode == null) {
                val previous = helper.code.environment
                val environment = ValidationEnvironment(
                    previous.arena,
                    previous.externalBindings,
                    (previous.functions + knownFunctions()).distinctBy { (it as ImmutableFunctionRef<*>).key },
                    previous.inlineTargets,
                    previous.resultRegion,
                    previous.types,
                    previous.fieldCapabilities
                )
                val final = CodeValidator().validate(helper.code.code, environment, ValidationPhase.FINAL) as ValidatedCode
                transaction.finalizeHelper(helper.function, final)
            }
        }
    }

    /** Only the compiler pipeline receives the transaction and its lowering/journal participant. */
    fun complete(code: ValidatedCode, lowering: CompilerJournalParticipant) {
        require(parent == null) { "A child expansion must be adopted" }
        try {
            final(code)
            frame.requireCurrent()
            transaction.enlist(lowering)
            transaction.commit()
            instance!!.commit()
            status = Status.COMMITTED
            finishArenas()
        } catch (failure: Throwable) {
            root.fail(ExpansionDiagnostic(site.span, failure.message ?: "Macro commit failed", budget.frames()), failure)
        }
    }

    fun abort(diagnostic: ExpansionDiagnostic, cause: Throwable? = null): Nothing = root.fail(diagnostic, cause)
    private fun fail(diagnostic: ExpansionDiagnostic, cause: Throwable? = null): Nothing {
        discard(Status.FAILED)
        throw ExpansionFailure(diagnostic).also { if (cause != null) it.initCause(cause) }
    }
    internal fun invalidate() {
        discard(Status.CLOSED)
    }
    private fun finishArenas() {
        children.asReversed().forEach { it.finishArenas() }
        arena.close()
        if (status != Status.ADOPTED) frame.close()
        if (parent == null) SessionEpoch.remove(this)
    }
    private fun discard(destination: Status) {
        if (status in setOf(Status.CLOSED, Status.FAILED, Status.COMMITTED)) return
        children.asReversed().forEach { it.discard(destination) }
        transaction.discard()
        val wasAdopted = status == Status.ADOPTED
        arena.close()
        if (!wasAdopted) frame.cancel()
        status = destination
        instance?.release()
        if (!wasAdopted) childOrdinal?.let { parent?.childOrdinals?.remove(it) }
        if (parent == null) SessionEpoch.remove(this)
    }
    override fun close() {
        if (status == Status.ADOPTED) return // Compiler-owned state remains live until its root finishes.
        discard(Status.CLOSED)
    }

    /** Backend allocations share the expansion's instance path, ordinal and transaction. */
    internal fun reserveCompilerName(hint: String, kind: String = "compiler"): String {
        arenaLive()
        require(kind.matches(Regex("[a-z][a-z0-9_]*"))) { "Invalid generated compiler name kind" }
        return name(hint, kind)
    }

    private fun name(hint: String, kind: String): String {
        require(hint.isNotBlank()) { "A generated declaration requires a hint" }
        val ordinal = nameOrdinal++
        val logical = listOf(path, environment.configurationKey, kind, ordinal.toString(), hint)
            .joinToString("") { "${it.length}:$it" }
        val digest = MessageDigest.getInstance("SHA-256").digest(logical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        val safeHint = hint.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_").take(24).ifBlank { "generated" }
        val value = if (kind == "objective") "m${digest.take(15)}" else "${environment.namespace}:mni/${digest.take(24)}/${kind}_${ordinal}_$safeHint"
        transaction.stage(StagedName(logical, value))
        return value
    }

    private inner class Context : MacroContext {
        private val quote = object : Quote {
            private val delegate = DefaultQuote(arena, guardedTypes, origin, minecraftFactory = environment.minecraftFactory, access = access)
            override fun block(body: Consumer<CodeBuilder>): Block = construct { delegate.block(body) }
            override fun <T> expression(type: TypeRef<T>, body: Consumer<CodeBuilder>): Expr<T> = construct { delegate.expression(type, body) }
        }
        private val helper = object : HelperService {
            override fun <R> define(hint: String, signature: FunctionSignature<R>, body: GeneratedBody<R>): FunctionRef<R> = construct {
                if (signature.parameters().any { it.mode() !in setOf(ParamMode.VALUE, ParamMode.REF) })
                    throw CodeModelException(site.span, "Helpers support only VALUE and REF parameters")
                val generatedName = name(hint, "helper")
                val function = ImmutableFunctionRef(FunctionKey(root.id.value, generatedName), generatedName, signature, FunctionKind.HELPER)
                registerFunction(function)
                val scope = arena.childScope(arena.rootScope)
                val metadata = arena.metadata(scope, origin)
                val parameterRefs = signature.parameters().map { parameter ->
                    arena.ref(metadata, parameter.type(), NodeLocation(arena.freshSymbol(), writable = parameter.mode() == ParamMode.REF))
                }
                val parameters = signature.parameters().zip(parameterRefs).map { (parameter, ref) ->
                    if (parameter.mode() == ParamMode.REF) ref else arena.value(ref.load())
                }
                val result = if ((signature.returnType() as ImmutableTypeRef<*>).kind == TypeKind.VOID) null
                else YieldTarget(signature.returnType(), arena.freshRegion())
                val builder = DefaultCodeBuilder(arena, guardedTypes, origin, scope, access, result, minecraftFactory = environment.minecraftFactory)
                builder.consume { body.build(it, immutableList(parameters)) }
                val block = builder.finish(requireResult = result != null)
                val helperEnvironment = ValidationEnvironment(
                    arena,
                    globals + parameterRefs,
                    knownFunctions(),
                    inlineTargets.values.toList(),
                    result?.let { ValidationRegion(it.region, it.type) },
                    types.values.toList(),
                    fieldCapabilities
                )
                val pre = CodeValidator().validate(block, helperEnvironment, ValidationPhase.PRE_EXPANSION) as PrevalidatedCode
                transaction.stage(StagedHelper(function, pre))
                function
            }
        }
        private val resource = object : ResourceService {
            override fun reserve(kind: ResourceKind, hint: String, immutableData: ResourceData): ResourceRef = construct {
                validateResource(kind, immutableData)
                val generatedName = name(hint, kind.name.lowercase(Locale.ROOT))
                transaction.stage(StagedResource(kind, generatedName, immutableData, site.span))
                object : ResourceRef {
                    override fun kind(): ResourceKind {
                        host()
                        return kind
                    }
                    override fun name(): String {
                        host()
                        return generatedName
                    }
                    override fun source(): SourceSpan {
                        host()
                        return site.span
                    }
                }
            }
        }
        private val diagnostic = object : DiagnosticService {
            override fun warning(site: SourceSpan, message: String) = construct { transaction.warning(ExpansionDiagnostic(site, message, budget.frames())) }
            override fun error(site: SourceSpan, message: String) {
                host()
                abort(ExpansionDiagnostic(site, message, budget.frames()))
            }
        }
        override fun quote(): Quote {
            host()
            return quote
        }
        override fun types(): TypeService {
            host()
            return guardedTypes
        }
        override fun diagnostics(): DiagnosticService {
            host()
            return diagnostic
        }
        override fun helpers(): HelperService {
            host()
            return helper
        }
        override fun resources(): ResourceService {
            host()
            return resource
        }
        override fun callSite(): SourceSpan {
            host()
            return site.span
        }
    }

    private fun validateResource(kind: ResourceKind, data: ResourceData) {
        val record = data.value() as? ConstantValue.Record ?: throw CodeModelException(site.span, "A resource requires a structured record descriptor")
        when (kind) {
            ResourceKind.OBJECTIVE -> if (record.fields.keys != setOf("criteria") || record.fields["criteria"] != ConstantValue.Text("dummy"))
                throw CodeModelException(site.span, "Only a dummy objective descriptor is supported")
            ResourceKind.STORAGE -> {
                if (record.fields.keys != setOf("initial")) throw CodeModelException(site.span, "Storage requires exactly one initial value")
                fun supported(value: ConstantValue): Boolean = when (value) {
                    is ConstantValue.Integral, is ConstantValue.Bool, is ConstantValue.FloatBits, is ConstantValue.DoubleBits, is ConstantValue.Text -> true
                    is ConstantValue.Record -> value.fields.values.all(::supported)
                    is ConstantValue.Sequence -> value.elements.all(::supported)
                    else -> false
                }
                if (!supported(record.fields.getValue("initial"))) throw CodeModelException(site.span, "Unsupported structured storage value")
            }
            ResourceKind.INTERNAL_FUNCTION -> throw CodeModelException(site.span, "Use HelperService for structured internal functions")
        }
    }

    companion object {
        private val ids = AtomicLong()
        private fun nextId(): Long = ids.updateAndGet { previous ->
            check(previous < Long.MAX_VALUE) { "Expansion session identity space exhausted" }
            previous + 1
        }
    }
}
