package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.MapOperations;
import top.mcfpp.mni.annotation.NoExternalWrites;
import top.mcfpp.mni.annotation.WritesReceiver;

/** One member signature for every map value and storage layout. */
public class NBTMapData {
    @WritesReceiver
    @MNIFunction(caller = "map", genericType = "E")
    public static void clear(NativeCallContext context) { MapOperations.INSTANCE.clear(context); }

    @NoExternalWrites
    @MNIFunction(normalParams = "string", caller = "map", returnType = "bool", genericType = "E")
    public static void containsKey(NativeCallContext context) {
        MapOperations.INSTANCE.containsKey(context);
    }

    @NoExternalWrites
    @MNIFunction(caller = "map", returnType = "bool", genericType = "E")
    public static void isEmpty(NativeCallContext context) {
        MapOperations.INSTANCE.isEmpty(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = "string", caller = "map", genericType = "E")
    public static void remove(NativeCallContext context) { MapOperations.INSTANCE.remove(context); }

    @WritesReceiver
    @MNIFunction(normalParams = "map<E>", caller = "map", genericType = "E")
    public static void merge(NativeCallContext context) { MapOperations.INSTANCE.merge(context); }

    @NoExternalWrites
    @MNIFunction(caller = "map", returnType = "int", genericType = "E")
    public static void size(NativeCallContext context) { MapOperations.INSTANCE.size(context); }
}
