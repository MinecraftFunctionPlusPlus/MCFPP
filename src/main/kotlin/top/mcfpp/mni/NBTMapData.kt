package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.MapOperations
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.mni.annotation.WritesReceiver

/** Implements MCFPP map lookup, size, removal, and merge operations. */
object NBTMapData {
    /** Removes all elements or entries from the receiver. */
    @WritesReceiver
    @MNIFunction(caller = "map", genericType = ["E"])
    @JvmStatic
    fun clear(context: NativeCallContext) { MapOperations.clear(context); }

    /** Tests whether the receiver contains the supplied key. */
    @NoExternalWrites
    @MNIFunction(normalParams = ["string"], caller = "map", returnType = "bool", genericType = ["E"])
    @JvmStatic
    fun containsKey(context: NativeCallContext) {
        MapOperations.containsKey(context)
    }

    /** Tests whether the receiver contains zero elements or entries. */
    @NoExternalWrites
    @MNIFunction(caller = "map", returnType = "bool", genericType = ["E"])
    @JvmStatic
    fun isEmpty(context: NativeCallContext) {
        MapOperations.isEmpty(context)
    }

    /** Removes the entry with the supplied key from the receiver. */
    @WritesReceiver
    @MNIFunction(normalParams = ["string"], caller = "map", genericType = ["E"])
    @JvmStatic
    fun remove(context: NativeCallContext) { MapOperations.remove(context); }

    /** Merges the supplied entries into the receiver. */
    @WritesReceiver
    @MNIFunction(normalParams = ["map<E>"], caller = "map", genericType = ["E"])
    @JvmStatic
    fun merge(context: NativeCallContext) { MapOperations.merge(context); }

    /** Returns the number of elements or entries in the receiver. */
    @NoExternalWrites
    @MNIFunction(caller = "map", returnType = "int", genericType = ["E"])
    @JvmStatic
    fun size(context: NativeCallContext) { MapOperations.size(context); }
}
