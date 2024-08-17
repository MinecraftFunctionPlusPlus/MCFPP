package top.mcfpp.lang

import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.*
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.mni.NBTListData
import top.mcfpp.mni.NBTMapData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

open class NBTMap : NBTDictionary{

    var keyList : NBTList
    var valueList : NBTList
    var keyValueSet: NBTDictionary

    val genericType: MCFPPType

    override var type: MCFPPType = MCFPPNBTType.Map

    /**
     * 创建一个map类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString(),
        genericType : MCFPPType
    ) : super(curr, identifier){
        this.genericType = genericType
        keyList = NBTList(curr, identifier + "_key", MCFPPBaseType.String)
        valueList = NBTList(curr, identifier + "_value", genericType)
        keyValueSet = NBTDictionary(curr, identifier + "_keyValueSet")
        keyList.nbtPath = nbtPath.memberIndex("key")
        valueList.nbtPath = nbtPath.memberIndex("value")
        keyValueSet.nbtPath = nbtPath.memberIndex("keyValueSet")
    }

    /**
     * 创建一个map值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString(), genericType : MCFPPType) : super(identifier){
        this.genericType = genericType
        keyList = NBTList(identifier + "_key", MCFPPBaseType.String)
        valueList = NBTList(identifier + "_value", genericType)
        keyValueSet = NBTDictionary(identifier + "_keyValueSet")
        keyList.nbtPath = nbtPath.memberIndex("key")
        valueList.nbtPath = nbtPath.memberIndex("value")
        keyValueSet.nbtPath = nbtPath.memberIndex("keyValueSet")
    }

    constructor(b: NBTMap): super(b){
        this.keyList = b.keyList.clone() as NBTList
        this.valueList = b.valueList.clone() as NBTList
        this.keyValueSet = b.keyValueSet.clone() as NBTDictionary
        this.genericType = b.genericType
    }

    override fun doAssign(b: Var<*>): NBTMap {
        return super.assign(b) as NBTMap
    }

    companion object{
        val data = CompoundData("map","mcfpp.lang")

        init {
            data.initialize()
            data.extends(MCAny.data)
            data.getNativeFunctionFromClass(NBTMapData::class.java)
        }
    }
}

class NBTMapConcrete : NBTMap, MCFPPValue<CompoundTag>{

    override lateinit var value: CompoundTag

    override var type: MCFPPType = MCFPPNBTType.Map


    //TODO 构造函数未检查value的类型是否符合泛型要求
    /**
     * 创建一个固定的map
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: CompoundTag,
        identifier: String = UUID.randomUUID().toString(),
        genericType: MCFPPType
    ) : super(curr, identifier, genericType){
        this.value = value
        val (k, v) = parseValue(value)
        keyList = NBTListConcrete<Any>(curr, k, identifier + "_key", MCFPPBaseType.String)
        valueList = NBTListConcrete<Any>(curr, v, identifier + "_value", genericType)
        keyValueSet = NBTDictionaryConcrete(curr, value, identifier + "_keyValueSet")
    }

    /**
     * 创建一个固定的map。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: CompoundTag, identifier: String = UUID.randomUUID().toString(), genericType: MCFPPType) : super(identifier, genericType){
        this.value = value
        val (k, v) = parseValue(value)
        keyList = NBTListConcrete<Any>(k, identifier + "_key", MCFPPBaseType.String)
        valueList = NBTListConcrete<Any>(v, identifier + "_value", genericType)
        keyValueSet = NBTDictionaryConcrete(value, identifier + "_keyValueSet")
    }

    /**
     * 复制一个map
     * @param b 被复制的map值
     */
    constructor(b: NBTMap, value: CompoundTag) : super(b){
        this.value = value
        val (k, v) = parseValue(value)
        keyList = NBTListConcrete<StringTag>(k, identifier + "_key", MCFPPBaseType.String)
        valueList = NBTListConcrete<Any>(v, identifier + "_value", genericType)
        keyValueSet = NBTDictionaryConcrete(value, identifier + "_keyValueSet")
    }

    constructor(v: NBTMapConcrete) : super(v){
        this.value = v.value
        keyList = v.keyList.clone() as NBTListConcrete<*>
        valueList = v.valueList.clone() as NBTListConcrete<*>
        keyValueSet = v.keyValueSet.clone() as NBTDictionaryConcrete
    }

    private fun parseValue(tag: CompoundTag): Pair<ListTag<StringTag>, ListTag<*>>{
        val keys = tag.keySet()
        val values = tag.values()
        //检查列表中的元素类型是否一致
        val type = values.first().javaClass
        if(values.any { it.javaClass != type }) throw VariableConverseException()
        val keyTags = ListTag(StringTag::class.java)
        val valueTags = ListTag(type)
        keyTags.addAll(keys.map { StringTag(it) })
        valueTags.addAll(values)
        value = CompoundTag().apply {
            put("key", keyTags)
            put("value", valueTags)
            put("keyValueSet", tag)
        }
        return keyTags to valueTags
    }

    override fun clone(): NBTMapConcrete {
        return NBTMapConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        TODO("Not yet implemented")
    }
}