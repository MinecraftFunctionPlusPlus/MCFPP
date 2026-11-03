package top.mcfpp.library.compiler

import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.function.Function
import top.mcfpp.type.TypeId
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.Enum as CompilerEnum
import top.mcfpp.backend.ReferenceHelperRecipe
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.lib.SbObject
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.annotation.Annotation
import java.util.IdentityHashMap

/** Ownership is explicit. Source files authorize implementation capture; foreign declarations stay references. */
interface LibraryExportOwnership {
    fun functionReference(function: Function): DeclarationReference.Dependency? = null
    fun typeReference(identity: TypeId): DeclarationReference.Dependency? = null
    fun declarationReference(value: Any): DeclarationReference.Dependency? = null
    fun isDependencyDeclaration(value: Any): Boolean = declarationReference(value) != null
    fun namespacePackages(namespace: String): List<PackageKey> = emptyList()
    fun sourceSignature(function: Function): FunctionSourceSignature? = null
    fun macroReferences(namespace: String?, name: String, source: MCFPPFile): List<DeclarationReference.Dependency> = emptyList()
}

data class OwnedLibrarySelection(
    val identity: LibraryIdentity,
    val target: LibraryTarget,
    val functions: List<Function>,
    val ownedSources: List<MCFPPFile>,
    val dependencies: List<LibraryDependency> = emptyList(),
    val ownership: LibraryExportOwnership = object : LibraryExportOwnership {},
    val templates: List<DataTemplate> = emptyList(),
    val enums: List<CompilerEnum> = emptyList(),
    val aliases: List<OwnedLibraryAlias> = emptyList(),
    val macros: List<OwnedLibraryMacro> = emptyList(),
    val helpers: List<OwnedLibraryHelper> = emptyList(),
    val scoreboards: List<SbObject> = emptyList(),
    val initializers: List<Function> = emptyList(),
    val resources: List<OwnedLibraryResource> = emptyList(),
    val providers: List<OwnedLibraryAnnotationProvider> = emptyList(),
    val services: LibraryExportServices? = null,
)

data class OwnedLibraryAlias(val name: String, val source: MCFPPFile)
data class OwnedLibraryMacro(val namespace: String, val descriptor: MacroFunctionInfo, val source: MCFPPFile?)
data class OwnedLibraryHelper(val recipe: ReferenceHelperRecipe, val source: MCFPPFile?)
data class OwnedLibraryAnnotationProvider(val namespace: String, val name: String, val provider: Class<out Annotation>, val source: MCFPPFile?)
data class OwnedLibraryResource(val path: String, val bytes: ByteArray, val module: String,
    val owner: Any? = null, val activation: String? = null, val origin: ResourceOrigin = ResourceOrigin.STATIC)
data class LibraryExportServices(val native: ManagedNativeBindings, val annotations: PortableAnnotationCodecs,
    val sourceProvider: (String) -> PackageKey?, val sourceType: (String, String) -> MCFPPType)

object LibraryExporter {
    fun snapshotCurrentProject(identity: LibraryIdentity, target: LibraryTarget,
                               resources: List<OwnedLibraryResource> = emptyList()): OwnedLibrarySelection =
        ProjectLibrarySelection.snapshot(identity, target, resources)
    fun capture(selection: OwnedLibrarySelection): LibraryModel {
        val capture = LibraryCapture(selection)
        return try { capture.capture() } finally { capture.releaseSourceAdapters() }
    }
}

/** Rewrites every T/C edge after canonical table numbering without changing D/E/Q/B ownership. */
internal class ExportNodeRemapper(private val canonical: CanonicalLibraryTables,
                                  private val declarations: Map<DeclarationId, DeclarationId> = emptyMap(),
                                  private val scopes: Map<ScopeId, ScopeId> = emptyMap(),
                                  private val bodies: Map<BodyId, BodyId> = emptyMap(),
                                  private val dependencies: Map<Long, Long>? = null,
                                  private val typeValues: Map<NameUse, TypeRef> = emptyMap()) {
    private val nodes = IdentityHashMap<SemanticNode, SemanticNode>()
    fun type(ref: TypeRef) = canonical.typeReferences.getValue(ref)
    fun constant(ref: ConstantRef) = canonical.constantReferences.getValue(ref)
    fun declaration(ref: DeclarationId) = if (declarations.isEmpty()) ref else declarations.getValue(ref)
    fun scope(ref: ScopeId) = if (scopes.isEmpty()) ref else scopes.getValue(ref)
    fun body(ref: BodyId) = if (bodies.isEmpty()) ref else bodies.getValue(ref)
    fun reference(ref: DeclarationReference): DeclarationReference = when (ref) {
        is DeclarationReference.Local -> ref.copy(target = declaration(ref.target))
        is DeclarationReference.Dependency -> dependencies?.let { ref.copy(dependencyIndex = it.getValue(ref.dependencyIndex)) } ?: ref
    }
    /** T/C edges are already canonical here; finish their D/N ownership edges after N flattening. */
    fun tables(tables: LibraryTables, syntax: Map<NodeRef, NodeRef>): LibraryTables = LibraryTables(tables.types.map { value -> when (value) {
        is LibraryType.Declaration -> value.copy(target = reference(value.target))
        is LibraryType.Parameter -> value.copy(owner = reference(value.owner))
        is LibraryType.Dependent -> value.copy(owner = declaration(value.owner), syntax = syntax.getValue(value.syntax))
        is LibraryType.Intersection -> value.copy(declaration = value.declaration?.let(::declaration))
        else -> value
    } }, tables.constants.map { value -> when (value) {
        is ConstantValue.FunctionValue -> value.copy(target = reference(value.target), captures = value.captures.map { it.copy(declaration = reference(it.declaration)) })
        else -> value
    } })
    fun annotation(value: AnnotationRecord): AnnotationRecord = value.copy(provider = reference(value.provider), arguments = value.arguments.map(::constant),
        state = value.state?.let { it.copy(fields = it.fields.map { field -> field.copy(value = portable(field.value)) }) })
    private fun portable(value: PortableValue): PortableValue = when (value) {
        is PortableValue.Constant -> value.copy(value = constant(value.value))
        is PortableValue.Declaration -> value.copy(value = reference(value.value))
        is PortableValue.Type -> value.copy(value = type(value.value))
        else -> value
    }
    fun stateCell(value: StateCell): StateCell = when (value) {
        is StateCell.Sequence -> value.copy(values = value.values.map(::portable))
        is StateCell.Map -> value.copy(entries = value.entries.map { it.copy(key = constant(it.key), value = portable(it.value)) })
        is StateCell.Set -> value.copy(values = value.values.map(::constant))
    }
    fun runtime(value: RuntimeRecord): RuntimeRecord = when (value) {
        is RuntimeRecord.FunctionBinding -> value.copy(declaration = reference(value.declaration), generationOptions = constant(value.generationOptions))
        is RuntimeRecord.TagContribution -> value.copy(members = value.members.map(::reference))
        is RuntimeRecord.Scoreboard -> value.copy(display = value.display?.let(::constant))
        is RuntimeRecord.Initialization -> value.copy(declaration = reference(value.declaration))
    }
    fun declarationRecord(value: LibraryDeclaration, syntax: Map<NodeRef, NodeRef>): LibraryDeclaration {
        val common = value.common.copy(owner = value.common.owner?.let(::declaration))
        fun n(value: NodeRef) = syntax.getValue(value)
        fun native(value: NativeBinding) = value.copy(caller = value.caller?.let(::type))
        fun accessor(value: LibraryAccessor): LibraryAccessor = when (value) {
            is LibraryAccessor.Function -> value.copy(target = reference(value.target))
            is LibraryAccessor.Native -> value.copy(target = reference(value.target))
            else -> value
        }
        fun macro(value: MacroType): MacroType = when (value) {
            is MacroType.Source -> value.copy(type = type(value.type))
            is MacroType.Callback -> value.copy(parameters = value.parameters.map { it.copy(type = macro(it.type)) }, result = macro(value.result))
        }
        return when (value) {
            is NamespaceDeclaration -> value.copy(common = common, members = value.members.map(::declaration).sortedBy { it.value })
            is FunctionDeclaration -> value.copy(common = common, receiver = value.receiver?.let(::type), readonly = value.readonly.map(::declaration),
                normal = value.normal.map(::declaration), result = value.result?.let(::type), body = value.body?.let(::body), native = value.native?.let(::native))
            is ValueDeclaration -> value.copy(common = common, type = value.type?.let(::type), declaredType = value.declaredType?.let(::n),
                defaultBody = value.defaultBody?.let(::body), frozen = value.frozen?.let(::constant))
            is CompoundDeclaration -> value.copy(common = common, readonly = value.readonly.map(::declaration), parents = value.parents.map { ParentRecord(it.type?.let(::type), it.expression?.let(::n)) },
                typeAs = value.typeAs?.let(::type), members = value.members.map(::declaration), memberOrder = value.memberOrder.map(::declaration), companion = value.companion?.let(::declaration),
                inherited = value.inherited.map { it.copy(origin = type(it.origin), slot = reference(it.slot), field = declaration(it.field), property = it.property?.let(::declaration),
                    getterOwner = it.getterOwner?.let(::type), setterOwner = it.setterOwner?.let(::type)) })
            is ConstructorDeclaration -> value.copy(common = common, normal = value.normal.map(::declaration), body = value.body?.let(::body))
            is PropertyDeclaration -> value.copy(common = common, backing = value.backing?.let(::reference), getter = accessor(value.getter), setter = accessor(value.setter))
            is OperatorDeclaration -> value.copy(common = common, operand = value.operand?.let(::type), implementation = reference(value.implementation))
            is EnumDeclaration -> value.copy(common = common, members = value.members.map(::declaration))
            is EnumMemberDeclaration -> value.copy(common = common, data = constant(value.data))
            is TypeAliasDeclaration -> value.copy(common = common, target = type(value.target), declaredTarget = value.declaredTarget?.let(::n))
            is MacroDeclaration -> value.copy(common = common, binding = value.binding.copy(native = native(value.binding.native),
                parameters = value.binding.parameters.map { it.copy(type = macro(it.type)) }, result = macro(value.binding.result)))
            is ReferenceHelperDeclaration -> value.copy(common = common, recipe = value.recipe.copy(operandTypes = value.recipe.operandTypes.map(::type),
                target = (value.recipe.target as? LibraryReferenceTarget.Selector)?.let { it.copy(filters = it.filters.map { filter -> filter.copy(type = type(filter.type)) }) } ?: value.recipe.target))
            is AnnotationProviderDeclaration -> value.copy(common = common)
            else -> error("No declaration remapper for kind ${value.tag}")
        }
    }
    private fun name(use: NameUse): NameUse = when (use) {
        is NameUse.Local -> use.copy(target = declaration(use.target))
        is NameUse.Declaration -> use.copy(target = reference(use.target))
        is NameUse.Lookup -> use.copy(candidates = use.candidates.map(::reference))
    }
    fun node(original: SemanticNode): SemanticNode {
        nodes[original]?.let { return it }
        fun e(value: Expression) = node(value) as Expression
        fun t(value: TypeSyntax) = node(value) as TypeSyntax
        fun s(value: Statement) = node(value) as Statement
        fun parts(values: List<TextPart>) = values.map { if (it is TextPart.Interpolation) it.copy(expression = e(it.expression)) else it }
        val result: SemanticNode = when (original) {
            is Expression.Name -> typeValues[original.use]?.let { resolved ->
                Expression.TypeValue(original.scope, TypeSyntax.Name(original.scope, name(original.use), type(resolved)))
            } ?: original.copy(use = name(original.use))
            is Expression.This -> original.copy(owner = reference(original.owner))
            is Expression.Super -> original.copy(owner = reference(original.owner))
            is Expression.Literal -> original.copy(constant = constant(original.constant))
            is Expression.TypeValue -> original.copy(type = t(original.type))
            is Expression.Group -> original.copy(value = original.value?.let(::e))
            is Expression.Unary -> original.copy(operand = e(original.operand))
            is Expression.Binary -> original.copy(left = e(original.left), right = e(original.right))
            is Expression.Cast -> original.copy(value = e(original.value), target = t(original.target))
            is Expression.Member -> original.copy(receiver = e(original.receiver), candidates = original.candidates?.map(::reference))
            is Expression.Index -> original.copy(receiver = e(original.receiver), index = original.index?.let(::e))
            is Expression.PropertyFilter -> original.copy(receiver = e(original.receiver), entries = original.entries.map { it.copy(value = e(it.value)) })
            is Expression.Call -> original.copy(callee = e(original.callee), readonly = original.readonly.map(::e),
                normal = original.normal.map { it.copy(expression = e(it.expression)) }, trailingLambda = original.trailingLambda?.let(::e))
            is Expression.Lambda -> original.copy(declaration = declaration(original.declaration), parameters = original.parameters.map(::declaration), body = body(original.body), tail = original.tail?.let(::e))
            is Expression.Range -> original.copy(lower = original.lower?.let(::e), upper = original.upper?.let(::e))
            is Expression.Coordinate -> original.copy(dimensions = original.dimensions.map { it.copy(value = it.value?.let(::constant)) })
            is Expression.ListValue -> original.copy(elements = original.elements.map(::e))
            is Expression.Compound -> original.copy(entries = original.entries.map { it.copy(value = e(it.value)) })
            is Expression.NbtArray -> original.copy(values = original.values.map(::constant))
            is Expression.InterpolatedString -> original.copy(parts = parts(original.parts))
            is TypeSyntax.Name -> original.copy(use = name(original.use), resolved = original.resolved?.let(::type))
            is TypeSyntax.Apply -> original.copy(base = t(original.base), readonly = original.readonly.map(::e), resolved = original.resolved?.let(::type))
            is TypeSyntax.Container -> original.copy(argument = t(original.argument))
            is TypeSyntax.Entity -> original.copy(limit = original.limit?.let(::constant))
            is TypeSyntax.Union -> original.copy(alternatives = original.alternatives.map(::t))
            is TypeSyntax.Intersection -> original.copy(components = original.components.map(::t))
            is TypeSyntax.Function -> original.copy(parameters = original.parameters.map(::t), result = t(original.result))
            is TypeSyntax.Inferred -> original.copy(expression = e(original.expression))
            is TypeSyntax.AnonymousData -> original.copy(declaration = declaration(original.declaration))
            is Statement.Block -> original.copy(statements = original.statements.map(::s))
            is Statement.Local -> original.copy(declaration = declaration(original.declaration), initializer = original.initializer?.let(::e))
            is Statement.Evaluate -> original.copy(value = e(original.value))
            is Statement.Assign -> original.copy(target = e(original.target), value = e(original.value))
            is Statement.If -> original.copy(branches = original.branches.map { it.copy(condition = e(it.condition), body = s(it.body)) }, elseBody = original.elseBody?.let(::s))
            is Statement.While -> original.copy(condition = e(original.condition), body = s(original.body))
            is Statement.DoWhile -> original.copy(body = s(original.body), condition = e(original.condition))
            is Statement.Foreach -> original.copy(variable = declaration(original.variable), iterable = e(original.iterable), body = s(original.body))
            is Statement.TryStore -> original.copy(body = s(original.body), destination = name(original.destination))
            is Statement.Execute -> original.copy(contexts = original.contexts.map { context -> context.copy(
                path = (context.path as? ExecutePath.Members)?.let { ExecutePath.Members(it.parts.map(::e)) } ?: context.path,
                value = e(context.value)) }, body = s(original.body))
            is Statement.Command -> original.copy(parts = parts(original.parts))
            is Statement.Return -> original.copy(value = original.value?.let(::e))
            is TemplateBody -> original.copy(members = original.members.map { node(it) as TemplateMember })
            is TemplateMember.Declaration -> original.copy(declaration = declaration(original.declaration))
            is TemplateMember.AnnotationApplication -> original.copy(targets = original.targets.map(::declaration))
            is TemplateMember.AnnotationConstruction -> original.copy(provider = reference(original.provider), arguments = original.arguments.map(::e), targets = original.targets.map(::declaration))
            else -> original
        }
        val scoped = moveScope(result, scope(original.scope))
        nodes[original] = scoped
        return scoped
    }

    private fun moveScope(node: SemanticNode, scope: ScopeId): SemanticNode = when (node) {
        is Expression.Literal -> node.copy(scope = scope)
        is Expression.Name -> node.copy(scope = scope)
        is Expression.This -> node.copy(scope = scope)
        is Expression.Super -> node.copy(scope = scope)
        is Expression.TypeValue -> node.copy(scope = scope)
        is Expression.Group -> node.copy(scope = scope)
        is Expression.Unary -> node.copy(scope = scope)
        is Expression.Binary -> node.copy(scope = scope)
        is Expression.Cast -> node.copy(scope = scope)
        is Expression.Member -> node.copy(scope = scope)
        is Expression.Index -> node.copy(scope = scope)
        is Expression.PropertyFilter -> node.copy(scope = scope)
        is Expression.Call -> node.copy(scope = scope)
        is Expression.Lambda -> node.copy(scope = scope)
        is Expression.Range -> node.copy(scope = scope)
        is Expression.Coordinate -> node.copy(scope = scope)
        is Expression.Selector -> node.copy(scope = scope)
        is Expression.ListValue -> node.copy(scope = scope)
        is Expression.Compound -> node.copy(scope = scope)
        is Expression.NbtArray -> node.copy(scope = scope)
        is Expression.InterpolatedString -> node.copy(scope = scope)
        is TypeSyntax.Name -> node.copy(scope = scope)
        is TypeSyntax.Apply -> node.copy(scope = scope)
        is TypeSyntax.Container -> node.copy(scope = scope)
        is TypeSyntax.Vector -> node.copy(scope = scope)
        is TypeSyntax.Entity -> node.copy(scope = scope)
        is TypeSyntax.Union -> node.copy(scope = scope)
        is TypeSyntax.Intersection -> node.copy(scope = scope)
        is TypeSyntax.AnonymousData -> node.copy(scope = scope)
        is TypeSyntax.Function -> node.copy(scope = scope)
        is TypeSyntax.Void -> node.copy(scope = scope)
        is TypeSyntax.Inferred -> node.copy(scope = scope)
        is Statement.Block -> node.copy(scope = scope)
        is Statement.Local -> node.copy(scope = scope)
        is Statement.Evaluate -> node.copy(scope = scope)
        is Statement.Assign -> node.copy(scope = scope)
        is Statement.If -> node.copy(scope = scope)
        is Statement.While -> node.copy(scope = scope)
        is Statement.DoWhile -> node.copy(scope = scope)
        is Statement.Foreach -> node.copy(scope = scope)
        is Statement.TryStore -> node.copy(scope = scope)
        is Statement.Execute -> node.copy(scope = scope)
        is Statement.Command -> node.copy(scope = scope)
        is Statement.Break -> node.copy(scope = scope)
        is Statement.Continue -> node.copy(scope = scope)
        is Statement.Return -> node.copy(scope = scope)
        is Statement.Empty -> node.copy(scope = scope)
        is TemplateBody -> node.copy(scope = scope)
        is TemplateMember.Declaration -> node.copy(scope = scope)
        is TemplateMember.AnnotationApplication -> node.copy(scope = scope)
        is TemplateMember.AnnotationConstruction -> node.copy(scope = scope)
        else -> error("Unregistered node scope remapper")
    }
}
