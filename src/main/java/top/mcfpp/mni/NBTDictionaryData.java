package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.DictionaryOperations;
import top.mcfpp.mni.annotation.NoExternalWrites;
import top.mcfpp.mni.annotation.WritesReceiver;

/** One signature per member, with value/layout selection at the storage boundary. */
public class NBTDictionaryData {
    @NoExternalWrites
    @MNIFunction(caller = "dict", genericType = "E", returnType = "int")
    public static void size(NativeCallContext context) { DictionaryOperations.INSTANCE.size(context); }

    @NoExternalWrites
    @MNIFunction(caller = "dict", genericType = "E", returnType = "bool")
    public static void isEmpty(NativeCallContext context) { DictionaryOperations.INSTANCE.isEmpty(context); }
    @WritesReceiver
    @MNIFunction(caller = "dict", genericType = "E")
    public static void clear(NativeCallContext context) {
        DictionaryOperations.INSTANCE.clear(context);
    }

    @NoExternalWrites
    @MNIFunction(normalParams = "string", caller = "dict", returnType = "bool", genericType = "E")
    public static void containsKey(NativeCallContext context) {
        DictionaryOperations.INSTANCE.containsKey(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = "dict<E>", caller = "dict", genericType = "E")
    public static void merge(NativeCallContext context) {
        DictionaryOperations.INSTANCE.merge(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = "string", caller = "dict", genericType = "E")
    public static void remove(NativeCallContext context) {
        DictionaryOperations.INSTANCE.remove(context);
    }
}
