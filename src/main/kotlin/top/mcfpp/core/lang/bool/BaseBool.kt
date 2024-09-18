package top.mcfpp.core.lang.bool

import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType

abstract class BaseBool<T: BaseBool<T>> : Var<T>() {

    override var type: MCFPPType = MCFPPBaseType.Bool

    abstract override fun negation(): Var<*>?

    abstract override fun and(a: Var<*>): Var<*>?

    abstract override fun or(a: Var<*>): Var<*>?

}