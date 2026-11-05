package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements enabling trigger objectives for players. */
object TriggerData {
    /** Enables the trigger objective for the supplied players. */
    @MNIFunction(identifier = "enable", readOnlyParams = ["entity"], caller = "mcfpp.minecraft.other:Trigger")
    @JvmStatic
    fun enable(context: NativeCallContext) {
        NativeMinecraftCommandOperations.triggerEnable(context)
    }
}
