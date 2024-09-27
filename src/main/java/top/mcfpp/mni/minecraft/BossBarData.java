package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.core.lang.*;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

import java.util.Objects;

public class BossBarData {

    @MNIFunction(caller = "BossBar", returnType = "CommandReturn")
    public static void add(DataTemplateObject bossbar, ValueWrapper<CommandReturn> returnValue){
        var id = Objects.requireNonNull(bossbar.getMemberVarWithT("id", MCString.class));
        var name = Objects.requireNonNull(bossbar.getMemberVarWithT("name", JsonText.class));
        var command = new Command("bossbar add");
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id.getIdentifier(), true);
        }
        command.build(name.toCommandPart(), true);
        if(command.isMacro()){
            var marcoCall = command.buildMacroCommand(Objects.requireNonNull(bossbar.nbtPath.parent()));
            returnValue.setValue(new CommandReturn(marcoCall,"bossbar_add"));
            Function.Companion.addCommand(marcoCall);
        }else {
            returnValue.setValue(new CommandReturn(command,"bossbar_add"));
            Function.Companion.addCommand(command);
        }
    }

    @MNIFunction(caller = "BossBar", returnType = "CommandReturn")
    public static void remove(DataTemplateObject bossbar, ValueWrapper<CommandReturn> returnValue){
        var id = Objects.requireNonNull(bossbar.getMemberVarWithT("id", MCString.class));
        var command = new Command("bossbar remove");
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id.getIdentifier(), true);
        }
        if(command.isMacro()){
            var marcoCall = command.buildMacroCommand(Objects.requireNonNull(bossbar.nbtPath.parent()));
            returnValue.setValue(new CommandReturn(marcoCall,"bossbar_remove"));
            Function.Companion.addCommand(marcoCall);
        }else {
            returnValue.setValue(new CommandReturn(command,"bossbar_remove"));
            Function.Companion.addCommand(command);
        }
    }

    @MNIFunction(caller = "BossBar", isObject = true, returnType = "CommandReturn")
    public static void list(ValueWrapper<CommandReturn> returnValue){
        var command = new Command("bossbar list");
        returnValue.setValue(new CommandReturn(command,"bossbar_list"));
        Function.Companion.addCommand(command);
    }

}
