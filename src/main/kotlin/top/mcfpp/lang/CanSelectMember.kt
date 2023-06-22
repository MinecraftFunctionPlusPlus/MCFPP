package top.mcfpp.lang

import top.mcfpp.lib.Function
import top.mcfpp.lib.Class
import top.mcfpp.lib.ClassMember
import javax.swing.text.StyledEditorKit.BoldAction

/**
 * 实现此抽象类即代表这个Kotlin类所代表的mcfpp数据结构能够被访问成员。
 * 在mcfpp中，成员的访问通常通过`.`运算符完成。最常见的可以访问成员的结构便是类，其次也包含了向量vector，以及玩家自定义的记分板数据结构mutiscore。
 *
 * 此抽象类继承于Var类，意味着只有变量才能实现此类，并需要实现变量应当拥有的方法，例如赋值，复制等
 */
abstract class CanSelectMember : Var{

    constructor(b: Var):super(b)

    constructor()

    /**
     * 它的类型
     */
    abstract var clsType: Class


    /**
     * 根据标识符获取一个成员。如果没有这个成员，则返回null
     *
     * @param key 成员的mcfpp标识符
     * @return 成员变量或null
     */
    abstract fun getMemberVar(key: String, accessModifier: ClassMember.AccessModifier): Pair<Var?, Boolean>

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param params 成员方法的参数
     * @return
     */
    abstract fun getMemberFunction(key: String, params: List<String>, accessModifier: ClassMember.AccessModifier): Pair<Function?, Boolean>
}