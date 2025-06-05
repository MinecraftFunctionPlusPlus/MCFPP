package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.NBTListConcrete;
import top.mcfpp.core.lang.nbt.NBTMap;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

public class NBTMapData {

    @MNIFunction(caller = "map", genericType = "E")
    public static void clear(NBTMap caller){
        NBTListData.clear(caller.getKeyList());
        NBTDictionaryData.clear(caller.getKeyValueSet());
    }

    @MNIFunction(normalParams = {"string"}, caller = "map", returnType = "bool", genericType = "E")
    public static void containsKey(MCString key, NBTMap caller, ValueWrapper<ScoreBool> re){
        NBTDictionaryData.containsKey(key, caller.getKeyValueSet(), re);
    }

    @MNIFunction(caller = "map", returnType = "bool", genericType = "E")
    public static void isEmpty(NBTMap caller, ValueWrapper<ScoreBool> re){
        re.setValue(new ScoreBoolConcrete(((NBTListConcrete) (caller.getKeyList())).getValue().isEmpty(), "return"));
    }

    @MNIFunction(normalParams = "string", caller = "map", genericType = "E")
    public static void remove(MCString key, NBTMap caller) {
        NBTDictionaryData.remove(key, caller.getKeyValueSet());
        NBTListData.remove(key, caller.getKeyList());
    }

    @MNIFunction(normalParams = "map<E>", caller = "map", genericType = "E")
    public static void merge(NBTMap source, NBTMap caller){
        NBTListData.addAll(source.getKeyList(), caller.getKeyList());
        NBTDictionaryData.merge(source.getKeyValueSet(), caller.getKeyValueSet());
    }

    @MNIFunction(caller = "map", returnType = "int", genericType = "E")
    public static void size(NBTMap caller, ValueWrapper<MCInt> re){
        var r = re.getValue();
        Function.addCommand(
                new Command("execute store score").build(r.getIdentifier(),true).build(r.getSbObject().toString(), true)
                        .build("run data get", true).build(caller.getKeyValueSet().nbtPath.toCommandPart(), true)
        );
    }
}
