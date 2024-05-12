package top.mcfpp.lang

import net.querz.nbt.tag.*
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.*
import top.mcfpp.model.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import java.util.*

/**
 * 表示一个列表类型。基于NBTBasedData实现。
 */
class NBTList<E : Var<*>> : NBTBasedData<ListTag<*>> {

    override var javaValue: ListTag<*>? = null

    override var type: MCFPPType

    val genericType: MCFPPType

    override var nbtType = NBTBasedData.Companion.NBTTypeWithTag.LIST

    /**
     * 创建一个list类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString(),
        genericType : MCFPPType
    ) : super(curr, identifier){
        type = MCFPPListType(genericType)
        this.genericType = genericType
    }

    /**
     * 创建一个list值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString(),
                genericType : MCFPPType) : super(identifier){
        type = MCFPPListType(genericType)
        this.genericType = genericType
    }

    /**
     * 创建一个固定的list
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: ListTag<*>,
        identifier: String = UUID.randomUUID().toString(),
        genericType : MCFPPType
    ) : super(curr, value, identifier){
        type = MCFPPListType(genericType)
        this.genericType = genericType
    }

    /**
     * 创建一个固定的list。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: ListTag<*>, identifier: String = UUID.randomUUID().toString(),
                genericType : MCFPPType) : super(value, identifier){
        type = MCFPPListType(genericType)
        this.genericType = genericType
    }

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: ListTag<*>) : super(b){
        type = b.getListType()
        this.genericType = (type as MCFPPListType).generic
    }


    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: MCFPPType): Var<*> {
        if(isConcrete){
            return when(type){
                this.type -> this
                MCFPPNBTType.NBT -> this
                MCFPPBaseType.Any -> this
                else -> throw VariableConverseException()
            }
        }else{
            return when(type){
                this.type -> this
                MCFPPNBTType.NBT -> this
                MCFPPBaseType.Any -> MCAny(this)
                else -> throw VariableConverseException()
            }
        }
    }

    /*
    override fun createTempVar(): Var<*> = TODO()
    override fun createTempVar(value: Tag<*>): Var<*> = NBTList<E>(value as ListTag<*>)
    */

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param readOnlyParams 只读参数
     * @param normalParams 普通参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        var re: Function = UnknownFunction(key)
        data.field.forEachFunction {
            //TODO 我们约定it为NativeFunction
            assert(it is NativeFunction)
            val nf = (it as NativeFunction).replaceGenericParams(mapOf("E" to genericType))
            if(nf.isSelf(key, normalParams)){
                re = nf
            }
        }
        val iterator = data.parent.iterator()
        while (re is UnknownFunction && iterator.hasNext()){
            re = iterator.next().getFunction(key,readOnlyParams , normalParams ,isStatic)
        }
        return re to true
    }

    override fun getByIndex(index: Var<*>): NBTBasedData<*> {
        return if(index is MCInt){
            if(index.isConcrete && isConcrete){
                if(index.javaValue!! >= (javaValue as ListTag<*>).size()){
                    throw IndexOutOfBoundsException("Index out of bounds")
                }else{
                    NBTBasedData((javaValue as ListTag<*>)[index.javaValue!!]!!)
                }
            }else {
                (cast(MCFPPNBTType.NBT) as NBTBasedData).getByIntIndex(index)
            }
        }else{
            throw IllegalArgumentException("Index must be a int")
        }
    }

    companion object {
        val data = CompoundData("list", "mcfpp")
        //注册函数

        init {
            data.parent.add(MCAny.data)
            data.field.addFunction(NativeFunction("add", NBTListData(),MCFPPBaseType.Void,"mcfpp")
                .appendNormalParam(MCFPPGenericType("E", emptyList()), "e"),false)
        }

    }
}
