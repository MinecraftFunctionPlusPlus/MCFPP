package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

open class MCByte: MCInt {

    override var type: MCFPPType = MCFPPNBTType.Byte
    constructor(curr: FieldContainer, identifier: String = TempPool.getVarIdentify()) : super(curr, identifier)

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    constructor(b: MCByte) : super(b)

    constructor(b: MCInt): super(b)

    override fun doAssignedBy(b: Var<*>) : MCByte {
        return when (b) {
            is MCByte -> {
                assignCommand(b)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    //this = a
    @InsertCommand
    override fun assignCommand(a: MCNumber<*>) : MCByte {
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
            ifThisIsNormalVarAndAIsConcrete = { b ->
                MCByteConcrete(this, (b as MCByteConcrete).value)
            },
            ifThisIsNormalVarAndAIsClassMember = { c, cmd ->
                if(cmd.size == 2){
                    Function.addCommand(cmd[0])
                }
                Function.addCommand(cmd.last().build(Commands.sbPlayerOperation(this, "=", c as MCInt)))
                MCByte(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = { c ->
                //变量进栈
                Function.addCommand(Commands.sbPlayerOperation(this, "=", c as MCInt))
                MCByte(this)
            }
        ) as MCByte
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        val re = super.implicitCast(type)
        if(!re.isError) return re
        return when (type) {
            MCFPPNBTType.Short -> MCShort(this)
            MCFPPBaseType.Int -> MCInt(this)
            else -> re
        }
    }

    override fun canImplicitCast(type: MCFPPType): Boolean {
        return super.canImplicitCast(type) || type == MCFPPNBTType.Short || type == MCFPPBaseType.Int
    }
    override fun storeToStack() {
        if(parentClass() != null || hasStoredInStack) return
        Function.addCommand(
            Command("execute store result")
            .build(nbtPath.toCommandPart())
            .build("byte 1 run scoreboard players get $name $sbObject"))
        hasStoredInStack = true
    }


}


class MCByteConcrete: MCByte, MCFPPValue<Byte> {

    override var value: Byte
    /**
     * 创建一个固定的string
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: Byte,
        identifier: String = TempPool.getVarIdentify()
    ) : super(curr, identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的string。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Byte, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    constructor(v: MCByte, value: Byte) : super(v) {
        this.value = value
    }

    constructor(v: MCByteConcrete) : super(v) {
        this.value = v.value
    }

    override fun clone(): MCByteConcrete {
        return MCByteConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        MCIntConcrete(this, value.toInt()).toDynamic(replace)
        return MCByte(this)
    }

    override fun plus(a: Var<*>): Var<*> {
        //t = t + a
        when(a){
            is MCByteConcrete -> {
                value = (value + a.value).toByte()
                return this
            }
            is MCByte -> {
                return a.plus(this)
            }
            else -> errorOp()
        }
    }

    override fun minus(a: Var<*>): Var<*> {
        //t = t + a
        when(a){
            is MCByteConcrete -> {
                value = (value - a.value).toByte()
                return this
            }
            is MCByte -> {
                return a.minus(this)
            }
            else -> errorOp()
        }
    }

    override fun times(a: Var<*>): Var<*> {
        //t = t * a
        when(a){
            is MCByteConcrete -> {
                value = (value * a.value).toByte()
                return this
            }
            is MCByte -> {
                return a.times(this)
            }
            else -> errorOp()
        }
    }

    override fun div(a: Var<*>): Var<*> {
        //t = t / a
        when(a){
            is MCByteConcrete -> {
                value = (value / a.value).toByte()
                return this
            }
            is MCByte -> {
                return a.div(this)
            }
            else -> errorOp()
        }
    }

    override fun rem(a: Var<*>): Var<*> {
        //t = t % a
        when(a){
            is MCByteConcrete -> {
                value = (value % a.value).toByte()
                return this
            }
            is MCByte -> {
                return a.rem(this)
            }
            else -> errorOp()
        }
    }

    override fun isBigger(a: Var<*>): Var<*> {
        //re = t > a
        if (a !is MCByte) errorOp()
        return if (a is MCByteConcrete) {
            ScoreBoolConcrete(value > a.value)
        } else {
            //注意大小于换符号！
            a.isSmaller(this)
        }
    }

    override fun isSmaller(a: Var<*>): Var<*> {
        //re = t < a
        if (a !is MCByte) errorOp()
        return if (a is MCByteConcrete) {
            ScoreBoolConcrete(value < a.value)
        } else {
            a.isBigger(this)
        }
    }

    override fun isSmallerOrEqual(a: Var<*>): Var<*> {
        //re = t <= a
        if (a !is MCByte) errorOp()
        return if (a is MCByteConcrete) {
            ScoreBoolConcrete(value <= a.value)
        } else {
            a.isBiggerOrEqual(this)
        }
    }

    override fun isBiggerOrEqual(a: Var<*>): Var<*> {
        //re = t <= a
        if (a !is MCByte) errorOp()
        return if (a is MCByteConcrete) {
            ScoreBoolConcrete(value >= a.value)
        } else {
            a.isSmallerOrEqual(this)
        }
    }

    override fun isEqual(a: Var<*>): Var<*> {
        //re = t == a
        if (a !is MCByte) errorOp()
        return if (a is MCByteConcrete) {
            ScoreBoolConcrete(value == a.value)
        } else {
            a.isEqual(this)
        }
    }

    override fun isNotEqual(a: Var<*>): Var<*> {
        //re = t != a
        if (a !is MCByte) errorOp()
        return if (a is MCByteConcrete) {
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
    override fun getTempVar(): MCByte {
        if (isTemp) return this
        return MCByteConcrete(value)
    }

}
