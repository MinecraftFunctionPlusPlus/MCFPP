package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.Member

class MCFPPTypeVar : Var<MCFPPType> {


    override var javaValue: MCFPPType? = null

    override var type: MCFPPType = MCFPPBaseType.Type

    constructor(identifier: String): super(identifier)

    constructor(identifier: String, type: MCFPPType) : super(identifier) {
        this.javaValue = type
    }

    constructor(type: MCFPPType) : super(){
        this.javaValue = type
    }

    override fun assign(b: Var<*>?) {
        if(b is MCFPPTypeVar){
            this.javaValue = b.javaValue
        }else{
            throw Exception("Cannot assign a ${b?.type} to a MCFPPTypeVar")
        }
    }

    override fun cast(type: MCFPPType): Var<*> {
        if(type.typeName == "type"){
            return this
        }else{
            throw Exception("Cannot cast a ${type.typeName} to a MCFPPTypeVar")
        }
    }

    override fun clone(): Var<*> {
        return this
    }

    override fun getTempVar(): Var<*> {
        return MCFPPTypeVar(javaValue!!)
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun toDynamic() {}

    override fun getVarValue(): Any? = javaValue

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

    companion object {
        val data = CompoundData("type","mcfpp")
    }
}