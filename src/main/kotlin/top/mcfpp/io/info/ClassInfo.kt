package top.mcfpp.io.info

import top.mcfpp.antlr.mcfppParser.ClassBodyContext
import top.mcfpp.io.info.AbstractClassInfo.Companion.currClass
import top.mcfpp.model.Class
import top.mcfpp.model.ObjectClass
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.model.generic.GenericObjectClass

interface AbstractClassInfo<T: Class>: ModelInfo<T>{
    companion object {
        var currClass : Class? = null
    }
}

class ClassInfo(
    var namespace: String,
    var identifier: String,
    var parents: List<AbstractClassInfo<*>>,
    var field: FieldInfo,
    var constructor: List<ClassConstructorInfo>
): AbstractClassInfo<Class> {
    override fun get(): Class {
        infoCache[this]?.let { return it }
        if(identifier.contains("$")){
            val id = identifier.substring(0, identifier.length - 1)
            val clazz = ObjectClass(id, namespace)
            currClass = clazz
            parents.forEach {
                clazz.extends(it.get())
            }
            clazz.field = field.get()

            currClass = null
            return clazz
        }
        val clazz = Class(identifier, namespace)
        currClass = clazz
        if(clazz != Class.baseClass){
            parents.forEach {
                clazz.extends(it.get())
            }
        }
        clazz.field = field.get()
        constructor.forEach {
            clazz.constructors.add(it.get())
        }
        currClass = null
        infoCache[this] = clazz
        return clazz
    }

    companion object {

        private val classCache = HashMap<Class, ClassInfo>()

        private val infoCache = HashMap<ClassInfo, Class>()

        init {
            infoCache[from(Class.baseClass)] = Class.baseClass
        }


        fun from(cls: Class): ClassInfo{
            classCache[cls]?.let { return it }
            val v = ClassInfo(
                cls.namespace,
                cls.identifier,
                if(cls != Class.baseClass) cls.parent.map { from(it as Class) } else emptyList(),
                FieldInfo.from(cls.field),
                cls.constructors.map { ClassConstructorInfo.from(it) }
            )
            classCache[cls] = v
            return v
        }
    }
}

class GenericClassInfo(
    var namespace: String,
    var identifier: String,
    var parents: List<AbstractClassInfo<*>>,
    var generic: List<ClassParamInfo>,
    var context: ClassBodyContext,
    var field: FieldInfo,
    var constructor: List<ClassConstructorInfo>
): AbstractClassInfo<Class> {

    override fun get(): GenericClass {
        infoCache[this]?.let { return it }
        if(identifier.contains("$")){
            val id = identifier.substring(0, identifier.length - 1)
            val clazz = GenericObjectClass(id, namespace, context)
            currClass = clazz
            parents.forEach {
                clazz.extends(it.get())
            }
            generic.forEach {
                clazz.readOnlyParams.add(it.get())
            }
            clazz.field = field.get()
            currClass = null
            return clazz
        }
        val clazz = GenericClass(identifier, namespace, context)
        currClass = clazz
        parents.forEach {
            clazz.extends(it.get())
        }
        generic.forEach {
            clazz.readOnlyParams.add(it.get())
        }
        clazz.field = field.get()
        constructor.forEach {
            clazz.constructors.add(it.get())
        }
        currClass = null
        infoCache[this] = clazz
        return clazz
    }

    companion object {

        private val classCache = HashMap<GenericClass, GenericClassInfo>()

        private val infoCache = HashMap<GenericClassInfo, GenericClass>()
        fun from(cls: GenericClass): GenericClassInfo{
            classCache[cls]?.let { return it }
            val v = GenericClassInfo(
                cls.namespace,
                cls.identifier,
                cls.parent.map {
                    if(it is GenericClass) from(it)
                    else ClassInfo.from(it as Class)
                },
                cls.readOnlyParams.map { ClassParamInfo.from(it) },
                cls.ctx,
                FieldInfo.from(cls.field),
                cls.constructors.map { ClassConstructorInfo.from(it) }
            )
            classCache[cls] = v
            return v
        }
    }
}