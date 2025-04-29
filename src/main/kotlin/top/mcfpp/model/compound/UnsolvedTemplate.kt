package top.mcfpp.model.compound

import top.mcfpp.io.info.DataTemplateInfo

class UnsolvedTemplate(val info: DataTemplateInfo): DataTemplate("unsolved_${info.identifier}") {
    fun resolve(): DataTemplate {
        return info.get()
    }
}

class UnsolvedObjectTemplate(val info: DataTemplateInfo): ObjectDataTemplate("unsolved_${info.identifier}"){
    fun resolve(): ObjectDataTemplate {
        return info.get() as ObjectDataTemplate
    }
}