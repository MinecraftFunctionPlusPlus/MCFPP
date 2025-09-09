package top.mcfpp.model.property

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function

class FunctionMutator: AbstractMutator {

    val function: Function

    constructor(function: Function){
        this.function = function
    }

    constructor(field: Var<*>, d: CompoundData) {
        function = Function("set_${field.identifier}", d.namespace, null)
        function.returnType = field.type
        function.scope.putVar("field", field)
        function.appendNormalParam(field.type, "value")
        function.scope.putVar("value", field.type.buildUnConcrete("value"))
        val thisObj = Class.currClass!!.getType().build("this", function)
        function.scope.putVar("this",thisObj)
        field.parent = thisObj
        function.owner = d
    }

    override fun setter(caller: CanSelectMember, field: Var<*>, b: Var<*>): Var<*> {
        function.invoke(arrayListOf(b), caller)
        return function.returnVar
    }

}