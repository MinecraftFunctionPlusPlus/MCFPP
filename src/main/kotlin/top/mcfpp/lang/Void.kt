package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.Member
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.util.LogProcessor

class Void: Var<Nothing>("void") {
    override var javaValue: Nothing? = null
    override var type: MCFPPType = MCFPPBaseType.Void

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun assign(b: Var<*>?) {
        LogProcessor.error("Cannot assign value to void type variable")
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: MCFPPType): Var<*> {
        LogProcessor.error("Cannot cast void type variable")
        return build("unknown", type, Function.currFunction)
    }

    override fun clone(): Void {
        return Void()
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): Var<*> {
        return this
    }

    override fun storeToStack() {
        LogProcessor.error("Cannot store void type variable to stack")
    }

    override fun getFromStack() {
        LogProcessor.error("Cannot get void type variable from stack")
    }

    override fun toDynamic() {}

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        LogProcessor.error("Cannot get member from void type variable")
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
        LogProcessor.error("Cannot get member function from void type variable")
        return UnknownFunction(key) to true
    }

    override fun getVarValue(): Any {
        return "Unknown"
    }

    companion object {
        val data = CompoundData("void","mcfpp")
    }
}