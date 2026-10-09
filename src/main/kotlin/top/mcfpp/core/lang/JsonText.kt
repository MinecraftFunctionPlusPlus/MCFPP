package top.mcfpp.core.lang

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.lib.ChatComponent
import top.mcfpp.lib.ListChatComponent
import top.mcfpp.lib.NBTChatComponent
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

/** A typed access to the actual list-form Minecraft text payload. */
class JsonText : NBTBasedData {
    var isElement = false
    override var type: MCFPPType = MCFPPBaseType.JsonText
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(other: JsonText) : super(other) { isElement = other.isElement }
    constructor(component: ChatComponent, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        val command = (if (component is ListChatComponent) component else component.toListComponent()).toCommandPart()
        if (!command.isMacro) {
            StorageAccess.initializeLiteral(this, CompilerValue.Nbt(command.toString()))
        } else {
            StorageAccess.ensure(this)
            Function.addCommands(Command("data modify").build(nbtPath.toCommandPart()).build("set value").build(command).buildMacroFunction())
            StorageAccess.publishNbt(this)
        }
    }
    constructor(other: JsonText, component: ChatComponent) : this(component, other.identifier)
    override fun doAssignedBy(b: Var<*>): NBTBasedData { StorageAccess.write(this, b); return this }
    override fun clone() = JsonText(this)
    override fun getTempVar() = StorageAccess.capture(this) as JsonText
    override fun getByIndex(index: Var<*>): PropertyVar {
        val element = getByIntIndex(index as MCInt)
        return PropertyVar(Property.buildSimpleProperty(element), element, this)
    }
    override fun getByIntIndex(index: MCInt): NBTBasedData =
        (StorageAccess.element(this, index, MCFPPBaseType.JsonText) as JsonText).also { it.isElement = true }
    override fun toCommandPart(): Command = StorageAccess.constantEncoding(this)?.let { Command(top.mcfpp.backend.NbtEncoding.snbt(it)) }
        ?: NBTChatComponent(this, true).toCommandPart()
    override fun plus(a: Var<*>): Var<*> {
        if (a !is JsonText) return errorOp()
        val result = getTempVar()
        StorageAccess.materialize(result)
        StorageAccess.materialize(a)
        Function.addCommands(Command("data modify").build(result.nbtPath.toCommandPart()).build("append from")
            .build(a.nbtPath.iteratorIndex().toCommandPart()).buildMacroFunction())
        StorageAccess.publishNbt(result)
        return result
    }
}
