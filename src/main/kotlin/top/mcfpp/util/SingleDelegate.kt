package top.mcfpp.util

open class Delegate0<V> {

    protected val handlers = mutableListOf<() -> V>()

    open fun addHandler(handler: () -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: () -> V) {
        handlers.remove(handler)
    }

    open operator fun plusAssign(handler: () -> V) {
        handlers.add(handler)
    }

    open operator fun minusAssign(handler: () -> V) {
        handlers.remove(handler)
    }

    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(): V {
        return handlers.fold(null as V?) { _, handler -> handler() }!!
    }

}

open class Delegate1<T,V> {
    protected val handlers = mutableListOf<(T) -> V>()

    open fun addHandler(handler: (T) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T) -> V) {
        handlers.remove(handler)
    }

    open operator fun plus(handler: (T) -> V): Delegate1<T, V> {
        return Delegate1<T, V>().apply { handlers.addAll(this.handlers);handlers.add(handler) }
    }

    open operator fun minus(handler: (T) -> V): Delegate1<T, V> {
        return Delegate1<T, V>().apply { handlers.addAll(this.handlers);handlers.remove(handler) }
    }

    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(value: T): V{
        return handlers.fold(null as V?) { _, handler -> handler(value) }!!
    }

}

open class Delegate2<T1, T2, V> {
    protected val handlers = mutableListOf<(T1, T2) -> V>()

    open fun addHandler(handler: (T1, T2) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T1, T2) -> V) {
        handlers.remove(handler)
    }

    open operator fun plus(handler: (T1, T2) -> V) {
        handlers.add(handler)
    }

    open operator fun minusAssign(handler: (T1, T2) -> V) {
        handlers.remove(handler)
    }

    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(value1: T1, value2: T2): V{
        return handlers.fold(null as V?) { _, handler -> handler(value1, value2) }!!
    }
}

open class Delegate3<T1, T2, T3, V> {
    protected val handlers = mutableListOf<(T1, T2, T3) -> V>()

    open fun addHandler(handler: (T1, T2, T3) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T1, T2, T3) -> V) {
        handlers.remove(handler)
    }

    open operator fun plusAssign(handler: (T1, T2, T3) -> V) {
        handlers.add(handler)
    }

    open operator fun minusAssign(handler: (T1, T2, T3) -> V) {
        handlers.remove(handler)
    }

    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(value1: T1, value2: T2, value3: T3): V{
        return handlers.fold(null as V?) { _, handler -> handler(value1, value2, value3) }!!
    }
}

open class Delegate4<T1, T2, T3, T4, V> {
    protected val handlers = mutableListOf<(T1, T2, T3, T4) -> V>()

    open fun addHandler(handler: (T1, T2, T3, T4) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T1, T2, T3, T4) -> V) {
        handlers.remove(handler)
    }

    open operator fun plusAssign(handler: (T1, T2, T3, T4) -> V) {
        handlers.add(handler)
    }

    open operator fun minusAssign(handler: (T1, T2, T3, T4) -> V) {
        handlers.remove(handler)
    }

    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(value1: T1, value2: T2, value3: T3, value4: T4): V{
        return handlers.fold(null as V?) { _, handler -> handler(value1, value2, value3, value4) }!!
    }
}

open class Delegate5<T1, T2, T3, T4, T5, V> {
    protected val handlers = mutableListOf<(T1, T2, T3, T4, T5) -> V>()

    open fun addHandler(handler: (T1, T2, T3, T4, T5) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T1, T2, T3, T4, T5) -> V) {
        handlers.remove(handler)
    }

    open operator fun plusAssign(handler: (T1, T2, T3, T4, T5) -> V) {
        handlers.add(handler)
    }

    open operator fun minusAssign(handler: (T1, T2, T3, T4, T5) -> V) {
        handlers.remove(handler)
    }

    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(value1: T1, value2: T2, value3: T3, value4: T4, value5: T5): V{
        return handlers.fold(null as V?) { _, handler -> handler(value1, value2, value3, value4, value5) }!!
    }
}

open class Delegate6<T1, T2, T3, T4, T5, T6, V> {
    protected val handlers = mutableListOf<(T1, T2, T3, T4, T5, T6) -> V>()

    open fun addHandler(handler: (T1, T2, T3, T4, T5, T6) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T1, T2, T3, T4, T5, T6) -> V) {
        handlers.remove(handler)
    }

    open operator fun plusAssign(handler: (T1, T2, T3, T4, T5, T6) -> V) {
        handlers.add(handler)
    }

    open operator fun minusAssign(handler: (T1, T2, T3, T4, T5, T6) -> V) {
        handlers.remove(handler)
    }

    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(value1: T1, value2: T2, value3: T3, value4: T4, value5: T5, value6: T6): V{
        return handlers.fold(null as V?) { _, handler -> handler(value1, value2, value3, value4, value5, value6) }!!
    }
}

open class Delegate7<T1, T2, T3, T4, T5, T6, T7, V> {
    protected val handlers = mutableListOf<(T1, T2, T3, T4, T5, T6, T7) -> V>()

    open fun addHandler(handler: (T1, T2, T3, T4, T5, T6, T7) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T1, T2, T3, T4, T5, T6, T7) -> V) {
        handlers.remove(handler)
    }

    open operator fun plusAssign(handler: (T1, T2, T3, T4, T5, T6, T7) -> V) {
        handlers.add(handler)
    }

    open operator fun minusAssign(handler: (T1, T2, T3, T4, T5, T6, T7) -> V) {
        handlers.remove(handler)
    }

    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(value1: T1, value2: T2, value3: T3, value4: T4, value5: T5, value6: T6, value7: T7): V{
        return handlers.fold(null as V?) { _, handler -> handler(value1, value2, value3, value4, value5, value6, value7) }!!
    }
}

open class Delegate8<T1, T2, T3, T4, T5, T6, T7, T8, V> {
    protected val handlers = mutableListOf<(T1, T2, T3, T4, T5, T6, T7, T8) -> V>()

    open fun addHandler(handler: (T1, T2, T3, T4, T5, T6, T7, T8) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T1, T2, T3, T4, T5, T6, T7, T8) -> V) {
        handlers.remove(handler)
    }

    open operator fun plusAssign(handler: (T1, T2, T3, T4, T5, T6, T7, T8) -> V) {
        handlers.add(handler)
    }

    open operator fun minusAssign(handler: (T1, T2, T3, T4, T5, T6, T7, T8) -> V) {
        handlers.remove(handler)
    }

    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(value1: T1, value2: T2, value3: T3, value4: T4, value5: T5, value6: T6, value7: T7, value8: T8): V{
        return handlers.fold(null as V?) { _, handler -> handler(value1, value2, value3, value4, value5, value6, value7, value8) }!!
    }
}

open class Delegate9<T1, T2, T3, T4, T5, T6, T7, T8, T9, V> {
    protected val handlers = mutableListOf<(T1, T2, T3, T4, T5, T6, T7, T8, T9) -> V>()

    open fun addHandler(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> V) {
        handlers.remove(handler)
    }

    open operator fun plusAssign(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> V) {
        handlers.add(handler)
    }

    open operator fun minusAssign(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> V) {
        handlers.remove(handler)
    }
    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(value1: T1, value2: T2, value3: T3, value4: T4, value5: T5, value6: T6, value7: T7, value8: T8, value9: T9): V{
        return handlers.fold(null as V?) { _, handler -> handler(value1, value2, value3, value4, value5, value6, value7, value8, value9) }!!
    }
}

open class Delegate10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, V> {
    protected val handlers = mutableListOf<(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> V>()

    open fun addHandler(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> V) {
        handlers.remove(handler)
    }

    open operator fun plusAssign(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> V) {
        handlers.add(handler)
    }

    open operator fun minusAssign(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> V) {
        handlers.remove(handler)
    }
    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(value1: T1, value2: T2, value3: T3, value4: T4, value5: T5, value6: T6, value7: T7, value8: T8, value9: T9, value10: T10): V{
        return handlers.fold(null as V?) { _, handler -> handler(value1, value2, value3, value4, value5, value6, value7, value8, value9, value10) }!!
    }
}

open class Delegate11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, V> {
    protected val handlers = mutableListOf<(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) -> V>()

    open fun addHandler(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) -> V) {
        handlers.remove(handler)
    }

    open operator fun plusAssign(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) -> V) {
        handlers.add(handler)
    }

    open operator fun minusAssign(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) -> V) {
        handlers.remove(handler)
    }
    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun invoke(value1: T1, value2: T2, value3: T3, value4: T4, value5: T5, value6: T6, value7: T7, value8: T8, value9: T9, value10: T10, value11: T11): V{
        return handlers.fold(null as V?) { _, handler -> handler(value1, value2, value3, value4, value5, value6, value7, value8, value9, value10, value11) }!!
    }
}

open class Delegate12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, V> {
    protected val handlers = mutableListOf<(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) -> V>()

    open fun addHandler(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) -> V) {
        handlers.add(handler)
    }

    open fun removeHandler(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) -> V) {
        handlers.remove(handler)
    }

    open operator fun plusAssign(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) -> V) {
        handlers.add(handler)
    }
    fun removeLast() = handlers.removeLast()

    fun clear() = handlers.clear()

    open operator fun minusAssign(handler: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) -> V) {
        handlers.remove(handler)
    }

    open operator fun invoke(value1: T1, value2: T2, value3: T3, value4: T4, value5: T5, value6: T6, value7: T7, value8: T8, value9: T9, value10: T10, value11: T11, value12: T12): V{
        return handlers.fold(null as V?) { _, handler -> handler(value1, value2, value3, value4, value5, value6, value7, value8, value9, value10, value11, value12) }!!
    }
}