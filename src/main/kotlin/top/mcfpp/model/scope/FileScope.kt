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

    fun getAccessibleInterface(identifier: String): DataTemplate? {
        return namespaceField.getInterface(identifier)
           ?: importField.values.firstNotNullOfOrNull { it.getInterface(identifier) }
           ?: importedNamespaceField.values.firstNotNullOfOrNull { it.getInterface(identifier) }
    }

    fun getAccessibleInterface(namespace:String, identifier: String): DataTemplate? {
        if(namespaceField.identifier == namespace){
            return namespaceField.getInterface(identifier)
        }
        return importedNamespaceField[namespace]?.getInterface(identifier)
            ?:importField[namespace]?.getInterface(identifier)
    }

    fun getAccessibleTemplate(identifier: String): DataTemplate? {
        return namespaceField.getTemplate(identifier)
          ?: importField.values.firstNotNullOfOrNull { it.getTemplate(identifier) }
          ?: importedNamespaceField.values.firstNotNullOfOrNull { it.getTemplate(identifier) }
    }

    fun getAccessibleTemplate(namespace:String, identifier: String): DataTemplate? {
        if(namespaceField.identifier == namespace){
            return namespaceField.getTemplate(identifier)
        }
        return importedNamespaceField[namespace]?.getTemplate(identifier)
            ?:importField[namespace]?.getTemplate(identifier)
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
        return namespaceField.getEnum(identifier)
         ?: importField.values.firstNotNullOfOrNull { it.getEnum(identifier) }
         ?: importedNamespaceField.values.firstNotNullOfOrNull { it.getEnum(identifier) }
    }

    fun getAccessibleEnum(namespace:String, identifier: String): Enum? {
        if(namespaceField.identifier == namespace){
            return namespaceField.getEnum(identifier)
        }
        return importedNamespaceField[namespace]?.getEnum(identifier)
            ?:importField[namespace]?.getEnum(identifier)
    }

    fun getAccessibleObject(identifier: String): CompoundData? {
        return namespaceField.getObject(identifier)
        ?: importField.values.firstNotNullOfOrNull { it.getObject(identifier) }
        ?: importedNamespaceField.values.firstNotNullOfOrNull { it.getObject(identifier) }
    }

    fun getAccessibleObject(namespace:String, identifier: String): CompoundData? {
        if(namespaceField.identifier == namespace){
            return namespaceField.getObject(identifier)
        }
        return importedNamespaceField[namespace]?.getObject(identifier)
            ?:importField[namespace]?.getObject(identifier)
    }

    fun getAccessibleFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function {
        var qwq = namespaceField.getFunction(key, readOnlyArgs, normalArgs)
        if(qwq !is UnknownFunction) return qwq
        for (i in importField.values) {
            qwq = i.getFunction(key, readOnlyArgs, normalArgs)
            if(qwq!is UnknownFunction) return qwq
        }
        for (i in importedNamespaceField.values) {
            qwq = i.getFunction(key, readOnlyArgs, normalArgs)
            if(qwq!is UnknownFunction) return qwq
        }
        return qwq
    }

     fun getAccessibleFunction(namespace:String, key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function {
         if(namespaceField.identifier == namespace){
             return namespaceField.getFunction(key, readOnlyArgs, normalArgs)
         }
         var qwq = importField[namespace]?.getFunction(key, readOnlyArgs, normalArgs)
         if(qwq !is UnknownFunction && qwq != null) return qwq
         qwq = importedNamespaceField[namespace]?.getFunction(key, readOnlyArgs, normalArgs)
         return qwq?:UnknownFunction(key)
    }

    private val hasChecked = false
    fun checkIndex() {
        if(hasChecked) return
        for ((k, v) in typeAlias){
            if(v is MCFPPTypeAliasType){
                val qwq = MCFPPType.parseFromContext(v.t, this)
                if(qwq == null){
                    LogProcessor.error("Undefined type: ${v.t.text}")
                    continue
                }
                if(qwq is MCFPPTypeAliasType){
                    LogProcessor.error("Cannot use type alias in type alias: ${v.t.text}")
                    continue
                }
                typeAlias[k] = qwq
            }
        }
    }

}