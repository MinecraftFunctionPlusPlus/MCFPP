package top.mcfpp.model.compound

import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.GenericDataTemplateInfo

class UnsolvedTemplate(name: String, namespace: String, interfaceFlag: Boolean, abstractFlag: Boolean, finalFlag: Boolean): DataTemplate(name, namespace) {
    lateinit var info: DataTemplateInfo
    constructor(info: DataTemplateInfo, name: String = info.identifier, namespace: String = info.namespace,
                interfaceFlag: Boolean = info.isInterface, abstractFlag: Boolean = info.isAbstract, finalFlag: Boolean = info.isFinal):
            this(name, namespace, interfaceFlag, abstractFlag, finalFlag) { this.info = info }
    init { isInterface = interfaceFlag; isAbstract = abstractFlag; isFinal = finalFlag }
    fun resolve(): DataTemplate {
        return info.get()
    }
}

class UnsolvedObjectTemplate(name: String, namespace: String, interfaceFlag: Boolean, abstractFlag: Boolean, finalFlag: Boolean): ObjectDataTemplate(name, namespace){
    lateinit var info: DataTemplateInfo
    constructor(info: DataTemplateInfo, name: String = info.identifier, namespace: String = info.namespace,
                interfaceFlag: Boolean = info.isInterface, abstractFlag: Boolean = info.isAbstract, finalFlag: Boolean = info.isFinal):
            this(name, namespace, interfaceFlag, abstractFlag, finalFlag) { this.info = info }
    init { isInterface = interfaceFlag; isAbstract = abstractFlag; isFinal = finalFlag }
    fun resolve(): ObjectDataTemplate {
        return info.get() as ObjectDataTemplate
    }
}

class UnsolvedGenericTemplate(val info: GenericDataTemplateInfo): GenericDataTemplate(info.context, info.identifier, info.namespace) {
    init { isInterface = info.isInterface; isAbstract = info.isAbstract; isFinal = info.isFinal }
    fun resolve(): DataTemplate {
        return info.get()
    }
}


class UnsolvedGenericObjectTemplate(val info: GenericDataTemplateInfo): GenericObjectDataTemplate(info.context, info.identifier, info.namespace) {
    init { isInterface = info.isInterface; isAbstract = info.isAbstract; isFinal = info.isFinal }
    fun resolve(): ObjectDataTemplate {
        return info.get() as ObjectDataTemplate
    }
}
