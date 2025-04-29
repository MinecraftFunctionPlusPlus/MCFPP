package top.mcfpp.core.lang

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.lib.SbObject
import top.mcfpp.model.Member
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.compound.EnumMember
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

@Suppress("LeakingThis")
open class EnumVar : Var<EnumVar>, OnScoreboard {

    override var isDataOnly: Boolean = false

    var sbObject: SbObject = SbObject.MCFPP_default

    override fun setObj(sbObject: SbObject): EnumVar {
        this.sbObject = sbObject
        return this
    }

    final override var name: String

    var enum: Enum

    /**
     * 创建一个枚举值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(enum: Enum, identifier: String = TempPool.getVarIdentify()) : super(identifier){
        this.enum = enum
        this.name = identifier
        type = enum.getType()
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: EnumVar) : super(b){
        sbObject = b.sbObject
        name = b.name
        enum = b.enum
        type = enum.getType()
        isDataOnly = b.isDataOnly
    }

    constructor(b: MCInt, enum : Enum): super(b){
        sbObject = b.sbObject
        name = b.name
        this.enum = enum
        type = enum.getType()
        isDataOnly = b.isDataOnly
    }

    override fun doAssignedBy(b: Var<*>): EnumVar {
        return when(b){
            is EnumVar -> {
                if(b.enum != enum){
                    LogProcessor.error("Enum type not match: ${b.enum.identifier}(${b.enum.namespaceID}) and ${enum.identifier}(${enum.namespaceID})")
                    return this
                }
                val i = this.asIntVar().assignedBy(b.asIntVar()) as MCInt
                if(i is MCIntConcrete) {
                    val member = enum.getMember(i.value)
                    if(member == null){
                        LogProcessor.error("Enum member not found: ${i.value}")
                        return this
                    }
                    asNBTVar().assignedBy(NBTBasedDataConcrete(member.data))    //nbt赋值
                    EnumVarConcrete(this, i.value)
                }
                else {
                    asNBTVar().assignedBy(b.asNBTVar())    //nbt赋值
                    this
                }
            }

            is MCStringConcrete -> {
                val value = b.value.value
                val member = enum.members[value]
                if(member == null){
                    LogProcessor.error("Enum member not found: $value")
                    return this
                }
                EnumVarConcrete(enum, member.value)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }


    override fun canAssignedBy(b: Var<*>): Boolean {
        if(!b.implicitCast(type).isError) return true
        if(b is MCStringConcrete) return true
        return false
    }

    override fun clone(): EnumVar {
        return EnumVar(this)
    }

    override fun getTempVar(): EnumVar {
        if (isTemp) return this
        val re = EnumVar(enum)
        re.isTemp = true
        return re.assignedBy(this)
    }

    override fun storeToStack() {
        //什么都不用做哦
    }

    override fun getFromStack() {
        //什么都不用做哦
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return when(key){
            "identifier" -> MCString("identifier").apply { nbtPath = this@EnumVar.nbtPath.memberIndex("identifier") } to true
            "value" -> MCInt("value").apply { nbtPath = this@EnumVar.nbtPath.memberIndex("value")} to true
            "data" -> NBTBasedData("data").apply { nbtPath = this@EnumVar.nbtPath.memberIndex("data")} to true
            else -> null to true
        }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return UnknownFunction(key) to true
    }

    open fun asIntVar(): MCInt{
        return MCInt(this)
    }

    open fun asNBTVar(): NBTBasedData {
        return NBTBasedData(this)
    }

    override fun toNBTVar(): NBTBasedData {
        return asNBTVar()
    }
}

class EnumVarConcrete : EnumVar, MCFPPValue<EnumMember> {

    override var value: EnumMember

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(enum: Enum, value: Int, identifier: String = TempPool.getVarIdentify()) : super(enum ,identifier) {
        this.value = enum.getMember(value)!!
    }

    constructor(enum: EnumVar, value: Int) : super(enum){
        this.value = enum.enum.getMember(value)!!
    }

    constructor(enum: EnumVarConcrete) : super(enum){
        this.value = enum.value
    }

    override fun clone(): EnumVarConcrete {
        return EnumVarConcrete(this)
    }

    /**
     * 动态化
     *
     */
    override fun toDynamic(replace: Boolean): Var<*> {
        //避免错误 Smart cast to 'ClassPointer' is impossible, because 'parent' is a mutable property that could have been changed by this time
        val parent = parent

        if (parentClass() != null) {
            val cmd = Commands.selectRun(parent!!, "scoreboard players set @s ${SbObject.MCFPP_default} $value")
            Function.addCommands(cmd)
        } else {
            val cmd = if (!isTemp)
                Command("execute store result").build(nbtPath.toCommandPart()).build("int 1 run ")
            else
                Command("")
            Function.addCommand(cmd.build("scoreboard players set $name ${SbObject.MCFPP_default} $value", false))
        }
        val re = EnumVar(this)
        NBTBasedDataConcrete(enum.getMember(value.value)!!.data).toDynamic(false)
        if(replace){
            if(parentTemplate() != null){
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else{
                Function.currFunction.field.putVar(identifier, re, true)
            }
        }
        return re
    }

    /**
     * 获取临时变量
     *
     * @return 返回临时变量
     */
    @Override
    @InsertCommand
    override fun getTempVar(): EnumVar {
        if (isTemp) return this
        return EnumVarConcrete(enum, value.value)
    }

    override fun asIntVar(): MCInt {
        return MCIntConcrete(this)
    }

    override fun asNBTVar(): NBTBasedData {
        return NBTBasedDataConcrete(super.asNBTVar(), this.value.data)
    }
}