package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.Function
import java.util.*
import kotlin.collections.HashMap

class MCFloat : Number<Float> {
    /**
     * 创建一个匿名的动态int
     */
    constructor() : this(UUID.randomUUID().toString()){
        isTemp = true
    }

    /**
     * 创建一个固定的匿名int
     * @param value 值
     */
    constructor(value: Float) : this(UUID.randomUUID().toString()) {
        isConcrete = true
        this.value = value
    }

    /**
     * 创建一个固定的int
     * @param id 标识符
     * @param value 值
     */
    constructor(id: String, value: Float) : super(
        Function.currFunction.namespaceID + "_" + id
    ) {
        identifier = id
        isConcrete = true
        this.value = value
    }

    /**
     * 创建一个int
     * @param id 值
     * @param curr 这边变量被储存在的缓存。用于命名。
     */
    constructor(id: String, curr: FieldContainer) : super(curr.prefix + id) {
        identifier = id
    }

    /**
     * 创建一个int值。它的key和identifier相同。
     * @param id identifier
     */
    constructor(id: String) : super(id) {
        identifier = id
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: MCFloat) : super(b)

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
}