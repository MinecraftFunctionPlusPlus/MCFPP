package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.lang.CommandReturn;
import top.mcfpp.lang.resource.Advancement;
import top.mcfpp.lang.resource.AdvancementConcrete;
import top.mcfpp.minecraft.PlayerVar;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

public class PlayerEntityData {

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grant(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(advancement instanceof AdvancementConcrete){
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s only " + ((AdvancementConcrete) advancement).getValue())
            ));
        }else {
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s only").buildMacro(advancement.getIdentifier(), true)
            ));
        }
    }

    @MNIRegister(caller = "Player")
    public static void grantAll(PlayerVar.PlayerEntityVar caller) {
        Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                new Command("advancement grant @s everything")
        ));
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantFrom(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(advancement instanceof AdvancementConcrete){
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s from " + ((AdvancementConcrete) advancement).getValue())
            ));
        }else {
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s from").buildMacro(advancement.getIdentifier(), true)
            ));
        }
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantThrough(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(advancement instanceof AdvancementConcrete){
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s through " + ((AdvancementConcrete) advancement).getValue())
            ));
        }else {
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s through").buildMacro(advancement.getIdentifier(), true)
            ));
        }
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantUntil(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(advancement instanceof AdvancementConcrete){
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s until " + ((AdvancementConcrete) advancement).getValue())
            ));
        }else {
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement grant @s until").buildMacro(advancement.getIdentifier(), true)
            ));
        }
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revoke(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(advancement instanceof AdvancementConcrete){
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s only " + ((AdvancementConcrete) advancement).getValue())
            ));
        }else {
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s only").buildMacro(advancement.getIdentifier(), true)
            ));
        }
    }

    @MNIRegister(caller = "Player")
    public static void revokeAll(PlayerVar.PlayerEntityVar caller) {
        Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                new Command("advancement revoke @s everything")
        ));
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeFrom(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(advancement instanceof AdvancementConcrete){
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s from " + ((AdvancementConcrete) advancement).getValue())
            ));
        }else {
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s from").buildMacro(advancement.getIdentifier(), true)
            ));
        }
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeThrough(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(advancement instanceof AdvancementConcrete){
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s through " + ((AdvancementConcrete) advancement).getValue())
            ));
        }else {
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s through").buildMacro(advancement.getIdentifier(), true)
            ));
        }
    }

    @MNIRegister(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeUntil(Advancement advancement, PlayerVar.PlayerEntityVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(advancement instanceof AdvancementConcrete){
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s until " + ((AdvancementConcrete) advancement).getValue())
            ));
        }else {
            Function.Companion.addCommands(Commands.INSTANCE.runAsEntity(caller.getEntity(),
                    new Command("advancement revoke @s until").buildMacro(advancement.getIdentifier(), true)
            ));
        }
    }
}
