package top.mcfpp.lang

abstract class ClassBase: CanSelectMember {

    constructor(b: Var):super(b)

    constructor()

    /**
     * 这个对象的记分板地址
     */
    abstract var address: MCInt


    abstract val tag: String
}