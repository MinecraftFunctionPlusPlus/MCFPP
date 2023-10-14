package top.mcfpp.lang

import top.mcfpp.lib.Function
import top.mcfpp.lib.Member

/**
 * 实现此抽象类即代表这个Kotlin类所代表的mcfpp数据结构能够被访问成员。
 * 在mcfpp中，成员的访问通常通过`.`运算符完成。最常见的可以访问成员的结构便是类，其次也包含了结构体和部分基本类型。
 *
 * 此抽象类继承于Var类，意味着只有变量才能实现此类，并需要实现变量应当拥有的方法，例如赋值，复制等
 */
interface CanSelectMember{

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var?, Boolean>

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param params 成员方法的参数
     * @return
     */
    fun getMemberFunction(key: String, params: List<String>, accessModifier: Member.AccessModifier): Pair<Function?, Boolean>
}