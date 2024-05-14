package top.mcfpp.lang

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPClassType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.*
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import java.util.*

/**
 * 代表了mc中的一个整数。实质上是记分板中的一个记分项。你可以对它进行加减乘除等基本运算操作，以及大小比较等逻辑运算。
 */
open class MCInt : MCNumber<Int> {
    /**
     * 创建一个int类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(curr.prefix + identifier) {
        this.identifier = identifier
    }

    /**
     * 创建一个int值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: MCInt) : super(b)

    override var type: MCFPPType = MCFPPBaseType.Int

    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var<*>) : MCInt {
        hasAssigned = true
        return when (b) {
            is MCInt -> {
                assignCommand(b)
            }
            else -> {
                throw VariableConverseException()
            }
        }
    }

    @Override
    override fun cast(type: MCFPPType): Var<*> {
        //TODO 类支持
        return when (type) {
            this.type -> this
            MCFPPBaseType.Float -> {
                MCInt("inp").assign(this)
                Function.addCommand("function math:hpo/float/_scoreto")
                return MCFloat().assign(MCFloat.ssObj)
            }
            MCFPPBaseType.Any -> MCAnyConcrete(this)
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                throw VariableConverseException()
            }
        }
    }

    @Override
    override fun assignCommand(a: MCNumber<Int>): MCInt {
        return assignCommand(a,null)
    }

    @InsertCommand
    fun assignCommand(a: MCNumber<Int>, replace: String?) : MCInt {
        if (parent != null) {
            val b = if(a.parent != null){
                a.getTempVar() as MCNumber<*>
            }else a
            //类的成员是运行时动态的
            //t = a
            //是成员
            val final = when(val parent = parent){
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                is MCFPPClassType -> {
                    arrayOf(Command.build("execute as ${parent.cls.uuid} run "))
                }
                else -> TODO()
            }
            if (b is MCIntConcrete) {
                //对类中的成员的值进行修改
                if(replace == null){
                    if(final.size == 2){
                        Function.addCommand(final[0])
                    }
                    final.last().build(Commands.sbPlayerSet(this,b.value as Int))
                }else{
                    Function.replaceCommand(final.last().build(replace), Function.currFunction.commands.size - 1)
                }
            } else {
                //对类中的成员的值进行修改
                if(replace == null){
                    if(final.size == 2){
                        Function.addCommand(final[0])
                    }
                    Function.addCommand(final.last().build(Commands.sbPlayerOperation(this,"=",b as MCInt)))
                }else{
                    Function.replaceCommand(final.last().build(replace), Function.currFunction.commands.size - 1)
                }
            }
            return this
        } else {
            //t = a
            if (a is MCIntConcrete) {
                return MCIntConcrete(this, a.value)
            } else {
                if(a.parent != null){
                    if(isTemp){
                        //a是成员
                        val cmd = when(val parent = a.parent){
                            is ClassPointer -> {
                                Commands.selectRun(parent)
                            }
                            is MCFPPClassType -> {
                                arrayOf(Command.build("execute as ${parent.cls.uuid} run "))
                            }
                            else -> TODO()
                        }
                        if(replace == null){
                            if(cmd.size == 2){
                                Function.addCommand(cmd[0])
                            }
                            Function.addCommand(cmd.last()
                                .build(Commands.sbPlayerOperation(this, "=", a as MCInt)))
                        }else{
                            Function.replaceCommand(cmd.last()
                                .build(replace), Function.currFunction.commands.size-1)
                        }
                    }
                    val append = "store result storage mcfpp:system " + Project.config.defaultNamespace + ".stack_frame[" + stackIndex + "]." + identifier + " int 1 run "
                    //是成员
                    val cmd = when(val parent = a.parent){
                        is ClassPointer -> {
                            Commands.selectRun(parent)
                        }
                        is MCFPPClassType -> {
                            arrayOf(Command.build("execute as ${parent.cls.uuid} ").build("run ","run"))
                        }
                        else -> TODO()
                    }
                    cmd.last().replace("run" to append)
                    if(replace == null){
                        if(cmd.size == 2){
                            Function.addCommand(cmd[0])
                        }
                        Function.addCommand(cmd.last().build(Commands.sbPlayerOperation(this, "=", a as MCInt)))
                    }else{
                        Function.replaceCommand(cmd.last().build(replace), Function.currFunction.commands.size-1)
                    }
                }else{
                    val head = if(isTemp) "" else "execute store result storage mcfpp:system " + Project.config.defaultNamespace + ".stack_frame[" + stackIndex + "]." + identifier + " int 1 run "
                    //变量进栈
                    if(replace == null){
                        Function.addCommand(Command.build(head)
                            .build(Commands.sbPlayerOperation(this, "=", a as MCInt)))
                    }else{
                        Function.replaceCommand(Command.build(head)
                            .build(replace), Function.currFunction.commands.size-1)
                    }
                }
            }
            return MCInt(this)  //复制为未跟踪变量
        }
    }

    @Override
    @InsertCommand
    override fun plus(a: Var<*>): Var<*> {
        //t += a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        if(!isTemp){
            return a.plus(getTempVar() as MCInt)
        }
        if (qwq is MCIntConcrete) {
            Function.addCommand(Commands.sbPlayerAdd(this, qwq.value))
            return this
        } else {
            Function.addCommand(Commands.sbPlayerOperation(this, "+=", qwq))
            return this
        }
    }

    @Override
    @InsertCommand
    override fun minus(a: Var<*>): Var<*> {
        //t -= a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        if(!isTemp){
            return a.minus(getTempVar() as MCInt)
        }
        if (qwq is MCIntConcrete) {
            Function.addCommand(Commands.sbPlayerRemove(this, qwq.value))
            return this
        } else {
            Function.addCommand(Commands.sbPlayerOperation(this, "-=", qwq))
            return this
        }
    }

    @Override
    @InsertCommand
    override fun multiple(a: Var<*>): Var<*> {
        //t *= a
        if(!isTemp){
            return (getTempVar() as MCInt).multiple(a)
        }
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        if (qwq is MCIntConcrete) {
            Function.addCommand(Commands.sbPlayerSet(qwq, qwq.value))
        }
        Function.addCommand(Commands.sbPlayerOperation(this, "*=", qwq))
        return this
    }

    @Override
    @InsertCommand
    override fun divide(a: Var<*>): Var<*> {
        if(!isTemp){
            return (getTempVar() as MCInt).divide(a)
        }
        //t /= a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        if (qwq is MCIntConcrete) {
            Function.addCommand(Commands.sbPlayerSet(qwq, qwq.value))
        }
        Function.addCommand(Commands.sbPlayerOperation(this, "/=", qwq))
        return this
    }

    @Override
    @InsertCommand
    override fun modular(a: Var<*>): Var<*> {
        if(!isTemp){
            return (getTempVar() as MCInt).modular(a)
        }
        //t %= a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        if (qwq is MCIntConcrete) {
            Function.addCommand(Commands.sbPlayerSet(qwq, qwq.value))
        }
        Function.addCommand(Commands.sbPlayerOperation(this, "%=", qwq))
        return this
    }

    @Override
    @InsertCommand
    override fun isBigger(a: Var<*>): MCBool {
        //re = t > a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        val re: MCBool
        if (qwq is MCIntConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute if score " + name + " " + `object` + " matches " + (qwq.value + 1) + ".. run return 1"
            )
            Function.addCommand("return 0")
        } else {
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute if score " + name + " " + `object` + " > " + qwq.name + " " + qwq.`object` + " run return 1"
            )
            Function.addCommand("return 0")
        }
        re.isTemp = true
        return re
    }

    @Override
    @InsertCommand
    override fun isSmaller(a: Var<*>): MCBool {
        //re = t < a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        val re: MCBool
        if (qwq is MCIntConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute if score " + name + " " + `object` + " matches " + ".." + (qwq.value - 1) + " run return 1"
            )
            Function.addCommand("return 0")
        } else {
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute if score " + name + " " + `object` + " < " + qwq.name + " " + qwq.`object` + " run return 1"
            )
            Function.addCommand("return 0")
        }
        re.isTemp = true
        return re
    }

    @Override
    @InsertCommand
    override fun isSmallerOrEqual(a: Var<*>): MCBool {
        //re = t <= a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        val re: MCBool
        if (qwq is MCIntConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute if score " + name + " " + `object` + " matches " + ".." + qwq.value + " run return 1"
            )
            Function.addCommand("return 0")
        } else {
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute if score " + name + " " + `object` + " <= " + qwq.name + " " + qwq.`object` + " run return 1"
            )
            Function.addCommand("return 0")
        }
        re.isTemp = true
        return re
    }

    @Override
    @InsertCommand
    override fun isGreaterOrEqual(a: Var<*>): MCBool {
        //re = t <= a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        val re: MCBool
        if (qwq is MCIntConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute if score " + name + " " + `object` + " matches " + qwq.value + ".. run return 1"
            )
            Function.addCommand("return 0")
        } else {
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute if score " + name + " " + `object` + " >= " + qwq.name + " " + qwq.`object` + " run return 1"
            )
            Function.addCommand("return 0")
        }
        re.isTemp = true
        return re
    }

    @Override
    @InsertCommand
    override fun isEqual(a: Var<*>): MCBool {
        //re = t == a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        val re: MCBool
        if (qwq is MCIntConcrete) {
            //execute store success score qwq qwq if score qwq qwq = owo owo
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute if score " + name + " " + `object` + " matches " + qwq.value + " run return 1"
            )
            Function.addCommand("return 0")
        } else {
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute if score " + name + " " + `object` + " = " + qwq.name + " " + qwq.`object` + " run return 1"
            )
            Function.addCommand("return 0")
        }
        re.isTemp = true
        return re
    }

    @Override
    @InsertCommand
    override fun isNotEqual(a: Var<*>): MCBool {
        //re = t != a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        val re: MCBool
        if (qwq is MCIntConcrete) {
            //execute store success score qwq qwq if score qwq qwq = owo owo
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute unless score " + name + " " + `object` + " matches " + qwq.value + " run return 1"
            )
            Function.addCommand("return 0")
        } else {
            re = ReturnedMCBool(Function.currFunction)
            Function.addCommand(
                "execute unless score " + name + " " + `object` + " = " + qwq.name + " " + qwq.`object` + " run return 1"
            )
            Function.addCommand("return 0")
        }
        re.isTemp = true
        return re
    }

    @Override
    override fun clone(): MCInt {
        return MCInt(this)
    }


    //private var index: Int = -1

    /**
     * 获取临时变量
     *
     * @return 返回临时变量
     */
    @Override
    @InsertCommand
    override fun getTempVar(): Var<*> {
        if (isTemp) return this
        val re = MCInt()
        re.isTemp = true
        return re.assign(this)
    }

    override fun storeToStack() {
        if(parent != null) return
        Function.addCommand("execute " +
                "store result storage mcfpp:system ${Project.config.defaultNamespace}.stack_frame[$stackIndex].$identifier int 1 " +
                "run scoreboard players get $name $`object`")
    }

    override fun getFromStack() {
        if(parent != null) return
        Function.addCommand("execute " +
                "store result score $name $`object` " +
                "run data get storage mcfpp:system ${Project.config.defaultNamespace}.stack_frame[0].$identifier")
    }

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
     * @param normalParams 成员方法的参数
     * @return 返回一个值对。第一个值是成员变量或null（如果成员方法不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberFunction(
        key: String,
        readOnlyParams:List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.getFunction(key, readOnlyParams, normalParams) to true
    }

    companion object {
        val data = CompoundData("int","mcfpp")

        init {
            data.initialize()
            data.extends(MCAny.data)
        }
    }
}

class MCIntConcrete : MCInt, MCFPPValue<Int>{

    override var value: Int

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
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Int, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        this.value = value
    }

    constructor(int: MCInt, value: Int) : super(int){
        this.value = value
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
            Function.addCommand(cmd.last().build("scoreboard players set @s $`object` $value"))
        } else {
            val cmd: String = if (!isTemp)
                "execute store result storage mcfpp:system " + Project.config.defaultNamespace + ".stack_frame[" + stackIndex + "]." + identifier + " int 1 run "
            else
                ""
            Function.addCommand(cmd + "scoreboard players set $name $`object` $value")
        }
        val re = MCInt(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    @Override
    override fun cast(type: MCFPPType): Var<*> {
        //TODO 类支持
        return when (type) {
            this.type -> this
            MCFPPBaseType.Float -> MCFloatConcrete(value.toFloat())
            MCFPPBaseType.Any -> this
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                throw VariableConverseException()
            }
        }
    }

    @InsertCommand
    override fun plus(a: Var<*>): Var<*> {
        //t = t + a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        if (qwq is MCIntConcrete) {
            value += qwq.value
            return this
        } else {
            return qwq.plus(this)
        }
    }

    @InsertCommand
    override fun minus(a: Var<*>): Var<*> {
        //t = t + a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        if (qwq is MCIntConcrete) {
            value -= qwq.value
            return this
        } else {
            return qwq.minus(this)
        }
    }


    @Override
    @InsertCommand
    override fun multiple(a: Var<*>): Var<*> {
        //t = t * a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        if (qwq is MCIntConcrete) {
            value *= qwq.value
            return this
        } else {
            return qwq.multiple(this)
        }
    }

    @Override
    @InsertCommand
    override fun divide(a: Var<*>): Var<*> {
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        if (qwq is MCIntConcrete) {
            value /= qwq.value
            return this
        } else {
            return qwq.divide(this)
        }
    }

    @Override
    @InsertCommand
    override fun modular(a: Var<*>): Var<*> {
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        if (qwq is MCIntConcrete) {
            value %= qwq.value
            return this
        } else {
            return qwq.modular(this)
        }
    }

    @Override
    @InsertCommand
    override fun isBigger(a: Var<*>): MCBool {
        //re = t > a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        return if (qwq is MCIntConcrete) {
            MCBoolConcrete(value > qwq.value)
        } else {
            //注意大小于换符号！
            qwq.isSmaller(this)
        }
    }

    @Override
    @InsertCommand
    override fun isSmaller(a: Var<*>): MCBool {
        //re = t < a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        return if (qwq is MCIntConcrete) {
            MCBoolConcrete(value < qwq.value)
        } else {
            qwq.isBigger(this)
        }
    }

    @Override
    @InsertCommand
    override fun isSmallerOrEqual(a: Var<*>): MCBool {
        //re = t <= a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        return if (qwq is MCIntConcrete) {
            MCBoolConcrete(value <= qwq.value)
        } else {
            qwq.isGreaterOrEqual(this)
        }
    }

    @Override
    @InsertCommand
    override fun isGreaterOrEqual(a: Var<*>): MCBool {
        //re = t <= a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        return if (qwq is MCIntConcrete) {
            MCBoolConcrete(value >= qwq.value)
        } else {
            qwq.isSmallerOrEqual(this)
        }
    }

    @Override
    @InsertCommand
    override fun isEqual(a: Var<*>): MCBool {
        //re = t == a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        return if (qwq is MCIntConcrete) {
            MCBoolConcrete(value == qwq.value)
        } else {
            qwq.isEqual(this)
        }
    }

    @Override
    @InsertCommand
    override fun isNotEqual(a: Var<*>): MCBool {
        //re = t != a
        val qwq: MCInt = if (a !is MCInt) a.cast(MCFPPBaseType.Int) as MCInt else a
        return if (qwq is MCIntConcrete) {
            MCBoolConcrete(value != qwq.value)
        } else {
            qwq.isNotEqual(this)
        }
    }

    /**
     * 获取临时变量
     *
     * @return 返回临时变量
     */
    @Override
    @InsertCommand
    override fun getTempVar(): Var<*> {
        if (isTemp) return this
        return MCIntConcrete(value)
    }
}