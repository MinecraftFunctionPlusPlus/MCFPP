package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIAccessor;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMutator;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.obj.EnumVar;
import top.mcfpp.core.lang.obj.ObjectVar;
import top.mcfpp.core.lang.obj.TypeDataTemplateObject;
import top.mcfpp.lib.SbObject;
import top.mcfpp.model.compound.TypeDataTemplate;
import top.mcfpp.model.scope.GlobalScope;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

public class WorldObjectData {

    private static TypeDataTemplate Time;

    private static TypeDataTemplate getTime(){
        if(Time == null){
            Time = (TypeDataTemplate) GlobalScope.getTemplate("mcfpp.minecraft", "Time");
        }
        return Time;
    }

    private static TypeDataTemplateObject newTime(){
        var qwq = (TypeDataTemplateObject) getTime().getType().build("return");
        qwq.setTemp(true);
        return qwq;
    }

    @MNIFunction(normalParams = "Difficulty", returnType = "CommandReturn")
    public static void setDifficulty(EnumVar difficulty, ValueWrapper<CommandReturn> re) {
        var command = Command.Companion.buildAll("difficulty", difficulty);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIAccessor("time")
    public static void getTime(ObjectVar caller, ValueWrapper<TypeDataTemplateObject> re){
        var t = new MCInt("time");
        t.setSbObject(SbObject.Companion.getMCFPP_TEMP());
        Function.addCommand("execute store result score time " + SbObject.Companion.getMCFPP_TEMP() + " run time query day");
        var qwq = newTime();
        qwq.setDelegateVar(qwq.getDelegateVar().assignedBy(t));
        re.setValue(qwq);
    }

    @MNIMutator("time")
    public static void setTime(TypeDataTemplateObject time, ObjectVar caller){
        Commands.processMacroCommand(Command.Companion.buildAll("time set", time));
    }

    @MNIMutator("weather")
    public static void setWeather(EnumVar weather, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("weather", weather);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Weather", "int"}, returnType = "CommandReturn")
    public static void setWeather(EnumVar weather, MCInt duration, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("weather", weather, duration);
        Commands.processMacroCommandReturn(re, command);
    }



}
