package top.mcfpp.mni;

import net.querz.nbt.tag.StringTag;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.core.lang.nbt.*;
import top.mcfpp.util.ValueWrapper;

public class NBTMapConcreteData {

    @MNIFunction(caller = "map", genericType = "E")
    public static void clear(NBTMapConcrete caller){
        NBTDictionaryConcreteData.clear((NBTDictionaryConcrete) caller.getKeyValueSet());
        NBTListConcreteData.clear((NBTListConcrete) caller.getKeyList());
        caller.getValue().clear();
    }

    @MNIFunction(normalParams = {"string key"}, caller = "map", returnType = "bool", genericType = "E")
    public static void containsKey(MCString key, NBTMapConcrete caller, ValueWrapper<BaseBool> re){
        NBTListConcreteData.contains(key, (NBTListConcrete) caller.getKeyList(), re);
    }

    @MNIFunction(caller = "map", returnType = "bool", genericType = "E")
    public static void isEmpty(NBTMapConcrete caller, ValueWrapper<ScoreBool> re){
        re.setValue(new ScoreBoolConcrete(((NBTListConcrete) (caller.getKeyList())).getValue().isEmpty(), "return"));
    }

    @MNIFunction(normalParams = {"string key"}, caller = "map", genericType = "E")
    public static void remove(MCString key, NBTMapConcrete caller){
        if(key instanceof MCStringConcrete keyC) {
            StringTag keyTag = keyC.getValue();
            String keyStr = keyTag.getValue();
            int index = caller.indexOf(keyStr);
            if(index == -1) return;
            ((NBTListConcrete)(caller.getKeyList())).getValue().remove(index);
            caller.getValue().remove(keyStr);
        }else {
            caller.toDynamic(true);
            NBTMapData.remove(key, caller);
        }
    }

    @MNIFunction(normalParams = {"map<E> source"}, caller = "map", genericType = "E")
    public static void merge(NBTMap source, NBTMapConcrete caller){
        if(source instanceof NBTMapConcrete sourceC){
            NBTListConcreteData.addAll(sourceC.getKeyList(),(NBTListConcrete)caller.getKeyList());
            NBTDictionaryConcreteData.merge(sourceC.getKeyValueSet(), (NBTDictionaryConcrete) caller.getKeyValueSet());
        }else {
            caller.toDynamic(true);
            NBTMapData.merge(source, caller);
        }
    }

    @MNIFunction(caller = "map", returnType = "int", genericType = "E")
    public static void size(NBTMapConcrete caller, ValueWrapper<MCInt> re){
        re.setValue(new MCIntConcrete(((NBTListConcrete)(caller.getKeyList())).getValue().size(), "return"));
    }
}
