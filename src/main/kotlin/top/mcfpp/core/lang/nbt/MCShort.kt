package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

open class MCShort: MCInt {

    override var type: MCFPPType = MCFPPNBTType.Short

    constructor(curr: FieldContainer, identifier: String = TempPool.getVarIdentify()) : super(curr, identifier)

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    constructor(b: MCShort) : super(b)

    constructor(b: MCInt): super(b)

    override fun doAssignedBy(b: Var<*>) : MCInt {
        return when (b) {
            is MCInt -> {
                assignCommand(b)
            }

            is CommandReturn -> {
                if(parentClass() != null){
                    Function.addCommands(
                        Commands.selectRun(parent!!, b.command, false)
                    )
                }else{
                    Function.addCommand(b.command)
                }
                MCInt(this)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }


    //this = a
    @InsertCommand
    override fun assignCommand(a: MCNumber<*>) : MCShort {
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete =  { b, final ->
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                Function.addCommand(final.last().build(Commands.sbPlayerSet(this, (b as MCIntConcrete).value)))
                this
            },
            ifThisIsClassMemberAndAIsNotConcrete = { b, final ->
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                Function.addCommand(final.last().build(Commands.sbPlayerOperation(this,"=",b as MCInt)))
                this
            },
            ifThisIsNormalVarAndAIsConcrete = { b, _ ->
                MCShortConcrete(this, (b as MCShortConcrete).value)
            },
            ifThisIsNormalVarAndAIsClassMember = { c, cmd ->
                if(cmd.size == 2){
                    Function.addCommand(cmd[0])
                }
                Function.addCommand(cmd.last().build(Commands.sbPlayerOperation(this, "=", c as MCInt)))
                MCByte(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = { c, _ ->
                //变量进栈
                Function.addCommand(Commands.sbPlayerOperation(this, "=", c as MCInt))
                MCByte(this)
            }
        ) as MCShort
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        if(!b.implicitCast(type).isError) return true
        return when(b){
            is CommandReturn -> true
            else -> false
        }
    }
}


class MCShortConcrete: MCShort, MCFPPValue<Short> {

    override var value: Short
    /**
     * 创建一个固定的string
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: Short,
        identifier: String = TempPool.getVarIdentify()
    ) : super(curr, identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的string。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Short, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    constructor(v: MCShort, value: Short) : super(v) {
        this.value = value
    }

    constructor(v: MCShortConcrete) : super(v) {
        this.value = v.value
    }

    override fun clone(): MCShortConcrete {
        return MCShortConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        MCIntConcrete(this, value.toInt()).toDynamic(replace)
        return MCShort(this)
    }

    override fun plus(a: Var<*>): Var<*> {
        //t = t + a
        when(a){
            is MCShortConcrete -> {
                value = (value + a.value).toShort()
                return this
            }
            is MCShort -> {
                return a.plus(this)
            }
            else -> errorOp()
        }
    }

    override fun minus(a: Var<*>): Var<*> {
        //t = t + a
        when(a){
            is MCShortConcrete -> {
                value = (value - a.value).toShort()
                return this
            }
            is MCShort -> {
                return a.minus(this)
            }
            else -> errorOp()
        }
    }

    override fun times(a: Var<*>): Var<*> {
        //t = t * a
        when(a){
            is MCShortConcrete -> {
                value = (value * a.value).toShort()
                return this
            }
            is MCShort -> {
                return a.times(this)
            }
            else -> errorOp()
        }
    }

    override fun div(a: Var<*>): Var<*> {
        //t = t / a
        when(a){
            is MCShortConcrete -> {
                value = (value / a.value).toShort()
                return this
            }
            is MCShort -> {
                return a.div(this)
            }
            else -> errorOp()
        }
    }

    override fun rem(a: Var<*>): Var<*> {
        //t = t % a
        when(a){
            is MCShortConcrete -> {
                value = (value % a.value).toShort()
                return this
            }
            is MCShort -> {
                return a.rem(this)
            }
            else -> errorOp()
        }
    }

    override fun isBigger(a: Var<*>): Var<*> {
        //re = t > a
        if (a !is MCShort) errorOp()
        return if (a is MCShortConcrete) {
            ScoreBoolConcrete(value > a.value)
        } else {
            //注意大小于换符号！
            a.isSmaller(this)
        }
    }

    override fun isSmaller(a: Var<*>): Var<*> {
        //re = t < a
        if (a !is MCShort) errorOp()
        return if (a is MCShortConcrete) {
            ScoreBoolConcrete(value < a.value)
        } else {
            a.isBigger(this)
        }
    }

    override fun isSmallerOrEqual(a: Var<*>): Var<*> {
        //re = t <= a
        if (a !is MCShort) errorOp()
        return if (a is MCShortConcrete) {
            ScoreBoolConcrete(value <= a.value)
        } else {
            a.isBiggerOrEqual(this)
        }
    }

    override fun isBiggerOrEqual(a: Var<*>): Var<*> {
        //re = t <= a
        if (a !is MCShort) errorOp()
        return if (a is MCShortConcrete) {
            ScoreBoolConcrete(value >= a.value)
        } else {
            a.isSmallerOrEqual(this)
        }
    }

    override fun isEqual(a: Var<*>): Var<*> {
        //re = t == a
        if (a !is MCShort) errorOp()
        return if (a is MCShortConcrete) {
            ScoreBoolConcrete(value == a.value)
        } else {
            a.isEqual(this)
        }
    }

    override fun isNotEqual(a: Var<*>): Var<*> {
        //re = t != a
        if (a !is MCShort) errorOp()
        return if (a is MCShortConcrete) {
            ScoreBoolConcrete(value != a.value)
        } else {
            a.isNotEqual(this)
        }
    }

    /**
     * 获取临时变量
     *
     * @return 返回临时变量
     */
    override fun getTempVar(): MCShort {
        if (isTemp) return this
        return MCShortConcrete(value)
    }

}
