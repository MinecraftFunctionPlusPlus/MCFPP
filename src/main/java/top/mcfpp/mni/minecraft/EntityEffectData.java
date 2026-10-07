package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class EntityEffectData {
    @MNIFunction(normalParams = {"mcfpp.minecraft.resource:Effect", "int = 30", "int = 0", "bool = false"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void effect(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityEffect(context);
    }

    @MNIFunction(normalParams = {"mcfpp.minecraft.resource:Effect", "int = 0", "bool = false"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void effectInfinite(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityEffectInfinite(context);
    }

    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void clearAllEffects(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityClearAllEffects(context);
    }

    @MNIFunction(normalParams = "mcfpp.minecraft.resource:Effect", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void clearEffect(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityClearEffect(context);
    }
}
