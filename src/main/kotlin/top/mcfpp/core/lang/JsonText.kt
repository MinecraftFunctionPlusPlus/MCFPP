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
import top.mcfpp.model.Member
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.property.AbstractAccessor
import top.mcfpp.model.property.AbstractMutator
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
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val string = key in setOf("color", "font", "insertion")
        if (!string && key !in setOf("bold", "italic", "underlined", "strikethrough", "obfuscated"))
            return super.getMemberVar(key, accessModifier)
        val field = (if (string) MCFPPBaseType.String else MCFPPBaseType.Bool).buildUnConcrete(key)
        field.isConst = isConst
        val property = Property(key, object : AbstractAccessor() {
            override fun getter(caller: CanSelectMember, field: Var<*>): Var<*> =
                top.mcfpp.backend.StandardTextOperations.readStyle(this@JsonText, key)
        }, object : AbstractMutator() {
            override fun setter(caller: CanSelectMember, field: Var<*>, b: Var<*>): Var<*> {
                val changed = top.mcfpp.backend.StandardTextOperations.withField(this@JsonText, key, b)
                if (!changed.isError) StorageAccess.writeReceiver(this@JsonText, changed)
                return if (changed.isError) changed else StorageAccess.capture(b)
            }
        })
        return PropertyVar(property, field, this) to true
    }
    override fun getByIndex(index: Var<*>): PropertyVar {
        val element = getByIntIndex(index as MCInt)
        return PropertyVar(Property.buildSimpleProperty(element), element, this)
    }
    override fun getByIntIndex(index: MCInt): NBTBasedData =
        (StorageAccess.element(this, index, MCFPPBaseType.JsonText) as JsonText).also { it.isElement = true }
    override fun toCommandPart(): Command {
        val encoded = StorageAccess.constantEncoding(this)
        if (encoded != null) return Command(if (top.mcfpp.backend.StandardTextOperations.modernEvents)
            top.mcfpp.backend.NbtEncoding.snbt(encoded) else top.mcfpp.backend.StandardTextOperations.json(encoded))
        if (!top.mcfpp.backend.StandardTextOperations.modernEvents) {
            top.mcfpp.util.LogProcessor.error("Displaying runtime structured text requires Minecraft 1.21.5 or newer; constant text works on older targets")
            return Command("\"\"")
        }
        return NBTChatComponent(this, true).toCommandPart()
    }
    override fun plus(a: Var<*>): Var<*> {
        if (a !is JsonText) return errorOp()
        return top.mcfpp.backend.StandardTextOperations.concatenate(this, a)
    }
}
