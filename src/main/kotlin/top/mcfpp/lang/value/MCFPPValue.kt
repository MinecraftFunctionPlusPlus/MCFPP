package top.mcfpp.lang.value

import top.mcfpp.lang.type.MCFPPType

/**
 * MCFPP中的一个值，可以包含一个Java的值
 */
interface MCFPPValue<T> {
    var type: MCFPPType
    var value: T
}