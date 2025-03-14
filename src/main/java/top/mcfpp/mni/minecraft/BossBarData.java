package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIAccessor;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMutator;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.MCStringConcrete;
import top.mcfpp.core.lang.entity.PlayerVar;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.FunctionUtil;
import top.mcfpp.util.ValueWrapper;

import java.util.Objects;

@SuppressWarnings("DataFlowIssue")
public class BossBarData {

    @MNIFunction(caller = "BossBar", returnType = "CommandReturn")
    public static void add(DataTemplateObject bossbar, ValueWrapper<CommandReturn> returnValue){
        var command = new Command("bossbar add");
        var id = bossbar.getMemberVarWithT("id", MCString.class);
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id, true);
        }
        var name = bossbar.getMemberVarWithT("name", JsonText.class);
        command.build(name.toCommandPart(), true);
        Commands.INSTANCE.method3(returnValue, command);
    }

    @MNIFunction(caller = "BossBar", returnType = "CommandReturn")
    public static void remove(DataTemplateObject bossbar, ValueWrapper<CommandReturn> returnValue){
        var command = new Command("bossbar remove");
        var id = bossbar.getMemberVarWithT("id", MCString.class);
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id, true);
        }
        Commands.INSTANCE.method3(returnValue, command);
    }

    @MNIFunction(caller = "BossBar", isObject = true, returnType = "CommandReturn")
    public static void list(DataTemplateObject bossbar, ValueWrapper<CommandReturn> returnValue){
        var command = new Command("bossbar list");
        returnValue.setValue(new CommandReturn(command,"bossbar_list"));
        Function.Companion.addCommand(command);
    }

    @MNIAccessor(name = "max")
    public static void getMax(DataTemplateObject bossbar, ValueWrapper<MCInt> returnValue){
        var id = bossbar.getMemberVarWithT("id", MCString.class);
        var command = new Command("execute store result score")
                .build(returnValue.getValue().nbtPath.toCommandPart(), true)
                .build("run bossbar get", true);
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id, true);
        }
        command.build("max", true);
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction();
            Function.Companion.addCommands(marcoCall);
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
                command.buildMacro(id, true);
            }
            command.build("max", true);
            command.build(valueC.getValue().toString(), true);
        }else {
            command = new Command("execute store result bossbar");
            if(id instanceof MCStringConcrete idC){
                command.build(idC.getValue().getValue(), true);
            }else {
                command.buildMacro(id, true);
            }
            command.build("max run", true);
            command.build(Commands.INSTANCE.sbPlayerGet(value), true);
        }
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction();
            Function.Companion.addCommands(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }

    @MNIAccessor(name = "value")
    public static void getValue(DataTemplateObject bossbar, ValueWrapper<MCInt> returnValue){
        var id = bossbar.getMemberVarWithT("id", MCString.class);
        var command = new Command("execute store result score")
                .build(returnValue.getValue().nbtPath.toCommandPart(), true)
                .build("run bossbar get", true);
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id, true);
        }
        command.build("value", true);
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction();
            Function.Companion.addCommands(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }

    @MNIMutator(name = "value")
    public static void setValue(DataTemplateObject bossbar, MCInt value){
        var id = bossbar.getMemberVarWithT("id", MCString.class);
        Command command;
        if(value instanceof MCIntConcrete valueC){
            command = new Command("bossbar set");
            if(id instanceof MCStringConcrete idC){
                command.build(idC.getValue().getValue(), true);
            }else {
                command.buildMacro(id, true);
            }
            command.build("value", true);
            command.build(valueC.getValue().toString(), true);
        }else {
            command = new Command("execute store result bossbar");
            if(id instanceof MCStringConcrete idC){
                command.build(idC.getValue().getValue(), true);
            }else {
                command.buildMacro(id, true);
            }
            command.build("value run", true);
            command.build(Commands.INSTANCE.sbPlayerGet(value), true);
        }
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction();
            Function.Companion.addCommands(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }

    @MNIAccessor(name = "visible")
    public static void getVisible(DataTemplateObject bossbar, ValueWrapper<ScoreBool> returnValue){
        var id = bossbar.getMemberVarWithT("id", MCString.class);
        var command = new Command("execute store result score")
                .build(returnValue.getValue().nbtPath.toCommandPart(), true)
                .build("run bossbar get", true);
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id, true);
        }
        command.build("visible", true);
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction();
            Function.Companion.addCommands(marcoCall);
        }else {
            Function.Companion.addCommand(command);
        }
    }

    @MNIMutator(name = "visible")
    public static void setVisible(DataTemplateObject bossbar, ScoreBool value){
        var id = bossbar.getMemberVarWithT("id", MCString.class);
        Command command;
        if(value instanceof ScoreBoolConcrete valueC){
            command = new Command("bossbar set");
            if(id instanceof MCStringConcrete idC){
                command.build(idC.getValue().getValue(), true);
            }else {
                command.buildMacro(id, true);
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
                            .buildMacro(id, true)
                            .build("visible true", true);
                    Function.Companion.addCommand(command1);
                    var command2 = new Command("bossbar set ")
                            .buildMacro(id, true)
                            .build("visible false", true);
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

    @MNIFunction(normalParams = "BossBarColor color", caller = "BossBar", returnType = "CommandReturn")
    public static void setColor(EnumVar color, DataTemplateObject caller, ValueWrapper<CommandReturn> re){
        var id = caller.getMemberVarWithT("id", MCString.class);
        Command command = new Command("bossbar set");
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id, true);
        }
        command.build("color", true);
        if(color instanceof EnumVarConcrete colorC){
            command.build(colorC.getValue().dataAsString(), true);
        }else {
            command.build("","color", true);
        }
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction();
            Function.Companion.addCommands(marcoCall);
            re.setValue(new CommandReturn(marcoCall[marcoCall.length - 1],"bossbar_set_color"));
        }else {
            var f = FunctionUtil.INSTANCE.enumWalk(color, command, "color");
            Function.Companion.addCommand(Commands.INSTANCE.function(f));
            re.setValue(new CommandReturn(Commands.INSTANCE.function(f),"bossbar_set_color"));
        }
    }

    @MNIFunction(normalParams = "text name", caller = "BossBar", returnType = "CommandReturn")
    public static void setName(JsonText name, DataTemplateObject caller, ValueWrapper<CommandReturn> re){
        var id = caller.getMemberVarWithT("id", MCString.class);
        Command command = new Command("bossbar set");
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id, true);
        }
        command.build("name", true);
        command.build(name.toCommandPart(), true);
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction();
            Function.Companion.addCommands(marcoCall);
            re.setValue(new CommandReturn(marcoCall[marcoCall.length - 1],"bossbar_set_name"));
        }else {
            Function.Companion.addCommand(command);
            re.setValue(new CommandReturn(command,"bossbar_set_name"));
        }
    }

    @MNIFunction(normalParams = "Player players", caller = "BossBar", returnType = "CommandReturn")
    public static void setVisiblePlayers(PlayerVar players, DataTemplateObject bossbar, ValueWrapper<CommandReturn> returnValue) {
        var id = bossbar.getMemberVarWithT("id", MCString.class);
        Command command = new Command("bossbar set");
        if (id instanceof MCStringConcrete idC) {
            command.build(idC.getValue().getValue());
        } else {
            command.buildMacro(id);
        }
        command.build("players");
        command.build(players.toCommandPart());
        Commands.INSTANCE.method3(returnValue, command);
    }

    @MNIFunction(normalParams = "BossBarStyle style", caller = "BossBar", returnType = "CommandReturn")
    public static void setStyle(EnumVar style, DataTemplateObject caller, ValueWrapper<CommandReturn> re){
        var id = caller.getMemberVarWithT("id", MCString.class);
        Command command = new Command("bossbar set");
        if(id instanceof MCStringConcrete idC){
            command.build(idC.getValue().getValue(), true);
        }else {
            command.buildMacro(id, true);
        }
        command.build("style", true);
        if(style instanceof EnumVarConcrete styleC){
            command.build(styleC.getValue().dataAsString(), true);
        }else {
            command.build("","style", true);
        }
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction();
            Function.Companion.addCommands(marcoCall);
            re.setValue(new CommandReturn(marcoCall[marcoCall.length - 1],"bossbar_set_style"));
        }else {
            var f = FunctionUtil.INSTANCE.enumWalk(style, command, "style");
            Function.Companion.addCommand(Commands.INSTANCE.function(f));
            re.setValue(new CommandReturn(Commands.INSTANCE.function(f),"bossbar_set_style"));
        }
    }
}
