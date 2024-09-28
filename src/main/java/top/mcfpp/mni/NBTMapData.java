package top.mcfpp.mni;

import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.StringTag;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.MCBool;
import top.mcfpp.core.lang.bool.MCBoolConcrete;
import top.mcfpp.util.ValueWrapper;

@SuppressWarnings({"unchecked"})
public class NBTMapData {

    @MNIFunction(caller = "dict")
    public static void clear(NBTMap caller){
        NBTListData.clear(caller.getKeyList());
        NBTListData.clear(caller.getValueList());
        NBTDictionaryData.clear(caller.getKeyValueSet());
    }

    @MNIFunction(normalParams = {"string key"}, caller = "dict", returnType = "bool")
    public static void containsKey(MCString key, NBTMapConcrete caller, ValueWrapper<MCBool> re){
        NBTListConcreteData.contains(key, (NBTListConcrete) caller.getKeyList(), re);
    }

    @MNIFunction(normalParams = {""}, caller = "dict")
    public static void containsValue(Var<?> element, NBTMapConcrete caller, ValueWrapper<MCBool> re){
        NBTListConcreteData.contains(element.toNBTVar(), (NBTListConcrete) caller.getValueList(), re);
    }

    @MNIFunction(caller = "dict", returnType = "bool")
    public static void isEmpty(NBTMapConcrete caller, ValueWrapper<MCBool> re){
        re.setValue(new MCBoolConcrete(((NBTListConcrete)(caller.getKeyList())).getValue().size() == 0, "return"));
    }

    @MNIFunction(caller = "dict", returnType = "list")
    public static void getKeys(NBTMapConcrete caller, ValueWrapper<NBTListConcrete> re){
        re.setValue((NBTListConcrete) caller.getKeyList());
    }

    @MNIFunction(caller = "dict", returnType = "list")
    public static void getValues(NBTMapConcrete caller, ValueWrapper<NBTListConcrete> re){
        re.setValue((NBTListConcrete) caller.getValueList());
    }

    @MNIFunction(normalParams = {"string key"}, caller = "dict")
    public static void remove(MCString key, NBTMapConcrete caller){
        if(key instanceof MCStringConcrete keyC) {
            StringTag keyTag = keyC.getValue();
            String keyStr = keyTag.getValue();
            caller.getValue().remove(keyStr);
            int index = ((ListTag<StringTag>)((NBTListConcrete)(caller.getKeyList())).getValue()).indexOf(keyTag);
            ((NBTListConcrete)(caller.getKeyList())).getValue().remove(index);
            ((NBTListConcrete)(caller.getValueList())).getValue().remove(index);
        }else {
            NBTMapData.remove(key, caller);
        }
    }

    @MNIFunction(normalParams = {"dict source"}, caller = "dict")
    public static void merge(NBTMap source, NBTMapConcrete caller){
        if(source instanceof NBTMapConcrete sourceC){

            NBTListConcreteData.addAll(sourceC.getKeyList(),(NBTListConcrete)caller.getKeyList());
            NBTListConcreteData.addAll(sourceC.getValueList(),(NBTListConcrete) caller.getValueList());
        }else {
            NBTMapData.merge(source, caller);
        }
    }

    @MNIFunction(caller = "dict", returnType = "int")
    public static void size(NBTMapConcrete caller, ValueWrapper<MCInt> re){
        re.setValue(new MCIntConcrete(((NBTListConcrete)(caller.getKeyList())).getValue().size(), "return"));
    }
}
