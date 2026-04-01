package top.mcfpp.core.lang.iterator

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.BaseBool

interface AbstractIterator {
    fun hasNext(): BaseBool

    fun moveNext()

    fun getCurr(): Var<*>
}