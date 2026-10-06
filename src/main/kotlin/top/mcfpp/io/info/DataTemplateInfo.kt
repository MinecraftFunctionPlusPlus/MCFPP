package top.mcfpp.io.info

import top.mcfpp.antlr.mcfppParser.TemplateBodyContext
import top.mcfpp.antlr.mcfppParser.ExpressionContext
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.GenericObjectDataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.type.MCFPPType

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
    val isInterface: Boolean,
    val isAbstract: Boolean,
    val isFinal: Boolean,
    var namespace: String,
    var identifier: String,
    var parents: List<AbstractTemplateInfo<*>>,
    /** Non-null source expressions replace copied parent metadata; null preserves manual/default parents. */
    val parentExpressions: List<String>?,
    var field: FieldInfo,
    var constructor: List<TemplateConstructorInfo>,
    var hasCompanionObject: Boolean,
    var isObject: Boolean,
    val typeAs: MCFPPType?,
    val initializers: Map<String, ExpressionContext>,
    val declarationEnvironment: DeclarationEnvironmentInfo?
): AbstractTemplateInfo<DataTemplate> {

    override fun get(): DataTemplate {
        infoCache[this]?.let { return it }
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
        currTemplate = template
        template.isInterface = isInterface
        template.isAbstract = isAbstract
        template.isFinal = isFinal
        // Self-typed native signatures must resolve to this canonical model while its scope is restored.
        infoCache[this] = template
        template.scope = field.get(template)
        if (parentExpressions != null) template.parentID.addAll(parentExpressions)
        else parents.forEach {
            template.extends(it.get())
            currTemplate = template
        }
        template.preInit.putAll(initializers)
        constructor.forEach {
            template.constructors.add(it.get(template))
        }
        currTemplate = null
        if(hasCompanionObject){
            template.companionObject = ObjectDataTemplate(identifier, namespace)
        }
        return template
    }

    companion object {
        var currTemplate: DataTemplate? = null

        private var templateCache = HashMap<DataTemplate, DataTemplateInfo>()
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
            templateCache[template]?.let { return it }
            currTemplate = template
            val d = DataTemplateInfo(
                template.isInterface,
                template.isAbstract,
                template.isFinal,
                template.namespace,
                template.identifier,
                if(template.parentID.isEmpty() && template != DataTemplate.baseDataTemplate) template.parent.filterIsInstance<DataTemplate>().map { AbstractTemplateInfo.from(it) } else emptyList(),
                template.parentID.takeIf { it.isNotEmpty() }?.toList(),
                FieldInfo.from(template.scope, template),
                template.constructors.map { TemplateConstructorInfo.from(it) },
                template.companionObject != null,
                template is ObjectDataTemplate,
                (template as? TypeDataTemplate)?.typeAs,
                LinkedHashMap(template.preInit),
                DeclarationEnvironmentInfo.from(template.declarationFile) ?: template.declarationEnvironment
            )
            currTemplate = null
            templateCache[template] = d
            return d
        }
    }
}

data class GenericDataTemplateInfo(
    val isInterface: Boolean,
    val isAbstract: Boolean,
    val isFinal: Boolean,
    var namespace: String,
    var identifier: String,
    var parents: List<AbstractTemplateInfo<*>>,
    /** Non-null source expressions replace copied parent metadata; null preserves manual/default parents. */
    val parentExpressions: List<String>?,
    var generic: List<DataTemplateParamInfo>,
    var context: TemplateBodyContext,
    var field: FieldInfo,
    var constructor: List<TemplateConstructorInfo>,
    var hasCompanionObject: Boolean,
    var isObject: Boolean,
    val declarationEnvironment: DeclarationEnvironmentInfo?
): AbstractTemplateInfo<DataTemplate> {

    override fun get(): DataTemplate {
        infoCache[this]?.let { return it }
        val template = if(isObject){
            GenericObjectDataTemplate(context, identifier, namespace)
        }else {
            GenericDataTemplate(context, identifier, namespace)
        }
        template.declarationFile = null
        template.declarationEnvironment = declarationEnvironment
        currTemplate = template
        template.isInterface = isInterface
        template.isAbstract = isAbstract
        template.isFinal = isFinal
        template.scope = field.get(template)
        if (parentExpressions != null) template.parentID.addAll(parentExpressions)
        else parents.forEach {
            template.extends(it.get())
            currTemplate = template
        }
        generic.forEach {
            template.readOnlyParams.add(it.get())
        }
        constructor.forEach {
            template.constructors.add(it.get(template))
        }
        currTemplate = null
        infoCache[this] = template
        if(hasCompanionObject && !isObject){
            template.companionObject = GenericObjectDataTemplate(context, identifier, namespace)
        }
        return template
    }

    companion object {
        var currTemplate: DataTemplate? = null

        private var templateCache = HashMap<GenericDataTemplate, GenericDataTemplateInfo>()
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
                template.isInterface,
                template.isAbstract,
                template.isFinal,
                template.namespace,
                template.identifier,
                if (template.parentID.isEmpty()) template.parent.filterIsInstance<DataTemplate>().map { AbstractTemplateInfo.from(it) } else emptyList(),
                template.parentID.takeIf { it.isNotEmpty() }?.toList(),
                template.readOnlyParams.map { DataTemplateParamInfo.from(it) },
                template.ctx,
                FieldInfo.from(template.scope, template),
                template.constructors.map { TemplateConstructorInfo.from(it) },
                template.companionObject != null,
                template is GenericObjectDataTemplate,
                DeclarationEnvironmentInfo.from(template.declarationFile) ?: template.declarationEnvironment
            )
            currTemplate = null
            templateCache[template] = d
            return d
        }
    }
}
