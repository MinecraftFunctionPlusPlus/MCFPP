package top.mcfpp.io.info

import top.mcfpp.antlr.mcfppParser.TemplateBodyContext
import top.mcfpp.antlr.mcfppParser.ExpressionContext
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.GenericObjectDataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate

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
    var namespace: String,
    var identifier: String,
    var parents: List<AbstractTemplateInfo<*>>,
    var field: FieldInfo,
    var constructor: List<TemplateConstructorInfo>,
    var hasCompanionObject: Boolean,
    var isObject: Boolean,
    val initializers: Map<String, ExpressionContext>,
    val declarationEnvironment: DeclarationEnvironmentInfo?
): AbstractTemplateInfo<DataTemplate> {

    override fun get(): DataTemplate {
        infoCache[this]?.let { return it }
        val template = if(isObject){
            ObjectDataTemplate(identifier, namespace)
        }else {
            DataTemplate(identifier, namespace)
        }
        template.declarationFile = null
        template.declarationEnvironment = declarationEnvironment
        currTemplate = template
        template.isInterface = isInterface
        template.isAbstract = isAbstract
        template.scope = field.get(template)
        parents.forEach {
            template.extends(it.get())
            currTemplate = template
        }
        template.preInit.putAll(initializers)
        constructor.forEach {
            template.constructors.add(it.get(template))
        }
        currTemplate = null
        infoCache[this] = template
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
                template.namespace,
                template.identifier,
                if(template != DataTemplate.baseDataTemplate) template.parent.filterIsInstance<DataTemplate>().map { AbstractTemplateInfo.from(it) } else emptyList(),
                FieldInfo.from(template.scope, template),
                template.constructors.map { TemplateConstructorInfo.from(it) },
                template.companionObject != null,
                template is ObjectDataTemplate,
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
    var namespace: String,
    var identifier: String,
    var parents: List<AbstractTemplateInfo<*>>,
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
        template.scope = field.get(template)
        parents.forEach {
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
                template.namespace,
                template.identifier,
                template.parent.filterIsInstance<DataTemplate>().map { AbstractTemplateInfo.from(it) },
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
