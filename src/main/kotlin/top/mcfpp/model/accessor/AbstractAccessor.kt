package top.mcfpp.model.accessor

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember

abstract class AbstractAccessor {

    abstract fun getter(caller: CanSelectMember, field: Var<*>): Var<*>

}