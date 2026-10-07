package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.mni.annotation.WritesReceiver;
import top.mcfpp.backend.NativeSelectorOperations;

public class SelectorData {

    @WritesReceiver
    @MNIFunction(normalParams = {"int"}, caller = "entity", returnType = "entity")
    public static void x(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.x(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"int"}, caller = "entity", returnType = "entity")
    public static void y(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.y(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"int"}, caller = "entity", returnType = "entity")
    public static void z(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.z(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"range"}, caller = "entity", returnType = "entity")
    public static void distance(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.distance(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"int"}, caller = "entity", returnType = "entity")
    public static void dx(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.dx(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"int"}, caller = "entity", returnType = "entity")
    public static void dy(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.dy(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"int"}, caller = "entity", returnType = "entity")
    public static void dz(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.dz(context);
    }

    //TODO score

    @WritesReceiver
    @MNIFunction(normalParams = {"string"}, caller = "entity", returnType = "entity")
    public static void tag(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.tag(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"string"}, caller = "entity", returnType = "entity")
    public static void tagNot(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.tagNot(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"string"}, caller = "entity", returnType = "entity")
    public static void team(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.team(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"string"}, caller = "entity", returnType = "entity")
    public static void teamNot(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.teamNot(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"string"}, caller = "entity", returnType = "entity")
    public static void name(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.name(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"string"}, caller = "entity", returnType = "entity")
    public static void nameNot(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.nameNot(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"mcfpp.minecraft.resource:EntityType"}, caller = "entity", returnType = "entity")
    public static void type(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.type(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"mcfpp.minecraft.resource:EntityType"}, caller = "entity", returnType = "entity")
    public static void typeNot(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.typeNot(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"mcfpp.minecraft.resource:Predicate"}, caller = "entity", returnType = "entity")
    public static void predicate(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.predicate(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"mcfpp.minecraft.resource:Predicate"}, caller = "entity", returnType = "entity")
    public static void predicateNot(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.predicateNot(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"range"}, caller = "entity", returnType = "entity")
    public static void xRotation(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.xRotation(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"range"}, caller = "entity", returnType = "entity")
    public static void yRotation(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.yRotation(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"nbt"}, caller = "entity", returnType = "entity")
    public static void filterNbt(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.nbt(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"range"}, caller = "entity", returnType = "entity")
    public static void level(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.level(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"string"}, caller = "entity", returnType = "entity")
    public static void gamemode(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.gamemode(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"string"}, caller = "entity", returnType = "entity")
    public static void gamemodeNot(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.gamemodeNot(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"mcfpp.minecraft.resource:Advancement"}, caller = "entity", returnType = "entity")
    public static void advancements(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.advancements(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"mcfpp.minecraft.resource:Advancement"}, caller = "entity", returnType = "entity")
    public static void advancementsNot(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.advancementsNot(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"int"}, caller = "entity", returnType = "entity")
    public static void limit(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.limit(context);
    }

    @WritesReceiver
    @MNIFunction(normalParams = {"string"}, caller = "entity", returnType = "entity")
    public static void sort(NativeCallContext context) {
        NativeSelectorOperations.INSTANCE.sort(context);
    }
}
