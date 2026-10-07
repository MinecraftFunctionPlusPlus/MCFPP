package top.mcfpp.core.lang.iterator

import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.util.TempPool

class ConcreteIterator<T: Var<*>>(identifier: String, val iterator: Iterator<T>): AbstractIterator, Iterable<T> {

    private var curr: T? = null

    override fun hasNext(): BaseBool {
        return ScoreBoolConcrete(iterator.hasNext())
    }

    override fun moveNext() {
        curr = iterator.next()
    }

    override fun getCurr(): Var<*> {
        return curr!!
    }

    override fun iterator(): Iterator<T> {
        return iterator
    }

    companion object {

        fun fromIntRange(first: Int, last: Int, identifier: String = TempPool.getVarIdentify()): ConcreteIterator<MCIntConcrete> {
            val values = (first..last).asSequence().map {
                MCIntConcrete(it)
            }
            return ConcreteIterator(identifier, values.iterator())
        }
    }
}
