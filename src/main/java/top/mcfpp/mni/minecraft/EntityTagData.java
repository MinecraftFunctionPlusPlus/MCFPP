package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class EntityTagData {
    @MNIFunction(normalParams = "string", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void addTag(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityAddTag(context);
    }

    @MNIFunction(normalParams = "string", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void removeTag(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityRemoveTag(context);
    }

    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void listTag(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityListTag(context);
    }
}
