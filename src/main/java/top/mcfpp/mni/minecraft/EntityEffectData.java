package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class EntityEffectData {
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void clearAllEffects(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityClearAllEffects(context);
    }

    @MNIFunction(normalParams = "mcfpp.minecraft.resource:Effect", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void clearEffect(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityClearEffect(context);
    }
}
