package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.primitive.LongTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

open class MCLong: NBTBasedData {

    override var type: MCFPPType = MCFPPNBTType.Long

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    constructor(b: MCLong) : super(b)

    override fun explicitCast(type: MCFPPType): Var<*> {
        val re = super.explicitCast(type)
        if(!re.isError) return re
        return when(type){
            MCFPPBaseType.Int -> {
                val ret = MCInt()
                Function.addCommand(
                    Command("execute store result score ${ret.name} ${ret.sbObject} run").build(Commands.dataGet(nbtPath))
                )
                ret
            }
            MCFPPNBTType.Byte -> {
                val ret = MCByte()
                Function.addCommand(
                    Command("execute store result score ${ret.name} ${ret.sbObject} run").build(Commands.dataGet(nbtPath))
                )
                ret
            }
            MCFPPNBTType.Short -> {
                val ret = MCShort()
                Function.addCommand(
                    Command("execute store result score ${ret.name} ${ret.sbObject} run").build(Commands.dataGet(nbtPath))
                )
                ret
            }
            else -> re
        }
    }

    override fun canExplicitCast(type: MCFPPType): Boolean {
        return type == MCFPPBaseType.Int || type == MCFPPNBTType.Byte || type == MCFPPNBTType.Short || super.canExplicitCast(type)
    }

    @Override
    @Throws(VariableConverseException::class)
    override fun doAssignedBy(b: Var<*>): MCLong {
        when (b) {
            is MCLong -> return assignCommand(b)
            is NBTBasedDataConcrete -> {
                if(b.nbtType == NBTBasedData.Companion.NBTTypeWithTag.LONG){
                    return assignCommand(b)
                }else{
                    LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                }
            }
            else -> LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
        }
        return this
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        if(!b.implicitCast(type).isError) return true
        if(b is NBTBasedDataConcrete){
            return b.nbtType == NBTBasedData.Companion.NBTTypeWithTag.LONG
        }
        return false
    }

    @InsertCommand
    override fun assignCommand(a: NBTBasedData) : MCLong {
        nbtType = a.nbtType
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete = {b, final ->
                b as MCLongConcrete
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
                MCLong(this)
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
                MCLong(this)
            },
            ifThisIsNormalVarAndAIsConcrete = {b ->
                MCLongConcrete(this, (b as MCLongConcrete).value)
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
                MCLong(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = {b ->
                Function.addCommand(Commands.dataSetFrom(nbtPath, b.nbtPath))
                MCLong(this)
            }) as MCLong
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }

}


class MCLongConcrete: MCLong, MCFPPValue<LongTag> {

    override var value: LongTag

    /**
     * 创建一个固定的string。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: LongTag, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    constructor(v: MCLong, value: LongTag) : super(v) {
        this.value = value
    }

    constructor(v: MCLongConcrete) : super(v) {
        this.value = v.value
    }

    override fun clone(): MCLongConcrete {
        return MCLongConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, value).toDynamic(replace)
        return MCLong(this)
    }
}
