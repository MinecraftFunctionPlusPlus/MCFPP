package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeEntityTargetOperations;
import top.mcfpp.mni.annotation.NoExternalWrites;

/** Explicit factories for direct entity targets. */
@NoExternalWrites
public class EntityTargetData {
    @MNIFunction(normalParams = {"string"}, returnType = "entity")
    public static void entityByName(NativeCallContext context) {
        NativeEntityTargetOperations.INSTANCE.entityByName(context);
    }

    @MNIFunction(normalParams = {"string"}, returnType = "entity")
    public static void entityByUUID(NativeCallContext context) {
        NativeEntityTargetOperations.INSTANCE.entityByUUID(context);
    }
}
