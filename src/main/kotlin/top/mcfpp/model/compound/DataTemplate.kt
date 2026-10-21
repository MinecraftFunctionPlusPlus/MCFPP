package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.analysis.SpecializationKey
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.DeclarationEnvironmentInfo
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.property.FunctionAccessor
import top.mcfpp.model.property.FunctionMutator
import top.mcfpp.model.property.NativeAccessor
import top.mcfpp.model.property.NativeMutator
import top.mcfpp.model.property.Property
import top.mcfpp.model.property.SimpleAccessor
import top.mcfpp.model.property.SimpleMutator
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.model.scope.FileScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.Utils.v

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
open class DataTemplate :
    FieldContainer,
    CompoundData {

    @Transient
    var declarationFile: MCFPPFile? = MCFPPFile.currFile

    @Transient
    var declarationEnvironment: DeclarationEnvironmentInfo? = null

    internal fun restoreDeclarationEnvironment(): MCFPPFile? {
        val file = declarationFile ?: declarationEnvironment?.restore()?.also { declarationFile = it }
        if (file != null) {
            scope.parent.removeAll { it is FileScope }
            scope.parent.add(file.field)
        }
        return file
    }

    /** Ordered library identities, resolved only after the complete include/import domain is available. */
    @Transient private val pendingParentTypes = arrayListOf<TypeId>()
    @Transient private var resolvingParentTypes = false
    @Transient internal var hasRestoredParentTypes = false
        private set

    internal val pendingParentTypeIds: List<TypeId>
        get() = java.util.Collections.unmodifiableList(ArrayList(pendingParentTypes))

    internal val hasPendingParentTypes: Boolean get() = pendingParentTypes.isNotEmpty()

    /** Metadata supplies identities, never generated specialization names or mutable compiler types. */
    internal fun deferParentTypes(parents: List<TypeId>) {
        check((parents as List<*>).all { it is TypeId }) { "Invalid pending parent identity" }
        val copy = ArrayList(parents)
        pendingParentTypes.clear()
        pendingParentTypes.addAll(copy)
        hasRestoredParentTypes = true
        requestDeclarationCompletion()
        Project.invalidateTemplateParentBinding(this)
    }

    /** Resolve source expressions and restored identities through the same canonical parent binding. */
    internal fun resolveDeclaredParents(): Boolean {
        if (Project.compileStage == Project.CompileStage.READ_LIB) return false
        if (hasDeclarationCompletionFailure) return false
        if (resolvingParentTypes) {
            LogProcessor.error("Cyclic inheritance while resolving parents of $namespaceID")
            return false
        }
        val callerTemplate = currTemplate
        val callerFunction = Function.currFunction
        resolvingParentTypes = true
        try {
            val file = restoreDeclarationEnvironment()
            val restoredParentTypes = hasPendingParentTypes
            val resolve = {
                currTemplate = this
                val errorsBefore = Project.errorCount
                val declarations = if (restoredParentTypes) pendingParentTypes.map { id ->
                    val declared = MCFPPType.resolveTypeId(id) as? MCFPPDataTemplateType
                    declared?.tryResolve()
                    val parent = declared?.template
                    id.toString() to parent?.takeIf { it.inheritanceIdentity == id }
                } else parentID.map { name ->
                    val declared = MCFPPType.parseFromString(name, scope) as? MCFPPDataTemplateType
                    declared?.tryResolve()
                    name to declared?.template
                }
                for ((name, parent) in declarations) {
                    when {
                        parent == null -> LogProcessor.error("Undefined template: $name")
                        parent.isFinal -> LogProcessor.error("Cannot extends ${parent.identifier} because it's final")
                    }
                }
                if (Project.errorCount != errorsBefore) false
                else {
                    val originalParents = parent.toList()
                    val originalScopes = scope.parent.toList()
                    var committed = false
                    try {
                        // Reuse the canonical cycle/duplicate checks; publish no partial parent graph.
                        declarations.forEach { (_, parent) -> extends(parent!!) }
                        committed = Project.errorCount == errorsBefore
                        if (committed) pendingParentTypes.clear()
                        committed
                    } finally {
                        if (!committed) {
                            parent.clear()
                            parent.addAll(originalParents)
                            scope.parent.clear()
                            scope.parent.addAll(originalScopes)
                        }
                    }
                }
            }
            return if (file == null) resolve() else file.withDeclarationContext(resolve)
        } finally {
            resolvingParentTypes = false
            currTemplate = callerTemplate
            Function.currFunction = callerFunction
        }
    }

    /**
     * 构造函数
     */
    var constructors: ArrayList<DataTemplateConstructor> = ArrayList()

    private val reference: ArrayList<DataTemplate> = ArrayList()

    var alwaysDynamic: Boolean = false

    /**
     * 调用构造函数之前对成员进行初始化的部分
     */
    val preInit = LinkedHashMap<String, mcfppParser.ExpressionContext>()

    data class DeferredFieldDeclaration(
        val context: mcfppParser.TemplateFieldDeclarationContext,
        val access: Member.AccessModifier,
        val annotations: MutableList<top.mcfpp.model.annotation.Annotation> = arrayListOf()
    )

    /** Source declarations awaiting pure type binding, or the actual object initializer. */
    @Transient
    val deferredFields = LinkedHashMap<String, DeferredFieldDeclaration>()

    @Transient
    val pendingFieldAnnotations = LinkedHashMap<String, MutableList<top.mcfpp.model.annotation.Annotation>>()

    @Transient
    val pendingDeclarationAnnotations = arrayListOf<top.mcfpp.model.annotation.Annotation>()

    /**
     * Compiler-whitelisted annotation metadata produced by declaration processors.
     *
     * These facts are deliberately kept apart from [annotations]: generated metadata is visible in
     * later read-only declaration views but is never replayed through the legacy annotation
     * protocol (and therefore never invokes a processor again). The declaration journal captures
     * this list so an atomic generated batch can roll back partially published metadata too.
     */
    @Transient
    private val generatedDeclarationMetadata = arrayListOf<top.mcfpp.mni.api.AnnotationView>()

    internal fun declarationMetadata(): List<top.mcfpp.mni.api.AnnotationView> = java.util.Collections.unmodifiableList(ArrayList(generatedDeclarationMetadata))

    /** Publication is journaled; repeatable metadata may add another fact with the same name. */
    internal fun publishGeneratedMetadata(annotation: top.mcfpp.mni.api.AnnotationView, repeatable: Boolean): Boolean {
        if (!hasCompletedDeclaration) return false
        if (!repeatable &&
            (
                annotations.any {
                    it.identifier == annotation.name() || "${it.namespace}:${it.identifier}" == annotation.name()
                } ||
                    generatedDeclarationMetadata.any { it.name() == annotation.name() }
                )
        ) return false
        generatedDeclarationMetadata.add(annotation)
        return true
    }

    /** Apply source annotations after inheritance and inferred fields have their canonical declarations. */
    internal fun applyDeclarationAnnotations() {
        val apply = {
            val callerTemplate = currTemplate
            currTemplate = this
            try {
                pendingDeclarationAnnotations.forEach { it.on(this) }
                pendingDeclarationAnnotations.clear()
                for ((name, annotations) in pendingFieldAnnotations) {
                    val field = scope.getVar(name)
                    if (field == null) {
                        LogProcessor.error("Cannot apply annotations to unresolved template field '$name'")
                        continue
                    }
                    annotations.forEach { it.on(field) }
                    field.annotations.addAll(annotations)
                }
                pendingFieldAnnotations.clear()
            } finally {
                currTemplate = callerTemplate
            }
        }
        val file = restoreDeclarationEnvironment()
        if (file == null) apply() else file.withDeclarationContext(apply)
    }

    var companionObject: DataTemplate? = null

    var isAbstract = false

    var isFinal = false

    /** Positive evidence belongs to this actual instance, not its presence in a specialization map. */
    internal enum class DeclarationCompletionPhase { UNREQUESTED, PENDING, COMPLETE, FAILED }
    @Transient private var declarationCompletionGeneration = 0L
    @Transient private var declarationCompletionPhase = DeclarationCompletionPhase.UNREQUESTED
    // A failed mutating stage may have consumed pending annotations already. Requeueing or
    // resetting a diagnostic counter cannot make those partially applied effects valid.
    @Transient private var declarationCompletionPoisoned = false

    internal val hasCompletedDeclaration: Boolean
        get() = declarationCompletionPhase == DeclarationCompletionPhase.COMPLETE

    internal val hasDeclarationCompletionFailure: Boolean
        get() = declarationCompletionPoisoned

    /** Read the same validated raw slots as a checkpoint, without retaining its restore actions. */
    internal fun preparationFacts(): DeclarationPreparationFacts {
        check(
            javaClass in setOf(
                DataTemplate::class.java,
                ObjectDataTemplate::class.java,
                GenericDataTemplate::class.java,
                GenericObjectDataTemplate::class.java,
                CompiledGenericDataTemplate::class.java,
                CompiledGenericObjectDataTemplate::class.java
            )
        ) {
            "Cannot read preparation facts for template shape ${javaClass.name}"
        }
        return checkpointDeclarationState().preparationFacts
    }

    /** Record only new failures of this owner's actual mutating stage, including pre-barrier work. */
    internal fun <T> declarationCompletionStage(action: () -> T): T {
        beginDeclarationCompletion()
        val errorsBefore = Project.errorCount
        var returnedNormally = false
        try {
            val result = action()
            returnedNormally = true
            return result
        } finally {
            if (!returnedNormally || Project.errorCount > errorsBefore) {
                declarationCompletionPoisoned = true
                declarationCompletionPhase = DeclarationCompletionPhase.FAILED
            }
        }
    }

    /** Every explicit request invalidates any earlier proof before it enters the pending queue. */
    internal fun requestDeclarationCompletion() {
        declarationCompletionGeneration++
        declarationCompletionPhase = DeclarationCompletionPhase.PENDING
    }

    internal fun beginDeclarationCompletion(): Long {
        if (declarationCompletionPhase != DeclarationCompletionPhase.PENDING) requestDeclarationCompletion()
        return declarationCompletionGeneration
    }

    internal fun canCompleteDeclaration(generation: Long): Boolean = generation == declarationCompletionGeneration &&
        !declarationCompletionPoisoned &&
        ((this as? CompiledGenericDataTemplate)?.originTemplate?.hasDeclarationCompletionFailure != true) &&
        pendingParentTypes.isEmpty() &&
        deferredFields.isEmpty() &&
        pendingFieldAnnotations.isEmpty() &&
        pendingDeclarationAnnotations.isEmpty() &&
        fieldLayoutDiagnostics.isEmpty()

    /** Only the real declaration barrier calls this, after all of its stages have returned. */
    internal fun finishDeclarationCompletion(generation: Long, successful: Boolean) {
        if (generation != declarationCompletionGeneration) return
        declarationCompletionPhase = if (successful && canCompleteDeclaration(generation)) DeclarationCompletionPhase.COMPLETE
        else DeclarationCompletionPhase.FAILED
    }

    /**
     * 获取这个容器中变量应该拥有的前缀
     * @return 其中的变量将会添加的前缀
     */
    override val prefix: String
        get() = namespace + "_template_" + identifier + "_"

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(identifier: String, namespace: String = Project.currNamespace) {
        this.identifier = identifier
        scope = CompoundDataScope(ArrayList())
        this.namespace = namespace
    }

    /**
     * 获取这个类对于的classType
     */
    internal override val inheritanceIdentity: TypeId
        get() = TypeId.Declaration(
            when {
                this is ObjectCompoundData -> "object"
                else -> "template"
            },
            namespace,
            identifier
        )

    override fun getType(): MCFPPDataTemplateType {
        val visiting = buildingTypes.get()
        val entered = visiting.add(this)
        try {
            val parents = if (entered) parent.filterIsInstance<DataTemplate>().map { it.getType() } else emptyList()
            return MCFPPDataTemplateType(this, ArrayList<MCFPPType>(parents).apply { add(MCFPPBaseType.Object) })
        } finally {
            if (entered) visiting.remove(this)
            if (visiting.isEmpty()) buildingTypes.remove()
        }
    }

    /**
     * 检查给定复合标签是否符合此数据模板
     * @param compoundTag 给定的复合标签
     * @return 返回值
     */
    fun checkCompoundStruct(compoundTag: CompoundTag) : Boolean {
        for (member in scope.allVars.filter { !it.isStatic }) {
            if (!compoundTag.containsKey(member.identifier)) return false
            if (!member.type.checkNBTType(compoundTag[member.identifier]!!)) return false
        }
        return true
    }

    fun checkDictionaryStruct(dict: Map<String, Var<*>>) : Boolean {
        for (member in scope.allVars.filter { !it.nullable && !it.isStatic }) {
            if (!dict.containsKey(member.identifier)) return false
            if (!dict[member.identifier]!!.type.isSubOf(member.type)) return false
        }
        return true
    }

    override fun isSubOf(compoundData: CompoundData): Boolean {
        if (compoundData.inheritanceIdentity == baseDataTemplate.inheritanceIdentity) return true
        return super.isSubOf(compoundData)
    }

    /**
     * 向这个类中添加一个成员
     * @param member 要添加的成员
     */
    override fun addMember(member: Member): Boolean {
        return when (member) {
            is DataTemplateConstructor -> {
                if (this.isAbstract) {
                    LogProcessor.error("Abstract DataTemplate Constructor")
                    return false
                }
                if (constructors.contains(member)) {
                    return false
                } else {
                    constructors.add(member)
                    return true
                }
            }
            is Function -> {
                // Retain abstract declarations so the complete obligation view can reject
                // an invalid concrete type instead of silently losing its missing slot.
                scope.addFunction(member, false)
            }
            is Var<*> -> {
                if (member is DataTemplateObject) {
                    if (ifInfinitiveReference(member.templateType)) {
                        LogProcessor.error("Infinitive reference: ${member.templateType.identifier} -> ${this.identifier}")
                        return scope.putVar(member.identifier, UnknownVar(member.identifier))
                    }
                }
                val added = scope.putVar(member.identifier, member)
                if (added && member.identifier in inheritedFieldRequirements) {
                    validateLocalField(member, inheritedFieldRequirements.getValue(member.identifier))
                    scope.fieldOrigins[member.identifier] = listOf(CompoundDataScope.FieldOrigin(this, member, null))
                }
                added
            }
            is Property -> {
                scope.putProperty(member.identifier, member)
            }
            else -> {
                throw IllegalArgumentException("")
            }
        }
    }

    override fun getAccess(compoundData: CompoundData): Member.AccessModifier {
        if (companionObject === compoundData || (compoundData as? DataTemplate)?.companionObject === this)
            return Member.AccessModifier.PRIVATE
        if (compoundData is DataTemplate) {
            if (getType().typeId == compoundData.getType().typeId) return Member.AccessModifier.PRIVATE
            return if (getType().isSubOf(compoundData.getType())) Member.AccessModifier.PROTECTED
            else Member.AccessModifier.PUBLIC
        }
        return super.getAccess(compoundData)
    }

    @Transient
    private val flattenedParents = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<CompoundData, Boolean>())
    /** FieldInfo restores inherited members as part of the declaration's saved scope. */
    @Transient internal var restoredFlattenedFields = false

    @Transient private val inheritedFieldRequirements = linkedMapOf<String, List<CompoundDataScope.FieldOrigin>>()
    @Transient private val inheritedLayoutFields = hashSetOf<String>()
    @Transient private val fieldLayoutDiagnostics = hashSetOf<String>()

    /** Declaration effects only: no initializer, runtime value, or annotation invocation. */
    internal class CompletedInheritedField(
        val origin: TypeId,
        field: Var<*>,
        property: Property?
    ) {
        val field: Var<*> = field.type.buildUnConcrete(field.identifier).also {
            Property.copyFieldMetadata(field, it)
            it.declaredParentTemplate = null
        }
        val property: Property? = property?.let { copyProperty(it) }?.also {
            it.parent = null
            it.declaredParentTemplate = null
        }
        internal fun detached() = CompletedInheritedField(origin, field, property)

        companion object {
            /** Wrappers are mutable receiver metadata; only canonical declarations may be shared. */
            internal fun copyProperty(property: Property, resolve: (Function) -> Function = { it }): Property {
                val accessor = when (val original = property.accessor) {
                    null -> null
                    is SimpleAccessor -> SimpleAccessor()
                    is FunctionAccessor -> FunctionAccessor(resolve(original.function))
                    is NativeAccessor -> NativeAccessor(
                        resolve(original.function) as? NativeFunction
                            ?: throw InheritedFieldRestoreFailure("native getter resolved to an ordinary function")
                    )
                    else -> throw InheritedFieldRestoreFailure("unsupported getter ${original.javaClass.name}")
                }
                val mutator = when (val original = property.mutator) {
                    null -> null
                    is SimpleMutator -> SimpleMutator()
                    is FunctionMutator -> FunctionMutator(resolve(original.function))
                    is NativeMutator -> NativeMutator(
                        resolve(original.function) as? NativeFunction
                            ?: throw InheritedFieldRestoreFailure("native setter resolved to an ordinary function")
                    )
                    else -> throw InheritedFieldRestoreFailure("unsupported setter ${original.javaClass.name}")
                }
                return Property(property.identifier, accessor, mutator).also {
                    it.parent = property.parent
                    it.declaredParentTemplate = property.declaredParentTemplate
                    it.accessModifier = property.accessModifier
                    it.isStatic = property.isStatic
                    it.isFinal = property.isFinal
                    it.isInherited = property.isInherited
                }
            }
        }
    }

    private class InheritedFieldRestoreFailure(message: String) : RuntimeException(message)

    @Transient private val restoredInheritedFieldEffects = linkedMapOf<String, CompletedInheritedField>()
    @Transient private val appliedInheritedFieldEffects = hashSetOf<String>()

    /** Capture effective inherited metadata without turning its field into a local declaration. */
    internal fun completedInheritedFields(): List<CompletedInheritedField> = inheritedLayoutFields.mapNotNull { name ->
        val origin = scope.fieldOrigins[name]?.singleOrNull() ?: return@mapNotNull null
        val field = scope.getVar(name) ?: return@mapNotNull null
        try {
            CompletedInheritedField(origin.owner.inheritanceIdentity, field, scope.getProperty(name))
        } catch (failure: InheritedFieldRestoreFailure) {
            fieldLayoutError("Cannot capture inherited field '$name' in $namespaceID: ${failure.message}")
            null
        }
    }

    /** Register detached, already-completed effects; layout will validate their canonical slots. */
    internal fun restoreInheritedFields(fields: List<CompletedInheritedField>) {
        for (effect in fields) {
            val name = effect.field.identifier
            try {
                restoredInheritedFieldEffects[name] = effect.detached()
                appliedInheritedFieldEffects.remove(name)
            } catch (failure: InheritedFieldRestoreFailure) {
                fieldLayoutError("Cannot restore inherited field '$name' in $namespaceID: ${failure.message}")
            }
        }
    }

    private fun restoredPropertyFunction(name: String, source: Function): Function {
        val ownerId = source.owner?.inheritanceIdentity
            ?: throw InheritedFieldRestoreFailure("accessor '${source.identifier}' has no declaration owner")
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<CompoundData, Boolean>())
        val pending = ArrayDeque<CompoundData>()
        val owners = arrayListOf<CompoundData>()
        pending.add(this)
        while (pending.isNotEmpty()) {
            val owner = pending.removeFirst()
            if (!visited.add(owner)) continue
            if (owner.inheritanceIdentity == ownerId) owners.add(owner)
            pending.addAll(owner.parent)
        }
        // Normal layout has already canonicalized a same-origin diamond. Use its actual
        // declaration owner rather than treating equivalent bound model copies as a conflict.
        val slotOwner = scope.fieldOrigins[name]?.singleOrNull()?.owner?.takeIf {
            it.inheritanceIdentity == ownerId && it in owners
        }
        val owner = slotOwner ?: owners.singleOrNull() ?: throw InheritedFieldRestoreFailure(
            "accessor '${source.identifier}' has no unique receiving declaration owner $ownerId"
        )
        val declarations = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
        declarations.addAll(owner.scope.functions.values.flatten())
        owner.scope.getProperty(name)?.let { property ->
            when (val accessor = property.accessor) {
                is FunctionAccessor -> declarations.add(accessor.function)
                is NativeAccessor -> declarations.add(accessor.function)
                else -> Unit
            }
            when (val mutator = property.mutator) {
                is FunctionMutator -> declarations.add(mutator.function)
                is NativeMutator -> declarations.add(mutator.function)
                else -> Unit
            }
        }
        return declarations.filter { candidate ->
            candidate.owner === owner &&
                ParameterMatcher.sameSignature(source, candidate) &&
                candidate.returnType.typeId == source.returnType.typeId &&
                candidate.isStatic == source.isStatic &&
                (candidate is NativeFunction) == (source is NativeFunction) &&
                (
                    source !is NativeFunction ||
                        candidate is NativeFunction &&
                        candidate.javaMethod == source.javaMethod &&
                        candidate.caller.typeId == source.caller.typeId
                    )
        }.singleOrNull() ?: throw InheritedFieldRestoreFailure(
            "accessor '${source.identifier}' has no unique receiving declaration in $ownerId"
        )
    }

    private fun applyRestoredInheritedFields() {
        for ((name, effect) in restoredInheritedFieldEffects) {
            if (!appliedInheritedFieldEffects.add(name)) continue
            // A genuine local declaration, including one still awaiting inference, always wins.
            if (name in deferredFields || scope.getVar(name)?.declaredParentTemplate === this) continue
            val origin = scope.fieldOrigins[name]?.singleOrNull()
            val field = scope.getVar(name)
            if (name !in inheritedLayoutFields || origin == null || field == null) {
                fieldLayoutError("Cannot restore inherited field '$name' in $namespaceID: no unique inherited slot")
                continue
            }
            if (origin.owner.inheritanceIdentity != effect.origin) {
                fieldLayoutError("Cannot restore inherited field '$name' in $namespaceID: origin ${effect.origin} differs from ${origin.owner.inheritanceIdentity}")
                continue
            }
            val saved = CompoundDataScope.FieldOrigin(origin.owner, effect.field, effect.property)
            val differences = origin.differences(saved)
            if (differences.isNotEmpty()) {
                fieldLayoutError("Cannot restore inherited field '$name' in $namespaceID from ${effect.origin}: incompatible ${differences.joinToString()}")
                continue
            }
            val property = try {
                effect.property?.let { saved ->
                    CompletedInheritedField.copyProperty(saved) { restoredPropertyFunction(name, it) }
                }
            } catch (failure: InheritedFieldRestoreFailure) {
                fieldLayoutError("Cannot restore inherited field '$name' in $namespaceID: ${failure.message}")
                continue
            }
            Property.copyFieldMetadata(effect.field, field)
            field.declaredParentTemplate = origin.owner as? DataTemplate
            property?.also {
                it.parent = null
                it.declaredParentTemplate = origin.owner as? DataTemplate
                it.isInherited = true
            }
            if (property == null) scope.removeProperty(name) else scope.putProperty(name, property, true)
            scope.fieldOrigins[name] = listOf(CompoundDataScope.FieldOrigin(origin.owner, field, property))
        }
    }

    private fun fieldLayoutError(message: String) {
        if (fieldLayoutDiagnostics.add(message)) LogProcessor.error(message)
    }

    private fun validateLocalField(local: Var<*>, origins: List<CompoundDataScope.FieldOrigin>) {
        val declaration = CompoundDataScope.FieldOrigin(this, local, scope.getProperty(local.identifier))
        for (origin in origins) {
            val differences = origin.contract.replacementDifferences(declaration.contract)
            if (differences.isNotEmpty()) fieldLayoutError(
                "Incompatible inherited field '${local.identifier}' in $namespaceID: " +
                    "${origin.owner.inheritanceIdentity} -> $inheritanceIdentity (${differences.joinToString()})"
            )
        }
    }

    /** An unresolved ancestor cannot supply a canonical inherited slot or its saved effects. */
    private fun hasPendingLayoutParents(): Boolean {
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<CompoundData, Boolean>())
        val pending = ArrayDeque<CompoundData>()
        pending.add(this)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current)) continue
            if (current is DataTemplate && current.hasPendingParentTypes) return true
            pending.addAll(current.parent)
        }
        return false
    }

    fun flatExtends(): CompoundData {
        // Namespace resolution may visit restored declarations before all includes are bound.
        // Keep both layout bookkeeping and the once-only effect markers untouched until then.
        if (hasPendingLayoutParents()) return this
        if (parent.all { it in flattenedParents }) {
            applyRestoredInheritedFields()
            return this
        }
        flattenedParents.addAll(parent)
        if (restoredFlattenedFields) {
            applyRestoredInheritedFields()
            return this
        }
        // Rebuild when a declaration header gained parents after an earlier completion pass.
        for (name in inheritedLayoutFields) {
            scope.removeVar(name)
            scope.removeProperty(name)
        }
        inheritedLayoutFields.clear()
        appliedInheritedFieldEffects.clear()
        inheritedFieldRequirements.clear()
        scope.fieldOrigins.clear()
        val candidates = linkedMapOf<String, MutableList<CompoundDataScope.FieldOrigin>>()
        for (compoundData in parent) {
            val readonlyNames = (compoundData as? CompiledGenericDataTemplate)?.originTemplate
                ?.readOnlyParams?.map { it.identifier }.orEmpty()
            val names = compoundData.scope.allVars.map { it.identifier } + compoundData.scope.fieldOrigins.keys
            for (name in names.distinct()) {
                if (name in readonlyNames) continue
                val field = compoundData.scope.getVar(name)
                val savedOrigins = compoundData.scope.fieldOrigins[name]
                // Carry declaration identity through a diamond, but copy the direct parent's
                // effective declaration so effects already applied in that parent survive.
                val origins = savedOrigins?.map { origin ->
                    if (savedOrigins.size == 1 && field != null)
                        CompoundDataScope.FieldOrigin(origin.owner, field, compoundData.scope.getProperty(name))
                    else origin
                } ?: field?.let {
                    listOf(
                        CompoundDataScope.FieldOrigin(
                            it.declaredParentTemplate ?: compoundData,
                            it,
                            compoundData.scope.getProperty(name)
                        )
                    )
                }.orEmpty()
                candidates.getOrPut(name) { arrayListOf() }.addAll(origins)
            }
        }
        for ((name, paths) in candidates) {
            val merge = CompoundDataScope.InheritedFields.merge(
                paths.map { origin ->
                    CompoundDataScope.FieldCandidate(
                        origin.owner.inheritanceIdentity,
                        CompoundDataScope.InheritedFields.ancestors(origin.owner),
                        origin.contract,
                        origin
                    )
                }
            )
            val origins = merge.origins.map { it.value }
            inheritedFieldRequirements[name] = origins
            val conflicts = merge.conflicts
            val labels = origins.joinToString { it.owner.inheritanceIdentity.toString() }
            if (conflicts.isNotEmpty()) fieldLayoutError(
                "Incompatible inherited field '$name' in $namespaceID from $labels (${conflicts.joinToString()})"
            )
            val local = scope.getVar(name)
            if (local != null || name in deferredFields) {
                if (local != null) {
                    validateLocalField(local, origins)
                    scope.fieldOrigins[name] = listOf(CompoundDataScope.FieldOrigin(this, local, scope.getProperty(name)))
                }
                continue
            }
            val effective = merge.effective.map { it.value }
            scope.fieldOrigins[name] = effective
            if (conflicts.isNotEmpty()) continue
            if (effective.size != 1) {
                fieldLayoutError("Ambiguous inherited field '$name' in $namespaceID from $labels; declare a compatible local field")
                continue
            }
            val selected = effective.single()
            val inheritedProperty = try {
                selected.property?.let { CompletedInheritedField.copyProperty(it) }
            } catch (failure: InheritedFieldRestoreFailure) {
                fieldLayoutError("Cannot inherit field '$name' in $namespaceID: ${failure.message}")
                continue
            }
            val inherited = selected.field.clone()
            // Some adapters' copy constructors retain their shape but default their type
            // to Any. The validated declaration supplies the exact inherited slot type.
            inherited.type = selected.field.type
            inherited.annotations.clear()
            inherited.annotations.addAll(selected.field.annotations)
            scope.putVar(name, inherited)
            inheritedProperty?.let { property ->
                scope.putProperty(
                    name,
                    property.also {
                        it.parent = null
                        it.isInherited = true
                    }
                )
            }
            inheritedLayoutFields.add(name)
        }
        applyRestoredInheritedFields()
        return this
    }

    @Transient private val inheritanceContractDiagnostics = hashSetOf<String>()

    /** Private completion relations only; published entities are roots for the composing deep journal. */
    internal class DeclarationStateCheckpoint internal constructor(
        elements: List<Any>,
        private val restoreState: () -> Unit,
        private val recapture: () -> DeclarationStateCheckpoint,
        val preparationFacts: DeclarationPreparationFacts
    ) {
        val elements: List<Any> = java.util.Collections.unmodifiableList(ArrayList(elements))
        fun restore() = restoreState()
        fun captureCurrent(): DeclarationStateCheckpoint = recapture()
    }

    internal open fun checkpointDeclarationState(): DeclarationStateCheckpoint = captureDeclarationState(emptyList(), emptyList(), emptyList())

    private fun captureDeclarationState(
        retained: List<(TemplatePrivateCapture) -> Unit>,
        previous: List<Any>,
        oldScopes: List<CompoundDataScope.JournalSnapshot>
    ): DeclarationStateCheckpoint {
        val graph = TemplatePrivateCapture(previous)
        val savedFile = declarationFile
        val savedEnvironment = declarationEnvironment
        val savedScope = scope
        val savedConstructors = constructors
        val savedRestored = restoredFlattenedFields
        val savedCompletionGeneration = declarationCompletionGeneration
        val savedCompletionPhase = declarationCompletionPhase
        val savedCompletionPoisoned = declarationCompletionPoisoned
        val savedResolvingParents = resolvingParentTypes
        val savedRestoredParentTypes = hasRestoredParentTypes
        val savedGeneratedMetadata = ArrayList(generatedDeclarationMetadata)
        val scopes = oldScopes + savedScope.checkpointRelations()
        graph.list(pendingParentTypes, TemplatePrivateCapture.ListRole.TYPE_IDS) { g, value -> g.element(value as TypeId) }
        graph.list(reference, TemplatePrivateCapture.ListRole.REFERENCES) { g, value -> g.element(value as DataTemplate) }
        graph.set(flattenedParents, TemplatePrivateCapture.SetRole.PARENTS) { g, value -> g.element(value as CompoundData) }
        graph.stringMap(inheritedFieldRequirements, TemplatePrivateCapture.MapRole.FIELD_REQUIREMENTS) { g, value ->
            g.list(value as List<*>, TemplatePrivateCapture.ListRole.ORIGINS) { nested, item -> nested.origin(item as CompoundDataScope.FieldOrigin) }
        }
        graph.set(inheritedLayoutFields, TemplatePrivateCapture.SetRole.STRINGS) { _, value -> check(value is String) }
        graph.set(fieldLayoutDiagnostics, TemplatePrivateCapture.SetRole.STRINGS) { _, value -> check(value is String) }
        graph.set(inheritanceContractDiagnostics, TemplatePrivateCapture.SetRole.STRINGS) { _, value -> check(value is String) }
        graph.stringMap(restoredInheritedFieldEffects, TemplatePrivateCapture.MapRole.RESTORED_EFFECTS) { g, value ->
            val effect = value as CompletedInheritedField
            g.element(effect)
            g.element(effect.origin)
            g.variable(effect.field)
            effect.property?.let(g::property)
        }
        graph.set(appliedInheritedFieldEffects, TemplatePrivateCapture.SetRole.STRINGS) { _, value -> check(value is String) }
        graph.stringMap(preInit, TemplatePrivateCapture.MapRole.INITIALIZERS) { g, value ->
            check(value is mcfppParser.ExpressionContext)
            g.element(value)
        }
        graph.stringMap(deferredFields, TemplatePrivateCapture.MapRole.DEFERRED_FIELDS) { g, value ->
            val field = value as DeferredFieldDeclaration
            g.element(field)
            g.element(field.context)
            g.list(field.annotations, TemplatePrivateCapture.ListRole.ANNOTATIONS) { nested, item -> nested.element(item as top.mcfpp.model.annotation.Annotation) }
        }
        graph.stringMap(pendingFieldAnnotations, TemplatePrivateCapture.MapRole.PENDING_ANNOTATIONS) { g, value ->
            g.list(value as List<*>, TemplatePrivateCapture.ListRole.ANNOTATIONS) { nested, item -> nested.element(item as top.mcfpp.model.annotation.Annotation) }
        }
        graph.list(pendingDeclarationAnnotations, TemplatePrivateCapture.ListRole.ANNOTATIONS) { g, value -> g.element(value as top.mcfpp.model.annotation.Annotation) }
        graph.list(savedConstructors, TemplatePrivateCapture.ListRole.CONSTRUCTORS) { g, value ->
            check(value is DataTemplateConstructor) { "Template checkpoint found an invalid constructor" }
            g.element(value)
        }
        savedEnvironment?.let { environment ->
            graph.element(environment)
            graph.stringMap(environment.imports, TemplatePrivateCapture.MapRole.IMPORTS) { _, value -> check(value is String) }
        }
        savedFile?.let(graph::element)
        // Capture only the current declaration's relations. Retained, disconnected roots below
        // belong to rollback, not to the declaration domain being compared by a new request.
        val preparationFacts = DeclarationPreparationFacts(
            this,
            linkedMapOf(
                "completion.generation" to RawPreparationFact.scalar(savedCompletionGeneration),
                "completion.phase" to RawPreparationFact.scalar(savedCompletionPhase.name),
                "completion.poisoned" to RawPreparationFact.scalar(savedCompletionPoisoned),
                "parents.resolving" to RawPreparationFact.scalar(savedResolvingParents),
                "parents.restored" to RawPreparationFact.scalar(savedRestoredParentTypes),
                "layout.restored" to RawPreparationFact.scalar(savedRestored),
                "scope" to RawPreparationFact.identity(savedScope),
                "file" to RawPreparationFact.identity(savedFile),
                "environment" to RawPreparationFact.identity(savedEnvironment),
                "relations" to graph.preparationRelations()
            )
        )
        for (snapshot in scopes) {
            graph.element(snapshot)
            val elements = snapshot.elements
            elements.variables.forEach(graph::variable)
            elements.types.forEach(graph::element)
            elements.properties.forEach(graph::property)
            elements.functions.forEach(graph::element)
            elements.parents.forEach(graph::element)
            elements.originOwners.forEach(graph::element)
        }
        retained.forEach { it(graph) }
        return DeclarationStateCheckpoint(graph.elements, {
            declarationFile = savedFile
            declarationEnvironment = savedEnvironment
            scope = savedScope
            constructors = savedConstructors
            restoredFlattenedFields = savedRestored
            declarationCompletionGeneration = savedCompletionGeneration
            declarationCompletionPhase = savedCompletionPhase
            declarationCompletionPoisoned = savedCompletionPoisoned
            resolvingParentTypes = savedResolvingParents
            hasRestoredParentTypes = savedRestoredParentTypes
            generatedDeclarationMetadata.clear()
            generatedDeclarationMetadata.addAll(savedGeneratedMetadata)
            graph.restore()
            scopes.forEach { it.restore() }
        }, {
            captureDeclarationState(graph.roots, graph.elements, scopes.map { it.captureCurrent() })
        }, preparationFacts)
    }

    /**
     * Live declaration-side half of the generated-member contract. It reuses the same visible
     * candidate walk as ordinary source lookup, so inherited hiding, overload identity and
     * private-parent visibility are not reimplemented here. A detached method is never published
     * before this check; [publishGeneratedMethod] repeats the local part immediately before the
     * transactional registration.
     */
    internal fun generatedMethodSlotConflict(function: Function): Function? {
        if (function.owner !== this || function.isStatic || function.ownerType != Function.Companion.OwnerType.TEMPLATE)
            return function
        return scope.getFunctionCandidates(function.identifier, null).firstOrNull {
            ParameterMatcher.sameSignature(it, function)
        }
    }

    /** Only a new, permitted instance-member slot may enter the completed declaration. */
    internal fun publishGeneratedMethod(function: Function): Boolean {
        if (function.owner !== this || function.isStatic || function.ownerType != Function.Companion.OwnerType.TEMPLATE)
            return false
        if (function.isAbstract || function.isOverride) return false
        if (scope.functions[function.identifier].orEmpty().any { ParameterMatcher.sameSignature(it, function) })
            return false
        return scope.addFunction(function, false)
    }

    /** Recompute after signature completion; only diagnostics are deduplicated, never validity. */
    internal fun validateInheritance(): Boolean {
        val result = top.mcfpp.analysis.InheritanceContractValidator.check(this)
        val initialization = top.mcfpp.model.function.ConstructorPlan.build(this)
        (result.diagnostics + initialization.diagnostics).forEach {
            if (inheritanceContractDiagnostics.add(it)) LogProcessor.error(it)
        }
        return result.isValid && initialization.isValid
    }

    internal fun canInstantiate(): Boolean {
        val valid = validateInheritance()
        if (isAbstract) {
            val message = "Cannot instantiate abstract template '$namespaceID'"
            if (inheritanceContractDiagnostics.add(message)) LogProcessor.error(message)
            return false
        }
        return valid
    }

    //TODO 不能正常检测循环引用
    fun ifInfinitiveReference(template: DataTemplate): Boolean {
        return template == this && reference.any { it == template || it.ifInfinitiveReference(template) }
    }

    fun resolveConstructor(arguments: List<Var<*>>): ParameterMatcher.TypeSelection = if (canInstantiate()) ParameterMatcher.selectConstructor(constructors, arguments)
    else ParameterMatcher.TypeSelection.Missing

    companion object {

        private val buildingTypes = ThreadLocal.withInitial {
            java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())
        }

        var currTemplate: DataTemplate? = null

        val baseDataTemplate by lazy {
            DataTemplate("DataObject","mcfpp.lang").apply {
                extends(MCFPPBaseType.Object.instanceData)
                //在GlobalField中注册和获取函数
            }
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <T: Var<*>> getField(obj: DataTemplateObject, identifier: String): T? {
            return obj.getMemberVar(identifier, Member.AccessModifier.PUBLIC).first as T?
        }

        @JvmStatic
        fun assignField(obj: DataTemplateObject, identifier: String, v: Var<*>) {
            val member = getField(obj, identifier)!!
            member.replacedBy(member.assignedBy(v))
        }

        @JvmStatic
        fun isInstance(obj: DataTemplateObject, template: DataTemplate): Boolean {
            return obj.templateType.isSubOf(template)
        }

        @JvmStatic
        fun isInstance(obj: DataTemplateObject, namespace: String?, templateID: String): Boolean {
            return isInstance(obj, GlobalScope.getTemplate(namespace, templateID)!!)
        }
    }
}

/** Immutable values and identity leaves; comparison never calls a compiler object's equals/hashCode. */
internal sealed class RawPreparationFact {
    class Identity internal constructor(val value: Any?) : RawPreparationFact() {
        override fun equals(other: Any?) = other is Identity && value === other.value
        override fun hashCode() = System.identityHashCode(value)
    }
    class Scalar internal constructor(val value: Any?) : RawPreparationFact() {
        init {
            check(value == null || value is String || value is Boolean || value is Long || value is Int) {
                "Invalid raw preparation scalar"
            }
        }
        override fun equals(other: Any?) = other is Scalar && value == other.value
        override fun hashCode() = value?.hashCode() ?: 0
    }
    class Sequence internal constructor(values: List<RawPreparationFact>) : RawPreparationFact() {
        val values: List<RawPreparationFact> = java.util.Collections.unmodifiableList(ArrayList(values))
        override fun equals(other: Any?) = other is Sequence && values == other.values
        override fun hashCode() = values.hashCode()
    }
    class Unordered internal constructor(values: List<RawPreparationFact>) : RawPreparationFact() {
        val values: List<RawPreparationFact> = java.util.Collections.unmodifiableList(ArrayList(values))
        override fun equals(other: Any?): Boolean {
            if (other !is Unordered || values.size != other.values.size) return false
            val remaining = other.values.toMutableList()
            return values.all { value ->
                remaining.indexOf(value).let { index ->
                    if (index < 0) false else {
                        remaining.removeAt(index)
                        true
                    }
                }
            }
        }
        override fun hashCode() = values.sumOf { it.hashCode() }
    }

    companion object {
        fun identity(value: Any?) = Identity(value)
        fun scalar(value: Any?) = Scalar(value)
        fun sequence(values: List<RawPreparationFact>) = Sequence(values)
        fun row(vararg values: RawPreparationFact) = Sequence(values.toList())
        fun unordered(values: List<RawPreparationFact>) = Unordered(values)

        /** Sealed compiler payloads own their collections; validate erased members before use. */
        fun closed(value: Any?): RawPreparationFact {
            fun tagged(name: String, vararg values: RawPreparationFact) = row(scalar(name), *values)
            fun items(values: List<*>, convert: (Any?) -> RawPreparationFact) = sequence(values.map(convert))
            return when (value) {
                top.mcfpp.analysis.CompilerValue.NullValue -> tagged("null")
                is top.mcfpp.analysis.CompilerValue.Integral -> tagged("int", scalar(value.value))
                is top.mcfpp.analysis.CompilerValue.FloatBits -> tagged("float", scalar(value.bits))
                is top.mcfpp.analysis.CompilerValue.DoubleBits -> tagged("double", scalar(value.bits))
                is top.mcfpp.analysis.CompilerValue.Bool -> tagged("bool", scalar(value.value))
                is top.mcfpp.analysis.CompilerValue.Text -> tagged("text", scalar(value.value))
                is top.mcfpp.analysis.CompilerValue.Nbt -> tagged("nbt", scalar(value.snbt))
                is top.mcfpp.analysis.CompilerValue.TypeValue -> tagged("type", closed(value.id))
                is top.mcfpp.analysis.CompilerValue.Typed -> tagged("typed", closed(value.type), closed(value.payload))
                is top.mcfpp.analysis.CompilerValue.Sequence -> tagged(
                    "sequence",
                    items(value.elements as List<*>) {
                        check(it is top.mcfpp.analysis.CompilerValue)
                        closed(it)
                    }
                )
                is top.mcfpp.analysis.CompilerValue.Record -> tagged(
                    "record",
                    unordered(
                        (value.fields as Map<*, *>).entries.map {
                            check(it.key is String && it.value is top.mcfpp.analysis.CompilerValue)
                            row(scalar(it.key), closed(it.value))
                        }
                    )
                )
                is TypeId.Builtin -> tagged("builtin", scalar(value.key))
                is TypeId.Declaration -> tagged("declaration", scalar(value.kind), scalar(value.namespace), scalar(value.name))
                is TypeId.Opaque -> tagged("opaque", scalar(value.declaration))
                is TypeId.Applied -> tagged(
                    "applied",
                    closed(value.constructor),
                    items(value.arguments as List<*>) {
                        check(it is TypeId)
                        closed(it)
                    }
                )
                is TypeId.Specialized -> tagged(
                    "specialized",
                    closed(value.constructor),
                    items(value.arguments as List<*>) {
                        check(it is top.mcfpp.analysis.CompilerValue)
                        closed(it)
                    }
                )
                is TypeId.Union -> tagged(
                    "union",
                    unordered(
                        (value.alternatives as Set<*>).map {
                            check(it is TypeId)
                            closed(it)
                        }
                    )
                )
                is TypeId.Selector -> tagged(
                    "selector",
                    scalar(value.limit),
                    scalar(value.isName),
                    value.entities?.let { names ->
                        items(names as List<*>) {
                            check(it is String)
                            scalar(it)
                        }
                    } ?: scalar(null)
                )
                else -> error("Template checkpoint cannot capture specialization payload")
            }
        }
    }
}

/** Raw declaration facts are independent snapshots. Original entities appear only as identity leaves. */
internal class DeclarationPreparationFacts internal constructor(owner: DataTemplate, values: Map<String, RawPreparationFact>) {
    val owner = RawPreparationFact.identity(owner)
    val values: Map<String, RawPreparationFact> = java.util.Collections.unmodifiableMap(LinkedHashMap(values))
    internal fun extended(additional: Map<String, RawPreparationFact>) = DeclarationPreparationFacts(
        owner.value as DataTemplate,
        LinkedHashMap(values).apply { putAll(additional) }
    )
    override fun equals(other: Any?) = other is DeclarationPreparationFacts && owner == other.owner && values == other.values
    override fun hashCode() = 31 * owner.hashCode() + values.hashCode()
}

/** Bounded relation capture shared by template completion and generic header hooks. */
internal class TemplatePrivateCapture(previous: List<Any>) {
    enum class ListRole { REFERENCES, ORIGINS, ANNOTATIONS, CONSTRUCTORS, READONLY_PARAMETERS, TYPE_IDS }
    enum class MapRole { FIELD_REQUIREMENTS, RESTORED_EFFECTS, INITIALIZERS, DEFERRED_FIELDS, PENDING_ANNOTATIONS, IMPORTS, COMPILED_TEMPLATES }
    enum class SetRole { PARENTS, STRINGS }

    private val identities = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
    private val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
    // Erased aliases must satisfy every consuming field's role. State restoration remains
    // unique per physical container, and recapture retains all previously observed roles.
    private val listRoles = java.util.IdentityHashMap<List<*>, MutableSet<ListRole>>()
    private val mapRoles = java.util.IdentityHashMap<Map<*, *>, MutableSet<MapRole>>()
    private val setRoles = java.util.IdentityHashMap<Set<*>, MutableSet<SetRole>>()
    private val saved = arrayListOf<Any>()
    private val restores = arrayListOf<() -> Unit>()
    private val preparation = arrayListOf<RawPreparationFact>()
    val roots = arrayListOf<(TemplatePrivateCapture) -> Unit>()
    val elements: List<Any> get() = saved

    init {
        previous.forEach(::element)
    }
    fun element(value: Any) {
        if (identities.add(value)) saved.add(value)
    }
    fun restore() {
        restores.forEach { it() }
    }
    fun state(identity: Any, capture: (TemplatePrivateCapture) -> Unit) {
        element(identity)
        if (!visited.add(identity)) return
        roots.add { it.state(identity, capture) }
        capture(this)
    }
    fun restoreWith(action: () -> Unit) {
        restores.add(action)
    }
    fun preparationFact(name: String, vararg values: RawPreparationFact) {
        preparation.add(RawPreparationFact.row(RawPreparationFact.scalar(name), *values))
    }
    fun preparationRelations(): RawPreparationFact = RawPreparationFact.unordered(preparation)

    fun list(items: List<*>, role: ListRole, visit: (TemplatePrivateCapture, Any?) -> Unit) {
        if (!listRoles.getOrPut(items) { hashSetOf() }.add(role)) return
        val shape = items.javaClass
        val resizable = shape in listClasses
        val fixed = shape == fixedListClass
        check(resizable || fixed || shape in immutableListClasses) {
            "Template checkpoint cannot capture list ${shape.name}"
        }
        val contents = items.toList()
        contents.forEach { validateListElement(role, it) }
        preparationFact(
            "list.${role.name}",
            RawPreparationFact.identity(items),
            RawPreparationFact.sequence(
                contents.map {
                    if (role == ListRole.TYPE_IDS) RawPreparationFact.closed(it) else RawPreparationFact.identity(it)
                }
            )
        )
        roots.add { it.list(items, role, visit) }
        element(items)
        contents.forEach { visit(this, it) }
        if (visited.add(items) && (resizable || fixed)) {
            @Suppress("UNCHECKED_CAST")
            val target = items as MutableList<Any?>
            restores.add {
                if (resizable) {
                    target.clear()
                    target.addAll(contents)
                } else contents.forEachIndexed { index, value -> target[index] = value }
            }
        }
    }

    fun stringMap(items: Map<*, *>, role: MapRole, visit: (TemplatePrivateCapture, Any?) -> Unit) = map(items, role, { check(it is String) }, visit)

    fun map(items: Map<*, *>, role: MapRole, key: (Any?) -> Unit, visit: (TemplatePrivateCapture, Any?) -> Unit) {
        if (!mapRoles.getOrPut(items) { hashSetOf() }.add(role)) return
        val mutable = items.javaClass in mapClasses
        check(mutable || items.javaClass in immutableMapClasses) {
            "Template checkpoint cannot capture map ${items.javaClass.name}"
        }
        // A list of entries avoids hashing a key before its entire supported shape is checked.
        val contents = items.entries.map { it.key to it.value }
        contents.forEach { (name, value) ->
            validateMapEntry(role, name, value)
            key(name)
        }
        preparationFact(
            "map.${role.name}",
            RawPreparationFact.identity(items),
            RawPreparationFact.unordered(
                contents.map { (name, value) ->
                    RawPreparationFact.row(
                        if (role == MapRole.COMPILED_TEMPLATES) RawPreparationFact.identity(name)
                        else RawPreparationFact.scalar(name),
                        if (role == MapRole.IMPORTS) RawPreparationFact.scalar(value)
                        else RawPreparationFact.identity(value)
                    )
                }
            )
        )
        contents.forEach { (_, value) -> visit(this, value) }
        roots.add { it.map(items, role, key, visit) }
        element(items)
        if (visited.add(items) && mutable) {
            @Suppress("UNCHECKED_CAST")
            val target = items as MutableMap<Any?, Any?>
            restores.add {
                target.clear()
                contents.forEach { (name, value) -> target[name] = value }
            }
        }
    }

    fun set(items: Set<*>, role: SetRole, visit: (TemplatePrivateCapture, Any?) -> Unit) {
        if (!setRoles.getOrPut(items) { hashSetOf() }.add(role)) return
        check(items.javaClass in setClasses) { "Template checkpoint cannot capture set ${items.javaClass.name}" }
        val contents = items.toList()
        contents.forEach { validateSetElement(role, it) }
        preparationFact(
            "set.${role.name}",
            RawPreparationFact.identity(items),
            RawPreparationFact.unordered(
                contents.map {
                    if (role == SetRole.STRINGS) RawPreparationFact.scalar(it) else RawPreparationFact.identity(it)
                }
            )
        )
        contents.forEach { visit(this, it) }
        roots.add { it.set(items, role, visit) }
        element(items)
        if (!visited.add(items)) return
        @Suppress("UNCHECKED_CAST")
        val target = items as MutableSet<Any?>
        restores.add {
            target.clear()
            target.addAll(contents)
        }
    }

    private fun validateListElement(role: ListRole, value: Any?) {
        val valid = when (role) {
            ListRole.REFERENCES -> value is DataTemplate
            ListRole.ORIGINS -> value is CompoundDataScope.FieldOrigin
            ListRole.ANNOTATIONS -> value is top.mcfpp.model.annotation.Annotation
            ListRole.CONSTRUCTORS -> value is DataTemplateConstructor
            ListRole.READONLY_PARAMETERS -> value is DataTemplateParam
            ListRole.TYPE_IDS -> value is TypeId
        }
        check(valid) {
            if (role == ListRole.CONSTRUCTORS) "Template checkpoint found an invalid constructor"
            else "Template checkpoint found an invalid $role list element"
        }
    }

    private fun validateMapEntry(role: MapRole, key: Any?, value: Any?) {
        val validKey = if (role == MapRole.COMPILED_TEMPLATES) key is SpecializationKey else key is String
        val validValue = when (role) {
            MapRole.FIELD_REQUIREMENTS, MapRole.PENDING_ANNOTATIONS -> value is List<*>
            MapRole.RESTORED_EFFECTS -> value is DataTemplate.CompletedInheritedField
            MapRole.INITIALIZERS -> value is mcfppParser.ExpressionContext
            MapRole.DEFERRED_FIELDS -> value is DataTemplate.DeferredFieldDeclaration
            MapRole.IMPORTS -> value is String
            MapRole.COMPILED_TEMPLATES -> value is CompiledGenericDataTemplate
        }
        check(validKey) { "Template checkpoint found an invalid $role map key" }
        check(validValue) { "Template checkpoint found an invalid $role map value" }
    }

    private fun validateSetElement(role: SetRole, value: Any?) {
        val valid = when (role) {
            SetRole.PARENTS -> value is CompoundData
            SetRole.STRINGS -> value is String
        }
        check(valid) { "Template checkpoint found an invalid $role set element" }
    }

    fun variable(value: Var<*>) = state(value) { graph ->
        // This final adapter slot is safe to read without consulting its open computed type getter.
        graph.list(value.annotations, ListRole.ANNOTATIONS) { g, item -> g.element(item as top.mcfpp.model.annotation.Annotation) }
    }

    fun origin(value: CompoundDataScope.FieldOrigin) {
        element(value)
        element(value.owner)
        element(value.typeId)
        variable(value.field)
        preparationFact(
            "origin", RawPreparationFact.identity(value), RawPreparationFact.identity(value.owner),
            RawPreparationFact.identity(value.field), RawPreparationFact.identity(value.property),
            RawPreparationFact.closed(value.typeId), RawPreparationFact.scalar(value.nullable),
            RawPreparationFact.scalar(value.isStatic), RawPreparationFact.scalar(value.isConst),
            RawPreparationFact.scalar(value.access.name), RawPreparationFact.scalar(value.isFinal)
        )
        value.property?.let(::property)
    }

    fun property(value: Property) = state(value) { graph ->
        graph.preparationFact(
            "property", RawPreparationFact.identity(value), RawPreparationFact.scalar(value.identifier),
            RawPreparationFact.identity(value.parent), RawPreparationFact.identity(value.declaredParentTemplate),
            RawPreparationFact.identity(value.accessor), RawPreparationFact.identity(value.mutator),
            RawPreparationFact.scalar(value.accessModifier.name), RawPreparationFact.scalar(value.isStatic),
            RawPreparationFact.scalar(value.isFinal), RawPreparationFact.scalar(value.isInherited)
        )
        value.parent?.let(graph::variable)
        value.declaredParentTemplate?.let(graph::element)
        value.accessor?.let { accessor ->
            graph.element(accessor)
            when (accessor.javaClass) {
                FunctionAccessor::class.java -> graph.element((accessor as FunctionAccessor).function)
                NativeAccessor::class.java -> graph.element((accessor as NativeAccessor).function)
                SimpleAccessor::class.java -> Unit
                else -> error("Template checkpoint cannot capture getter ${accessor.javaClass.name}")
            }
        }
        value.mutator?.let { mutator ->
            graph.element(mutator)
            when (mutator.javaClass) {
                FunctionMutator::class.java -> graph.element((mutator as FunctionMutator).function)
                NativeMutator::class.java -> graph.element((mutator as NativeMutator).function)
                SimpleMutator::class.java, top.mcfpp.model.property.AnonymousNativeMutator::class.java -> Unit
                else -> error("Template checkpoint cannot capture setter ${mutator.javaClass.name}")
            }
        }
    }

    companion object {
        private val listClasses: Set<Class<*>> = setOf(java.util.ArrayList::class.java, java.util.LinkedList::class.java)
        private val fixedListClass = java.util.Arrays.asList(1, 2).javaClass
        private val immutableListClasses: Set<Class<*>> = setOf(
            emptyList<Any>().javaClass,
            listOf(1).javaClass,
            java.util.Collections.emptyList<Any>().javaClass,
            java.util.List.of(1).javaClass,
            java.util.List.of(1, 2, 3).javaClass
        )
        private val mapClasses: Set<Class<*>> = setOf(java.util.HashMap::class.java, java.util.LinkedHashMap::class.java)
        private val immutableMapClasses: Set<Class<*>> = setOf(
            emptyMap<Any, Any>().javaClass,
            mapOf(1 to 2).javaClass,
            java.util.Collections.emptyMap<Any, Any>().javaClass,
            java.util.Map.of(1, 2).javaClass,
            java.util.Map.of(1, 2, 3, 4).javaClass
        )
        private val setClasses: Set<Class<*>> = setOf(
            java.util.HashSet::class.java,
            java.util.LinkedHashSet::class.java,
            java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>()).javaClass
        )
    }
}
