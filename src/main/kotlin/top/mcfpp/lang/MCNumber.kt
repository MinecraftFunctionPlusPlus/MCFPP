package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.SbObject
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import java.util.*

/**
 * 代表了mcfpp中的一个数字类型。数字类型都是以记分板为基础的。
 *
 * @param T 这个数字类型中包装的类型
 */
abstract class MCNumber<T> : Var<MCNumber<T>>, OnScoreboard {

    var holder: ScoreHolder? = null

    var sbObject: SbObject

    /**
     * 创建一个数字类型变量
     *
     * @param identifier 标识符。默认为随机的uuid
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        sbObject = SbObject.MCFPP_default
    }

    /**
     * 复制一个数字类型变量
     */
    constructor(b: MCNumber<T>) : super(b) {
        sbObject = b.sbObject
    }

    constructor(b: EnumVar) : super(b){
        sbObject = b.sbObject
    }

    @Override
    override fun setObj(sbObject: SbObject): MCNumber<T> {
        this.sbObject = sbObject
        return this
    }

    /**
     * 赋值
     * @param a 值来源
     */
    abstract fun assignCommand(a: MCNumber<T>) : MCNumber<T>

    /**
     * 加法
     * @param a 加数
     * @return 计算的结果
     */
    abstract override fun plus(a: Var<*>): Var<*>

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    abstract override fun minus(a: Var<*>): Var<*>

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    abstract override fun multiple(a: Var<*>): Var<*>

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    abstract override fun divide(a: Var<*>): Var<*>

    /**
     * 取余
     * @param a 除数
     * @return 计算的结果
     */
    abstract override fun modular(a: Var<*>): Var<*>

    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract override fun isBigger(a: Var<*>): MCBool


    /**
     * 这个数是否小于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract override fun isSmaller(a: Var<*>): MCBool

    /**
     * 这个数是否小于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract override fun isSmallerOrEqual(a: Var<*>): MCBool

    /**
     * 这个数是否大于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract override fun isGreaterOrEqual(a: Var<*>): MCBool

    /**
     * 这个数是否等于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract override fun isEqual(a: Var<*>): MCBool

    /**
     * 这个数是否不等于a
     * @param a 右侧值
     * @return 计算结果
     */
    abstract override fun isNotEqual(a: Var<*>): MCBool

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
}
