package top.mcfpp.model.accessor

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.CompoundData
import top.mcfpp.model.function.Function

class FunctionAccessor(field: Var<*>, d: CompoundData): AbstractAccessor(field) {

    var function: Function

    init {
        function = Function("get_${field.identifier}", d.namespace, field.type, null)
        function.field.putVar("field", field)
        function.owner = d
    }

    override fun getter(caller: CanSelectMember): Var<*> {
        function.invoke(ArrayList(), caller)
        return function.returnVar
    }
}