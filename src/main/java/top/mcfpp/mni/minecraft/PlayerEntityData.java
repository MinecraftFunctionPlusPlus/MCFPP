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

@SuppressWarnings("unused")
public class PlayerEntityData extends EntityVarData {

    public static void grant(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s only " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            var c = new Command("advancement grant @s only").buildMacro(advancement, true).buildMacroFunction();
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(), c[c.length - 1]);
            var l = Arrays.asList(c).subList(0, c.length - 1);
            l.addAll(Arrays.asList(commands));
            commands = l.toArray(new Command[0]);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void grantAll(PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        var commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                new Command("advancement grant @s everything")
        );
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void grantFrom(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s from " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            var c = new Command("return run advancement grant @s from").buildMacro(advancement, true).buildMacroFunction();
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(), c[c.length - 1]);
            var l = Arrays.asList(c).subList(0, c.length - 1);
            l.addAll(Arrays.asList(commands));
            commands = l.toArray(new Command[0]);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void grantThrough(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s through " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            var c = new Command("return run advancement grant @s through").buildMacro(advancement, true).buildMacroFunction();
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(), c[c.length - 1]);
            var l = Arrays.asList(c).subList(0, c.length - 1);
            l.addAll(Arrays.asList(commands));
            commands = l.toArray(new Command[0]);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void grantUntil(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s until " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            var c = new Command("return run advancement grant @s until").buildMacro(advancement, true).buildMacroFunction();
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(), c[c.length - 1]);
            var l = Arrays.asList(c).subList(0, c.length - 1);
            l.addAll(Arrays.asList(commands));
            commands = l.toArray(new Command[0]);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void revoke(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s only " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            var c = new Command("advancement revoke @s only").buildMacro(advancement, true).buildMacroFunction();
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(), c[c.length - 1]);
            var l = Arrays.asList(c).subList(0, c.length - 1);
            l.addAll(Arrays.asList(commands));
            commands = l.toArray(new Command[0]);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void revokeAll(PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        var commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                new Command("advancement revoke @s everything")
        );
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void revokeFrom(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s from " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            var c = new Command("advancement revoke @s from").buildMacro(advancement, true).buildMacroFunction();
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(), c[c.length - 1]);
            var l = Arrays.asList(c).subList(0, c.length - 1);
            l.addAll(Arrays.asList(commands));
            commands = l.toArray(new Command[0]);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void revokeThrough(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s through " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            var c = new Command("advancement revoke @s through").buildMacro(advancement, true).buildMacroFunction();
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(), c[c.length - 1]);
            var l = Arrays.asList(c).subList(0, c.length - 1);
            l.addAll(Arrays.asList(commands));
            commands = l.toArray(new Command[0]);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void revokeUntil(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command[] commands;
        if(advancement instanceof AdvancementConcrete){
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s until " + ((AdvancementConcrete) advancement).getValue())
            );
        }else {
            var c = new Command("advancement revoke @s until").buildMacro(advancement, true).buildMacroFunction();
            commands = Commands.INSTANCE.runAsEntity(caller.getEntity(), c[c.length - 1]);
            var l = Arrays.asList(c).subList(0, c.length - 1);
            l.addAll(Arrays.asList(commands));
            commands = l.toArray(new Command[0]);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void getAttribute(MCString id, MCFloat scale, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue){
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

    public static void clear(PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var commands = Commands.INSTANCE.runAsEntity(caller.getEntity(),
                new Command("clear @s")
        );
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }
}
