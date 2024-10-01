package top.mcfpp.core.lang

import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.Member
import top.mcfpp.util.LogProcessor
import java.util.UUID

open class MCFPPTypeVar : Var<MCFPPTypeVar>, MCFPPValue<MCFPPType> {

    override lateinit var value: MCFPPType

    override var type: MCFPPType = MCFPPBaseType.Type

    constructor(type: MCFPPType = MCFPPBaseType.Any, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        this.value = type
    }

    override fun doAssignedBy(b: Var<*>) : MCFPPTypeVar {
        if(b is MCFPPTypeVar){
            this.value = b.value
            hasAssigned = true
        } else {
            LogProcessor.error("Cannot assign a ${b.type} to a MCFPPTypeVar")
        }
        return this
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }


    override fun clone(): MCFPPTypeVar {
        return this
    }

    override fun getTempVar(): MCFPPTypeVar {
        return MCFPPTypeVar(value)
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        TODO("Not yet implemented")
    }

    companion object {
        val data = CompoundData("type","mcfpp")
    }
}