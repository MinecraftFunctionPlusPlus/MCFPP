package top.mcfpp.lang

/**
 * 代表了mcfpp中的一个数字类型。数字类型都是以记分板为基础的。
 * 泛型T代表了这个数字类型中包装的类型
 */
abstract class Number<T> : Var, OnScoreboard {
    var value: T? = null
    var `object`: SbObject

    /**
     * 创建一个匿名数字类型变量
     */
    constructor() : super() {
        `object` = SbObject.MCS_default
    }

    /**
     * 创建一个数字类型变量
     * @param i 标识符
     */
    constructor(i: String) {
        identifier = i
        `object` = SbObject.MCS_default
    }

    constructor(b: Number<T>) : super(b) {
        value = b.value
        `object` = b.`object`
    }

    @Override
    override fun setObj(sbObject: SbObject): Number<T> {
        `object` = sbObject
        return this
    }

    /**
     * 赋值
     * @param a 值来源
     */
    abstract fun assignCommand(a: Number<T>)

    /**
     * 加法
     * @param a 加数
     * @return 计算的结果
     */
    abstract fun plus(a: Number<T>): Number<T>

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    abstract fun minus(a: Number<T>): Number<T>

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    abstract fun multiple(a: Number<T>): Number<T>

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    abstract fun divide(a: Number<T>): Number<T>

    /**
     * 取余
     * @param a 除数
     * @return 计算的结果
     */
    abstract fun modular(a: Number<T>): Number<T>

    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract fun isGreater(a: Number<T>): MCBool


    /**
     * 这个数是否小于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract fun isLess(a: Number<T>): MCBool

    /**
     * 这个数是否小于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract fun isLessOrEqual(a: Number<T>): MCBool

    /**
     * 这个数是否大于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract fun isGreaterOrEqual(a: Number<T>): MCBool

    /**
     * 这个数是否等于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract fun isEqual(a: Number<T>): MCBool

    /**
     * 这个数是否不等于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract fun notEqual(a: Number<T>): MCBool
}