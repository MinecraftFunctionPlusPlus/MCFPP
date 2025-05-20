package top.mcfpp.core.lang

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.ChatComponent
import top.mcfpp.lib.ListChatComponent
import top.mcfpp.lib.NBTChatComponent
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
            is JsonText -> assignCommand(b)
            else -> LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
        }
        return this
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun clone(): NBTBasedData {
        return JsonText(this)
    }

    override fun getTempVar(): JsonText {
        val temp = JsonText()
        temp.isTemp = true
        return temp.assignCommand(this) as JsonText
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
        return when(a){
            is JsonTextConcrete -> {
                if(parentClass()!= null){
                    val cmd = Commands.selectRun(parent!!,
                        Command("data modify").build(nbtPath.toCommandPart()).build("append value")
                           .build(a.toCommandPart())
                    )
                    Function.addCommands(cmd)
                }else{
                    val cmd = Command.build("data modify")
                        .build(nbtPath.toCommandPart())
                        .build("append value")
                        .build(a.toCommandPart())
                    Function.addCommand(cmd)
                }
                this
            }
            is JsonText -> {
                if(parentClass()!= null){
                    val b = if(a.parentClass() != null) a.getTempVar() else a
                    val cmd = Commands.selectRun(parent!!,
                        Command("data modify").build(nbtPath.toCommandPart()).build("append from")
                          .build(b.nbtPath.toCommandPart())
                    )
                    Function.addCommands(cmd)
                }else{
                    val cmd = if(a.parentClass()!= null){
                        Commands.selectRun(a.parent!!,
                            Command("data modify").build(nbtPath.toCommandPart()).build("append from")
                                .build(a.nbtPath.toCommandPart()))
                    } else {
                        arrayOf(Command.build("data modify")
                            .build(nbtPath.toCommandPart())
                            .build("append from")
                            .build(a.nbtPath.toCommandPart()))
                    }
                    Function.addCommands(cmd)
                }
                this
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
        val v = if(value is ListChatComponent) value else value.toListComponent()
        if (parentClass() != null) {
            val cmd = Commands.selectRun(parent!!,
                Command("data modify entity @s data.${identifier} set value ")
                    .build(v.toCommandPart())
            )
            Function.addCommands(cmd)
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value ")
                .build(v.toCommandPart())
            Function.addCommand(cmd)
        }
        val re = NBTBasedData(this)
        if(replace){
            if(parentTemplate() != null){
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else{
                Function.currFunction.field.putVar(identifier, re, true)
            }
        }
        return re
    }

    override fun toCommandPart(): Command {
        return value.toCommandPart()
    }

    override fun plus(a: Var<*>): Var<*> {
        when(a){
            is JsonTextConcrete -> {
                val v = value as? ListChatComponent ?: value.toListComponent()
                if(a.value is ListChatComponent){
                    v.append((a.value as ListChatComponent).components)
                }else{
                    v.append(a.value)
                }
                value = v
            }
            is JsonText -> {
                val v = value as? ListChatComponent?: value.toListComponent()
                v.append(NBTChatComponent(a, true))
                value = v
            }
            else -> errorOp()
        }
        return this
    }
}