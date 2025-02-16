package top.mcfpp.type

import net.querz.nbt.tag.IntArrayTag
import net.querz.nbt.tag.Tag
import top.mcfpp.core.lang.ClassPointer
import top.mcfpp.core.lang.Var
import top.mcfpp.model.*
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.Function
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

    open fun getGenericClassType(compiledClass: Class) : MCFPPClassType {
        val t = MCFPPClassType(compiledClass, parentType)
        return t
    }

    override fun tryResolve() {
        if(cls is UnsolvedClass){
            cls = (cls as UnsolvedClass).resolve()
        }
    }

    override fun getAccess(function: Function): Member.AccessModifier {
        return if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.CLASS){
            function.parentClass()!!.getAccess(cls)
        }else if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.TEMPLATE){
            function.parentTemplate()!!.getAccess(cls)
        }else{
            Member.AccessModifier.PUBLIC
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