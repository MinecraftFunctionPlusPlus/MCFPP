package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.DictionaryOperations
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.mni.annotation.WritesReceiver

/** Implements MCFPP dictionary lookup, size, removal, and merge operations. */
object NBTDictionaryData {
    /** Returns the number of elements or entries in the receiver. */
    @NoExternalWrites
    @MNIFunction(caller = "dict", genericType = ["E"], returnType = "int")
    @JvmStatic
    fun size(context: NativeCallContext) { DictionaryOperations.size(context); }

    /** Tests whether the receiver contains zero elements or entries. */
    @NoExternalWrites
    @MNIFunction(caller = "dict", genericType = ["E"], returnType = "bool")
    @JvmStatic
    fun isEmpty(context: NativeCallContext) { DictionaryOperations.isEmpty(context); }
    /** Removes all elements or entries from the receiver. */
    @WritesReceiver
    @MNIFunction(caller = "dict", genericType = ["E"])
    @JvmStatic
    fun clear(context: NativeCallContext) {
        DictionaryOperations.clear(context)
    }

    /** Tests whether the receiver contains the supplied key. */
    @NoExternalWrites
    @MNIFunction(normalParams = ["string"], caller = "dict", returnType = "bool", genericType = ["E"])
    @JvmStatic
    fun containsKey(context: NativeCallContext) {
        DictionaryOperations.containsKey(context)
    }

    /** Merges the supplied entries into the receiver. */
    @WritesReceiver
    @MNIFunction(normalParams = ["dict<E>"], caller = "dict", genericType = ["E"])
    @JvmStatic
    fun merge(context: NativeCallContext) {
        DictionaryOperations.merge(context)
    }

    /** Removes the entry with the supplied key from the receiver. */
    @WritesReceiver
    @MNIFunction(normalParams = ["string"], caller = "dict", genericType = ["E"])
    @JvmStatic
    fun remove(context: NativeCallContext) {
        DictionaryOperations.remove(context)
    }
}
