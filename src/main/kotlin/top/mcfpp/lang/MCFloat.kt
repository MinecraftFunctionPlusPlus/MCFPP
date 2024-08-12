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
import top.mcfpp.lib.SbObject
import top.mcfpp.model.*
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

open class MCFloat : MCNumber<Float> {

    var sign: MCInt
    var int0: MCInt
    var int1: MCInt
    var exp : MCInt

    /**
     * 创建一个float类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(curr: FieldContainer, identifier: String = UUID.randomUUID().toString()) : this(curr.prefix + identifier){
        sign = MCInt(name).setObj(SbObject.MCS_float_sign) as MCInt
        int0 = MCInt(name).setObj(SbObject.MCS_float_int0) as MCInt
        int1 = MCInt(name).setObj(SbObject.MCS_float_int1) as MCInt
        exp = MCInt(name).setObj(SbObject.MCS_float_exp) as MCInt
    }

    /**
     * 创建一个float值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        sign = MCInt(name).setObj(SbObject.MCS_float_sign) as MCInt
        int0 = MCInt(name).setObj(SbObject.MCS_float_int0) as MCInt
        int1 = MCInt(name).setObj(SbObject.MCS_float_int1) as MCInt
        exp = MCInt(name).setObj(SbObject.MCS_float_exp) as MCInt
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: MCFloat) : super(b){
        sign = MCInt(b.sign)
        int0 = MCInt(b.int0)
        int1 = MCInt(b.int1)
        exp = MCInt(b.exp)
    }

    override var type: MCFPPType = MCFPPBaseType.Float

    /**
     * 将分数储存在临时实体中
     *
     */
    @InsertCommand
    open fun toTempEntity() : MCFloat{
        val parent = parent
        if (parent != null) {
            val cmd = when(parent){
                is MCFPPClassType -> {
                    arrayOf(Command.build("execute as ${parent.cls.uuid} run "))
                }
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                else -> TODO()
            }
            if(cmd.size == 2){
                Function.addCommand(cmd[0])
            }
            Function.addCommand(cmd.last().build("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_sign} = @s ${sign.sbObject} "))
            Function.addCommand(cmd.last().build("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_int0} = @s ${int0.sbObject} "))
            Function.addCommand(cmd.last().build("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_int1} = @s ${int1.sbObject} "))
            Function.addCommand(cmd.last().build("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_exp} = @s ${exp.sbObject} "))
        } else {
            Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_exp} = ${exp.name} ${exp.sbObject}")
            Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_sign} = ${sign.name} ${sign.sbObject}")
            Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_int0} = ${int0.name} ${int0.sbObject}")
            Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_int1} = ${int1.name} ${int1.sbObject}")
        }
        return tempFloat
    }

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var<*>) : MCFloat {
        hasAssigned = true
        return when(b){
            is MCFloat ->{
                assignCommand(b)
            }
            else ->{
                throw VariableConverseException()
            }
        }
    }

    /**
     * 赋值
     * @param a 值来源
     */
    @InsertCommand
    override fun assignCommand(a: MCNumber<Float>) : MCFloat {
        val parent = parent
        if(a.parent != null) TODO()
        if(parent != null){
            val cmd = when(parent){
                is MCFPPClassType -> {
                    arrayOf(Command.build("execute as ${parent.cls.uuid} run "))
                }
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                else -> TODO()
            }
            //类的成员是运行时动态的
            //t = a
            val pwp = a as MCFloat
            if(cmd.size == 2){
                Function.addCommand(cmd[0])
            }
            if(pwp is MCFloatConcrete){
                //对类中的成员的值进行修改
                val qwq = floatToMCFloat(pwp.value)
                Function.addCommand(cmd.last().build("scoreboard players set @s ${sign.sbObject} ${qwq[0]}"))
                Function.addCommand(cmd.last().build("scoreboard players set @s ${int0.sbObject} ${qwq[1]}"))
                Function.addCommand(cmd.last().build("scoreboard players set @s ${int1.sbObject} ${qwq[2]}"))
                Function.addCommand(cmd.last().build("scoreboard players set @s ${exp.sbObject} ${qwq[3]}"))
            }else{
                //对类中的成员的值进行修改
                Function.addCommand(cmd.last().build("scoreboard players operation @s ${sign.sbObject} = ${pwp.sign.name} ${pwp.sign.sbObject}"))
                Function.addCommand(cmd.last().build("scoreboard players operation @s ${int0.sbObject} = ${pwp.int0.name} ${pwp.int0.sbObject}"))
                Function.addCommand(cmd.last().build("scoreboard players operation @s ${int1.sbObject} = ${pwp.int1.name} ${pwp.int1.sbObject}"))
                Function.addCommand(cmd.last().build("scoreboard players operation @s ${exp.sbObject} = ${pwp.exp.name} ${pwp.exp.sbObject}"))
                //Function.addCommand("$cmd run scoreboard players operation @s $`object` = ${a.name} ${a.`object`}")
            }
            return this
        }else{
            //this = a
            if(a is MCFloatConcrete){
                return MCFloatConcrete(this, a.value)
            }
            val pwp = a as MCFloat
            if(isTemp){
                Function.addCommand("scoreboard players operation ${sign.name} ${sign.sbObject} = ${pwp.sign.name} ${pwp.sign.sbObject}")
                Function.addCommand("scoreboard players operation ${int0.name} ${int0.sbObject} = ${pwp.int0.name} ${pwp.int0.sbObject}")
                Function.addCommand("scoreboard players operation ${int1.name} ${int1.sbObject} = ${pwp.int1.name} ${pwp.int1.sbObject}")
                Function.addCommand("scoreboard players operation ${exp.name} ${exp.sbObject} = ${pwp.exp.name} ${pwp.exp.sbObject}")
            }else{
                Function.addCommand("execute " +
                        "store result storage mcfpp:system " + Project.config.rootNamespace + ".stack_frame[" + stackIndex + "]." + identifier + ".sign" + " int 1 " +
                        "run scoreboard players operation ${sign.name} ${sign.sbObject} = ${pwp.sign.name} ${pwp.sign.sbObject}")
                Function.addCommand("execute " +
                        "store result storage mcfpp:system " + Project.config.rootNamespace + ".stack_frame[" + stackIndex + "]." + identifier + ".int0" + " int 1 " +
                        "run scoreboard players operation ${int0.name} ${int0.sbObject} = ${pwp.int0.name} ${pwp.int0.sbObject}")
                Function.addCommand("execute " +
                        "store result storage mcfpp:system " + Project.config.rootNamespace + ".stack_frame[" + stackIndex + "]." + identifier + ".int1" + " int 1 " +
                        "run scoreboard players operation ${int1.name} ${int1.sbObject} = ${pwp.int1.name} ${pwp.int1.sbObject}")
                Function.addCommand("execute " +
                        "store result storage mcfpp:system " + Project.config.rootNamespace + ".stack_frame[" + stackIndex + "]." + identifier + ".exp" + " int 1 " +
                        "run scoreboard players operation ${exp.name} ${exp.sbObject} = ${pwp.exp.name} ${pwp.exp.sbObject}")
            }
            return this
        }
    }

    /**
     * 加法
     * @param a 加数
     * @return 计算的结果
     */
    @InsertCommand
    override fun plus(a: Var<*>): Var<*> {
        //t = t + a
        if(!isTemp) return (getTempVar() as MCFloat).plus(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        if(qwq != tempFloat) qwq.toTempEntity()
        Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_add")
        return this
    }

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    @InsertCommand
    override fun minus(a: Var<*>): Var<*> {
        //t = t - a
        if(!isTemp) return (getTempVar() as MCFloat).minus(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        if(qwq != tempFloat) qwq.toTempEntity()
        Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_rmv")
        return this
    }

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    @InsertCommand
    override fun multiple(a: Var<*>): Var<*> {
        //t = t * a
        if(!isTemp) return (getTempVar() as MCFloat).minus(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        if(qwq != tempFloat) qwq.toTempEntity()
        Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_mult")
        return this
    }

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    @InsertCommand
    override fun divide(a: Var<*>): Var<*> {
        //t = t - a
        if(!isTemp) return (getTempVar() as MCFloat).divide(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        if(qwq != tempFloat) qwq.toTempEntity()
        Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_div")
        return this
    }

    /**
     * 取余。浮点数无法取余
     * @param a 除数
     * @return 计算的结果
     */
    @InsertCommand
    override fun modular(a: Var<*>): Var<*> {
        LogProcessor.error("Cannot get the remainder of a float number")
        throw IllegalArgumentException("")
    }

    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isBigger(a: Var<*>): MCBool {
        //re = t > a
        if(!isTemp) return (getTempVar() as MCFloat).isBigger(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re = MCBool()
        if(qwq != tempFloat) qwq.toTempEntity()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math:hpo/float/_isbigger")
        return re
    }

    /**
     * 这个数是否小于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isSmaller(a: Var<*>): MCBool {
        //re = t < a
        if(!isTemp) return (getTempVar() as MCFloat).isSmaller(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re = MCBool()
        if(qwq != tempFloat) qwq.toTempEntity()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math:hpo/float/_issmaller")
        return re
    }

    /**
     * 这个数是否小于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isSmallerOrEqual(a: Var<*>): MCBool {
        //re = t <= a
        if(!isTemp) return (getTempVar() as MCFloat).isSmallerOrEqual(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re = MCBool()
        if(qwq != tempFloat) qwq.toTempEntity()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math:hpo/float/_issmallerorequal")
        return re
    }

    /**
     * 这个数是否大于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isGreaterOrEqual(a: Var<*>): MCBool {
        //re = t >= a
        if(!isTemp) return (getTempVar() as MCFloat).isGreaterOrEqual(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re = MCBool()
        if(qwq != tempFloat) qwq.toTempEntity()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math:hpo/float/_isbiggerorequal")
        return re
    }

    /**
     * 这个数是否等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isEqual(a: Var<*>): MCBool {
        //re = t == a
        if(!isTemp) return (getTempVar() as MCFloat).isEqual(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re = MCBool()
        if(qwq != tempFloat) qwq.toTempEntity()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math:hpo/float/_equal")
        return re
    }

    /**
     * 这个数是否不等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isNotEqual(a: Var<*>): MCBool {
        //re = t != a
        if(!isTemp) return (getTempVar() as MCFloat).isNotEqual(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re = MCBool()
        if(qwq != tempFloat) qwq.toTempEntity()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math:hpo/float/_notequal")
        return re
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    @InsertCommand
    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            this.type -> this
            MCFPPBaseType.Int -> {
                ssObj.assign(this)
                Function.addCommand("function math:hpo/float/_toscore")
                val temp = MCInt("res")
                val re = MCInt()
                re.assign(temp)
                re
            }
            MCFPPBaseType.Any -> MCAnyConcrete(this)
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                throw VariableConverseException()
            }
        }
    }

    override fun clone(): MCFloat {
        return MCFloat(this)
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @param cache 随便写一个啦（比如`HashMap()`什么的），这里的cache没用的
     *
     * @return
     */
    @InsertCommand
    override fun getTempVar(): Var<*> {
        Function.addCommand("scoreboard players operation float_exp int = ${exp.name} ${exp.sbObject}")
        Function.addCommand("scoreboard players operation float_int0 int = ${int0.name} ${int0.sbObject}")
        Function.addCommand("scoreboard players operation float_int1 int = ${int1.name} ${int1.sbObject}")
        Function.addCommand("scoreboard players operation float_sign int = ${sign.name} ${sign.sbObject}")
        return ssObj
    }

    override fun storeToStack() {
        TODO("Not yet implemented")
    }

    override fun getFromStack() {
        TODO("Not yet implemented")
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

    companion object{

        val data = CompoundData("float","mcfpp")

        const val tempFloatEntityUUID = "53aa19cc-a067-402b-8ba1-9328cc5fb6c1"
        const val tempFloatEntityUUIDNBT = "[I;1403656652,-1603846101,-1952345304,-866142527]"

        fun floatToMCFloat(float: Float): Array<Int>{
            //获取指数部分
            val sign = if (float < 0) -1 else if(float == 0f) 0 else 1
            val absFloat = float.absoluteValue
            val exponent = floor(log10(absFloat.toDouble())).toInt()
            val factor = 10.0.pow((8 - exponent - 1).toDouble()).toInt()
            val n =  exponent + 1
            val qwq = (absFloat * factor).toInt()
            return arrayOf(sign, qwq/10000, qwq%10000, n)
        }

        val ssObj = MCFloatConcrete(value = Float.NaN)

        val tempFloat : MCFloat = MCFloat()

        init {
            ssObj.sign = MCInt("float_sign").setObj(SbObject.Math_int) as MCInt
            ssObj.int0 = MCInt("float_int0").setObj(SbObject.Math_int) as MCInt
            ssObj.int1 = MCInt("float_int1").setObj(SbObject.Math_int) as MCInt
            ssObj.exp = MCInt("float_exp").setObj(SbObject.Math_int) as MCInt
            ssObj.isTemp = true
            tempFloat.sign = MCInt(tempFloatEntityUUID).setObj(SbObject.MCS_float_sign) as MCInt
            tempFloat.exp = MCInt(tempFloatEntityUUID).setObj(SbObject.MCS_float_exp) as MCInt
            tempFloat.int0 = MCInt(tempFloatEntityUUID).setObj(SbObject.MCS_float_int0) as MCInt
            tempFloat.int1 = MCInt(tempFloatEntityUUID).setObj(SbObject.MCS_float_int1) as MCInt
            tempFloat.isTemp = true
        }

        fun ssObjToVar(identifier: String = UUID.randomUUID().toString()) : MCFloat{
            val re = MCFloat(identifier)
            re.isTemp = true
            re.assign(ssObj)
            return re
        }
    }
}

class MCFloatConcrete : MCFloat, MCFPPValue<Float>{

    override var value: Float
        set(value) {
            field = value
            setJavaValue(value)
        }

    /**
     * 创建一个固定的float
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(curr: FieldContainer, value: Float, identifier: String = UUID.randomUUID().toString()) : super(curr.prefix + identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的float。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(identifier: String = UUID.randomUUID().toString(), value: Float) : super(identifier) {
        this.value = value
    }

    constructor(float: MCFloat, value: Float): super(float){
        this.value = value
    }

    constructor(v: MCFloatConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): MCFloatConcrete {
        return MCFloatConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val qwq = MCFloat(this)
        (sign as MCIntConcrete).toDynamic(false)
        (int0 as MCIntConcrete).toDynamic(false)
        (int1 as MCIntConcrete).toDynamic(false)
        (exp as MCIntConcrete).toDynamic(false)
        if(replace){
            Function.currFunction.field.putVar(identifier, qwq, true)
        }
        return qwq
    }

    /**
     * 设置值，并更新记分板
     *
     * @param value
     */
    private fun setJavaValue(value: Float){
        val qwq = floatToMCFloat(value)
        sign = MCIntConcrete(qwq[0], name).setObj(SbObject.MCS_float_sign) as MCIntConcrete
        int0 = MCIntConcrete(qwq[1], name).setObj(SbObject.MCS_float_int0) as MCIntConcrete
        int1 = MCIntConcrete(qwq[2], name).setObj(SbObject.MCS_float_int1) as MCIntConcrete
        exp = MCIntConcrete(qwq[3], name).setObj(SbObject.MCS_float_exp) as MCIntConcrete
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    @InsertCommand
    override fun cast(type: MCFPPType): Var<*> {
        return when(type) {
            this.type -> this
            MCFPPBaseType.Int -> MCIntConcrete(value.toInt())
            MCFPPBaseType.Any -> MCAnyConcrete(this)
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                throw VariableConverseException()
            }
        }
    }

    /**
     * 将分数储存在临时实体中
     */
    @InsertCommand
    override fun toTempEntity() : MCFloat{
        val parent = parent
        if (parent != null) {
            val cmd = when(parent){
                is MCFPPClassType -> {
                    arrayOf(Command.build("execute as ${parent.cls.uuid} run "))
                }
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                else -> TODO()
            }
            if(cmd.size == 2){
                Function.addCommand(cmd[0])
            }
            Function.addCommand(cmd.last().build("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_sign} ${(sign as MCIntConcrete).value}"))
            Function.addCommand(cmd.last().build("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_int0} ${(int0 as MCIntConcrete).value}"))
            Function.addCommand(cmd.last().build("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_int1} ${(int1 as MCIntConcrete).value}"))
            Function.addCommand(cmd.last().build("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_exp} ${(exp as MCIntConcrete).value}"))
        } else {
            Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_sign} ${(sign as MCIntConcrete).value}")
            Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_int0} ${(int0 as MCIntConcrete).value}")
            Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_int1} ${(int1 as MCIntConcrete).value}")
            Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_exp} ${(exp as MCIntConcrete).value}")
        }
        return tempFloat
    }


    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     *
     * @return
     */
    @InsertCommand
    override fun getTempVar(): Var<*> {
        ssObj.value = value
        val qwq = floatToMCFloat(value)
        Function.addCommand("scoreboard players set float_exp int ${qwq[0]}")
        Function.addCommand("scoreboard players set float_int0 int ${qwq[1]}")
        Function.addCommand("scoreboard players set float_int1 int ${qwq[2]}")
        Function.addCommand("scoreboard players set float_sign int ${qwq[3]}")
        return ssObj
    }

    /**
     * 加法
     * @param a 加数
     * @return 计算的结果
     */
    @InsertCommand
    override fun plus(a: Var<*>): Var<*> {
        //t = t + a
        if(!isTemp) return (getTempVar() as MCFloat).plus(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        if (qwq is MCFloatConcrete) {
            this.value += qwq.value
        } else {
            val re = toDynamic(true)
            if(qwq != tempFloat) qwq.toTempEntity()
            Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_add")
            return re
        }
        return this
    }

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    @InsertCommand
    override fun minus(a: Var<*>): Var<*> {
        //t = t - a
        if(!isTemp) return (getTempVar() as MCFloat).minus(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        if (qwq is MCFloatConcrete) {
            this.value -= qwq.value
        } else {
            val re = toDynamic(true)
            if(qwq != tempFloat) qwq.toTempEntity()
            Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_rmv")
            return re
        }
        return this
    }

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    @InsertCommand
    override fun multiple(a: Var<*>): Var<*> {
        //t = t * a
        if(!isTemp) return (getTempVar() as MCFloat).multiple(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        if (qwq is MCFloatConcrete) {
            this.value *= qwq.value
        } else {
            val re = toDynamic(true)
            if(qwq != tempFloat) qwq.toTempEntity()
            Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_mult")
            return re
        }
        return this
    }

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    @InsertCommand
    override fun divide(a: Var<*>): Var<*> {
        //t = t - a
        if(!isTemp) return (getTempVar() as MCFloat).divide(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        if (qwq is MCFloatConcrete) {
            this.value /= qwq.value
        } else {
            val re = toDynamic(true)
            if(qwq != tempFloat) qwq.toTempEntity()
            Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_div")
            return re
        }
        return this
    }

    /**
     * 取余。浮点数无法取余
     * @param a 除数
     * @return 计算的结果
     */
    @InsertCommand
    override fun modular(a: Var<*>): Var<*> {
        throw IllegalArgumentException("")
    }

    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isBigger(a: Var<*>): MCBool {
        //re = t > a
        if(!isTemp) return (getTempVar() as MCFloat).isBigger(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re : MCBool
        if (qwq is MCFloatConcrete) {
            re = MCBoolConcrete(value > qwq.value)
        } else {
            toDynamic(true)
            if(qwq != tempFloat) qwq.toTempEntity()
            re = MCBool()
            Function.addCommand(
                "execute " +
                        "store result score ${re.name} ${re.boolObject} " +
                        "as $tempFloatEntityUUID " +
                        "run function math:hpo/float/_isbigger")
        }
        return re
    }

    /**
     * 这个数是否小于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isSmaller(a: Var<*>): MCBool {
        //re = t < a
        if(!isTemp) return (getTempVar() as MCFloat).isSmaller(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re : MCBool
        if (qwq is MCFloatConcrete) {
            re = MCBoolConcrete(value < qwq.value)
        } else {
            toDynamic(true)
            if(qwq != tempFloat) qwq.toTempEntity()
            re = MCBool()
            Function.addCommand(
                "execute " +
                        "store result score ${re.name} ${re.boolObject} " +
                        "as $tempFloatEntityUUID " +
                        "run function math:hpo/float/_issmaller")
        }
        return re
    }

    /**
     * 这个数是否小于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isSmallerOrEqual(a: Var<*>): MCBool {
        //re = t <= a
        if(!isTemp) return (getTempVar() as MCFloat).isSmallerOrEqual(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re : MCBool
        if (qwq is MCFloatConcrete) {
            re = MCBoolConcrete(value <= qwq.value)
        } else {
            toDynamic(true)
            if(qwq != tempFloat) qwq.toTempEntity()
            re = MCBool()
            Function.addCommand(
                "execute " +
                        "store result score ${re.name} ${re.boolObject} " +
                        "as $tempFloatEntityUUID " +
                        "run function math:hpo/float/_issmallerorequal")
        }
        return re
    }

    /**
     * 这个数是否大于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isGreaterOrEqual(a: Var<*>): MCBool {
        //re = t >= a
        if(!isTemp) return (getTempVar() as MCFloat).isSmallerOrEqual(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re : MCBool
        if (qwq is MCFloatConcrete) {
            re = MCBoolConcrete(value >= qwq.value)
        } else {
            toDynamic(true)
            if(qwq != tempFloat) qwq.toTempEntity()
            re = MCBool()
            Function.addCommand(
                "execute " +
                        "store result score ${re.name} ${re.boolObject} " +
                        "as $tempFloatEntityUUID " +
                        "run function math:hpo/float/_isbiggerorequal")
        }
        return re
    }

    /**
     * 这个数是否等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isEqual(a: Var<*>): MCBool {
        //re = t == a
        if(!isTemp) return (getTempVar() as MCFloat).isEqual(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re : MCBool
        if (qwq is MCFloatConcrete) {
            re = MCBoolConcrete(value >= qwq.value)
        } else {
            toDynamic(true)
            if(qwq != tempFloat) qwq.toTempEntity()
            re = MCBool()
            Function.addCommand(
                "execute " +
                        "store result score ${re.name} ${re.boolObject} " +
                        "as $tempFloatEntityUUID " +
                        "run function math:hpo/float/_equal")
        }
        return re
    }

    /**
     * 这个数是否不等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isNotEqual(a: Var<*>): MCBool {
        //re = t != a
        if(!isTemp) return (getTempVar() as MCFloat).isNotEqual(a)
        val qwq: MCFloat = if (a !is MCFloat) a.cast(MCFPPBaseType.Float) as MCFloat else a
        val re : MCBool
        if (qwq is MCFloatConcrete) {
            re = MCBoolConcrete(value >= qwq.value)
        } else {
            toDynamic(true)
            if(qwq != tempFloat) qwq.toTempEntity()
            re = MCBool()
            Function.addCommand(
                "execute " +
                        "store result score ${re.name} ${re.boolObject} " +
                        "as $tempFloatEntityUUID " +
                        "run function math:hpo/float/_notequal")
        }
        return re
    }
}