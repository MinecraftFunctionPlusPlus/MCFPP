package top.mcfpp.model.accessor

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPType

class FunctionAccessor(field: Var<*>, d: CompoundData): AbstractAccessor() {

    var function: Function

    init {
        function = Function("get_${field.identifier}", d.namespace, null)
        function.returnType = field.type
        function.field.putVar("field", field)
        function.appendNormalParam(field.type, "field")
        function.owner = d
    }

    override fun getter(caller: CanSelectMember, field: Var<*>): Var<*> {
        function.invoke(arrayListOf(field), caller)
        return function.returnVar
    }
}