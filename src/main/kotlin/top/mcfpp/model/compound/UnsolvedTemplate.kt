package top.mcfpp.model.compound

import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.GenericDataTemplateInfo

class UnsolvedTemplate(val info: DataTemplateInfo, name: String = info.identifier, namespace: String = info.namespace,
                       interfaceFlag: Boolean = info.isInterface, abstractFlag: Boolean = info.isAbstract): DataTemplate(name, namespace) {
    init { isInterface = interfaceFlag; isAbstract = abstractFlag }
    fun resolve(): DataTemplate {
        return info.get()
    }
}

class UnsolvedObjectTemplate(val info: DataTemplateInfo, name: String = info.identifier, namespace: String = info.namespace,
                             interfaceFlag: Boolean = info.isInterface, abstractFlag: Boolean = info.isAbstract): ObjectDataTemplate(name, namespace){
    init { isInterface = interfaceFlag; isAbstract = abstractFlag }
    fun resolve(): ObjectDataTemplate {
        return info.get() as ObjectDataTemplate
    }
}

class UnsolvedGenericTemplate(val info: GenericDataTemplateInfo): GenericDataTemplate(info.context, info.identifier, info.namespace) {
    init { isInterface = info.isInterface; isAbstract = info.isAbstract }
    fun resolve(): DataTemplate {
        return info.get()
    }
}


class UnsolvedGenericObjectTemplate(val info: GenericDataTemplateInfo): GenericObjectDataTemplate(info.context, info.identifier, info.namespace) {
    init { isInterface = info.isInterface; isAbstract = info.isAbstract }
    fun resolve(): ObjectDataTemplate {
        return info.get() as ObjectDataTemplate
    }
}
