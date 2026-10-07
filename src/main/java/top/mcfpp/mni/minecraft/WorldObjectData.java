package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIAccessor;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMutator;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.obj.StaticMemberView;
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

    @MNIFunction(normalParams = "Difficulty", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setDifficulty(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.worldSetDifficulty(context);
    }

    @MNIAccessor("time")
    public static void getTime(StaticMemberView caller, ValueWrapper<TypeDataTemplateObject> re){
        var t = new MCInt("time");
        t.setSbObject(SbObject.Companion.getMCFPP_TEMP());
        Function.addCommand("execute store result score time " + SbObject.Companion.getMCFPP_TEMP() + " run time query day");
        var qwq = newTime();
        qwq.setDelegateVar(qwq.getDelegateVar().assignedBy(t));
        re.setValue(qwq);
    }

    @MNIMutator("time")
    public static void setTime(TypeDataTemplateObject time, StaticMemberView caller){
        Commands.processMacroCommand(Command.Companion.buildAll("time set", time));
    }

    @MNIFunction(normalParams = {"Weather", "int"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setWeather(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldSetWeather(context);
    }



}
