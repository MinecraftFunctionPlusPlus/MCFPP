package top.mcfpp.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.*
import top.mcfpp.Project
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
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
open class NBTList<E : Var<*>> : NBTBasedData<ListTag<*>> {

    final override var type: MCFPPType

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
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: NBTList<E>) : super(b){
        type = b.type
        this.genericType = (type as MCFPPListType).generic
    }


    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            this.type -> this
            MCFPPNBTType.NBT -> this
            MCFPPBaseType.Any -> MCAnyConcrete(this)
            else -> throw VariableConverseException()
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
            super.getByIntIndex(index)
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

class NBTListConcrete<E : Var<*>>: NBTList<E>, INBTBasedDataConcrete<ListTag<*>> {

    override var value: ListTag<*>

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
    ) : this(value, curr.prefix + identifier, genericType)

    constructor(value: ListTag<*>, identifier: String, genericType: MCFPPType) : super(identifier, genericType){
        type = MCFPPListType(genericType)
        this.value = value
    }

    constructor(list : NBTList<E>, value: ListTag<*>):super(list){
        this.value = value
    }

    override fun toDynamic(): NBTBasedData<ListTag<*>>{
        val parent = parent
        if (parent != null) {
            val cmd = when(parent){
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                is MCFPPClassType -> {
                    arrayOf(Command.build("execute as ${parent.cls.uuid} run "))
                }
                else -> TODO()
            }
            if(cmd.size == 2){
                Function.addCommand(cmd[0])
            }
            Function.addCommand(cmd.last().build(
                "data modify entity @s data.${identifier} set value ${SNBTUtil.toSNBT(value)}")
            )
        } else {
            val cmd = Command.build(
                "data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[$stackIndex].$identifier set value ${SNBTUtil.toSNBT(value)}"
            )
            Function.addCommand(cmd)
        }
        return NBTList(this)
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            this.type -> this
            MCFPPNBTType.NBT -> this
            MCFPPBaseType.Any -> this
            else -> throw VariableConverseException()
        }
    }

    override fun getByIndex(index: Var<*>): NBTBasedData<*> {
        return if(index is MCInt){
            if(index is MCIntConcrete){
                if(index.value >= value.size()){
                    throw IndexOutOfBoundsException("Index out of bounds")
                }else{
                    NBTBasedDataConcrete(value[index.value]!!)
                }
            }else {
                //index未知
                super.getByIntIndex(index)
            }
        }else{
            throw IllegalArgumentException("Index must be a int")
        }
    }
}
