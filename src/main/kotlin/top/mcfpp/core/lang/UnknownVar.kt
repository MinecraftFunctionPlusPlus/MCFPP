package top.mcfpp.core.lang

import net.querz.nbt.tag.StringTag
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.function.Function
import top.mcfpp.model.Member
import top.mcfpp.model.function.UnknownFunction

class UnknownVar(identifier: String) : Var<UnknownVar>(identifier) {

    override fun doAssignedBy(b: Var<*>) : UnknownVar {
        hasAssigned = true
        return this
    }

    override fun explicitCast(type: MCFPPType): Var<*> = type.build(identifier, Function.currFunction)

    override fun implicitCast(type: MCFPPType): Var<*> = type.build(identifier, Function.currFunction)

    override fun canAssignedBy(b: Var<*>): Boolean = true

    override fun clone(): UnknownVar = this

    override fun getTempVar(): UnknownVar = this

    override fun storeToStack(){}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return UnknownVar(key) to true
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return UnknownFunction("unknown") to true
    }

    override fun toNBTVar(): NBTBasedData {
        return NBTBasedDataConcrete(StringTag("unknown"),"unknown")
    }


}