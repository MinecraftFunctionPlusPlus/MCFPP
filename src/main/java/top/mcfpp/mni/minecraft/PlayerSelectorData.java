package top.mcfpp.mni.minecraft;

import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.resource.Advancement;
import top.mcfpp.core.lang.resource.AdvancementConcrete;
import top.mcfpp.core.minecraft.PlayerVar;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

import java.util.Arrays;

public class PlayerSelectorData {

    public static void grant(Advancement advancement, PlayerVar.PlayerSelectorVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        Command command;
        var selector = caller.getSelector().getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command("advancement grant").build(selector.toCommandPart(), true)
                    .build("only " + ((AdvancementConcrete) advancement).getValue(), true);
        }else {
            command = new Command("advancement grant").build(selector.toCommandPart(), true)
                    .build("only", true).buildMacro(advancement, true);
        }
        commands = command.buildMacroFunction();
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void grantAll(PlayerVar.PlayerSelectorVar caller, ValueWrapper<CommandReturn> returnValue) {
        var selector = caller.getSelector();
        var commands = new Command("advancement grant").build(selector.getValue().toCommandPart(), true).build("everything", true).buildMacroFunction();
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void grantFrom(Advancement advancement, PlayerVar.PlayerSelectorVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        Command command;
        var selector = caller.getSelector().getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command("advancement grant").build(selector.toCommandPart(), true)
                    .build("from " + ((AdvancementConcrete) advancement).getValue(), true);
        }else {
            command = new Command("advancement grant").build(selector.toCommandPart(), true)
                    .build("from", true).buildMacro(advancement, true);
        }
        commands = command.buildMacroFunction();
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void grantThrough(Advancement advancement, PlayerVar.PlayerSelectorVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        Command command;
        var selector = caller.getSelector().getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command("advancement grant").build(selector.toCommandPart(), true)
                    .build("through " + ((AdvancementConcrete) advancement).getValue(), true);
        }else {
            command = new Command("advancement grant").build(selector.toCommandPart(), true)
                    .build("through", true).buildMacro(advancement, true);
        }
        commands = command.buildMacroFunction();
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void grantUntil(Advancement advancement, PlayerVar.PlayerSelectorVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        Command command;
        var selector = caller.getSelector().getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command("advancement grant").build(selector.toCommandPart(), true)
                    .build("until " + ((AdvancementConcrete) advancement).getValue(), true);
        }else {
            command = new Command("advancement grant").build(selector.toCommandPart(), true)
                    .build("until", true).buildMacro(advancement, true);
        }
        commands = command.buildMacroFunction();
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void revoke(Advancement advancement, PlayerVar.PlayerSelectorVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        Command command;
        var selector = caller.getSelector().getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command("advancement revoke").build(selector.toCommandPart(), true)
                    .build("only " + ((AdvancementConcrete) advancement).getValue(), true);
        }else {
            command = new Command("advancement revoke").build(selector.toCommandPart(), true)
                    .build("only", true).buildMacro(advancement, true);
        }
        commands = command.buildMacroFunction();
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void revokeAll(PlayerVar.PlayerSelectorVar caller, ValueWrapper<CommandReturn> returnValue) {
        var selector = caller.getSelector();
        var commands = new Command("advancement revoke").build(selector.getValue().toCommandPart(), true).build("everything", true).buildMacroFunction();
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void revokeFrom(Advancement advancement, PlayerVar.PlayerSelectorVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        Command command;
        var selector = caller.getSelector().getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command("advancement revoke").build(selector.toCommandPart(), true)
                    .build("from " + ((AdvancementConcrete) advancement).getValue(), true);
        }else {
            command = new Command("advancement revoke").build(selector.toCommandPart(), true)
                    .build("from", true).buildMacro(advancement, true);
        }
        commands = command.buildMacroFunction();
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void revokeThrough(Advancement advancement, PlayerVar.PlayerSelectorVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        Command command;
        var selector = caller.getSelector().getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command("advancement revoke").build(selector.toCommandPart(), true)
                    .build("through " + ((AdvancementConcrete) advancement).getValue(), true);
        }else {
            command = new Command("advancement revoke").build(selector.toCommandPart(), true)
                    .build("through", true).buildMacro(advancement, true);
        }
        commands = command.buildMacroFunction();
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void revokeUntil(Advancement advancement, PlayerVar.PlayerSelectorVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        Command command;
        var selector = caller.getSelector().getValue();
        if(advancement instanceof AdvancementConcrete){
            command = new Command("advancement revoke").build(selector.toCommandPart(), true)
                    .build("until " + ((AdvancementConcrete) advancement).getValue(), true);
        }else {
            command = new Command("advancement revoke").build(selector.toCommandPart(), true)
                    .build("until", true).buildMacro(advancement, true);
        }
        commands = command.buildMacroFunction();
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void getAttribute(MCString id, MCFloat scale, PlayerVar.PlayerSelectorVar caller, ValueWrapper<CommandReturn> returnValue){
        Command[] commands;
        Command command;
        if(id instanceof MCStringConcrete idC){
            command = new Command("attribute @s " + idC.getValue().getValue() + " get");
        }else {
            command = new Command("attribute @s").buildMacro(id, true).build("get", false);
        }
        if(scale instanceof MCFloatConcrete scaleC){
            command.build(scaleC.getValue().toString(), true);
        }else {
            command.buildMacro(scale, true);
        }
        if(command.isMacro()){
            var mcs = command.buildMacroFunction();
            commands = Commands.INSTANCE.runAsEntity(caller.getSelector(), mcs[mcs.length - 1]);
            var l = Arrays.asList(mcs).subList(0, mcs.length - 1);
            l.addAll(Arrays.asList(commands));
            commands = l.toArray(new Command[0]);
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller.getSelector(), command);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }
}
