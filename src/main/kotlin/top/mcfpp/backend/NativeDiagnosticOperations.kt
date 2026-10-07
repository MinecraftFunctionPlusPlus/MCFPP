package top.mcfpp.backend

import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.util.LogProcessor

/** Diagnostics report compile-time representations rather than reading runtime message contents. */
object NativeDiagnosticOperations {
    fun debug(context: NativeCallContext) = context.withArguments {
        // Set a breakpoint here to pause compilation.
    }

    fun info(context: NativeCallContext) = report(context) { LogProcessor.info(it) }
    fun warn(context: NativeCallContext) = report(context) { LogProcessor.warn(it) }
    fun error(context: NativeCallContext) = report(context) { LogProcessor.error(it) }

    private fun report(context: NativeCallContext, action: (String) -> Unit) = context.withArguments { arguments ->
        val message = arguments[0] as MCString
        action(if (message is MCStringConcrete) message.value.value else message.toString())
    }
}
