package top.mcfpp.model.compound

import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.GenericDataTemplateInfo
import top.mcfpp.model.scope.GlobalScope

class UnsolvedTemplate(name: String, namespace: String, interfaceFlag: Boolean, abstractFlag: Boolean, finalFlag: Boolean): DataTemplate(name, namespace) {
    lateinit var info: DataTemplateInfo
    constructor(info: DataTemplateInfo, name: String = info.identifier, namespace: String = info.namespace,
                interfaceFlag: Boolean = info.isInterface, abstractFlag: Boolean = info.isAbstract, finalFlag: Boolean = info.isFinal):
            this(name, namespace, interfaceFlag, abstractFlag, finalFlag) { this.info = info }
    init { isInterface = interfaceFlag; isAbstract = abstractFlag; isFinal = finalFlag }
    fun resolve(): DataTemplate {
        return GlobalScope.getCanonicalTemplate(namespace, identifier, isInterface)
            ?.takeUnless { it is UnsolvedTemplate || it is UnsolvedGenericTemplate }
            ?: info.get()
    }
}

class UnsolvedObjectTemplate(name: String, namespace: String, interfaceFlag: Boolean, abstractFlag: Boolean, finalFlag: Boolean): ObjectDataTemplate(name, namespace){
    lateinit var info: DataTemplateInfo
    constructor(info: DataTemplateInfo, name: String = info.identifier, namespace: String = info.namespace,
                interfaceFlag: Boolean = info.isInterface, abstractFlag: Boolean = info.isAbstract, finalFlag: Boolean = info.isFinal):
            this(name, namespace, interfaceFlag, abstractFlag, finalFlag) { this.info = info }
    init { isInterface = interfaceFlag; isAbstract = abstractFlag; isFinal = finalFlag }
    fun resolve(): ObjectDataTemplate {
        return (canonicalObject(namespace, identifier) as? ObjectDataTemplate)
            ?.takeUnless { it is UnsolvedObjectTemplate }
            ?: info.get() as ObjectDataTemplate
    }
}

class UnsolvedGenericTemplate(val info: GenericDataTemplateInfo): GenericDataTemplate(info.context, info.identifier, info.namespace) {
    init { isInterface = info.isInterface; isAbstract = info.isAbstract; isFinal = info.isFinal }
    fun resolve(): DataTemplate {
        return (GlobalScope.getCanonicalTemplate(namespace, identifier, isInterface) as? GenericDataTemplate)
            ?.takeUnless { it is UnsolvedGenericTemplate }
            ?: info.get()
    }
}


class UnsolvedGenericObjectTemplate(val info: GenericDataTemplateInfo): GenericObjectDataTemplate(info.context, info.identifier, info.namespace) {
    init { isInterface = info.isInterface; isAbstract = info.isAbstract; isFinal = info.isFinal }
    fun resolve(): GenericObjectDataTemplate {
        return (canonicalObject(namespace, identifier) as? GenericObjectDataTemplate)
            ?.takeUnless { it is UnsolvedGenericObjectTemplate }
            ?: info.get() as GenericObjectDataTemplate
    }
}

private fun canonicalObject(namespace: String, identifier: String): DataTemplate? =
    listOfNotNull(GlobalScope.localNamespaces[namespace], GlobalScope.libNamespaces[namespace], GlobalScope.stdNamespaces[namespace])
        .firstNotNullOfOrNull { it.scope.getObject(identifier) as? DataTemplate }
