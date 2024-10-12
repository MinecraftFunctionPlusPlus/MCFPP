package top.mcfpp.type

import net.querz.nbt.tag.IntArrayTag
import net.querz.nbt.tag.Tag
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.Function
import top.mcfpp.core.lang.ClassPointer

/**
 * 用于标识由mcfpp class定义出来的类
 */
open class MCFPPClassType(
    var cls:Class,
    override var parentType: List<MCFPPType>
): MCFPPType(cls, parentType) {

    override val nbtType: java.lang.Class<out Tag<*>>
        get() = IntArrayTag::class.java

    override val objectData: CompoundData
        get() = cls.objectClass?: CompoundData(cls.identifier)

    val genericType : List<MCFPPType> = ArrayList()

//    init {
//        registerType({it.contains(regex)}){
//            val matcher = regex.find(it)!!.groupValues
//            val clazz = GlobalField.getClass(matcher[1],matcher[2])
//            if(clazz!=null)
//                MCFPPClassType(clazz, parentType)
//            else
//                MCFPPBaseType.Any //TODO: 找不到该类型
//        }
//    }

    /**
     * 获取这个类的实例的指针实体在mcfunction中拥有的tag
     */
    open val tag: String
        get() {
            if(genericType.isNotEmpty()){
                return cls.namespace + "_class_" + cls.identifier + "_type[" + genericType.sortedBy { it.typeName }.joinToString("_") { it.typeName } + "]"
            }
            return cls.namespace + "_class_" + cls.identifier + "_type"
        }

    override val typeName: String
        get() = "class(${cls.namespace}:${cls.identifier})[${
            genericType.joinToString("_") { it.typeName }
        }]"

    open fun getGenericClassType(compiledClass: Class) : MCFPPClassType {
        val t = MCFPPClassType(compiledClass, parentType)
        return t
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

    override fun build(identifier: String, container: FieldContainer): Var<*> = ClassPointer(cls, container, identifier)
    override fun build(identifier: String): Var<*> = ClassPointer(cls, identifier)
    override fun build(identifier: String, clazz: Class): Var<*> = ClassPointer(this.cls, clazz, identifier)
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = ClassPointer(cls, container, identifier)
    override fun buildUnConcrete(identifier: String): Var<*> = ClassPointer(cls, identifier)
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = ClassPointer(this.cls, clazz, identifier)

    companion object{
        val regex = Regex("^class\\((.+):(.+)\\)$")
    }
}