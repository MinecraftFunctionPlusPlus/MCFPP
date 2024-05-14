package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.function.Function
import top.mcfpp.model.Member
import java.util.UUID

/**
 * 代表了mcfpp中的一个数字类型。数字类型都是以记分板为基础的。
 *
 * @param T 这个数字类型中包装的类型
 */
abstract class MCNumber<T> : Var<T>, OnScoreboard {

    var `object`: SbObject

    /**
     * 创建一个数字类型变量
     *
     * @param identifier 标识符。默认为随机的uuid
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        `object` = SbObject.MCFPP_default
    }

    /**
     * 复制一个数字类型变量
     */
    constructor(b: MCNumber<T>) : super(b as Var<*>) {
        `object` = b.`object`
    }

    @Override
    override fun setObj(sbObject: SbObject): MCNumber<T> {
        `object` = sbObject
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
     * @param params 成员方法的参数
     * @return 返回一个值对。第一个值是成员变量或null（如果成员方法不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }
}
