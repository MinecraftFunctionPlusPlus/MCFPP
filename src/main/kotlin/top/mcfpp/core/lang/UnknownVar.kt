package top.mcfpp.core.lang

import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

class UnknownVar(identifier: String) : Var<UnknownVar>(identifier) {

    constructor(): this(TempPool.getVarIdentify())

    override fun doAssignedBy(b: Var<*>) : UnknownVar {
        hasAssigned = true
        return this
    }

    override fun explicitCast(type: MCFPPType): Var<*> = type.build(identifier, Function.currFunction)

    override fun canExplicitCast(type: MCFPPType) = true

    override fun implicitCast(type: MCFPPType): Var<*> = type.build(identifier, Function.currFunction)

    override fun canImplicitCast(type: MCFPPType) = true

    override fun clone(): UnknownVar = this

    override fun getTempVar(): UnknownVar = this

    override fun storeToStack(){}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return UnknownVar(key) to true
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return UnknownFunction("unknown") to true
    }

    override fun toNBTVar(): NBTBasedData {
        return NBTBasedDataConcrete(StringTag("unknown"),"unknown")
    }

}