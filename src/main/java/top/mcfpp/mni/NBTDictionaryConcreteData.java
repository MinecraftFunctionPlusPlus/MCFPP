package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.MCStringConcrete;
import top.mcfpp.core.lang.nbt.NBTDictionary;
import top.mcfpp.core.lang.nbt.NBTDictionaryConcrete;
import top.mcfpp.util.ValueWrapper;

public class NBTDictionaryConcreteData {

    @MNIFunction(caller = "dict")
    public static void clear(NBTDictionaryConcrete caller){
        caller.getValue().clear();
    }

    @MNIFunction(normalParams = "string", caller = "dict", returnType = "bool", genericType = "E")
    public static void containsKey(MCString key, NBTDictionaryConcrete caller, ValueWrapper<ScoreBool> re){
        if(key instanceof MCStringConcrete keyC){
            String value = keyC.getValue().getValue();
            var nbt = caller.getValue();
            re.setValue(new ScoreBoolConcrete(nbt.containsKey(value), "return"));
        }else {
            caller.toDynamic(false);
            NBTDictionaryData.containsKey(key, caller, re);
        }
    }

    @MNIFunction(normalParams = "dict<E>", caller = "dict", genericType = "E")
    public static void merge(NBTDictionary source, NBTDictionaryConcrete caller){
        if(source instanceof NBTDictionaryConcrete dictC){
            var sourceNBT = dictC.getValue();
            var callerNBT = caller.getValue();
            for(String key : sourceNBT.keySet()){
                callerNBT.put(key, sourceNBT.get(key));
            }
        }else {
            caller.toDynamic(true);
            NBTDictionaryData.merge(source, caller);
        }
    }

    @MNIFunction(normalParams = "string", caller = "dict", genericType = "E")
    public static void remove(MCString key, NBTDictionaryConcrete caller){
        if(key instanceof MCStringConcrete keyC){
            String value = keyC.getValue().getValue();
            var nbt = caller.getValue();
            nbt.remove(value);
        }else {
            caller.toDynamic(false);
            NBTDictionaryData.remove(key, caller);
        }
    }

}
