package top.mcfpp.lang.value

import top.mcfpp.lang.type.MCFPPType

/**
 * 值，可以包含一个Java的值
 */
interface MCFPPValue<T> {
    var type: MCFPPType
    var javaValue: T
}