package top.mcfpp.core.lang

import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.lib.SbObject
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.TempPool

/**
 * 代表了mcfpp中的一个数字类型。整数使用记分板；浮点数按目标版本使用记分板分量或NBT。
 *
 * @param T 这个数字类型中包装的类型
 */
abstract class MCNumber<T> : Var<MCNumber<T>> {

    var isDataOnly: Boolean = false

    var name: String

    var holder: ScoreHolder? = null

    var sbObject: SbObject

    /**
     * 创建一个int类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = TempPool.getVarIdentify()
    ) : super(identifier) {
        this.name = curr.prefix + identifier
        sbObject = SbObject.MCFPP_default
    }

    /**
     * 创建一个数字类型变量
     *
     * @param identifier 标识符。默认为随机的uuid
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.name = identifier
        sbObject = SbObject.MCFPP_default
    }

    /**
     * 复制一个数字类型变量
     */
    @Suppress("LeakingThis")
    constructor(b: MCNumber<T>) : super(b) {
        name = b.name
        sbObject = b.sbObject
        isDataOnly = b.isDataOnly
    }

    @Suppress("LeakingThis")
    constructor(b: EnumVar) : super(b){
        name = b.name
        sbObject = b.sbObject
        isDataOnly = b.isDataOnly
    }

    open fun setObj(sbObject: SbObject): MCNumber<T> {
        this.sbObject = sbObject
        return this
    }

    /**
     * 赋值
     * @param a 值来源
     */
    abstract fun assignCommand(a: MCNumber<*>) : MCNumber<T>
}
