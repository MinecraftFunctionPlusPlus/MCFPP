package top.mcfpp.lang

import net.querz.nbt.tag.CompoundTag
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPClassType
import top.mcfpp.lang.type.MCFPPNBTType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.lib.ChatComponent
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import java.util.*

/**
 * JsonText代表了Minecraft中的富文本格式，即文本组件，又叫原始JSON文本。在MCFPP中，使用类型jtext来定义。
 *
 * 原始JSON文本有多种格式，但是对于MCFPP的jtext，其原始JSON文本永远只会是列表形式。
 *
 * 对于非编译期的JSONText，其本质是一个和原始JSON文本格式一致的NBT列表数据结构。因此，可以使用整数索引访问原始JSON文本中的每一个部分。
 *
 * 访问所得的依然是一个jtext。
 *
 */
open class JsonText : NBTBasedData<CompoundTag> {

    var isElement = false

    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(curr.prefix + identifier) {
        this.identifier = identifier
    }

    /**
     * 创建一个int值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: JsonText) : super(b)

    override fun assign(b: Var<*>): NBTBasedData<CompoundTag> {
        when(b){
            is JsonText -> assignCommand(b)
            else -> {
                LogProcessor.error("Cannot assign [${b::class.simpleName}] to [${this::class.simpleName}]")
            }
        }
        return this
    }

    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.JText -> this
            MCFPPNBTType.NBT -> NBTBasedData(this)
            MCFPPBaseType.Any -> MCAnyConcrete(this)
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                UnknownVar(this.identifier)
            }
        }
    }

    override fun clone(): NBTBasedData<CompoundTag> {
        return JsonText(this)
    }

    override fun getTempVar(): Var<*> {
        val temp = JsonText()
        temp.isTemp = true
        return temp.assignCommand(this)
    }

    override fun getByIndex(index: Var<*>): NBTBasedData<*> {
        if(isElement){
            throw IllegalArgumentException("Cannot get index of jtext element")
        }
        return when(index){
            is MCInt -> getByIntIndex(index)
            else -> throw IllegalArgumentException("Invalid index type ${index.type}")
        }
    }

    override fun getByIntIndex(index: MCInt): NBTBasedData<*> {
        val re = JsonText(this)
        re.nbtPath.intIndex(index)
        re.isElement = true
        return re
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val v = data.getVar(key)
        if(!isElement) v?.nbtPath?.iteratorIndex()
        v?.nbtPath?.memberIndex(key)
        return v to true
    }

    companion object {
        val data = CompoundData("JsonText","mcfpp.lang")

        init {
            data.initialize()
            data.extends(NBTBasedData.data)

            data.addMember(MCInt("color"))
            data.addMember(MCBool("bold"))
            data.addMember(MCBool("italic"))
            data.addMember(MCBool("underlined"))
            data.addMember(MCBool("strikethrough"))
            data.addMember(MCBool("obfuscated"))
            data.addMember(MCString("insertion"))

        }
    }
}

class JsonTextConcrete : MCFPPValue<ChatComponent>, JsonText {

    override var value: ChatComponent

    /**
     * 创建一个固定的int
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: ChatComponent,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: ChatComponent, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        this.value = value
    }

    constructor(jsonText: JsonText, value: ChatComponent) : super(jsonText){
        this.value = value
    }

    constructor(int: JsonTextConcrete) : super(int){
        this.value = int.value
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        if (parent != null) {
            val cmd = when(parent){
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                is MCFPPClassType -> {
                    arrayOf(Command.build("execute as ${parent.cls.uuid} run "))
                }
                else -> TODO()
            }
            if(cmd.size == 2){
                Function.addCommand(cmd[0])
            }
            Function.addCommand(cmd.last().build(
                "data modify entity @s data.${identifier} set value ")
                .build(value.toCommandPart())
            )
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value ")
                .build(value.toCommandPart())
            Function.addCommand(cmd)
        }
        val re = NBTBasedData(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    companion object {
        val data = CompoundData("JsonTextConcrete","mcfpp.lang")

        init {
            data.initialize()
            data.extends(JsonText.data)
        }
    }

}