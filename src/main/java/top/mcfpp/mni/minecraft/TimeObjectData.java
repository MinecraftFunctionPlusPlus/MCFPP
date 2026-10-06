package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeTimeOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.mni.annotation.NoExternalWrites;

@NoExternalWrites
public class TimeObjectData {

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void tick(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.factory(context, 1);
    }

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void second(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.factory(context, 20);
    }

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void min(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.factory(context, 1200);
    }

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void hour(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.factory(context, 72000);
    }

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void day(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.factory(context, 144000);
    }

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void gameDay(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.factory(context, 24000);
    }

}
