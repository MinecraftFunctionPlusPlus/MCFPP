package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.ListOperations
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.mni.annotation.WritesReceiver

/** Implements mutable MCFPP lists with values known during compilation or stored at runtime. */
object NBTListData {
    /** Returns the number of elements or entries in the receiver. */
    @NoExternalWrites
    @MNIFunction(caller = "list", genericType = ["E"], returnType = "int")
    @JvmStatic
    fun size(context: NativeCallContext) { ListOperations.size(context); }

    /** Tests whether the receiver contains zero elements or entries. */
    @NoExternalWrites
    @MNIFunction(caller = "list", genericType = ["E"], returnType = "bool")
    @JvmStatic
    fun isEmpty(context: NativeCallContext) { ListOperations.isEmpty(context); }
    /** Appends an element to the receiver list. */
    @WritesReceiver
    @MNIFunction(normalParams = ["E"], caller = "list", genericType = ["E"])
    @JvmStatic
    fun add(context: NativeCallContext) { ListOperations.add(context, false); }

    /** Inserts an element at the start of the receiver list. */
    @WritesReceiver
    @MNIFunction(normalParams = ["E"], caller = "list", genericType = ["E"])
    @JvmStatic
    fun prepend(context: NativeCallContext) { ListOperations.add(context, true); }

    /** Appends the supplied list elements in their original order. */
    @WritesReceiver
    @MNIFunction(normalParams = ["list<E>"], caller = "list", genericType = ["E"])
    @JvmStatic
    fun addAll(context: NativeCallContext) { ListOperations.addAll(context, false); }

    /** Inserts the supplied list elements at the start in their original order. */
    @WritesReceiver
    @MNIFunction(normalParams = ["list<E>"], caller = "list", genericType = ["E"])
    @JvmStatic
    fun prependAll(context: NativeCallContext) { ListOperations.addAll(context, true); }

    /** Inserts an element at the supplied list index. */
    @WritesReceiver
    @MNIFunction(normalParams = ["int", "E"], caller = "list", genericType = ["E"])
    @JvmStatic
    fun insert(context: NativeCallContext) { ListOperations.insert(context); }

    /** Removes the element at the supplied list index. */
    @WritesReceiver
    @MNIFunction(normalParams = ["int"], caller = "list", genericType = ["E"])
    @JvmStatic
    fun removeAt(context: NativeCallContext) { ListOperations.removeAt(context); }

    /** Removes the first matching element from the receiver list. */
    @WritesReceiver
    @MNIFunction(normalParams = ["E"], caller = "list", genericType = ["E"])
    @JvmStatic
    fun remove(context: NativeCallContext) { ListOperations.remove(context); }

    /** Returns the first matching zero-based index, or -1 when the element is absent. */
    @NoExternalWrites
    @MNIFunction(normalParams = ["E"], caller = "list", genericType = ["E"], returnType = "int")
    @JvmStatic
    fun indexOf(context: NativeCallContext) { ListOperations.indexOf(context, false); }

    /** Returns the last matching zero-based index, or -1 when the element is absent. */
    @NoExternalWrites
    @MNIFunction(normalParams = ["E"], caller = "list", genericType = ["E"], returnType = "int")
    @JvmStatic
    fun lastIndexOf(context: NativeCallContext) { ListOperations.indexOf(context, true); }

    /** Tests whether the receiver contains the supplied element. */
    @NoExternalWrites
    @MNIFunction(normalParams = ["E"], caller = "list", genericType = ["E"], returnType = "bool")
    @JvmStatic
    fun contains(context: NativeCallContext) { ListOperations.contains(context); }

    /** Removes all elements or entries from the receiver. */
    @WritesReceiver
    @MNIFunction(caller = "list", genericType = ["E"])
    @JvmStatic
    fun clear(context: NativeCallContext) { ListOperations.clear(context); }
}
