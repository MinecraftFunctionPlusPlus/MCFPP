package top.mcfpp.lang

import top.mcfpp.lib.Function
import top.mcfpp.lib.Class

/**
 * 实现此接口即代表这个Java类所代表的类型能够从这个类型的变量中搜寻此变量所代表的mcfpp类的成员。
 */
abstract class CanSelectMember : Var{

    constructor(b: Var):super(b)

    constructor()

    /**
     * 它的类型
     */
    abstract var clsType: Class

    /**
     * 这个对象的记分板地址
     */
    abstract var address: MCInt

    /**
     * 获取类中的一个成员。如果没有这个成员，则返回null
     *
     * @param key 成员的mcfpp标识符
     * @return 成员变量或null
     */
    abstract fun getVarMember(key: String): Var?

    /**
     * 获取类中的一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员的mcfpp标识符
     * @param params 成员方法或者null
     * @return
     */
    abstract fun getFunctionMember(key: String, params: List<String>): Function?
}