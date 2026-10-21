package top.mcfpp.io.info

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser.TemplateBodyContext
import top.mcfpp.antlr.mcfppParser.ExpressionContext
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.GenericObjectDataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.model.compound.UnionDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId

interface AbstractTemplateInfo<T: DataTemplate>: ModelInfo<T>{

    companion object {
        var currTemplate : DataTemplate? = null

        fun from(template: DataTemplate): AbstractTemplateInfo<*> = when (template) {
            is GenericDataTemplate -> GenericDataTemplateInfo.from(template)
            else -> DataTemplateInfo.from(template)
        }
    }
}

data class DataTemplateInfo(
    val isAbstract: Boolean,
    val isFinal: Boolean,
    var namespace: String,
    var identifier: String,
    /** Closed parent identities retain order, generic origin, and typed readonly bindings. */
    val parentTypes: List<TypeId>,
    /** Non-null source expressions are rebound in the original declaration environment. */
    val parentExpressions: List<String>?,
    var field: FieldInfo,
    var constructor: List<TemplateConstructorInfo>,
    var hasCompanionObject: Boolean,
    var isObject: Boolean,
    val typeAs: MCFPPType?,
    val initializers: Map<String, ExpressionContext>,
    val declarationEnvironment: DeclarationEnvironmentInfo?,
    val annotationMetadata: DeclarationAnnotationsInfo = DeclarationAnnotationsInfo(),
    val inheritedFieldEffects: List<CompletedInheritedFieldInfo> = emptyList(),
    /** Definitions for ordinary parents which have no namespace declaration or alias. */
    val embeddedParents: List<AbstractTemplateInfo<*>> = emptyList()
): AbstractTemplateInfo<DataTemplate> {

    override fun get(): DataTemplate {
        infoCache[this]?.let { return it }
        val identity = TypeId.Declaration(if (isObject) "object" else "template", namespace, identifier)
        Project.findRegisteredAnonymousTemplate(identity)?.let {
            infoCache[this] = it
            return it
        }
        return Project.collectingTemplateSignatures { restore() }
    }

    private fun restore(): DataTemplate {
        val template = if(isObject){
            ObjectDataTemplate(identifier, namespace)
        }else if (typeAs != null) {
            typeAs.tryResolve()
            TypeDataTemplate(typeAs, identifier, namespace)
        }else {
            DataTemplate(identifier, namespace)
        }
        template.declarationFile = null
        template.declarationEnvironment = declarationEnvironment
        val previousTemplate = currTemplate
        currTemplate = template
        try {
            template.isAbstract = isAbstract
            template.isFinal = isFinal
            // Self-typed native signatures must resolve to this canonical model while its scope is restored.
            infoCache[this] = template
            template.scope = field.get(template)
            annotationMetadata.restore(template)
            template.restoreInheritedFields(inheritedFieldEffects.map { it.get() })
            restoreEmbeddedParents(embeddedParents)
            if (parentExpressions != null) template.parentID.addAll(parentExpressions)
            else template.deferParentTypes(parentTypes)
            template.preInit.putAll(initializers)
            constructor.forEach {
                template.constructors.add(it.get(template))
            }
            if(hasCompanionObject){
                template.companionObject = ObjectDataTemplate(identifier, namespace)
            }
            // Embedded signatures are absent from namespace indexes. Queue them only after the
            // whole recursive declaration has been restored; the outer collection owns completion.
            if (UnionDataTemplate.isIntersectionIdentity(namespace, identifier))
                Project.registerAnonymousTemplate(template)
            else Project.registerTemplateContract(template)
            return template
        } finally {
            currTemplate = previousTemplate
        }
    }

    companion object {
        var currTemplate: DataTemplate? = null

        private val templateCache = java.util.IdentityHashMap<DataTemplate, DataTemplateInfo>()
        private var infoCache = HashMap<DataTemplateInfo, DataTemplate>()

        fun clearWriteCache() {
            templateCache.clear()
        }

        init {
            resetCaches()
        }

        fun resetCaches() {
            currTemplate = null
            templateCache.clear()
            infoCache.clear()
            infoCache[from(DataTemplate.baseDataTemplate)] = DataTemplate.baseDataTemplate
        }

        fun from(template: DataTemplate): DataTemplateInfo {
            require(template !is CompiledGenericDataTemplate) {
                "A compiled generic template must be exported by its specialized type identity"
            }
            templateCache[template]?.let { return it }
            currTemplate = template
            val d = DataTemplateInfo(
                template.isAbstract,
                template.isFinal,
                template.namespace,
                template.identifier,
                if (template.parentID.isEmpty() && template !== DataTemplate.baseDataTemplate)
                    ArrayList(template.pendingParentTypeIds.ifEmpty {
                        template.parent.filterIsInstance<DataTemplate>().map { it.inheritanceIdentity }
                    }) else emptyList(),
                template.parentID.takeIf { it.isNotEmpty() }?.toList(),
                FieldInfo.from(template.scope, template),
                template.constructors.map { TemplateConstructorInfo.from(it) },
                template.companionObject != null,
                template is ObjectDataTemplate,
                (template as? TypeDataTemplate)?.typeAs,
                LinkedHashMap(template.preInit),
                DeclarationEnvironmentInfo.from(template.declarationFile) ?: template.declarationEnvironment,
                DeclarationAnnotationsInfo.from(template),
                template.completedInheritedFields().map(CompletedInheritedFieldInfo::from),
                embeddedParentDefinitions(template)
            )
            currTemplate = null
            templateCache[template] = d
            return d
        }
    }
}

data class GenericDataTemplateInfo(
    val isAbstract: Boolean,
    val isFinal: Boolean,
    var namespace: String,
    var identifier: String,
    /** Closed parent identities retain order, generic origin, and typed readonly bindings. */
    val parentTypes: List<TypeId>,
    /** Non-null source expressions are rebound in the original declaration environment. */
    val parentExpressions: List<String>?,
    var generic: List<DataTemplateParamInfo>,
    var context: TemplateBodyContext,
    var field: FieldInfo,
    var constructor: List<TemplateConstructorInfo>,
    var hasCompanionObject: Boolean,
    var isObject: Boolean,
    val declarationEnvironment: DeclarationEnvironmentInfo?,
    val annotationMetadata: DeclarationAnnotationsInfo = DeclarationAnnotationsInfo(),
    val inheritedFieldEffects: List<CompletedInheritedFieldInfo> = emptyList(),
    val embeddedParents: List<AbstractTemplateInfo<*>> = emptyList()
): AbstractTemplateInfo<DataTemplate> {

    override fun get(): DataTemplate {
        infoCache[this]?.let { return it }
        return Project.collectingTemplateSignatures { restore() }
    }

    private fun restore(): DataTemplate {
        val template = if(isObject){
            GenericObjectDataTemplate(context, identifier, namespace)
        }else {
            GenericDataTemplate(context, identifier, namespace)
        }
        template.declarationFile = null
        template.declarationEnvironment = declarationEnvironment
        val previousTemplate = currTemplate
        currTemplate = template
        try {
            template.isAbstract = isAbstract
            template.isFinal = isFinal
            infoCache[this] = template
            template.scope = field.get(template)
            annotationMetadata.restore(template)
            template.restoreInheritedFields(inheritedFieldEffects.map { it.get() })
            restoreEmbeddedParents(embeddedParents)
            if (parentExpressions != null) template.parentID.addAll(parentExpressions)
            else template.deferParentTypes(parentTypes)
            generic.forEach {
                template.readOnlyParams.add(it.get())
            }
            constructor.forEach {
                template.constructors.add(it.get(template))
            }
            if(hasCompanionObject && !isObject){
                template.companionObject = GenericObjectDataTemplate(context, identifier, namespace)
            }
            return template
        } finally {
            currTemplate = previousTemplate
        }
    }

    companion object {
        var currTemplate: DataTemplate? = null

        private val templateCache = java.util.IdentityHashMap<GenericDataTemplate, GenericDataTemplateInfo>()
        private var infoCache = HashMap<GenericDataTemplateInfo, GenericDataTemplate>()

        fun clearWriteCache() {
            templateCache.clear()
        }

        fun resetCaches() {
            currTemplate = null
            templateCache.clear()
            infoCache.clear()
        }

        fun from(template: GenericDataTemplate): GenericDataTemplateInfo {
            templateCache[template]?.let { return it }
            currTemplate = template
            val d = GenericDataTemplateInfo(
                template.isAbstract,
                template.isFinal,
                template.namespace,
                template.identifier,
                if (template.parentID.isEmpty()) ArrayList(template.pendingParentTypeIds.ifEmpty {
                    template.parent.filterIsInstance<DataTemplate>().map { it.inheritanceIdentity }
                }) else emptyList(),
                template.parentID.takeIf { it.isNotEmpty() }?.toList(),
                template.readOnlyParams.map { DataTemplateParamInfo.from(it) },
                template.ctx,
                FieldInfo.from(template.scope, template),
                template.constructors.map { TemplateConstructorInfo.from(it) },
                template.companionObject != null,
                template is GenericObjectDataTemplate,
                DeclarationEnvironmentInfo.from(template.declarationFile) ?: template.declarationEnvironment,
                DeclarationAnnotationsInfo.from(template),
                template.completedInheritedFields().map(CompletedInheritedFieldInfo::from),
                embeddedParentDefinitions(template)
            )
            currTemplate = null
            templateCache[template] = d
            return d
        }
    }
}

/** Specialized parents are reconstructed from their origin and typed arguments, never copied layouts. */
private fun embeddedParentDefinitions(template: DataTemplate): List<AbstractTemplateInfo<*>> {
    if (template.parentID.isNotEmpty() || template === DataTemplate.baseDataTemplate) return emptyList()
    val scopes = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values +
        GlobalScope.stdNamespaces.values).map { it.scope }
    return template.parent.filterIsInstance<DataTemplate>().filter { parent ->
        parent !is CompiledGenericDataTemplate && scopes.none { scope ->
            scope.template.values.any { it === parent } || scope.genericTemplate.values.any { it === parent } ||
                scope.objects.any { it === parent } || scope.cachedAliasTargets().any { type ->
                    when (type) {
                        is top.mcfpp.type.MCFPPDataTemplateType -> type.template === parent
                        else -> false
                    }
                }
        }
    }.map(AbstractTemplateInfo.Companion::from)
}

private fun restoreEmbeddedParents(definitions: List<AbstractTemplateInfo<*>>) {
    definitions.forEach { info ->
        val parent = info.get()
        val canonical = Project.findRegisteredAnonymousTemplate(parent.inheritanceIdentity)
        check(canonical == null || canonical === parent) { "Conflicting embedded parent identity '${parent.inheritanceIdentity}'" }
        if (canonical == null) Project.registerAnonymousTemplate(parent)
    }
}
