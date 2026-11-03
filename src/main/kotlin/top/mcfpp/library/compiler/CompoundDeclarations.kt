package top.mcfpp.library.compiler

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.backend.NbtEncoding
import top.mcfpp.core.lang.MCNumber
import top.mcfpp.core.lang.OnScoreboard
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.Member
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum as CompilerEnum
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.*
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*

interface CompoundCaptureBindings : FunctionCaptureBindings {
    fun id(value: Any, owner: DeclarationId?): DeclarationId
    fun reference(value: Any): DeclarationReference
    fun compoundKey(value: Any): DeclarationKey?
    fun compoundOrigin(value: Any): AnonymousOrigin? = null
    fun compoundOwner(value: Any): DeclarationId? = null
    fun compoundName(value: Any, name: String): String = name
    fun compoundEnvironment(value: Any): EnvironmentId
    fun compoundAnnotations(value: Any): List<Annotation> = when (value) {
        is DataTemplate -> value.annotations
        is Var<*> -> value.annotations
        is CompilerEnum -> value.annotations
        else -> emptyList()
    }
    fun identity(value: top.mcfpp.type.TypeId): TypeRef
    fun dependentType(owner: DeclarationId, syntax: NodeRef): TypeRef
    fun <T> inCompound(value: DataTemplate, owner: DeclarationId, action: () -> T): T
    fun declarationContext(value: DataTemplate): ParserRuleContext? = (value as? GenericDataTemplate)?.ctx?.parent as? ParserRuleContext
    fun storeTemplate(owner: DeclarationId, node: TemplateBody)
    fun preparedParents(value: DataTemplate): List<ParentRecord>? = null
    /** A source capture retains this snapshot so its copied annotation identities stay stable. */
    fun inheritedFields(value: DataTemplate): List<SourceInheritedField> = value.completedInheritedFields().map {
        SourceInheritedField(it.origin, it.field, it.property)
    }
    /** FieldOrigin can retain a parent's effective copy while owner still identifies the original slot. */
    fun inheritedSlot(owner: CompoundData, field: Var<*>): DeclarationReference = reference(field)
    fun sourceFunction(value: Function): Function = value
    /** Generated providers can supply an explicit semantic order when no source body exists. */
    fun memberOrder(value: DataTemplate, members: List<Pair<Any, DeclarationId>>): List<DeclarationId>? = null
}

data class SourceInheritedField(val origin: TypeId, val field: Var<*>, val property: Property?)

data class CapturedCompound(val id: DeclarationId, val declaration: CompoundDeclaration, val declarations: Map<DeclarationId, LibraryDeclaration>)
class CompoundShell(val id: DeclarationId, val declaration: LibraryDeclaration, initial: Any? = null) {
    var value: Any? = initial
        internal set
    var isBound: Boolean = false
        internal set
}
data class BoundCompound(val value: DataTemplate, val deferredParents: List<ParentRecord>)

interface CompoundRestoreBindings {
    fun declaration(reference: DeclarationId): LibraryDeclaration
    fun shell(reference: DeclarationId): CompoundShell
    fun reference(reference: DeclarationReference): Any
    fun type(reference: TypeRef): MCFPPType
    fun constant(reference: ConstantRef): RestoredDefault
    fun environment(reference: EnvironmentId): MCFPPFile
    fun annotation(reference: AnnotationId): Annotation
    /** The provider resolves the unique owned TemplateBody root and projects it without parsing text. */
    fun template(owner: DeclarationId): Parser.TemplateBodyContext
    fun expression(body: BodyId): Parser.ExpressionContext? = null
    fun block(body: BodyId): Parser.CurlBlockContext? = null
    fun defaultValue(body: BodyId): Parser.ValueContext? = null
    fun allocated(shell: CompoundShell) {}
    fun initializer(owner: DeclarationId, field: DeclarationId, body: BodyId) {}
    fun deferredParents(owner: DeclarationId, parents: List<ParentRecord>) {}
    fun bound(shell: CompoundShell) {}
}

object CompoundDeclarations {
    fun capture(template: DataTemplate, id: DeclarationId, bindings: CompoundCaptureBindings): CapturedCompound {
        require(template !is CompiledGenericDataTemplate) { "Specialized templates are captured through their origin and typed arguments" }
        val environment = bindings.compoundEnvironment(template)
        val statements = StatementBridge(bindings)
        val records = linkedMapOf<DeclarationId, LibraryDeclaration>()
        val context = bindings.declarationContext(template)
        val sourceBody = sourceBody(context) ?: (template as? GenericDataTemplate)?.ctx
        return bindings.inCompound(template, id) {
            val readonlySource = sourceReadonly(context)?.readOnlyParameterList()?.readOnlyParameter().orEmpty()
            val readonly = (template as? GenericDataTemplate)?.readOnlyParams.orEmpty().mapIndexed { index, parameter ->
                val parameterId = bindings.id(parameter, id)
                val source = readonlySource.getOrNull(index)
                val declared = source?.type()?.let { bindings.storeNode(parameterId, statements.type(it)) }
                val type = parameter.type?.let { bindings.type(it, id, declared) }
                    ?: declared?.let { bindings.dependentType(id, it) }
                    ?: bindings.type(MCFPPConcreteType.Type, id, null)
                val default = source?.value()?.let { value ->
                    val root = bindings.withScope(value, parameterId) { statements.expression(value) }
                    bindings.storeBody(SyntaxBodySource(parameterId, environment, BodyRole.DEFAULT, root))
                }
                val common = common(parameter, parameter.identifier, id, environment, DeclarationAccess.PRIVATE, 0, emptyList(), bindings)
                records[parameterId] = ValueDeclaration(common, ValueRole.READONLY_PARAMETER, ValueStorage.PARAMETER,
                    type, declared, default, null, when (parameter.variance) {
                        top.mcfpp.model.compound.DeclarationVariance.INVARIANT -> Variance.INVARIANT
                        top.mcfpp.model.compound.DeclarationVariance.IN -> Variance.IN
                        top.mcfpp.model.compound.DeclarationVariance.OUT -> Variance.OUT
                    }, index.toLong())
                // A template readonly parameter is a value binding with its own D, never a name-only T identity.
                bindings.publishLocal(parameterId, parameter.identifier, source ?: context ?: ParserRuleContext())
                parameterId
            }
            val sourceFields = sourceBody?.templateMemberDeclaration().orEmpty().mapNotNull { it.templateMember().templateFieldDeclaration() }
                .associateBy { it.Identifier().text }
            val members = mutableListOf<Pair<Any, DeclarationId>>()
            val accessorTargets = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
            template.scope.allProperties.filter { it.declaredParentTemplate === template }.forEach { value ->
                when (val accessor = value.accessor) { is FunctionAccessor -> accessorTargets.add(accessor.function); is NativeAccessor -> accessorTargets.add(accessor.function) }
                when (val mutator = value.mutator) { is FunctionMutator -> accessorTargets.add(mutator.function); is NativeMutator -> accessorTargets.add(mutator.function) }
            }
            fun field(value: Var<*>, owner: DeclarationId, source: Parser.TemplateFieldDeclarationContext? = null): DeclarationId {
                val fieldId = bindings.id(value, owner)
                if (fieldId !in records) records[fieldId] = captureField(value, fieldId, owner, environment, template, source, statements, bindings)
                return fieldId
            }
            template.scope.allVars.filter { it.declaredParentTemplate === template }.forEach { members += it to field(it, id, sourceFields[it.identifier]) }
            fun captureFunction(function: Function): DeclarationReference {
                val source = bindings.sourceFunction(function)
                val reference = bindings.reference(source)
                if (source.owner === template && reference is DeclarationReference.Local && reference.target !in records) {
                    val captured = FunctionDeclarations.capture(source, reference.target, bindings)
                    records[reference.target] = captured.declaration
                    records.putAll(captured.parameters)
                }
                return reference
            }
            template.scope.forEachFunction { function ->
                if (function.owner === template && !function.actualCallBody && function !is DataTemplateConstructor && function !is ExtensionFunction && function !in accessorTargets) {
                    val reference = captureFunction(function)
                    if (reference is DeclarationReference.Local) members += function to reference.target
                }
            }
            fun property(value: Property, owner: DeclarationId): DeclarationId {
                val propertyId = bindings.id(value, owner)
                if (propertyId !in records) {
                    fun accessorFunction(function: Function): DeclarationReference {
                        val target = captureFunction(function)
                        if (target is DeclarationReference.Local && value.declaredParentTemplate === template && function.owner === template) {
                            val record = records.getValue(target.target) as FunctionDeclaration
                            records[target.target] = record.copy(common = record.common.copy(key = null, owner = propertyId, access = DeclarationAccess.PRIVATE))
                        }
                        return target
                    }
                    val backing = template.scope.getVar(value.identifier)?.takeIf { it.declaredParentTemplate === template }?.let(bindings::reference)
                    val getter = when (val accessor = value.accessor) {
                        null -> LibraryAccessor.Absent; is SimpleAccessor -> LibraryAccessor.Simple
                        is FunctionAccessor -> LibraryAccessor.Function(accessorFunction(accessor.function))
                        is NativeAccessor -> LibraryAccessor.Native(accessorFunction(accessor.function))
                        else -> unsupported("Unsupported property getter")
                    }
                    val setter = when (val mutator = value.mutator) {
                        null -> LibraryAccessor.Absent; is SimpleMutator -> LibraryAccessor.Simple
                        is FunctionMutator -> LibraryAccessor.Function(accessorFunction(mutator.function))
                        is NativeMutator -> LibraryAccessor.Native(accessorFunction(mutator.function))
                        else -> unsupported("Unsupported property setter")
                    }
                    records[propertyId] = PropertyDeclaration(common(value, value.identifier, owner, environment,
                        access(value.accessModifier), flags(value.isFinal, value.isStatic), emptyList(), bindings), backing, getter, setter)
                }
                return propertyId
            }
            template.scope.allProperties.filter { it.declaredParentTemplate === template }.forEach { members += it to property(it, id) }
            template.scope.operators.forEach { (operator, overloads) -> overloads.forEach { (operand, implementation) ->
                if (implementation.owner === template && !implementation.actualCallBody) {
                    val operatorId = bindings.id(OperatorSlot(template, operator, operand), id)
                    val target = captureFunction(implementation)
                    if (target is DeclarationReference.Local && template.scope.functions.values.flatten().none { it === implementation }) {
                        val record = records.getValue(target.target) as FunctionDeclaration
                        records[target.target] = record.copy(common = record.common.copy(key = null, owner = operatorId, access = DeclarationAccess.PRIVATE))
                    }
                    records[operatorId] = OperatorDeclaration(common(implementation, operator, id, environment,
                        access(implementation.accessModifier), 0, emptyList(), bindings).copy(key = null), operator,
                        operand?.let { bindings.type(it, id, null) }, target)
                    members += OperatorSlot(template, operator, operand) to operatorId
                }
            } }
            template.constructors.forEach { constructor ->
                val constructorId = bindings.id(constructor, id)
                val captured = captureConstructor(constructor, constructorId, id, environment, bindings)
                records.putAll(captured)
                members += constructor to constructorId
            }
            val parents = bindings.preparedParents(template) ?: run {
                val source = sourceParents(context)
                if (source.isNotEmpty()) source.mapIndexed { index, parent ->
                    val name = parent.className()
                    val base = TypeSyntax.Name(bindings.scope(parent), bindings.name(name.classWithoutNamespace().Identifier().text,
                        name.namespacePath()?.text, NameRole.TYPE, name))
                    val tree = parent.readOnlyArgs()?.let { TypeSyntax.Apply(bindings.scope(parent), base,
                        it.expressionList()?.expression().orEmpty().map(statements::expression)) } ?: base
                    val closed = template.parent.filterIsInstance<DataTemplate>().takeIf { it.size == source.size }?.get(index)
                    ParentRecord(closed?.let { bindings.type(it.getType(), id, null) }, bindings.storeNode(id, tree))
                } else {
                    if (template.parentID.isNotEmpty()) unsupported("Parent headers require source contexts or prepared structured parents")
                    if (template.pendingParentTypeIds.isNotEmpty()) template.pendingParentTypeIds.map { ParentRecord(bindings.identity(it), null) }
                    else template.parent.filterIsInstance<DataTemplate>().map { ParentRecord(bindings.type(it.getType(), id, null), null) }
                }
            }
            val inherited = bindings.inheritedFields(template).map { effect ->
                val slot = template.scope.fieldOrigins[effect.field.identifier]?.singleOrNull()
                    ?: unsupported("Completed inherited effect has no unique originating slot")
                val effectField = field(effect.field, id)
                val effectProperty = effect.property?.let { property(it, id) }
                fun getterOwner(property: Property?): TypeRef? = when (val accessor = property?.accessor) {
                    is FunctionAccessor -> accessor.function.owner; is NativeAccessor -> accessor.function.owner; else -> null
                }?.let { bindings.identity(it.inheritanceIdentity) }
                fun setterOwner(property: Property?): TypeRef? = when (val mutator = property?.mutator) {
                    is FunctionMutator -> mutator.function.owner; is NativeMutator -> mutator.function.owner; else -> null
                }?.let { bindings.identity(it.inheritanceIdentity) }
                InheritedEffect(bindings.identity(effect.origin), bindings.inheritedSlot(slot.owner, slot.field), effectField, effectProperty,
                    getterOwner(effect.property), setterOwner(effect.property))
            }
            // Accessor targets are ordinary D records but retain separate identities from same-signature methods.
            val inheritedRecords = inherited.flatMap { listOfNotNull(it.field, it.property) }.toSet()
            val ownedRecords = records.filter { (key, record) -> record.common.owner == id &&
                (record !is ValueDeclaration || record.role == ValueRole.MEMBER) && key !in inheritedRecords }.keys
            val memberIds = (members.map { it.second } + ownedRecords).distinct()
            val ordered = bindings.memberOrder(template, members)?.also { require(it.toSet() == memberIds.toSet() && it.distinct().size == it.size) }
                ?: orderMembers(sourceBody, template, members, memberIds)
            sourceBody?.let { bindings.storeTemplate(id, statements.template(it)) }
            if (template is GenericDataTemplate && sourceBody == null) unsupported("Generic template requires a structural member stream")
            val category = when {
                template is UnionDataTemplate || UnionDataTemplate.isIntersectionIdentity(template.namespace, template.identifier) -> CompoundCategory.INTERSECTION
                template is TypeDataTemplate -> CompoundCategory.TYPE_AS
                template is ObjectCompoundData -> CompoundCategory.OBJECT
                context is Parser.AnonymousTemplateTypeContext || (bindings.compoundOrigin(template) != null && bindings.compoundKey(template) == null) -> CompoundCategory.ANONYMOUS
                else -> CompoundCategory.DATA
            }
            val common = common(template, template.identifier, null, environment, DeclarationAccess.PUBLIC,
                flags(template.isFinal, false) or if (template.isAbstract) DeclarationFlags.ABSTRACT else 0,
                bindings.compoundAnnotations(template).map { bindings.annotation(id, it) }, bindings)
            val declaration = CompoundDeclaration(common, category, readonly, parents,
                (template as? TypeDataTemplate)?.let { bindings.type(it.typeAs, id, null) }, memberIds, ordered,
                template.companionObject?.let { bindings.id(it, null) }, inherited)
            records[id] = declaration
            CapturedCompound(id, declaration, records.toMap())
        }
    }

    data class OperatorSlot(val owner: DataTemplate, val operator: String, val operand: MCFPPType?)

    private fun captureField(value: Var<*>, id: DeclarationId, owner: DeclarationId, environment: EnvironmentId,
                             template: DataTemplate, source: Parser.TemplateFieldDeclarationContext?, statements: StatementBridge,
                             bindings: CompoundCaptureBindings): ValueDeclaration {
        val typeContext = source?.templateType()?.singleTemplateFieldType()?.type()
        val declaredTree = typeContext?.let(statements::type) ?: source?.templateType()?.unionTemplateFieldType()?.let {
            TypeSyntax.Union(bindings.scope(it), it.type().map(statements::type))
        }
        val declared = declaredTree?.let { bindings.storeNode(id, it) }
        if (value.type is UnresolvedType && declared == null) unsupported("Dependent field requires a structured declared type")
        val initializer = if (value.declaredParentTemplate === template) template.preInit[value.identifier] ?: source?.expression() else null
        val body = initializer?.let { context -> bindings.withScope(context, id) {
            bindings.storeBody(SyntaxBodySource(id, environment, BodyRole.INITIALIZER, statements.expression(context)))
        } }
        val readonly = value.symbol?.readonly == true
        val frozen = if (readonly) bindings.constant(requireNotNull(StorageAccess.snapshot(value)), StorageAccess.boundTypes(value)) else null
        val dataOnly = (value as? OnScoreboard)?.isDataOnly ?: (value as? MCNumber<*>)?.isDataOnly ?: false
        val flags = flags(value.isFinal, value.isStatic) or (if (value.isConst) DeclarationFlags.CONST else 0) or
            (if (value.nullable) DeclarationFlags.NULLABLE else 0) or (if (value.symbol?.forceRuntime == true) DeclarationFlags.FORCE_RUNTIME else 0) or
            (if (dataOnly) DeclarationFlags.DATA_ONLY else 0)
        return ValueDeclaration(common(value, value.identifier, owner, environment, access(value.accessModifier), flags,
            bindings.compoundAnnotations(value).map { bindings.annotation(id, it) }, bindings), ValueRole.MEMBER,
            if (value.isConst) ValueStorage.CONST else if (value.symbol?.forceRuntime == true) ValueStorage.EXTERNAL else ValueStorage.VAR,
            bindings.type(value.type, owner, declared), declared, body, frozen)
    }

    private fun captureConstructor(constructor: DataTemplateConstructor, id: DeclarationId, owner: DeclarationId,
                                   environment: EnvironmentId, bindings: CompoundCaptureBindings): Map<DeclarationId, LibraryDeclaration> {
        val records = linkedMapOf<DeclarationId, LibraryDeclaration>()
        val syntax = (constructor.ast?.parent as? Parser.TemplateConstructorDeclarationContext)?.normalParams()?.parameterList()?.parameter().orEmpty()
        val statements = StatementBridge(bindings)
        return bindings.inFunction(constructor, id, environment) {
            val normal = constructor.normalParams.mapIndexed { index, parameter ->
                val parameterId = bindings.reserveParameter(id, parameter, false, index)
                val declared = syntax.getOrNull(index)?.type()?.let { bindings.storeNode(parameterId, statements.type(it)) }
                if (parameter.type is UnresolvedType && declared == null) unsupported("Constructor parameter requires its structured type")
                val default = parameter.defaultContext ?: syntax.getOrNull(index)?.value()
                val body = default?.takeIf { parameter.hasDefault }?.let { context -> bindings.withScope(context, parameterId) {
                    bindings.storeBody(SyntaxBodySource(parameterId, environment, BodyRole.DEFAULT, statements.expression(context)))
                } }
                val frozen = parameter.defaultValue?.takeIf { parameter.hasDefault }?.let { bindings.constant(it, parameter.defaultTypes) }
                require(!parameter.hasDefault || body != null || frozen != null)
                records[parameterId] = ValueDeclaration(DeclCommon(null, null, parameter.identifier, id, environment, DeclarationAccess.PRIVATE, 0),
                    ValueRole.NORMAL_PARAMETER, ValueStorage.PARAMETER, bindings.type(parameter.type, id, declared), declared, body, frozen, parameterIndex = index.toLong())
                bindings.publishParameter(parameterId, parameter, false, index)
                parameterId
            }
            val intrinsic = if (constructor is TypeDataTemplateConstructor) ConstructorIntrinsic.TYPE_AS else ConstructorIntrinsic.USER_BODY
            val root = constructor.ast?.let(statements::block) ?: bindings.preparedBody(constructor)
                ?: if (intrinsic == ConstructorIntrinsic.USER_BODY && constructor.normalParams.isEmpty())
                    bindings.withScope(ParserRuleContext(), id) { Statement.Block(it, emptyList()) } else null
            val body = if (intrinsic == ConstructorIntrinsic.USER_BODY) bindings.storeBody(SyntaxBodySource(id, environment,
                BodyRole.CONSTRUCTOR, root ?: unsupported("User constructor requires a structural body"))) else null
            records[id] = ConstructorDeclaration(common(constructor, constructor.identifier, owner, environment,
                access(constructor.accessModifier), 0, constructor.annotations.map { bindings.annotation(id, it) }, bindings), normal, body, intrinsic)
            records.toMap()
        }
    }

    fun captureEnum(value: CompilerEnum, id: DeclarationId, bindings: CompoundCaptureBindings): Map<DeclarationId, LibraryDeclaration> {
        val environment = bindings.compoundEnvironment(value)
        val records = linkedMapOf<DeclarationId, LibraryDeclaration>()
        val members = value.members.values.sortedWith(compareBy<EnumMember> { it.value }.thenBy { it.identifier }).map { member ->
            val memberId = bindings.id(member, id)
            records[memberId] = EnumMemberDeclaration(common(member, member.identifier, id, environment, DeclarationAccess.PUBLIC, 0,
                emptyList(), bindings), member.value.toLong(), bindings.constant(CompilerValue.Nbt(NbtEncoding.snbt(member.data)), emptyMap()))
            memberId
        }
        records[id] = EnumDeclaration(common(value, value.identifier, null, environment, DeclarationAccess.PUBLIC, 0,
            bindings.compoundAnnotations(value).map { bindings.annotation(id, it) }, bindings), members)
        return records.toMap()
    }

    fun captureAlias(alias: MCFPPTypeAliasType, name: String, id: DeclarationId, environment: EnvironmentId,
                     bindings: CompoundCaptureBindings): TypeAliasDeclaration {
        val declared = bindings.storeNode(id, StatementBridge(bindings).type(alias.typeContext))
        val target = alias.cachedTarget ?: unsupported("Resolve the alias through its source context before capture")
        return TypeAliasDeclaration(common(alias, name, null, environment, DeclarationAccess.PUBLIC, 0, emptyList(), bindings),
            bindings.type(target, id, declared), declared)
    }

    fun allocateShell(id: DeclarationId, declaration: LibraryDeclaration, bindings: CompoundRestoreBindings): CompoundShell {
        val name = declaration.common.name
        val namespace = declaration.common.key?.namespace ?: bindings.environment(declaration.common.environment).namespace.identifier
        val value: Any? = when (declaration) {
            is CompoundDeclaration -> when {
                declaration.category == CompoundCategory.INTERSECTION -> {
                    require(declaration.readonly.isEmpty() && declaration.parents.all { it.type != null }) { "Intersection shell requires its canonical closed component shells" }
                    UnionDataTemplate(emptyList()).apply { identifier = name }
                }
                declaration.readonly.isNotEmpty() && declaration.category == CompoundCategory.OBJECT -> GenericObjectDataTemplate(bindings.template(id), name, namespace)
                declaration.readonly.isNotEmpty() -> GenericDataTemplate(bindings.template(id), name, namespace)
                declaration.category == CompoundCategory.OBJECT -> ObjectDataTemplate(name, namespace)
                declaration.category == CompoundCategory.TYPE_AS -> TypeDataTemplate(MCFPPPrivateType.Void, name, namespace)
                else -> DataTemplate(name, namespace)
            }
            is EnumDeclaration -> {
                val key = "$namespace:$name"
                val previous = GlobalScope.scoreboards[key]
                CompilerEnum(name, namespace).also {
                    if (previous == null) GlobalScope.scoreboards.remove(key) else GlobalScope.scoreboards[key] = previous
                }
            }
            else -> null
        }
        return CompoundShell(id, declaration, value).also(bindings::allocated)
    }

    fun bind(shell: CompoundShell, bindings: CompoundRestoreBindings): Any {
        if (shell.isBound) return requireNotNull(shell.value)
        val declaration = shell.declaration
        val common = declaration.common
        val owner = common.owner?.let { bindings.shell(it).value as? DataTemplate }
        val value: Any = when (declaration) {
            is CompoundDeclaration -> {
                val template = shell.value as DataTemplate
                template.declarationFile = bindings.environment(common.environment); template.declarationEnvironment = null
                template.isAbstract = common.flags and DeclarationFlags.ABSTRACT != 0L
                template.isFinal = common.flags and DeclarationFlags.FINAL != 0L
                template.scope.parent.clear()
                template.annotations.addAll(common.annotations.map(bindings::annotation))
                if (template is TypeDataTemplate) template.typeAs = bindings.type(requireNotNull(declaration.typeAs))
                if (template is GenericDataTemplate) declaration.readonly.forEach { parameterId ->
                    val record = bindings.declaration(parameterId) as ValueDeclaration
                    val parameter = DataTemplateParam(record.common.name, record.common.name,
                        record.type?.let(bindings::type), when (record.variance) {
                            Variance.INVARIANT -> top.mcfpp.model.compound.DeclarationVariance.INVARIANT
                            Variance.IN -> top.mcfpp.model.compound.DeclarationVariance.IN
                            Variance.OUT -> top.mcfpp.model.compound.DeclarationVariance.OUT
                        })
                    template.readOnlyParams += parameter
                    bindings.shell(parameterId).value = parameter
                }
                val deferred = declaration.parents.filter { it.type == null }
                if (!template.hasStructuredParentBinding) declaration.parents.mapNotNull { it.type }.forEach { type ->
                    val parent = bindings.type(type) as? MCFPPDataTemplateType ?: error("Parent is not a data template")
                    if (parent.template !in template.parent) template.extends(parent.template)
                    else if (parent.template.scope !in template.scope.parent) template.scope.parent.add(parent.template.scope)
                }
                template.scope.parent.add(template.declarationFile!!.field)
                declaration.memberOrder.forEach { memberId ->
                    val record = bindings.declaration(memberId)
                    val member = requireNotNull(bindings.shell(memberId).value) { "Bind member $memberId before its owning compound" }
                    when (record) {
                        is ValueDeclaration -> if (record.role == ValueRole.MEMBER) {
                            val field = member as Var<*>
                            field.declaredParentTemplate = template
                            template.scope.putVar(commonName(record), field, true)
                            record.defaultBody?.let { body ->
                                bindings.initializer(shell.id, memberId, body)
                                bindings.expression(body)?.let { template.preInit[field.identifier] = it }
                            }
                        }
                        is ConstructorDeclaration -> template.constructors.add(member as DataTemplateConstructor)
                        is FunctionDeclaration -> template.scope.addFunction(member as Function, true)
                        is PropertyDeclaration -> template.scope.putProperty(record.common.name, member as Property, true)
                        is OperatorDeclaration -> template.scope.addOperator(record.operator, record.operand?.let(bindings::type), member as Function, true)
                    }
                }
                template.companionObject = declaration.companion?.let { bindings.shell(it).value as DataTemplate }
                val effects = declaration.inherited.map { effect ->
                    val origin = bindings.type(effect.origin)
                    val original = bindings.reference(effect.slot) as? Var<*> ?: error("Inherited slot is not a field")
                    val field = bindings.shell(effect.field).value as Var<*>
                    require(original.identifier == field.identifier) { "Inherited effect refers to a different originating slot" }
                    val property = effect.property?.let { bindings.shell(it).value as Property }
                    fun getter(function: Function) { effect.getterOwner?.let { function.owner = bindings.type(it).instanceData } }
                    fun setter(function: Function) { effect.setterOwner?.let { function.owner = bindings.type(it).instanceData } }
                    when (val accessor = property?.accessor) { is FunctionAccessor -> getter(accessor.function); is NativeAccessor -> getter(accessor.function) }
                    when (val mutator = property?.mutator) { is FunctionMutator -> setter(mutator.function); is NativeMutator -> setter(mutator.function) }
                    DataTemplate.CompletedInheritedField(origin.typeId, field, property)
                }
                template.restoreInheritedFields(effects)
                bindings.deferredParents(shell.id, deferred)
                template
            }
            is ValueDeclaration -> restoreField(declaration, owner, bindings)
            is ConstructorDeclaration -> {
                val template = requireNotNull(owner)
                val constructor = if (declaration.intrinsic == ConstructorIntrinsic.TYPE_AS)
                    TypeDataTemplateConstructor(template as TypeDataTemplate) else DataTemplateConstructor(template, declaration.body?.let(bindings::block))
                constructor.identifier = common.name
                constructor.declarationFile = bindings.environment(common.environment); constructor.declarationEnvironment = null
                constructor.accessModifier = access(common.access)
                declaration.normal.forEach { parameterId ->
                    val parameter = bindings.declaration(parameterId) as ValueDeclaration
                    val restored = FunctionParam(bindings.type(requireNotNull(parameter.type)), parameter.common.name, constructor,
                        parameter.defaultBody != null || parameter.frozen != null).apply {
                        parameter.frozen?.let { bindings.constant(it).let { value -> defaultValue = value.value; defaultTypes = value.types } }
                        defaultContext = parameter.defaultBody?.let(bindings::defaultValue)
                    }
                    constructor.normalParams += restored
                    bindings.shell(parameterId).value = restored
                }
                constructor.buildParamVar()
                constructor
            }
            is PropertyDeclaration -> {
                val getter = when (val accessor = declaration.getter) {
                    LibraryAccessor.Absent -> null; LibraryAccessor.Simple -> SimpleAccessor()
                    is LibraryAccessor.Function -> FunctionAccessor(bindings.reference(accessor.target) as Function)
                    is LibraryAccessor.Native -> NativeAccessor(bindings.reference(accessor.target) as NativeFunction)
                }
                val setter = when (val mutator = declaration.setter) {
                    LibraryAccessor.Absent -> null; LibraryAccessor.Simple -> SimpleMutator()
                    is LibraryAccessor.Function -> FunctionMutator(bindings.reference(mutator.target) as Function)
                    is LibraryAccessor.Native -> NativeMutator(bindings.reference(mutator.target) as NativeFunction)
                }
                Property(common.name, getter, setter).apply {
                    accessModifier = access(common.access); isFinal = common.flags and DeclarationFlags.FINAL != 0L
                    isStatic = common.flags and DeclarationFlags.STATIC != 0L; declaredParentTemplate = owner
                }
            }
            is OperatorDeclaration -> bindings.reference(declaration.implementation) as Function
            is EnumDeclaration -> (shell.value as CompilerEnum).also { enumeration ->
                enumeration.annotations.addAll(common.annotations.map(bindings::annotation))
                declaration.members.forEach { enumeration.addMember(bindings.shell(it).value as EnumMember) }
            }
            is EnumMemberDeclaration -> {
                require(declaration.ordinal in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Enum ordinal exceeds the current compiler's integer domain" }
                val data = bindings.constant(declaration.data).value as? CompilerValue.Nbt ?: error("Enum data must be canonical NBT")
                EnumMember(common.name, declaration.ordinal.toInt(), NbtEncoding.parse(data.snbt))
            }
            is TypeAliasDeclaration -> bindings.type(declaration.target)
            else -> error("Not a compound declaration kind ${declaration.tag}")
        }
        shell.value = value
        shell.isBound = true
        bindings.bound(shell)
        return value
    }

    private fun restoreField(record: ValueDeclaration, owner: DataTemplate?, bindings: CompoundRestoreBindings): Var<*> {
        val type = bindings.type(requireNotNull(record.type))
        val frozen = record.frozen?.let(bindings::constant)
        val value = if (frozen == null) type.buildUnConcrete(record.common.name) else {
            requireNotNull(StorageAccess.restore(type, frozen.value, record.common.name, frozen.types)).also {
                requireNotNull(StorageAccess.freezeReadonly(it, record.common.name))
            }
        }
        if (type is MCFPPGenericDataTemplateType && value.type.typeId == type.typeId.constructor) value.type = type
        value.accessModifier = access(record.common.access); value.declaredParentTemplate = owner
        value.isFinal = record.common.flags and DeclarationFlags.FINAL != 0L
        value.isStatic = record.common.flags and DeclarationFlags.STATIC != 0L
        value.isConst = record.common.flags and DeclarationFlags.CONST != 0L
        value.nullable = record.common.flags and DeclarationFlags.NULLABLE != 0L
        value.annotations.addAll(record.common.annotations.map(bindings::annotation))
        if (value is MCNumber<*>) value.isDataOnly = record.common.flags and DeclarationFlags.DATA_ONLY != 0L
        if (value is OnScoreboard) value.isDataOnly = record.common.flags and DeclarationFlags.DATA_ONLY != 0L
        if (frozen == null) value.bindDeclaration(forceRuntime = record.common.flags and DeclarationFlags.FORCE_RUNTIME != 0L)
        return value
    }

    private fun orderMembers(source: Parser.TemplateBodyContext?, template: DataTemplate, members: List<Pair<Any, DeclarationId>>, all: List<DeclarationId>): List<DeclarationId> {
        val ordered = linkedSetOf<DeclarationId>()
        source?.templateMemberDeclaration()?.forEach { declaration ->
            val member = declaration.templateMember()
            members.filter { (value, _) -> when (value) {
                is Var<*> -> member.templateFieldDeclaration()?.Identifier()?.text == value.identifier
                is Property -> member.templateFieldDeclaration()?.Identifier()?.text == value.identifier
                is DataTemplateConstructor -> member.templateConstructorDeclaration()?.curlBlock() === value.ast
                is Function -> member.templateFunctionDeclaration()?.curlBlock() === value.ast
                is OperatorSlot -> member.operationOverrideDeclaration()?.supportOperator()?.text == value.operator ||
                    member.nativeOperationOverrideDeclaration()?.supportOperator()?.text == value.operator
                else -> false
            } }.forEach { ordered.add(it.second) }
        }
        template.preInit.keys.forEach { name -> members.firstOrNull { it.first is Var<*> && (it.first as Var<*>).identifier == name }?.second?.let(ordered::add) }
        ordered.addAll(all)
        return ordered.toList()
    }
    private fun sourceBody(source: ParserRuleContext?): Parser.TemplateBodyContext? = when (source) {
        is Parser.TemplateDeclarationContext -> source.templateBody()
        is Parser.ObjectTemplateDeclarationContext -> source.templateBody()
        is Parser.AnonymousTemplateTypeContext -> source.templateBody()
        else -> null
    }
    private fun sourceParents(source: ParserRuleContext?): List<Parser.ExtendNameContext> = when (source) {
        is Parser.TemplateDeclarationContext -> source.compoundDeclaration()?.extendName().orEmpty()
        is Parser.ObjectTemplateDeclarationContext -> source.compoundDeclaration().extendName()
        is Parser.AnonymousTemplateTypeContext -> source.extendName()
        else -> emptyList()
    }
    private fun sourceReadonly(source: ParserRuleContext?): Parser.ReadOnlyParamsContext? = when (source) {
        is Parser.TemplateDeclarationContext -> (source.compoundDeclaration()?.declarationName() ?: source.declarationName())?.readOnlyParams()
        is Parser.ObjectTemplateDeclarationContext -> source.compoundDeclaration().declarationName().readOnlyParams()
        else -> null
    }
    private fun common(value: Any, name: String, owner: DeclarationId?, environment: EnvironmentId, access: DeclarationAccess,
                       flags: Long, annotations: List<AnnotationId>, bindings: CompoundCaptureBindings) =
        DeclCommon(bindings.compoundKey(value), bindings.compoundOrigin(value), bindings.compoundName(value, name), owner ?: bindings.compoundOwner(value), environment, access, flags, annotations)
    private fun commonName(record: LibraryDeclaration) = record.common.name
    private fun flags(final: Boolean, static: Boolean) = (if (final) DeclarationFlags.FINAL else 0) or (if (static) DeclarationFlags.STATIC else 0)
    private fun access(value: Member.AccessModifier) = when (value) {
        Member.AccessModifier.PUBLIC -> DeclarationAccess.PUBLIC; Member.AccessModifier.PROTECTED -> DeclarationAccess.PROTECTED
        Member.AccessModifier.PRIVATE -> DeclarationAccess.PRIVATE; Member.AccessModifier.COMPILE_PRIVATE -> unsupported("Compiler-private visibility requires an explicit export decision")
    }
    private fun access(value: DeclarationAccess) = when (value) {
        DeclarationAccess.PUBLIC -> Member.AccessModifier.PUBLIC; DeclarationAccess.PROTECTED -> Member.AccessModifier.PROTECTED; DeclarationAccess.PRIVATE -> Member.AccessModifier.PRIVATE
    }
    private fun unsupported(message: String): Nothing = throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", message)
}
