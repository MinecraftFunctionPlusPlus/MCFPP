package top.mcfpp.util

import top.mcfpp.core.lang.Var

/** Binary-compatible result carrier for Java MNI providers using the original ABI. */
class ValueWrapper<T : Var<*>>(var value: T) {
    /** Assign through the current destination so storage and value facts remain consistent. */
    @Suppress("UNCHECKED_CAST")
    fun assign(v: T) {
        value = value.assignedBy(v) as T
    }

    fun set(v: T) { value = v }

    fun get(): T = value
}
