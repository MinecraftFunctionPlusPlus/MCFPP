package top.mcfpp.core.lang

import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.accessor.Property
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType

class PropertyVar(val property: Property, val caller: CanSelectMember): Var<PropertyVar>(property.field.identifier) {

    override var type: MCFPPType = property.field.type

    override fun doAssignedBy(b: Var<*>): PropertyVar {
        return PropertyVar(Property.buildSimpleProperty(property.setter(caller, b)), caller)
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return property.field.canAssignedBy(b)
    }

    override fun clone(): PropertyVar = this

    override fun getTempVar(): PropertyVar = this

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return property.field.getMemberVar(key, accessModifier)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return property.field.getMemberFunction(key, readOnlyParams, normalParams, accessModifier)
    }


}