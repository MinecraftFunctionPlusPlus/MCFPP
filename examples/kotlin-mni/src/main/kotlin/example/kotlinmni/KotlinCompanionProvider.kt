package example.kotlinmni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.MCInt
import top.mcfpp.mni.annotation.NoExternalWrites

@NoExternalWrites
class KotlinCompanionProvider {
    companion object {
        @JvmStatic
        @MNIFunction(identifier = "companionCallerProbe", caller = "int")
        fun callerProbe(caller: MCInt) {
            check(caller.value >= 0)
        }
    }
}
