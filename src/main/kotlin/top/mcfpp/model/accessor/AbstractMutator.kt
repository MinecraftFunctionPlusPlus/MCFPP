package top.mcfpp.model.accessor

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember

abstract class AbstractMutator(var field: Var<*>) {

    abstract fun setter(caller: CanSelectMember, b: Var<*>): Var<*>

}