package top.mcfpp.core.lang

import top.mcfpp.command.Command
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.ChatComponent
import top.mcfpp.lib.ListChatComponent
import top.mcfpp.lib.NBTChatComponent
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

/**
 * JsonText代表了Minecraft中的富文本格式，即文本组件，又叫原始JSON文本。在MCFPP中，使用类型text来定义。
 *
 * 原始JSON文本有多种格式，但是对于MCFPP的text，其原始JSON文本永远只会是列表形式。
 *
 * 对于非编译期的JSONText，其本质必然是一个和原始JSON文本格式一致的NBT列表数据结构。因此，可以使用整数索引访问原始JSON文本中的每一个部分。
 *
 * 访问所得的依然是一个text。
 *
 *
 */
open class JsonText : NBTBasedData {

    var isElement = false

    override var type: MCFPPType = MCFPPBaseType.JsonText

    /**
     * 创建一个int值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: JsonText) : super(b)

    override fun doAssignedBy(b: Var<*>): NBTBasedData {
        when (b) {
            is JsonTextConcrete -> return JsonTextConcrete(this, b.value)
            is JsonText -> {
                assignCommand(b)
                return JsonText(this)
            }
            else -> LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
        }
        return this
    }

    override fun clone(): NBTBasedData {
        return JsonText(this)
    }

    override fun getTempVar(): JsonText {
        val temp = JsonText()
        temp.isTemp = true
        temp.nbtPath = NBTPath.temp.memberIndex(temp.identifier)
        if (this is JsonTextConcrete) {
            val payload = if (value is ListChatComponent) value else value.toListComponent()
            Function.addCommand(Command.build("data modify").build(temp.nbtPath.toCommandPart())
                .build("set value").build(payload.toCommandPart()))
        } else temp.assignCommand(this)
        return temp
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        if(isElement){
            throw IllegalArgumentException("Cannot get index of text element")
        }
        return when(index){
            is MCInt -> PropertyVar(Property.buildSimpleProperty(getByIntIndex(index)),getByIntIndex(index), this)
            else -> throw IllegalArgumentException("Invalid index type ${index.type}")
        }
    }

    override fun getByIntIndex(index: MCInt): NBTBasedData {
        val re = JsonText(this)
        re.nbtPath.intIndex(index)
        re.isElement = true
        return re
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val v = MCFPPBaseType.JsonText.instanceData.getVar(key)
        if(!isElement) v?.nbtPath?.iteratorIndex()
        v?.nbtPath?.memberIndex(key)
        return v to true
    }

    override fun toCommandPart(): Command{
        return NBTChatComponent(this, true).toCommandPart()
    }

    override fun plus(a: Var<*>): Var<*> {
        val result = getTempVar()
        return when(a){
            is JsonTextConcrete -> {
                val components = (a.value as? ListChatComponent)?.components ?: listOf(a.value)
                for (component in components) {
                    Function.addCommand(Command.build("data modify").build(result.nbtPath.toCommandPart())
                        .build("append value").build(component.toCommandPart()))
                }
                result
            }
            is JsonText -> {
                Function.addCommand(
                    Command.build("data modify")
                        .build(result.nbtPath.toCommandPart())
                        .build("append from")
                        .build(a.nbtPath.iteratorIndex().toCommandPart())
                )
                result
            }
            else -> errorOp()
        }
    }
}

class JsonTextConcrete : MCFPPValue<ChatComponent>, JsonText {

    override var value: ChatComponent

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: ChatComponent, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = copyComponents(value)
    }

    constructor(jsonText: JsonText, value: ChatComponent) : super(jsonText){
        this.value = copyComponents(value)
    }

    constructor(int: JsonTextConcrete) : super(int){
        this.value = copyComponents(int.value)
    }

    override fun clone(): JsonTextConcrete = JsonTextConcrete(this)

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        val v = if(value is ListChatComponent) value else value.toListComponent()
        val cmd = Command.build("data modify")
            .build(nbtPath.toCommandPart())
            .build("set value ")
            .build(v.toCommandPart())
        Function.addCommand(cmd)
        val re = JsonText(this)
        if(replace){
            if(parentTemplate() != null){
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else{
                Function.currFunction.scope.putVar(identifier, re, true)
            }
        }
        return re
    }

    override fun toCommandPart(): Command {
        return value.toCommandPart()
    }

    override fun plus(a: Var<*>): Var<*> {
        return when(a){
            is JsonTextConcrete -> {
                val result = copyComponents(value)
                result.components.addAll((a.value as? ListChatComponent)?.components ?: listOf(a.value))
                JsonTextConcrete(result)
            }
            is JsonText -> super.plus(a)
            else -> errorOp()
        }
    }

    private fun copyComponents(component: ChatComponent): ListChatComponent = ListChatComponent().apply {
        components.addAll((component as? ListChatComponent)?.components ?: listOf(component))
    }
}
