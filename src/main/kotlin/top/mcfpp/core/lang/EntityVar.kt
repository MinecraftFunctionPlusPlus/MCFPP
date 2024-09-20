package top.mcfpp.core.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.IntArrayTag
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.type.MCFPPEntityType.Entity
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*


/**
 * 代表了一个实体。一个实体类型的变量通常是一个UUID数组，可以通过Thrower法来选择实体，从而实现对实体的操作。
 *
 */
open class EntityVar : NBTBasedData{

    override var type: MCFPPType = Entity

    /**
     * 创建一个entity类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(curr.prefix + identifier) {
        this.identifier = identifier
    }

    /**
     * 创建一个entity值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
    }

    /**
     * 复制一个entity
     * @param b 被复制的entity值
     */
    constructor(b: EntityVar) : super(b)

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return Pair(SelectorVar.data.getVar(key), false)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return SelectorVar.data.getFunction(key, readOnlyParams, normalParams) to true
    }

    override fun doAssign(b: Var<*>): EntityVar {
        when (b) {
            is EntityVar -> {
                assignCommand(b)
            }
            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
            }
        }
        return this
    }

    companion object {
        val data by lazy {
            CompoundData("entity","mcfpp")
        }
    }

}

class EntityVarConcrete: EntityVar, MCFPPValue<IntArrayTag> {

    override var value: IntArrayTag

    constructor(curr: FieldContainer, value: IntArrayTag, identifier: String = UUID.randomUUID().toString()) : super(curr, identifier){
        this.value = value
    }


    constructor(value: IntArrayTag, identifier: String = UUID.randomUUID().toString()) : super(identifier){
        this.value = value
    }

    constructor(b: EntityVar, value: IntArrayTag) : super(b){
        this.value = value
    }

    constructor(b: EntityVarConcrete): super(b){
        this.value = b.value
    }

    override fun clone(): EntityVar {
        return EntityVarConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        if (parentClass() != null) {
            val cmd = Commands.selectRun(parent!!, "data modify entity @s data.${identifier} set value ${SNBTUtil.toSNBT(value)}")
            Function.addCommands(cmd)
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value ${SNBTUtil.toSNBT(value)}")
            Function.addCommand(cmd)
        }
        val re = EntityVar(this)
        if(replace){
            if(parentTemplate() != null){
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else{
                Function.currFunction.field.putVar(identifier, re, true)
            }
        }
        return re
    }

    override fun toString(): String {
        return "[$type,value=${SNBTUtil.toSNBT(value)}]"
    }
}