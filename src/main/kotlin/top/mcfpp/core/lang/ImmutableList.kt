package top.mcfpp.core.lang

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.mni.NBTListData
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.property.Property
import top.mcfpp.model.property.SimpleAccessor
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class ImmutableList : NBTList {

    /**
     * 创建一个list值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify(),
                genericType : MCFPPType
    ) : super(identifier, genericType)

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: ImmutableList) : super(b)

    override fun getByIndex(index: Var<*>): PropertyVar {
        val p = super.getByIndex(index)
        return PropertyVar(Property(p.identifier, SimpleAccessor(), null), p, this)
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

class ImmutableListConcrete: ImmutableList, MCFPPValue<ListTag>{

    override var value: ListTag

    constructor(value: ListTag, identifier: String, genericType: MCFPPType) : super(identifier, genericType){
        type = MCFPPListType(genericType)
        this.value = value
    }

    constructor(list : ImmutableList, value: ListTag):super(list){
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
        Function.addCommands(Commands.buildMacroAdjustedCommands(this, Command("data modify")
            .build(nbtPath.toCommandPart())
            .build("set value ${Tag.toSNBT(value)}")))
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
        val v = if(index is MCInt){
            if(index is MCIntConcrete){
                if(index.value >= value.size){
                    throw IndexOutOfBoundsException("Index out of bounds")
                }else{
                    NBTBasedDataConcrete(value[index.value])
                }
            }else {
                //index未知
                super.getByIntIndex(index)
            }
        }else{
            throw IllegalArgumentException("Index must be a int")
        }
        return PropertyVar(Property.buildSimpleProperty(v), v,this)
    }

    override fun toString(): String {
        return "[$type,value=${Tag.toSNBT(value)}]"
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
            val nf = (it as NativeFunction).replaceGenericParams(mapOf("E" to genericType))
            if(nf.isSelf(key, normalArgs)){
                re = nf
            }
        }
        val iterator = data.parent.iterator()
        while (re is UnknownFunction && iterator.hasNext()){
            re = iterator.next().getFunction(key, readOnlyArgs, normalArgs, isStatic)
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

        val empty = ImmutableListConcrete(ListTag(), "empty", MCFPPBaseType.Any)

    }
}
