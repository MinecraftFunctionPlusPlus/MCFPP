package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.Function
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.absoluteValue

class MCFloat : Number<Float> {

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
     * 创建一个固定的float
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(curr: FieldContainer, value: Float, identifier: String = UUID.randomUUID().toString()) : super(curr.prefix + identifier) {
        isConcrete = true
        this.value = value
        sign = MCInt(name).setObj(SbObject.MCS_float_sign) as MCInt
        int0 = MCInt(name).setObj(SbObject.MCS_float_int0) as MCInt
        int1 = MCInt(name).setObj(SbObject.MCS_float_int1) as MCInt
        exp = MCInt(name).setObj(SbObject.MCS_float_exp) as MCInt
    }

    /**
     * 创建一个固定的float。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Float, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        isConcrete = true
        this.value = value
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

    @get:Override
    override val type: String
        get() = "float"


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
    override fun assignCommand(a: Number<Float>) {
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
            //t = a
            if(a.isConcrete){
                isConcrete = false
                //对类中的成员的值进行修改
                //Function.addCommand("$cmd run scoreboard players set @s $`object` ${a.value}")
            }else{
                //对类中的成员的值进行修改
                //Function.addCommand("$cmd run scoreboard players operation @s $`object` = ${a.name} ${a.`object`}")
            }
        }else{
            //t = a
            if (a.isConcrete) {
                isConcrete = true
                value = a.value
            } else {
                isConcrete = false
                //变量进栈
                //Function.addCommand(
                //    "execute store result storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + identifier + " int 1" +
                //            " run " + Commands.SbPlayerOperation(this, "=", a as MCInt)
                //)
            }
        }
    }

    /**
     * 加法
     * @param a 加数
     * @return 计算的结果
     */
    override fun plus(a: Number<Float>): Number<Float> {
        TODO("Not yet implemented")
    }

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    override fun minus(a: Number<Float>): Number<Float> {
        TODO("Not yet implemented")
    }

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    override fun multiple(a: Number<Float>): Number<Float> {
        TODO("Not yet implemented")
    }

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    override fun divide(a: Number<Float>): Number<Float> {
        TODO("Not yet implemented")
    }

    /**
     * 取余
     * @param a 除数
     * @return 计算的结果
     */
    override fun modular(a: Number<Float>): Number<Float> {
        TODO("Not yet implemented")
    }

    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    override fun isGreater(a: Number<Float>): MCBool {
        TODO("Not yet implemented")
    }

    /**
     * 这个数是否小于a
     * @param a 右侧值
     * @return 计算结果
     */
    override fun isLess(a: Number<Float>): MCBool {
        TODO("Not yet implemented")
    }

    /**
     * 这个数是否小于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    override fun isLessOrEqual(a: Number<Float>): MCBool {
        TODO("Not yet implemented")
    }

    /**
     * 这个数是否大于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    override fun isGreaterOrEqual(a: Number<Float>): MCBool {
        TODO("Not yet implemented")
    }

    /**
     * 这个数是否等于a
     * @param a 右侧值
     * @return 计算结果
     */
    override fun isEqual(a: Number<Float>): MCBool {
        TODO("Not yet implemented")
    }

    /**
     * 这个数是否不等于a
     * @param a 右侧值
     * @return 计算结果
     */
    override fun notEqual(a: Number<Float>): MCBool {
        TODO("Not yet implemented")
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: String): Var? {
        TODO("Not yet implemented")
    }

    override fun clone(): Any {
        TODO("Not yet implemented")
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(cache: HashMap<Var, String>): Var {
        TODO("Not yet implemented")
    }

    companion object{
        fun floatToMCFloat(float: Float): Array<Int>{
            //获取指数部分
            val sign = if (float < 0) -1 else if(float == 0f) 0 else 1
            val absFloat = float.absoluteValue
            val exp = absFloat.toRawBits().shr(23) and 0xff
            val mantissa = absFloat.toRawBits() and 0x7fffff
            val xxxx = mantissa shr 13
            val yyyy = mantissa and 0x1fff
            val n = exp - 127 - 10

            return arrayOf(sign, xxxx, yyyy, n)
        }

    }
}