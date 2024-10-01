package top.mcfpp.core.lang

import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import java.util.UUID

class ObjectVar(override var value: CanSelectMember, identifier: String = UUID.randomUUID().toString()): Var<ObjectVar>(identifier), MCFPPValue<CanSelectMember> {

    override fun toDynamic(replace: Boolean): Var<*> {
        return this
    }

    override fun doAssignedBy(b: Var<*>): ObjectVar {
        LogProcessor.error("Cannot assign value to object type variable")
        return this
    }


    override fun canAssignedBy(b: Var<*>) = false

    override fun clone(): ObjectVar = this

    override fun getTempVar(): ObjectVar = this

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return value.getMemberVar(key, accessModifier)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return value.getMemberFunction(key, readOnlyParams, normalParams, accessModifier)
    }

}