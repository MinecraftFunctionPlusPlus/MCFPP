package top.mcfpp.lang.type

import top.mcfpp.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.Function

/**
 * 用于标识由mcfpp class定义出来的类
 */
open class MCFPPClassType(
    var cls:Class,
    override var parentType: List<MCFPPType>
):MCFPPType(cls, parentType) {

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

    open fun getGenericClassType(compiledClass: Class) : MCFPPClassType{
        val t = MCFPPClassType(compiledClass, parentType)
        return t
    }

    /**
     * 获取这个类中的一个静态成员字段。
     *
     * @param key 字段的标识符
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的字段，若不存在此字段则为null；第二个值是是否有足够的访问权限访问此字段。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val member = cls.getVar(key,true)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    /**
     * 获取这个类中的一个成员方法。
     *
     * @param key 方法的标识符
     * @param normalParams 方法的参数
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的方法，若不存在此方法则为null；第二个值是是否有足够的访问权限访问此方法。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberFunction(key: String, readOnlyParams: List<MCFPPType>, normalParams: List<MCFPPType>, accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        //获取函数
        val member = cls.field.getFunction(key, readOnlyParams, normalParams)
        return Pair(member, accessModifier >= member.accessModifier)
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

    companion object{
        val regex = Regex("^class\\((.+):(.+)\\)$")
    }
}