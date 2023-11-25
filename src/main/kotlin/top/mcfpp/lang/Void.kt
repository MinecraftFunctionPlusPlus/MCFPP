package top.mcfpp.lang

import top.mcfpp.Project
import top.mcfpp.exception.VoidVarException
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member

class Void: Var("void") {

    override val type: String
        get() = "void"

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun assign(b: Var?) {
        Project.error("无法对void类型变量进行赋值")
        throw VoidVarException()
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: String): Var? {
        Project.error("无法对void类型变量进行类型转换")
        throw VoidVarException()
    }

    override fun clone(): Any {
        return Void()
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): Var {
        return this
    }

    override fun storeToStack() {
        Project.error("无法对void类型变量进行栈操作")
        throw VoidVarException()
    }

    override fun getFromStack() {
        Project.error("无法对void类型变量进行栈操作")
        throw VoidVarException()
    }

    override fun toDynamic() {}

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var?, Boolean> {
        Project.error("无法从void类型变量获取成员")
        throw VoidVarException()
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
        params: List<String>,
        accessModifier: Member.AccessModifier
    ): Pair<Function?, Boolean> {
        Project.error("无法从void类型变量获取成员")
        throw VoidVarException()
    }
}