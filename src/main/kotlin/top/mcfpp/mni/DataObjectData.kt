package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeTextOperations

/** Implements text conversion for MCFPP data objects. */
object DataObjectData {

    /** Creates a text component that displays the receiver value. */
    @MNIFunction(caller = "DataObject", returnType = "text")
    @JvmStatic
    fun toText(context: NativeCallContext) {
        NativeTextOperations.template(context)
    }
}
