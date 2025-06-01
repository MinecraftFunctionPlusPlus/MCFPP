package top.mcfpp.core.lang.obj

import top.mcfpp.core.lang.ConcreteVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

class ObjectVar(type: CanSelectMember, identifier: String = TempPool.getVarIdentify()) :
    ConcreteVar<ObjectVar, CanSelectMember>(identifier, type) {

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
            first?.parent = value
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

    override fun getAccess(function: Function): Member.AccessModifier {
        return value.getAccess(function)
    }

    override fun replaceMemberVar(v: Var<*>) {
        value.replaceMemberVar(v)
    }

}