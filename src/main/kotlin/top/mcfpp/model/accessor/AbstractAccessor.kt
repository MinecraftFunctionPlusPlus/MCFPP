package top.mcfpp.model.accessor

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember

abstract class AbstractAccessor(var field: Var<*>) {

    abstract fun getter(caller: CanSelectMember): Var<*>

}