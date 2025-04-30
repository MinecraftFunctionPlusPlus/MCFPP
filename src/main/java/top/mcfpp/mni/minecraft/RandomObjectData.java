package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
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

    @MNIFunction(returnType = "CommandReturn")
    public static void resetAll(ValueWrapper<CommandReturn> re){
        var command = new Command("random reset *");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(readOnlyParams = {"bool", "bool"}, normalParams = "int", returnType = "CommandReturn")
    public static void reset(
            MCFPPValue<Boolean> includeWorldSeed, MCFPPValue<Boolean> includeSequenceID,
            MCInt seed,
            ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("random reset *", seed, includeSequenceID.getValue(), includeSequenceID.getValue());
        Commands.processMacroCommandReturn(re, command);
    }
}
