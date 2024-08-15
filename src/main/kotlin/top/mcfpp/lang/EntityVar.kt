package top.mcfpp.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.IntArrayTag
import net.querz.nbt.tag.Tag
import top.mcfpp.Project
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.MCFPPBaseType.BaseEntity
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPClassType
import top.mcfpp.lang.type.MCFPPNBTType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import java.util.UUID


/**
 * 代表了一个实体。一个实体类型的变量通常是一个UUID数组，可以通过Thrower法来选择实体，从而实现对实体的操作。
 *
 */
open class EntityVar : NBTBasedData<IntArrayTag>{

    override var type: MCFPPType = BaseEntity

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


    /**
     * 将b中的值赋值给此实体变量
     * @param b 变量的对象
     */
    override fun assign(b: Var<*>): EntityVar {
        hasAssigned = true
        when (b) {
            is EntityVar -> {
                assignCommand(b)
            }
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
            }
        }
        return this
    }

    companion object {
        val data = CompoundData("entity","mcfpp")
    }

}

class EntityVarConcrete: EntityVar, MCFPPValue<IntArrayTag>{

    override var value: IntArrayTag

    constructor(curr: FieldContainer, value: IntArrayTag) : super(curr){
        this.value = value
    }

    constructor(value: IntArrayTag) : super(){
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
                "data modify entity @s data.${identifier} set value ${SNBTUtil.toSNBT(value)}")
            )
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value ${SNBTUtil.toSNBT(value)}")
            Function.addCommand(cmd)
        }
        val re = EntityVar(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    override fun toString(): String {
        return "[$type,value=${SNBTUtil.toSNBT(value)}]"
    }
}