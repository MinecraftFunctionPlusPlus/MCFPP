package top.mcfpp.core.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.DoubleTag
import net.querz.nbt.tag.StringTag
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import java.util.*

open class MCDouble: NBTBasedData {
    
    /**
     * 创建一个double类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个double值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: MCDouble) : super(b)

    @InsertCommand
    override fun assignCommand(a: NBTBasedData) : MCDouble {
        nbtType = a.nbtType
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete = {b, final ->
                b as MCDoubleConcrete
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetValue(nbtPath, b.value))
                if(final.last().isMacro){
                    Function.addCommands(final.last().buildMacroFunction())
                }else{
                    Function.addCommand(final.last())
                }
                MCDouble(this)
            },
            ifThisIsClassMemberAndAIsNotConcrete = {b, final ->
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetFrom(nbtPath, b.nbtPath))
                if(final.last().isMacro){
                    Function.addCommands(final.last().buildMacroFunction())
                }else{
                    Function.addCommand(final.last())
                }
                MCDouble(this)
            },
            ifThisIsNormalVarAndAIsConcrete = {b, _ ->
                MCDoubleConcrete(this, (b as MCDoubleConcrete).value)
            },
            ifThisIsNormalVarAndAIsClassMember = {b, final ->
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetFrom(nbtPath, b.nbtPath))
                if(final.last().isMacro){
                    Function.addCommands(final.last().buildMacroFunction())
                }else{
                    Function.addCommand(final.last())
                }
                MCDouble(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = {b, _ ->
                Function.addCommand(Commands.dataSetFrom(nbtPath, b.nbtPath))
                NBTBasedData(this)
            }) as MCDouble
    }

    companion object {

        val data by lazy {
            CompoundData("double","mcfpp")
        }
    }
}


class MCDoubleConcrete: MCDouble, MCFPPValue<DoubleTag> {

    override var value: DoubleTag

    /**
     * 创建一个固定的string
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: DoubleTag,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的string。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: DoubleTag, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        this.value = value
    }

    constructor(v: MCDouble, value: DoubleTag): super(v){
        this.value = value
    }

    constructor(v: MCDoubleConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): MCDoubleConcrete {
        return MCDoubleConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        if (parentClass() != null) {
            val cmd = Commands.selectRun(parent!!, "data modify entity @s data.${identifier} set value ${SNBTUtil.toSNBT(value)}")
            Function.addCommands(cmd)
        } else {
            val cmd = Command.build(
                "data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[$stackIndex].$identifier set value ${SNBTUtil.toSNBT(value)}"
            )
            Function.addCommand(cmd)
        }
        val re = MCDouble(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    @Override
    override fun explicitCast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.Double -> this
            MCFPPNBTType.NBT -> NBTBasedDataConcrete(value)
            MCFPPBaseType.Any -> MCAnyConcrete(this)
            else -> throw VariableConverseException()
        }
    }
}