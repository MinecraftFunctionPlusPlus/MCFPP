package top.mcfpp.library.api

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.tree.TerminalNodeImpl
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.compiler.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.Member
import top.mcfpp.model.Generic
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum as CompilerEnum
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.*
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.library.model.AnnotationPhase
import top.mcfpp.library.model.compareUtf8
import top.mcfpp.library.syntax.AnonymousOrigin
import java.util.IdentityHashMap

/** Source-only locators. Enum/alias AST ownership comes from existing parsed units, never reparsing. */
internal class SourceCompoundIndex(private val selection: OwnedLibrarySelection) : AutoCloseable {
    data class Entry(val value: Any, val kind: Int, val name: String, val namespace: String,
        val owner: Any?, val source: MCFPPFile?, val context: ParserRuleContext?, val public: Boolean,
                     val origin: AnonymousOrigin? = null)
    val entries = ArrayList<Entry>()
    val templates = ArrayList<DataTemplate>()
    val enumerations = ArrayList<CompilerEnum>()
    val aliases = ArrayList<Pair<OwnedLibraryAlias, MCFPPTypeAliasType>>()
    val types = HashMap<TypeId, Any>()
    private val byObject = IdentityHashMap<Any, Entry>()
    private val units = IdentityHashMap<MCFPPFile, P.CompilationUnitContext>()
    private val slots = ArrayList<Pair<CompoundDeclarations.OperatorSlot, Entry>>()
    private val implementations = IdentityHashMap<Any, Function>()
    private val originalOperators = IdentityHashMap<DataTemplate, HashMap<String, HashMap<MCFPPType?, Function>>>()
    private val genericViews = IdentityHashMap<GenericDataTemplate, GenericDataTemplate>()
    private val declaredTypes = IdentityHashMap<MCFPPType, P.TypeContext>()
    private val inferredTypes = IdentityHashMap<MCFPPType, P.ExpressionContext>()
    private val signatures = IdentityHashMap<Function, FunctionSourceSignature>()
    private val functionAliases = IdentityHashMap<Function, Function>()
    data class AccessorExpression(val field: String, val getter: Boolean, val expression: ParserRuleContext)
    private val accessorExpressions = IdentityHashMap<Function, AccessorExpression>()
    private val sourceAnnotations = IdentityHashMap<P.AnnotationContext, Annotation>()
    private val annotationConstructions = IdentityHashMap<P.AnnotationContext, Class<out Annotation>>()
    private val pendingAnnotations = java.util.Collections.newSetFromMap(IdentityHashMap<Annotation, Boolean>())
    private val completedEffectAnnotations = java.util.Collections.newSetFromMap(IdentityHashMap<Annotation, Boolean>())
    private val anonymousSites = IdentityHashMap<DataTemplate, P.AnonymousTemplateTypeContext>()
    private val anonymousModels = IdentityHashMap<P.AnonymousTemplateTypeContext, DataTemplate>()
    private val sourceTypes = IdentityHashMap<P.TypeContext, MCFPPType>()
    private val inheritedFields = IdentityHashMap<DataTemplate, List<SourceInheritedField>>()

    fun owns(source: MCFPPFile?): Boolean = source != null && selection.ownedSources.any { it === source }
    private fun owns(value: DataTemplate): Boolean = !selection.ownership.isDependencyDeclaration(value) &&
        selection.ownership.typeReference(value.getType().typeId) == null &&
        (value.declarationFile == null || owns(value.declarationFile))
    fun entry(value: Any): Entry? = byObject[value] ?: (value as? Function)?.let { function ->
        functionAliases[function]?.let(byObject::get) ?: function.sourceDeclarationContext?.let { anchor ->
            entries.singleOrNull { (it.value as? Function)?.sourceDeclarationContext === anchor }
        }
    } ?: (value as? Function)?.takeIf { it.ast != null }?.let { function ->
        entries.singleOrNull { (it.value as? Function)?.let { candidate -> candidate.ast === function.ast && candidate.identifier == function.identifier } == true }
    } ?: (value as? Var<*>)?.let { field ->
        val parent = field.declaredParentTemplate as? GenericDataTemplate
        val view = parent?.let(genericViews::get)
        view?.scope?.getVar(field.identifier)?.let(byObject::get)
    } ?: (value as? CompoundDeclarations.OperatorSlot)?.let { slot ->
        slots.firstOrNull { (known, _) -> known.owner === slot.owner && known.operator == slot.operator && known.operand?.typeId == slot.operand?.typeId }?.second
    }
    fun implementation(value: CompoundDeclarations.OperatorSlot): Function? = entry(value)?.let { implementations[it.value] }
    fun declaredType(value: MCFPPType): P.TypeContext? = declaredTypes[value]
    fun rememberDeclaredType(value: MCFPPType, site: P.TypeContext) { declaredTypes[value] = site }
    fun sourceType(site: P.TypeContext): MCFPPType? = sourceTypes[site]
    fun lexicalOwners(value: Any): List<Any> = generateSequence(entry(value)?.owner) { entry(it)?.owner }.toList().asReversed()
    fun inferredType(value: MCFPPType): P.ExpressionContext? = inferredTypes[value]
    fun inheritedFields(value: DataTemplate): List<SourceInheritedField> = inheritedFields.getValue(value)
    fun inheritedSlot(owner: CompoundData, field: Var<*>): Var<*> {
        val prototype = (owner as? CompiledGenericDataTemplate)?.originTemplate ?: owner
        val declaration = entry(prototype)?.value as? CompoundData ?: prototype
        return declaration.scope.getVar(field.identifier)
            ?: fail("Inherited origin ${prototype.namespaceID} has no declared slot ${field.identifier}")
    }
    fun signature(value: Function): FunctionSourceSignature? = signatures[value]
    fun sourceFunction(value: Function): Function = functionAliases[value] ?: entry(value)?.value as? Function ?: value
    private fun functionContext(value: Function): ParserRuleContext? = when (val site = value.sourceDeclarationContext) {
        is P.FunctionDeclarationPartContext -> site.parent as? ParserRuleContext
        null -> value.ast?.parent as? ParserRuleContext
        else -> site
    }
    fun nativeSignature(value: NativeFunction): FunctionSourceSignature? {
        val source = value.declarationFile ?: return null
        val matches = all(unit(source)).filterIsInstance<P.NativeFuncDeclarationContext>().filter { declaration ->
            val part = declaration.functionDeclarationPart()
            part.Identifier().text == value.identifier &&
                part.functionParams().normalParams().parameterList()?.parameter().orEmpty().map { it.Identifier().text } == value.normalParams.map { it.identifier } &&
                part.functionParams().readOnlyParams()?.readOnlyParameterList()?.readOnlyParameter().orEmpty().map { it.Identifier().text } == value.readOnlyParams.map { it.identifier }
        }.toList()
        return matches.singleOrNull()?.functionDeclarationPart()?.let { FunctionSourceSignature(it.functionParams(), it.functionReturnType()) }
    }
    fun accessorExpression(value: Function): AccessorExpression? = accessorExpressions[value]
    fun annotation(site: P.AnnotationContext): Annotation = sourceAnnotations[site] ?: fail("Source annotation has no captured declaration recipe")
    fun annotationConstruction(site: P.AnnotationContext): Class<out Annotation>? = annotationConstructions[site]
    fun phase(value: Annotation): AnnotationPhase = if (value !in completedEffectAnnotations && value in pendingAnnotations)
        AnnotationPhase.PENDING else AnnotationPhase.COMPLETED
    private fun completedEffect(annotation: Annotation) {
        if (completedEffectAnnotations.add(annotation)) annotation.parents.forEach(::completedEffect)
    }
    fun annotations(): List<Annotation> {
        val values = arrayListOf<Annotation>()
        entries.forEach { entry -> when (val value = entry.value) {
            is DataTemplate -> {
                values.addAll(value.annotations); values.addAll(value.pendingDeclarationAnnotations)
                if (value is GenericDataTemplate) pendingAnnotations.addAll(value.annotations)
                pendingAnnotations.addAll(value.pendingDeclarationAnnotations)
                value.pendingFieldAnnotations.values.forEach { annotations ->
                    values.addAll(annotations)
                    pendingAnnotations.addAll(annotations)
                }
            }
            is Function -> values.addAll(value.annotations)
            is Var<*> -> values.addAll(value.annotations)
            is CompilerEnum -> values.addAll(value.annotations)
        } }
        // Pending field annotations can be retained by the authored member stream before they
        // appear on the current Var view. Include those exact instances in stable source order.
        units.entries.sortedWith { left, right -> compareUtf8(logicalUnit(left.key), logicalUnit(right.key)) }.forEach { (_, unit) ->
            all(unit).filterIsInstance<P.AnnotationContext>().forEach { site ->
                sourceAnnotations[site]?.let { annotation -> if (values.none { it === annotation }) values.add(annotation) }
            }
        }
        return values
    }
    fun unit(source: MCFPPFile): P.CompilationUnitContext = units.getOrPut(source) {
        val indexed = Project.trees[source] as? P.CompilationUnitContext
        indexed ?: run {
            val namespace = source.namespace.scope
            val anchors = namespace.functions.values.flatten().filter { it.declarationFile === source }.mapNotNull { it.ast } +
                namespace.template.values.filter { it.declarationFile === source }.flatMap { template ->
                    listOfNotNull((template as? GenericDataTemplate)?.ctx) + template.preInit.values + template.constructors.mapNotNull { it.ast } + template.scope.functions.values.flatten().mapNotNull { it.ast }
                } + namespace.objects.filterIsInstance<DataTemplate>().filter { it.declarationFile === source }.flatMap {
                    it.preInit.values + it.constructors.mapNotNull { constructor -> constructor.ast } + it.scope.functions.values.flatten().mapNotNull { function -> function.ast }
                }
            anchors.asSequence().mapNotNull(::compilationUnit).firstOrNull()
                ?: fail("No existing parsed unit is available for ${source.namespace.identifier}")
        }
    }
    private fun compilationUnit(context: ParserRuleContext): P.CompilationUnitContext? =
        generateSequence(context) { it.parent as? ParserRuleContext }.filterIsInstance<P.CompilationUnitContext>().firstOrNull()
    private fun all(context: ParseTree): Sequence<ParserRuleContext> = sequence {
        if (context is ParserRuleContext) yield(context)
        for (index in 0 until context.childCount) yieldAll(all(context.getChild(index)))
    }
    private fun add(entry: Entry): Entry {
        byObject[entry.value]?.let { return it }
        entries.add(entry); byObject[entry.value] = entry
        if (entry.value is CompoundDeclarations.OperatorSlot) slots += entry.value to entry
        return entry
    }
    fun collect() {
        selection.templates.forEach(::template)
        selection.enums.forEach(::enumeration)
        selection.aliases.forEach { alias ->
            if (!owns(alias.source)) fail("Alias source is not owned")
            val site = unit(alias.source).typealiasDeclaration().singleOrNull { it.Identifier().text == alias.name }
                ?: fail("Alias ${alias.name} has no unique parsed declaration")
            val original = alias.source.namespace.scope.journalTypeAliases[alias.name] as? MCFPPTypeAliasType
                ?: MCFPPTypeAliasType(site.type()).also { it.resolve(alias.source.field) }
            requireNotNull(original.cachedTarget) { "Alias must be resolved before export" }
            aliases += alias to original
            add(Entry(original, 10, alias.name, alias.source.namespace.identifier, null, alias.source, site, true))
            include(requireNotNull(original.cachedTarget), site.type())
        }
        selection.functions.forEach { function ->
            val signature = FunctionDeclarations.sourceSignature(function)
            val normal = signature?.parameters?.normalParams()?.parameterList()?.parameter().orEmpty()
            function.normalParams.forEachIndexed { index, parameter -> include(parameter.type, normal.getOrNull(index)?.type()) }
            (function.normalParams + (function as? Generic<*>)?.readOnlyParams.orEmpty() +
                (function as? NativeFunction)?.readOnlyParams.orEmpty()).forEach { parameter -> parameter.defaultTypes.values.forEach(::includeType) }
            ((function as? Generic<*>)?.readOnlyParams.orEmpty()).forEach { includeType(it.type) }
            include(function.returnType, signature?.result?.type())
        }
    }
    fun include(type: MCFPPType) = includeType(type)
    fun include(type: MCFPPType, site: P.TypeContext?) {
        if (site != null) {
            sourceTypes[site] = type
            val body = site.typeBody()
            if (type is MCFPPDataTemplateType && body.anonymousTemplateType() != null) {
                anonymousSites[type.template] = body.anonymousTemplateType(); anonymousModels[body.anonymousTemplateType()] = type.template
            }
            if (type is MCFPPTypeWithGeneric && body.type() != null && type.generic.size == 1) include(type.generic.single(), body.type())
            if (type is MCFPPUnionType) body.unionType()?.type()?.zip(type.types)?.forEach { (syntax, value) -> include(value, syntax) }
        }
        includeType(type)
    }
    fun anonymous(site: P.AnonymousTemplateTypeContext, source: MCFPPFile, lexicalOwner: Any?): DataTemplate {
        anonymousModels[site]?.let { template(it); return entry(it)!!.value as DataTemplate }
        val anchors = Project.journalState().anonymous.filter { it.declarationFile === source }
        val found = anchors.firstOrNull { candidate -> anonymousAnchor(candidate) === site }
        val stable = anonymousOrigin(source, site)
        val value = found ?: GenericDataTemplate(site.templateBody(), "anonymous_${stable.declarationOrdinal}_${stable.childPath.joinToString("_")}", source.namespace.identifier).apply {
            declarationFile = source
            when (lexicalOwner) { is DataTemplate -> scope.parent.add(lexicalOwner.scope); is Function -> scope.parent.add(lexicalOwner.scope) }
            parentID.addAll(site.extendName().map { it.text })
        }
        anonymousSites[value] = site; anonymousModels[site] = value
        template(value)
        return entry(value)!!.value as DataTemplate
    }
    private fun anonymousAnchor(value: DataTemplate): P.AnonymousTemplateTypeContext? =
        (value.preInit.values + value.deferredFields.values.map { it.context } + value.constructors.mapNotNull { it.ast } + value.scope.functions.values.flatten().mapNotNull { it.ast })
            .asSequence().mapNotNull { context -> generateSequence(context as ParserRuleContext) { it.parent as? ParserRuleContext }.filterIsInstance<P.AnonymousTemplateTypeContext>().firstOrNull() }.firstOrNull()
    fun anonymousOrigin(source: MCFPPFile, site: P.AnonymousTemplateTypeContext): AnonymousOrigin {
        val chain = generateSequence(site as ParserRuleContext) { it.parent as? ParserRuleContext }.toList().asReversed()
        val unit = unit(source)
        val declaration = chain.firstOrNull { it.parent === unit } ?: site
        val declarations = unit.children.orEmpty().filterIsInstance<ParserRuleContext>().filter { it !is P.SeparatorContext && it !is P.NamespaceDeclarationContext && it !is P.ImportDeclarationContext }
        val ordinal = declarations.indexOf(declaration).coerceAtLeast(0).toLong()
        val nested = chain.dropWhile { it !== declaration }.zipWithNext().map { (parent, child) ->
            parent.children.orEmpty().filterIsInstance<ParserRuleContext>().indexOf(child).toLong().also { require(it >= 0) }
        }
        return AnonymousOrigin(logicalUnit(source), ordinal, nested)
    }
    fun logicalUnit(source: MCFPPFile): String {
        if (source.path == ".") return "namespace/${source.namespace.identifier}"
        val root = (Project.config.sourcePath ?: Project.config.root).toAbsolutePath().normalize()
        val path = source.toPath().toAbsolutePath().normalize()
        require(path.startsWith(root)) { "Source is outside its logical source root" }
        return root.relativize(path).toString().replace('\\', '/')
    }
    private fun includeType(type: MCFPPType) {
        when (type) {
            is MCFPPDataTemplateType -> if (type.template !== DataTemplate.baseDataTemplate && owns(type.template)) template(type.template)
            is MCFPPEnumType -> {
                if (!selection.ownership.isDependencyDeclaration(type.enum)) enumeration(type.enum)
            }
            is MCFPPTypeWithGeneric -> type.generic.forEach(::includeType)
            is MCFPPUnionType -> type.types.forEach(::includeType)
        }
    }
    private fun template(original: DataTemplate) {
        if (entry(original) != null) return
        if (original is CompiledGenericDataTemplate) {
            template(original.originTemplate)
            return
        }
        val source = original.declarationFile
        val template = if (original is GenericDataTemplate) genericViews.getOrPut(original) {
            genericView(original, source ?: fail("Generic template requires its actual source environment"))
        } else original
        if (!owns(original)) fail("Foreign template ${template.namespaceID} must be referenced as a dependency")
        val anonymous = anonymousSites[original] ?: anonymousAnchor(original)
        val derivedIntersection = original is UnionDataTemplate
        val context = if (derivedIntersection) null else anonymous ?: source?.let { all(unit(it)).firstOrNull { node -> when (node) {
            is P.TemplateDeclarationContext -> template !is ObjectCompoundData &&
                (node.compoundDeclaration()?.declarationName() ?: node.declarationName())?.classWithoutNamespace()?.text == template.identifier
            is P.ObjectTemplateDeclarationContext -> template is ObjectCompoundData && node.compoundDeclaration().declarationName().classWithoutNamespace().text == template.identifier
            else -> false
        } } ?: fail("Template ${template.identifier} has no parsed declaration") }
        val companionOwner = (selection.templates + selection.ownedSources.flatMap { it.namespace.scope.template.values })
            .firstOrNull { it !== original && it.companionObject === original }
        if (companionOwner != null && entry(companionOwner) == null) {
            this.template(companionOwner)
            if (entry(template) != null) return
        }
        val chain = generateSequence(context?.parent as? ParserRuleContext) { it.parent as? ParserRuleContext }.toList()
        val lexicalOwner = if (anonymous != null) entries.filter { it.context != null && it.context in chain }.minByOrNull { chain.indexOf(it.context) }?.value
            ?: source?.namespace?.scope?.functions?.values?.flatten()?.firstOrNull { function ->
                !function.actualCallBody && chain.any { it === function.ast?.parent || it === function.ast }
            }
            ?: source?.topFunction?.takeIf { function -> chain.any { it === function.ast?.parent || it === function.ast } } else companionOwner?.let { entry(it)?.value ?: it }
        val origin = anonymous?.let { anonymousOrigin(requireNotNull(source), it) }
        val name = origin?.let { "anonymous_${it.declarationOrdinal}_${it.childPath.joinToString("_")}" } ?: template.identifier
        val public = !derivedIntersection && anonymous == null && (source != null || selection.templates.any { it === original })
        val entry = add(Entry(template, 3, name, template.namespace, lexicalOwner, source, context, public, origin))
        byObject[original] = entry
        templates += template; types[template.getType().typeId] = template
        types[original.nominalIdentity] = template
        if (template is GenericDataTemplate) template.readOnlyParams.forEachIndexed { index, parameter ->
            val sourceParameter = (original as GenericDataTemplate).readOnlyParams[index]
            val parameterEntry = add(Entry(parameter, 5, parameter.identifier, template.namespace, template, source, null, false))
            byObject[sourceParameter] = parameterEntry
        }
        val body = when (context) {
            is P.TemplateDeclarationContext -> context.templateBody()
            is P.ObjectTemplateDeclarationContext -> context.templateBody()
            is P.AnonymousTemplateTypeContext -> context.templateBody()
            else -> null
        }
        body?.let { materializeOperators(template, requireNotNull(source), it) }
        template.parent.filterIsInstance<DataTemplate>().filter { it !== DataTemplate.baseDataTemplate }.forEach { parent ->
            if (owns(parent)) this.template(parent)
        }
        val sourceParents = when (context) {
            is P.TemplateDeclarationContext -> context.compoundDeclaration()?.extendName().orEmpty()
            is P.ObjectTemplateDeclarationContext -> context.compoundDeclaration().extendName()
            is P.AnonymousTemplateTypeContext -> context.extendName()
            else -> emptyList()
        }
        sourceParents.forEach { parent ->
            val name = parent.className()
            val namespace = name.namespacePath()?.text
            val identifier = name.classWithoutNamespace().Identifier().text
            val field = requireNotNull(source) { "A structured parent requires its author source environment" }.field
            val resolved = if (namespace == null) field.getAccessibleTemplate(identifier) else field.getAccessibleTemplate(namespace, identifier)
            if (resolved != null && owns(resolved)) this.template(resolved)
        }
        val fields = template.scope.allVars.filter { it.declaredParentTemplate === template }.sortedBy { it.identifier }
        fields.forEach { field ->
            add(Entry(field, 5, field.identifier, template.namespace, template, source, null, public && field.accessModifier != Member.AccessModifier.PRIVATE))
            val syntax = body?.templateMemberDeclaration()?.firstOrNull { it.templateMember().templateFieldDeclaration()?.Identifier()?.text == field.identifier }
                ?.templateMember()?.templateFieldDeclaration()?.templateType()?.singleTemplateFieldType()?.type()
            include(field.type, syntax)
            top.mcfpp.analysis.StorageAccess.boundTypes(field).values.forEach(::includeType)
        }
        val targets = IdentityHashMap<Function, Any>()
        template.scope.allProperties.filter { it.declaredParentTemplate === template }.sortedBy { it.identifier }.forEach { property ->
            add(Entry(property, 6, property.identifier, template.namespace, template, source, null, public && property.accessModifier != Member.AccessModifier.PRIVATE))
            val field = body?.templateMemberDeclaration()?.firstOrNull {
                it.templateMember().templateFieldDeclaration()?.Identifier()?.text == property.identifier
            }?.templateMember()?.templateFieldDeclaration()
            fun prepare(function: Function, getter: Boolean, expression: ParserRuleContext?) {
                if (field != null) accessorSignature(function, field, getter)
                if (expression != null) accessorExpressions[function] = AccessorExpression(property.identifier, getter, expression)
            }
            when (val accessor = property.accessor) {
                is FunctionAccessor -> { targets[accessor.function] = property; prepare(accessor.function, true,
                    field?.accessor()?.getter()?.let { it.expression() ?: it.javaRefer() }) }
                is NativeAccessor -> { targets[accessor.function] = property; prepare(accessor.function, true, null) }
                else -> Unit
            }
            when (val mutator = property.mutator) {
                is FunctionMutator -> { targets[mutator.function] = property; prepare(mutator.function, false,
                    field?.accessor()?.setter()?.let { it.expression() ?: it.javaRefer() }) }
                is NativeMutator -> { targets[mutator.function] = property; prepare(mutator.function, false, null) }
                else -> Unit
            }
        }
        // completedInheritedFields creates detached copies, including copied annotation graphs.
        // Retain one snapshot for both discovery and D capture; a second call would create roots
        // with different identities after the A arena has already been frozen.
        val effects = template.completedInheritedFields().map { SourceInheritedField(it.origin, it.field, it.property) }
            .sortedWith { left, right -> compareUtf8(left.field.identifier, right.field.identifier) }
        inheritedFields[template] = effects
        effects.forEach { effect ->
            effect.field.annotations.forEach(::completedEffect)
            val origin = template.scope.fieldOrigins[effect.field.identifier]?.singleOrNull()
                ?: fail("Completed inherited field ${effect.field.identifier} has no unique origin in ${template.namespaceID}")
            val originOwner = (origin.owner as? CompiledGenericDataTemplate)?.originTemplate ?: origin.owner
            if (originOwner is DataTemplate && owns(originOwner)) this.template(originOwner)
            include(inheritedSlot(origin.owner, origin.field).type)
            add(Entry(effect.field, 5, effect.field.identifier, template.namespace, template, source, null, false))
            include(effect.field.type)
            effect.property?.let { property ->
                add(Entry(property, 6, property.identifier, template.namespace, template, source, null, false))
                if (origin.owner is CompiledGenericDataTemplate) {
                    val declarationOwner = entry(originOwner)?.value as? CompoundData
                    val originalProperty = declarationOwner?.scope?.getProperty(origin.field.identifier)
                    fun getter(value: Property?): Function? = when (val accessor = value?.accessor) {
                        is FunctionAccessor -> accessor.function; is NativeAccessor -> accessor.function; else -> null
                    }
                    fun setter(value: Property?): Function? = when (val mutator = value?.mutator) {
                        is FunctionMutator -> mutator.function; is NativeMutator -> mutator.function; else -> null
                    }
                    getter(property)?.let { actual -> getter(originalProperty)?.let { functionAliases[actual] = it } }
                    setter(property)?.let { actual -> setter(originalProperty)?.let { functionAliases[actual] = it } }
                }
                fun accessor(function: Function) {
                    function.owner?.let { owner -> if (owner is DataTemplate && owns(owner)) this.template(owner) }
                    if (entry(function) == null && (function.declarationFile == null || owns(function.declarationFile)) &&
                        !selection.ownership.isDependencyDeclaration(function) && !function.actualCallBody)
                        targets[function] = property
                    include(function.returnType)
                    function.normalParams.forEach { include(it.type) }
                    (function as? Generic<*>)?.readOnlyParams?.forEach { include(it.type) }
                    (function as? NativeFunction)?.readOnlyParams?.forEach { include(it.type) }
                }
                when (val getter = property.accessor) {
                    is FunctionAccessor -> accessor(getter.function)
                    is NativeAccessor -> accessor(getter.function)
                    else -> Unit
                }
                when (val setter = property.mutator) {
                    is FunctionMutator -> accessor(setter.function)
                    is NativeMutator -> accessor(setter.function)
                    else -> Unit
                }
            }
        }
        template.scope.operators.entries.sortedBy { it.key }.forEach { (operator, overloads) ->
            overloads.forEach { (operand, implementation) -> if (implementation.owner === template && !implementation.actualCallBody) {
                val slot = CompoundDeclarations.OperatorSlot(template, operator, operand)
                val entry = add(Entry(slot, 7, operator, template.namespace, template, source, implementation.ast?.parent as? ParserRuleContext, false))
                implementations[entry.value] = implementation
                if (template.scope.functions.values.flatten().none { it === implementation }) targets[implementation] = slot
            } }
        }
        val functions = template.scope.functions.values.flatten().filter { it.owner === template && !it.actualCallBody && it !is DataTemplateConstructor && it !is ExtensionFunction } + targets.keys
        val seen = IdentityHashMap<Function, Boolean>()
        functions.filter { seen.put(it, true) == null }.forEach { function ->
            add(Entry(function, 2, function.identifier, template.namespace, targets[function] ?: template, source,
                functionContext(function), public && targets[function] == null && function.accessModifier != Member.AccessModifier.PRIVATE))
            val signature = signatures[function] ?: FunctionDeclarations.sourceSignature(function)
            function.normalParams.forEachIndexed { index, parameter -> include(parameter.type, signature?.parameters?.normalParams()?.parameterList()?.parameter()?.getOrNull(index)?.type()) }
            include(function.returnType, signature?.result?.type())
        }
        template.constructors.forEach { constructor ->
            if (source == null && constructor.ast == null && constructor !is TypeDataTemplateConstructor)
                fail("Metadata constructor ${template.namespaceID} requires a real body or supported linked runtime")
            val name = if (anonymous != null) "_init_${entry.name}_${template.constructors.indexOf(constructor)}" else constructor.identifier
            add(Entry(constructor, 4, name, template.namespace, template, source,
                constructor.ast?.parent as? ParserRuleContext, public && constructor.accessModifier != Member.AccessModifier.PRIVATE))
            val syntax = (constructor.ast?.parent as? P.TemplateConstructorDeclarationContext)?.normalParams()?.parameterList()?.parameter().orEmpty()
            constructor.normalParams.forEachIndexed { index, parameter -> include(parameter.type, syntax.getOrNull(index)?.type()) }
        }
        if (template !is GenericDataTemplate) body?.let { bindSourceAnnotations(template, it) }
        template.companionObject?.let { if (it !== template) this.template(it) }
    }
    private fun bindSourceAnnotations(template: DataTemplate, body: P.TemplateBodyContext) {
        val pending = arrayListOf<P.AnnotationContext>()
        body.templateMemberDeclaration().forEach { declaration ->
            declaration.templateMember().annotation()?.let { pending.add(it); return@forEach }
            if (pending.isEmpty()) return@forEach
            val target = member(declaration).value
            val values = when (target) { is Var<*> -> target.annotations; is Function -> target.annotations; else -> emptyList() }
            val used = java.util.Collections.newSetFromMap(IdentityHashMap<Annotation, Boolean>())
            pending.forEach { site ->
                val provider = sourceAnnotationProvider(site, requireNotNull(requireNotNull(entry(template)).source))
                val value = values.firstOrNull { it.javaClass === provider && it !in used }
                    ?: (target as? Var<*>)?.identifier?.let { template.pendingFieldAnnotations[it] }.orEmpty().firstOrNull { it.javaClass === provider && it !in used }
                    ?: fail("The author's annotation instance was not retained for ${site.Identifier().text}")
                used.add(value); sourceAnnotations[site] = value
            }
            pending.clear()
        }
    }
    fun annotationTargets(site: P.TemplateMemberDeclarationContext): List<Entry> {
        val body = site.parent as? P.TemplateBodyContext ?: fail("Annotation has no template member stream")
        val ordered = body.templateMemberDeclaration()
        val target = ordered.drop(ordered.indexOf(site) + 1).firstOrNull { it.templateMember().annotation() == null }
            ?: fail("Template annotation has no following declaration")
        return listOf(member(target))
    }
    private fun fieldType(field: P.TemplateFieldDeclarationContext): P.TypeContext? = field.templateType()?.singleTemplateFieldType()?.type()
        ?: field.templateType()?.unionTemplateFieldType()?.let { union ->
            object : P.TypeContext(null, -1) {
                override fun typeBody() = object : P.TypeBodyContext(null, -1) {
                    override fun unionType() = object : P.UnionTypeContext(null, -1) {
                        override fun type(): MutableList<P.TypeContext> = union.type()
                    }
                }
            }
        }
    private fun accessorSignature(function: Function, field: P.TemplateFieldDeclarationContext, getter: Boolean) {
        val type = fieldType(field) ?: return
        val normal = object : P.NormalParamsContext(null, -1) {
            override fun parameterList(): P.ParameterListContext? = if (getter) null else object : P.ParameterListContext(null, -1) {
                override fun parameter(): MutableList<P.ParameterContext> = arrayListOf(object : P.ParameterContext(null, -1) {
                    override fun type() = type
                    override fun Identifier() = TerminalNodeImpl(CommonToken(P.Identifier, "value"))
                })
            }
        }
        signatures[function] = FunctionSourceSignature(object : P.FunctionParamsContext(null, -1) {
            override fun normalParams() = normal
        }, if (getter || function.returnType != MCFPPPrivateType.Void)
            object : P.FunctionReturnTypeContext(null, -1) { override fun type() = type } else null)
    }
    /** Generic prototype scopes describe headers, so build an isolated declaration view from its existing tree.
     * No specialization, visitor execution, default evaluation or source parsing occurs here. */
    private fun genericView(original: GenericDataTemplate, source: MCFPPFile): GenericDataTemplate {
        val view = (if (original is GenericObjectDataTemplate) GenericObjectDataTemplate(original.ctx, original.identifier, original.namespace)
            else GenericDataTemplate(original.ctx, original.identifier, original.namespace)).apply {
            declarationFile = source; isAbstract = original.isAbstract; isFinal = original.isFinal
            readOnlyParams.addAll(original.readOnlyParams.map { DataTemplateParam(it.typeIdentifier, it.identifier, it.type, it.variance) })
            parent.addAll(original.parent); parentID.addAll(original.parentID)
            companionObject = original.companionObject
            annotations.addAll(original.annotations); pendingDeclarationAnnotations.addAll(original.pendingDeclarationAnnotations)
            scope.parent.add(source.field)
            scope.parent.addAll(original.scope.parent.filter { it !== source.field })
        }
        original.readOnlyParams.forEach { parameter ->
            view.scope.putType(parameter.identifier, MCFPPGenericParamType(parameter.identifier, arrayListOf()))
        }
        val readonlyNames = (original.readOnlyParams.map { it.identifier } + entries.filter { it.value is GenericDataTemplate && (it.value as GenericDataTemplate).scope in original.scope.parent }
            .flatMap { (it.value as GenericDataTemplate).readOnlyParams.map { parameter -> parameter.identifier } } +
            (selection.functions + source.namespace.scope.functions.values.flatten() + entries.mapNotNull { it.value as? Function }).filter { it.scope in original.scope.parent }.flatMap { (it as? Generic<*>)?.readOnlyParams.orEmpty().map { parameter -> parameter.identifier } }).toSet()
        fun depends(tree: ParseTree, extra: Set<String>): Boolean = if (tree.childCount == 0) tree.text in readonlyNames + extra
            else (0 until tree.childCount).any { depends(tree.getChild(it), extra) }
        fun declared(site: P.TypeContext, function: Function? = null): MCFPPType {
            val extra = (function as? Generic<*>)?.readOnlyParams?.map { it.identifier }?.toSet().orEmpty()
            return if (depends(site, extra)) UnresolvedType(site.text).also { declaredTypes[it] = site } else source.withDeclarationContext {
                MCFPPType.parseFromContext(site, function?.scope ?: view.scope, function) ?: fail("Generic member type has no author binding")
            }
        }
        fun parameter(function: Function, name: String, type: MCFPPType, default: P.ValueContext?, readonly: Boolean): FunctionParam =
            FunctionParam(type, name, function, default != null, readonly).apply { defaultContext = default }
        val header = when (val context = original.ctx.parent) {
            is P.TemplateDeclarationContext -> (context.compoundDeclaration()?.declarationName() ?: context.declarationName())?.readOnlyParams()
            is P.ObjectTemplateDeclarationContext -> context.compoundDeclaration().declarationName().readOnlyParams()
            else -> null
        }
        view.readOnlyParams.forEachIndexed { index, parameter ->
            val site = header?.readOnlyParameterList()?.readOnlyParameter()?.getOrNull(index)?.type()
            if (parameter.type == null) parameter.type = site?.let { declared(it) } ?: MCFPPConcreteType.Type
            if (parameter.type is UnresolvedType && site != null) declaredTypes[requireNotNull(parameter.type)] = site
        }
        original.ctx.templateMemberDeclaration().forEachIndexed { ordinal, declaration ->
            val member = declaration.templateMember()
            member.annotation()?.let { site ->
                annotationConstructions[site] = sourceAnnotationProvider(site, source)
                return@forEachIndexed
            }
            val access = when {
                declaration.accessModifier()?.PRIVATE() != null -> Member.AccessModifier.PRIVATE
                declaration.accessModifier()?.PROTECTED() != null -> Member.AccessModifier.PROTECTED
                else -> Member.AccessModifier.PUBLIC
            }
            member.templateFieldDeclaration()?.let { field ->
                val sourceTypes = field.templateType()?.singleTemplateFieldType()?.type()?.let(::listOf)
                    ?: field.templateType()?.unionTemplateFieldType()?.type().orEmpty()
                val type = if (sourceTypes.isEmpty()) object : MCFPPType() {}.also {
                    inferredTypes[it] = field.expression() ?: fail("An inferred field requires its source initializer")
                } else if (sourceTypes.size == 1) declared(sourceTypes.single()) else MCFPPUnionType(*sourceTypes.map { declared(it) }.toTypedArray())
                val value = UnknownVar(field.Identifier().text).apply {
                    this.type = type; declaredParentTemplate = view; accessModifier = access; isConst = field.CONST() != null
                    isStatic = view is ObjectCompoundData
                    nullable = field.templateType()?.singleTemplateFieldType()?.QUEST() != null || field.templateType()?.unionTemplateFieldType()?.QUEST() != null
                }
                view.scope.putVar(value.identifier, value, true)
                field.expression()?.let { view.preInit[value.identifier] = it }
                val property = field.accessor()?.let { accessor ->
                    fun languageReference(reference: P.JavaReferContext): Boolean {
                        val root = reference.Identifier().first().text
                        return root in setOf("field", "value", "this", "super", value.identifier) || view.scope.getVar(root) != null ||
                            view.scope.getProperty(root) != null || source.field.getType(root) != null
                    }
                    val getter = accessor.getter()?.let { syntax ->
                        if (syntax.javaRefer() != null && !languageReference(syntax.javaRefer())) NativeAccessor(syntax.javaRefer().text, view, value).also {
                            it.function.declarationFile = source; accessorSignature(it.function, field, true)
                        } else if (syntax.curlBlock() == null && syntax.expression() == null && syntax.javaRefer() == null) SimpleAccessor() else {
                            val function = Function("get-${value.identifier}", view, syntax.curlBlock()).apply {
                                declarationFile = source; accessorField = value.identifier; returnType = type
                                accessModifier = Member.AccessModifier.PRIVATE
                            }
                            accessorSignature(function, field, true)
                            (syntax.expression() ?: syntax.javaRefer())?.let { accessorExpressions[function] = AccessorExpression(value.identifier, true, it) }
                            FunctionAccessor(function)
                        }
                    }
                    val setter = accessor.setter()?.let { syntax ->
                        if (syntax.javaRefer() != null && !languageReference(syntax.javaRefer())) NativeMutator(syntax.javaRefer().text, view, value).also {
                            it.function.declarationFile = source; accessorSignature(it.function, field, false)
                        } else if (syntax.curlBlock() == null && syntax.expression() == null && syntax.javaRefer() == null) SimpleMutator() else {
                            val function = Function("set-${value.identifier}", view, syntax.curlBlock()).apply {
                                declarationFile = source; accessorField = value.identifier; accessModifier = Member.AccessModifier.PRIVATE
                                normalParams.add(parameter(this, "value", type, null, false))
                            }
                            accessorSignature(function, field, false)
                            (syntax.expression() ?: syntax.javaRefer())?.let { accessorExpressions[function] = AccessorExpression(value.identifier, false, it) }
                            FunctionMutator(function)
                        }
                    }
                    Property(value.identifier, getter, setter)
                } ?: Property.buildSimpleProperty(value)
                view.scope.putProperty(value.identifier, property.apply { declaredParentTemplate = view; accessModifier = access; isStatic = value.isStatic }, true)
            }
            member.templateFunctionDeclaration()?.let { site ->
                val part = site.functionDeclarationPart()
                val function = if (part.functionParams().readOnlyParams() != null)
                    GenericFunction(part.Identifier().text, view, site.curlBlock() ?: fail("Generic abstract member requires a source body adapter"))
                else Function(part.Identifier().text, view, site.curlBlock())
                function.declarationFile = source; function.scope.parent.add(source.field); function.accessModifier = access
                function.sourceDeclarationContext = part
                signatures[function] = FunctionSourceSignature(part.functionParams(), part.functionReturnType())
                function.isAbstract = site.ABSTRACT() != null; function.isOverride = site.OVERRIDE() != null
                part.functionParams().readOnlyParams()?.readOnlyParameterList()?.readOnlyParameter().orEmpty().forEach { syntax ->
                    val type = syntax.type()?.let { declared(it, function) } ?: MCFPPConcreteType.Type
                    val formal = parameter(function, syntax.Identifier().text, type, syntax.value(), true)
                    (function as Generic<*>).readOnlyParams.add(formal)
                    if (type == MCFPPConcreteType.Type) function.scope.putType(formal.identifier, MCFPPGenericParamType(formal.identifier, arrayListOf()))
                }
                part.functionParams().normalParams().parameterList()?.parameter().orEmpty().forEach { syntax ->
                    function.normalParams.add(parameter(function, syntax.Identifier().text, declared(syntax.type(), function), syntax.value(), false))
                }
                function.returnType = part.functionReturnType()?.type()?.let { declared(it, function) } ?: MCFPPPrivateType.Void
                view.scope.addFunction(function, true)
            }
            member.templateConstructorDeclaration()?.let { site ->
                val function = DataTemplateConstructor(view, site.curlBlock()).apply { declarationFile = source; accessModifier = access; scope.parent.add(source.field); sourceDeclarationContext = site }
                site.normalParams()?.parameterList()?.parameter().orEmpty().forEach { syntax ->
                    function.normalParams.add(parameter(function, syntax.Identifier().text, declared(syntax.type(), function), syntax.value(), false))
                }
                view.constructors.add(function)
            }
            member.operationOverrideDeclaration()?.let { site ->
                val function = if (site.functionParams().readOnlyParams() != null) GenericFunction("__operator_$ordinal", view, site.curlBlock())
                    else Function("__operator_$ordinal", view, site.curlBlock())
                function.declarationFile = source; function.accessModifier = access; function.scope.parent.add(source.field)
                function.sourceDeclarationContext = site
                signatures[function] = FunctionSourceSignature(site.functionParams(), site.functionReturnType())
                site.functionParams().readOnlyParams()?.readOnlyParameterList()?.readOnlyParameter().orEmpty().forEach { syntax ->
                    val type = syntax.type()?.let { declared(it, function) } ?: MCFPPConcreteType.Type
                    val formal = parameter(function, syntax.Identifier().text, type, syntax.value(), true)
                    (function as Generic<*>).readOnlyParams.add(formal)
                    if (type == MCFPPConcreteType.Type) function.scope.putType(formal.identifier, MCFPPGenericParamType(formal.identifier, arrayListOf()))
                }
                site.functionParams().normalParams().parameterList()?.parameter().orEmpty().forEach { syntax ->
                    function.normalParams.add(parameter(function, syntax.Identifier().text, declared(syntax.type(), function), syntax.value(), false))
                }
                if (function.normalParams.size > 1) fail("An operator has more than one explicit operand")
                function.returnType = site.functionReturnType()?.type()?.let { declared(it, function) } ?: MCFPPPrivateType.Void
                require(view.scope.addOperator(site.supportOperator().text, function.normalParams.singleOrNull()?.type, function, false))
            }
            if (member.nativeOperationOverrideDeclaration() != null) fail("Native generic operations require portable native bindings")
        }
        return view
    }
    private fun sourceAnnotationProvider(site: P.AnnotationContext, source: MCFPPFile): Class<out Annotation> = source.withDeclarationContext {
        val name = site.Identifier().text
        val provider = source.field.getAnnotation(name) ?: source.field.getAccessibleAnnotation(name)
            ?: GlobalScope.stdNamespaces.values.firstNotNullOfOrNull { it.scope.getAnnotation(name) }
            ?: fail("No annotation provider in the author environment: $name")
        require(!top.mcfpp.mni.api.TemplateProcessor::class.java.isAssignableFrom(provider)) { "Template processors require their named declaration preparation" }
        provider
    }
    private fun enumeration(value: CompilerEnum) {
        if (entry(value) != null) return
        val source = selection.ownedSources.singleOrNull { candidate ->
            candidate.namespace.identifier == value.namespace && candidate.namespace.scope.enums.any { it === value } &&
                all(unit(candidate)).any { it is P.EnumDeclarationContext && it.Identifier().text == value.identifier }
        }
        if (selection.ownership.isDependencyDeclaration(value)) fail("Foreign enum ${value.namespaceID} must remain a dependency")
        add(Entry(value, 8, value.identifier, value.namespace, null, source, null, true))
        enumerations += value; types[value.getType().typeId] = value
        value.members.values.sortedBy { it.value }.forEach { member ->
            add(Entry(member, 9, member.identifier, value.namespace, value, source, null, true))
        }
    }
    fun path(entry: Entry): List<Entry> = generateSequence(entry.owner?.let(this::entry)) { it.owner?.let(this::entry) }.toList().asReversed()
    /** Closed source operators are parsed by the frontend but may not have a materialized scope slot. */
    private fun materializeOperators(template: DataTemplate, source: MCFPPFile, body: P.TemplateBodyContext) {
        body.templateMemberDeclaration().forEachIndexed { ordinal, member ->
            val operation = member.templateMember().operationOverrideDeclaration() ?: return@forEachIndexed
            if (template.scope.operators.values.any { overloads -> overloads.values.any { it.owner === template && it.ast === operation.curlBlock() } })
                return@forEachIndexed
            if (operation.functionParams().readOnlyParams() != null) fail("Parameterized source operators require EXPORT_FULL")
            val normal = operation.functionParams().normalParams().parameterList()?.parameter().orEmpty()
            if (normal.size > 1) fail("Closed operator capture requires zero or one explicit operand")
            val function = Function("__operator_$ordinal", template, operation.curlBlock()).apply {
                declarationFile = source
                scope.parent.add(source.field)
                accessModifier = when {
                    member.accessModifier()?.PRIVATE() != null -> Member.AccessModifier.PRIVATE
                    member.accessModifier()?.PROTECTED() != null -> Member.AccessModifier.PROTECTED
                    else -> Member.AccessModifier.PUBLIC
                }
            }
            source.withDeclarationContext {
                normal.forEach { parameter ->
                    val type = MCFPPType.parseFromContext(parameter.type(), function.scope, function)
                        ?: fail("Operator operand has no closed author type")
                    if (type is UnresolvedType || type is MCFPPGenericParamType) fail("Dependent source operators require EXPORT_FULL")
                    val formal = FunctionParam(type, parameter.Identifier().text, function, parameter.value() != null).apply {
                        defaultContext = parameter.value()
                    }
                    function.normalParams.add(formal)
                    function.scope.putVar(formal.identifier, formal.buildVar())
                }
                function.returnType = operation.functionReturnType()?.type()?.let {
                    MCFPPType.parseFromContext(it, function.scope, function) ?: fail("Operator result has no closed author type")
                } ?: MCFPPPrivateType.Void
            }
            val operator = operation.supportOperator().text
            val operand = function.normalParams.singleOrNull()?.type
            if (template.scope.operators[operator]?.containsKey(operand) == true)
                fail("Parsed operator at ${operation.start.line}:${operation.start.charPositionInLine} conflicts with another operand slot")
            if (!originalOperators.containsKey(template)) {
                originalOperators[template] = template.scope.operators
                template.scope.operators = HashMap(template.scope.operators.mapValues { HashMap(it.value) })
            }
            require(template.scope.addOperator(operator, operand, function, false))
        }
    }
    override fun close() {
        originalOperators.forEach { (template, operators) -> template.scope.operators = operators }
        originalOperators.clear()
    }
    fun member(site: P.TemplateMemberDeclarationContext): Entry {
        val syntax = site.templateMember()
        val ownerContext = generateSequence(site as ParserRuleContext) { it.parent as? ParserRuleContext }.toList()
        // Anonymous member bodies share all outer declaration ancestors. Resolve in lexical
        // parent order so a field in `var abilities: data { ... }` belongs to that data body.
        val template = ownerContext.firstNotNullOfOrNull { context ->
            templates.firstOrNull { entry(it)?.context === context }
        } ?: fail("Template member owner is unknown")
        val name = syntax.templateFieldDeclaration()?.Identifier()?.text
        if (name != null) return entries.singleOrNull { it.owner === template && it.kind == 5 && it.name == name }
            ?: fail("Parsed field $name at ${site.start.line}:${site.start.charPositionInLine} has no unique source declaration identity in ${template.namespaceID}")
        val declaration: ParserRuleContext = syntax.templateFunctionDeclaration() ?: syntax.templateConstructorDeclaration()
            ?: syntax.operationOverrideDeclaration() ?: syntax.nativeOperationOverrideDeclaration()
            ?: fail("Unsupported parsed template member")
        val body = syntax.templateFunctionDeclaration()?.curlBlock() ?: syntax.templateConstructorDeclaration()?.curlBlock()
            ?: syntax.operationOverrideDeclaration()?.curlBlock()
        val kind = when {
            syntax.templateFunctionDeclaration() != null -> 2
            syntax.templateConstructorDeclaration() != null -> 4
            else -> 7
        }
        val matches = entries.filter { entry ->
            if (entry.kind != kind || entry.owner !== template) false else {
                val implementation = implementations[entry.value] ?: entry.value as? Function
                entry.context === declaration || (body != null && implementation?.ast === body)
            }
        }
        return matches.singleOrNull() ?: fail("Parsed ${declaration.javaClass.simpleName} at ${declaration.start.line}:${declaration.start.charPositionInLine} " +
            "has ${matches.size} exact source declaration identities in ${template.namespaceID}")
    }
    private fun fail(message: String): Nothing = throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", message)
}
