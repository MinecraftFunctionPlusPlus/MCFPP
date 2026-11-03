package top.mcfpp.mni.expand

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.lib.*
import top.mcfpp.mni.api.*
import top.mcfpp.mni.api.SymbolId
import top.mcfpp.model.function.Function
import top.mcfpp.type.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.IdentityHashMap

/** A detached address operand. A captured operand retains its original data and Location. */
internal data class CompilerScoreAddress(
    val player: String,
    val objective: String,
    val type: TypeId,
    val contextBound: Boolean
)

internal class CompilerAddressOperand internal constructor(
    val constant: CompilerValue?,
    val reference: ValueRef?,
    val data: StoredData?,
    val location: Location?,
    val score: CompilerScoreAddress? = null,
    val contextBound: Boolean = score?.contextBound ?: false
)

internal sealed interface CompilerNbtSource {
    data class Storage(val storage: String) : CompilerNbtSource
    class Entity internal constructor(val operand: CompilerAddressOperand) : CompilerNbtSource
    class Block internal constructor(val operand: CompilerAddressOperand) : CompilerNbtSource
}

internal sealed interface CompilerNbtStep {
    data class Member(val value: CompilerAddressOperand) : CompilerNbtStep
    data class Index(val value: CompilerAddressOperand) : CompilerNbtStep
    data class Predicate(val value: CompilerAddressOperand) : CompilerNbtStep
    object Iterator : CompilerNbtStep
}

internal class CompilerNbtPath internal constructor(val source: CompilerNbtSource, steps: List<CompilerNbtStep>) {
    val steps: List<CompilerNbtStep> = immutableList(steps)
    val contextBound: Boolean = source !is CompilerNbtSource.Storage ||
        steps.any { step ->
            when (step) {
                is CompilerNbtStep.Member -> step.value.contextBound
                is CompilerNbtStep.Index -> step.value.contextBound
                is CompilerNbtStep.Predicate -> step.value.contextBound
                CompilerNbtStep.Iterator -> false
            }
        }
}

/** Only this compiler bridge holds live adapters. Code and plans receive fresh session symbols. */
internal class CompilerStorageLocation internal constructor(
    val data: StoredData?,
    val location: Location?,
    val layout: StorageLayout?,
    val path: CompilerNbtPath?,
    val version: Long?,
    val stackIndex: Int,
    val view: ValueRef.TypedView?,
    val trustConstants: Boolean,
    val contextBound: Boolean,
    val global: Boolean,
    val borrow: top.mcfpp.core.lang.context.ContextBorrowPayload? = null
)

/** Captured data roots are queried here after preparation, never through a live data.path getter. */
internal class CompilerStoredData internal constructor(
    val original: StoredData,
    val root: Place,
    val path: CompilerNbtPath,
    val layout: StorageLayout,
    layouts: Map<Place, StorageLayout>,
    val version: Long,
    sources: Map<top.mcfpp.analysis.SymbolId, StoredData>,
    facts: Map<Place, ValueFacts>
) {
    private val layouts = immutableMap(layouts)
    val sources = immutableMap(sources)
    val facts = immutableMap(facts)
    fun layoutAt(place: Place): StorageLayout = layouts[place] ?: layout
    fun source(place: Place): StoredData? = if (place.root == root.root) original else sources[place.root]
}

/**
 * A helper declaration bound into the same source domain as the root target.
 *
 * [parameters] are the exact physical stack slots used by the generated detached helper. The
 * canonical [function] identity is registered below before any root plan is emitted, so helper
 * calls never resolve through a display name or a live expansion handle.
 */
internal class CompilerHelperBinding internal constructor(
    val function: Function,
    val key: FunctionKey,
    val parameters: Map<SymbolId, CompilerStorageLocation>,
    val parameterOrder: List<SymbolId>,
    val refParameters: Set<SymbolId>,
    val globals: Map<SymbolId, CompilerStorageLocation>,
    val resultPath: String?
) {
    /** The detached CFG registered before the session arena closes. */
    internal var plan: LoweringPlan? = null
}

/** Enrollment is checked explicitly; canonical queries never resolve names or reread live addresses. */
internal class CompilerSourceBindings private constructor(
    private val prepared: CompilerSourceEnvironment,
    val session: ExpansionSession,
    val target: Function
) {
    val binding: BindingEnvironment = prepared.binding
    val macros = prepared.macros
    val declarations: Map<FunctionKey, BindingEnvironment> = prepared.declarationEnvironments
    val fieldCapabilities: Set<FieldKey> = prepared.fieldCapabilities
    private val originals = prepared.compilerSymbols
    /**
     * Member declarations are canonical source targets even though they never appear in
     * the unqualified global function view. Enrolling them here keeps the actual member
     * target, its prepared readonly facts and its declaration identity in the same bridge.
     */
    private val sourceFunctions = (
        binding.functions + binding.memberFunctionDeclarations +
            declarations.values.flatMap { it.functions }
        )
        .associateBy { (it.function as ImmutableFunctionRef<*>).key }
    private val functions: Map<FunctionKey, Function>
    private val locations: Map<SymbolId, CompilerStorageLocation>
    private val sessionOriginals: Map<SymbolId, Var<*>>
    /** Helpers are compiler-owned extensions of the immutable source map, never synthetic sources. */
    private val helperFunctions = linkedMapOf<FunctionKey, CompilerHelperBinding>()
    private val specializationStack = arrayListOf<FunctionKey>()
    val roots: Map<SymbolId, Ref<*>>
    val loweringTarget: LoweringTarget
    private val storageProof: StorageProof
    private val storedData: Map<Identity, CompilerStoredData>
    private val targetProof: List<Any?>

    init {
        prepared.requireCurrent()
        require(Function.currFunction === target) { "Source target is not the current compiler function" }
        val catalog = binding.types
        listOf(
            session.environment.types.intType(),
            session.environment.types.boolType(),
            session.environment.types.floatType(),
            session.environment.types.stringType(),
            session.environment.types.voidType()
        ).forEach {
            require(catalog.owns(it)) { "Session types must use the prepared source catalog" }
        }
        storageProof = StorageProof(originals.values.toList())
        storedData = storageProof.freezeData()
        targetProof = targetFacts(target)
        val universe = (catalog.ref(MCFPPBaseType.Int) as ImmutableTypeRef<*>).key.universe
        val targetKey = prepared.compilerFunctions.entries.firstOrNull { it.value === target }?.key
            ?: FunctionKey(universe, "compiler-target:${target.declarationId.value}")
        val declarationFunctions = sourceFunctions.values.mapNotNull { declaration ->
            prepared.compilerFunctions[(declaration.function as ImmutableFunctionRef<*>).key]?.let {
                declaration.declarationIdentity to it
            }
        }.toMap()
        functions = immutableMap(prepared.compilerFunctions + declarationFunctions + (targetKey to target))
        val refRoots = linkedMapOf<SymbolId, Ref<*>>()
        val physical = linkedMapOf<SymbolId, CompilerStorageLocation>()
        val adapters = linkedMapOf<SymbolId, Var<*>>()
        // Structural address keys contain identity wrappers, never Var/NBTSource.equals/hashCode.
        val aliases = linkedMapOf<List<Any?>, SymbolId>()
        val scoped = functions.values.flatMap { it.scope.vars.values }.toList()
        val sourceSymbols = (binding.symbols.values + declarations.values.flatMap { it.symbols.values })
            .associateBy { it.symbol }
        val positions = originals.mapValues { (_, variable) -> storageProof.location(variable, scoped.any { it === variable }) }
        originals.forEach { (sourceId, variable) ->
            val source = sourceSymbols[sourceId]
                ?: throw IllegalArgumentException("Compiler source identity has no declaration symbol")
            require(catalog.owns(source.type)) { "Source symbol belongs to another catalog" }
            val frozen = positions.getValue(sourceId)
            val context = if (frozen.contextBound) session.rootExecutionContext().id else null
            val alias = storageProof.alias(variable) + listOf((source.type as ImmutableTypeRef<*>).key.toString(), source.writable)
            val root = aliases.getOrPut(alias) { session.arena.freshSymbol() }
            val metadata = session.arena.metadata(session.arena.rootScope, session.origin, context)
            val reference = session.arena.ref(
                metadata,
                source.type,
                NodeLocation(root, executionContextId = context, writable = source.writable)
            )
            refRoots[sourceId] = reference
            physical.putIfAbsent(root, frozen)
            adapters.putIfAbsent(root, variable)
            session.registerBinding(reference, frozen.global)
        }
        roots = immutableMap(refRoots)
        locations = immutableMap(physical)
        sessionOriginals = immutableMap(adapters)
        sourceFunctions.values.forEach { session.registerFunction(it.function) }
        session.registerFieldCapabilities(fieldCapabilities) // Empty is an explicit deny-all capability.
        loweringTarget = LoweringTarget(
            targetKey,
            session.rootExecutionContext().id,
            PlanDomainStamp(setOf(universe), fingerprint(storageProof.facts), fingerprint(targetProof))
        )
        session.registerSourceProof(::requireCurrent)
    }

    /** Run before controlled compiler preparation. It is deliberately separate from table access. */
    fun requireCurrent() {
        prepared.requireCurrent()
        require(Function.currFunction === target && targetFacts(target) == targetProof) {
            "Compiler target changed; capture a fresh source binding domain"
        }
        storageProof.requireCurrent()
    }

    fun requireDomain(domain: PlanDomainStamp?) {
        require(domain != null && domain.complete && domain == loweringTarget.domain) { "Lowering plan belongs to another source/layout/target domain" }
    }

    /**
     * Bind one final-only helper into this compiler source domain.
     *
     * The caller has already proved the frozen source domain immediately before the production
     * journal's controlled writes begin (CompilerCommitBackend.prepare). This method deliberately
     * does not re-run the full pre-write declaration snapshot: helper registration itself mutates
     * NamespaceScope.functions, so that snapshot is expected to differ by the time the second
     * helper is bound. The precise helper checks below (final proof, canonical identity, exact
     * signature, parameter positions and enrolled captures) remain mandatory.
     */
    fun bindHelper(helper: StagedHelper, function: Function): CompilerHelperBinding {
        val key = (helper.function as ImmutableFunctionRef<*>).key
        helperFunctions[key]?.let { existing ->
            require(existing.function === function) { "Staged helper identity was rebound to another function" }
            return existing
        }
        val final = requireNotNull(helper.finalCode) { "A helper must pass final validation before it can be bound" }
        val signature = helper.function.parameters()
        val external = final.environment.externalBindings
        require(external.size >= signature.size) { "Helper environment lost its parameter or capture bindings" }
        val parameterRefs = external.takeLast(signature.size)
        val globals = external.dropLast(signature.size)
        val parameterSlots = linkedMapOf<SymbolId, CompilerStorageLocation>()
        val parameterOrder = arrayListOf<SymbolId>()
        val refParameters = linkedSetOf<SymbolId>()
        signature.zip(parameterRefs).forEach { (parameter, reference) ->
            val symbol = captureSymbol(reference)
            val compilerType = compilerType(parameter.type())
            require(compilerType.hasRuntimeRepresentation) { "Helper parameter '${parameter.name()}' has no runtime representation" }
            parameterSlots[symbol] = stackParameter(stackName(parameter.name()), compilerType)
            parameterOrder += symbol
            if (parameter.mode() == ParamMode.REF) refParameters += symbol
        }
        val globalSlots = linkedMapOf<SymbolId, CompilerStorageLocation>()
        globals.forEach { reference ->
            val symbol = captureSymbol(reference)
            globalSlots[symbol] = storage(symbol)
        }
        val result = if ((helper.function.returnType() as ImmutableTypeRef<*>).kind == TypeKind.VOID) null
        else NBTPath.stack.intIndex(0).memberIndex("return").toCommandPart().analyze()
        return CompilerHelperBinding(
            function,
            key,
            immutableMap(parameterSlots),
            immutableList(parameterOrder),
            immutableSet(refParameters),
            immutableMap(globalSlots),
            result
        ).also {
            helperFunctions[key] = it
        }
    }

    fun helper(key: FunctionKey): CompilerHelperBinding? = helperFunctions[key]

    /**
     * A REF helper is emitted as a stable call-site body whose parameter symbols are replaced by
     * the caller's captured locations. Recursive specialization has no finite address mapping, so
     * it is rejected before any partial body is installed.
     */
    internal fun <T> specializing(key: FunctionKey, action: () -> T): T {
        require(key !in specializationStack) { "Recursive REF helper specialization is not representable" }
        specializationStack += key
        try {
            return action()
        } finally {
            specializationStack.removeAt(specializationStack.lastIndex)
        }
    }

    private fun stackName(name: String): String {
        require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && name != "return") {
            "Helper parameter '$name' cannot be represented in a stack frame"
        }
        return name
    }

    private fun stackParameter(name: String, type: MCFPPType): CompilerStorageLocation {
        fun operand(value: CompilerValue) = CompilerAddressOperand(value, null, null, null)
        val path = CompilerNbtPath(
            CompilerNbtSource.Storage("mcfpp:system"),
            listOf(
                CompilerNbtStep.Member(operand(CompilerValue.Text("stack_frame"))),
                CompilerNbtStep.Index(operand(CompilerValue.Integral(0L))),
                CompilerNbtStep.Member(operand(CompilerValue.Text(name)))
            )
        )
        return CompilerStorageLocation(
            null, null, StorageLayout.Nbt("mcfpp:system", "stack_frame[0].$name"),
            path, null, 0, null, trustConstants = false, contextBound = false, global = true
        )
    }

    private fun captureSymbol(reference: Ref<*>): SymbolId = when (reference) {
        is ImmutableRef<*> -> reference.location.root
        is ImmutableLocal<*> -> reference.location.root
        is ImmutableMinecraftScoreRef -> throw IllegalArgumentException("Compiler helper captures cannot use a score reference")
    }

    fun original(source: SymbolId): Var<*> = originals[source] ?: sessionOriginals[source]
        ?: throw IllegalArgumentException("Source identity is outside this compiler bridge")
    fun storage(symbol: SymbolId): CompilerStorageLocation = helperFunctions.values.firstNotNullOfOrNull { it.parameters[symbol] }
        ?: locations[symbol]
        ?: throw IllegalArgumentException("Session symbol has no enrolled compiler position")
    fun stored(data: StoredData): CompilerStoredData = storedData[Identity(data)]
        ?: throw IllegalArgumentException("Captured data is outside this compiler bridge")
    fun function(key: FunctionKey): Function = helperFunctions[key]?.function ?: functions[key]
        ?: throw IllegalArgumentException("Function identity is outside this compiler bridge. Helper '$key' is not bound")
    fun readonly(key: FunctionKey): List<TypedConstant> {
        if (key in helperFunctions) return emptyList()
        require(key in sourceFunctions || key == loweringTarget.function) { "Readonly target is outside this compiler bridge" }
        return sourceFunctions[key]?.preparedReadonly?.arguments.orEmpty()
    }
    fun compilerType(type: TypeRef<*>): MCFPPType = binding.types.source(type)
    fun compilerType(type: PlanType): MCFPPType {
        val canonical = binding.types.graph.type(type.key)
        require(PlanType.from(canonical) == type) { "Plan type differs from its canonical source descriptor" }
        return binding.types.source(canonical)
    }

    companion object {
        fun capture(
            prepared: CompilerSourceEnvironment,
            session: ExpansionSession,
            target: Function = Function.currFunction
        ): CompilerSourceBindings = CompilerSourceBindings(prepared, session, target)

        /** The compiler entry creates its session with exactly the catalog used by binding/macros. */
        fun expansionEnvironment(
            prepared: CompilerSourceEnvironment,
            configurationKey: String = "",
            namespace: String = "mcfpp"
        ): ExpansionEnvironment {
            prepared.requireCurrent()
            return ExpansionEnvironment(
                SourceTypes(prepared.binding),
                prepared.binding.functions.map { it.function },
                configurationKey = configurationKey,
                namespace = namespace
            )
        }
    }

    /** Type-service overload selection reuses the source binder, including its default rules. */
    private class SourceTypes(private val binding: BindingEnvironment) : TypeService {
        @Suppress("UNCHECKED_CAST")
        private fun <T> ref(type: MCFPPType) = binding.types.ref(type) as TypeRef<T>
        override fun intType(): TypeRef<IntT> = ref(MCFPPBaseType.Int)
        override fun boolType(): TypeRef<BoolT> = ref(MCFPPBaseType.Bool)
        override fun floatType(): TypeRef<FloatT> = ref(MCFPPBaseType.Float)
        override fun stringType(): TypeRef<StringT> = ref(MCFPPBaseType.String)
        override fun voidType(): TypeRef<VoidT> = ref(MCFPPPrivateType.Void)
        override fun lookup(name: String): TypeRef<*> = binding.type(name) ?: error("Unknown or inaccessible source type '$name'")
        override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> {
            argumentTypes.forEach { require(binding.types.owns(it)) { "Argument type belongs to another source catalog" } }
            val candidates = binding.functions(name, SourceSpan.unknown().copy(file = binding.sourceFile))
            val arguments = argumentTypes.mapIndexed { index, type ->
                SourceSymbol("mniArgument$index", type, SymbolId(SessionId(0), -index.toLong() - 1))
            }
            val syntax = mcfppParser(
                CommonTokenStream(
                    mcfppLexer(
                        CharStreams.fromString(
                            "$name(${arguments.joinToString(",") { it.name }})",
                            binding.sourceFile
                        )
                    )
                )
            ).functionCall()
            val environment = BindingEnvironment(
                binding.types,
                arguments,
                candidates,
                binding.fields.values,
                sourceFile = binding.sourceFile,
                functionNames = mapOf(name to candidates),
                callCandidates = emptyMap()
            )
            return SnapshotSourceCodeBinder().call(syntax, environment).target.function
        }
    }
}

/** An identity token is safe even when the wrapped compiler class has effectful equals/toString. */
private class Identity(private val value: Any) {
    override fun equals(other: Any?): Boolean = other is Identity && value === other.value
    override fun hashCode(): Int = System.identityHashCode(value)
    fun text(): String = "${value.javaClass.name}:${System.identityHashCode(value)}"
}
private fun identity(value: Any?): Any? = value?.let(::Identity)

private fun raw(value: Any, name: String): Any? {
    var owner: Class<*>? = value.javaClass
    while (owner != null) {
        try {
            return owner.getDeclaredField(name).apply { isAccessible = true }.get(value)
        } catch (_: NoSuchFieldException) {
            owner = owner.superclass
        }
    }
    return null
}

/** Raw score carriers already name a physical register; their unused NBT path is not an address. */
private fun usesRawScores(variable: Var<*>): Boolean = (variable is MCNumber<*> || variable is ScoreBool) && raw(variable, "isDataOnly") == false

private fun rawScore(variable: Var<*>): CompilerScoreAddress? {
    if (!usesRawScores(variable)) return null
    val type = when {
        variable is MCInt && raw(variable, "type") === MCFPPBaseType.Int -> MCFPPBaseType.Int.typeId
        variable is ScoreBool && raw(variable, "type") === MCFPPBaseType.Bool -> MCFPPBaseType.Bool.typeId
        else -> return null
    }
    val player = raw(variable, "name") as? String ?: return null
    val objective = (raw(variable, if (variable is ScoreBool) "boolObject" else "sbObject") as? SbObject)?.name ?: return null
    return CompilerScoreAddress(player, objective, type, player.startsWith("@"))
}

/** Every mutable carrier reachable from an address is enrolled, including captured selector data. */
private class StorageProof(private val roots: List<Var<*>>) {
    private val enrolledData = arrayListOf<StoredData>()
    val facts: List<Any?> = snapshot()
    fun requireCurrent() {
        require(snapshot() == facts) { "Compiler storage address, layout or write version changed; capture a fresh source binding domain" }
    }

    private fun snapshot(): List<Any?> {
        val variables = IdentityHashMap<Var<*>, Boolean>()
        val data = IdentityHashMap<StoredData, Boolean>()
        val states = arrayListOf<Any?>()
        lateinit var visit: (Var<*>) -> Unit
        lateinit var stored: (StoredData) -> Unit
        fun path(path: NBTPath?): Any? {
            if (path == null) return null
            val source = when (val source = path.source) {
                is StorageSource -> listOf("storage", source.storage)
                is EntitySource -> {
                    visit(source.entity)
                    listOf("entity", identity(source.entity))
                }
                is BlockSource -> {
                    visit(source.pos)
                    listOf("block", identity(source.pos))
                }
                else -> throw IllegalArgumentException("Unsupported compiler NBT address source")
            }
            val parts = path.pathList.map { step ->
                when (step) {
                    is MemberPath -> {
                        visit(step.value)
                        listOf("member", identity(step.value))
                    }
                    is IntPath -> {
                        visit(step.value)
                        listOf("index", identity(step.value))
                    }
                    is NBTPredicatePath -> {
                        visit(step.value)
                        listOf("predicate", identity(step.value))
                    }
                    is IteratorPath -> listOf("iterator")
                    else -> throw IllegalArgumentException("Unsupported compiler NBT address step")
                }
            }
            return listOf(identity(path), identity(path.source), source, parts)
        }
        stored = { value ->
            if (data.put(value, true) == null) {
                if (enrolledData.none { it === value }) enrolledData.add(value)
                val state = listOf(
                    identity(value), frozen(value.root), path(value.path), frozen(value.layout),
                    value.versions.version(value.root),
                    (raw(value, "childLayouts") as Map<*, *>).entries.map { listOf(frozen(it.key), frozen(it.value)) },
                    value.types.entries.map { listOf(frozen(it.key), identity(it.value)) },
                    value.declarations.entries.map { listOf(frozen(it.key), frozen(it.value)) },
                    value.facts.entries().entries.map { listOf(frozen(it.key), frozen(it.value)) },
                    value.facts.knownLengths().entries.map { listOf(frozen(it.key), it.value) }, value.facts.reachable,
                    value.retainedSources().map(::identity)
                )
                states.add(state)
                value.retainedSources().forEach(stored)
            }
        }
        visit = { value ->
            if (variables.put(value, true) == null) {
                val binding = value.storageBinding
                val number = value as? MCNumber<*>
                val bool = value as? ScoreBool
                states.add(
                    listOf(
                        identity(value), value.javaClass.name, value.identifier, identity(raw(value, "type")),
                        value.stackIndex, identity(raw(value, "parent")), frozen(value.symbol),
                        value.storageReadVersion?.let { listOf(identity(it.first), it.second) },
                        identity(binding), binding?.let { listOf(identity(it.data), frozen(it.place), frozen(it.location), frozen(it.view), it.trustConstants) },
                        if (binding == null && usesRawScores(value)) null else path(raw(value, "nbtPath") as? NBTPath),
                        DeclarationLexicalSnapshot.rawValue(value)?.constant?.let(::frozen),
                        number?.let { listOf(it.name, identity(it.sbObject), it.sbObject.name, it.sbObject.criterion, identity(it.holder), it.isDataOnly) },
                        bool?.let { listOf(it.name, identity(it.boolObject), it.boolObject.name, it.boolObject.criterion, it.isDataOnly) },
                        top.mcfpp.core.lang.context.ContextBorrowing.payload(value)?.identity?.let(::frozen)
                    )
                )
                binding?.data?.let(stored)
                if (value is MCFloat) listOf(value.sign, value.int0, value.int1, value.exp).forEach(visit)
                if (value is Pos3Var) listOfNotNull(raw(value, "x") as? Var<*>, raw(value, "y") as? Var<*>, raw(value, "z") as? Var<*>).forEach(visit)
            }
        }
        roots.forEach(visit)
        return states
    }

    @Suppress("UNCHECKED_CAST")
    fun freezeData(): Map<Identity, CompilerStoredData> = enrolledData.associate { data ->
        Identity(data) to CompilerStoredData(
            data,
            data.root,
            freezePath(data.path),
            data.layout.checkpointCopy(),
            (raw(data, "childLayouts") as Map<Place, StorageLayout>).mapValues { it.value.checkpointCopy() },
            data.versions.version(data.root),
            data.retainedSources().associateBy { it.root.root },
            data.facts.entries()
        )
    }

    fun alias(variable: Var<*>): List<Any?> {
        val binding = variable.storageBinding
        return if (binding != null) listOf(identity(binding.data), frozen(binding.location))
        else when (variable) {
            is MCNumber<*> -> if (!variable.isDataOnly) listOf("score", variable.name, variable.sbObject.name)
            else listOf("adapter", identity(variable))
            is ScoreBool -> if (!variable.isDataOnly) listOf("score", variable.name, variable.boolObject.name)
            else listOf("adapter", identity(variable))
            else -> listOf("adapter", identity(variable))
        }
    }

    fun location(variable: Var<*>, scoped: Boolean): CompilerStorageLocation {
        val binding = variable.storageBinding
        val number = variable as? MCNumber<*>
        val bool = variable as? ScoreBool
        val path = binding?.data?.path ?: if (usesRawScores(variable)) null else raw(variable, "nbtPath") as? NBTPath
        val layout = binding?.data?.layoutAt(binding.place)?.checkpointCopy() ?: when {
            variable is MCFloat && !variable.isDataOnly -> StorageLayout.LegacyFloat(
                immutableList(
                    listOf(variable.sign, variable.int0, variable.int1, variable.exp).map { StorageLayout.Scoreboard(it.name, it.sbObject.name) }
                )
            )
            number != null && !number.isDataOnly -> StorageLayout.Scoreboard(number.name, number.sbObject.name)
            bool != null && !bool.isDataOnly -> StorageLayout.Scoreboard(bool.name, bool.boolObject.name)
            path != null -> null // An unbound NBT adapter is represented by its complete detached path.
            else -> throw IllegalArgumentException("Source variable has no existing compiler storage position")
        }
        val frozenPath = path?.let(::freezePath)
        val context = top.mcfpp.core.lang.context.ContextBorrowing.payload(variable) != null || frozenPath?.contextBound == true ||
            (layout is StorageLayout.Scoreboard && layout.player.startsWith("@")) ||
            (layout is StorageLayout.LegacyFloat && layout.components.any { it.player.startsWith("@") })
        val stack = frozenPath?.steps.orEmpty().any { step ->
            val constant = (step as? CompilerNbtStep.Member)?.value?.constant
            val payload = (constant as? CompilerValue.Typed)?.payload ?: constant
            (payload as? CompilerValue.Text)?.value?.startsWith("stack_frame") == true
        }
        return CompilerStorageLocation(
            binding?.data, binding?.location, layout, frozenPath,
            binding?.let { it.data.versions.version(it.place) }, variable.stackIndex, binding?.view,
            binding?.trustConstants ?: false, context, !scoped && !context && !stack,
            top.mcfpp.core.lang.context.ContextBorrowing.payload(variable)
        )
    }

    private fun operand(variable: Var<*>): CompilerAddressOperand {
        val binding = variable.storageBinding
        val constant = DeclarationLexicalSnapshot.rawValue(variable)?.constant
        val reference = binding?.view ?: binding?.let { ValueRef.Read(variable.type.typeId, it.place) }
        val score = if (binding == null) rawScore(variable) else null
        require(constant != null || binding != null || score != null) { "Compiler address operand has no frozen value or original captured location" }
        return CompilerAddressOperand(
            constant,
            reference,
            binding?.data,
            binding?.location,
            score,
            constant == null && addressContext(variable)
        )
    }

    private fun addressContext(variable: Var<*>, seen: IdentityHashMap<Var<*>, Boolean> = IdentityHashMap()): Boolean {
        if (seen.put(variable, true) != null) return false
        if (top.mcfpp.core.lang.context.ContextBorrowing.payload(variable) != null) return true
        val binding = variable.storageBinding
        if (binding == null && usesRawScores(variable)) return rawScore(variable)?.contextBound == true
        val layout = binding?.data?.layoutAt(binding.place)
        if (layout is StorageLayout.Scoreboard &&
            layout.player.startsWith("@") ||
            layout is StorageLayout.LegacyFloat &&
            layout.components.any { it.player.startsWith("@") }
        ) return true
        val path = binding?.data?.path ?: raw(variable, "nbtPath") as? NBTPath ?: return false
        if (path.source is EntitySource || path.source is BlockSource) return true
        return path.pathList.any { step ->
            when (step) {
                is MemberPath -> addressContext(step.value, seen)
                is IntPath -> addressContext(step.value, seen)
                is NBTPredicatePath -> addressContext(step.value, seen)
                else -> false
            }
        }
    }
    private fun freezePath(path: NBTPath): CompilerNbtPath {
        val source = when (val source = path.source) {
            is StorageSource -> CompilerNbtSource.Storage(source.storage)
            is EntitySource -> CompilerNbtSource.Entity(operand(source.entity))
            is BlockSource -> CompilerNbtSource.Block(operand(source.pos))
            else -> throw IllegalArgumentException("Unsupported compiler NBT source")
        }
        return CompilerNbtPath(
            source,
            path.pathList.map {
                when (it) {
                    is MemberPath -> CompilerNbtStep.Member(operand(it.value))
                    is IntPath -> CompilerNbtStep.Index(operand(it.value))
                    is NBTPredicatePath -> CompilerNbtStep.Predicate(operand(it.value))
                    is IteratorPath -> CompilerNbtStep.Iterator
                    else -> throw IllegalArgumentException("Unsupported compiler NBT step")
                }
            }
        )
    }
}

private fun targetFacts(target: Function): List<Any?> = listOf(
    identity(target), target.declarationId.value,
    identity(Project.config), Project.config.version, Project.config.rootNamespace,
    target.namespace, target.identifier, identity(target.scope), identity(target.returnType),
    target.scope.vars.entries.map { listOf(it.key, identity(it.value)) },
    identity(target.commands),
    target.commands.map { command ->
        listOf(
            identity(command),
            command.isCompleted,
            (raw(command, "commandParts") as List<*>).map { part ->
                listOf(
                    part?.javaClass?.name,
                    part?.let { raw(it, "command") },
                    identity(part?.let { raw(it, "v") }),
                    identity(part?.let { raw(it, "value") }),
                    part?.let { raw(it, "word") }
                )
            },
            frozen(raw(command, "replacePoint")),
            (raw(command, "tags") as List<*>).toList()
        )
    },
    target.child.map(::identity), target.parent.map(::identity),
    target.isReturned, identity(raw(target, "ast")), raw(target, "bodyCompiled")
)

/** Encode only closed compiler facts. No arbitrary compiler getter, equals or command renderer. */
private fun frozen(value: Any?): Any? = when (value) {
    null, is String, is Number, is Boolean -> value
    is Enum<*> -> listOf(value.javaClass.name, value.name)
    is TypeId.Builtin -> listOf("builtin", value.key)
    is TypeId.Declaration -> listOf("declaration", value.kind, value.namespace, value.name)
    is TypeId.Applied -> listOf("applied", frozen(value.constructor), value.arguments.map(::frozen))
    is TypeId.Specialized -> listOf("specialized", frozen(value.constructor), value.arguments.map(::frozen))
    is TypeId.Union -> listOf("union", value.alternatives.map(::frozen))
    is TypeId.Selector -> listOf("selector-type", value.limit, value.entities?.toList(), value.isName)
    is TypeId.Opaque -> listOf("opaque", value.declaration)
    is CompilerValue -> when (value) {
        CompilerValue.NullValue -> listOf("null")
        is CompilerValue.Integral -> listOf("int", value.value)
        is CompilerValue.FloatBits -> listOf("float", value.bits)
        is CompilerValue.DoubleBits -> listOf("double", value.bits)
        is CompilerValue.Bool -> listOf("bool", value.value)
        is CompilerValue.Text -> listOf("text", value.value)
        is CompilerValue.TypeValue -> listOf("type-value", frozen(value.id))
        is CompilerValue.Nbt -> listOf("nbt", value.snbt)
        is CompilerValue.Sequence -> listOf("sequence", value.elements.map(::frozen))
        is CompilerValue.Record -> listOf("record", value.fields.entries.map { listOf(it.key, frozen(it.value)) })
        is CompilerValue.Typed -> listOf("typed", frozen(value.type), frozen(value.payload))
    }
    is Place -> listOf(value.root.value, value.path.map(::frozen))
    is PathSegment.Field -> listOf("field", value.name)
    is PathSegment.Index -> listOf("index", value.index)
    PathSegment.UnknownIndex -> listOf("unknown-index")
    is Location -> listOf(
        frozen(value.place),
        value.frameOffset,
        frozen(value.indices),
        frozen(value.keys),
        frozen(value.captured),
        value.predicates.sorted(),
        frozen(value.capturedLocations)
    )
    is ValueRef.Constant -> listOf("constant", frozen(value.type), frozen(value.value))
    is ValueRef.Read -> listOf("read", frozen(value.type), frozen(value.place))
    is ValueRef.Result -> listOf("result", frozen(value.type), value.instruction)
    is ValueRef.TypedView -> listOf("view", frozen(value.type), frozen(value.source), frozen(value.place), frozen(value.location))
    is Symbol -> listOf(value.id.value, value.name, frozen(value.declaredType), value.mutable, value.forceRuntime, value.isLiteral, value.readonly,
        value.contextScope?.let { listOf(it.session.id, it.id, it.isActive) })
    is StorageLayout.Scoreboard -> listOf("score", value.player, value.objective)
    is StorageLayout.Nbt -> listOf("nbt", value.source, value.path)
    is StorageLayout.LegacyFloat -> listOf("legacy-float", value.components.map(::frozen))
    StorageLayout.Constant -> listOf("constant-layout")
    StorageLayout.CompilerOnly -> listOf("compiler-layout")
    is ValueFacts -> listOf(frozen(value.type), frozen(value.value), frozen(value.state), frozen(value.readableLayout), frozen(value.reference))
    is top.mcfpp.core.lang.ref.ReferenceMetadata -> listOf("reference", value.descriptors.map(::frozen),
        value.borrows.map { frozen(it.identity) }, frozen(value.targetFacts))
    is top.mcfpp.core.lang.ref.ReferenceDescriptor -> listOf(frozen(value.kind), frozen(value.fields), frozen(value.targetShape),
        value.pathShape.map { part -> when (part) {
            is top.mcfpp.core.lang.ref.ReferencePathShape.Field -> listOf("field", part.name)
            top.mcfpp.core.lang.ref.ReferencePathShape.Index -> listOf("index")
        } })
    is top.mcfpp.lib.EntityFacts -> listOf(value.upperBound, value.playersOnly, value.knownKinds?.sorted())
    is top.mcfpp.core.lang.ref.ReferenceTargetShape.Selector -> listOf("selector-shape", frozen(value.kind), value.filters.map {
        listOf(it.name, it.reverse, frozen(it.type))
    })
    top.mcfpp.core.lang.ref.ReferenceTargetShape.PlayerName -> "player-name-shape"
    top.mcfpp.core.lang.ref.ReferenceTargetShape.UUIDText -> "uuid-shape"
    top.mcfpp.core.lang.ref.ReferenceTargetShape.BoundExecutor -> "bound-executor-shape"
    is TypeKnowledge.Exact -> listOf("exact", frozen(value.type))
    is TypeKnowledge.Candidates -> listOf("candidates", value.types.map(::frozen))
    TypeKnowledge.Unknown -> listOf("unknown-type")
    is ValueKnowledge.Constant -> listOf("known", frozen(value.value))
    is ValueKnowledge.Program -> frozen(value.expression)
    is ValueKnowledge.Borrow -> listOf("scoped-borrow", frozen(value.payload.identity))
    is ValueKnowledge.Partial -> listOf("partial", frozen(value.parts))
    ValueKnowledge.Unknown -> listOf("unknown-value")
    is SelectorExpression -> listOf(
        "selector",
        value.selectorKind?.name,
        value.ownerFrame,
        value.predicates.map {
            listOf(it.identifier, it.reverse, frozen(it.operand.reference), frozen(it.operand.location))
        },
        value.directTarget?.let { listOf(it.kind.name, frozen(it.operand.reference), frozen(it.operand.location)) }
    )
    is Map<*, *> -> value.entries.map { listOf(frozen(it.key), frozen(it.value)) }
    else -> throw IllegalArgumentException("Unsupported frozen compiler fact '${value.javaClass.name}'")
}

private fun fingerprint(facts: List<Any?>): String {
    fun encode(value: Any?): String = when (value) {
        null -> "n"
        is List<*> -> "l${value.size}:${value.joinToString("") { encode(it) }}"
        is Identity -> encode(value.text())
        is String -> "s${value.length}:$value"
        is Boolean -> if (value) "t" else "f"
        is Number -> "${value.javaClass.simpleName}:${value};"
        else -> error("Nonclosed compiler fingerprint")
    }
    return MessageDigest.getInstance("SHA-256").digest(encode(facts).toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
