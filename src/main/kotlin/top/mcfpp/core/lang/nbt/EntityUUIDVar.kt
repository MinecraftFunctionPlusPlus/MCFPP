package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.minecraft.EntityVarData
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate


/**
 * 代表了一个实体。一个实体类型的变量通常是一个UUID数组，可以通过Thrower法来选择实体，从而实现对实体的操作。
 *
 */
open class EntityUUIDVar : NBTBasedData{

    override var type: MCFPPType = MCFPPNBTType.IntArray

    var isName = false

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    constructor(b: EntityUUIDVar) : super(b)


    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return Pair(data.getVar(key), false)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.getFunction(key, readOnlyArgs, normalArgs) to true
    }

    override fun doAssignedBy(b: Var<*>): EntityUUIDVar {
        when (b) {
            is EntityUUIDVar -> {
                assignCommand(b)
                isName = b.isName
            }

            is MCStringConcrete -> {
                assignCommand(b)
                isName = true
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
            }
        }
        return this
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        if(!b.implicitCast(type).isError) return true
        if(b is MCStringConcrete) return true
        return false
    }

    @InsertCommand
    override fun assignCommand(a: NBTBasedData) : EntityUUIDVar {
        nbtType = a.nbtType
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete = {b, final ->
                b as NBTBasedDataConcrete
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
                EntityUUIDVar(this)
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
                EntityUUIDVar(this)
            },
            ifThisIsNormalVarAndAIsConcrete = {b, _ ->
                EntityUUIDVarConcrete(this, (b as NBTBasedDataConcrete).value)
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
                EntityUUIDVar(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = {b, _ ->
                Function.addCommand(Commands.dataSetFrom(nbtPath, b.nbtPath))
                EntityUUIDVar(this)
            }
        ) as EntityUUIDVar
    }

    companion object {
        val data by lazy {
            CompoundData("uuid","mcfpp").apply {
                injectedBy(EntityVarData::class.java)
            }
        }
    }

}

class EntityUUIDVarConcrete: EntityUUIDVar, MCFPPValue<Tag<*>> {

    override var value: Tag<*>

    constructor(value: Tag<*>, identifier: String = TempPool.getVarIdentify()) : super(identifier){
        this.value = value
    }

    constructor(b: EntityUUIDVar, value: Tag<*>) : super(b){
        this.value = value
    }

    constructor(b: EntityUUIDVarConcrete): super(b){
        this.value = b.value
    }

    override fun clone(): EntityUUIDVar {
        return EntityUUIDVarConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        if (parentClass() != null) {
            val cmd = Commands.selectRun(parent!!, "data modify entity @s data.${identifier} set value ${Tag.toSNBT(value)}")
            Function.addCommands(cmd)
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value ${Tag.toSNBT(value)}")
            Function.addCommand(cmd)
        }
        val re = EntityUUIDVar(this)
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
        return "[$type,value=${Tag.toSNBT(value)}]"
    }
}