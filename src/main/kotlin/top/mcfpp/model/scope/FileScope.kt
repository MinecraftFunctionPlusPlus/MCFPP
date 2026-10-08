package top.mcfpp.model.scope

import top.mcfpp.core.lang.Var
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPTypeAliasType
import top.mcfpp.util.LogProcessor

class FileScope: SimpleLibScope(){

    /**
     * 此文件所在的命名空间。文件域可以访问到整个命名空间中的内容
     */
    lateinit var namespaceField: NamespaceScope

    /**
     * 此文件引用了的类，模板，函数等
     */
    val importField = HashMap<String, NamespaceScope>()

    /**
     * 此文件引用的命名空间
     */
    val importedNamespaceField = HashMap<String, NamespaceScope>()

    private fun declarations(scope: NamespaceScope): List<NamespaceScope> =
        (listOfNotNull(GlobalScope.localNamespaces[scope.identifier]?.scope,
            GlobalScope.libNamespaces[scope.identifier]?.scope, GlobalScope.stdNamespaces[scope.identifier]?.scope) + scope).distinct()

    private fun accessibleScopes(): List<NamespaceScope> =
        (declarations(namespaceField) + importField.values + importedNamespaceField.values.flatMap(::declarations)).distinct()

    private fun qualifiedScopes(namespace: String): List<NamespaceScope> =
        if (namespaceField.identifier == namespace) declarations(namespaceField)
        else importedNamespaceField[namespace]?.let(::declarations).orEmpty() + listOfNotNull(importField[namespace])

    fun getAccessibleInterface(identifier: String): DataTemplate? {
        return accessibleScopes().firstNotNullOfOrNull { it.getInterface(identifier) }
    }

    fun getAccessibleInterface(namespace:String, identifier: String): DataTemplate? {
        return qualifiedScopes(namespace).firstNotNullOfOrNull { it.getInterface(identifier) }
    }

    fun getAccessibleTemplate(identifier: String): DataTemplate? {
        return accessibleScopes().firstNotNullOfOrNull { it.getTemplate(identifier) }
    }

    fun getAccessibleTemplate(namespace:String, identifier: String): DataTemplate? {
        return qualifiedScopes(namespace).firstNotNullOfOrNull { it.getTemplate(identifier) }
    }

    fun getAccessibleAnnotation(identifier: String): Class<out Annotation>? {
        return namespaceField.getAnnotation(identifier)
          ?: importField.values.firstNotNullOfOrNull { it.getAnnotation(identifier) }
          ?: importedNamespaceField.values.firstNotNullOfOrNull { it.getAnnotation(identifier) }
    }

    fun getAccessibleAnnotation(namespace:String, identifier: String): Class<out Annotation>? {
        if (namespaceField.identifier == namespace) {
            return namespaceField.getAnnotation(identifier)
        }
        return importedNamespaceField[namespace]?.getAnnotation(identifier)
            ?: importField[namespace]?.getAnnotation(identifier)
    }

    fun getAccessibleEnum(identifier: String): Enum? {
        return accessibleScopes().firstNotNullOfOrNull { it.getEnum(identifier) }
    }

    fun getAccessibleEnum(namespace:String, identifier: String): Enum? {
        return qualifiedScopes(namespace).firstNotNullOfOrNull { it.getEnum(identifier) }
    }

    fun getAccessibleObject(identifier: String): CompoundData? {
        return accessibleScopes().firstNotNullOfOrNull { it.getObject(identifier) }
    }

    fun getAccessibleObject(namespace:String, identifier: String): CompoundData? {
        return qualifiedScopes(namespace).firstNotNullOfOrNull { it.getObject(identifier) }
    }

    fun getAccessibleFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function {
        return top.mcfpp.model.function.ParameterMatcher.select(getAccessibleFunctionCandidates(key), key, readOnlyArgs, normalArgs)
            ?: UnknownFunction(key)
    }

    fun getAccessibleFunctionByTypes(key: String, normalArgs: List<MCFPPType>): top.mcfpp.model.function.ParameterMatcher.TypeSelection {
        return top.mcfpp.model.function.ParameterMatcher.selectTypes(getAccessibleFunctionCandidates(key), key, normalArgs)
    }

    fun getAccessibleFunctionCandidates(key: String): List<Function> = accessibleScopes().groupBy { it.identifier }
        .values.flatMap { scopes -> scopes.firstNotNullOfOrNull { scope ->
            scope.getFunctionCandidates(key).takeIf { it.isNotEmpty() }
        }.orEmpty() }

     fun getAccessibleFunction(namespace:String, key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function {
         val candidates=qualifiedScopes(namespace).firstNotNullOfOrNull { scope ->
             scope.getFunctionCandidates(key).takeIf { it.isNotEmpty() }
         }.orEmpty()
         return top.mcfpp.model.function.ParameterMatcher.select(candidates,key,readOnlyArgs,normalArgs) ?: UnknownFunction(key)
    }

    override fun getType(key: String): MCFPPType? {
        if (super.containType(key)) return super.getType(key)
        return accessibleScopes().firstOrNull { it.containType(key) }?.getType(key)
    }

    override fun containType(id: String): Boolean = super.containType(id)
        || namespaceField.containType(id)
        || importField.values.any { it.containType(id) }
        || importedNamespaceField.values.any { it.containType(id) }

    fun checkIndex() {
        resolveTypeAliases()
        namespaceField.resolveTypeAliases()
    }

}
