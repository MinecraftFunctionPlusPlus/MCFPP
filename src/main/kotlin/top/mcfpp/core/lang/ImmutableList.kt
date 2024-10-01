package top.mcfpp.core.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.IntTag
import net.querz.nbt.tag.ListTag
import top.mcfpp.Project
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.model.accessor.SimpleAccessor
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.mni.NBTListData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.accessor.Property
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import java.util.*

open class ImmutableList : NBTList{

    /**
     * 创建一个list类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString(),
        genericType : MCFPPType
    ) : super(curr, identifier, genericType)

    /**
     * 创建一个list值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString(),
                genericType : MCFPPType
    ) : super(identifier, genericType)

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: ImmutableList) : super(b)

    override fun getByIndex(index: Var<*>): PropertyVar {
        val p = super.getByIndex(index)
        return PropertyVar(Property(p, SimpleAccessor(p), null), this)
    }

    companion object {
        val data by lazy {
            CompoundData("ImmutableList", "mcfpp.lang").apply {
                extends(NBTBasedData.data)
                getNativeFromClass(NBTListData::class.java)
            }
        }

    }
}

class ImmutableListConcrete: ImmutableList, MCFPPValue<ListTag<*>>{

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

    constructor(list : ImmutableList, value: ListTag<*>):super(list){
        this.value = value
    }


    constructor(v: ImmutableListConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): ImmutableListConcrete {
        return ImmutableListConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        if (parent != null) {
            val cmd = Commands.selectRun(parent)
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
        val re = NBTList(this)
        if(replace){
            if(parentTemplate() != null) {
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else{
                Function.currFunction.field.putVar(identifier, re, true)
            }
        }
        return re
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun explicitCast(type: MCFPPType): Var<*> {
        return when(type){
            this.type -> this
            MCFPPNBTType.NBT -> this
            MCFPPBaseType.Any -> this
            else -> throw VariableConverseException()
        }
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        return PropertyVar(
            Property.buildSimpleProperty(
                if(index is MCInt){
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
            ),
            this
        )
    }

    override fun toString(): String {
        return "[$type,value=${SNBTUtil.toSNBT(value)}]"
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        var re: Function = UnknownFunction(key)
        data.field.forEachFunction {
            //TODO 我们约定it为NativeFunction，但是没有考虑拓展函数
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

    companion object {
        val data = CompoundData("ImmutableList", "mcfpp.lang")
        //注册函数

        init {
            data.extends(MCAny.data)
            //data.getNativeFunctionFromClass(NBTListConcreteData::class.java)
        }

        val empty = ImmutableListConcrete(ListTag.createUnchecked(IntTag::class.java), "empty", MCFPPBaseType.Any)

    }
}
