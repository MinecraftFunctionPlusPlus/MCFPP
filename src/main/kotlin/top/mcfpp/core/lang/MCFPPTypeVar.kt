package top.mcfpp.core.lang

import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPCompoundType
import top.mcfpp.type.MCFPPConcreteType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

class MCFPPTypeVar : Var<MCFPPTypeVar>{

    override var type: MCFPPType = MCFPPConcreteType.Type

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    constructor(type: MCFPPType, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        top.mcfpp.analysis.StorageAccess.initializeLiteral(this, top.mcfpp.analysis.CompilerValue.TypeValue(type.typeId),
            mapOf(type.typeId to type))
    }

    val value: MCFPPType get() = top.mcfpp.analysis.StorageAccess.resolveTypeValue(this)
        ?: error("Type declaration has no complete value")

    override fun doAssignedBy(b: Var<*>) : MCFPPTypeVar {
        if(b is MCFPPTypeVar){
            top.mcfpp.analysis.StorageAccess.write(this, b)
        } else {
            LogProcessor.error("Cannot assign a ${b.type} to a MCFPPTypeVar")
        }
        return this
    }

    override fun clone(): MCFPPTypeVar {
        return MCFPPTypeVar(identifier).apply { setAs(this@MCFPPTypeVar) }
    }

    override fun getTempVar(): MCFPPTypeVar {
        return top.mcfpp.analysis.StorageAccess.capture(this) as MCFPPTypeVar
    }

    override fun storeToStack() { LogProcessor.error("Type values have no runtime storage representation") }

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val descriptor = top.mcfpp.analysis.StorageAccess.resolveTypeValue(this) ?: run {
            LogProcessor.error("Type value requires a complete readonly binding")
            return UnknownVar(key) to false
        }
        return descriptor.getMemberVar(key, accessModifier)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        val descriptor = top.mcfpp.analysis.StorageAccess.resolveTypeValue(this) ?: run {
            LogProcessor.error("Type value requires a complete readonly binding")
            return top.mcfpp.model.function.UnknownFunction(key) to false
        }
        return descriptor.getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)
    }

    override fun replaceMemberVar(v: Var<*>) {
        when(val type = top.mcfpp.analysis.StorageAccess.resolveTypeValue(this)){
            is MCFPPCompoundType -> {
                type.objectData.scope.putVar(v.identifier, v, true)
            }
            else -> LogProcessor.error("Type value does not expose a mutable static declaration '${v.identifier}'")
        }
    }
}
