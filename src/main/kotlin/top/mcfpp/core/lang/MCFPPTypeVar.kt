package top.mcfpp.core.lang

import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPCompoundType
import top.mcfpp.type.MCFPPConcreteType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

class MCFPPTypeVar : ConcreteVar<MCFPPTypeVar, MCFPPType>{

    override var type: MCFPPType = MCFPPConcreteType.Type

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(type: MCFPPType = MCFPPBaseType.Any, identifier: String = TempPool.getVarIdentify()) : super(identifier, type) {
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
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        TODO("Not yet implemented")
    }

    override fun replaceMemberVar(v: Var<*>) {
        when(val type = type){
            is MCFPPCompoundType -> {
                type.objectData.scope.putVar(v.identifier, v, true)
            }
            else -> TODO()
        }
    }
}