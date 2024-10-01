package top.mcfpp.core.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.*
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.mni.NBTListConcreteData
import top.mcfpp.mni.NBTListData
import top.mcfpp.model.*
import top.mcfpp.model.accessor.Property
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

/**
 * 表示一个列表类型。基于NBTBasedData实现。
 */
open class NBTList : NBTBasedData {

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
                genericType : MCFPPType
    ) : super(identifier){
        type = MCFPPListType(genericType)
        this.genericType = genericType
    }

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: NBTList) : super(b){
        type = b.type
        this.genericType = (type as MCFPPListType).generic
    }

    override fun doAssignedBy(b: Var<*>): NBTList {
        return when (b) {
            is NBTList -> assignCommand(b)
            is NBTBasedDataConcrete -> {
                if (b.nbtType == this.nbtType) {
                    assignCommand(b)
                } else {
                    LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                    return this
                }
            }

            is NBTBasedData -> {
                if (b.nbtType == this.nbtType) {
                    assignCommand(b)
                } else {
                    LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                    return this
                }
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        if(!b.implicitCast(type).isError) return true
        if(b is NBTBasedData){
            return b.nbtType == this.nbtType
        }
        return false
    }


    @InsertCommand
    override fun assignCommand(a: NBTBasedData) : NBTList{
        nbtType = a.nbtType
        if (parentClass() != null){
            val b = if(a.parentClass() != null){
                a.getTempVar()
            }else a
            val final = Commands.selectRun(parent!!)
            if (b is NBTBasedDataConcrete) {
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
            } else {
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
            }
        }else{
            if(a.parentClass() != null){
                val final = Commands.selectRun(parent!!)
                //对类中的成员的值进行修改
                //a必然是不确定的
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetFrom(nbtPath, a.nbtPath))
                if(final.last().isMacro){
                    Function.addCommands(final.last().buildMacroFunction())
                }else{
                    Function.addCommand(final.last())
                }
            }
        }
        //返回值
        return NBTList(this)
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

    override fun getByIndex(index: Var<*>): PropertyVar {
        return PropertyVar(
            Property.buildSimpleProperty(if(index is MCInt){
            super.getByIntIndex(index)
        }else{
            throw IllegalArgumentException("Index must be a int")
        }), this)
    }

    companion object {
        val data = CompoundData("list", "mcfpp.lang")
        //注册函数

        init {
            data.extends(NBTBasedData.data)
            data.getNativeFromClass(NBTListData::class.java)
        }

    }
}

/**
 *
 * 2024.7.12：我试过了，是真的，会坏
 *
 */
class NBTListConcrete: NBTList, MCFPPValue<ListTag<*>> {

    override lateinit var value: ListTag<*>

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

    constructor(list : NBTList, value: ListTag<*>):super(list){
        this.value = value
    }


    constructor(v: NBTListConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): NBTListConcrete {
        return NBTListConcrete(this)
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
            Property.buildSimpleProperty (if(index is MCInt){
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
        }), this)
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
        val data = CompoundData("list", "mcfpp.lang")
        //注册函数

        init {
            data.extends(MCAny.data)
            data.getNativeFromClass(NBTListConcreteData::class.java)
        }

        val empty = NBTListConcrete(ListTag.createUnchecked(IntTag::class.java), "empty", MCFPPBaseType.Any)

    }
}
