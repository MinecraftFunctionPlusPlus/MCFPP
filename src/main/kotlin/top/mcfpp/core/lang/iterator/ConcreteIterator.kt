package top.mcfpp.core.lang.iterator

import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.RangeVarConcrete
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

        @JvmStatic
        @JvmOverloads
        fun fromIntRange(identifier: String = TempPool.getVarIdentify(), range: RangeVarConcrete): ConcreteIterator<MCIntConcrete> {
            val list = (range.value.first!!.toInt()..range.value.second!!.toInt()).map {
                MCIntConcrete(it)
            }
            return ConcreteIterator(identifier, list.iterator())
        }
    }
}