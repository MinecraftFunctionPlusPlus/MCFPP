package top.mcfpp.mni;

import kotlin.NotImplementedError;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

public class NBTMapData {

    @MNIFunction(caller = "dict")
    public static void clear(NBTMap caller){
        NBTListData.clear(caller.getKeyList());
        NBTListData.clear(caller.getValueList());
        NBTDictionaryData.clear(caller.getKeyValueSet());
    }

    @MNIFunction(normalParams = {"string key"}, caller = "dict", returnType = "bool")
    public static void containsKey(MCString key, NBTMapConcrete caller, ValueWrapper<ScoreBool> re){
        NBTListData.contains(key, caller.getKeyList(), re);
    }

    @MNIFunction(normalParams = {""}, caller = "dict")
    public static void containsValue(Var<?> element, NBTMapConcrete caller, ValueWrapper<ScoreBool> re){
        NBTListData.contains(element.toNBTVar(), caller.getValueList(), re);
    }

    @MNIFunction(caller = "dict", returnType = "bool")
    public static void isEmpty(NBTMapConcrete caller, ValueWrapper<ScoreBool> re){
        re.setValue(new ScoreBoolConcrete(((NBTListConcrete) (caller.getKeyList())).getValue().isEmpty(), "return"));
    }

    @MNIFunction(caller = "dict", returnType = "list")
    public static void getKeys(NBTMapConcrete caller, ValueWrapper<NBTList> re){
        re.assign(caller.getKeyList());
    }

    @MNIFunction(caller = "dict", returnType = "list")
    public static void getValues(NBTMapConcrete caller, ValueWrapper<NBTList> re){
        re.assign(caller.getValueList());
    }

    @MNIFunction(normalParams = {"string key"}, caller = "dict")
    public static void remove(MCString key, NBTMapConcrete caller) {
        //TODO
        throw new NotImplementedError();
    }

    @MNIFunction(normalParams = {"dict source"}, caller = "dict")
    public static void merge(NBTMap source, NBTMapConcrete caller){
        NBTListData.addAll(source.getKeyList(), caller.getKeyList());
        NBTListData.addAll(source.getValueList(), caller.getValueList());
        NBTDictionaryData.merge(source.getKeyValueSet(), caller.getKeyValueSet());
    }

    @MNIFunction(caller = "dict", returnType = "int")
    public static void size(NBTMapConcrete caller, ValueWrapper<MCInt> re){
        var r = re.getValue();
        Function.Companion.addCommand(
                new Command("execute store score").build(r.getIdentifier(),true).build(r.getSbObject().toString(), true)
                        .build("run data get", true).build(caller.getKeyValueSet().nbtPath.toCommandPart(), true)
        );
    }
}
