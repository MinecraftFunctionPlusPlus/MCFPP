package top.mcfpp.core.lang

import top.mcfpp.util.LogProcessor

@Suppress("UNCHECKED_CAST")
abstract class PrivateVar<T: PrivateVar<T>>: Var<T>() {
    final override fun doAssignedBy(b: Var<*>): T {
        LogProcessor.error("Cannot assign to $type.")
        return this as T
    }

    final override fun clone(): T = this as T

    final override fun getTempVar(): T = this as T

    final override fun storeToStack() {}

    final override fun getFromStack() {}

}