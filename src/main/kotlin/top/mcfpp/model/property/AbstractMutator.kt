package top.mcfpp.model.property

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember

abstract class AbstractMutator {

    abstract fun setter(caller: CanSelectMember, field: Var<*>, b: Var<*>): Var<*>

}