package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIAccessor;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMutator;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.MCBool;
import top.mcfpp.core.lang.bool.MCBoolConcrete;
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
            var marcoCall = command.buildMacroFunction(Objects.requireNonNull(bossbar.nbtPath.parent()));
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
            var marcoCall = command.buildMacroFunction(Objects.requireNonNull(bossbar.nbtPath.parent()));
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

    @MNIAccessor(name = "max")
    public static void getMax(DataTemplateObject bossbar, ValueWrapper<MCInt> returnValue){
        var id = Objects.requireNonNull(bossbar.getMemberVarWithT("id", MCString.class));
        var command = new Command("execute store result score")
                .build(returnValue.getValue().nbtPath.toCommandPart(), true)
                .build("run bossbar get", true);
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id.getIdentifier(), true);
        }
        command.build("max", true);
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction(Objects.requireNonNull(bossbar.nbtPath.parent()));
            Function.Companion.addCommand(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }

    @MNIMutator(name = "max")
    public static void setMax(DataTemplateObject bossbar, MCInt value){
        var id = Objects.requireNonNull(bossbar.getMemberVarWithT("id", MCString.class));
        Command command;
        if(value instanceof MCIntConcrete valueC){
            command = new Command("bossbar set");
            if(id instanceof MCStringConcrete idC){
                command.build(idC.getValue().getValue(), true);
            }else {
                command.buildMacro(id.getIdentifier(), true);
            }
            command.build("max", true);
            command.build(valueC.getValue().toString(), true);
        }else {
            command = new Command("execute store result bossbar");
            if(id instanceof MCStringConcrete idC){
                command.build(idC.getValue().getValue(), true);
            }else {
                command.buildMacro(id.getIdentifier(), true);
            }
            command.build("max run", true);
            command.build(Commands.INSTANCE.sbPlayerGet(value), true);
        }
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction(Objects.requireNonNull(bossbar.nbtPath.parent()));
            Function.Companion.addCommand(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }

    @MNIAccessor(name = "value")
    public static void getValue(DataTemplateObject bossbar, ValueWrapper<MCInt> returnValue){
        var id = Objects.requireNonNull(bossbar.getMemberVarWithT("id", MCString.class));
        var command = new Command("execute store result score")
                .build(returnValue.getValue().nbtPath.toCommandPart(), true)
                .build("run bossbar get", true);
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id.getIdentifier(), true);
        }
        command.build("value", true);
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction(Objects.requireNonNull(bossbar.nbtPath.parent()));
            Function.Companion.addCommand(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }

    @MNIMutator(name = "value")
    public static void setValue(DataTemplateObject bossbar, MCInt value){
        var id = Objects.requireNonNull(bossbar.getMemberVarWithT("id", MCString.class));
        Command command;
        if(value instanceof MCIntConcrete valueC){
            command = new Command("bossbar set");
            if(id instanceof MCStringConcrete idC){
                command.build(idC.getValue().getValue(), true);
            }else {
                command.buildMacro(id.getIdentifier(), true);
            }
            command.build("value", true);
            command.build(valueC.getValue().toString(), true);
        }else {
            command = new Command("execute store result bossbar");
            if(id instanceof MCStringConcrete idC){
                command.build(idC.getValue().getValue(), true);
            }else {
                command.buildMacro(id.getIdentifier(), true);
            }
            command.build("value run", true);
            command.build(Commands.INSTANCE.sbPlayerGet(value), true);
        }
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction(Objects.requireNonNull(bossbar.nbtPath.parent()));
            Function.Companion.addCommand(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }

    @MNIAccessor(name = "visible")
    public static void getVisible(DataTemplateObject bossbar, ValueWrapper<MCBool> returnValue){
        var id = Objects.requireNonNull(bossbar.getMemberVarWithT("id", MCString.class));
        var command = new Command("execute store result score")
                .build(returnValue.getValue().nbtPath.toCommandPart(), true)
                .build("run bossbar get", true);
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id.getIdentifier(), true);
        }
        command.build("visible", true);
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction(Objects.requireNonNull(bossbar.nbtPath.parent()));
            Function.Companion.addCommand(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }

    @MNIMutator(name = "visible")
    public static void setVisible(DataTemplateObject bossbar, MCBool value){
        var id = Objects.requireNonNull(bossbar.getMemberVarWithT("id", MCString.class));
        Command command;
        if(value instanceof MCBoolConcrete valueC){
            command = new Command("bossbar set");
            if(id instanceof MCStringConcrete idC){
                command.build(idC.getValue().getValue(), true);
            }else {
                command.buildMacro(id.getIdentifier(), true);
            }
            command.build("visible", true);
            command.build(valueC.getValue().toString(), true);

        }else {
            if(id instanceof MCStringConcrete idC){
                command = Commands.INSTANCE.tempFunction(Function.Companion.getCurrFunction(), (f) -> {
                    var command1 = new Command("execute if score " + value.getIdentifier() + " " + value.getBoolObject().getName() + " matches 1 run return run")
                            .build("bossbar set " + idC.getValue().getValue() + " visible true", true);
                    Function.Companion.addCommand(command1);
                    var command2 = new Command("bossbar set " + idC.getValue().getValue() + " visible false");
                    Function.Companion.addCommand(command2);
                    return null;
                }).getFirst();
            }else {
                command = Commands.INSTANCE.tempFunction(Function.Companion.getCurrFunction(), (f) -> {
                    var command1 = new Command("execute if score " + value.getIdentifier() + " " + value.getBoolObject().getName() + " matches 1 run return run")
                            .build("bossbar set ", true)
                            .buildMacro(id.getIdentifier(), true)
                            .build("visible true", true).toMacro();
                    Function.Companion.addCommand(command1);
                    var command2 = new Command("bossbar set ")
                            .buildMacro(id.getIdentifier(), true)
                            .build("visible false", true).toMacro();
                    Function.Companion.addCommand(command2);
                    return null;
                }).getFirst();
                command.build("with",true).build(bossbar.nbtPath.parent().toCommandPart(), true);
            }
        }
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction(Objects.requireNonNull(bossbar.nbtPath.parent()));
            Function.Companion.addCommand(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }
}
