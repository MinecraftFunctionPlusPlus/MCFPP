package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import java.util.*

open class MCFPPTypeVar : Var<MCFPPType>, MCFPPValue<MCFPPType> {

    override var value: MCFPPType

    override var type: MCFPPType = MCFPPBaseType.Type

    constructor(type: MCFPPType = MCFPPBaseType.Any, identifier: String = UUID.randomUUID().toString()) : super(
        identifier
    ) {
        this.value = type
    }

    override fun assign(b: Var<*>): MCFPPTypeVar {
        if (b is MCFPPTypeVar) {
            this.value = b.value
            hasAssigned = true
        } else {
            throw Exception("Cannot assign a ${b.type} to a MCFPPTypeVar")
        }
        return this
    }

    override fun cast(type: MCFPPType): Var<*> {
        if (type.typeName == "type") {
            return this
        } else {
            throw Exception("Cannot cast a ${type.typeName} to a MCFPPTypeVar")
        }
    }

    override fun clone(): Var<*> {
        return this
    }

    override fun getTempVar(): Var<*> {
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
        val data = CompoundData("type", "mcfpp")
    }
}