package top.mcfpp.mni;

import net.querz.nbt.tag.CompoundTag;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.*;
import top.mcfpp.util.ValueWrapper;

public class NBTDictionaryConcreteData {

    @MNIRegister(caller = "dict")
    public static void clear(NBTDictionaryConcrete caller){
        caller.getValue().clear();
    }

    @MNIRegister(normalParams = {"string key"}, caller = "dict", returnType = "bool")
    public static void containsKey(MCString key, NBTDictionaryConcrete caller, ValueWrapper<MCBool> re){
        if(key instanceof MCStringConcrete keyC){
            String value = keyC.getValue().getValue();
            CompoundTag nbt = caller.getValue();
            re.setValue(new MCBoolConcrete(value != null && nbt.containsKey(value), "return"));
        }else {
            caller.toDynamic(false);
            NBTDictionaryData.containsKey(key, caller, re);
        }
    }

    @MNIRegister(normalParams = {"dict source"}, caller = "dict")
    public static void merge(NBTDictionary source, NBTDictionaryConcrete caller){
        if(source instanceof NBTDictionaryConcrete dictC){
            CompoundTag sourceNBT = dictC.getValue();
            CompoundTag callerNBT = caller.getValue();
            for(String key : sourceNBT.keySet()){
                callerNBT.put(key, sourceNBT.get(key));
            }
        }else {
            caller.toDynamic(true);
            NBTDictionaryData.merge(source, caller);
        }
    }

    @MNIRegister(normalParams = {"string key"}, caller = "dict")
    public static void remove(MCString key, NBTDictionaryConcrete caller){
        if(key instanceof MCStringConcrete keyC){
            String value = keyC.getValue().getValue();
            CompoundTag nbt = caller.getValue();
            nbt.remove(value);
        }else {
            caller.toDynamic(false);
            NBTDictionaryData.remove(key, caller);
        }
    }

}
