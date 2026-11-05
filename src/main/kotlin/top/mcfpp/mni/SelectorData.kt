package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.mni.annotation.WritesReceiver
import top.mcfpp.backend.NativeSelectorOperations

/** Implements entity selector filters for MCFPP selector values. */
object SelectorData {

    /** Sets the selector origin X coordinate. */
    @WritesReceiver
    @MNIFunction(normalParams = ["int"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun x(context: NativeCallContext) {
        NativeSelectorOperations.x(context)
    }

    /** Sets the selector origin Y coordinate. */
    @WritesReceiver
    @MNIFunction(normalParams = ["int"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun y(context: NativeCallContext) {
        NativeSelectorOperations.y(context)
    }

    /** Sets the selector origin Z coordinate. */
    @WritesReceiver
    @MNIFunction(normalParams = ["int"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun z(context: NativeCallContext) {
        NativeSelectorOperations.z(context)
    }

    /** Filters entities by distance from the selector origin. */
    @WritesReceiver
    @MNIFunction(normalParams = ["range"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun distance(context: NativeCallContext) {
        NativeSelectorOperations.distance(context)
    }

    /** Sets the selector box extent along the X axis. */
    @WritesReceiver
    @MNIFunction(normalParams = ["int"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun dx(context: NativeCallContext) {
        NativeSelectorOperations.dx(context)
    }

    /** Sets the selector box extent along the Y axis. */
    @WritesReceiver
    @MNIFunction(normalParams = ["int"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun dy(context: NativeCallContext) {
        NativeSelectorOperations.dy(context)
    }

    /** Sets the selector box extent along the Z axis. */
    @WritesReceiver
    @MNIFunction(normalParams = ["int"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun dz(context: NativeCallContext) {
        NativeSelectorOperations.dz(context)
    }

    //TODO score

    /** Requires the supplied entity tag. */
    @WritesReceiver
    @MNIFunction(normalParams = ["string"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun tag(context: NativeCallContext) {
        NativeSelectorOperations.tag(context)
    }

    /** Excludes entities carrying the supplied tag. */
    @WritesReceiver
    @MNIFunction(normalParams = ["string"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun tagNot(context: NativeCallContext) {
        NativeSelectorOperations.tagNot(context)
    }

    /** Filters entities by team. */
    @WritesReceiver
    @MNIFunction(normalParams = ["string"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun team(context: NativeCallContext) {
        NativeSelectorOperations.team(context)
    }

    /** Excludes the supplied team. */
    @WritesReceiver
    @MNIFunction(normalParams = ["string"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun teamNot(context: NativeCallContext) {
        NativeSelectorOperations.teamNot(context)
    }

    /** Filters entities by name. */
    @WritesReceiver
    @MNIFunction(normalParams = ["string"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun name(context: NativeCallContext) {
        NativeSelectorOperations.name(context)
    }

    /** Excludes the supplied name. */
    @WritesReceiver
    @MNIFunction(normalParams = ["string"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun nameNot(context: NativeCallContext) {
        NativeSelectorOperations.nameNot(context)
    }

    /** Filters entities by entity type. */
    @WritesReceiver
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:EntityType"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun type(context: NativeCallContext) {
        NativeSelectorOperations.type(context)
    }

    /** Excludes the supplied entity type. */
    @WritesReceiver
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:EntityType"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun typeNot(context: NativeCallContext) {
        NativeSelectorOperations.typeNot(context)
    }

    /** Requires the supplied resource predicate to pass. */
    @WritesReceiver
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Predicate"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun predicate(context: NativeCallContext) {
        NativeSelectorOperations.predicate(context)
    }

    /** Requires the supplied resource predicate to fail. */
    @WritesReceiver
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Predicate"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun predicateNot(context: NativeCallContext) {
        NativeSelectorOperations.predicateNot(context)
    }

    /** Filters entities by pitch range. */
    @WritesReceiver
    @MNIFunction(normalParams = ["range"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun xRotation(context: NativeCallContext) {
        NativeSelectorOperations.xRotation(context)
    }

    /** Filters entities by yaw range. */
    @WritesReceiver
    @MNIFunction(normalParams = ["range"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun yRotation(context: NativeCallContext) {
        NativeSelectorOperations.yRotation(context)
    }

    /** Filters entities by the supplied NBT data. */
    @WritesReceiver
    @MNIFunction(normalParams = ["nbt"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun filterNbt(context: NativeCallContext) {
        NativeSelectorOperations.nbt(context)
    }

    /** Filters players by experience level. */
    @WritesReceiver
    @MNIFunction(normalParams = ["range"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun level(context: NativeCallContext) {
        NativeSelectorOperations.level(context)
    }

    /** Filters players by game mode. */
    @WritesReceiver
    @MNIFunction(normalParams = ["string"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun gamemode(context: NativeCallContext) {
        NativeSelectorOperations.gamemode(context)
    }

    /** Excludes the supplied game mode. */
    @WritesReceiver
    @MNIFunction(normalParams = ["string"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun gamemodeNot(context: NativeCallContext) {
        NativeSelectorOperations.gamemodeNot(context)
    }

    /** Filters players by advancement progress. */
    @WritesReceiver
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Advancement"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun advancements(context: NativeCallContext) {
        NativeSelectorOperations.advancements(context)
    }

    /** Excludes the supplied advancement progress match. */
    @WritesReceiver
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Advancement"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun advancementsNot(context: NativeCallContext) {
        NativeSelectorOperations.advancementsNot(context)
    }

    /** Sets a positive maximum result count. */
    @WritesReceiver
    @MNIFunction(normalParams = ["int"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun limit(context: NativeCallContext) {
        NativeSelectorOperations.limit(context)
    }

    /** Sets entity ordering before the result limit. */
    @WritesReceiver
    @MNIFunction(normalParams = ["string"], caller = "entity", returnType = "entity")
    @JvmStatic
    fun sort(context: NativeCallContext) {
        NativeSelectorOperations.sort(context)
    }
}
