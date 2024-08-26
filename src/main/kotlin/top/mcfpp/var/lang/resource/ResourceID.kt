package top.mcfpp.`var`.lang.resource

import net.querz.nbt.tag.StringTag
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.`var`.lang.*
import top.mcfpp.type.MCFPPType
import top.mcfpp.`var`.lang.MCFPPValue
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.type.MCFPPResourceType
import java.util.*

open class ResourceID : NBTBasedData<StringTag> {

    override var type: MCFPPType = MCFPPResourceType.ResourceID

    /**
     * 创建一个命名空间ID类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.identifier = identifier
    }

    /**
     * 创建一个命名空间ID值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: ResourceID) : super(b)

    override fun explicitCast(type: MCFPPType): Var<*> {
        when(type){
            MCFPPResourceType.ResourceID -> return this
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                return UnknownVar(this.identifier)
            }
        }
    }

    companion object {
        val data = CompoundData("ResourceID","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(NBTBasedData.data)
        }
    }
}

open class ResourceIDConcrete: MCFPPValue<String>, ResourceID{

    override var value: String

    constructor(
        curr: FieldContainer,
        value: String,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.value = value
    }

    constructor(value: String, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        this.value = value
    }

    constructor(id: ResourceID, value: String) : super(id){
        this.value = value
    }

    constructor(id: ResourceIDConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): ResourceIDConcrete {
        return ResourceIDConcrete(this)
    }

    override fun getTempVar(): ResourceIDConcrete {
        return ResourceIDConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        if (parent != null) {
            val cmd = Commands.selectRun(parent, "data modify entity @s data.${identifier} set value $value")
            Function.addCommands(cmd)
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value $value")
            Function.addCommand(cmd)
        }
        val re = NBTBasedData(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }

}