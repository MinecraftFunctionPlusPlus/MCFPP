package top.mcfpp.mni;

import net.querz.nbt.tag.StringTag;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.core.lang.*;
import top.mcfpp.util.ValueWrapper;

public class NBTMapData {

    @MNIRegister(caller = "dict")
    public static void clear(NBTMap caller){
        NBTListData.clear(caller.getKeyList());
        NBTListData.clear(caller.getValueList());
        NBTDictionaryData.clear(caller.getKeyValueSet());
    }

    @MNIRegister(normalParams = {"string key"}, caller = "dict", returnType = "bool")
    public static void containsKey(MCString key, NBTMapConcrete caller, ValueWrapper<MCBool> re){
        NBTListConcreteData.contains(key, (NBTListConcrete<?>) caller.getKeyList(), re);
    }

    @MNIRegister(normalParams = {""}, caller = "dict")
    public static void containsValue(Var<?> element, NBTMapConcrete caller, ValueWrapper<MCBool> re){
        NBTListConcreteData.contains(element.toNBTVar(), (NBTListConcrete<?>) caller.getValueList(), re);
    }

    @MNIRegister(caller = "dict", returnType = "bool")
    public static void isEmpty(NBTMapConcrete caller, ValueWrapper<MCBool> re){
        re.setValue(new MCBoolConcrete(((NBTListConcrete<?>)(caller.getKeyList())).getValue().size() == 0, "return"));
    }

    @MNIRegister(caller = "dict", returnType = "list")
    public static void getKeys(NBTMapConcrete caller, ValueWrapper<NBTListConcrete<?>> re){
        re.setValue((NBTListConcrete<?>) caller.getKeyList());
    }

    @MNIRegister(caller = "dict", returnType = "list")
    public static void getValues(NBTMapConcrete caller, ValueWrapper<NBTListConcrete<?>> re){
        re.setValue((NBTListConcrete<?>) caller.getValueList());
    }

    @MNIRegister(normalParams = {"string key"}, caller = "dict")
    public static void remove(MCString key, NBTMapConcrete caller){
        if(key instanceof MCStringConcrete keyC) {
            StringTag keyTag = keyC.getValue();
            String keyStr = keyTag.getValue();
            caller.getValue().remove(keyStr);
            int index = ((NBTListConcrete)(caller.getKeyList())).getValue().indexOf(keyTag);
            ((NBTListConcrete<?>)(caller.getKeyList())).getValue().remove(index);
            ((NBTListConcrete<?>)(caller.getValueList())).getValue().remove(index);
        }else {
            NBTMapData.remove(key, caller);
        }
    }

    @MNIRegister(normalParams = {"dict source"}, caller = "dict")
    public static void merge(NBTMap source, NBTMapConcrete caller){
        if(source instanceof NBTMapConcrete sourceC){

            NBTListConcreteData.addAll(sourceC.getKeyList(),(NBTListConcrete)caller.getKeyList());
            NBTListConcreteData.addAll(sourceC.getValueList(),(NBTListConcrete) caller.getValueList());
        }else {
            NBTMapData.merge(source, caller);
        }
    }

    @MNIRegister(caller = "dict", returnType = "int")
    public static void size(NBTMapConcrete caller, ValueWrapper<MCInt> re){
        re.setValue(new MCIntConcrete(((NBTListConcrete<?>)(caller.getKeyList())).getValue().size(), "return"));
    }
}
