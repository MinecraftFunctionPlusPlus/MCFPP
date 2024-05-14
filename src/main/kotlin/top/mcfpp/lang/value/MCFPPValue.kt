package top.mcfpp.lang.value

import top.mcfpp.lang.Var
import top.mcfpp.lang.type.MCFPPType

/**
 * MCFPP中的一个值，可以包含一个Java的值
 */
interface MCFPPValue<T> {
    var type: MCFPPType
    var value: T

    /**
     * 将这个被追踪的变量转换为未被跟踪的变量。这个方法同时会修改当前缓存中的变量，让缓存中的变量也变成未追踪状态，从而让下一次获取变量的时候获取到
     * 的是未被跟踪的变量
     */
    fun toDynamic(replace : Boolean): Var<*>
}