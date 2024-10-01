package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.resource.Advancement;
import top.mcfpp.core.lang.resource.AdvancementConcrete;
import top.mcfpp.core.minecraft.PlayerVar;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

import java.util.Arrays;

@SuppressWarnings("unused")
public class PlayerEntityConcreteData {

    public static void grant(Advancement advancement, PlayerVar.PlayerEntityVarConcrete caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] command;
        String playerName = caller.value.getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command[] {new Command("advancement grant " + playerName + " only " + ((AdvancementConcrete) advancement).getValue())};
        }else {
            command = new Command("advancement grant " + playerName + " only").buildMacro(advancement, true).buildMacroFunction();
        }
        returnValue.setValue(new CommandReturn(command[command.length - 1], "return"));
        Function.Companion.addCommands(command);
    }

    public static void grantAll(PlayerVar.PlayerEntityVarConcrete caller, ValueWrapper<CommandReturn> returnValue) {
        String playerName = caller.value.getValue();
        var command = new Command("advancement grant " + playerName  + " everything");
        returnValue.setValue(new CommandReturn(command, "return"));
        Function.Companion.addCommand(command);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantFrom(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        String playerName = caller.getEntity().getName();
        if(advancement instanceof AdvancementConcrete){
            commands = new Command[]{new Command("advancement grant " + playerName + " from " + ((AdvancementConcrete) advancement).getValue())};
        }else {
            commands = new Command("return run advancement grant @s from").buildMacro(advancement, true).buildMacroFunction();
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantThrough(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        String playerName = caller.getEntity().getName();
        if(advancement instanceof AdvancementConcrete){
            commands = new Command[]{new Command("advancement grant " + playerName + " through " + ((AdvancementConcrete) advancement).getValue())};
        }else {
            commands = new Command("return run advancement grant @s through").buildMacro(advancement, true).buildMacroFunction();
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantUntil(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        String playerName = caller.getEntity().getName();
        if(advancement instanceof AdvancementConcrete){
            commands = new Command[]{new Command("advancement grant " + playerName + " until " + ((AdvancementConcrete) advancement).getValue())};
        }else {
            commands = new Command("return run advancement grant @s until").buildMacro(advancement, true).buildMacroFunction();
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revoke(Advancement advancement, PlayerVar.PlayerEntityVarConcrete caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] command;
        String playerName = caller.value.getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command[] {new Command("advancement revoke " + playerName + " only " + ((AdvancementConcrete) advancement).getValue())};
        }else {
            command = new Command("advancement revoke " + playerName + " only").buildMacro(advancement, true).buildMacroFunction();
        }
        returnValue.setValue(new CommandReturn(command[command.length - 1], "return"));
        Function.Companion.addCommands(command);
    }

    @MNIFunction(caller = "Player", returnType = "CommandReturn")
    public static void revokeAll(PlayerVar.PlayerEntityVarConcrete caller, ValueWrapper<CommandReturn> returnValue) {
        String playerName = caller.value.getValue();
        var command = new Command("advancement revoke " + playerName  + " everything");
        returnValue.setValue(new CommandReturn(command, "return"));
        Function.Companion.addCommand(command);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeFrom(Advancement advancement, PlayerVar.PlayerEntityVarConcrete caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] command;
        String playerName = caller.value.getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command[] {new Command("advancement revoke " + playerName + " from " + ((AdvancementConcrete) advancement).getValue())};
        }else {
            command = new Command("advancement revoke " + playerName + " from").buildMacro(advancement, true).buildMacroFunction();
        }
        returnValue.setValue(new CommandReturn(command[command.length - 1], "return"));
        Function.Companion.addCommands(command);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeThrough(Advancement advancement, PlayerVar.PlayerEntityVarConcrete caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] command;
        String playerName = caller.value.getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command[] {new Command("advancement revoke " + playerName + " through " + ((AdvancementConcrete) advancement).getValue())};
        }else {
            command = new Command("advancement revoke " + playerName + " through").buildMacro(advancement, true).buildMacroFunction();
        }
        returnValue.setValue(new CommandReturn(command[command.length - 1], "return"));
        Function.Companion.addCommands(command);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeUntil(Advancement advancement, PlayerVar.PlayerEntityVarConcrete caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] command;
        String playerName = caller.value.getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command[] {new Command("advancement revoke " + playerName + " until " + ((AdvancementConcrete) advancement).getValue())};
        }else {
            command = new Command("advancement revoke " + playerName + " until").buildMacro(advancement, true).buildMacroFunction();
        }
        returnValue.setValue(new CommandReturn(command[command.length - 1], "return"));
        Function.Companion.addCommands(command);
    }

    @MNIFunction(normalParams = {"string key, float scale"}, caller = "Player", returnType = "int")
    public static void getAttribute(MCString id, MCFloat scale, PlayerVar.PlayerEntityVarConcrete caller, ValueWrapper<CommandReturn> returnValue){
        Command[] commands;
        Command command;
        String playerName = caller.value.getValue();
        if(id instanceof MCStringConcrete idC){
            command = new Command("attribute " + playerName + " " + idC.getValue().getValue() + " get");
        }else {
            command = new Command("attribute " + playerName).buildMacro(id, true).build("get", false);
        }
        if(scale instanceof MCFloatConcrete scaleC){
            command.build(scaleC.getValue().toString(), true);
        }else {
            command.buildMacro(scale, true);
        }
        if(command.isMacro()){
            var mcs = command.buildMacroFunction();
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(), mcs[mcs.length - 1]);
            var l = Arrays.asList(mcs).subList(0, mcs.length - 1);
            l.addAll(Arrays.asList(commands));
            commands = l.toArray(new Command[0]);
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(), command);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }
}
