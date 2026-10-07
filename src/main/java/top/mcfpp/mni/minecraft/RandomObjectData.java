package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.core.lang.MCFPPValue;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.util.ValueWrapper;

public class RandomObjectData {
    @MNIFunction(normalParams = "range",  returnType = "int")
    public static void rand(MCFPPValue<Integer> range, ValueWrapper<MCInt> re){
        var i = re.get();
        Command.Companion.buildAll("execute store result scores",i.getName(), i.getSbObject(), "run random value", range);
    }

    @MNIFunction(normalParams = "range", returnType = "int")
    public static void roll(MCFPPValue<Integer> range, ValueWrapper<MCInt> re){
        var i = re.get();
        Command.Companion.buildAll("execute store result scores",i.getName(), i.getSbObject(), "run random roll", range);
    }

    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void resetAll(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.randomResetAll(context);
    }

    @MNIFunction(readOnlyParams = {"bool", "bool"}, normalParams = "int", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void reset(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.randomResetAllSequences(context);
    }
}
