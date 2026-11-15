package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.StandardStringOperations

/** Global counterpart of the native string member. */
@top.mcfpp.mni.annotation.NoExternalWrites
object StandardStringData {
    @JvmStatic @MNIFunction(normalParams = ["string", "string"], returnType = "string")
    fun contact(context: NativeCallContext) = context.withArguments { args ->
        val nested = NativeCallContext(context.function, args[0], listOf(args[1]), context.declaredReturnType)
        StandardStringOperations.contact(nested)
        nested.resultAdapter()?.let(context::publishResult)
    }
}
