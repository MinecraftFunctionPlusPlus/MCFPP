package top.mcfpp.core.lang

import top.mcfpp.type.MCFPPType
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
    abstract fun assignCommand(a: MCNumber<*>) : MCNumber<T>

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
