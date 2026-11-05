package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeTimeOperations
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites

/** Creates Time values by converting integer amounts to game ticks. */
@NoExternalWrites
object TimeObjectData {

    /** Creates a Time value by multiplying the supplied integer by 1 game ticks. */
    @MNIFunction(normalParams = ["int"], returnType = "Time")
    @JvmStatic
    fun tick(context: NativeCallContext) {
        NativeTimeOperations.factory(context, 1)
    }

    /** Creates a Time value by multiplying the supplied integer by 20 game ticks. */
    @MNIFunction(normalParams = ["int"], returnType = "Time")
    @JvmStatic
    fun second(context: NativeCallContext) {
        NativeTimeOperations.factory(context, 20)
    }

    /** Creates a Time value by multiplying the supplied integer by 1200 game ticks. */
    @MNIFunction(normalParams = ["int"], returnType = "Time")
    @JvmStatic
    fun min(context: NativeCallContext) {
        NativeTimeOperations.factory(context, 1200)
    }

    /** Creates a Time value by multiplying the supplied integer by 72000 game ticks. */
    @MNIFunction(normalParams = ["int"], returnType = "Time")
    @JvmStatic
    fun hour(context: NativeCallContext) {
        NativeTimeOperations.factory(context, 72000)
    }

    /** Creates a Time value by multiplying the supplied integer by 144000 game ticks. */
    @MNIFunction(normalParams = ["int"], returnType = "Time")
    @JvmStatic
    fun day(context: NativeCallContext) {
        NativeTimeOperations.factory(context, 144000)
    }

    /** Creates a Time value by multiplying the supplied integer by 24000 game ticks. */
    @MNIFunction(normalParams = ["int"], returnType = "Time")
    @JvmStatic
    fun gameDay(context: NativeCallContext) {
        NativeTimeOperations.factory(context, 24000)
    }

}
