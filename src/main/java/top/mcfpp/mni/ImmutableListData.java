package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.ListOperations;
import top.mcfpp.mni.annotation.NoExternalWrites;

/** Read-only members share the same list query backend in every value state. */
public class ImmutableListData {
    @NoExternalWrites
    @MNIFunction(normalParams = "E", caller = "ImmutableList", genericType = "E", returnType = "int")
    public static void indexOf(NativeCallContext context) {
        ListOperations.INSTANCE.indexOf(context, false);
    }

    @NoExternalWrites
    @MNIFunction(normalParams = "E", caller = "ImmutableList", genericType = "E", returnType = "int")
    public static void lastIndexOf(NativeCallContext context) {
        ListOperations.INSTANCE.indexOf(context, true);
    }

    @NoExternalWrites
    @MNIFunction(normalParams = "E", caller = "ImmutableList", genericType = "E", returnType = "bool")
    public static void contains(NativeCallContext context) {
        ListOperations.INSTANCE.contains(context);
    }
}
