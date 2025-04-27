package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.DataTemplateObject;
import top.mcfpp.core.lang.MCFPPValue;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.util.ValueWrapper;

import javax.management.MBeanConstructorInfo;

public class RandomData {

    @MNIFunction(readOnlyParams = {"bool", "bool"}, normalParams = "int", caller = "Random", returnType = "CommandReturn")
    public static void reset(
            MCFPPValue<Boolean> includeWorldSeed, MCFPPValue<Boolean> includeSequenceID,
            MCInt seed,
            DataTemplateObject caller,
            ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("random reset", caller, seed, includeSequenceID.getValue(), includeSequenceID.getValue());
        Commands.processMacroCommandReturn(re, command);
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
