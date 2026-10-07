package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIAccessor;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMutator;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.mni.annotation.NoExternalWrites;

@SuppressWarnings("DataFlowIssue")
public class BossBarData {

    @MNIFunction(caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void add(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarAdd(context);
    }

    @MNIFunction(caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void remove(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarRemove(context);
    }

    @MNIFunction(caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void listAll(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarList(context);
    }

    @NoExternalWrites
    @MNIAccessor(value = "max")
    public static void getMax(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarGetMax(context);
    }

    @MNIMutator(value = "max")
    public static void setMax(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarSetMax(context);
    }

    @NoExternalWrites
    @MNIAccessor(value = "value")
    public static void getValue(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarGetValue(context);
    }

    @MNIMutator(value = "value")
    public static void setValue(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarSetValue(context);
    }

    @NoExternalWrites
    @MNIAccessor(value = "visible")
    public static void getVisible(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarGetVisible(context);
    }

    @MNIMutator(value = "visible")
    public static void setVisible(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarSetVisible(context);
    }

    @MNIFunction(normalParams = "BossBarColor", caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setColor(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarSetColor(context);
    }

    @MNIFunction(normalParams = "text", caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setName(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarSetName(context);
    }

    @MNIFunction(normalParams = "entity", caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setVisiblePlayers(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.bossbarSetVisiblePlayers(context);
    }

    @MNIFunction(normalParams = "BossBarStyle", caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setStyle(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarSetStyle(context);
    }
}
