package top.mcfpp.core.lang

import top.mcfpp.command.Commands
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeRelations
import top.mcfpp.util.TempPool

/** Statically abstract erased payload. The payload's concrete members are never exposed. */
class MCObject(identifier: String = TempPool.getVarIdentify()) : MCAny(identifier) {
    override var type: MCFPPType = MCFPPBaseType.Object

    override fun doAssignedBy(b: Var<*>): MCObject {
        val payload = when (b) {
            is MCObject -> b.lastVar
            is MCAny -> b.lastVar
            else -> b
        }
        lastVar = payload
        // Compiler-only payloads can be passed during specialization, but cannot be stored.
        if (payload != null) {
            if (!payload.type.hasRuntimeRepresentation) return this
            val tag = if (top.mcfpp.analysis.ValueSnapshot.of(payload) != null) top.mcfpp.util.NBTUtil.varToNBT(payload) else null
            if (tag != null) {
                Function.addCommand(Commands.dataSetValue(nbtPath, tag))
            } else {
                payload.storeToStack()
                Function.addCommand(Commands.dataSetFrom(nbtPath, payload.nbtPath))
            }
        } else {
            Function.addCommand(Commands.dataSetFrom(nbtPath, b.nbtPath))
        }
        return this
    }

    override fun canImplicitCast(type: MCFPPType): Boolean = TypeRelations.resolveImplicitConversion(this.type, type) != null

    override fun implicitCast(type: MCFPPType): Var<*> = when (type) {
        MCFPPBaseType.Object -> this
        MCFPPBaseType.Any -> MCAny().apply { setAs(this@MCObject); lastVar = this@MCObject.lastVar }
        else -> Var.buildCastErrorVar(type)
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier) = type.getMemberVar(key, accessModifier)
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, accessModifier: Member.AccessModifier) =
        type.getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)

    override fun clone(): MCObject = MCObject(identifier).apply { setAs(this@MCObject); lastVar = this@MCObject.lastVar }
    override fun getTempVar(): MCObject = this
    override fun storeToStack() {
        if (lastVar?.type?.hasRuntimeRepresentation == false) {
            top.mcfpp.util.LogProcessor.error("Compiler-only value '${lastVar!!.type}' cannot be materialized as an object runtime payload")
        }
    }
    override fun getFromStack() {}
}
