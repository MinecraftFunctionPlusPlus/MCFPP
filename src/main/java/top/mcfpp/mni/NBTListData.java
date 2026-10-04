package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.ListOperations;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.Var;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.nbt.NBTList;
import top.mcfpp.mni.annotation.NoExternalWrites;
import top.mcfpp.mni.annotation.WritesReceiver;
import top.mcfpp.util.ValueWrapper;

/** One member signature for constant and runtime lists. */
public class NBTListData {
    @WritesReceiver
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E")
    public static void add(Var<?> value, NBTList caller) { ListOperations.INSTANCE.add(caller, value, false); }

    @WritesReceiver
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E")
    public static void prepend(Var<?> value, NBTList caller) { ListOperations.INSTANCE.add(caller, value, true); }

    @WritesReceiver
    @MNIFunction(normalParams = {"list<E>"}, caller = "list", genericType = "E")
    public static void addAll(NBTList source, NBTList caller) { ListOperations.INSTANCE.addAll(caller, source, false); }

    @WritesReceiver
    @MNIFunction(normalParams = {"list<E>"}, caller = "list", genericType = "E")
    public static void prependAll(NBTList source, NBTList caller) { ListOperations.INSTANCE.addAll(caller, source, true); }

    @WritesReceiver
    @MNIFunction(normalParams = {"int", "E"}, caller = "list", genericType = "E")
    public static void insert(MCInt index, Var<?> value, NBTList caller) { ListOperations.INSTANCE.insert(caller, index, value); }

    @WritesReceiver
    @MNIFunction(normalParams = {"int"}, caller = "list", genericType = "E")
    public static void removeAt(MCInt index, NBTList caller) { ListOperations.INSTANCE.removeAt(caller, index); }

    @WritesReceiver
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E")
    public static void remove(Var<?> value, NBTList caller) { ListOperations.INSTANCE.remove(caller, value); }

    @NoExternalWrites
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E", returnType = "int")
    public static void indexOf(Var<?> value, NBTList caller, ValueWrapper<MCInt> result) {
        result.setValue(ListOperations.INSTANCE.indexOf(caller, value, false));
    }

    @NoExternalWrites
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E", returnType = "int")
    public static void lastIndexOf(Var<?> value, NBTList caller, ValueWrapper<MCInt> result) {
        result.setValue(ListOperations.INSTANCE.indexOf(caller, value, true));
    }

    @NoExternalWrites
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E", returnType = "bool")
    public static void contains(Var<?> value, NBTList caller, ValueWrapper<BaseBool> result) {
        result.setValue(ListOperations.INSTANCE.contains(caller, value));
    }

    @WritesReceiver
    @MNIFunction(caller = "list", genericType = "E")
    public static void clear(NBTList caller) { ListOperations.INSTANCE.clear(caller); }
}
