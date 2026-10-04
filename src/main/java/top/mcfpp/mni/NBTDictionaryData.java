package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.DictionaryOperations;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.NBTDictionary;
import top.mcfpp.mni.annotation.NoExternalWrites;
import top.mcfpp.mni.annotation.WritesReceiver;
import top.mcfpp.util.ValueWrapper;

/** One signature per member, with value/layout selection at the storage boundary. */
public class NBTDictionaryData {
    @WritesReceiver
    @MNIFunction(caller = "dict", genericType = "E")
    public static void clear(NBTDictionary caller) {
        DictionaryOperations.INSTANCE.clear(caller);
    }

    @NoExternalWrites
    @MNIFunction(normalParams = "string", caller = "dict", returnType = "bool", genericType = "E")
    public static void containsKey(MCString key, NBTDictionary caller, ValueWrapper<ScoreBool> result) {
        result.setValue(DictionaryOperations.INSTANCE.containsKey(caller, key));
    }

    @WritesReceiver
    @MNIFunction(normalParams = "dict<E>", caller = "dict", genericType = "E")
    public static void merge(NBTDictionary source, NBTDictionary caller) {
        DictionaryOperations.INSTANCE.merge(caller, source);
    }

    @WritesReceiver
    @MNIFunction(normalParams = "string", caller = "dict", genericType = "E")
    public static void remove(MCString key, NBTDictionary caller) {
        DictionaryOperations.INSTANCE.remove(caller, key);
    }
}
