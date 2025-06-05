package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.nbt.tags.collection.LongArrayTag
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

open class NBTLongArray: NBTArray {

    override var type: MCFPPType = MCFPPNBTType.LongArray

    override val arrayType: MCFPPType = MCFPPNBTType.Long

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    constructor(b: NBTArray) : super(b)

    override fun clone(): NBTLongArray {
        return NBTLongArray(this)
    }

    override fun doAssignedBy(b: Var<*>) : NBTLongArray {
        return when (b) {
            is NBTLongArray -> assignCommand(b)
            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    @InsertCommand
    protected open fun assignCommand(a: NBTLongArray) : NBTLongArray {
        nbtType = a.nbtType
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete = {b, final ->
                b as NBTLongArrayConcrete
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
                NBTLongArray(this)
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
                NBTLongArray(this)
            },
            ifThisIsNormalVarAndAIsConcrete = {b ->
                NBTLongArrayConcrete(this, (b as NBTLongArrayConcrete).value)
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
                NBTLongArray(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = {b ->
                Function.addCommand(Commands.dataSetFrom(nbtPath, b.nbtPath))
                NBTLongArray(this)
            }
        ) as NBTLongArray
    }

}

class NBTLongArrayConcrete: NBTLongArray, MCFPPValue<LongArrayTag>{

    override var value: LongArrayTag

    constructor(value: LongArrayTag, identifier: String = TempPool.getVarIdentify()) : super(identifier){
        this.value = value
    }

    constructor(b: NBTArray, value: LongArrayTag) : super(b){
        this.value = value
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, value).toDynamic(replace)
        return NBTLongArray(this)
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        if(index is MCInt){
            val v = arrayType.build(TempPool.getVarIdentify())
            v.nbtPath = nbtPath.intIndex(index)
            v.parent = this
            return PropertyVar(Property.buildSimpleProperty(v), v,this)
        }else{
            LogProcessor.error("Index must be a int")
            val re = UnknownVar("error_${identifier}_index_${index.identifier}")
            return PropertyVar(Property.buildSimpleProperty(re), re, this)
        }
    }

    override fun clone(): NBTLongArrayConcrete {
        return NBTLongArrayConcrete(this, value)
    }

}