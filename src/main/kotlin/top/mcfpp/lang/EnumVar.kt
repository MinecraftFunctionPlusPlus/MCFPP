package top.mcfpp.lang

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPClassType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.lib.SbObject
import top.mcfpp.model.Enum
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

open class EnumVar : Var<EnumVar> {

    var sbObject: SbObject = SbObject.MCFPP_default

    var enum: Enum

    /**
     * 创建一个枚举类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        enum: Enum,
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(enum, curr.prefix + identifier) {
        this.identifier = identifier
    }

    /**
     * 创建一个枚举值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(enum: Enum, identifier: String = UUID.randomUUID().toString()) : super(identifier){
        this.enum = enum
        type = enum.getType()
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: EnumVar) : super(b){
        enum = b.enum
        type = enum.getType()
    }

    constructor(b: MCInt, enum : Enum): super(b){
        sbObject = b.sbObject
        this.enum = enum
        type = enum.getType()
    }

    override fun onAssign(b: Var<*>): EnumVar {
        var v = b.implicitCast(this.type)
        if(!v.isError){
            v = b
        }
        hasAssigned = true
        return when(v){
            is EnumVar -> {
                val i = this.getIntVar().assign(v.getIntVar()) as MCInt
                if(i is MCIntConcrete) EnumVarConcrete(i, enum)
                else EnumVar(i, enum)
            }
            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(v.type.typeName, type.typeName))
                this
            }
        }
    }

    override fun clone(): EnumVar {
        return EnumVar(this)
    }

    override fun getTempVar(): EnumVar {
        if (isTemp) return this
        val re = EnumVar(enum)
        re.isTemp = true
        return re.assign(this)
    }

    override fun storeToStack() {
        if(parent != null) return
        Function.addCommand(
            Command("execute store result")
                .build(nbtPath.toCommandPart())
                .build("int 1 run scoreboard players get $name ${SbObject.MCFPP_default}")
        )
    }

    override fun getFromStack() {
        if(parent != null) return
        Function.addCommand(
            Command("execute store result score $name ${SbObject.MCFPP_default} run data get")
                .build(nbtPath.toCommandPart())
        )
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }

    open fun getIntVar(): MCInt{
        return MCInt(this)
    }
}

class EnumVarConcrete : EnumVar, MCFPPValue<Int>{

    override var value: Int

    /**
     * 创建一个固定的int
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        enum: Enum,
        curr: FieldContainer,
        value: Int,
        identifier: String = UUID.randomUUID().toString()
    ) : super(enum, curr.prefix + identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(enum: Enum, value: Int, identifier: String = UUID.randomUUID().toString()) : super(enum ,identifier) {
        this.value = value
    }

    constructor(enum: EnumVar, value: Int) : super(enum){
        this.value = value
    }

    constructor(enum: EnumVarConcrete) : super(enum){
        this.value = enum.value
    }

    constructor(b: MCIntConcrete, enum : Enum): super(b, enum){
        value = b.value
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
            Function.addCommand(cmd.last().build("scoreboard players set @s ${SbObject.MCFPP_default} $value"))
        } else {
            val cmd = if (!isTemp)
                Command("execute store result").build(nbtPath.toCommandPart()).build("int 1 run ")
            else
                Command("")
            Function.addCommand(cmd.build("scoreboard players set $name ${SbObject.MCFPP_default} $value", false))
        }
        val re = EnumVar(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    @Override
    override fun explicitCast(type: MCFPPType): Var<*> {
        //TODO 类支持
        return when (type) {
            this.type -> this
            MCFPPBaseType.Any -> this
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                throw VariableConverseException()
            }
        }
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
        return EnumVarConcrete(enum ,value)
    }

    override fun getIntVar(): MCInt {
        return MCIntConcrete(this)
    }
}