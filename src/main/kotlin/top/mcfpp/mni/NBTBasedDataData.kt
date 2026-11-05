package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction

/** Implements text conversion for NBT values. */
object NBTBasedDataData {

    /** Creates a text component that displays the receiver value. */
    @MNIFunction(caller = "nbt", returnType = "text")
    @JvmStatic
    fun toText(context: NativeCallContext) {
        top.mcfpp.backend.NativeTextOperations.nbt(context)
    }

}
