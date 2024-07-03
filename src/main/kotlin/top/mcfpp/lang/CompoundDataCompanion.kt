package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.*
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction

open class CompoundDataCompanion : CanSelectMember {

    /**
     * 类
     */
    var dataType : CompoundData

    /**
     * 新建一个指向这个类的类型指针
     */
    constructor(dataType: CompoundData){
        this.dataType = dataType
    }

    /**
     * 复制一个类的类型指针
     */
    constructor(compoundDataType: CompoundDataCompanion) {
        this.dataType = compoundDataType.dataType
    }

    @Override
    fun clone(): Any {
        return CompoundDataCompanion(this)
    }

    @get:Override
    open val type: String
        get() = "CompoundDataType@${dataType.identifier}"

    /**
     * 获取这个类中的一个静态成员字段。
     *
     * @param key 字段的标识符
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的字段，若不存在此字段则为null；第二个值是是否有足够的访问权限访问此字段。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val member = dataType.getVar(key,true)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    /**
     * 获取这个类中的一个静态成员方法。
     *
     * @param key 方法的标识符
     * @param normalParams 方法的参数
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的方法，若不存在此方法则为null；第二个值是是否有足够的访问权限访问此方法。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberFunction(key: String, readOnlyParams: List<MCFPPType>, normalParams: List<MCFPPType>, accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        //获取函数
        val member = dataType.field.getFunction(key, readOnlyParams, normalParams)
        return if(member is UnknownFunction){
            Pair(UnknownFunction(key), true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    override fun getAccess(function: Function): Member.AccessModifier {
        return if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.CLASS){
            function.parentClass()!!.getAccess(dataType)
        }else if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.TEMPLATE){
            function.parentTemplate()!!.getAccess(dataType)
        }else{
            Member.AccessModifier.PUBLIC
        }
    }
}