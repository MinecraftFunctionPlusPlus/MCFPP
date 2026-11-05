package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction

/** Implements text conversion for MCFPP any values. */
object MCAnyData {
    /** Creates a text component that displays the receiver value. */
    @MNIFunction(caller = "any", returnType = "text")
    @JvmStatic
    fun toText(context: NativeCallContext) {
        top.mcfpp.backend.NativeTextOperations.representation(context)
    }

}
