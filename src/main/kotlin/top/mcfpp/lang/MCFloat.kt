package top.mcfpp.lang

import org.jetbrains.annotations.Nullable
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.Function
import top.mcfpp.lib.GlobalField
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

class MCFloat : Number<Float> {

    lateinit var sign: MCInt
    lateinit var int0: MCInt
    lateinit var int1: MCInt
    lateinit var exp : MCInt

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
     * 创建一个固定的float
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(curr: FieldContainer, value: Float, identifier: String = UUID.randomUUID().toString()) : super(curr.prefix + identifier) {
        isConcrete = true
        isDynamic = false
        setValue(value)
    }

    /**
     * 创建一个固定的float。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Float, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        isConcrete = true
        isDynamic = false
        setValue(value)
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

    @get:Override
    override val type: String
        get() = "float"

    override var isDynamic: Boolean = true
    override fun toDynamic() {
        isDynamic = true
        exp.toDynamic()
        int0.toDynamic()
        int1.toDynamic()
        sign.toDynamic()
    }

    /**
     * 设置值，并更新记分板
     *
     * @param value
     */
    fun setValue(value: Float?){
        val qwq = floatToMCFloat(value!!)
        sign = MCInt(qwq[0], name).setObj(SbObject.MCS_float_sign) as MCInt
        int0 = MCInt(qwq[1], name).setObj(SbObject.MCS_float_int0) as MCInt
        int1 = MCInt(qwq[2], name).setObj(SbObject.MCS_float_int1) as MCInt
        exp = MCInt(qwq[3], name).setObj(SbObject.MCS_float_exp) as MCInt
    }

    /**
     * 将分数储存在临时实体中
     *
     */
    fun storeToTempEntity() {
        if (isConcrete){
            if (isClassMember) {
                //如果是类的成员
                val cmd: String = if (!isStatic) {
                    //静态
                    "execute as @e[type=marker,tag=${clsPointer!!.clsType.tag}] " +
                            "if score @s ${clsPointer!!.address.`object`.name} = ${clsPointer!!.name} ${clsPointer!!.address.`object`.name}"
                } else {
                    "execute as @e[type=marker,tag=${clsPointer!!.clsType.staticTag}]"
                }
                Function.addCommand("$cmd run scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_sign} ${sign.value}")
                Function.addCommand("$cmd run scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_int0} ${int0.value}")
                Function.addCommand("$cmd run scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_int1} ${int1.value}")
                Function.addCommand("$cmd run scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_exp} ${exp.value}")
            } else {
                Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_sign} ${sign.value}")
                Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_int0} ${int0.value}")
                Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_int1} ${int1.value}")
                Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_exp} ${exp.value}")
            }
        }else{
            if (isClassMember) {
                //如果是类的成员
                val cmd: String = if (!isStatic) {
                    //静态
                    "execute as @e[type=marker,tag=${clsPointer!!.clsType.tag}] " +
                            "if score @s ${clsPointer!!.address.`object`.name} = ${clsPointer!!.name} ${clsPointer!!.address.`object`.name}"
                } else {
                    "execute as @e[type=marker,tag=${clsPointer!!.clsType.staticTag}]"
                }
                Function.addCommand("$cmd run scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_sign} = @s ${sign.`object`} ")
                Function.addCommand("$cmd run scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_int0} = @s ${int0.`object`} ")
                Function.addCommand("$cmd run scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_int1} = @s ${int1.`object`} ")
                Function.addCommand("$cmd run scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_exp} = @s ${exp.`object`} ")
            } else {
                Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_exp} = ${exp.name} ${exp.`object`}")
                Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_sign} = ${sign.name} ${sign.`object`}")
                Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_int0} = ${int0.name} ${int0.`object`}")
                Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_int1} = ${int1.name} ${int1.`object`}")
            }
        }

    }

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
        when(b){
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
    override fun assignCommand(a: Number<Float>) {
        if(a.isClassMember) TODO()
        if(isClassMember){
            //如果是类的成员
            val cmd : String = if(!isStatic){
                //静态
                "execute as @e[type=marker,tag=${clsPointer!!.clsType.tag}] " +
                        "if score @s ${clsPointer!!.address.`object`.name} = ${clsPointer!!.name} ${clsPointer!!.address.`object`.name}"
            }else{
                "execute as @e[type=marker,tag=${clsPointer!!.clsType.staticTag}]"
            }
            //类的成员是运行时动态的
            isConcrete = false
            //t = a
            val pwp = a as MCFloat
            if(pwp.isConcrete){
                //对类中的成员的值进行修改
                val qwq = floatToMCFloat(pwp.value!!)
                Function.addCommand("$cmd run scoreboard players set @s ${sign.`object`} ${qwq[0]}")
                Function.addCommand("$cmd run scoreboard players set @s ${int0.`object`} ${qwq[1]}")
                Function.addCommand("$cmd run scoreboard players set @s ${int1.`object`} ${qwq[2]}")
                Function.addCommand("$cmd run scoreboard players set @s ${exp.`object`} ${qwq[3]}")
            }else{
                //对类中的成员的值进行修改
                Function.addCommand("$cmd run scoreboard players set @s ${sign.`object`} = ${pwp.sign.name} ${pwp.sign.`object`}")
                Function.addCommand("$cmd run scoreboard players set @s ${int0.`object`} = ${pwp.int0.name} ${pwp.int0.`object`}")
                Function.addCommand("$cmd run scoreboard players set @s ${int1.`object`} = ${pwp.int1.name} ${pwp.int1.`object`}")
                Function.addCommand("$cmd run scoreboard players set @s ${exp.`object`} = ${pwp.exp.name} ${pwp.exp.`object`}")
                //Function.addCommand("$cmd run scoreboard players operation @s $`object` = ${a.name} ${a.`object`}")
            }
        }else{
            //t = a
            if (a.isConcrete) {
                isConcrete = true
                value = a.value
                setValue(a.value)
            } else {
                val pwp = a as MCFloat
                isConcrete = false
                Function.addCommand("execute " +
                        "store result storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + identifier + ".sign" + " int 1 " +
                        "run scoreboard players set ${sign.name} ${sign.`object`} = ${pwp.sign.name} ${pwp.sign.`object`}")
                Function.addCommand("execute " +
                        "store result storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + identifier + ".int0" + " int 1 " +
                        "run scoreboard players set ${int0.name} ${int0.`object`} = ${pwp.int0.name} ${pwp.int0.`object`}")
                Function.addCommand("execute " +
                        "store result storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + identifier + ".int1" + " int 1 " +
                        "run scoreboard players set ${int1.name} ${int1.`object`} = ${pwp.int1.name} ${pwp.int1.`object`}")
                Function.addCommand("execute " +
                        "store result storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + identifier + ".exp" + " int 1 " +
                        "run scoreboard players set ${exp.name} ${exp.`object`} = ${pwp.exp.name} ${pwp.exp.`object`}")
            }
        }
    }

    /**
     * 加法
     * @param a 加数
     * @return 计算的结果
     */
    @InsertCommand
    override fun plus(a: Number<Float>): Number<Float> {
        //t = t + a
        if(!isTemp) return getTempVar(HashMap()).plus(a)
        val qwq = a as MCFloat
        if (qwq.isConcrete) {
            if (isConcrete) {
                setValue(value!! + qwq.value!!)
            } else {
                qwq.storeToTempEntity()
                Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_add")
            }
        } else {
            if (!isDynamic) {
                toDynamic()
            }
            qwq.storeToTempEntity()
            Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_add")
        }
        return this
    }

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    @InsertCommand
    override fun minus(a: Number<Float>): Number<Float> {
        //t = t - a
        if(!isTemp) return getTempVar(HashMap()).minus(a)
        val qwq = a as MCFloat
        if (qwq.isConcrete) {
            if (isConcrete) {
                setValue(value!! - qwq.value!!)
            } else {
                qwq.storeToTempEntity()
                Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_rmv")
            }
        } else {
            if (!isDynamic) {
                toDynamic()
            }
            qwq.storeToTempEntity()
            Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_rmv")
        }
        return this
    }

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    @InsertCommand
    override fun multiple(a: Number<Float>): Number<Float> {
        //t = t * a
        if(!isTemp) return getTempVar(HashMap()).multiple(a)
        val qwq = a as MCFloat
        if (qwq.isConcrete) {
            if (isConcrete) {
                setValue(value!! * qwq.value!!)
            } else {
                qwq.storeToTempEntity()
                Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_mult")
            }
        } else {
            if (!isDynamic) {
                toDynamic()
            }
            qwq.storeToTempEntity()
            Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_mult")
        }
        return this
    }

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    @InsertCommand
    override fun divide(a: Number<Float>): Number<Float> {
        //t = t - a
        if(!isTemp) return getTempVar(HashMap()).divide(a)
        val qwq = a as MCFloat
        if (qwq.isConcrete) {
            if (isConcrete) {
                setValue(value!! / qwq.value!!)
            } else {
                qwq.storeToTempEntity()
                Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_div")
            }
        } else {
            if (!isDynamic) {
                toDynamic()
            }
            qwq.storeToTempEntity()
            Function.addCommand("execute as $tempFloatEntityUUID run function math:hpo/float/_div")
        }
        return this
    }

    /**
     * 取余。浮点数无法取余
     * @param a 除数
     * @return 计算的结果
     */
    @InsertCommand
    override fun modular(a: Number<Float>): Number<Float> {
        throw IllegalArgumentException("")
    }

    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isGreater(a: Number<Float>): MCBool {
        //re = t > a
        if(!isTemp) return getTempVar(HashMap()).isGreater(a)
        val qwq = a as MCFloat
        val re : MCBool
        if (qwq.isConcrete) {
            if (isConcrete) {
                re = MCBool(value!! > qwq.value!!)
            } else {
                re = MCBool()
                qwq.storeToTempEntity()
                Function.addCommand(
                    "execute " +
                        "store result score ${re.name} ${re.boolObject} " +
                        "as $tempFloatEntityUUID " +
                        "run function math:hpo/float/_isbigger")
            }
        } else {
            if (!isDynamic) {
                toDynamic()
            }
            qwq.storeToTempEntity()
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
    override fun isLess(a: Number<Float>): MCBool {
        //re = t < a
        if(!isTemp) return getTempVar(HashMap()).isLess(a)
        val qwq = a as MCFloat
        val re : MCBool
        if (qwq.isConcrete) {
            if (isConcrete) {
                re = MCBool(value!! < qwq.value!!)
            } else {
                re = MCBool()
                qwq.storeToTempEntity()
                Function.addCommand(
                    "execute " +
                            "store result score ${re.name} ${re.boolObject} " +
                            "as $tempFloatEntityUUID " +
                            "run function math:hpo/float/_issmaller")
            }
        } else {
            if (!isDynamic) {
                toDynamic()
            }
            qwq.storeToTempEntity()
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
    override fun isLessOrEqual(a: Number<Float>): MCBool {
        //re = t <= a
        if(!isTemp) return getTempVar(HashMap()).isLessOrEqual(a)
        val qwq = a as MCFloat
        val re : MCBool
        if (qwq.isConcrete) {
            if (isConcrete) {
                re = MCBool(value!! <= qwq.value!!)
            } else {
                re = MCBool()
                qwq.storeToTempEntity()
                Function.addCommand(
                    "execute " +
                            "store result score ${re.name} ${re.boolObject} " +
                            "as $tempFloatEntityUUID " +
                            "run function math:hpo/float/_issmallerorequal")
            }
        } else {
            if (!isDynamic) {
                toDynamic()
            }
            qwq.storeToTempEntity()
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
    override fun isGreaterOrEqual(a: Number<Float>): MCBool {
        //re = t >= a
        if(!isTemp) return getTempVar(HashMap()).isLessOrEqual(a)
        val qwq = a as MCFloat
        val re : MCBool
        if (qwq.isConcrete) {
            if (isConcrete) {
                re = MCBool(value!! >= qwq.value!!)
            } else {
                re = MCBool()
                qwq.storeToTempEntity()
                Function.addCommand(
                    "execute " +
                            "store result score ${re.name} ${re.boolObject} " +
                            "as $tempFloatEntityUUID " +
                            "run function math:hpo/float/_isbiggerorequal")
            }
        } else {
            if (!isDynamic) {
                toDynamic()
            }
            qwq.storeToTempEntity()
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
    override fun isEqual(a: Number<Float>): MCBool {
        //re = t == a
        if(!isTemp) return getTempVar(HashMap()).isEqual(a)
        val qwq = a as MCFloat
        val re : MCBool
        if (qwq.isConcrete) {
            if (isConcrete) {
                re = MCBool(value!! == qwq.value!!)
            } else {
                re = MCBool()
                qwq.storeToTempEntity()
                Function.addCommand(
                    "execute " +
                            "store result score ${re.name} ${re.boolObject} " +
                            "as $tempFloatEntityUUID " +
                            "run function math:hpo/float/_equal")
            }
        } else {
            if (!isDynamic) {
                toDynamic()
            }
            qwq.storeToTempEntity()
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
    override fun notEqual(a: Number<Float>): MCBool {
        //re = t != a
        if(!isTemp) return getTempVar(HashMap()).isEqual(a)
        val qwq = a as MCFloat
        val re : MCBool
        if (qwq.isConcrete) {
            if (isConcrete) {
                re = MCBool(value!! != qwq.value!!)
            } else {
                re = MCBool()
                qwq.storeToTempEntity()
                Function.addCommand(
                    "execute " +
                            "store result score ${re.name} ${re.boolObject} " +
                            "as $tempFloatEntityUUID " +
                            "run function math:hpo/float/_notequal")
            }
        } else {
            if (!isDynamic) {
                toDynamic()
            }
            qwq.storeToTempEntity()
            re = MCBool()
            Function.addCommand(
                "execute " +
                        "store result score ${re.name} ${re.boolObject} " +
                        "as $tempFloatEntityUUID " +
                        "run function math:hpo/float/_notequal")
        }
        return re
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    @InsertCommand
    override fun cast(type: String): Var? {
        TODO()
    }

    override fun clone(): Any {
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
    override fun getTempVar(cache: HashMap<Var, String>): MCFloat {
        if(isConcrete){
            ssObj.setValue(value)
            ssObj.isConcrete = true
            ssObj.isDynamic = false
        }
        ssObj.exp.assign(exp)
        ssObj.int0.assign(int0)
        ssObj.int1.assign(int1)
        ssObj.sign.assign(sign)
        return ssObj
    }

    companion object{

        const val tempFloatEntityUUID = "53aa19cc-a067-402b-8ba1-9328cc5fb6c1"
        const val tempFloatEntityUUIDNBT = "[I;1403656652,41063,16427,35745,161803436799681]"

        fun floatToMCFloat(float: Float): Array<Int>{
            //获取指数部分
            val sign = if (float < 0) -1 else if(float == 0f) 0 else 1
            val absFloat = float.absoluteValue
            val exponent = floor(log10(absFloat.toDouble())).toInt()
            val factor = 10.0.pow((8 - exponent - 1).toDouble()).toInt()
            val n =  8 - exponent - 1
            val qwq = (absFloat * factor).toInt()
            return arrayOf(sign, qwq/10000, qwq%10000, n)
        }

        val ssObj : MCFloat

        init {
            ssObj = MCFloat()
            ssObj.sign = MCInt("float_sign").setObj(SbObject.Math_int) as MCInt
            ssObj.int0 = MCInt("float_int0").setObj(SbObject.Math_int) as MCInt
            ssObj.int1 = MCInt("float_int1").setObj(SbObject.Math_int) as MCInt
            ssObj.int1 = MCInt("float_exp").setObj(SbObject.Math_int) as MCInt
            ssObj.isTemp = true
        }
    }
}