package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements entity attribute values and modifiers. */
object EntityAttributeData {
    /** Sets the receiver attribute base value. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["string", "double"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setAttributeBase(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entitySetAttributeBase(context)
    }

    /**
     * Adds a named modifier to the receiver attribute. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(readOnlyParams = ["mcfpp.minecraft.other:AttributeModifierType"], normalParams = ["string", "mcfpp.minecraft.other:AttributeModifier"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun addAttributeModifier(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityAddAttributeModifier(context)
    }

    /**
     * Reads the receiver attribute base value with the supplied scale. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(normalParams = ["string", "float"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun getAttributeBase(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityGetAttributeBase(context)
    }

    /**
     * Reads the receiver attribute value, including modifiers, with the supplied scale. Publishes the command
     * result and success status through context.
     */
    @MNIFunction(normalParams = ["string", "float"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun getAttribute(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityGetAttribute(context)
    }

    /**
     * Removes the named modifier from the receiver attribute. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["string", "mcfpp.minecraft.other:AttributeModifier"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun removeAttributeModifier(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityRemoveAttributeModifier(context)
    }

    /**
     * Reads a named attribute modifier value with the supplied scale. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(normalParams = ["string", "mcfpp.minecraft.other:AttributeModifier", "float"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun getAttributeModifier(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityGetAttributeModifier(context)
    }
}
