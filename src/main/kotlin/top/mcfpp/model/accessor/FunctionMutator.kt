package top.mcfpp.model.accessor

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.CompoundData
import top.mcfpp.model.function.Function

class FunctionMutator(field: Var<*>, d: CompoundData): AbstractMutator(field) {

    val function: Function

    init {
        function = Function("set_${field.identifier}", d.namespace, field.type, null)
        function.field.putVar("field", field)
        function.appendNormalParam(field.type, "value")
        function.field.putVar("value", field.type.build("value"))
        function.owner = d
    }

    override fun setter(caller: CanSelectMember, b: Var<*>): Var<*> {
        function.invoke(arrayListOf(b), caller)
        return function.returnVar
    }

}