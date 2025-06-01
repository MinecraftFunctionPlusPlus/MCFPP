package top.mcfpp.model.field

import top.mcfpp.core.lang.Var
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.compound.GenericClass
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPTypeAliasType
import top.mcfpp.util.LogProcessor

class FileField: SimpleLibField(){

    /**
     * 此文件所在的命名空间。文件域可以访问到整个命名空间中的内容
     */
    lateinit var namespaceField: NamespaceField

    /**
     * 此文件引用了的类，模板，函数等
     */
    val importField = NamespaceField()

    /**
     * 此文件引用的命名空间
     */
    val importedNamespaceField = arrayListOf<NamespaceField>()

    fun getAccessibleClass(identifier: String, readOnlyParam: List<MCFPPType>): GenericClass? {
        return namespaceField.getClass(identifier, readOnlyParam)
            ?: importField.getClass(identifier, readOnlyParam)
            ?: importedNamespaceField.firstOrNull { it.getClass(identifier, readOnlyParam) != null }
                ?.getClass(identifier, readOnlyParam)
    }

    fun getAccessibleClass(identifier: String): Class? {
        return namespaceField.getClass(identifier)
            ?: importField.getClass(identifier)
            ?: importedNamespaceField.firstOrNull { it.getClass(identifier)!= null }
                ?.getClass(identifier)
    }

    fun getAccessibleInterface(identifier: String): Interface? {
        return namespaceField.getInterface(identifier)
           ?: importField.getInterface(identifier)
           ?: importedNamespaceField.firstOrNull { it.getInterface(identifier)!= null }
               ?.getInterface(identifier)
    }

    fun getAccessibleTemplate(identifier: String): DataTemplate? {
        return namespaceField.getTemplate(identifier)
          ?: importField.getTemplate(identifier)
          ?: importedNamespaceField.firstOrNull { it.getTemplate(identifier)!= null }
              ?.getTemplate(identifier)
    }

    fun getAccessibleAnnotation(identifier: String): java.lang.Class<out Annotation>? {
        return namespaceField.getAnnotation(identifier)
          ?: importField.getAnnotation(identifier)
          ?: importedNamespaceField.firstOrNull { it.getAnnotation(identifier)!= null }
              ?.getAnnotation(identifier)
    }

    fun getAccessibleEnum(identifier: String): Enum? {
        return namespaceField.getEnum(identifier)
         ?: importField.getEnum(identifier)
         ?: importedNamespaceField.firstOrNull { it.getEnum(identifier)!= null }
             ?.getEnum(identifier)
    }

    fun getAccessibleObject(identifier: String): CompoundData? {
        return namespaceField.getObject(identifier)
        ?: importField.getObject(identifier)
        ?: importedNamespaceField.firstOrNull { it.getObject(identifier)!= null }
           ?.getObject(identifier)
    }

    fun getAccessibleFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function {
        var qwq = namespaceField.getFunction(key, readOnlyArgs, normalArgs)
        if(qwq !is UnknownFunction) return qwq
        qwq = importField.getFunction(key, readOnlyArgs, normalArgs)
        if(qwq!is UnknownFunction) return qwq
        for (i in importedNamespaceField) {
            qwq = i.getFunction(key, readOnlyArgs, normalArgs)
            if(qwq!is UnknownFunction) return qwq
        }
        return qwq
    }

    private val hasChecked = false
    fun checkIndex() {
        if(hasChecked) return
        for (c in classes.values()){
            for (p in c.parent){
                if(p is Class.Companion.UndefinedClassOrInterface){
                    val r = p.getDefinedClassOrInterface()
                    if(r == null){
                        LogProcessor.error("Undefined class or interface: ${p.namespaceID}")
                        continue
                    }
                    c.unExtends(p)
                    c.extends(r)
                }
            }
        }
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