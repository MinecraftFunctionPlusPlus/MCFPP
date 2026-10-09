package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.ListOperations;
import top.mcfpp.mni.annotation.NoExternalWrites;
import top.mcfpp.mni.annotation.WritesReceiver;

/** One member signature for constant and runtime lists. */
public class NBTListData {
    @NoExternalWrites
    @MNIFunction(caller = "list", genericType = "E", returnType = "int")
    public static void size(NativeCallContext context) { ListOperations.INSTANCE.size(context); }

    @NoExternalWrites
    @MNIFunction(caller = "list", genericType = "E", returnType = "bool")
    public static void isEmpty(NativeCallContext context) { ListOperations.INSTANCE.isEmpty(context); }
    @WritesReceiver
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E")
    public static void add(NativeCallContext context) { ListOperations.INSTANCE.add(context, false); }

    @WritesReceiver
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E")
    public static void prepend(NativeCallContext context) { ListOperations.INSTANCE.add(context, true); }

    @WritesReceiver
    @MNIFunction(normalParams = {"list<E>"}, caller = "list", genericType = "E")
    public static void addAll(NativeCallContext context) { ListOperations.INSTANCE.addAll(context, false); }

    @WritesReceiver
    @MNIFunction(normalParams = {"list<E>"}, caller = "list", genericType = "E")
    public static void prependAll(NativeCallContext context) { ListOperations.INSTANCE.addAll(context, true); }

    @WritesReceiver
    @MNIFunction(normalParams = {"int", "E"}, caller = "list", genericType = "E")
    public static void insert(NativeCallContext context) { ListOperations.INSTANCE.insert(context); }

    @WritesReceiver
    @MNIFunction(normalParams = {"int"}, caller = "list", genericType = "E")
    public static void removeAt(NativeCallContext context) { ListOperations.INSTANCE.removeAt(context); }

    @WritesReceiver
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E")
    public static void remove(NativeCallContext context) { ListOperations.INSTANCE.remove(context); }

    @NoExternalWrites
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E", returnType = "int")
    public static void indexOf(NativeCallContext context) { ListOperations.INSTANCE.indexOf(context, false); }

    @NoExternalWrites
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E", returnType = "int")
    public static void lastIndexOf(NativeCallContext context) { ListOperations.INSTANCE.indexOf(context, true); }

    @NoExternalWrites
    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E", returnType = "bool")
    public static void contains(NativeCallContext context) { ListOperations.INSTANCE.contains(context); }

    @WritesReceiver
    @MNIFunction(caller = "list", genericType = "E")
    public static void clear(NativeCallContext context) { ListOperations.INSTANCE.clear(context); }
}
