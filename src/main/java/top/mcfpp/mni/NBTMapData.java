package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.MapOperations;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.NBTMap;
import top.mcfpp.mni.annotation.NoExternalWrites;
import top.mcfpp.mni.annotation.WritesReceiver;
import top.mcfpp.util.ValueWrapper;

/** One member signature for every map value and storage layout. */
public class NBTMapData {
    @WritesReceiver
    @MNIFunction(caller = "map", genericType = "E")
    public static void clear(NBTMap caller) { MapOperations.INSTANCE.clear(caller); }

    @NoExternalWrites
    @MNIFunction(normalParams = "string", caller = "map", returnType = "bool", genericType = "E")
    public static void containsKey(MCString key, NBTMap caller, ValueWrapper<ScoreBool> result) {
        result.setValue(MapOperations.INSTANCE.containsKey(caller, key));
    }

    @NoExternalWrites
    @MNIFunction(caller = "map", returnType = "bool", genericType = "E")
    public static void isEmpty(NBTMap caller, ValueWrapper<ScoreBool> result) {
        result.setValue(MapOperations.INSTANCE.isEmpty(caller));
    }

    @WritesReceiver
    @MNIFunction(normalParams = "string", caller = "map", genericType = "E")
    public static void remove(MCString key, NBTMap caller) { MapOperations.INSTANCE.remove(caller, key); }

    @WritesReceiver
    @MNIFunction(normalParams = "map<E>", caller = "map", genericType = "E")
    public static void merge(NBTMap source, NBTMap caller) { MapOperations.INSTANCE.merge(caller, source); }

    @NoExternalWrites
    @MNIFunction(caller = "map", returnType = "int", genericType = "E")
    public static void size(NBTMap caller, ValueWrapper<MCInt> result) { result.setValue(MapOperations.INSTANCE.size(caller)); }
}
