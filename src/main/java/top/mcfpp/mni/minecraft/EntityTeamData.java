package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class EntityTeamData {
    @MNIFunction(normalParams = "mcfpp.minecraft:Team", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void joinTeam(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityJoinTeam(context);
    }

    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void leaveTeam(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityLeaveTeam(context);
    }
}
