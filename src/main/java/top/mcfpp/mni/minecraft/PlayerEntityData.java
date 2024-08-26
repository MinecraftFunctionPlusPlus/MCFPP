package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.var.lang.*;
import top.mcfpp.var.lang.resource.Advancement;
import top.mcfpp.var.lang.resource.AdvancementConcrete;
import top.mcfpp.var.minecraft.PlayerVar;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

public class PlayerEntityData {

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grant(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s only " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s only").buildMacro(advancement.getIdentifier(), true)
            );
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIRegister(caller = "Player", returnType = "CommandReturn")
    public static void grantAll(PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        var commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                new Command("advancement grant @s everything")
        );
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantFrom(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s from " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s from").buildMacro(advancement.getIdentifier(), true)
            );
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantThrough(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s through " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s through").buildMacro(advancement.getIdentifier(), true)
            );
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantUntil(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s until " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s until").buildMacro(advancement.getIdentifier(), true)
            );
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revoke(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s only " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s only").buildMacro(advancement.getIdentifier(), true)
            );
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIRegister(caller = "Player", returnType = "CommandReturn")
    public static void revokeAll(PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        var commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                new Command("advancement revoke @s everything")
        );
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeFrom(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s from " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s from").buildMacro(advancement.getIdentifier(), true)
            );
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeThrough(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s through " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s through").buildMacro(advancement.getIdentifier(), true)
            );
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeUntil(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s until " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s until").buildMacro(advancement.getIdentifier(), true)
            );
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    @MNIRegister(normalParams = {"string key, float scale"}, caller = "Player", returnType = "int")
    public static void getAttribute(MCString id, MCFloat scale, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue){
        Command[] commands;
        if(id instanceof MCStringConcrete idC){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("attribute @s " + idC.getValue().getValue() + " get")
            );
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("attribute @s").buildMacro(id.getIdentifier(), true).build("get", false)
            );
        }
        if(scale instanceof MCFloatConcrete scaleC){
            commands[commands.length - 1] = commands[commands.length - 1].build(scaleC.getValue().toString(), true);
        }else {
            commands[commands.length - 1] = commands[commands.length - 1].buildMacro(scale.getIdentifier(), true);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }
}
