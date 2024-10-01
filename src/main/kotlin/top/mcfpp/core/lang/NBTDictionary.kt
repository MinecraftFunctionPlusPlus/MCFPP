package top.mcfpp.core.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.CompoundTag
import top.mcfpp.Project
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.model.accessor.SimpleAccessor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.mni.NBTDictionaryData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.accessor.Property
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPDictType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

open class NBTDictionary : NBTBasedData {

    override var type: MCFPPType = MCFPPDictType(MCFPPBaseType.Any)

    /**
     * 创建一个dict类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr, identifier)

    /**
     * 创建一个dict值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    /**
     * 复制一个dict
     * @param b 被复制的dict值
     */
    constructor(b: NBTDictionary) : super(b)

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun doAssignedBy(b: Var<*>): NBTDictionary {
        when (b) {
            is NBTDictionary -> {
                return assignCommand(b) as NBTDictionary
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                return this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }


    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.field.getFunction(key,readOnlyParams , normalParams) to true
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        return if(index is MCString){
            PropertyVar(Property.buildSimpleProperty(super.getByStringIndex(index)), this)
        }else{
            throw IllegalArgumentException("Index must be a string")
        }
    }

    companion object{
        val data = CompoundData("dict", "mcfpp.lang")

        init {
            data.initialize()
            data.getNativeFromClass(NBTDictionaryData::class.java)
        }
    }
}

open class NBTDictionaryConcrete : NBTDictionary, MCFPPValue<CompoundTag> {

    override lateinit var value: CompoundTag

    /**
     * 创建一个固定的dict
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: CompoundTag,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr, identifier){
        this.value = value
    }

    /**
     * 创建一个固定的dict。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: CompoundTag, identifier: String = UUID.randomUUID().toString()) : super(identifier){
        this.value = value
    }


    constructor(v: NBTDictionaryConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): NBTDictionaryConcrete {
        return NBTDictionaryConcrete(this)
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
        val re = NBTDictionary(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun explicitCast(type: MCFPPType): Var<*> {
        return when(type){
            is MCFPPDictType -> {
                if(type.generic == (this.type as MCFPPDictType).generic){
                    this
                }else{
                    buildCastErrorVar(type)
                }
            }
            MCFPPNBTType.NBT -> NBTBasedDataConcrete(value)
            MCFPPBaseType.Any -> this
            else -> buildCastErrorVar(type)
        }
    }


    override fun getByIndex(index: Var<*>): PropertyVar {
        return if(index is MCString){
            if(index is MCStringConcrete){
                if(value.containsKey(index.value.valueToString())){
                    throw IndexOutOfBoundsException("Index out of bounds")
                }else{
                    PropertyVar(Property.buildSimpleProperty(NBTBasedDataConcrete(value[index.value.valueToString()])), this)
                }
            }else {
                PropertyVar(Property.buildSimpleProperty(super.getByStringIndex(index)), this)
            }
        }else{
            throw IllegalArgumentException("Index must be a string")
        }
    }


}