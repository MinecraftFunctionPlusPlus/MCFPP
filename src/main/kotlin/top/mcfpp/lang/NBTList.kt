package top.mcfpp.lang

import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.Tag
import top.mcfpp.Project
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member
import java.util.*
import kotlin.Number

class NBTList<T : Tag<*>?> : NBTBasedData, Indexable<NBT> {

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
        value: ListTag<T>,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr, value, identifier)

    /**
     * 创建一个固定的list。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: ListTag<T>, identifier: String = UUID.randomUUID().toString()) : super(value, identifier)

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: ListTag<T>) : super(b)

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun assign(b: Var?) {
        hasAssigned = true
        when (b) {
            is NBTList<*> -> {
                assignCommand(b as NBTList<T>)
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
        if(isConcrete){
            return when(type){
                "list" -> this
                "nbt" -> NBT(value!!)
                else -> throw VariableConverseException()
            }
        }else{
            return when(type){
                "list" -> this
                "nbt" -> {
                    val re = NBT(identifier)
                    re.nbtType = NBT.Companion.NBTType.LIST
                    re.parent = parent
                    re
                }
                else -> throw VariableConverseException()
            }
        }
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): Var {
        if (isTemp) return this
        if (isConcrete) {
            return NBTList(value as ListTag<T>)
        }
        val re = NBTList<T>()
        re.isTemp = true
        re.assign(this)
        return re
    }


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
    ): Pair<Function?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getByIndex(index: Var): NBT {
        return if(index is MCInt){
            if(index.isConcrete && isConcrete){
                if(index.value!! >= (value as ListTag<T>).size()){
                    throw IndexOutOfBoundsException("Index out of bounds")
                }else{
                    NBT((value as ListTag<T>)[index.value!!]!!)
                }
            }else {
                (cast("nbt") as NBT).getByIntIndex(index)
            }
        }else{
            throw IllegalArgumentException("Index must be a int")
        }
    }
}