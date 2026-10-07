package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class EntityAttributeData {
    @MNIFunction(normalParams = {"string", "double"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setAttributeBase(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entitySetAttributeBase(context);
    }

    @MNIFunction(readOnlyParams = "mcfpp.minecraft.other:AttributeModifierType", normalParams = {"string", "mcfpp.minecraft.other:AttributeModifier"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void addAttributeModifier(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityAddAttributeModifier(context);
    }

    @MNIFunction(normalParams = {"string", "float"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void getAttributeBase(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityGetAttributeBase(context);
    }

    @MNIFunction(normalParams = {"string", "float"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void getAttribute(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityGetAttribute(context);
    }

    @MNIFunction(normalParams = {"string", "mcfpp.minecraft.other:AttributeModifier"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void removeAttributeModifier(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityRemoveAttributeModifier(context);
    }

    @MNIFunction(normalParams = {"string", "mcfpp.minecraft.other:AttributeModifier", "float"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void getAttributeModifier(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityGetAttributeModifier(context);
    }
}
