package top.mcfpp.core.lang

import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor

@Suppress("UNCHECKED_CAST")
abstract class PrivateVar<T: PrivateVar<T>>: Var<T>() {
    final override fun doAssignedBy(b: Var<*>): T {
        LogProcessor.error("Cannot assign to $type.")
        return this as T
    }

    final override fun canAssignedBy(b: Var<*>) = false

    final override fun clone(): T = this as T

    final override fun getTempVar(): T = this as T

    final override fun storeToStack() {}

    final override fun getFromStack() {}

}