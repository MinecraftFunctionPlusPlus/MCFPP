package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.ListOperations;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.Var;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.nbt.NBTList;
import top.mcfpp.mni.annotation.NoExternalWrites;
import top.mcfpp.util.ValueWrapper;

/** Read-only members share the same list query backend in every value state. */
public class ImmutableListData {
    @NoExternalWrites
    @MNIFunction(normalParams = "E", caller = "ImmutableList", genericType = "E", returnType = "int")
    public static void indexOf(Var<?> value, NBTList caller, ValueWrapper<MCInt> result) {
        result.setValue(ListOperations.INSTANCE.indexOf(caller, value, false));
    }

    @NoExternalWrites
    @MNIFunction(normalParams = "E", caller = "ImmutableList", genericType = "E", returnType = "int")
    public static void lastIndexOf(Var<?> value, NBTList caller, ValueWrapper<MCInt> result) {
        result.setValue(ListOperations.INSTANCE.indexOf(caller, value, true));
    }

    @NoExternalWrites
    @MNIFunction(normalParams = "E", caller = "ImmutableList", genericType = "E", returnType = "bool")
    public static void contains(Var<?> value, NBTList caller, ValueWrapper<BaseBool> result) {
        result.setValue(ListOperations.INSTANCE.contains(caller, value));
    }
}
