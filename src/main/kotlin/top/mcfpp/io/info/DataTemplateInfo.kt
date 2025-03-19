package top.mcfpp.io.info

import top.mcfpp.model.DataTemplate
import top.mcfpp.model.ObjectDataTemplate

data class DataTemplateInfo(
    var namespace: String,
    var identifier: String,
    var parents: List<DataTemplateInfo>,
    var field: FieldInfo,
    var constructor: List<TemplateConstructorInfo>
): ModelInfo<DataTemplate> {

    override fun get(): DataTemplate {
        infoCache[this]?.let { return it }
        val template = if(identifier.contains("$")){
            val id = identifier.substring(0, identifier.length - 1)
            ObjectDataTemplate(id, namespace)
        }else {
            DataTemplate(identifier, namespace)
        }
        currTemplate = template
        parents.forEach {
            template.extends(it.get())
            currTemplate = template
        }
        if(!template.ifExtends(DataTemplate.baseDataTemplate)){
            template.extends(DataTemplate.baseDataTemplate)
        }
        template.field = field.get()
        constructor.forEach {
            template.constructors.add(it.get())
        }
        currTemplate = null
        infoCache[this] = template
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
            val d = DataTemplateInfo(
                template.namespace,
                template.identifier,
                if(template != DataTemplate.baseDataTemplate) template.parent.map { from(it as DataTemplate) } else emptyList(),
                FieldInfo.from(template.field),
                template.constructors.map { TemplateConstructorInfo.from(it) }
            )
            templateCache[template] = d
            return d
        }
    }
}