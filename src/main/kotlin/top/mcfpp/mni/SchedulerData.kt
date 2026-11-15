package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.SchedulerRuntime
import top.mcfpp.core.lang.MCInt

object SchedulerData {
    @JvmStatic @MNIFunction(normalParams = ["(int) -> bool"])
    fun tick(context: NativeCallContext) = context.withArguments { SchedulerRuntime.register(MCInt(1), it.single(), repeating = true) }
    @JvmStatic @MNIFunction(normalParams = ["int", "(int) -> bool"])
    fun loop(context: NativeCallContext) = context.withArguments { SchedulerRuntime.register(it[0], it[1], repeating = true) }
    @JvmStatic @MNIFunction(normalParams = ["int", "() -> void"])
    fun schedule(context: NativeCallContext) = context.withArguments { SchedulerRuntime.register(it[0], it[1], repeating = false) }
}
