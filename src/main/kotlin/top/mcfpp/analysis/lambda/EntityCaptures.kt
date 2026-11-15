package top.mcfpp.analysis.lambda

import top.mcfpp.analysis.*
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.ref.ReferenceKind
import top.mcfpp.core.lang.ref.ReferenceValues
import top.mcfpp.core.lang.ref.ReferenceVar
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*

/** A runtime entity capture owns its target operands through the existing reference descriptor ABI. */
internal object EntityCaptures {
    fun dynamic(value: Var<*>) = value.type is MCFPPEntityType && StorageAccess.snapshot(value) == null

    private fun descriptor(value: Var<*>) = ReferenceValues.create(MCFPPNBTRefType(MCFPPNBTType.NBT),
        ReferenceKind.NBT_ENTITY, mapOf("path" to MCString(StringTag("UUID"))), value)

    fun encode(value: Var<*>, destination: StorageBinding) {
        val reference = descriptor(value)
        StorageAccess.write(StorageAccess.adapter(reference.type, "entityCapture", destination), reference)
    }

    fun alias(value: Var<*>, name: String, environment: StorageBinding): Var<*> {
        // Only the shape/evidence is needed here. Creator operand copies execute when the
        // FunctionValue environment is built, never in the later callback frame.
        lateinit var shape: ReferenceVar
        Commands.fakeFunction(Function.currFunction) { shape = descriptor(value) }
        StorageAccess.copyRuntimeEvidence(StorageAccess.ensure(shape), environment)
        val reference = StorageAccess.adapter(shape.type, name, environment)
        val target = requireNotNull(ReferenceValues.entityTarget(reference)) { "Entity capture has no target shape" }
        return SelectorVar(name).apply {
            type = value.type
            StorageAccess.updateEntityTarget(this, target)
        }
    }
}
