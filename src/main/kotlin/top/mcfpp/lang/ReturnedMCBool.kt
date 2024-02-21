package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member
import top.mcfpp.util.LogProcessor

/**
 * 一个函数由函数返回的bool类型变量。
 *
 * 通常的bool变量是依托于记分板储存，即0代表false，1代表true。
 *当一个函数返回一个bool类型变量的时候，会使用`return 1`或者`return 0`的方式返回。因此可以使用`execute if function`来获取一个函数的bool类型的返回值。
 *同时，利用多个if和unless串联，也可以实现非或的效果。
 *
 * @constructor 创建一个函数返回的bool类型变量
 */
class ReturnedMCBool(val parentFunction: Function) : MCBool() {

    init {
        this.isStatic = false
        this.isConcrete = false
        this.isTemp = false
        this.isConst = false
        this.isImport = false
    }

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun assign(b: Var<*>?) {
        LogProcessor.error("The bool type returned by the function is read-only")
        throw Exception()
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: MCFPPType): Var<*> {
        LogProcessor.error("The bool type returned by the function is read-only")
        throw Exception()
    }

    override fun clone(): ReturnedMCBool {
        return ReturnedMCBool(parentFunction)
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): MCBool {
        val re = MCBool()
        re.assign(this)
        return re
    }

    override fun toDynamic() {
        LogProcessor.warn("The bool type returned by function is always dynamic")
    }

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
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
        params: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }


}