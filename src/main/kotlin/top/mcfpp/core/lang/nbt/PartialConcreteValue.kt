package top.mcfpp.core.lang.nbt

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.nbt.tags.Tag

interface PartialConcreteValue<T: Tag<*>, Value>: MCFPPValue<Value> {

    fun isAllConcrete(): Boolean

    fun getConcretePart(): T

    fun getNotConcretePart(): Value

}