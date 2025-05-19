package top.mcfpp.type

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.ClassPointer
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.UnsolvedClass
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.util.TempPool

/**
 * 用于标识由mcfpp class定义出来的类
 */
open class MCFPPClassType(
    var cls:Class,
    parentType: ArrayList<out MCFPPType>
): MCFPPType(parentType) {

    override val nbtType: java.lang.Class<out Tag<*>>
        get() = IntArrayTag::class.java

    override val objectData: CompoundData
        get() = cls.objectClass?: CompoundData(cls.identifier)

    override val typeName: String
        get() = "class(${cls.namespace}:${cls.identifier})"

    override val simpleName: String
        get() = cls.identifier

    override fun defaultValue(): Var<*> {
        return ClassPointer(cls, "default")
    }

    open fun getGenericClassType(compiledClass: Class) : MCFPPClassType {
        val t = MCFPPClassType(compiledClass, parentType)
        return t
    }

    override fun tryResolve() {
        if(cls is UnsolvedClass){
            cls = (cls as UnsolvedClass).resolve()
        }
    }

    override fun build(identifier: String, container: FieldContainer): Var<*> = ClassPointer(cls, identifier)
    override fun build(identifier: String): Var<*> = ClassPointer(cls, identifier)
    override fun build(identifier: String, clazz: Class): Var<*> = ClassPointer(cls, identifier)
    override fun build(value: Any): Var<*> = ClassPointer(cls, TempPool.getVarIdentify())
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = ClassPointer(cls, identifier)
    override fun buildUnConcrete(identifier: String): Var<*> = ClassPointer(cls, identifier)
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = ClassPointer(cls, identifier)

    override fun toString(): String {
        return typeName
    }

    companion object{
        val regex = Regex("^class\\((.+):(.+)\\)$")
    }
}