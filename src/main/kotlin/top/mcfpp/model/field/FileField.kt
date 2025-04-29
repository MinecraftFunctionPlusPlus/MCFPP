package top.mcfpp.model.field

import top.mcfpp.core.lang.Var
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.Interface
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.type.MCFPPType

class FileField : IField{

    lateinit var namespaceField: NamespaceField

    /**
     * 此文件引用了的类，模板，函数等
     */
    val importField = NamespaceField()

    /**
     * 此文件引用的命名空间
     */
    val importedNamespaceField = arrayListOf<NamespaceField>()

    fun getClass(identifier: String, readOnlyParam: List<MCFPPType>): GenericClass? {
        return namespaceField.getClass(identifier, readOnlyParam)
            ?: importField.getClass(identifier, readOnlyParam)
            ?: importedNamespaceField.firstOrNull { it.getClass(identifier, readOnlyParam) != null }
                ?.getClass(identifier, readOnlyParam)
    }

    fun getClass(identifier: String): Class? {
        return namespaceField.getClass(identifier)
            ?: importField.getClass(identifier)
            ?: importedNamespaceField.firstOrNull { it.getClass(identifier)!= null }
                ?.getClass(identifier)
    }

    fun getInterface(identifier: String): Interface? {
        return namespaceField.getInterface(identifier)
           ?: importField.getInterface(identifier)
           ?: importedNamespaceField.firstOrNull { it.getInterface(identifier)!= null }
               ?.getInterface(identifier)
    }

    fun getTemplate(identifier: String): DataTemplate? {
        return namespaceField.getTemplate(identifier)
          ?: importField.getTemplate(identifier)
          ?: importedNamespaceField.firstOrNull { it.getTemplate(identifier)!= null }
              ?.getTemplate(identifier)
    }

    fun getAnnotation(identifier: String): java.lang.Class<out Annotation>? {
        return namespaceField.getAnnotation(identifier)
          ?: importField.getAnnotation(identifier)
          ?: importedNamespaceField.firstOrNull { it.getAnnotation(identifier)!= null }
              ?.getAnnotation(identifier)
    }

    fun getEnum(identifier: String): Enum? {
        return namespaceField.getEnum(identifier)
         ?: importField.getEnum(identifier)
         ?: importedNamespaceField.firstOrNull { it.getEnum(identifier)!= null }
             ?.getEnum(identifier)
    }

    fun getObject(identifier: String): CompoundData? {
        return namespaceField.getObject(identifier)
        ?: importField.getObject(identifier)
        ?: importedNamespaceField.firstOrNull { it.getObject(identifier)!= null }
           ?.getObject(identifier)
    }

    fun getFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function {
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

}