package top.mcfpp.model.property

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function

class FunctionAccessor: AbstractAccessor {

    var function: Function

    constructor(function: Function): super() {
        this.function = function
    }

    constructor(field: Var<*>, d: CompoundData): super() {
        function = Function("get_${field.identifier}", d.namespace, null)
        function.returnType = field.type
        function.scope.putVar("field", field)
        function.appendNormalParam(field.type, "field")
        val thisObj = Class.currClass!!.getType().build("this", function)
        function.scope.putVar("this",thisObj)
        field.parent = thisObj
        function.owner = d
    }

    override fun getter(caller: CanSelectMember, field: Var<*>): Var<*> {
        function.invoke(arrayListOf(field), caller)
        return function.returnVar
    }
}