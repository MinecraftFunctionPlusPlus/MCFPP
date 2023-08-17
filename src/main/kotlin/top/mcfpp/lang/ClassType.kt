package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.Function
import top.mcfpp.lib.Class
import top.mcfpp.lib.Member

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

    /**
     * 获取这个类中的一个静态成员字段。
     *
     * @param key 字段的标识符
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的字段，若不存在此字段则为null；第二个值是是否有足够的访问权限访问此字段。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var?, Boolean> {
        val member = clsType.getStaticMemberVar(key)?.clone(this)
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
     * @param params 方法的参数
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的方法，若不存在此方法则为null；第二个值是是否有足够的访问权限访问此方法。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberFunction(key: String, params: List<String>, accessModifier: Member.AccessModifier): Pair<Function?, Boolean> {
        //获取函数
        val member = clsType.staticField.getFunction(key, params)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    override fun getTempVar(cache: HashMap<Var, String>): Var {
        return this
    }
}