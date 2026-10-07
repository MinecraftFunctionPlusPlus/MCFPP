package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.MCFPPValue;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.util.ValueWrapper;

public class RandomData {

    @MNIFunction(readOnlyParams = {"bool", "bool"}, normalParams = "int", caller = "Random", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void reset(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.randomReset(context);
    }

    @MNIFunction(normalParams = "range", caller = "Random", returnType = "int")
    public static void rand(MCFPPValue<Integer> range, DataTemplateObject caller, ValueWrapper<MCInt> re){
        var i = re.get();
        Command.Companion.buildAll("execute store result scores",i.getName(), i.getSbObject(), "run random value", range, caller);
    }

    @MNIFunction(normalParams = "range", caller = "Random", returnType = "int")
    public static void roll(MCFPPValue<Integer> range, DataTemplateObject caller, ValueWrapper<MCInt> re){
        var i = re.get();
        Command.Companion.buildAll("execute store result scores",i.getName(), i.getSbObject(), "run random roll", range, caller);
    }

}
