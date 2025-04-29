package top.mcfpp.core.lang.entity

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.DataTemplateObject
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

open class SpecifiedEntityVar: NBTBasedData {

    var isName: Boolean = false

    override var type: MCFPPType = MCFPPEntityType.SpecifiedEntity

    /**
     * 创建一个string值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个string
     * @param b 被复制的string值
     */
    constructor(b: SpecifiedEntityVar) : super(b){
        isName = b.isName
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

    override fun doAssignedBy(b: Var<*>): SpecifiedEntityVar {
        when (b) {
            is SpecifiedEntityVar -> return assignCommand(b)
            else -> LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
        }
        return this
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        if(!b.implicitCast(type).isError) return true
        if(b is NBTBasedDataConcrete){
            return b.nbtType == NBTBasedData.Companion.NBTTypeWithTag.STRING
        }
        return false
    }

    @InsertCommand
    override fun assignCommand(a: NBTBasedData) : SpecifiedEntityVar {
        nbtType = a.nbtType
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete = {b, final ->
                b as SpecifiedEntityConcreteVar
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
                SpecifiedEntityVar(this)
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
                SpecifiedEntityVar(this)
            },
            ifThisIsNormalVarAndAIsConcrete = {b, _ ->
                SpecifiedEntityConcreteVar(this, (b as MCStringConcrete).value)
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
                SpecifiedEntityVar(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = {b, _ ->
                Function.addCommand(Commands.dataSetFrom(nbtPath, b.nbtPath))
                NBTBasedData(this)
            }) as SpecifiedEntityVar
    }

    override fun getTempVar(): SpecifiedEntityVar {
        val temp = SpecifiedEntityVar()
        temp.isTemp = true
        return temp.assignCommand(this)
    }

    override fun toCommandPart(): Command {
        return Command().buildMacro(this)
    }

    companion object {
        val data = CompoundData("entity","mcfpp")

        val uuidRegex = Regex("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}\$")

    }
}

class SpecifiedEntityConcreteVar: SpecifiedEntityVar, MCFPPValue<StringTag> {

    override var value: StringTag

    /**
     * 创建一个固定的string。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: StringTag, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    constructor(v: SpecifiedEntityVar, value: StringTag): super(v){
        this.value = value
    }

    constructor(v: SpecifiedEntityConcreteVar) : super(v){
        this.value = v.value
    }

    override fun clone(): SpecifiedEntityConcreteVar {
        return SpecifiedEntityConcreteVar(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, value)
        val re = SpecifiedEntityVar(this)
        if(replace){
            if(parentTemplate() != null){
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else {
                Function.currFunction.field.putVar(identifier, re, true)
            }
        }
        return re
    }

    override fun getTempVar(): SpecifiedEntityVar {
        return SpecifiedEntityConcreteVar(value).apply {
            isTemp = true
        }
    }

    override fun toCommandPart(): Command {
        return Command(value.value)
    }
}