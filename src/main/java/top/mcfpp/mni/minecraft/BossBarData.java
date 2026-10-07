package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIAccessor;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMutator;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.MCStringConcrete;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

import java.util.Objects;

@SuppressWarnings("DataFlowIssue")
public class BossBarData {

    @MNIFunction(caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void add(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarAdd(context);
    }

    @MNIFunction(caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void remove(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarRemove(context);
    }

    @MNIFunction(caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void listAll(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarList(context);
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
            Function.addCommands(marcoCall);
        }else {
            Function.addCommand(command);
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
            Function.addCommands(marcoCall);
        }else {
            Function.addCommand(command);
        }
    }
    @MNIAccessor(value = "max")
    public static void getMax(DataTemplateObject bossbar, ValueWrapper<MCInt> returnValue){
        getIntAttr("max", bossbar, returnValue);
    }

    @MNIMutator(value = "max")
    public static void setMax(DataTemplateObject bossbar, MCInt value){
        setIntAttr("max", bossbar, value);
    }

    @MNIAccessor(value = "value")
    public static void getValue(DataTemplateObject bossbar, ValueWrapper<MCInt> returnValue){
        getIntAttr("value", bossbar, returnValue);
    }

    @MNIMutator(value = "value")
    public static void setValue(DataTemplateObject bossbar, MCInt value){
        setIntAttr("value", bossbar, value);
    }

    @MNIAccessor(value = "visible")
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
            Function.addCommands(marcoCall);
        }else {
            Function.addCommand(command);
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
                    Function.addCommand(command1);
                    var command2 = new Command("bossbar set " + idC.getValue().getValue() + " visible false");
                    Function.addCommand(command2);
                    return null;
                }).getFirst();
            }else {
                command = Commands.tempFunction(Function.Companion.getCurrFunction(), (f) -> {
                    var command1 = new Command("execute if score " + value.getIdentifier() + " " + value.getBoolObject().getName() + " matches 1 run return run")
                            .build("bossbar set ").buildMacro(id).build("visible true");
                    Function.addCommand(command1);
                    var command2 = new Command("bossbar set ")
                            .buildMacro(id)
                            .build("visible false");
                    Function.addCommand(command2);
                    return null;
                }).getFirst();
                command.build("with",true).build(bossbar.nbtPath.parent().toCommandPart(), true);
            }
        }
        if(command.isMacro()){
            var marcoCall = command.buildMacroFunction(Objects.requireNonNull(bossbar.nbtPath.parent()));
            Function.addCommand(marcoCall);
        }else {
            Function.addCommand(command);
        }
    }

    @MNIFunction(normalParams = "BossBarColor", caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setColor(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarSetColor(context);
    }

    @MNIFunction(normalParams = "text", caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setName(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarSetName(context);
    }

    @MNIFunction(normalParams = "entity", caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setVisiblePlayers(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.bossbarSetVisiblePlayers(context);
    }

    @MNIFunction(normalParams = "BossBarStyle", caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setStyle(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.bossbarSetStyle(context);
    }
}
