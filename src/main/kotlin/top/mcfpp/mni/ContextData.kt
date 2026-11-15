package top.mcfpp.mni

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.util.LogProcessor

/** Context operand factories publish ordinary immutable values, never command text. */
object ContextData {
    @JvmStatic
    @NoExternalWrites
    @MNIFunction(normalParams = ["float", "float", "bool", "bool"], returnType = "Rotation")
    fun rotation(context: NativeCallContext) {
        val fields = linkedMapOf<String, CompilerValue>()
        for ((index, name) in listOf("yaw", "pitch", "relativeYaw", "relativePitch").withIndex()) {
            val value = context.argumentSnapshot(index)
            if (value == null) { LogProcessor.error("Rotation.$name requires a known value"); return }
            fields[name] = value
        }
        context.publishResult(StorageAccess.literal(context.declaredReturnType, CompilerValue.Record(fields)))
    }
}
