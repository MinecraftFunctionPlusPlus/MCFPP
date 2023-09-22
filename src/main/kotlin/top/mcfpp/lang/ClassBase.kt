package top.mcfpp.lang

import top.mcfpp.lib.Class

/**
 * 此抽象类对类的基本性质进行了一些声明，例如类的地址。和mcfpp类有关的类都应当继承于此抽象方法
 *
 */
abstract class ClassBase: Var {

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
     * 表示这个类的对象的实体拥有的标签
     */
    abstract val tag: String
}