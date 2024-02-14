package top.mcfpp.lang

import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.StringTag
import net.querz.nbt.tag.Tag
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.*
import top.mcfpp.lib.Function
import java.util.*
import kotlin.reflect.jvm.javaMethod

open class NBTDictionary : NBTBasedData, Indexable<NBT> {

    /**
     * 创建一个list类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr, identifier)

    /**
     * 创建一个list值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    /**
     * 创建一个固定的list
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: CompoundTag,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr, value, identifier)

    /**
     * 创建一个固定的list。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: CompoundTag, identifier: String = UUID.randomUUID().toString()) : super(value, identifier)

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: CompoundTag) : super(b)

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun assign(b: Var?) {
        hasAssigned = true
        when (b) {
            is NBTDictionary -> {
                assignCommand(b)
            }
            else -> {
                throw VariableConverseException()
            }
        }
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: String): Var {
        return if(isConcrete){
            when(type){
                "dict" -> this
                "nbt" -> NBT(value!!)
                "any" -> this
                else -> throw VariableConverseException()
            }
        }else{
            when(type){
                "dict" -> this
                "nbt" -> {
                    val re = NBT(identifier)
                    re.nbtType = NBT.Companion.NBTType.COMPOUND
                    re.parent = parent
                    re
                }
                "any" -> MCAny(this)
                else -> throw VariableConverseException()
            }
        }
    }

    override fun createTempVar(): Var = NBTMap()
    override fun createTempVar(value: Tag<*>): Var = NBTMap(value as CompoundTag)

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var?, Boolean> {
        TODO("Not yet implemented")
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param params 成员方法的参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        params: List<String>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.field.getFunction(key, params) to true
    }

    override fun getByIndex(index: Var): NBT {
        return if(index is MCString){
            if(index.isConcrete && isConcrete){
                if((value as CompoundTag).containsKey((index.value as StringTag).valueToString())){
                    throw IndexOutOfBoundsException("Index out of bounds")
                }else{
                    NBT((value as CompoundTag)[(index.value as StringTag).valueToString()])
                }
            }else {
                (cast("nbt") as NBT).getByStringIndex(index)
            }
        }else{
            throw IllegalArgumentException("Index must be a string")
        }
    }

    companion object{
        val data = CompoundData("dict", "mcfpp")

        init {
            data.initialize()
            data.field.addFunction(NativeFunction(NBTDictionaryData::remove.javaMethod!!,"void","mcfpp").appendParam("string","e"),false)
            data.field.addFunction(NativeFunction(NBTDictionaryData::merge.javaMethod!!,"void","mcfpp").appendParam("dict","d"),false)
            data.field.addFunction(NativeFunction(NBTDictionaryData::containsKey.javaMethod!!,"bool","mcfpp").appendParam("string","key"),false)
        }
    }
}