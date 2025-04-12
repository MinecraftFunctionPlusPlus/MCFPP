package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.mni.NBTDictionaryConcreteData
import top.mcfpp.mni.NBTDictionaryData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.property.Property
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

open class NBTDictionary : NBTBasedData {

    override var type: MCFPPType = MCFPPDictType(MCFPPBaseType.Any)

    /**
     * 创建一个dict值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

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

    @InsertCommand
    override fun assignCommand(a: NBTBasedData): NBTBasedData {
        nbtType = a.nbtType
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete = {b, final ->
                if(b is NBTDictionaryConcrete){
                    if(!b.isAllConcrete()){
                        b.toDynamic(true)
                    }
                    //对类中的成员的值进行修改
                    if(final.size == 2){
                        Function.addCommand(final[0])
                    }
                    if(b.isAllConcrete()){
                        final.last().build(Commands.dataSetValue(nbtPath, b.getConcretePart()))
                    }else{
                        final.last().build(Commands.dataSetFrom(nbtPath, b.nbtPath))
                    }
                    if(final.last().isMacro){
                        Function.addCommands(final.last().buildMacroFunction())
                    }else{
                        Function.addCommand(final.last())
                    }
                    NBTDictionary(this)
                }else{
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
                    NBTDictionary(this)
                }
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
                NBTDictionary(this)
            },
            ifThisIsNormalVarAndAIsConcrete = {b, _ ->
                NBTDictionaryConcrete(this, (b as NBTDictionaryConcrete).value)
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
                NBTDictionary(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = {b, _ ->
                Function.addCommand(Commands.dataSetFrom(nbtPath, b.nbtPath))
                NBTDictionary(this)
            }
        ) as NBTDictionary
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
        var re: Function = UnknownFunction(key)
        data.field.forEachFunction {
            //TODO 我们约定it为NativeFunction，但是没有考虑拓展函数
            assert(it is NativeFunction)
            val nf = (it as NativeFunction).replaceGenericParams(mapOf("E" to (type as MCFPPDictType).generic))
            if(nf.isSelf(key, normalArgs)){
                re = nf
            }
        }
        val iterator = data.parent.iterator()
        while (re is UnknownFunction && iterator.hasNext()){
            re = iterator.next().getFunction(key, readOnlyArgs, normalArgs,isStatic)
        }
        return re to true
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        return if(index is MCString){
            PropertyVar(Property.buildSimpleProperty(super.getByStringIndex(index)), super.getByStringIndex(index), this)
        }else{
            throw IllegalArgumentException("Index must be a string")
        }
    }

    companion object{
        val data by lazy {
            CompoundData("dict", "mcfpp.lang").apply {
                initialize()
                getNativeFromClass(NBTDictionaryData::class.java)
            }
        }
    }
}

/**
 * 被编译器跟踪的字典。本质是一个HashMap。和[DataTemplateObjectConcrete]不同的是，[DataTemplateObjectConcrete]的本质就是一个NBT复合标签。被编译器跟踪的字典其实是被跟踪了它的键部分。编译器应当知道这个字典全部的键，即使不知道这些键对应什么值。
 */
class NBTDictionaryConcrete : NBTDictionary, PartialConcreteValue<CompoundTag, HashMap<String, Var<*>>> {

    override var value: HashMap<String, Var<*>>

    /**
     * 创建一个固定的dict。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: HashMap<String, Var<*>>, identifier: String = TempPool.getVarIdentify()) : super(identifier){
        this.value = value
    }

    constructor(dict: NBTDictionary, value: HashMap<String, Var<*>>): super(dict){
        this.value = HashMap(value.mapValues { it.value.clone() })
    }

    constructor(v: NBTDictionaryConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): NBTDictionaryConcrete {
        return NBTDictionaryConcrete(this)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        var re: Function = UnknownFunction(key)
        data.field.forEachFunction {
            //TODO 我们约定it为NativeFunction，但是没有考虑拓展函数
            assert(it is NativeFunction)
            val nf = (it as NativeFunction).replaceGenericParams(mapOf("E" to (type as MCFPPDictType).generic))
            if(nf.isSelf(key, normalArgs)){
                re = nf
            }
        }
        val iterator = data.parent.iterator()
        while (re is UnknownFunction && iterator.hasNext()){
            re = iterator.next().getFunction(key, readOnlyArgs, normalArgs,isStatic)
        }
        return re to true
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        if(value.isEmpty()) return NBTDictionary(this)
        Function.addCommands(Commands.method2(this, Commands.dataSetValue(nbtPath, getConcretePart())))
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
                }else {
                    buildCastErrorVar(type)
                }
            }

            is MCFPPMapType -> {
                if(type.generic == (this.type as MCFPPDictType).generic){
                    NBTMapConcrete(value, genericType = type.generic).setAs(this)
                }else{
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

            MCFPPBaseType.Any -> (MCAnyConcrete(value).setAs(this) as MCAnyConcrete).apply { inferredType = this@NBTDictionaryConcrete.type }
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
                if(!value.containsKey(index.value.value)){
                    val re = (type as MCFPPDictType).generic.build(index.value.value)
                    re.parent = this
                    re.nbtPath = nbtPath.memberIndex(index.value.value)
                    PropertyVar(Property.buildSimpleSetter(index.value.value), re, this)
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

    override fun isAllConcrete(): Boolean {
        return value.values.all { it is MCFPPValue<*> }
    }

    override fun getNotConcretePart(): HashMap<String, Var<*>> {
        return HashMap(value.filter { it.value !is MCFPPValue<*> || it.value is NBTListConcrete && !(it.value as NBTListConcrete).isAllConcrete() })
    }

    override fun getConcretePart(): CompoundTag {
        val compound = CompoundTag()
        for (v in value){
            if(v.value is MCFPPValue<*>){
                compound.put(v.key, NBTUtil.valueToNBT((v.value as MCFPPValue<*>).value))
            }
        }
        return compound
    }

    companion object {
        val data by lazy {
            CompoundData("dict", "mcfpp.lang").apply {
                initialize()
                getNativeFromClass(NBTDictionaryConcreteData::class.java)
            }
        }
    }

}