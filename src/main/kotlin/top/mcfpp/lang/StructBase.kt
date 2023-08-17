package top.mcfpp.lang

import top.mcfpp.lib.Struct

abstract class StructBase : CanSelectMember {

    constructor(b: Var):super(b)

    constructor()

    /**
     * 它的类型
     */
    abstract val structType: Struct
}