package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class TeamData {
    @MNIFunction(caller = "Team", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void unregister(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.teamUnregister(context);
    }

    @MNIFunction(caller = "Team", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void register(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.teamRegister(context);
    }

    @MNIFunction(caller = "Team", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void clear(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.teamClear(context);
    }

}
