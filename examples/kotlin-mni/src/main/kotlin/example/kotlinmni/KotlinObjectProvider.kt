package example.kotlinmni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.util.ValueWrapper

@NoExternalWrites
object KotlinObjectProvider {
    @JvmStatic
    @MNIFunction(identifier = "objectSum", readOnlyParams = ["int"], normalParams = ["int"], returnType = "int")
    fun sum(constant: MCInt, value: Var<*>, result: ValueWrapper<MCInt>) {
        result.assign(MCInt(constant.value + (value as MCInt).value))
    }

    @JvmStatic
    @MNIFunction(identifier = "objectPing")
    fun ping() = Unit

    /** Context ABI alternative: metadata still describes the MCFPP argument list. */
    @JvmStatic
    @MNIFunction(identifier = "objectContextProbe", normalParams = ["int"])
    fun contextProbe(context: NativeCallContext) {
        check(context.arguments.size == 1) { "Expected one MCFPP argument" }
    }
}
