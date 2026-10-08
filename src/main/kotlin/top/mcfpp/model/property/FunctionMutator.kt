package top.mcfpp.model.property

import top.mcfpp.core.lang.Var
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function

class FunctionMutator: AbstractMutator {

    val function: Function

    constructor(function: Function){
        this.function = function
        function.accessorField = function.identifier.substringAfter("set-").substringBefore("-call-")
    }

    constructor(field: Var<*>, d: CompoundData, context: mcfppParser.CurlBlockContext? = null) {
        function = Function("set-${field.identifier}", d as DataTemplate, context)
        function.accessModifier = top.mcfpp.model.Member.AccessModifier.PRIVATE
        function.returnType = top.mcfpp.type.MCFPPPrivateType.Void
        function.accessorField = field.identifier
        function.appendNormalParam(field.type, "value")
        function.scope.putVar("value", function.normalParams.single().buildVar(), true)
    }

    override fun setter(caller: CanSelectMember, field: Var<*>, b: Var<*>): Var<*> {
        function.invoke(arrayListOf(b), caller)
        return field
    }

}
