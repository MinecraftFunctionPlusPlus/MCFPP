package top.mcfpp.analysis

import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.model.Generic
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.function.SpecializationPolicy
import top.mcfpp.type.*
import java.util.Collections
import java.util.IdentityHashMap
import top.mcfpp.antlr.mcfppParser as P

private fun <T> signatureList(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
private fun <K, V> signatureMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))

internal data class CallableDeclarationId(val symbol: SymbolId)
internal data class PreparationDiagnostic(
    val code: PreparationFactCode,
    val source: SourceSpan,
    val message: String,
    val cause: Throwable? = null
)
internal data class PreparationLimits(
    val maxCandidates: Int = 4096,
    val maxFormals: Int = 1024,
    val maxTypeDepth: Int = 128,
    val maxTemplateHeaders: Int = 1024
) {
    init {
        require(maxCandidates > 0 && maxFormals > 0 && maxTypeDepth > 0 && maxTemplateHeaders > 0)
    }
}

/** Internal compiler fact; its immutable payload does not require a restored Var adapter. */
internal class PreparedExpressionFact private constructor(val bound: DeclarationBinding.Bound, val source: SourceSpan) {
    val type: MCFPPType get() = bound.type
    val value: CompilerValue get() = checkNotNull(bound.constant)

    companion object {
        fun readonly(lexical: DeclarationLexicalSnapshot, expression: P.ExpressionContext): PreparedExpressionFact = fromBound(
            DeclarationBinding(DeclarationPreparationContext(lexical)).readonly(expression),
            preparationSpan(expression),
            DeclarationBinding(DeclarationPreparationContext(lexical))
        )

        internal fun fromBound(
            value: DeclarationBinding.Bound,
            source: SourceSpan,
            binder: DeclarationBinding,
            maxDepth: Int = 128
        ): PreparedExpressionFact {
            fun invalid(message: String): Nothing = throw PreparationFactFailure(PreparationFactCode.NON_CLOSED_VALUE, source, message)
            val type = binder.preparedDeclaredType(value.type, source, maxDepth)
            val proof = value.constant ?: invalid("Readonly argument has no closed compiler proof")
            if (proof is CompilerValue.Typed && proof.type != type.typeId) invalid("Readonly proof has a different declared type")
            val descriptors = linkedMapOf(type.typeId to type)
            fun descriptor(id: TypeId): MCFPPType = descriptors[id] ?: value.descriptors[id]?.let {
                binder.preparedDeclaredType(it, source, maxDepth).also { frozen ->
                    if (frozen.typeId != id) invalid("Readonly descriptor identity does not match its key")
                    descriptors[id] = frozen
                }
            } ?: throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                source,
                "Readonly type value requires a prepared descriptor for $id"
            )
            fun collectType(current: MCFPPType) {
                descriptors.putIfAbsent(current.typeId, current)
                when (current) {
                    is MCFPPTypeWithGeneric -> current.generic.forEach(::collectType)
                    is MCFPPUnionType -> current.types.forEach(::collectType)
                }
            }
            collectType(type)
            fun inspect(current: CompilerValue, depth: Int) {
                if (depth > maxDepth) invalid("Readonly payload exceeds the preparation depth")
                when (current) {
                    is CompilerValue.Typed -> {
                        collectType(descriptor(current.type))
                        inspect(current.payload, depth + 1)
                    }
                    is CompilerValue.TypeValue -> collectType(descriptor(current.id))
                    is CompilerValue.Sequence -> current.elements.forEach { inspect(it, depth + 1) }
                    is CompilerValue.Record -> current.fields.values.forEach { inspect(it, depth + 1) }
                    is CompilerValue.FloatBits -> if (!Float.fromBits(current.bits).isFinite()) invalid("Readonly float must be finite")
                    is CompilerValue.DoubleBits -> if (!Double.fromBits(current.bits).isFinite()) invalid("Readonly double must be finite")
                    is CompilerValue.Integral -> if (type == MCFPPBaseType.Int && current.value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                        invalid("Readonly int must fit int32")
                    else -> Unit
                }
            }
            inspect(proof, 0)
            fun checkPayload(expected: MCFPPType, current: CompilerValue, depth: Int) {
                if (depth > maxDepth) invalid("Readonly payload exceeds the preparation depth")
                if (expected is MCFPPUnionType) {
                    val tagged = current as? CompilerValue.Typed
                    val branch = tagged?.let { value -> expected.types.firstOrNull { it.typeId == value.type } }
                    if (branch != null) {
                        checkPayload(branch, current, depth + 1)
                        return
                    }
                }
                val payload = if (current is CompilerValue.Typed) {
                    if (current.type != expected.typeId) invalid("Readonly member proof has a different type")
                    current.payload
                } else current
                val valid = when (expected) {
                    MCFPPBaseType.Int -> payload is CompilerValue.Integral && payload.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
                    MCFPPBaseType.Float -> payload is CompilerValue.FloatBits
                    MCFPPBaseType.Bool -> payload is CompilerValue.Bool
                    MCFPPBaseType.String -> payload is CompilerValue.Text
                    MCFPPConcreteType.Type -> payload is CompilerValue.TypeValue
                    MCFPPBaseType.Any, MCFPPBaseType.Object -> payload is CompilerValue.Typed
                    is MCFPPListType, is MCFPPImmutableListType -> {
                        val sequence = payload as? CompilerValue.Sequence ?: invalid("Readonly list proof has no sequence payload")
                        sequence.elements.forEach { checkPayload((expected as MCFPPTypeWithGeneric).generic.single(), it, depth + 1) }
                        true
                    }
                    is MCFPPCompoundType -> {
                        val record = payload as? CompilerValue.Record ?: invalid("Readonly dictionary proof has no record payload")
                        record.fields.values.forEach { checkPayload(expected.generic.single(), it, depth + 1) }
                        true
                    }
                    else -> true // Other H02 literal shapes retain their already-bound compiler proof.
                }
                if (!valid) invalid("Readonly payload does not match its declared type")
            }
            checkPayload(type, proof, 0)
            return PreparedExpressionFact(
                DeclarationBinding.Bound(
                    type,
                    if (proof is CompilerValue.Typed) proof else CompilerValue.Typed(type.typeId, proof),
                    Collections.unmodifiableSet(LinkedHashSet(value.dependencies)),
                    value.declaredTypeText,
                    signatureMap(descriptors)
                ),
                source
            )
        }
    }
}

/** Exact author-closure authority, obtained only from an open projection of the caller's own body. */
internal class StructuredAuthorAccess private constructor(
    private val body: top.mcfpp.model.function.BodyHandle,
    candidates: List<Function>
) {
    private val candidates = signatureList(candidates)

    fun permits(function: Function, caller: Function?): Boolean {
        if (caller == null || StructuredBindings.frame(caller).bodyHandle != body) return false
        body.requireOpen()
        return candidates.any { it === function }
    }

    companion object {
        fun capture(site: org.antlr.v4.runtime.ParserRuleContext, caller: Function): StructuredAuthorAccess? {
            val author = StructuredBindings.frame(caller)
            val body = author.bodyHandle ?: return null
            val projected = top.mcfpp.model.function.StructuredBodyContexts.projected(site) ?: return null
            if (projected.handle != body || projected.origin(site)?.node !is top.mcfpp.library.syntax.Expression.Name) return null
            val candidates = StructuredBindings.functions(site, author).orEmpty()
            return if (candidates.isEmpty()) null else StructuredAuthorAccess(body, candidates)
        }
    }
}

/** A missing caller grants public access only; candidates themselves are never an access capability. */
internal class SignaturePreparationRequest(
    candidates: List<Function>,
    explicitReadonly: List<PreparedExpressionFact>,
    val source: SourceSpan,
    receiverTypes: Map<String, MCFPPType> = emptyMap(),
    val caller: Function? = null,
    val authorAccess: StructuredAuthorAccess? = null
) {
    val candidates = signatureList(candidates)
    val explicitReadonly = signatureList(explicitReadonly)
    val receiverTypes = signatureMap(receiverTypes)
}

/** Formal identities remain those of the declaration, including defaults not evaluated here. */
internal class PreparedCompilerFormal(
    val identity: FunctionParam,
    val name: String,
    val type: MCFPPType,
    val readonly: Boolean,
    val hasDefault: Boolean,
    val defaultSyntax: P.ValueContext?,
    val defaultValue: CompilerValue?,
    defaultTypes: Map<TypeId, MCFPPType>
) {
    val typeIdentity: TypeId = type.typeId
    val defaultTypes = signatureMap(defaultTypes)
}

internal class PreparedSignatureIdentity(
    val declaration: CallableDeclarationId,
    val owner: TypeId?,
    readonly: List<CompilerValue>,
    normalTypes: List<TypeId>,
    val returnType: TypeId
) {
    val readonly = signatureList(readonly)
    val normalTypes = signatureList(normalTypes)
    override fun equals(other: Any?) = other is PreparedSignatureIdentity &&
        declaration == other.declaration &&
        owner == other.owner &&
        readonly == other.readonly &&
        normalTypes == other.normalTypes &&
        returnType == other.returnType
    override fun hashCode() = listOf(declaration, owner, readonly, normalTypes, returnType).hashCode()
}

/** Captured declaration facts are a version, not a guess at a global compiler revision. */
internal class SignatureDeclarationStamp(
    val namespace: String,
    val name: String,
    imports: Map<String, String>,
    defaults: List<String?>,
    val domain: DeclarationDomainStamp? = null
) {
    val imports = signatureMap(imports)
    val defaults = signatureList(defaults)
}

internal class PreparedCompilerSignature internal constructor(
    val declaration: CallableDeclarationId,
    internal val function: Function,
    val owner: TypeId?,
    val lexical: DeclarationLexicalSnapshot,
    readonly: List<PreparedCompilerFormal>,
    readonlyArguments: List<PreparedExpressionFact>,
    normal: List<PreparedCompilerFormal>,
    val returnType: MCFPPType,
    val source: SourceSpan,
    val stamp: SignatureDeclarationStamp,
    internal val headerReader: HeaderDeclarationRead? = null
) {
    val readonly = signatureList(readonly)
    val readonlyArguments = signatureList(readonlyArguments)
    val normal = signatureList(normal)
    val returnIdentity = returnType.typeId
    val identity = PreparedSignatureIdentity(
        declaration,
        owner,
        this.readonlyArguments.map { it.value },
        this.normal.map { it.typeIdentity },
        returnIdentity
    )
}

internal sealed interface PreparedCandidate {
    val declaration: CallableDeclarationId
    class Applicable(val signature: PreparedCompilerSignature) : PreparedCandidate {
        override val declaration = signature.declaration
    }
    class Inapplicable(override val declaration: CallableDeclarationId, val reason: String, val source: SourceSpan) : PreparedCandidate
}

internal class PreparedDeclarations(candidates: List<PreparedCandidate>) {
    val candidates = signatureList(candidates)
    val signatures = signatureList(candidates.filterIsInstance<PreparedCandidate.Applicable>().map { it.signature })
    val types: Map<TypeId, MCFPPType>
    init {
        val domain = linkedMapOf<TypeId, MCFPPType>()
        fun collect(type: MCFPPType) {
            if (domain.putIfAbsent(type.typeId, type) != null) return
            when (type) {
                is MCFPPTypeWithGeneric -> type.generic.forEach(::collect)
                is MCFPPUnionType -> type.types.forEach(::collect)
            }
        }
        signatures.forEach { signature ->
            (signature.readonly + signature.normal).forEach { formal ->
                collect(formal.type)
                formal.defaultTypes.values.forEach(::collect)
            }
            collect(signature.returnType)
            signature.readonlyArguments.forEach { it.bound.descriptors.values.forEach(::collect) }
        }
        types = signatureMap(domain)
    }
}

internal enum class DeclarationCompletionKind { MATERIALIZE_HEADER, PENDING_DECLARATION_EFFECTS, UNRESOLVED_ALIAS, REQUIRES_DECLARATION_LAYOUT }

/** Only real, shape-checked declaration failures can carry a completion capability. */
internal sealed class CompletionTarget(val kind: DeclarationCompletionKind, val identity: Any) {
    class Header(val reservation: PreparedTemplateReservation) : CompletionTarget(DeclarationCompletionKind.MATERIALIZE_HEADER, reservation.model)
    class Alias(val alias: MCFPPTypeAliasType) : CompletionTarget(DeclarationCompletionKind.UNRESOLVED_ALIAS, alias)
    class Declaration private constructor(val template: top.mcfpp.model.compound.DataTemplate, kind: DeclarationCompletionKind) : CompletionTarget(kind, template) {
        companion object {
            fun of(template: top.mcfpp.model.compound.DataTemplate): Declaration {
                template.preparationFacts() // exact compiler receiver and erased private-role validation
                return Declaration(
                    template,
                    if (template.pendingDeclarationAnnotations.isNotEmpty() || template.pendingFieldAnnotations.isNotEmpty())
                        DeclarationCompletionKind.PENDING_DECLARATION_EFFECTS else DeclarationCompletionKind.REQUIRES_DECLARATION_LAYOUT
                )
            }
        }
    }
}

internal class DeclarationCompletionRequest(
    val declaration: CallableDeclarationId,
    internal val function: Function,
    val lexical: DeclarationLexicalSnapshot,
    val source: SourceSpan,
    val reason: String,
    explicitReadonly: List<PreparedExpressionFact>,
    completedReadonly: List<PreparedExpressionFact>,
    val readonlyComplete: Boolean,
    val stamp: SignatureDeclarationStamp,
    val target: CompletionTarget
) {
    val kind: DeclarationCompletionKind get() = target.kind
    /** The target's actual converted readonly values, distinct from the calling function's formals. */
    val targetReadonly: List<PreparedExpressionFact> = signatureList((target as? CompletionTarget.Header)?.reservation?.readonlyFacts.orEmpty())
    val explicitReadonly = signatureList(explicitReadonly)
    val completedReadonly = signatureList(completedReadonly)
}

private class SignatureCompletion(val request: DeclarationCompletionRequest) : RuntimeException(request.reason)

/** A batch owns its real staged shells; publishing is an explicit declaration-phase operation. */
internal class PreparedHeaderBatch internal constructor(
    declarations: List<CallableDeclarationId>,
    requests: List<DeclarationCompletionRequest>,
    headers: List<PreparedTemplateReservation> = emptyList(),
    localHeaders: Map<PreparedTemplateReservation, PreparedTemplateHeader> = emptyMap(),
    domains: List<DeclarationDomainStamp> = emptyList(),
    internal val productionOptions: GenerationOptions? = null
) {
    val declarations = signatureList(declarations)
    val requests = signatureList(requests)
    val headers = signatureList(headers)
    private val localHeaders = signatureMap(localHeaders)
    private val domains = signatureList(domains + requests.map { checkNotNull(it.stamp.domain) })
    var consumed: Boolean = false
        private set
    var discarded: Boolean = false
        private set
    fun requireOpen() {
        check(!discarded) { "Signature preparation batch was discarded" }
        check(!consumed) { "Signature preparation batch was already consumed" }
    }
    /** No namespace registration or complete-cache publication is performed here. */
    internal fun publishHeaders() {
        requireOpen()
        // Preflight the entire batch before reserving any names or mutating any prototype.
        headers.forEach { it.prototype.checkHeaderPublication(it) }
        localHeaders.forEach { (owner, record) ->
            check(owner in headers && (owner.localHeader == null || owner.localHeader!!.hasSameFacts(record))) {
                "A published header changed; prepare a fresh declaration domain"
            }
        }
        headers.forEach { it.prototype.publishPreparedHeader(it) }
        localHeaders.forEach { (owner, record) -> if (owner.localHeader == null) owner.recordLocalHeader(record) }
    }
    /** The external declaration phase takes ownership once, after every domain and conflict check.
     * No annotation, compiler visitor, body or completion barrier runs during this transfer.
     */
    internal fun adoptForCompletion(): AdoptedCompletionBatch {
        requireOpen()
        check(!consumed) { "Signature preparation batch was already consumed" }
        if (productionOptions != null &&
            productionOptions != GenerationOptions(
                top.mcfpp.Project.config.version,
                top.mcfpp.CompileSettings.maxWhileInline,
                top.mcfpp.CompileSettings.foldIRConstants
            )
        )
            throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                requests.firstOrNull()?.source ?: headers.firstOrNull()?.source ?: SourceSpan.unknown(),
                "Generation options changed; prepare a fresh completion batch"
            )
        domains.forEach { it.requireCurrent() }
        // publishHeaders preflights all entries before its first mutation; its admitted operations
        // only install canonical relations/names and cannot invoke an external callback.
        publishHeaders()
        consumed = true
        return AdoptedCompletionBatch(declarations, requests, headers)
    }
    internal fun domainStamps(): List<DeclarationDomainStamp> = domains
    internal fun discard() {
        discarded = true
    }
}

internal class AdoptedCompletionBatch internal constructor(
    declarations: List<CallableDeclarationId>,
    requests: List<DeclarationCompletionRequest>,
    headers: List<PreparedTemplateReservation>
) {
    val declarations = signatureList(declarations)
    val requests = signatureList(requests)
    val headers = signatureList(headers)
}

internal sealed interface SignaturePreparationResult {
    class Ready(val declarations: PreparedDeclarations) : SignaturePreparationResult
    class NeedsCompletion(requests: List<DeclarationCompletionRequest>, val staged: PreparedHeaderBatch) : SignaturePreparationResult {
        val requests = signatureList(requests)
    }
    class Rejected(diagnostics: List<PreparationDiagnostic>) : SignaturePreparationResult {
        val diagnostics = signatureList(diagnostics)
    }
}

/** Local reader facts admit RESERVED shells; they are never a production Ready declaration batch. */
internal sealed interface HeaderCallPreparationResult {
    class Prepared(val facts: ParameterMatcher.PreparedCallFacts) : HeaderCallPreparationResult
    class NeedsCompletion(requests: List<DeclarationCompletionRequest>) : HeaderCallPreparationResult {
        val requests = signatureList(requests)
    }
    class Rejected(diagnostics: List<PreparationDiagnostic>) : HeaderCallPreparationResult {
        val diagnostics = signatureList(diagnostics)
    }
}

internal class HeaderDeclarationRead(val owner: PreparedTemplateReservation, val lexical: DeclarationLexicalSnapshot)

/** All candidates are prepared independently. This class has no normal-argument input or ranking. */
internal class SignaturePreparationSession(private val limits: PreparationLimits = PreparationLimits()) {
    internal val generationOptions = GenerationOptions(
        top.mcfpp.Project.config.version,
        top.mcfpp.CompileSettings.maxWhileInline,
        top.mcfpp.CompileSettings.foldIRConstants
    )
    private var discarded = false
    private var pending: PreparedHeaderBatch? = null
    private val templateHeaders = linkedMapOf<SpecializationKey, PreparedTemplateReservation>()
    private val stagedLocalHeaders = linkedMapOf<PreparedTemplateReservation, PreparedTemplateHeader>()

    internal fun ownsHeaderType(type: MCFPPType): Boolean = templateHeaders.values.any { it.type === type }

    internal fun requireHeaderReader(owner: PreparedTemplateReservation, source: SourceSpan) {
        if (discarded ||
            pending?.discarded == true ||
            pending?.consumed == true ||
            templateHeaders[owner.key] !== owner ||
            owner.key.options != generationOptions ||
            owner.type.checkpointPrivateState().elements.single() !== owner.model
        )
            throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                source,
                "Header reader belongs to a foreign or expired preparation batch"
            )
        owner.requireCurrentDeclaration()
    }

    internal fun ownsReaderHeaderType(owner: PreparedTemplateReservation, type: MCFPPType, source: SourceSpan): Boolean {
        requireHeaderReader(owner, source)
        val target = templateHeaders.values.firstOrNull { it.type === type } ?: return false
        requireHeaderReader(target, source)
        return true
    }

    /** Reader work extends this batch's canonical table without invalidating its current snapshot. */
    internal fun stageReaderHeader(
        owner: PreparedTemplateReservation,
        prototype: GenericDataTemplate,
        readonly: List<PreparedExpressionFact>,
        source: SourceSpan
    ): PreparedTemplateReservation {
        requireHeaderReader(owner, source)
        return reserveHeader(prototype, readonly, source, generationOptions)
    }

    /** Publish a new immutable snapshot explicitly, after the reader has collected more headers. */
    internal fun refreshHeaderReaderBatch(
        owner: PreparedTemplateReservation,
        source: SourceSpan = owner.source,
        additionalRequests: List<DeclarationCompletionRequest> = emptyList()
    ): PreparedHeaderBatch {
        requireHeaderReader(owner, source)
        val previous = pending
        val refreshed = PreparedHeaderBatch(
            previous?.declarations.orEmpty(),
            previous?.requests.orEmpty() + additionalRequests,
            templateHeaders.values.toList(),
            stagedLocalHeaders,
            previous?.domainStamps().orEmpty() +
                templateHeaders.values.map { DeclarationDomainStamp.captureRoots(listOf(it.prototype) + it.readonlyFacts.flatMap { fact -> fact.bound.descriptors.values }, it.source) },
            previous?.productionOptions
        )
        previous?.discard()
        pending = refreshed
        return refreshed
    }

    /** The caller explicitly transfers this immutable snapshot to the local AST preparation queue. */
    internal fun readHeaders(
        batch: PreparedHeaderBatch,
        lexical: DeclarationLexicalSnapshot,
        limits: HeaderReadLimits = HeaderReadLimits()
    ): HeaderReadResult = PreparedTemplateHeaderReader(this, lexical, limits).read(batch)

    internal fun readerReservations(owner: PreparedTemplateReservation): List<PreparedTemplateReservation> {
        requireHeaderReader(owner, owner.source)
        return signatureList(templateHeaders.values)
    }

    internal fun recordReaderHeader(owner: PreparedTemplateReservation, record: PreparedTemplateHeader): PreparedTemplateHeader {
        requireHeaderReader(owner, owner.source)
        val shared = owner.prototype.preparedHeader(owner.key) === owner
        if (!shared) {
            owner.recordLocalHeader(record)
            return record
        }
        val previous = owner.localHeader
        if (previous != null) {
            if (!previous.hasSameFacts(record)) throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                owner.source,
                "Published header facts changed; declaration completion requires a fresh domain"
            )
            return previous
        }
        // A shell published before reading has no record to verify. Keep its new local facts in
        // this session until the caller explicitly publishes the refreshed batch.
        stagedLocalHeaders[owner] = record
        return record
    }

    internal fun checkpointHeaderReader(batch: PreparedHeaderBatch, source: SourceSpan): () -> Unit {
        if (discarded || batch.discarded || pending !== batch || batch.headers.any { templateHeaders[it.key] !== it })
            throw PreparationFactFailure(PreparationFactCode.UNPREPARED_TYPE, source, "Header queue requires the current owned batch")
        val entries = LinkedHashMap(templateHeaders)
        val local = LinkedHashMap(stagedLocalHeaders)
        val states = entries.values.map { it.checkpoint() }
        return {
            states.forEach { it() }
            templateHeaders.clear()
            templateHeaders.putAll(entries)
            stagedLocalHeaders.clear()
            stagedLocalHeaders.putAll(local)
        }
    }

    private fun reserveHeader(
        prototype: GenericDataTemplate,
        readonly: List<PreparedExpressionFact>,
        source: SourceSpan,
        options: GenerationOptions
    ): PreparedTemplateReservation {
        PreparedTemplateReservation.requireSupported(prototype, source)
        if (readonly.size != prototype.readOnlyParams.size)
            throw PreparationFactFailure(
                PreparationFactCode.FORMAL_DEPENDENCY,
                source,
                "A template reservation requires every readonly formal"
            )
        val key = SpecializationKey(prototype.declarationId, readonly.map { SpecializationArgument.Constant(it.value) }, options)
        templateHeaders[key]?.let {
            it.requireCurrentDeclaration()
            return it
        }
        if (templateHeaders.size >= limits.maxTemplateHeaders)
            throw PreparationFactFailure(PreparationFactCode.UNPREPARED_TYPE, source, "Template header specialization budget exceeded")
        return prototype.reservePreparedHeader(key, readonly, source).also { templateHeaders[key] = it }
    }

    /** Arguments are the already closed, converted formal facts; this entry never evaluates syntax. */
    fun stageHeader(
        prototype: GenericDataTemplate,
        readonly: List<PreparedExpressionFact>,
        source: SourceSpan,
        options: GenerationOptions = generationOptions
    ): PreparedTemplateReservation {
        check(!discarded) { "Signature preparation session was discarded" }
        if (pending != null) {
            pending!!.discard()
            pending = null
            templateHeaders.clear()
            stagedLocalHeaders.clear()
        }
        try {
            return reserveHeader(prototype, readonly, source, options)
        } catch (failure: PreparationFactFailure) {
            templateHeaders.clear()
            throw failure
        }
    }

    /** Transfer to controlled declaration completion without inventing a successful header reader. */
    fun stagedHeaderBatch(): PreparedHeaderBatch {
        check(!discarded) { "Signature preparation session was discarded" }
        pending?.let {
            it.requireOpen()
            return it
        }
        return PreparedHeaderBatch(
            emptyList(),
            emptyList(),
            templateHeaders.values.toList(),
            stagedLocalHeaders,
            templateHeaders.values.map { DeclarationDomainStamp.captureRoots(listOf(it.prototype) + it.readonlyFacts.flatMap { fact -> fact.bound.descriptors.values }, it.source) }
        ).also { pending = it }
    }

    fun prepare(request: SignaturePreparationRequest): SignaturePreparationResult {
        check(!discarded) { "Signature preparation session was discarded" }
        if (pending != null) {
            pending!!.discard()
            pending = null
            templateHeaders.clear()
            stagedLocalHeaders.clear()
        }
        val result = prepareCandidates(request)
        if (result.diagnostics.isNotEmpty()) {
            templateHeaders.clear()
            stagedLocalHeaders.clear()
            return SignaturePreparationResult.Rejected(result.diagnostics)
        }
        if (result.completion.isNotEmpty() || templateHeaders.values.any { it.phase != HeaderPhase.COMPLETE }) {
            val domains = try {
                request.candidates.map { function ->
                    DeclarationDomainStamp.capture(
                        DeclarationLexicalSnapshot.capture(function, this),
                        function,
                        request.source,
                        request.candidates + listOfNotNull(request.caller) + request.receiverTypes.values +
                            request.explicitReadonly.flatMap { it.bound.descriptors.values }
                    )
                }
            } catch (failure: PreparationFactFailure) {
                templateHeaders.clear()
                stagedLocalHeaders.clear()
                return SignaturePreparationResult.Rejected(listOf(PreparationDiagnostic(failure.code, failure.source, failure.message.orEmpty(), failure.cause)))
            }
            val batch = PreparedHeaderBatch(
                result.declarations,
                result.completion,
                templateHeaders.values.toList(),
                domains = domains,
                productionOptions = generationOptions
            )
            pending = batch
            return SignaturePreparationResult.NeedsCompletion(result.completion, batch)
        }
        return SignaturePreparationResult.Ready(PreparedDeclarations(result.prepared))
    }

    /** No normal argument input or winner selection; H04 consumes these facts afterwards. */
    internal fun prepareReaderCall(
        owner: PreparedTemplateReservation,
        ownerLexical: DeclarationLexicalSnapshot,
        request: SignaturePreparationRequest
    ): HeaderCallPreparationResult {
        val before = LinkedHashMap(templateHeaders)
        try {
            requireHeaderReader(owner, request.source)
            DeclarationPreparationContext.forHeaderDeclaration(ownerLexical, owner, ownerLexical, source = request.source)
            if (ownerLexical.headerSession !== this) throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                request.source,
                "Header call belongs to a foreign preparation session"
            )
            val result = prepareCandidates(request, HeaderDeclarationRead(owner, ownerLexical))
            if (result.diagnostics.isNotEmpty()) {
                templateHeaders.clear()
                templateHeaders.putAll(before)
                return HeaderCallPreparationResult.Rejected(result.diagnostics)
            }
            if (result.completion.isNotEmpty()) return HeaderCallPreparationResult.NeedsCompletion(result.completion)
            return HeaderCallPreparationResult.Prepared(ParameterMatcher.PreparedCallFacts(request, PreparedDeclarations(result.prepared)))
        } catch (failure: PreparationFactFailure) {
            templateHeaders.clear()
            templateHeaders.putAll(before)
            return HeaderCallPreparationResult.Rejected(listOf(PreparationDiagnostic(failure.code, failure.source, failure.message.orEmpty(), failure.cause)))
        }
    }

    private class CandidatePreparation(
        val declarations: List<CallableDeclarationId>,
        val prepared: List<PreparedCandidate>,
        val completion: List<DeclarationCompletionRequest>,
        val diagnostics: List<PreparationDiagnostic>
    )

    private fun prepareCandidates(request: SignaturePreparationRequest, reader: HeaderDeclarationRead? = null): CandidatePreparation {
        fun rejected(message: String) = CandidatePreparation(
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(
                PreparationDiagnostic(PreparationFactCode.UNSUPPORTED_EXPRESSION, request.source, message)
            )
        )
        if (request.candidates.size > limits.maxCandidates) return rejected("Signature candidate budget exceeded")
        // Validate raw receivers/containers/input syntax before lexical capture consults any of
        // their declaration views. This is a check only; no member is restored or materialized.
        try {
            DeclarationDomainStamp.captureRoots(
                request.candidates + listOfNotNull(request.caller) +
                    request.receiverTypes.values + request.explicitReadonly.flatMap { it.bound.descriptors.values },
                request.source
            )
        } catch (failure: PreparationFactFailure) {
            return CandidatePreparation(
                emptyList(),
                emptyList(),
                emptyList(),
                listOf(
                    PreparationDiagnostic(failure.code, failure.source, failure.message.orEmpty(), failure.cause)
                )
            )
        }
        // Cached bodies are implementation artifacts, even if a consumer accidentally supplies them.
        val cached = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
        request.candidates.forEach { cached.addAll(it.compiledFunctions.values) }
        val seen = hashSetOf<SymbolId>()
        val originals = request.candidates.filter { it !in cached && seen.add(it.declarationId) }
        val prepared = arrayListOf<PreparedCandidate>()
        val completion = arrayListOf<DeclarationCompletionRequest>()
        val diagnostics = arrayListOf<PreparationDiagnostic>()
        for (function in originals) {
            try {
                val lexical = DeclarationLexicalSnapshot.capture(function, this)
                if (lexical.formals.size > limits.maxFormals) return rejected("Signature formal budget exceeded")
                prepared.add(
                    if (reader == null && request.authorAccess == null) SpecializationPolicy.prepareDeclaredSignature(
                        function,
                        lexical,
                        request.explicitReadonly,
                        request.source,
                        request.receiverTypes,
                        limits.maxTypeDepth,
                        request.caller
                    )
                    else PureSignaturePreparation.prepare(
                        function,
                        lexical,
                        request.explicitReadonly,
                        request.source,
                        request.receiverTypes,
                        limits.maxTypeDepth,
                        request.caller,
                        reader,
                        request.authorAccess
                    )
                )
            } catch (failure: SignatureCompletion) {
                completion.add(failure.request)
            } catch (failure: PreparationFactFailure) {
                diagnostics.add(PreparationDiagnostic(failure.code, failure.source, failure.message.orEmpty(), failure.cause))
            }
        }
        return CandidatePreparation(originals.map { CallableDeclarationId(it.declarationId) }, prepared, completion, diagnostics)
    }

    fun discard() {
        pending?.discard()
        pending = null
        templateHeaders.clear()
        stagedLocalHeaders.clear()
        discarded = true
    }
}

/** Implementation of the explicit pure policy entry, sharing H02 facts and its type gate. */
internal object PureSignaturePreparation {
    fun prepare(
        function: Function,
        lexical: DeclarationLexicalSnapshot,
        supplied: List<PreparedExpressionFact>,
        source: SourceSpan,
        receiverTypes: Map<String, MCFPPType>,
        maxDepth: Int,
        caller: Function?,
        reader: HeaderDeclarationRead? = null,
        authorAccess: StructuredAuthorAccess? = null
    ): PreparedCandidate {
        val owner = function.owner
        val access = function.accessModifier
        val accessible = when {
            access == top.mcfpp.model.Member.AccessModifier.PUBLIC -> true
            caller == null -> false
            owner is top.mcfpp.model.compound.DataTemplate -> caller.accessTo(owner) >= access
            owner == null && access == top.mcfpp.model.Member.AccessModifier.PRIVATE -> authorAccess?.permits(function, caller) == true
            else -> owner != null && caller.owner === owner && access == top.mcfpp.model.Member.AccessModifier.PRIVATE
        }
        if (!accessible) throw PreparationFactFailure(
            PreparationFactCode.UNSUPPORTED_EXPRESSION,
            source,
            "Cannot access declaration method '${function.identifier}'"
        )
        if (lexical.formals.map { it.name }.distinct().size != lexical.formals.size)
            throw PreparationFactFailure(PreparationFactCode.FORMAL_DEPENDENCY, source, "Duplicate declaration formal name")
        val progress = arrayListOf<PreparedExpressionFact>()
        try {
            return prepareCandidate(function, lexical, supplied, source, receiverTypes, maxDepth, progress, reader)
        } catch (failure: PreparationFactFailure) {
            val target = failure.completion ?: throw failure
            throw SignatureCompletion(
                DeclarationCompletionRequest(
                    CallableDeclarationId(function.declarationId),
                    function, lexical, failure.source, failure.message.orEmpty(), supplied, progress,
                    progress.size == lexical.formals.count { it.readonly },
                    SignatureDeclarationStamp(
                        lexical.namespace,
                        function.identifier,
                        lexical.imports,
                        lexical.formals.map { it.defaultSyntax?.text },
                        DeclarationDomainStamp.capture(
                            lexical,
                            function,
                            failure.source,
                            listOfNotNull(caller) + receiverTypes.values
                        )
                    ),
                    target
                )
            )
        }
    }

    private fun prepareCandidate(
        function: Function,
        lexical: DeclarationLexicalSnapshot,
        supplied: List<PreparedExpressionFact>,
        source: SourceSpan,
        receiverTypes: Map<String, MCFPPType>,
        maxDepth: Int,
        facts: ArrayList<PreparedExpressionFact>,
        reader: HeaderDeclarationRead?
    ): PreparedCandidate {
        val id = CallableDeclarationId(function.declarationId)
        val readonly = lexical.formals.filter { it.readonly }
        val parameters = ((function as? Generic<*>)?.readOnlyParams ?: (function as? NativeFunction)?.readOnlyParams.orEmpty()).toList()
        val normal = function.normalParams.toList()
        fun inapplicable(message: String) = PreparedCandidate.Inapplicable(id, message, source)
        if (supplied.size > readonly.size) return inapplicable("Too many readonly arguments")
        if (parameters.drop(supplied.size).any { !it.hasDefault }) return inapplicable("Missing required readonly argument")
        val previous = linkedMapOf<String, DeclarationBinding.Bound>()
        val typeBindings = linkedMapOf<String, MCFPPType>()
        fun context(
            index: Int,
            previous: Map<String, DeclarationBinding.Bound> = emptyMap(),
            bindings: Map<String, MCFPPType> = emptyMap()
        ) = if (reader == null)
            DeclarationPreparationContext(lexical, index, previous, bindings)
        else DeclarationPreparationContext.forHeaderDeclaration(
            lexical,
            reader.owner,
            reader.lexical,
            index,
            previous,
            bindings,
            source
        )
        val receiverBinder = DeclarationBinding(context(0))
        receiverTypes.forEach { (name, type) -> typeBindings[name] = receiverBinder.preparedDeclaredType(type, source, maxDepth) }
        val readonlyFormals = arrayListOf<PreparedCompilerFormal>()
        fun binder(index: Int) = DeclarationBinding(context(index, previous, typeBindings))
        fun declared(type: MCFPPType, binder: DeclarationBinding, site: SourceSpan = source): MCFPPType {
            val facts = previous.mapValues { (_, value) ->
                top.mcfpp.model.function.ClosedReadonlyBinding(value.type, requireNotNull(value.constant), value.descriptors)
            }
            val resolved = SpecializationPolicy.structuredType(function, type, facts) ?: type
            return binder.preparedDeclaredType(resolved, site, maxDepth)
        }
        fun formal(parameter: FunctionParam, type: MCFPPType, isReadonly: Boolean): PreparedCompilerFormal {
            val defaults = linkedMapOf<TypeId, MCFPPType>()
            parameter.defaultTypes.forEach { (key, value) ->
                val frozen = binder(readonly.size).preparedDeclaredType(value, source, maxDepth)
                if (key != frozen.typeId) throw PreparationFactFailure(
                    PreparationFactCode.UNPREPARED_TYPE,
                    source,
                    "Default descriptor requires a fresh declaration domain"
                )
                defaults[key] = frozen
            }
            fun collect(current: MCFPPType) {
                if (defaults.putIfAbsent(current.typeId, current) != null) return
                when (current) {
                    is MCFPPTypeWithGeneric -> current.generic.forEach(::collect)
                    is MCFPPUnionType -> current.types.forEach(::collect)
                }
            }
            collect(type)
            fun proof(value: CompilerValue, depth: Int) {
                if (depth > maxDepth) throw PreparationFactFailure(
                    PreparationFactCode.NON_CLOSED_VALUE,
                    source,
                    "Default proof exceeds the preparation depth"
                )
                fun need(id: TypeId) {
                    val descriptor = defaults[id] ?: typeBindings.values.firstOrNull { it.typeId == id }
                        ?: listOf(
                            MCFPPBaseType.Int,
                            MCFPPBaseType.Float,
                            MCFPPBaseType.Bool,
                            MCFPPBaseType.String,
                            MCFPPConcreteType.Type,
                            MCFPPPrivateType.Null
                        ).firstOrNull { it.typeId == id }
                        ?: throw PreparationFactFailure(
                            PreparationFactCode.UNPREPARED_TYPE,
                            source,
                            "Default proof requires prepared descriptor $id"
                        )
                    collect(binder(readonly.size).preparedDeclaredType(descriptor, source, maxDepth))
                }
                when (value) {
                    is CompilerValue.Typed -> {
                        need(value.type)
                        proof(value.payload, depth + 1)
                    }
                    is CompilerValue.TypeValue -> need(value.id)
                    is CompilerValue.Sequence -> value.elements.forEach { proof(it, depth + 1) }
                    is CompilerValue.Record -> value.fields.values.forEach { proof(it, depth + 1) }
                    else -> Unit
                }
            }
            parameter.defaultValue?.let { proof(it, 0) }
            return PreparedCompilerFormal(
                parameter, parameter.identifier, type, isReadonly,
                parameter.hasDefault, parameter.defaultSyntax(), parameter.defaultValue, defaults
            )
        }
        for ((index, parameter) in parameters.withIndex()) {
            val bind = binder(index)
            val defaultSyntax = parameters[index].defaultSyntax() ?: readonly[index].defaultSyntax
            val site = defaultSyntax?.let { top.mcfpp.mni.expand.StructuredSourceFacts.span(it) } ?: source
            val target = declared(readonly[index].type, bind, site)
            val raw = supplied.getOrNull(index)?.bound ?: defaultSyntax?.let(bind::readonlyValue)
                ?: readonly[index].defaultValue?.let { proof ->
                    val proofType = (proof as? CompilerValue.Typed)?.type ?: target.typeId
                    val actual = if (proofType == target.typeId) target else readonly[index].defaultTypes[proofType]
                        ?: listOf(
                            MCFPPBaseType.Int,
                            MCFPPBaseType.Float,
                            MCFPPBaseType.Bool,
                            MCFPPBaseType.String,
                            MCFPPConcreteType.Type
                        ).firstOrNull { it.typeId == proofType }
                        ?: throw PreparationFactFailure(PreparationFactCode.UNPREPARED_TYPE, site, "Default value has no prepared source type")
                    DeclarationBinding.Bound(actual, proof, descriptors = readonly[index].defaultTypes)
                }
                ?: return inapplicable("Readonly default has no prepared value")
            val argument = PreparedExpressionFact.fromBound(raw, supplied.getOrNull(index)?.source ?: site, bind, maxDepth)
            if (TypeRelations.resolveImplicitConversion(argument.type, target) == null)
                return inapplicable("Readonly argument '$index' does not adapt to the declared type")
            val constrained = if (target == MCFPPBaseType.Any || target == MCFPPBaseType.Object) {
                argument.bound.copy(type = target, constant = CompilerValue.Typed(target.typeId, argument.value))
            } else bind.constrain(argument.bound, target)
            if (constrained.constant == null) return inapplicable("Readonly conversion has no closed compiler proof")
            val fact = PreparedExpressionFact.fromBound(constrained, argument.source, bind, maxDepth)
            facts.add(fact)
            previous[readonly[index].name] = fact.bound
            val payload = (fact.value as CompilerValue.Typed).payload
            if (payload is CompilerValue.TypeValue) typeBindings[readonly[index].name] = fact.bound.descriptors[payload.id]
                ?: throw PreparationFactFailure(PreparationFactCode.UNPREPARED_TYPE, site, "Type argument has no prepared descriptor")
            // Do not resolve another default's descriptors until all its predecessors are available.
            readonlyFormals.add(
                PreparedCompilerFormal(
                    parameter, readonly[index].name, target, true,
                    parameter.hasDefault, defaultSyntax,
                    readonly[index].defaultValue, fact.bound.descriptors
                )
            )
        }
        val bind = binder(readonly.size)
        val normals = normal.map { formal(it, declared(it.type, bind), false) }
        val result = declared(function.returnType, bind)
        (normals.map { it.type } + result).firstNotNullOfOrNull { TypeUsage.ordinaryDiagnostic(it) }?.let {
            throw PreparationFactFailure(PreparationFactCode.UNSUPPORTED_EXPRESSION, source, it)
        }
        val capturedReadonly = parameters.mapIndexed { index, parameter -> formal(parameter, readonlyFormals[index].type, true) }
        return PreparedCandidate.Applicable(
            PreparedCompilerSignature(
                id, function, function.owner?.inheritanceIdentity,
                lexical, capturedReadonly, facts, normals, result, source,
                SignatureDeclarationStamp(
                    lexical.namespace,
                    function.identifier,
                    lexical.imports,
                    (parameters + normal).map { it.defaultContext?.text }
                ),
                reader
            )
        )
    }
}
