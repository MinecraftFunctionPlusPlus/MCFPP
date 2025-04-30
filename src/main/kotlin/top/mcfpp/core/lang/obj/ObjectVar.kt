package top.mcfpp.core.lang.obj

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

class ObjectVar(override var value: CanSelectMember, identifier: String = TempPool.getVarIdentify()): Var<ObjectVar>(identifier),
    MCFPPValue<CanSelectMember> {

    override var type: MCFPPType = MCFPPPrivateType.MCFPPObjectVarType

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
        return value.getMemberVar(key, accessModifier).apply {
            parent = value
        }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return value.getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)
    }

}