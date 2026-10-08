package top.mcfpp.model.property

import top.mcfpp.core.lang.Var
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function

class FunctionAccessor: AbstractAccessor {

    var function: Function

    constructor(function: Function): super() {
        this.function = function
        function.accessorField = function.identifier.substringAfter("get-").substringBefore("-call-")
    }

    constructor(field: Var<*>, d: CompoundData, context: mcfppParser.CurlBlockContext? = null): super() {
        function = Function("get-${field.identifier}", d as DataTemplate, context)
        function.accessModifier = top.mcfpp.model.Member.AccessModifier.PRIVATE
        function.returnType = field.type
        function.accessorField = field.identifier
    }

    override fun getter(caller: CanSelectMember, field: Var<*>): Var<*> {
        return function.invoke(emptyList(), caller)
    }
}
