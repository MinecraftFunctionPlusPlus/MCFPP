package top.mcfpp.model.property

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.function.Function

class FunctionMutator: AbstractMutator {

    val function: Function

    constructor(function: Function){
        this.function = function
    }

    constructor(field: Var<*>, d: CompoundData) {
        function = Function("set_${field.identifier}", d.namespace, null)
        function.returnType = field.type
        function.field.putVar("field", field)
        function.appendNormalParam(field.type, "value")
        function.field.putVar("value", field.type.build("value"))
        val thisObj = Class.currClass!!.getType().build("this", function)
        function.field.putVar("this",thisObj)
        function.owner = d
    }

    override fun setter(caller: CanSelectMember, field: Var<*>, b: Var<*>): Var<*> {
        function.invoke(arrayListOf(b), caller)
        return function.returnVar
    }

}