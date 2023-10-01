package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member

@Deprecated("")
class SbValue : Var() {
    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun clone(): SbValue {
        TODO()
    }

    override fun getTempVar(): Var {
        TODO("Not yet implemented")
    }

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var?, Boolean> {
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
        params: List<String>,
        accessModifier: Member.AccessModifier
    ): Pair<Function?, Boolean> {
        TODO("Not yet implemented")
    }
}