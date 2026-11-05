package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction

/** Implements conversion from an MCFPP string to a text component. */
object MCStringData {

    /** Creates a text component that displays the receiver value. */
    @MNIFunction(caller = "string", returnType = "text", `override` = true)
    @JvmStatic
    fun toText(context: NativeCallContext) {
        top.mcfpp.backend.NativeTextOperations.string(context)
    }
}
