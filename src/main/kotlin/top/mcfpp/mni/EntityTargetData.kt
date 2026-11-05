package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeEntityTargetOperations
import top.mcfpp.mni.annotation.NoExternalWrites

/** Explicit factories for direct entity targets. */
@NoExternalWrites
object EntityTargetData {
    /** Creates an entity target from a player name. The target resolves when generated code uses it. */
    @MNIFunction(normalParams = ["string"], returnType = "entity")
    @JvmStatic
    fun entityByName(context: NativeCallContext) {
        NativeEntityTargetOperations.entityByName(context)
    }

    /** Creates an entity target from UUID text. The target resolves when generated code uses it. */
    @MNIFunction(normalParams = ["string"], returnType = "entity")
    @JvmStatic
    fun entityByUUID(context: NativeCallContext) {
        NativeEntityTargetOperations.entityByUUID(context)
    }
}
