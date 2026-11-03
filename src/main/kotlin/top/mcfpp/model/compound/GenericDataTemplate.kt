package top.mcfpp.model.compound

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.PreparedExpressionFact
import top.mcfpp.analysis.PreparedTemplateReservation
import top.mcfpp.analysis.SpecializationArgument
import top.mcfpp.analysis.SpecializationKey
import top.mcfpp.analysis.SpecializationKeys
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.SymbolId
import top.mcfpp.antlr.MCFPPGenericDataTemplateFieldVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPGenericDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitNamespaceID
import java.util.Collections
import java.util.IdentityHashMap

/** Author-session directory for a restored generic declaration; no operation consumes display source. */
interface GenericTemplateSession {
    fun requireOpen()
    fun readonlyType(prototype: GenericDataTemplate, index: Int): MCFPPType
    fun readonlyType(prototype: GenericDataTemplate, index: Int, converted: List<Var<*>>): MCFPPType = readonlyType(prototype, index)
    /** Resolve published D/B/C defaults in the author session before ordinary readonly conversion. */
    fun completeReadonlyArguments(prototype: GenericDataTemplate, supplied: List<Var<*>>): List<Var<*>> = supplied
    fun validateHeader(prototype: GenericDataTemplate)
    /** A null target requests nominal parent heads; an instance has converted readonly values installed. */
    fun parentTypes(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate?): List<MCFPPDataTemplateType>
    fun project(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate): top.mcfpp.model.function.ProjectedSyntax
    fun cloneAnnotations(recorded: List<Annotation>): top.mcfpp.model.function.TemplateAnnotationClone
    fun cloneAnnotations(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate,
                         recorded: List<Annotation>): top.mcfpp.model.function.TemplateAnnotationClone = cloneAnnotations(recorded)
    fun validateInferredTypes(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate?, types: Map<String, MCFPPType>) {}
    /** Restore member D/B/default/annotation facts on the actual models created from this fresh template. */
    fun bindMembers(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate, projection: top.mcfpp.model.function.ProjectedSyntax)
    val materializesOperators: Boolean get() = false
    /** Restore actual D7/native operator models after normal members exist, without visiting source syntax. */
    fun bindOperators(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate, projection: top.mcfpp.model.function.ProjectedSyntax) {
        error("LIB_BODY_UNSUPPORTED: Generic template session has no operator materializer")
    }
}

/**
 * 结构体是一种和类的语法极为相似的数据结构。在结构体中，只能有int类型的数据，或者说记分板的数据作为结构体的成员。
 *
 * 结构体通过记分板的命名来区分“内存”区域。
 *
 * 例如命名空间为test下的结构体foo，有成员mem，那么mcfpp就会创建一个名字为`test_struct_foo_mem`的记分板。
 * 这个结构体的实例则会根据实例变量的名字（在Minecraft中的标识符）来记分板上记录对应的值，例如`foo a`，在记分板上对应的值就是`前缀_a`
 *
 * 如果一个结构体的对象作为类的成员被引用，那么mcfpp会创建一个名字为`<namespace>_class_<classname>_<classMember>_struct_<structMember>`
 *的记分板，并让实体指针在相应的记分板上拥有值
 *
 * 除此之外，结构体是一种值类型的变量，而不是引用类型。因此在赋值的时候会把整个结构体进行一次赋值。
 */
open class GenericDataTemplate : DataTemplate {

    @Transient var structuredSession: GenericTemplateSession? = null

    val ctx: mcfppParser.TemplateBodyContext

    @Transient
    val declarationId = SymbolId.fresh()

    val compiledTemplates: HashMap<SpecializationKey, CompiledGenericDataTemplate> = HashMap()

    /** Models under declaration completion remain canonical without entering the COMPLETE cache. */
    @Transient private val materializations = LinkedHashMap<SpecializationKey, Materialization>()
    private class Materialization(val key: SpecializationKey, val model: CompiledGenericDataTemplate) {
        var origins: MaterializedOrigins? = null
        var context: mcfppParser.TemplateBodyContext? = null
    }
    internal class MaterializedOrigins(
        fields: Map<mcfppParser.TemplateFieldDeclarationContext, Var<*>>,
        properties: Map<mcfppParser.TemplateFieldDeclarationContext, Property>,
        functions: Map<ParserRuleContext, Function>
    ) {
        val fields: Map<mcfppParser.TemplateFieldDeclarationContext, Var<*>> = Collections.unmodifiableMap(IdentityHashMap(fields))
        val properties: Map<mcfppParser.TemplateFieldDeclarationContext, Property> = Collections.unmodifiableMap(IdentityHashMap(properties))
        val functions: Map<ParserRuleContext, Function> = Collections.unmodifiableMap(IdentityHashMap(functions))
    }
    internal fun materializedTemplate(key: SpecializationKey): CompiledGenericDataTemplate? = materializations[key]?.model
    internal fun materializedInstances(): List<CompiledGenericDataTemplate> {
        val seen = Collections.newSetFromMap(IdentityHashMap<CompiledGenericDataTemplate, Boolean>())
        return Collections.unmodifiableList((compiledTemplates.values + materializations.values.map { it.model }).filter { seen.add(it) })
    }
    /** The closed provider remains installed so a released prototype cannot fall back to source. */
    internal fun clearStructuredMaterializations() {
        check(structuredSession != null) { "Only a structured generic session owns these materializations" }
        compiledTemplates.clear()
        materializations.clear()
        preparedHeaders.clear()
    }
    internal fun materializedOrigins(key: SpecializationKey): MaterializedOrigins? = materializations[key]?.origins

    internal fun revokeCompletedMaterialization(model: CompiledGenericDataTemplate) {
        val entries = compiledTemplates.entries.filter { it.value === model }.map { it.key }
        for (key in entries) {
            materializations.putIfAbsent(key, Materialization(key, model))
            compiledTemplates.remove(key)
        }
    }

    /** Called only after the real Project barrier has assigned its revocable proof. */
    internal fun finishMaterialization(model: CompiledGenericDataTemplate) {
        for ((key, entry) in materializations) if (entry.model === model) {
            if (model.hasCompletedDeclaration && entry.origins != null) compiledTemplates[key] = model
            else if (compiledTemplates[key] === model) compiledTemplates.remove(key)
        }
    }

    /** Incomplete models are private to header-aware declaration preparation, never legacy lookup. */
    @Transient private val preparedHeaders = LinkedHashMap<SpecializationKey, PreparedTemplateReservation>()

    internal fun preparedHeader(key: SpecializationKey): PreparedTemplateReservation? = preparedHeaders[key]

    internal fun reservePreparedHeader(
        key: SpecializationKey,
        facts: List<PreparedExpressionFact>,
        source: SourceSpan
    ): PreparedTemplateReservation {
        preparedHeaders[key]?.let {
            it.requireCurrentDeclaration()
            return it
        }
        return PreparedTemplateReservation.create(this, key, facts, source, compiledTemplates[key] ?: materializations[key]?.model)
    }

    internal fun checkHeaderPublication(reservation: PreparedTemplateReservation) {
        check(reservation.prototype === this && reservation.key.declaration == declarationId) { "Template reservation belongs to another declaration" }
        reservation.requireCurrentDeclaration()
        val previous = preparedHeaders[reservation.key]
        val complete = compiledTemplates[reservation.key]
        val materializing = materializations[reservation.key]?.model
        check(previous == null || previous === reservation) { "A canonical template shell was published by another batch; prepare again" }
        check(complete == null || complete === reservation.model) { "A complete template was published by another batch; prepare again" }
        check(materializing == null || materializing === reservation.model) { "A template is materializing in another batch; prepare again" }
    }

    internal fun publishPreparedHeader(reservation: PreparedTemplateReservation) {
        checkHeaderPublication(reservation)
        if (reservation.publishName(index)) index++
        preparedHeaders[reservation.key] = reservation
    }

    private class HeaderCheckpoint(
        val elements: List<Any>,
        val restore: () -> Unit,
        val recapture: () -> HeaderCheckpoint,
        val preparation: RawPreparationFact
    )

    private class MaterializationCheckpoint(
        val elements: List<Any>,
        val restore: () -> Unit,
        val recapture: () -> MaterializationCheckpoint,
        val preparation: RawPreparationFact
    )
    private fun captureMaterializations(retained: List<Materialization> = emptyList()): MaterializationCheckpoint {
        val entries = (materializations as Map<*, *>).entries.map { (key, value) ->
            check(key is SpecializationKey && value is Materialization)
            (key.arguments as List<*>).forEach { argument ->
                check(argument is SpecializationArgument.Constant)
                checkSpecializationPayload(argument.value)
            }
            check(key.declaration == declarationId && value.key == key && value.model.originTemplate === this)
            key to value
        }
        val seen = Collections.newSetFromMap(IdentityHashMap<Materialization, Boolean>())
        val records = (retained + entries.map { it.second }).filter { seen.add(it) }
        val origins = records.map { it.origins }
        val contexts = records.map { it.context }
        val roots = buildList<Any> {
            records.forEach { entry ->
                add(entry.model)
                entry.context?.let(::add)
                entry.key.arguments.forEach { add((it as SpecializationArgument.Constant).value) }
                entry.origins?.let { origin ->
                    addAll(origin.fields.keys)
                    addAll(origin.fields.values)
                    addAll(origin.properties.values)
                    addAll(origin.functions.keys)
                    addAll(origin.functions.values)
                }
            }
        }
        val preparation = RawPreparationFact.unordered(
            entries.map { (key, value) ->
                RawPreparationFact.row(
                    preparationKey(key),
                    RawPreparationFact.identity(value.model),
                    RawPreparationFact.identity(value.origins),
                    RawPreparationFact.identity(value.context)
                )
            }
        )
        return MaterializationCheckpoint(roots, {
            records.forEachIndexed { index, record -> record.origins = origins[index] }
            records.forEachIndexed { index, record -> record.context = contexts[index] }
            materializations.clear()
            entries.forEach { (key, value) -> materializations[key] = value }
        }, { captureMaterializations(records) }, preparation)
    }

    /** Retain detached entries as well as entries now reachable through the private cache. */
    private fun captureHeaderState(retained: List<PreparedTemplateReservation> = emptyList()): HeaderCheckpoint {
        val entries = (preparedHeaders as Map<*, *>).entries.map { (key, value) ->
            check(key is SpecializationKey && value is PreparedTemplateReservation) { "Invalid prepared header cache entry" }
            check(key.declaration == declarationId && value.prototype === this) { "Invalid prepared header owner" }
            (key.arguments as List<*>).forEach { argument ->
                check(argument is SpecializationArgument.Constant) { "Prepared headers require closed readonly arguments" }
                checkSpecializationPayload(argument.value)
            }
            check(key == value.key) { "Prepared header key does not match its record" }
            key to value
        }
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<PreparedTemplateReservation, Boolean>())
        val records = (retained + entries.map { it.second }).filter { seen.add(it) }
        val states = records.map { it.checkpoint() }
        val roots = records.flatMap { it.checkpointElements() }
        val preparation = RawPreparationFact.unordered(
            entries.map { (key, value) ->
                RawPreparationFact.row(
                    preparationKey(key),
                    RawPreparationFact.identity(value),
                    RawPreparationFact.identity(value.model),
                    RawPreparationFact.identity(value.type),
                    value.rawPreparationFacts()
                )
            }
        )
        return HeaderCheckpoint(roots, {
            states.forEach { it() }
            preparedHeaders.clear()
            entries.forEach { (key, value) -> preparedHeaders[key] = value }
        }, { captureHeaderState(records) }, preparation)
    }

    val readOnlyParams: ArrayList<DataTemplateParam> = ArrayList()

    var index = 0

    @Transient private var varianceChecked = false
    @Transient private var headerPrepared = false

    internal override fun checkpointDeclarationState(): DeclarationStateCheckpoint = captureGenericDeclarationState(super.checkpointDeclarationState(), emptyList(), emptyList())

    private fun captureGenericDeclarationState(
        base: DeclarationStateCheckpoint,
        retained: List<(TemplatePrivateCapture) -> Unit>,
        previous: List<Any>,
        previousHeaders: HeaderCheckpoint? = null,
        previousMaterializations: MaterializationCheckpoint? = null
    ): DeclarationStateCheckpoint {
        val graph = TemplatePrivateCapture(previous + base.elements)
        val savedVariance = varianceChecked
        val savedHeader = headerPrepared
        val savedIndex = index
        val savedSession = structuredSession
        val headers = previousHeaders?.recapture?.invoke() ?: captureHeaderState()
        val materialized = previousMaterializations?.recapture?.invoke() ?: captureMaterializations()
        headers.elements.forEach(graph::element)
        materialized.elements.forEach(graph::element)
        graph.list(readOnlyParams, TemplatePrivateCapture.ListRole.READONLY_PARAMETERS) { g, value ->
            val parameter = value as DataTemplateParam
            g.state(parameter) { current ->
                val name = parameter.identifier
                val typeName = parameter.typeIdentifier
                val type = parameter.type
                type?.let(current::element)
                current.preparationFact(
                    "readonly.parameter",
                    RawPreparationFact.identity(parameter),
                    RawPreparationFact.scalar(name),
                    RawPreparationFact.scalar(typeName),
                    RawPreparationFact.identity(type)
                )
                current.restoreWith {
                    parameter.identifier = name
                    parameter.typeIdentifier = typeName
                    parameter.type = type
                }
            }
        }
        graph.map(compiledTemplates, TemplatePrivateCapture.MapRole.COMPILED_TEMPLATES, { value ->
            check(value is SpecializationKey)
            // Constructors defensively own these containers. Check erased payloads before map restore hashes them.
            (value.arguments as List<*>).forEach { argument ->
                when (argument) {
                    is SpecializationArgument.Constant -> checkSpecializationPayload(argument.value)
                    is SpecializationArgument.Borrow -> error("Scoped context borrows cannot specialize persistent template declarations")
                    SpecializationArgument.Unknown, SpecializationArgument.Error -> Unit
                    else -> error("Template checkpoint cannot capture specialization argument")
                }
            }
        }) { g, value -> g.element(value as CompiledGenericDataTemplate) }
        val preparationFacts = base.preparationFacts.extended(
            linkedMapOf(
                "generic.headerPrepared" to RawPreparationFact.scalar(savedHeader),
                "generic.session" to RawPreparationFact.identity(savedSession),
                "generic.varianceChecked" to RawPreparationFact.scalar(savedVariance),
                "generic.index" to RawPreparationFact.scalar(savedIndex),
                "generic.relations" to graph.preparationRelations(),
                "generic.headers" to headers.preparation,
                "generic.materializations" to materialized.preparation
            )
        )
        retained.forEach { it(graph) }
        return DeclarationStateCheckpoint(graph.elements, {
            base.restore()
            varianceChecked = savedVariance
            headerPrepared = savedHeader
            index = savedIndex
            structuredSession = savedSession
            headers.restore()
            materialized.restore()
            graph.restore()
        }, {
            captureGenericDeclarationState(base.captureCurrent(), graph.roots, graph.elements, headers, materialized)
        }, preparationFacts)
    }

    private fun preparationKey(key: SpecializationKey): RawPreparationFact = RawPreparationFact.row(
        RawPreparationFact.scalar(key.declaration.value),
        RawPreparationFact.scalar(key.options.targetVersion),
        RawPreparationFact.scalar(key.options.maxWhileInline),
        RawPreparationFact.scalar(key.options.foldIRConstants),
        RawPreparationFact.sequence(
            (key.arguments as List<*>).map { argument ->
                when (argument) {
                    is SpecializationArgument.Constant -> RawPreparationFact.closed(argument.value)
                    is SpecializationArgument.Borrow -> RawPreparationFact.closed(argument.identity)
                    SpecializationArgument.Unknown -> RawPreparationFact.scalar("unknown")
                    SpecializationArgument.Error -> RawPreparationFact.scalar("error")
                    else -> error("Template checkpoint cannot capture specialization argument")
                }
            }
        )
    )

    private fun checkSpecializationPayload(value: Any?) {
        RawPreparationFact.closed(value)
    }

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(ctx: mcfppParser.TemplateBodyContext, identifier: String, namespace: String = Project.currNamespace) : super(identifier, namespace) {
        this.ctx = ctx
    }

    open fun compile(readOnlyArgs: List<Var<*>>): CompiledGenericDataTemplate? = Project.collectingTemplateSignatures {
        val callerFunction = Function.currFunction
        val callerTemplate = currTemplate
        try {
            val file = restoreDeclarationEnvironment()
            if (file == null) compileInDeclarationEnvironment(readOnlyArgs)
            else file.withDeclarationContext { compileInDeclarationEnvironment(readOnlyArgs) }
        } finally {
            Function.currFunction = callerFunction
            currTemplate = callerTemplate
        }
    }

    internal fun prepareHeader() {
        if (!Project.hasValidTemplateInheritance(this)) return
        declarationCompletionStage { prepareHeaderInDeclarationEnvironment() }
    }

    private fun prepareHeaderInDeclarationEnvironment() {
        structuredSession?.let { session ->
            session.requireOpen()
            check(parentID.isEmpty()) { "LIB_BODY_UNSUPPORTED: Restored generic parents require structural bindings" }
            if (!varianceChecked) {
                readOnlyParams.forEachIndexed { index, parameter -> parameter.type = session.readonlyType(this, index) }
                session.validateHeader(this)
                varianceChecked = true
            }
            if (!headerPrepared) {
                headerPrepared = true
                session.parentTypes(this, null).forEach { parent ->
                    if (parent.template.isFinal) LogProcessor.error("Cannot extend final template '${parent.template.identifier}'")
                    else if (this.parent.none { it.inheritanceIdentity == parent.typeId }) extends(parent.template)
                }
                if (parent.isEmpty()) extends(DataTemplate.baseDataTemplate)
            }
            return
        }
        if (!varianceChecked) {
            for (param in readOnlyParams) if (param.type == null)
                param.type = MCFPPType.parseFromString(param.typeIdentifier, scope)
            checkTemplateVariance(this)
            varianceChecked = true
        }
        if (headerPrepared) return
        headerPrepared = true
        for (param in readOnlyParams) {
            param.type = MCFPPType.parseFromString(param.typeIdentifier, scope) ?: run {
                LogProcessor.error("Invalid readonly template parameter type: ${param.typeIdentifier}")
                MCFPPBaseType.Any
            }
        }
        for (name in parentID) {
            val (namespace, identifier) = name.substringBefore('<').splitNamespaceID()
            val parent = GlobalScope.getData(namespace, identifier)
            when {
                parent == null -> LogProcessor.error("Undefined template: $name")
                parent.isFinal -> LogProcessor.error("Cannot extends $identifier because it's final")
                else -> if (this.parent.none { it.inheritanceIdentity == parent.inheritanceIdentity }) extends(parent)
            }
        }
        if (parent.isEmpty()) extends(DataTemplate.baseDataTemplate)
    }

    protected fun bindReadonlyArguments(readOnlyArgs: List<Var<*>>): List<Var<*>>? {
        val supplied = structuredSession?.completeReadonlyArguments(this, readOnlyArgs) ?: readOnlyArgs
        if (supplied.size != readOnlyParams.size) {
            LogProcessor.error("Readonly argument count does not match template '$identifier'")
            return null
        }
        val args = ArrayList<Var<*>>()
        for (i in readOnlyParams.indices) {
            val param = readOnlyParams[i]
            val type = structuredSession?.readonlyType(this, i, args) ?: param.type
            val argument = supplied[i]
            if (type == null || !ParameterMatcher.accepts(argument, type) || !SpecializationKeys.isConstant(argument)) {
                LogProcessor.error("Readonly template argument '${param.identifier}' requires a complete value of ${param.typeIdentifier}")
                return null
            }
            val cast = argument.implicitCast(type)
            if (cast.isError) return null
            val snapshot = StorageAccess.snapshot(cast)
            if (snapshot == null) {
                LogProcessor.error("Readonly template argument '${param.identifier}' requires a complete compile-time value")
                return null
            }
            val value = StorageAccess.freezeReadonly(cast, param.identifier)
            if (value == null) {
                LogProcessor.error("Readonly template argument layout is not supported for '${param.identifier}'")
                return null
            }
            args.add(value)
        }
        return args
    }

    protected open fun createCompiledTemplate(
        identifier: String,
        args: List<Var<*>>,
        argumentValues: List<CompilerValue>
    ): CompiledGenericDataTemplate = CompiledGenericDataTemplate(identifier, namespace, this, args, argumentValues)

    /** A generic header describes its materializations; its raw scope has no completed fields. */
    internal fun recordDeclarationAnnotations(recorded: List<Annotation>) {
        annotations.addAll(recorded)
        // A declaration signature may have materialized a binding before the annotation pass.
        materializedInstances().forEach { target ->
            target.declarationCompletionStage { copyDeclarationAnnotations(target, recorded) }
        }
    }

    private fun copyDeclarationAnnotations(target: CompiledGenericDataTemplate, recorded: List<Annotation>) {
        val session = structuredSession
        if (session != null) {
            session.requireOpen()
            val copied = session.cloneAnnotations(this, target, recorded)
            target.annotations.addAll(copied.annotations)
            target.pendingDeclarationAnnotations.addAll(copied.pending)
        } else {
            val copied = Property.copyAnnotations(recorded)
            target.annotations.addAll(copied)
            target.pendingDeclarationAnnotations.addAll(copied)
        }
    }

    /** Restore only the reservation's converted facts, inside the external declaration transaction.
     * The ordinary materializer then promotes this published canonical shell and retains its source
     * origins. Calling-function readonly parameters are unrelated to this template binding.
     */
    internal fun materializePreparedHeader(reservation: PreparedTemplateReservation): CompiledGenericDataTemplate {
        check(preparedHeaders[reservation.key] === reservation && reservation.prototype === this && reservation.isPublished) {
            "Declaration completion requires its adopted canonical reservation"
        }
        reservation.requireCurrentDeclaration()
        check(
            reservation.key.options == top.mcfpp.analysis.GenerationOptions(
                Project.config.version,
                top.mcfpp.CompileSettings.maxWhileInline,
                top.mcfpp.CompileSettings.foldIRConstants
            )
        ) {
            "Generation options changed; prepare a fresh declaration binding"
        }
        check(reservation.readonlyFacts.size == readOnlyParams.size)
        val args = reservation.readonlyFacts.mapIndexed { ordinal, fact ->
            StorageAccess.restore(fact.type, fact.value, readOnlyParams[ordinal].identifier, fact.bound.descriptors)
                ?: throw top.mcfpp.analysis.PreparationFactFailure(
                    top.mcfpp.analysis.PreparationFactCode.NON_CLOSED_VALUE,
                    fact.source,
                    "Cannot restore readonly declaration argument '${readOnlyParams[ordinal].identifier}'"
                )
        }
        val model = compile(args)
        check(model === reservation.model) { "Declaration materialization changed the canonical template shell" }
        return reservation.model
    }

    private fun compileInDeclarationEnvironment(readOnlyArgs: List<Var<*>>): CompiledGenericDataTemplate? {
        structuredSession?.requireOpen()
        if (!Project.hasValidTemplateInheritance(this)) return null
        prepareHeader()
        val args = bindReadonlyArguments(readOnlyArgs) ?: return null
        val key = SpecializationKeys.forArguments(declarationId, args)
        compiledTemplates[key]?.let { cached ->
            if (cached.hasCompletedDeclaration) return cached
            revokeCompletedMaterialization(cached)
        }
        materializations[key]?.let { entry ->
            if (!entry.model.hasDeclarationCompletionFailure && !Project.isTemplateCompletionQueued(entry.model))
                Project.registerTemplateContract(entry.model)
            return entry.model
        }

        val header = preparedHeaders[key]
        val template = header?.let {
            it.requireCurrentDeclaration()
            if (!it.isPublished) publishPreparedHeader(it)
            it.beginMaterialization()
            it.model
        } ?: declarationCompletionStage {
            createCompiledTemplate(
                "${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}-$index",
                args,
                key.arguments.map { (it as SpecializationArgument.Constant).value }
            )
        }
        materializations[key] = Materialization(key, template)
        if (header == null) index++
        return template.declarationCompletionStage {
            materializeCompiledTemplate(template, args, key)
        }
    }

    private fun materializeCompiledTemplate(
        template: CompiledGenericDataTemplate,
        args: List<Var<*>>,
        key: SpecializationKey
    ): CompiledGenericDataTemplate {
        template.declarationFile = declarationFile
        template.declarationEnvironment = declarationEnvironment
        if (structuredSession != null) installReadonly(template, args)
        val projected = structuredSession?.project(this, template)
        val context = if (projected == null) ctx else projected.context as? mcfppParser.TemplateBodyContext
            ?: error("LIB_BODY_UNSUPPORTED: Generic template projection has the wrong root")
        materializations[key]?.context = context
        val unsupported = context.templateMemberDeclaration().map { it.templateMember() }.filter {
            it.operationOverrideDeclaration() != null || it.nativeOperationOverrideDeclaration() != null
        }
        if (unsupported.isNotEmpty() && (projected == null || structuredSession?.materializesOperators != true)) {
            unsupported.forEach { source ->
                Project.withCompilationContext(source) {
                    LogProcessor.error("Generic template operator header has no materialization adapter")
                }
            }
            Project.registerTemplateContract(template)
            return template
        }
        template.isAbstract = isAbstract
        template.isFinal = isFinal
        template.initialize()
        template.restoreDeclarationEnvironment()
        // Library metadata can retain the same annotation in both declared and pending lists.
        // Copy the graph together, preserving aliases, and schedule each declaration once.
        copyDeclarationAnnotations(
            template,
            annotations + pendingDeclarationAnnotations.filter { pending ->
                annotations.none { it === pending }
            }
        )
        if (structuredSession == null) installReadonly(template, args)

        if (structuredSession != null) {
            structuredSession!!.parentTypes(this, template).forEach { template.extends(it.template) }
            if (template.parent.isEmpty()) template.extends(DataTemplate.baseDataTemplate)
        } else if (parentID.isNotEmpty()) {
            template.parentID.addAll(parentID)
            Project.resolveTemplateInheritance(template)
        } else for (parent in this.parent) {
            template.extends(parent)
        }

        //注册
        currTemplate = template
        MCFPPGenericDataTemplateFieldVisitor(template).visitTemplateBody(context)
        currTemplate = template
        if (projected == null) top.mcfpp.antlr.MCFPPAnnotationVisitor().visitTemplateBody(context)
        else {
            structuredSession!!.bindMembers(this, template, projected)
            if (unsupported.isNotEmpty()) structuredSession!!.bindOperators(this, template, projected)
        }
        bindCompanion(template, args)
        Project.registerTemplateContract(template)

        return template
    }

    private fun installReadonly(template: CompiledGenericDataTemplate, args: List<Var<*>>) {
        for (i in readOnlyParams.indices) {
            StorageAccess.resolveTypeValue(args[i])?.let { type ->
                template.scope.putType(readOnlyParams[i].identifier, type)
            }
            template.scope.putVar(readOnlyParams[i].identifier, args[i], false)
            template.scope.putProperty(readOnlyParams[i].identifier, Property.buildSimpleProperty(args[i]))
        }

    }

    /** Source identities describe local declarations only; inherited slots retain their own owner. */
    internal fun bindMaterializedOrigins(template: CompiledGenericDataTemplate) {
        val entry = materializations.values.firstOrNull { it.model === template } ?: return
        if (entry.origins != null || template.hasDeclarationCompletionFailure) return
        val fields = IdentityHashMap<mcfppParser.TemplateFieldDeclarationContext, Var<*>>()
        val properties = IdentityHashMap<mcfppParser.TemplateFieldDeclarationContext, Property>()
        val functions = IdentityHashMap<ParserRuleContext, Function>()
        val offsets = hashMapOf<String, Int>()
        val context = entry.context ?: ctx
        fun ownsBody(function: Function, body: mcfppParser.CurlBlockContext?): Boolean {
            if (function.ast === body) return true
            val handle = function.bodyHandle ?: return false
            val node = body?.let(top.mcfpp.model.function.StructuredBodyContexts::origin)?.node ?: return false
            return handle.repository.body(handle).root == node
        }
        for ((ordinal, declaration) in context.templateMemberDeclaration().withIndex()) {
            val prototype = ctx.templateMemberDeclaration().getOrNull(ordinal)
            val member = declaration.templateMember()
            member.templateFieldDeclaration()?.let { source ->
                val field = template.scope.getVar(source.Identifier().text)
                val property = template.scope.getProperty(source.Identifier().text)
                if (field == null || property == null || field.declaredParentTemplate !== template) {
                    Project.withCompilationContext(source) { LogProcessor.error("Missing local field materialization: ${source.Identifier().text}") }
                } else {
                    fields[source] = field
                    properties[source] = property
                    prototype?.templateMember()?.templateFieldDeclaration()?.let {
                        fields[it] = field
                        properties[it] = property
                    }
                }
            }
            member.templateFunctionDeclaration()?.let { source ->
                val name = source.functionDeclarationPart().Identifier().text
                val index = offsets.getOrDefault(name, 0)
                offsets[name] = index + 1
                val function = template.scope.functions[name].orEmpty().filter { it.owner === template }.getOrNull(index)
                if (function == null || !ownsBody(function, source.curlBlock())) {
                    Project.withCompilationContext(source) { LogProcessor.error("Missing local function materialization: $name") }
                } else { functions[declaration] = function; prototype?.let { functions[it] = function } }
            }
            member.templateConstructorDeclaration()?.let { source ->
                val function = template.constructors.singleOrNull { ownsBody(it, source.curlBlock()) }
                if (function == null) Project.withCompilationContext(source) { LogProcessor.error("Missing constructor materialization") }
                else { functions[declaration] = function; prototype?.let { functions[it] = function } }
            }
        }
        entry.origins = MaterializedOrigins(fields, properties, functions)
    }
}

class DataTemplateParam(

    /**
     * 参数类型标识符
     */
    var typeIdentifier: String,

    /**
     * 参数的名字
     */
    var identifier: String,

    /**
     * 参数类型在首次 prepareHeader 后解析；从库恢复的参数已保存该类型。
     */
    var type: MCFPPType? = null,

    /**
     * 协变，逆变和不变
     */
    val variance: DeclarationVariance = DeclarationVariance.INVARIANT
)

enum class DeclarationVariance { INVARIANT, OUT, IN }

open class CompiledGenericDataTemplate(
    identifier: String,
    namespace: String = Project.currNamespace,
    var originTemplate: GenericDataTemplate,
    args: List<Var<*>>,
    argumentValues: List<CompilerValue>
) : DataTemplate(identifier, namespace) {
    val args: List<Var<*>>
        get() = originTemplate.readOnlyParams.map { parameter ->
            requireNotNull(scope.getVar(parameter.identifier)) { "Missing readonly binding '${parameter.identifier}'" }
        }
    protected val identity = TypeId.Specialized(
        originTemplate.nominalIdentity,
        argumentValues
    )

    internal override val inheritanceIdentity: TypeId
        get() = identity

    override val nominalIdentity: TypeId
        get() = identity

    override fun getType(): MCFPPDataTemplateType {
        val t = super.getType()
        return MCFPPGenericDataTemplateType(t.template, t.parentType, identity)
    }
}
