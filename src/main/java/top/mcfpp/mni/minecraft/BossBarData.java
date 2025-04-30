package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIAccessor;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMutator;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.core.lang.entity.PlayerVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.MCStringConcrete;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.obj.EnumVar;
import top.mcfpp.core.lang.obj.EnumVarConcrete;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

import java.util.Objects;

@SuppressWarnings("DataFlowIssue")
public class BossBarData {

    @MNIFunction(caller = "BossBar", returnType = "CommandReturn")
    public static void add(DataTemplateObject bossbar, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll(
                "bossbar add",
                bossbar.getMemberVarWithT("id", MCString.class),
                bossbar.getMemberVarWithT("name", JsonText.class)
        );
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(caller = "BossBar", returnType = "CommandReturn")
    public static void remove(DataTemplateObject bossbar, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll(
                "bossbar remove",
                bossbar.getMemberVarWithT("id", MCString.class)
        );
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(caller = "BossBar", returnType = "CommandReturn")
    public static void list(DataTemplateObject bossbar, ValueWrapper<CommandReturn> returnValue){
        var command = new Command("bossbar list");
        returnValue.setValue(new CommandReturn(command,"bossbar_list"));
        Function.Companion.addCommand(command);
    }

    private static void getIntAttr(String attrID, DataTemplateObject bossbar, ValueWrapper<MCInt> returnValue){
        var command = Command.Companion.buildAll(
                "execute store result score",
                returnValue.getValue().nbtPath.toCommandPart(),
                "run bossbar get",
                bossbar.getMemberVarWithT("id", MCString.class),
                attrID
        );
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction();
            Function.Companion.addCommands(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }

    private static void setIntAttr(String attrID, DataTemplateObject bossbar, MCInt value){
        Command command;
        var id = bossbar.getMemberVarWithT("id", MCString.class);
        if(value instanceof MCIntConcrete valueC){
            command = Command.Companion.buildAll("bossbar set", id, attrID, valueC.getValue().toString());
        }else {
            command = Command.Companion.buildAll(
                    "execute store result bossbar", id, attrID, "run",
                    Commands.sbPlayerGet(value)
            );
        }
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction();
            Function.Companion.addCommands(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }
    @MNIAccessor(name = "max")
    public static void getMax(DataTemplateObject bossbar, ValueWrapper<MCInt> returnValue){
        getIntAttr("max", bossbar, returnValue);
    }

    @MNIMutator(value = "max")
    public static void setMax(DataTemplateObject bossbar, MCInt value){
        setIntAttr("max", bossbar, value);
    }

    @MNIAccessor(name = "value")
    public static void getValue(DataTemplateObject bossbar, ValueWrapper<MCInt> returnValue){
        getIntAttr("value", bossbar, returnValue);
    }

    @MNIMutator(value = "value")
    public static void setValue(DataTemplateObject bossbar, MCInt value){
        setIntAttr("value", bossbar, value);
    }

    @MNIAccessor(name = "visible")
    public static void getVisible(DataTemplateObject bossbar, ValueWrapper<ScoreBool> returnValue){
        var command = Command.Companion.buildAll(
                "execute store result score",
                returnValue.getValue().nbtPath.toCommandPart(),
                "run bossbar get",
                bossbar.getMemberVarWithT("id", MCString.class),
                "visible"
        );
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction();
            Function.Companion.addCommands(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }

    @MNIMutator(value = "visible")
    public static void setVisible(DataTemplateObject bossbar, ScoreBool value){
        var id = bossbar.getMemberVarWithT("id", MCString.class);
        Command command;
        if(value instanceof ScoreBoolConcrete valueC){
            command = new Command("bossbar set");
            if(id instanceof MCStringConcrete idC){
                command.build(idC.getValue().getValue());
            }else {
                command.buildMacro(id);
            }
            command.build("visible");
            command.build(valueC.getValue().toString());

        }else {
            if(id instanceof MCStringConcrete idC){
                command = Commands.tempFunction(Function.Companion.getCurrFunction(), (f) -> {
                    var command1 = new Command("execute if score " + value.getIdentifier() + " " + value.getBoolObject().getName() + " matches 1 run return run")
                            .build("bossbar set " + idC.getValue().getValue() + " visible true");
                    Function.Companion.addCommand(command1);
                    var command2 = new Command("bossbar set " + idC.getValue().getValue() + " visible false");
                    Function.Companion.addCommand(command2);
                    return null;
                }).getFirst();
            }else {
                command = Commands.tempFunction(Function.Companion.getCurrFunction(), (f) -> {
                    var command1 = new Command("execute if score " + value.getIdentifier() + " " + value.getBoolObject().getName() + " matches 1 run return run")
                            .build("bossbar set ").buildMacro(id).build("visible true");
                    Function.Companion.addCommand(command1);
                    var command2 = new Command("bossbar set ")
                            .buildMacro(id)
                            .build("visible false");
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

    @MNIFunction(normalParams = "BossBarColor", caller = "BossBar", returnType = "CommandReturn")
    public static void setColor(EnumVar color, DataTemplateObject caller, ValueWrapper<CommandReturn> re){
        var id = caller.getMemberVarWithT("id", MCString.class);
        Command command = Command.Companion.buildAll("bossbar set", id, "color");
        if(color instanceof EnumVarConcrete colorC){
            command.build(colorC.getValue().dataAsString(), true);
        }else {
            command.build("","color", true);
        }
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = "text", caller = "BossBar", returnType = "CommandReturn")
    public static void setName(JsonText name, DataTemplateObject caller, ValueWrapper<CommandReturn> re){
        var id = caller.getMemberVarWithT("id", MCString.class);
        Command command = Command.Companion.buildAll("bossbar set", id, "name", name);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = "Player", caller = "BossBar", returnType = "CommandReturn")
    public static void setVisiblePlayers(PlayerVar players, DataTemplateObject bossbar, ValueWrapper<CommandReturn> returnValue) {
        var id = bossbar.getMemberVarWithT("id", MCString.class);
        Command command = Command.Companion.buildAll("bossbar set", id, "players", players);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = "BossBarStyle", caller = "BossBar", returnType = "CommandReturn")
    public static void setStyle(EnumVar style, DataTemplateObject caller, ValueWrapper<CommandReturn> re){
        var id = caller.getMemberVarWithT("id", MCString.class);
        Command command = Command.Companion.buildAll("bossbar set", id, "style");
        if(style instanceof EnumVarConcrete styleC){
            command.build(styleC.getValue().dataAsString(), true);
        }else {
            command.build("","style", true);
        }
        Commands.processMacroCommandReturn(re, command);
    }
}
