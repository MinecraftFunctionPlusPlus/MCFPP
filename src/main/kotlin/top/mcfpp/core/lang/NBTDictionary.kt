package top.mcfpp.core.lang

import net.querz.nbt.tag.CompoundTag
import top.mcfpp.command.Commands
import top.mcfpp.mni.NBTDictionaryData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.accessor.Property
import top.mcfpp.model.function.Function
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*
import kotlin.collections.HashMap

open class NBTDictionary : NBTBasedData {

    override var type: MCFPPType = MCFPPDictType(MCFPPBaseType.Any)

    /**
     * 创建一个dict类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为随机UUID
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
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.field.getFunction(key, readOnlyArgs, normalArgs) to true
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        return if(index is MCString){
            PropertyVar(Property.buildSimpleProperty(super.getByStringIndex(index)), super.getByStringIndex(index), this)
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

/**
 * 被编译器跟踪的字典。本质是一个HashMap。和[DataTemplateObjectConcrete]不同的是，[DataTemplateObjectConcrete]的本质就是一个NBT复合标签。被编译器跟踪的字典其实是被跟踪了它的键部分。编译器应当知道这个字典全部的键，即使不知道这些键对应什么值。
 */
class NBTDictionaryConcrete : NBTDictionary, MCFPPValue<HashMap<String, Var<*>>> {

    override lateinit var value: HashMap<String, Var<*>>

    /**
     * 创建一个固定的dict
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: HashMap<String, Var<*>>,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr, identifier){
        this.value = value
    }

    /**
     * 创建一个固定的dict。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: HashMap<String, Var<*>>, identifier: String = UUID.randomUUID().toString()) : super(identifier){
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
        if(value.isEmpty()) return NBTDictionary(this)
        if (parent != null) {
            val cmd = Commands.selectRun(parent)
            if(cmd.size == 2){
                Function.addCommand(cmd[0])
            }
            Function.addCommand(cmd.last().build(Commands.dataMergeValue(nbtPath, getConcretePart())))
        } else {
            Function.addCommand(Commands.dataMergeValue(nbtPath, getConcretePart()))
        }
        val re = NBTDictionary(this)
        if(replace){
            if(parentTemplate() != null) {
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else{
                Function.currFunction.field.putVar(identifier, re, true)
            }
        }
        return re
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        return when(type) {
            is MCFPPDictType -> {
                if (type.generic == (this.type as MCFPPDictType).generic) {
                    this
                } else {
                    buildCastErrorVar(type)
                }
            }

            MCFPPNBTType.NBT -> {
                if (isAllConcrete()) {
                    NBTBasedDataConcrete(this, NBTUtil.valueToNBT(value))
                } else {
                    NBTBasedData(this)
                }
            }

            MCFPPBaseType.Any -> this
            else -> buildCastErrorVar(type)
        }
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
            is MCFPPDataTemplateType -> {
                return if(type.template.checkDictionaryStruct(value)){
                    if(isAllConcrete()){
                        DataTemplateObjectConcrete(type.template, NBTUtil.valueToNBT(value) as CompoundTag, identifier)
                    }else {
                        DataTemplateObject(type.template, identifier)
                    }
                }else{
                    buildCastErrorVar(type)
                }
            }
            MCFPPNBTType.NBT -> {
                if(isAllConcrete()){
                    NBTBasedDataConcrete(this, NBTUtil.valueToNBT(value))
                }else{
                    NBTBasedData(this)
                }
            }
            MCFPPBaseType.Any -> this
            else -> {
                LogProcessor.error(TextTranslator.CAST_ERROR.translate(this.type.typeName, type.typeName))
                buildCastErrorVar(type)
            }
        }
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        return if(index is MCString){
            if(index is MCStringConcrete){
                if(value.containsKey(index.value.value)){
                    LogProcessor.error("No such key: ${index.value.value} in dict: $identifier")
                    val re = UnknownVar("error_key_${index.value.value}")
                    PropertyVar(Property.buildSimpleProperty(re), re, this)
                }else{
                    val re = value[index.value.value]!!
                    re.identifier = index.value.value
                    re.parent = this
                    re.nbtPath = nbtPath.memberIndex(index.value.value)
                    PropertyVar(Property.buildSimpleProperty(re), re, this)
                }
            }else {
                toDynamic(true)
                PropertyVar(Property.buildSimpleProperty(super.getByStringIndex(index)), super.getByStringIndex(index),this)
            }
        }else{
            LogProcessor.error("Index must be a string")
            val re = UnknownVar("error_index_${index.identifier}")
            PropertyVar(Property.buildSimpleProperty(re), re, this)
        }
    }

    override fun replaceMemberVar(v: Var<*>) {
        value[v.identifier] = v
    }

    fun isAllConcrete(): Boolean {
        return value.all { it is MCFPPValue<*> }
    }

    fun getConcretePart(): CompoundTag {
        val compound = CompoundTag()
        for (v in value){
            if(v.value is MCFPPValue<*>){
                compound.put(v.key, NBTUtil.valueToNBT((v.value as MCFPPValue<*>).value))
            }
        }
        return compound
    }

}