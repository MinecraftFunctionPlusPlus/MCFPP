package top.mcfpp.analysis

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.CompiledGenericObjectDataTemplate
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.GenericObjectDataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.RawPreparationFact
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.type.*
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPGenericDataTemplateType
import top.mcfpp.type.MCFPPGenericObjectDataTemplateType
import top.mcfpp.type.TypeId
import top.mcfpp.util.StringHelper.splitNamespaceID
import java.util.Collections
import top.mcfpp.antlr.mcfppParser as P

internal enum class HeaderPhase { RESERVED, HEADER_READY, COMPLETING, COMPLETE }

/**
 * A reservation is a typed canonical shell, never a claim that its empty scope is a field view.
 * The reader/queue adds completed header facts in a subsequent stage. Until then a new reservation
 * can only require completion. No host callback or runtime adapter is retained here.
 */
internal class PreparedTemplateReservation private constructor(
    val prototype: GenericDataTemplate,
    val key: SpecializationKey,
    val identity: TypeId.Specialized,
    val model: CompiledGenericDataTemplate,
    readonlyFacts: List<PreparedExpressionFact>,
    val source: SourceSpan,
    val type: MCFPPDataTemplateType,
    private var currentPhase: HeaderPhase,
    private var published: Boolean
) {
    val readonlyFacts: List<PreparedExpressionFact> = Collections.unmodifiableList(ArrayList(readonlyFacts))
    // A legacy specialization is installed before its outer declaration barrier runs.
    // Observe the actual model's revocable proof without mutating compiler state during staging.
    // RESERVED shells still need a reader/materialization owner even if their empty scope was
    // accidentally submitted to a declaration barrier.
    val phase: HeaderPhase get() = if (currentPhase == HeaderPhase.COMPLETING && model.hasCompletedDeclaration)
        HeaderPhase.COMPLETE else currentPhase
    val isPublished: Boolean get() = published
    private var header: PreparedTemplateHeader? = null
    internal val localHeader: PreparedTemplateHeader? get() = header
    private val originalNamespace = prototype.namespace
    private val originalName = prototype.identifier
    private val parameterNames = prototype.readOnlyParams.map { it.identifier to it.typeIdentifier }
    private val flags = prototype.isAbstract to prototype.isFinal
    private val bodyText = prototype.ctx.text
    private val parentSpellings = prototype.parentID.toList()

    internal fun requireCurrentDeclaration() {
        if (prototype.namespace != originalNamespace ||
            prototype.identifier != originalName ||
            prototype.readOnlyParams.map { it.identifier to it.typeIdentifier } != parameterNames ||
            (prototype.isAbstract to prototype.isFinal) != flags ||
            prototype.ctx.text != bodyText ||
            prototype.parentID.toList() != parentSpellings
        )
            throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                source,
                "Template declaration changed; discard the batch and prepare a fresh domain"
            )
    }

    internal fun publishName(index: Int): Boolean {
        if (published) return false
        model.identifier = "${originalName}_${parameterNames.joinToString("_") { it.second }}-$index"
        published = true
        return true
    }

    /** Only the explicit declaration materialization owner may enter this state. */
    internal fun beginMaterialization() {
        check(published) { "An unpublished template shell cannot materialize" }
        check(currentPhase == HeaderPhase.RESERVED || currentPhase == HeaderPhase.HEADER_READY) {
            "Template shell is already completing or complete"
        }
        currentPhase = HeaderPhase.COMPLETING
    }

    internal fun recordLocalHeader(value: PreparedTemplateHeader) {
        requireCurrentDeclaration()
        check(value.owner === this)
        header = value
        if (currentPhase == HeaderPhase.RESERVED) currentPhase = HeaderPhase.HEADER_READY
    }

    // COMPLETE is derived from the materialization state and the real declaration barrier proof.
    // Checkpoints retain the local state; the deep declaration journal retains the model's proof.
    private class RawHeaderState(val phase: HeaderPhase, val published: Boolean, val header: PreparedTemplateHeader?)
    private fun rawHeaderState() = RawHeaderState(currentPhase, published, header)

    /** Raw slots only: observing these facts must never consult the model's completion proof. */
    internal fun rawPreparationFacts(): RawPreparationFact {
        val state = rawHeaderState()
        return RawPreparationFact.row(
            RawPreparationFact.scalar(state.phase.name),
            RawPreparationFact.scalar(state.published),
            RawPreparationFact.identity(state.header)
        )
    }

    internal fun checkpoint(): () -> Unit {
        val state = rawHeaderState()
        val name = model.identifier
        return {
            currentPhase = state.phase
            published = state.published
            model.identifier = name
            header = state.header
        }
    }

    /** Private checkpoint publishes actual deep roots, rather than this record's implementation. */
    internal fun checkpointElements(): List<Any> = buildList {
        add(prototype)
        add(model)
        add(type)
        add(identity)
        readonlyFacts.forEach { fact ->
            add(fact.type)
            add(fact.value)
            addAll(fact.bound.descriptors.values)
        }
        header?.let { record ->
            record.parents.forEach {
                add(it.type)
                add(it.syntax)
            }
            record.fields.forEach { field ->
                add(field.declaration)
                add(field.bound.type)
                field.bound.constant?.let(::add)
                addAll(field.bound.descriptors.values)
                addAll(field.annotations)
            }
            record.visibleFields.forEach { field ->
                add(field.origin)
                add(field.bound.type)
                field.bound.constant?.let(::add)
                addAll(field.bound.descriptors.values)
            }
            record.members.forEach { member ->
                add(member.declaration)
                addAll(member.annotations)
                (member.readonly + member.normal).forEach { formal ->
                    add(formal.declaration)
                    formal.type.type?.let(::add)
                    formal.defaultValue?.let(::add)
                    addAll(formal.defaultTypes.values)
                }
                member.result.type?.let(::add)
            }
        }
    }

    companion object {
        internal fun requireSupported(prototype: GenericDataTemplate, source: SourceSpan) {
            if (prototype.javaClass != GenericDataTemplate::class.java && prototype.javaClass != GenericObjectDataTemplate::class.java)
                throw PreparationFactFailure(
                    PreparationFactCode.UNPREPARED_TYPE,
                    source,
                    "Pure header reservation does not support template shape ${prototype.javaClass.name}"
                )
        }

        internal fun create(
            prototype: GenericDataTemplate,
            key: SpecializationKey,
            facts: List<PreparedExpressionFact>,
            source: SourceSpan,
            existing: CompiledGenericDataTemplate? = null
        ): PreparedTemplateReservation {
            requireSupported(prototype, source)
            val values = (key.arguments as List<*>).map {
                (it as? SpecializationArgument.Constant)?.value
                    ?: throw PreparationFactFailure(
                        PreparationFactCode.NON_CLOSED_VALUE,
                        source,
                        "Template header keys require complete readonly values"
                    )
            }
            check(key.declaration == prototype.declarationId && values == facts.map { it.value }) {
                "Template header key does not match its declaration and readonly facts"
            }
            val identity = TypeId.Specialized(
                TypeId.Declaration(
                    when {
                        prototype is GenericObjectDataTemplate -> "object"
                        else -> "template"
                    },
                    prototype.namespace,
                    prototype.identifier
                ),
                values
            )
            // Exact compiler constructors only; no virtual createCompiledTemplate/initialize/getType.
            val shell = existing ?: if (prototype is GenericObjectDataTemplate)
                CompiledGenericObjectDataTemplate("<prepared>", prototype.namespace, prototype, emptyList(), values)
            else CompiledGenericDataTemplate("<prepared>", prototype.namespace, prototype, emptyList(), values)
            if (existing != null) {
                check(
                    existing.javaClass == CompiledGenericDataTemplate::class.java ||
                        existing.javaClass == CompiledGenericObjectDataTemplate::class.java
                ) { "Unknown cached template shape" }
                check(existing.originTemplate === prototype && existing.inheritanceIdentity == identity) { "Cached template identity does not match the typed header key" }
            } else {
                shell.declarationFile = prototype.declarationFile
                shell.declarationEnvironment = prototype.declarationEnvironment
                shell.isAbstract = prototype.isAbstract
                shell.isFinal = prototype.isFinal
            }
            val type = if (prototype is GenericObjectDataTemplate)
                MCFPPGenericObjectDataTemplateType(shell, arrayListOf(), identity)
            else MCFPPGenericDataTemplateType(shell, arrayListOf(), identity)
            return PreparedTemplateReservation(
                prototype, key, identity, shell, facts, source, type,
                if (existing == null) HeaderPhase.RESERVED else HeaderPhase.COMPLETING, existing != null
            )
        }
    }
}

private fun <T> headerList(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

/** Own method formals stay templates until that method receives its own readonly arguments. */
internal class PreparedHeaderType(
    val syntax: P.TypeContext?,
    val type: MCFPPType?,
    dependencies: Set<String> = emptySet()
) {
    val dependencies: Set<String> = Collections.unmodifiableSet(LinkedHashSet(dependencies))
    init {
        require(type != null || dependencies.isNotEmpty())
    }
}

internal class PreparedHeaderParameter(
    val declaration: ParserRuleContext,
    val name: String,
    val readonly: Boolean,
    val type: PreparedHeaderType,
    val defaultSyntax: P.ValueContext?,
    val defaultValue: CompilerValue?,
    defaultTypes: Map<TypeId, MCFPPType>,
    defaultDependencies: Set<String>,
    val source: SourceSpan
) {
    val defaultTypes: Map<TypeId, MCFPPType> = Collections.unmodifiableMap(LinkedHashMap(defaultTypes))
    val defaultDependencies: Set<String> = Collections.unmodifiableSet(LinkedHashSet(defaultDependencies))
    val defaultNeedsBinding: Boolean get() = defaultSyntax != null && (type.type == null || defaultDependencies.isNotEmpty())
}

internal enum class PreparedHeaderProperty { DEFAULT, GET, SET, GET_SET }
internal enum class PreparedHeaderMemberKind { FUNCTION, CONSTRUCTOR, OPERATOR, NATIVE_OPERATOR }

internal class PreparedFieldHeader(
    val declaration: P.TemplateFieldDeclarationContext,
    val bound: DeclarationBinding.Bound,
    val nullable: Boolean,
    val isConst: Boolean,
    val isStatic: Boolean,
    val access: AccessModifier,
    val property: PreparedHeaderProperty,
    annotations: List<P.AnnotationContext>,
    val source: SourceSpan
) {
    val name: String = declaration.Identifier().text
    val initializer: P.ExpressionContext? = declaration.expression()
    val annotations = headerList(annotations)
    val type: MCFPPType get() = bound.type
}

internal class PreparedParentHeader(val syntax: ParserRuleContext, val type: MCFPPType, val source: SourceSpan)

internal class PreparedCallableHeader(
    val declaration: ParserRuleContext,
    val name: String,
    val kind: PreparedHeaderMemberKind,
    val access: AccessModifier,
    val isAbstract: Boolean,
    val isOverride: Boolean,
    val isStatic: Boolean,
    readonly: List<PreparedHeaderParameter>,
    normal: List<PreparedHeaderParameter>,
    val result: PreparedHeaderType,
    annotations: List<P.AnnotationContext>,
    val source: SourceSpan
) {
    val readonly = headerList(readonly)
    val normal = headerList(normal)
    val annotations = headerList(annotations)
}

/** A canonical slot fact; local syntax is retained without constructing a Var or Property. */
internal class PreparedVisibleField(
    val name: String,
    val origin: TypeId,
    val bound: DeclarationBinding.Bound,
    val contract: CompoundDataScope.FieldContract,
    val property: PreparedHeaderProperty?,
    val declaration: P.TemplateFieldDeclarationContext?,
    val source: SourceSpan
)

/** Complete field facts remain preparation records, never a production Ready or layout proof. */
internal class PreparedTemplateHeader(
    val owner: PreparedTemplateReservation,
    parents: List<PreparedParentHeader>,
    fields: List<PreparedFieldHeader>,
    members: List<PreparedCallableHeader>,
    val implicitBaseParent: Boolean,
    visibleFields: List<PreparedVisibleField>? = null
) {
    val parents = headerList(parents)
    val fields = headerList(fields)
    val members = headerList(members)
    val visibleFields = headerList(visibleFields.orEmpty())
    val hasCompleteInheritedView = visibleFields != null

    // Store immutable identities now, rather than comparing mutable compiler descriptors later.
    // This also verifies the facts actually consumed from the original lexical domain on reuse.
    private val facts: List<Any?> = buildList {
        fun descriptors(values: Map<TypeId, MCFPPType>) = values.mapValues { it.value.typeId }
        fun type(value: PreparedHeaderType) = listOf(value.syntax, value.type?.typeId, value.dependencies)
        fun formal(value: PreparedHeaderParameter) = listOf(
            value.declaration, value.name, value.readonly,
            type(value.type), value.defaultSyntax, value.defaultValue,
            descriptors(value.defaultTypes), value.defaultDependencies, value.source
        )
        add(implicitBaseParent)
        add(parents.map { listOf(it.syntax, it.type.typeId, it.source) })
        add(
            fields.map {
                listOf(
                    it.declaration, it.bound.type.typeId, it.bound.constant,
                    it.bound.dependencies, it.bound.declaredTypeText, descriptors(it.bound.descriptors),
                    it.nullable, it.isConst, it.isStatic, it.access, it.property, it.annotations, it.source
                )
            }
        )
        add(
            members.map {
                listOf(
                    it.declaration, it.name, it.kind, it.access, it.isAbstract,
                    it.isOverride, it.isStatic, it.readonly.map(::formal), it.normal.map(::formal),
                    type(it.result), it.annotations, it.source
                )
            }
        )
        add(hasCompleteInheritedView)
        add(
            this@PreparedTemplateHeader.visibleFields.map {
                listOf(
                    it.name, it.origin, it.bound.type.typeId,
                    it.bound.constant, descriptors(it.bound.descriptors), it.contract, it.property, it.declaration, it.source
                )
            }
        )
    }

    internal fun hasSameFacts(other: PreparedTemplateHeader): Boolean = owner === other.owner && facts == other.facts
}

internal data class HeaderReadLimits(
    val maxNodes: Int = 32768,
    val maxSpecializations: Int = 1024,
    val maxDepth: Int = 128,
    val maxRounds: Int = 128
) {
    init {
        require(maxNodes > 0 && maxSpecializations > 0 && maxDepth > 0 && maxRounds > 0)
    }
}

internal sealed interface HeaderReadResult {
    class LocalHeaders(headers: List<PreparedTemplateHeader>, val staged: PreparedHeaderBatch) : HeaderReadResult {
        val headers = headerList(headers)
    }
    class NeedsCompletion(requests: List<DeclarationCompletionRequest>, val staged: PreparedHeaderBatch) : HeaderReadResult {
        val requests = headerList(requests)
    }
    class Rejected(diagnostics: List<PreparationDiagnostic>) : HeaderReadResult {
        val diagnostics = headerList(diagnostics)
    }
}

/** Reads compiler syntax and pure facts; it never invokes a field/body visitor or creates adapters. */
internal class PreparedTemplateHeaderReader(
    private val session: SignaturePreparationSession,
    private val lexical: DeclarationLexicalSnapshot,
    private val limits: HeaderReadLimits
) {
    private class Entry(
        val owner: PreparedTemplateReservation,
        val lexical: DeclarationLexicalSnapshot,
        val path: List<String>
    )
    private class Completion(val requests: List<DeclarationCompletionRequest>) : RuntimeException()
    private var nodes = 0
    private val domains = arrayListOf(lexical)
    private val entries = linkedMapOf<SpecializationKey, Entry>()
    private val queue = ArrayDeque<Entry>()
    private val completed = arrayListOf<PreparedTemplateHeader>()
    private lateinit var active: Entry

    fun read(batch: PreparedHeaderBatch): HeaderReadResult {
        val origin = batch.headers.firstOrNull()?.source ?: SourceSpan.unknown()
        var restore: () -> Unit = {}
        try {
            restore = session.checkpointHeaderReader(batch, origin)
            if (lexical.headerSession !== session) fail(origin, "Header queue belongs to a foreign lexical domain")
            for (header in batch.headers) enqueue(header, null)
            while (queue.isNotEmpty()) {
                active = queue.removeFirst()
                session.requireHeaderReader(active.owner, active.owner.source)
                val record = readLocal(active)
                completed.add(record)
                discover()
            }
            val inherited = inheritedHeaders().map { session.recordReaderHeader(it.owner, it) }
            val updated = if (batch.headers.isEmpty()) batch else session.refreshHeaderReaderBatch(batch.headers.first())
            return HeaderReadResult.LocalHeaders(inherited, updated)
        } catch (completion: Completion) {
            // These are explicit declaration requests, not proof that an unfinished header is ready.
            val owner = batch.headers.firstOrNull()
            val updated = if (owner == null) batch else session.refreshHeaderReaderBatch(owner, owner.source, completion.requests)
            return HeaderReadResult.NeedsCompletion(completion.requests, updated)
        } catch (failure: PreparationFactFailure) {
            restore()
            return HeaderReadResult.Rejected(listOf(PreparationDiagnostic(failure.code, failure.source, failure.message.orEmpty())))
        }
    }

    private fun label(owner: PreparedTemplateReservation) = "${owner.prototype.namespace}:${owner.prototype.identifier}${owner.identity.arguments}"

    private fun fail(source: SourceSpan, message: String, code: PreparationFactCode = PreparationFactCode.UNPREPARED_TYPE): Nothing {
        val path = if (::active.isInitialized) active.path.joinToString(" -> ") else entries.values.firstOrNull()?.path?.joinToString(" -> ").orEmpty()
        throw PreparationFactFailure(code, source, "$message; header path: $path")
    }

    private fun enqueue(owner: PreparedTemplateReservation, parent: Entry?) {
        if (owner.key in entries) return
        val path = parent?.path.orEmpty() + label(owner)
        if (entries.size >= limits.maxSpecializations) fail(owner.source, "Header specialization budget exceeded: ${path.joinToString(" -> ")}")
        if (path.size > limits.maxDepth) fail(owner.source, "Header depth budget exceeded: ${path.joinToString(" -> ")}")
        if (path.size > limits.maxRounds) fail(owner.source, "Header queue round budget exceeded: ${path.joinToString(" -> ")}")
        val domain = domains.firstOrNull { it.template(owner.prototype, owner.prototype.ctx) != null }
            ?: fail(owner.source, "No original declaration domain for ${label(owner)}")
        Entry(owner, domain, headerList(path)).also {
            entries[owner.key] = it
            queue.addLast(it)
        }
    }

    private fun discover() {
        for (header in session.readerReservations(active.owner)) enqueue(header, active)
    }

    private class InheritanceNode(
        val identity: TypeId,
        val parents: List<TypeId>,
        val fields: List<PreparedVisibleField>,
        val source: SourceSpan,
        val completedLayout: Boolean = false
    )
    private class CompletedParent(val model: CompoundData, val source: SourceSpan, val entry: Entry)

    /** Finish the whole graph before installing records: reference cycles are not inheritance cycles. */
    private fun inheritedHeaders(): List<PreparedTemplateHeader> {
        val records = completed.associateBy { it.owner.identity }
        val graph = linkedMapOf<TypeId, InheritanceNode>()
        val models = linkedMapOf<TypeId, CompoundData>()
        completed.forEach { models[it.owner.identity] = it.owner.model }
        val actual = ArrayDeque<CompletedParent>()
        fun supported(model: CompoundData, source: SourceSpan) {
            // The production default chain ends at this exact compiler-owned object declaration.
            // A same-named CompoundData or an arbitrary subclass is not a builtin capability.
            if (model.javaClass == CompoundData::class.java && model === MCFPPBaseType.Object.instanceData) return
            if (model.javaClass !in setOf<Class<*>>(
                    DataTemplate::class.java,
                    ObjectDataTemplate::class.java,
                    CompiledGenericDataTemplate::class.java,
                    CompiledGenericObjectDataTemplate::class.java
                )
            )
                fail(source, "Unsupported inherited declaration shape ${model.javaClass.name}")
        }
        fun parent(type: MCFPPType, source: SourceSpan): TypeId {
            if (type.javaClass !in setOf<Class<*>>(
                    MCFPPDataTemplateType::class.java,
                    MCFPPObjectDataTemplateType::class.java,
                    MCFPPGenericDataTemplateType::class.java,
                    MCFPPGenericObjectDataTemplateType::class.java
                )
            )
                fail(source, "An inherited header parent must be a template")
            val model = (type as MCFPPDataTemplateType).checkpointPrivateState().elements.single() as? DataTemplate
                ?: fail(source, "Inherited parent has no canonical template")
            supported(model, source)
            if (model.isFinal) fail(source, "Cannot extend final template ${model.inheritanceIdentity}")
            val id = model.inheritanceIdentity
            val reserved = records[id]
            if (reserved != null) {
                if (reserved.owner.model !== model) fail(source, "Inherited parent is not the canonical reserved model $id")
            } else actual.addLast(CompletedParent(model, source, active))
            return id
        }
        for (record in completed) {
            active = entries.getValue(record.owner.key)
            graph[record.owner.identity] = InheritanceNode(
                record.owner.identity,
                record.parents.map { parent(it.type, it.source) },
                record.fields.map { field ->
                    PreparedVisibleField(
                        field.name,
                        record.owner.identity,
                        field.bound,
                        CompoundDataScope.FieldContract(
                            field.type.typeId,
                            field.nullable,
                            field.isStatic,
                            field.isConst,
                            field.access,
                            false
                        ),
                        field.property,
                        field.declaration,
                        field.source
                    )
                },
                record.owner.source
            )
        }
        val knownFields = setOf(
            top.mcfpp.core.lang.MCInt::class.java, top.mcfpp.core.lang.MCFloat::class.java,
            top.mcfpp.core.lang.bool.ScoreBool::class.java, top.mcfpp.core.lang.nbt.MCString::class.java,
            top.mcfpp.core.lang.MCFPPTypeVar::class.java, top.mcfpp.core.lang.nbt.NBTList::class.java,
            top.mcfpp.core.lang.nbt.NBTDictionary::class.java, top.mcfpp.core.lang.nbt.NBTMap::class.java,
            top.mcfpp.core.lang.obj.DataTemplateObject::class.java,
            top.mcfpp.core.lang.MCAny::class.java, top.mcfpp.core.lang.MCObject::class.java,
            top.mcfpp.core.lang.obj.EnumVar::class.java, top.mcfpp.core.lang.UnionTypeVar::class.java,
            top.mcfpp.core.lang.ImmutableList::class.java, top.mcfpp.core.lang.nbt.NBTBasedData::class.java,
            top.mcfpp.core.lang.nbt.MCByte::class.java, top.mcfpp.core.lang.nbt.MCShort::class.java,
            top.mcfpp.core.lang.nbt.MCLong::class.java, top.mcfpp.core.lang.nbt.MCDouble::class.java,
            top.mcfpp.core.lang.nbt.NBTByteArray::class.java, top.mcfpp.core.lang.nbt.NBTIntArray::class.java,
            top.mcfpp.core.lang.nbt.NBTLongArray::class.java
        )
        while (actual.isNotEmpty()) {
            val item = actual.removeFirst()
            val model = item.model
            val source = item.source
            active = item.entry
            supported(model, source)
            val id = model.inheritanceIdentity
            if (id in graph) {
                if (models[id] !== model) fail(source, "Inherited declaration $id has conflicting canonical models")
                continue
            }
            if (graph.size >= limits.maxNodes) fail(source, "Inherited declaration node budget exceeded")
            models[id] = model
            if (model is DataTemplate &&
                model !== DataTemplate.baseDataTemplate &&
                (!model.hasCompletedDeclaration || model.hasPendingParentTypes)
            )
                fail(source, "Inherited declaration $id requires completed layout facts")
            val parents = model.parent.map { ancestor ->
                supported(ancestor, source)
                actual.addLast(CompletedParent(ancestor, source, active))
                ancestor.inheritanceIdentity
            }
            val readonlyNames = (model as? CompiledGenericDataTemplate)?.originTemplate?.readOnlyParams?.map { it.identifier }.orEmpty()
            val binder = DeclarationBinding(context(active))
            val fields = model.scope.allVars.filter { it.identifier !in readonlyNames }.map { field ->
                if (++nodes > limits.maxNodes) fail(source, "Inherited field node budget exceeded")
                if (field.javaClass !in knownFields) fail(source, "Unsupported completed parent field shape ${field.javaClass.name}")
                val property = model.scope.getProperty(field.identifier)
                val origin = model.scope.fieldOrigins[field.identifier]?.singleOrNull()?.owner
                    ?: field.declaredParentTemplate ?: model
                val declaration = origin
                supported(declaration, source)
                val type = binder.preparedDeclaredType(field.type, source)
                val raw = DeclarationLexicalSnapshot.rawValue(field)
                if (raw != null && binder.preparedDeclaredType(raw.type, source).typeId != type.typeId)
                    fail(source, "Inherited field proof has a different declared type")
                val descriptors = linkedMapOf<TypeId, MCFPPType>()
                raw?.descriptors?.forEach { (id, descriptor) ->
                    val frozen = binder.preparedDeclaredType(descriptor, source)
                    if (frozen.typeId != id) fail(source, "Inherited field descriptor identity does not match its key")
                    descriptors[id] = frozen
                }
                var bound = (raw ?: DeclarationBinding.Bound(type)).copy(
                    type = type,
                    headerReceiver = null,
                    superOwner = null,
                    dependencies = Collections.unmodifiableSet(LinkedHashSet(raw?.dependencies.orEmpty())),
                    descriptors = Collections.unmodifiableMap(LinkedHashMap(descriptors))
                )
                if (bound.constant != null) {
                    val proof = PreparedExpressionFact.fromBound(bound, source, binder).bound
                    // Keep the entire storage descriptor set as well as proof-reachable types:
                    // published facts must also detect drift in a currently unused descriptor.
                    descriptors.putAll(proof.descriptors)
                    bound = proof.copy(descriptors = Collections.unmodifiableMap(LinkedHashMap(descriptors)))
                }
                val contract = CompoundDataScope.FieldContract(
                    type.typeId,
                    field.nullable,
                    field.isStatic,
                    field.isConst,
                    field.accessModifier,
                    field.isFinal || property?.isFinal == true
                )
                val kind = property?.let {
                    when {
                        it.accessor != null && it.mutator != null -> PreparedHeaderProperty.GET_SET
                        it.accessor != null -> PreparedHeaderProperty.GET
                        it.mutator != null -> PreparedHeaderProperty.SET
                        else -> PreparedHeaderProperty.DEFAULT
                    }
                }
                PreparedVisibleField(
                    field.identifier,
                    declaration.inheritanceIdentity,
                    bound,
                    contract,
                    kind,
                    null,
                    source
                )
            }
            graph[id] = InheritanceNode(id, parents, fields, source, true)
        }
        val ordered = arrayListOf<InheritanceNode>()
        val states = hashMapOf<TypeId, Int>()
        for (root in graph.values) {
            if (states[root.identity] == 2) continue
            val stack = arrayListOf(root to root.parents.iterator())
            states[root.identity] = 1
            while (stack.isNotEmpty()) {
                if (stack.size > limits.maxDepth) fail(root.source, "Inherited header depth budget exceeded")
                val (current, parents) = stack.last()
                if (!parents.hasNext()) {
                    states[current.identity] = 2
                    ordered.add(current)
                    stack.removeAt(stack.lastIndex)
                } else {
                    val id = parents.next()
                    val next = graph[id] ?: fail(current.source, "Inherited header $id has no prepared facts")
                    when (states[id]) {
                        1 -> fail(current.source, "Cyclic inheritance: " + (stack.map { it.first.identity } + id).joinToString(" -> "))
                        2 -> Unit
                        else -> {
                            states[id] = 1
                            stack.add(next to next.parents.iterator())
                        }
                    }
                }
            }
        }
        val ancestors = linkedMapOf<TypeId, Set<TypeId>>()
        val visible = linkedMapOf<TypeId, List<PreparedVisibleField>>()
        for (node in ordered) {
            ancestors[node.identity] = node.parents.flatMap { listOf(it) + ancestors.getValue(it) }.toSet()
            if (node.completedLayout) {
                visible[node.identity] = node.fields
                continue
            }
            val inherited = node.parents.flatMap { visible.getValue(it) }.groupBy { it.name }
            val locals = node.fields.associateBy { it.name }
            val result = linkedMapOf<String, PreparedVisibleField>()
            for ((name, paths) in inherited) {
                val merge = CompoundDataScope.InheritedFields.merge(
                    paths.map { field ->
                        CompoundDataScope.FieldCandidate(field.origin, ancestors[field.origin].orEmpty(), field.contract, field)
                    }
                )
                val labels = merge.origins.joinToString { it.owner.toString() }
                if (merge.conflicts.isNotEmpty()) fail(
                    node.source,
                    "Incompatible inherited field '$name' in ${node.identity} from $labels (${merge.conflicts.joinToString()})"
                )
                val local = locals[name]
                if (local != null) {
                    for (origin in merge.origins) {
                        val differences = origin.contract.replacementDifferences(local.contract)
                        if (differences.isNotEmpty()) fail(
                            local.source,
                            "Incompatible inherited field '$name': ${origin.owner} -> ${node.identity} (${differences.joinToString()})"
                        )
                    }
                    result[name] = local
                } else {
                    if (merge.effective.size != 1) fail(
                        node.source,
                        "Ambiguous inherited field '$name' in ${node.identity} from $labels; declare a compatible local field"
                    )
                    result[name] = merge.effective.single().value
                }
            }
            node.fields.forEach { result[it.name] = it }
            visible[node.identity] = headerList(result.values)
        }
        return completed.map { record ->
            PreparedTemplateHeader(
                record.owner,
                record.parents,
                record.fields,
                record.members,
                record.implicitBaseParent,
                visible.getValue(record.owner.identity)
            )
        }
    }

    /** Iterative postorder preserves source sibling order and puts nested calls before their users. */
    private fun syntax(root: ParseTree): List<ParseTree> {
        val result = arrayListOf<ParseTree>()
        val pending = ArrayDeque<Pair<ParseTree, Boolean>>()
        pending.addLast(root to false)
        while (pending.isNotEmpty()) {
            val (node, visited) = pending.removeLast()
            if (visited) {
                result.add(node)
                continue
            }
            if (++nodes > limits.maxNodes) fail((node as? ParserRuleContext)?.let(::preparationSpan) ?: preparationSpan(root as ParserRuleContext), "Header syntax node budget exceeded")
            pending.addLast(node to true)
            for (index in node.childCount - 1 downTo 0) pending.addLast(node.getChild(index) to false)
        }
        return result
    }

    private fun access(declaration: P.TemplateMemberDeclarationContext): AccessModifier = when {
        declaration.accessModifier()?.PRIVATE() != null -> AccessModifier.PRIVATE
        declaration.accessModifier()?.PROTECTED() != null -> AccessModifier.PROTECTED
        else -> AccessModifier.PUBLIC
    }

    private fun context(entry: Entry, fields: Collection<PreparedFieldHeader> = emptyList()): DeclarationPreparationContext = DeclarationPreparationContext.forHeader(
        entry.lexical,
        entry.owner,
        fields.map { PreparedOwnedHeaderField(entry.owner, it.declaration, it.bound) }
    )

    private fun expression(entry: Entry, fields: Collection<PreparedFieldHeader>, expression: P.ExpressionContext,
                           expected: MCFPPType? = null): DeclarationBinding.Bound {
        val context = context(entry, fields)
        val calls = java.util.IdentityHashMap<P.FunctionCallContext, top.mcfpp.model.function.ParameterMatcher.PreparedCallFacts>()
        for (call in syntax(expression).filterIsInstance<P.FunctionCallContext>()) {
            val binder = DeclarationBinding(context, calls)
            val readonly = call.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty().map {
                PreparedExpressionFact.fromBound(binder.readonly(it), preparationSpan(it), binder)
            }
            val (namespace, name) = call.namespaceID().text.splitNamespaceID()
            val request = SignaturePreparationRequest(context.lexical.functions(namespace, name), readonly, preparationSpan(call))
            when (val prepared = session.prepareReaderCall(entry.owner, entry.lexical, request)) {
                is HeaderCallPreparationResult.Prepared -> {
                    prepared.facts.declarations.signatures.forEach { domains.add(it.lexical) }
                    calls[call] = prepared.facts
                }
                is HeaderCallPreparationResult.NeedsCompletion -> throw Completion(prepared.requests)
                is HeaderCallPreparationResult.Rejected -> {
                    val first = prepared.diagnostics.first()
                    fail(first.source, first.message, first.code)
                }
            }
        }
        return DeclarationBinding(context, calls).expression(expression, expected)
    }

    private fun fieldType(binder: DeclarationBinding, syntax: P.TemplateTypeContext): Pair<MCFPPType, Boolean> {
        this.syntax(syntax)
        val single = syntax.singleTemplateFieldType()
        if (single != null) return binder.type(single.type()) to (single.QUEST() != null)
        val union = syntax.unionTemplateFieldType()
        return MCFPPUnionType(*union.type().map(binder::type).toTypedArray()) to (union.QUEST() != null)
    }

    private fun readLocal(entry: Entry): PreparedTemplateHeader {
        val owner = entry.owner
        if (owner.prototype.hasPendingParentTypes) fail(owner.source, "Restored parent identities require declaration completion")
        val declarations = owner.prototype.ctx.templateMemberDeclaration()
        val fields = linkedMapOf<String, PreparedFieldHeader>()
        val pending = arrayListOf<Pair<P.TemplateMemberDeclarationContext, List<P.AnnotationContext>>>()
        val memberSources = arrayListOf<Pair<P.TemplateMemberDeclarationContext, List<P.AnnotationContext>>>()
        var annotations = arrayListOf<P.AnnotationContext>()
        for (declaration in declarations) {
            val annotation = declaration.templateMember().annotation()
            if (annotation != null) {
                annotations.add(annotation)
                continue
            }
            val saved = headerList(annotations)
            annotations = arrayListOf()
            if (declaration.templateMember().templateFieldDeclaration() != null) pending.add(declaration to saved)
            else memberSources.add(declaration to saved)
        }
        val names = pending.map { it.first.templateMember().templateFieldDeclaration().Identifier().text }
        if (names.distinct().size != names.size) fail(owner.source, "Duplicate local field declaration", PreparationFactCode.FORMAL_DEPENDENCY)
        var rounds = 0
        while (pending.isNotEmpty()) {
            if (++rounds > limits.maxRounds) fail(preparationSpan(pending.first().first), "Field inference round budget exceeded")
            var progressed = false
            var blocked: PreparationFactFailure? = null
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val (declaration, savedAnnotations) = iterator.next()
                val field = declaration.templateMember().templateFieldDeclaration()
                val source = preparationSpan(field)
                try {
                    val binder = DeclarationBinding(context(entry, fields.values))
                    val declared = field.templateType()?.let { fieldType(binder, it) }
                    val inferred = if (declared == null || field.CONST() != null)
                        field.expression()?.let { expression(entry, fields.values, it, declared?.first) } else null
                    val bound = if (declared != null) {
                        if (inferred != null && TypeRelations.resolveImplicitConversion(inferred.type, declared.first) == null)
                            fail(source, "Initializer does not adapt to its declared field type", PreparationFactCode.NON_CLOSED_VALUE)
                        if (inferred == null) DeclarationBinding.Bound(declared.first)
                        else if ((declared.first == MCFPPBaseType.Any || declared.first == MCFPPBaseType.Object) && inferred.constant != null)
                            inferred.copy(type = declared.first, constant = CompilerValue.Typed(declared.first.typeId, inferred.constant))
                        else binder.constrain(inferred, declared.first)
                    } else inferred ?: fail(source, "Field has neither a declared type nor an initializer", PreparationFactCode.UNKNOWN_NAME)
                    val frozenType = binder.preparedDeclaredType(bound.type, source)
                    var frozen = bound.copy(
                        type = frozenType,
                        headerReceiver = null,
                        superOwner = null,
                        constant = bound.constant.takeIf { field.CONST() != null },
                        dependencies = Collections.unmodifiableSet(LinkedHashSet(bound.dependencies)),
                        descriptors = Collections.unmodifiableMap(bound.descriptors.mapValues { (_, type) -> binder.preparedDeclaredType(type, source) })
                    )
                    if (frozen.constant != null) frozen = PreparedExpressionFact.fromBound(frozen, source, binder).bound
                    val getter = field.accessor()?.getter() != null
                    val setter = field.accessor()?.setter() != null
                    val property = when {
                        getter && setter -> PreparedHeaderProperty.GET_SET
                        getter -> PreparedHeaderProperty.GET
                        setter -> PreparedHeaderProperty.SET
                        else -> PreparedHeaderProperty.DEFAULT
                    }
                    fields[field.Identifier().text] = PreparedFieldHeader(
                        field, frozen, declared?.second == true,
                        field.CONST() != null, owner.prototype is GenericObjectDataTemplate,
                        access(declaration), property, savedAnnotations, source
                    )
                    iterator.remove()
                    progressed = true
                } catch (failure: PreparationFactFailure) {
                    if (failure.code != PreparationFactCode.UNPREPARED_TYPE) throw failure
                    blocked = failure
                }
            }
            if (!progressed) {
                val failure = checkNotNull(blocked)
                fail(failure.source, "Field inference made no progress (${pending.joinToString { it.first.templateMember().templateFieldDeclaration().Identifier().text }}): ${failure.message}")
            }
        }
        val binder = DeclarationBinding(context(entry, fields.values))
        val parentSyntax = generateSequence(owner.prototype.ctx.parent) { it.parent }.firstNotNullOfOrNull {
            when (it) {
                is P.TemplateDeclarationContext -> it.compoundDeclaration()
                is P.ObjectTemplateDeclarationContext -> it.compoundDeclaration()
                else -> null
            }
        }?.extendName().orEmpty()
        val parents = if (parentSyntax.isNotEmpty()) parentSyntax.map { parent ->
            syntax(parent)
            PreparedParentHeader(parent, binder.preparedDeclaredType(UnresolvedType(parent.text), preparationSpan(parent)), preparationSpan(parent))
        } else owner.prototype.parentID.map { spelling ->
            PreparedParentHeader(owner.prototype.ctx, binder.preparedDeclaredType(UnresolvedType(spelling), owner.source), owner.source)
        }
        val members = memberSources.map { (declaration, saved) -> member(entry, declaration, saved) }
        val ordered = names.map(fields::getValue)
        return PreparedTemplateHeader(owner, parents, ordered, members, parents.isEmpty())
    }

    private fun member(
        entry: Entry,
        declaration: P.TemplateMemberDeclarationContext,
        annotations: List<P.AnnotationContext>
    ): PreparedCallableHeader {
        val member = declaration.templateMember()
        val function = member.templateFunctionDeclaration()
        val constructor = member.templateConstructorDeclaration()
        val operation = member.operationOverrideDeclaration()
        val nativeOperation = member.nativeOperationOverrideDeclaration()
        val part = function?.functionDeclarationPart()
        val params = part?.functionParams() ?: operation?.functionParams() ?: nativeOperation?.functionParams()
        val readonly = params?.readOnlyParams()?.readOnlyParameterList()?.readOnlyParameter().orEmpty()
        val normal = (params?.normalParams() ?: constructor?.normalParams())?.parameterList()?.parameter().orEmpty()
        val names = (
            readonly.map { parameter -> parameter.Identifier().text } +
                normal.map { parameter -> parameter.Identifier().text }
            )
        if (names.distinct().size != names.size) fail(preparationSpan(declaration), "Duplicate member formal name", PreparationFactCode.FORMAL_DEPENDENCY)
        val ownNames = names.take(readonly.size).toSet()
        val normalNames = names.drop(readonly.size).toSet()
        // Keep the already frozen original environment. A member's own unbound formals
        // remain syntax patterns; recapturing its file here would admit later import changes.
        val binder = DeclarationBinding(context(entry))
        fun dependencies(root: ParserRuleContext): Set<String> {
            val references = linkedSetOf<String>()
            for (node in syntax(root)) {
                when (node) {
                    is P.TypeBodyContext -> {
                        val name = node.className()?.takeIf { it.namespacePath() == null }?.classWithoutNamespace()?.text
                            ?: node.Identifier()?.text
                        if (name != null) references.add(name)
                    }
                    is P.VarWithSuffixContext -> node.Identifier()?.text?.let(references::add)
                }
            }
            if (references.any { it in normalNames }) fail(
                preparationSpan(root),
                "Member type/default depends on an unavailable runtime formal",
                PreparationFactCode.FORMAL_DEPENDENCY
            )
            return references.intersect(ownNames)
        }
        fun type(syntax: P.TypeContext): PreparedHeaderType {
            val pending = dependencies(syntax)
            return if (pending.isEmpty()) PreparedHeaderType(syntax, binder.type(syntax))
            else PreparedHeaderType(syntax, null, pending)
        }
        fun formal(parameter: ParserRuleContext, name: String, declaredType: P.TypeContext?,
                   default: P.ValueContext?, isReadonly: Boolean): PreparedHeaderParameter {
            val target = declaredType?.let(::type) ?: PreparedHeaderType(null, MCFPPConcreteType.Type)
            val pending = default?.let(::dependencies).orEmpty()
            val fact = if (default != null && pending.isEmpty()) {
                val bound = binder.readonlyValue(default)
                if (target.type != null && TypeRelations.resolveImplicitConversion(bound.type, target.type) == null)
                    fail(preparationSpan(default), "Member default does not adapt to its declared type", PreparationFactCode.NON_CLOSED_VALUE)
                val converted = when (target.type) {
                    null -> bound
                    MCFPPBaseType.Any, MCFPPBaseType.Object -> bound.copy(
                        type = target.type,
                        constant = CompilerValue.Typed(target.type.typeId, checkNotNull(bound.constant))
                    )
                    else -> binder.constrain(bound, target.type)
                }
                PreparedExpressionFact.fromBound(converted, preparationSpan(default), binder)
            } else null
            return PreparedHeaderParameter(
                parameter, name, isReadonly,
                target, default, fact?.value, fact?.bound?.descriptors.orEmpty(), pending, preparationSpan(parameter)
            )
        }
        val returnSyntax = part?.functionReturnType() ?: operation?.functionReturnType() ?: nativeOperation?.functionReturnType()
        val result = returnSyntax?.type()?.let(::type) ?: PreparedHeaderType(null, MCFPPPrivateType.Void)
        val kind = when {
            function != null -> PreparedHeaderMemberKind.FUNCTION
            constructor != null -> PreparedHeaderMemberKind.CONSTRUCTOR
            nativeOperation != null -> PreparedHeaderMemberKind.NATIVE_OPERATOR
            operation != null -> PreparedHeaderMemberKind.OPERATOR
            else -> fail(preparationSpan(declaration), "Unsupported header member", PreparationFactCode.UNSUPPORTED_EXPRESSION)
        }
        val name = part?.Identifier()?.text ?: operation?.supportOperator()?.text ?: nativeOperation?.supportOperator()?.text ?: "<constructor>"
        return PreparedCallableHeader(
            declaration, name, kind, access(declaration), function?.ABSTRACT() != null,
            function?.OVERRIDE() != null, entry.owner.prototype is GenericObjectDataTemplate,
            readonly.map { formal(it, it.Identifier().text, it.type(), it.value(), true) },
            normal.map { formal(it, it.Identifier().text, it.type(), it.value(), false) }, result,
            annotations, preparationSpan(declaration)
        )
    }
}
