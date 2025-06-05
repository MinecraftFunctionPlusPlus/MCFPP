@file:Suppress("LeakingThis")

package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.mni.NBTMapConcreteData
import top.mcfpp.mni.NBTMapData
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.property.AnonymousNativeMutator
import top.mcfpp.model.property.Property
import top.mcfpp.model.property.SimpleAccessor
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPMapType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

open class NBTMap : NBTBasedData {

    var keyList: NBTList
    var keyValueSet: NBTDictionary

    val genericType: MCFPPType

    override var parent: CanSelectMember? = null
        get() = super.parent
        set(value) {
            field = value
            keyValueSet.parent = value
            keyValueSet.parent = value
        }

    /**
     * 创建一个map值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify(), genericType : MCFPPType) : super(identifier){
        this.genericType = genericType
        keyList = NBTList("keys", MCFPPBaseType.String)
        keyList.parent = parent
        keyList.nbtPath = nbtPath.memberIndex("keys")
        keyValueSet = NBTDictionary("keyValueSet")
        keyValueSet.parent = parent
        keyValueSet.nbtPath = nbtPath.memberIndex("keyValueSet")
        type = MCFPPMapType(genericType)
    }

    constructor(b: NBTMap): super(b){
        this.genericType = b.genericType
        this.keyList = b.keyList.clone()
        this.keyList.parent = parent
        this.keyList.nbtPath = nbtPath.memberIndex("keys")
        this.keyValueSet = b.keyValueSet.clone() as NBTDictionary
        this.keyValueSet.parent = parent
        this.keyValueSet.nbtPath = nbtPath.memberIndex("keyValueSet")
        type = MCFPPMapType(genericType)
    }

    override fun doAssignedBy(b: Var<*>): NBTMap {
        when (b) {
            is NBTMap -> {
                return assignCommand(b) as NBTMap
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                return this
            }
        }
    }

    private fun convertDictValueToMap(tag: CompoundTag): CompoundTag{
        val mapTag = CompoundTag()
        mapTag.put("keyValueSet", tag)
        val list = ListTag()
        for ((key, value) in tag){
            val kv = CompoundTag()
            kv.put("key", StringTag(key))
            kv.put("value", value)
            list.add(kv)
        }
        mapTag.put("keyValueList", list)
        return mapTag
    }

    @InsertCommand
    override fun assignCommand(a: NBTBasedData): NBTBasedData {
        nbtType = a.nbtType
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete = {b, final ->
                b as NBTMapConcrete
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                val qwq = Commands.tempFunction(Function.currFunction){
                    Function.addCommand(Commands.dataSetValue(keyList.nbtPath, NBTUtil.valueToNBT(b.value.keys)))
                    if(b.isAllConcrete()){
                        Function.addCommand(Commands.dataSetValue(keyValueSet.nbtPath, b.getConcretePart()))
                    }else{
                        Function.addCommand(Commands.dataSetFrom(keyValueSet.nbtPath, b.keyValueSet.nbtPath))
                        Function.addCommand(Commands.dataMergeValue(keyValueSet.nbtPath, b.getConcretePart()))
                    }
                }
                final.last().build(qwq.first)
                if(final.last().isMacro){
                    Function.addCommands(final.last().buildMacroFunction())
                }else{
                    Function.addCommand(final.last())
                }
                NBTMap(this)
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
                NBTMap(this)
            },
            ifThisIsNormalVarAndAIsConcrete = {b ->
                b as NBTMapConcrete
                if(!b.isAllConcrete()){
                    Function.addCommand(Commands.dataSetFrom(keyValueSet.nbtPath, b.keyValueSet.nbtPath))
                }
                NBTMapConcrete(this, b.value)
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
                NBTMap(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = {b ->
                Function.addCommand(Commands.dataSetFrom(nbtPath, b.nbtPath))
                NBTMap(this)
            }
        ) as NBTMap
    }


    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return when(key){
            "keys" -> {
                return PropertyVar(Property.buildSimpleGetter("keys"), keyList, this) to true
            }
            "keyValueSet" -> {
                return PropertyVar(Property.buildSimpleGetter("keyValueSet"), keyValueSet, this) to true
            }
            else -> null to true
        }
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
            val nf = (it as NativeFunction).replaceGenericParams(mapOf("E" to (type as MCFPPMapType).generic))
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
            val re = genericType.buildUnConcrete("").setAs(this)
            re.parent = this
            re.nbtPath = keyValueSet.nbtPath.memberIndex(index)
            re.isDynamic = true
            val property = Property("", SimpleAccessor(), AnonymousNativeMutator { _, v ->
                re.assignedBy(v)
                if(index is MCStringConcrete){
                    Function.addCommands(Commands.buildMacroAdjustedCommands(this, Commands.dataAppendValue(keyList.nbtPath, index.value)))
                }else {
                    if(index.parentClass() != null){
                        Function.addCommands(Commands.buildMacroAdjustedCommands(this, Commands.dataAppendFrom(keyList.nbtPath, index.getTempVar().nbtPath)))
                    }else{
                        Function.addCommands(Commands.buildMacroAdjustedCommands(this, Commands.dataAppendFrom(keyList.nbtPath, index.nbtPath)))
                    }
                }
                return@AnonymousNativeMutator re
            })
            PropertyVar(property, re, this)
        }else {
            LogProcessor.error("Index must be a string")
            val re = UnknownVar("error_${identifier}_index_${index.identifier}")
            return PropertyVar(Property.buildSimpleProperty(re), re, this)
        }
    }

    companion object {
        val data by lazy {
            CompoundData("map","mcfpp.lang").apply {
                initialize()
                extends(MCFPPBaseType.Any.instanceData)
                injectedBy(NBTMapData::class.java)
            }
        }
    }
}

class NBTMapConcrete : NBTMap, MCFPPValue<HashMap<String, Var<*>>> {

    override var value: HashMap<String, Var<*>>

    /**
     * 创建一个固定的map。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: HashMap<String, Var<*>>, identifier: String = TempPool.getVarIdentify(), genericType: MCFPPType) : super(identifier, genericType){
        this.value = value
        keyList = NBTListConcrete(ArrayList(value.keys.map { MCStringConcrete(StringTag(it)) }), "keys", MCFPPBaseType.String).setAs(keyValueSet) as NBTListConcrete
        keyValueSet = NBTDictionaryConcrete(value).setAs(keyValueSet) as NBTDictionaryConcrete
    }

    /**
     * 复制一个map
     * @param b 被复制的map值
     */
    constructor(b: NBTMap, value: HashMap<String, Var<*>>) : super(b){
        this.value = value
        keyList = NBTListConcrete(ArrayList(value.keys.map { MCStringConcrete(StringTag(it)) }), "keys", MCFPPBaseType.String).setAs(keyList) as NBTListConcrete
        keyValueSet = NBTDictionaryConcrete(value).setAs(keyValueSet) as NBTDictionaryConcrete
    }

    constructor(v: NBTMapConcrete) : super(v){
        this.value = v.value
        keyList = NBTListConcrete(ArrayList(v.value.keys.map { MCStringConcrete(StringTag(it)) }), "keys", MCFPPBaseType.String).setAs(keyList) as NBTListConcrete
        keyValueSet = v.keyValueSet.clone().setAs(keyValueSet) as NBTDictionaryConcrete
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
            val nf = (it as NativeFunction).replaceGenericParams(mapOf("E" to (type as MCFPPMapType).generic))
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

    override fun replaceMemberVar(v: Var<*>) {
        value[v.identifier] = v
    }

    fun isAllConcrete(): Boolean {
        return value.values.all { it is MCFPPValue<*> }
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

    override fun clone(): NBTMapConcrete {
        return NBTMapConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        (keyList as NBTListConcrete).synchronous()
        (keyValueSet as NBTDictionaryConcrete).toDynamic(false)
        val re = NBTMap(this)
        if(replace){
            if(parentTemplate() != null) {
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else{
                Function.currFunction.field.putVar(identifier, re, true)
            }
        }
        return re
    }

    fun indexOf(key: String): Int{
        return (keyValueSet as NBTDictionaryConcrete).value.keys.indexOfFirst { it == key }
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        return if(index is MCString){
            if(index is MCStringConcrete){
                if(!value.containsKey(index.value.value)){
                    val re = (type as MCFPPMapType).generic.build(index.value.value)
                    re.parent = this
                    re.nbtPath = keyValueSet.nbtPath.memberIndex(index.value.value)
                    PropertyVar(Property(re.identifier, null, AnonymousNativeMutator{_, v ->
                        if(v !is MCFPPValue<*>){
                            re.assignedBy(v)
                        }
                        //如果没有这个键，就添加。重复判断避免重复赋值的时候重复添加
                        if(!value.containsKey(index.value.value)){
                            (keyList as NBTListConcrete).value.add(MCStringConcrete(index))
                        }
                        return@AnonymousNativeMutator re
                    }), re, this)
                }else {
                    val re = value[index.value.value]!!
                    re.identifier = index.value.value
                    re.parent = this
                    re.nbtPath = keyValueSet.nbtPath.memberIndex(index.value.value)
                    if(re !is MCFPPValue<*>){
                        re.isDynamic = true
                    }
                    PropertyVar(Property.buildSimpleProperty(re), re, this)
                }
            }else {
                toDynamic(true)
                PropertyVar(Property.buildSimpleProperty(super.getByIndex(index)), super.getByIndex(index),this)
            }
        }else{
            LogProcessor.error("Index must be a string")
            val re = UnknownVar("error_index_${index.identifier}")
            PropertyVar(Property.buildSimpleProperty(re), re, this)
        }
    }

    companion object{
        val data by lazy {
            CompoundData("map","mcfpp.lang").apply {
                initialize()
                extends(MCFPPBaseType.Any.instanceData)
                injectedBy(NBTMapConcreteData::class.java)
            }
        }
    }
}