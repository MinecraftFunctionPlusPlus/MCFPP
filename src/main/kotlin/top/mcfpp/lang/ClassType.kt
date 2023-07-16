package top.mcfpp.lang

import top.mcfpp.exception.TODOException
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.Function
import top.mcfpp.lib.Class
import top.mcfpp.lib.ClassMember

/**
 * ClassType是类的类型指针。它是一种特殊的指针，能够指向这个类的静态成员。
 *
 * @see Class 类的核心实现
 * @see ClassObject 类的实例。指针的目标
 * @see ClassPointer 类的指针
 */
class ClassType: ClassBase{

    /**
     * 类
     */
    override var clsType: Class

    /**
     * 指针指向的地址，永远为-1。通常不会有用，因为类的静态实体只会有一个，只需要使用tag锁定即可。
     */
    override var address: MCInt

    /**
     * 获取这个类的实例的指针实体在mcfunction中拥有的tag
     */
    override val tag: String
        get() = clsType.namespace + "_class_" + clsType.identifier + "_type"

    /**
     * 新建一个指向这个类的类型指针
     */
    constructor(cls: Class){
        clsType = cls
        address = MCInt("\$classType",-1).setObj(clsType.addressSbObject) as MCInt
    }

    /**
     * 复制一个类的类型指针
     */
    constructor(clsType: ClassType) {
        this.clsType = clsType.clsType
        this.address = clsType.address
    }

    /**
     * 是否为静态。对于类的类型指针，它总是静态的。
     */
    @get:Override
    override var isStatic: Boolean = true

    @Override
    override fun clone(): Any {
        return ClassType(this)
    }

    @get:Override
    override val type: String
        get() = "ClassType@$clsType"

    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {}

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun getMemberVar(key: String, accessModifier: ClassMember.AccessModifier): Pair<Var?, Boolean> {
        val member = clsType.getStaticMemberVar(key)?.clone(this)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    @Override
    override fun getMemberFunction(key: String, params: List<String>, accessModifier: ClassMember.AccessModifier): Pair<Function?, Boolean> {
        //获取函数
        val member = clsType.staticCache.getFunction(clsType.namespace, key, params)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    override fun getTempVar(): Var {
        return this
    }
}