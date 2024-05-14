package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.function.Function
import top.mcfpp.model.Member
import top.mcfpp.model.function.UnknownFunction

class UnknownVar : Var<Any> {

    constructor(identifier: String):super(identifier)

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun assign(b: Var<*>) : UnknownVar {return this}

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: MCFPPType): Var<*> = build(identifier,type, Function.currFunction)

    override fun clone(): UnknownVar = this

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): Var<*> = this

    override fun storeToStack(){}

    override fun getFromStack() {}

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return UnknownVar(key) to true
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param params 成员方法的参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return UnknownFunction("unknown") to true
    }


}