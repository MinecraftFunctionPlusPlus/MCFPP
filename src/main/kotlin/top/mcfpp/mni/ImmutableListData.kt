package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.ListOperations
import top.mcfpp.mni.annotation.NoExternalWrites

/** Read-only members share the same list query backend in every value state. */
object ImmutableListData {
    /** Returns the first matching zero-based index, or -1 when the element is absent. */
    @NoExternalWrites
    @MNIFunction(normalParams = ["E"], caller = "ImmutableList", genericType = ["E"], returnType = "int")
    @JvmStatic
    fun indexOf(context: NativeCallContext) {
        ListOperations.indexOf(context, false)
    }

    /** Returns the last matching zero-based index, or -1 when the element is absent. */
    @NoExternalWrites
    @MNIFunction(normalParams = ["E"], caller = "ImmutableList", genericType = ["E"], returnType = "int")
    @JvmStatic
    fun lastIndexOf(context: NativeCallContext) {
        ListOperations.indexOf(context, true)
    }

    /** Tests whether the receiver contains the supplied element. */
    @NoExternalWrites
    @MNIFunction(normalParams = ["E"], caller = "ImmutableList", genericType = ["E"], returnType = "bool")
    @JvmStatic
    fun contains(context: NativeCallContext) {
        ListOperations.contains(context)
    }
}
