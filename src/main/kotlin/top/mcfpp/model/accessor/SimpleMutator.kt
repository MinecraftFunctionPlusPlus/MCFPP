package top.mcfpp.model.accessor

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember

class SimpleMutator(field: Var<*>): AbstractMutator(field) {

    override fun setter(caller: CanSelectMember, b: Var<*>): Var<*> {
        field.parent = caller
        return field.assignedBy(b)
    }

}