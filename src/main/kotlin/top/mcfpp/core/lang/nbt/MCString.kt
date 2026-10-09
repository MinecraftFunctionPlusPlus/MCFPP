package top.mcfpp.core.lang.nbt

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.JsonText
import top.mcfpp.lib.NBTChatComponent
import top.mcfpp.lib.PlainChatComponent
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

/** String data is immutable when known and otherwise read from the bound producer. */
open class MCString : NBTBasedData {
    override var type: MCFPPType = MCFPPBaseType.String
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(source: MCString) : super(source)
    constructor(value: StringTag, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Text(value.value))
    }
    override val value: StringTag
        get() {
            val closed = (StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.Text
                ?: error("String has no complete immutable value")
            return StringTag(closed.value)
        }
    override fun doAssignedBy(source: Var<*>): MCString = StorageAccess.write(this, source) as MCString
    override fun assignCommand(source: NBTBasedData): MCString = StorageAccess.write(this, source) as MCString
    override fun explicitCast(type: MCFPPType): Var<*> = StorageAccess.view(this, type)
    override fun implicitCast(type: MCFPPType): Var<*> {
        if (type == MCFPPBaseType.JsonText) {
            val closed = (StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.Text
            return if (closed != null) JsonText(PlainChatComponent(closed.value))
            else JsonText(NBTChatComponent(this, false))
        }
        return super.implicitCast(type)
    }
    override fun canImplicitCast(type: MCFPPType) = type == MCFPPBaseType.JsonText || super.canImplicitCast(type)
    override fun clone(): MCString = MCString(this)
    override fun getTempVar(): MCString = StorageAccess.capture(this) as MCString
    override fun toCommandPart(): Command {
        val closed = (StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.Text
        return if (closed != null) Command(closed.value) else super.toCommandPart()
    }
    companion object { val data = CompoundData("string", "mcfpp") }
}
