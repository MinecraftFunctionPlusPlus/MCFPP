package top.mcfpp.core.lang

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.bool.CommandBoolPart
import top.mcfpp.core.lang.bool.ExecuteBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.MCLong
import top.mcfpp.core.lang.nbt.MCLongConcrete
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.core.lang.obj.EnumVarConcrete
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.LongTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import kotlin.math.nextDown
import kotlin.math.nextUp

/**
 * 代表了mc中的一个整数。实质上是记分板中的一个记分项。你可以对它进行加减乘除等基本运算操作，以及大小比较等逻辑运算。
 */
open class MCInt : MCNumber<Int> {

    constructor(curr: FieldContainer, identifier: String = TempPool.getVarIdentify()) : super(curr, identifier)

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    constructor(b: MCInt) : super(b)

    constructor(b: EnumVar): super(b)

    override var type: MCFPPType = MCFPPBaseType.Int

    override fun doAssignedBy(b: Var<*>) : MCInt {
        return when (b) {
            is MCInt -> {
                assignCommand(b)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        val re = super.explicitCast(type)
        if(!re.isError) return re
        //TODO 类支持
        return when (type) {
            MCFPPBaseType.Float -> {
                MCInt("inp").assignedBy(this)
                Function.addCommand("function math:hpo/float/_scoreto")
                return MCFloat().assignedBy(MCFloat.ssObj)
            }
            MCFPPNBTType.Long -> {
                storeToStack()
                val ret = MCLong()
                Function.addCommand(Commands.dataSetFrom(ret.nbtPath, nbtPath))
                ret
            }
            else -> re
        }
    }

    override fun canExplicitCast(type: MCFPPType): Boolean {
        return super.canExplicitCast(type) || type == MCFPPNBTType.Long || type == MCFPPBaseType.Float
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        val re = super.implicitCast(type)
        if(!re.isError) return re
        //TODO 类支持
        return when (type) {
            MCFPPBaseType.Float -> {
                MCInt("inp").assignedBy(this)
                Function.addCommand("function math:hpo/float/_scoreto")
                return MCFloat().assignedBy(MCFloat.ssObj)
            }
            else -> re
        }
    }

    override fun canImplicitCast(type: MCFPPType): Boolean {
        return super.canImplicitCast(type) || type == MCFPPBaseType.Float
    }

    //this = a
    @InsertCommand
    override fun assignCommand(a: MCNumber<*>) : MCInt {
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete =  { b, final ->
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                if(isDataOnly){
                    Function.addCommand(final.last().build(Commands.dataSetValue(nbtPath, IntTag((b as MCIntConcrete).value))))
                }else{
                    Function.addCommand(final.last().build(Commands.sbPlayerSet(this, (b as MCIntConcrete).value)))
                }
                this
            },
            ifThisIsClassMemberAndAIsNotConcrete = { b, final ->
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                if(isDataOnly){
                    Function.addCommand(final.last().build(
                        Command("execute store result").build(nbtPath.toCommandPart()).build("int 1").build("run")
                            .build("scoreboard players get ${(b as MCInt).name} ${b.sbObject}")
                    ))
                }else{
                    Function.addCommand(final.last().build(Commands.sbPlayerOperation(this,"=",b as MCInt)))
                }
                this
            },
            ifThisIsNormalVarAndAIsConcrete = { b ->
                if(isDataOnly){
                    Function.addCommand(Commands.dataSetValue(nbtPath, IntTag((b as MCIntConcrete).value)))
                    this
                }else{
                    MCIntConcrete(this, (b as MCIntConcrete).value)
                }
            },
            ifThisIsNormalVarAndAIsClassMember = { c, cmd ->
                if(cmd.size == 2){
                    Function.addCommand(cmd[0])
                }
                if(isDataOnly){
                    Function.addCommand(cmd.last().build(
                        Command("execute store result").build(nbtPath.toCommandPart()).build("int 1").build("run")
                            .build("scoreboard players get ${(c as MCInt).name} ${c.sbObject}")
                    ))
                }else{
                    Function.addCommand(cmd.last().build(Commands.sbPlayerOperation(this, "=", c as MCInt)))
                }
                MCInt(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = { c ->
                if(isDataOnly){
                    Function.addCommand(
                        Command("execute store result").build(nbtPath.toCommandPart()).build("int 1").build("run")
                            .build("scoreboard players get ${(c as MCInt).name} ${c.sbObject}")
                    )
                }else{
                    Function.addCommand(Commands.sbPlayerOperation(this, "=", c as MCInt))
                }
                MCInt(this)
            }
        ) as MCInt
    }

    @InsertCommand
    override fun plus(a: Var<*>): Var<*> {
        if(!isTemp && a.isTemp && a !is MCIntConcrete){
            return a.plus(this)
        }else if(!isTemp){
            return getTempVar().plus(a)
        }
        when(a){
            is MCIntConcrete -> {
                Function.addCommand(Commands.sbPlayerAdd(this, a.value))
                return this
            }
            is MCInt -> {
                Function.addCommand(Commands.sbPlayerOperation(this, "+=", a))
                return this
            }
            else -> errorOp()
        }
    }

    @InsertCommand
    override fun minus(a: Var<*>): Var<*> {
        if(!isTemp && a.isTemp && a !is MCIntConcrete){
            return a.minus(this)
        }else if(!isTemp){
            return getTempVar().minus(a)
        }
        when(a){
            is MCIntConcrete -> {
                Function.addCommand(Commands.sbPlayerRemove(this, a.value))
                return this
            }
            is MCInt -> {
                Function.addCommand(Commands.sbPlayerOperation(this, "-=", a))
                return this
            }
            else -> errorOp()
        }
    }

    @InsertCommand
    override fun times(a: Var<*>): Var<*> {
        //t *= a
        if(!isTemp && a.isTemp && a !is MCIntConcrete){
            return a.times(this)
        }else if(!isTemp){
            return getTempVar().times(a)
        }
        when(a){
            is MCIntConcrete -> {
                Function.addCommand(Commands.sbPlayerSet(a, a.value))
                Function.addCommand(Commands.sbPlayerOperation(this, "*=", a))
                return this
            }
            is MCInt -> {
                Function.addCommand(Commands.sbPlayerOperation(this, "*=", a))
                return this
            }
            else -> errorOp()
        }
    }

    @InsertCommand
    override fun div(a: Var<*>): Var<*> {
        //t /= a
        if(!isTemp && a.isTemp && a !is MCIntConcrete){
            return a.div(this)
        }else if(!isTemp){
            return getTempVar().div(a)
        }
        when(a){
            is MCIntConcrete -> {
                Function.addCommand(Commands.sbPlayerSet(a, a.value))
                Function.addCommand(Commands.sbPlayerOperation(this, "/=", a))
                return this
            }
            is MCInt -> {
                Function.addCommand(Commands.sbPlayerOperation(this, "/=", a))
                return this
            }
            else -> errorOp()
        }
    }

    @InsertCommand
    override fun rem(a: Var<*>): Var<*> {
        //t %= a
        if(!isTemp && a.isTemp && a !is MCIntConcrete){
            return a.rem(this)
        }else if(!isTemp){
            return getTempVar().rem(a)
        }
        when(a){
            is MCIntConcrete -> {
                Function.addCommand(Commands.sbPlayerSet(a, a.value))
                Function.addCommand(Commands.sbPlayerOperation(this, "%=", a))
                return this
            }
            is MCInt -> {
                Function.addCommand(Commands.sbPlayerOperation(this, "%=", a))
                return this
            }
            else -> errorOp()
        }
    }

    @InsertCommand
    override fun isBigger(a: Var<*>): Var<*> {
        //re = t > a
        if (a !is MCInt) errorOp()
        if(isDataOnly) getFromStack()
        val re = ExecuteBool()
        if (a is MCIntConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re.value.add(CommandBoolPart(false, Command("if score $name $sbObject matches ${a.value + 1}..")))
        } else {
            re.value.add(CommandBoolPart(false, Command("if score $name $sbObject > ${a.name} ${a.sbObject}")))
        }
        re.isTemp = true
        return re
    }

    @InsertCommand
    override fun isSmaller(a: Var<*>): Var<*> {
        //re = t < a
        if (a !is MCInt) errorOp()
        if(isDataOnly) getFromStack()
        val re = ExecuteBool()
        if (a is MCIntConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re.value.add(CommandBoolPart(false, Command("if score $name $sbObject matches ..${a.value - 1}")))
        } else {
            re.value.add(CommandBoolPart(false, Command("if score $name $sbObject < ${a.name} ${a.sbObject}")))
        }
        re.isTemp = true
        return re
    }

    @InsertCommand
    override fun isSmallerOrEqual(a: Var<*>): Var<*> {
        //re = t <= a
        if (a !is MCInt) errorOp()
        if(isDataOnly) getFromStack()
        val re = ExecuteBool()
        if (a is MCIntConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re.value.add(CommandBoolPart(false, Command("if score $name $sbObject matches ..${a.value}")))
        } else {
            re.value.add(CommandBoolPart(false, Command("if score $name $sbObject <= ${a.name} ${a.sbObject}")))
        }
        re.isTemp = true
        return re
    }

    @InsertCommand
    override fun isBiggerOrEqual(a: Var<*>): Var<*> {
        //re = t <= a
        if (a !is MCInt) errorOp()
        if(isDataOnly) getFromStack()
        val re = ExecuteBool()
        if (a is MCIntConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re.value.add(CommandBoolPart(false, Command("if score $name $sbObject matches ${a.value}..")))
        } else {
            re.value.add(CommandBoolPart(false, Command("if score $name $sbObject >= ${a.name} ${a.sbObject}")))
        }
        re.isTemp = true
        return re
    }

    @InsertCommand
    override fun isEqual(a: Var<*>): Var<*> {
        //re = t == a
        if (a !is MCInt) errorOp()
        if(isDataOnly) getFromStack()
        val re = ExecuteBool()
        if (a is MCIntConcrete) {
            //execute store success score qwq qwq if score qwq qwq = owo owo
            re.value.add(CommandBoolPart(false, Command("if score $name $sbObject matches ${a.value}")))
        } else {
            re.value.add(CommandBoolPart(false, Command("if score $name $sbObject = ${a.name} ${a.sbObject}")))
        }
        re.isTemp = true
        return re
    }

    @InsertCommand
    override fun isNotEqual(a: Var<*>): Var<*> {
        //re = t != a
        if (a !is MCInt) errorOp()
        if(isDataOnly) getFromStack()
        val re = ExecuteBool()
        if(a is MCIntConcrete){
            //execute store success score qwq qwq if score qwq qwq matches owo owo
            re.value.add(CommandBoolPart(false, Command("unless score $name $sbObject matches ${a.value}")))
        }else{
            re.value.add(CommandBoolPart(false, Command("unless score $name $sbObject = ${a.name} ${a.sbObject}")))
        }
        re.isTemp = true
        return re
    }

    override fun inRange(a: Var<*>): Var<*> {
        if(a !is RangeVar) errorOp()
        if(a is RangeVarConcrete){
            val left = a.value.first
            val right = a.value.second
            val range = if(a.isIntRange()) a else RangeVarConcrete(left?.nextUp() to right?.nextDown())
            val re = ExecuteBool()
            re.value.add(
                CommandBoolPart(
                    false,
                    Command("if score $name $sbObject matches").build(range.toCommandPart())
                )
            )
        }
        if(a.isIntRange()){
            return a.left.isSmallerOrEqual(this).and(a.right.isBiggerOrEqual(this))
        }
        TODO()
    }

    override fun clone(): MCInt {
        return MCInt(this)
    }

    /**
     * 获取临时变量
     *
     * @return 返回临时变量
     */
    @InsertCommand
    override fun getTempVar(): MCInt {
        if (isTemp) return this
        val re = MCInt()
        re.isTemp = true
        if(isDataOnly) getFromStack()
        return re.assignedBy(this) as MCInt
    }

    override fun storeToStack() {
        if(parentClass() != null || hasStoredInStack) return
        Function.addCommand(Command("execute store result")
            .build(nbtPath.toCommandPart())
            .build("int 1 run scoreboard players get $name $sbObject"))
        hasStoredInStack = true
    }

    override fun getFromStack() {
        if(parent != null) return
        Function.addCommand(
            Command("execute store result score $name $sbObject run data get")
                .build(nbtPath.toCommandPart())
        )
    }
}

class MCIntConcrete : MCInt, MCFPPValue<Int> {

    override var value: Int = 0

    /**
     * 创建一个固定的int
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: Int,
        identifier: String = TempPool.getVarIdentify()
    ) : super(curr, identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Int, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    constructor(int: MCInt, value: Int) : super(int){
        this.value = value
    }

    constructor(int: MCIntConcrete) : super(int){
        this.value = int.value
    }

    constructor(enum: EnumVarConcrete) : super(enum){
        this.value = enum.value.value
    }

    override fun clone(): MCIntConcrete {
        return MCIntConcrete(this)
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    /**
     * 动态化
     *
     */
    override fun toDynamic(replace: Boolean): Var<*> {
        //避免错误 Smart cast to 'ClassPointer' is impossible, because 'parent' is a mutable property that could have been changed by this time
        val parent = parent

        if (parentClass() != null) {
            val cmd = Commands.selectRun(parent!!, "scoreboard players set @s $sbObject $value")
            Function.addCommands(cmd)
        } else {
            Function.addCommand("scoreboard players set $name $sbObject $value")
        }
        val re = MCInt(this)
        if(replace){
            if(parentTemplate() != null){
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else{
                Function.currFunction.scope.putVar(identifier, re, true)
            }
        }
        return re
    }

    @Override
    override fun explicitCast(type: MCFPPType): Var<*> {
        val re = super.explicitCast(type)
        if(!re.isError) return re
        //TODO 类支持
        return when (type) {
            MCFPPBaseType.Float -> MCFloatConcrete(value.toFloat(), this.identifier)
            MCFPPNBTType.Long -> {
                storeToStack()
                return MCLongConcrete(LongTag(value.toLong()), this.identifier)
            }
            else -> re
        }
    }

    @InsertCommand
    override fun plus(a: Var<*>): Var<*> {
        //t = t + a
        if(!isTemp) return getTempVar().plus(a)
        when(a){
            is MCIntConcrete -> {
                value += a.value
                return this
            }
            is MCInt -> {
                return a.plus(this)
            }
            else -> errorOp()
        }
    }

    @InsertCommand
    override fun minus(a: Var<*>): Var<*> {
        //t = t + a
        if(!isTemp) return getTempVar().minus(a)
        when(a){
            is MCIntConcrete -> {
                value -= a.value
                return this
            }
            is MCInt -> {
                return a.minus(this)
            }
            else -> errorOp()
        }
    }


    @Override
    @InsertCommand
    override fun times(a: Var<*>): Var<*> {
        //t = t * a
        if(!isTemp) return getTempVar().times(a)
        when(a){
            is MCIntConcrete -> {
                value *= a.value
                return this
            }
            is MCInt -> {
                return a.times(this)
            }
            else -> errorOp()
        }
    }

    @Override
    @InsertCommand
    override fun div(a: Var<*>): Var<*> {
        //t = t / a
        if(!isTemp) return getTempVar().div(a)
        when(a){
            is MCIntConcrete -> {
                value /= a.value
                return this
            }
            is MCInt -> {
                return a.div(this)
            }
            else -> errorOp()
        }
    }

    @Override
    @InsertCommand
    override fun rem(a: Var<*>): Var<*> {
        //t = t % a
        if(!isTemp) return getTempVar().rem(a)
        when(a){
            is MCIntConcrete -> {
                value %= a.value
                return this
            }
            is MCInt -> {
                return a.rem(this)
            }
            else -> errorOp()
        }
    }

    @Override
    @InsertCommand
    override fun isBigger(a: Var<*>): Var<*> {
        //re = t > a
        if (a !is MCInt) errorOp()
        return if (a is MCIntConcrete) {
            ScoreBoolConcrete(value > a.value)
        } else {
            //注意大小于换符号！
            a.isSmaller(this)
        }
    }

    @Override
    @InsertCommand
    override fun isSmaller(a: Var<*>): Var<*> {
        //re = t < a
        if (a !is MCInt) errorOp()
        return if (a is MCIntConcrete) {
            ScoreBoolConcrete(value < a.value)
        } else {
            a.isBigger(this)
        }
    }

    @Override
    @InsertCommand
    override fun isSmallerOrEqual(a: Var<*>): Var<*> {
        //re = t <= a
        if (a !is MCInt) errorOp()
        return if (a is MCIntConcrete) {
            ScoreBoolConcrete(value <= a.value)
        } else {
            a.isBiggerOrEqual(this)
        }
    }

    @Override
    @InsertCommand
    override fun isBiggerOrEqual(a: Var<*>): Var<*> {
        //re = t <= a
        if (a !is MCInt) errorOp()
        return if (a is MCIntConcrete) {
            ScoreBoolConcrete(value >= a.value)
        } else {
            a.isSmallerOrEqual(this)
        }
    }

    @Override
    @InsertCommand
    override fun isEqual(a: Var<*>): Var<*> {
        //re = t == a
        if (a !is MCInt) errorOp()
        return if (a is MCIntConcrete) {
            ScoreBoolConcrete(value == a.value)
        } else {
            a.isEqual(this)
        }
    }

    @Override
    @InsertCommand
    override fun isNotEqual(a: Var<*>): Var<*> {
        //re = t != a
        if (a !is MCInt) errorOp()
        return if (a is MCIntConcrete) {
            ScoreBoolConcrete(value != a.value)
        } else {
            a.isNotEqual(this)
        }
    }

    override fun inRange(a: Var<*>): Var<*> {
        if(a !is RangeVar) errorOp()
        if(a is RangeVarConcrete){
            val left = a.value.first
            val right = a.value.second
            if(left != null && value < left) return ScoreBoolConcrete(false)
            if(right!= null && value > right) return ScoreBoolConcrete(false)
            return ScoreBoolConcrete(true)
        }
        if(!a.isIntRange()){
            return toDynamic(false).inRange(a)
        }else{
            TODO()
        }
    }

    /**
     * 获取临时变量
     *
     * @return 返回临时变量
     */
    @Override
    @InsertCommand
    override fun getTempVar(): MCIntConcrete {
        if (isTemp) return this
        return MCIntConcrete(value).apply { isTemp = true }
    }

    override fun toNBTVar(): NBTBasedData {
        return NBTBasedDataConcrete(super.toNBTVar(), IntTag(value))
    }
}