package top.mcfpp.model.property

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember

class SimpleMutator: AbstractMutator() {

    override fun setter(caller: CanSelectMember, field: Var<*>, b: Var<*>): Var<*> {
        field.parent = caller
        return field.assignedBy(b)
    }

}