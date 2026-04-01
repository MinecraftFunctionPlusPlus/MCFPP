package top.mcfpp.io.info

import top.mcfpp.antlr.mcfppParser.TemplateBodyContext
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.GenericObjectDataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate

interface AbstractTemplateInfo<T: DataTemplate>: ModelInfo<T>{

    companion object {
        var currTemplate : DataTemplate? = null
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
    var isObject: Boolean
): AbstractTemplateInfo<DataTemplate> {

    override fun get(): DataTemplate {
        infoCache[this]?.let { return it }
        val template = if(isObject){
            ObjectDataTemplate(identifier, namespace)
        }else {
            DataTemplate(identifier, namespace)
        }
        currTemplate = template
        template.scope = field.get()
        parents.forEach {
            template.extends(it.get())
            currTemplate = template
        }
        constructor.forEach {
            template.constructors.add(it.get())
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

        init {
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
                if(template != DataTemplate.baseDataTemplate) template.parent.map { from(it as DataTemplate) } else emptyList(),
                FieldInfo.from(template.scope),
                template.constructors.map { TemplateConstructorInfo.from(it) },
                template.companionObject != null,
                template is ObjectDataTemplate
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
    var isObject: Boolean
): AbstractTemplateInfo<DataTemplate> {

    override fun get(): DataTemplate {
        infoCache[this]?.let { return it }
        val template = if(isObject){
            GenericObjectDataTemplate(context, identifier, namespace)
        }else {
            GenericDataTemplate(context, identifier, namespace)
        }
        currTemplate = template
        template.scope = field.get()
        parents.forEach {
            template.extends(it.get())
            currTemplate = template
        }
        generic.forEach {
            template.readOnlyParams.add(it.get())
        }
        constructor.forEach {
            template.constructors.add(it.get())
        }
        currTemplate = null
        infoCache[this] = template
        if(hasCompanionObject){
            template.companionObject = GenericObjectDataTemplate(context, identifier, namespace)
        }
        return template
    }

    companion object {
        var currTemplate: DataTemplate? = null

        private var templateCache = HashMap<GenericDataTemplate, GenericDataTemplateInfo>()
        private var infoCache = HashMap<GenericDataTemplateInfo, GenericDataTemplate>()

        fun from(template: GenericDataTemplate): GenericDataTemplateInfo {
            templateCache[template]?.let { return it }
            currTemplate = template
            val d = GenericDataTemplateInfo(
                template.isInterface,
                template.isAbstract,
                template.namespace,
                template.identifier,
                template.parent.map { from(it as GenericDataTemplate) },
                template.readOnlyParams.map { DataTemplateParamInfo.from(it) },
                template.ctx,
                FieldInfo.from(template.scope),
                template.constructors.map { TemplateConstructorInfo.from(it) },
                template.companionObject != null,
                template is GenericObjectDataTemplate
            )
            currTemplate = null
            templateCache[template] = d
            return d
        }
    }
}