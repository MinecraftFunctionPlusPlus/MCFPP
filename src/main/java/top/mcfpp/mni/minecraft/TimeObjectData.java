package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.MCIntConcrete;
import top.mcfpp.core.lang.obj.TypeDataTemplateObject;
import top.mcfpp.model.compound.TypeDataTemplate;
import top.mcfpp.model.scope.GlobalScope;
import top.mcfpp.util.ValueWrapper;

public class TimeObjectData {

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

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void tick(MCInt tick, ValueWrapper<TypeDataTemplateObject> returnValue) {
        var qwq = newTime();
        qwq.setDelegateVar(qwq.getDelegateVar().assignedBy(tick));
        returnValue.setValue(qwq);
    }

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void second(MCInt second, ValueWrapper<TypeDataTemplateObject> returnValue) {
        var qwq = newTime();
        qwq.setDelegateVar(qwq.getDelegateVar().assignedBy(second.times(new MCIntConcrete(20, "temp"))));
        returnValue.setValue(qwq);
    }

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void min(MCInt day, ValueWrapper<TypeDataTemplateObject> returnValue) {
        var qwq = newTime();
        qwq.setDelegateVar(qwq.getDelegateVar().assignedBy(day.times(new MCIntConcrete(1200, "temp"))));
        returnValue.setValue(qwq);
    }

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void hour(MCInt day, ValueWrapper<TypeDataTemplateObject> returnValue) {
        var qwq = newTime();
        qwq.setDelegateVar(qwq.getDelegateVar().assignedBy(day.times(new MCIntConcrete(72000, "temp"))));
        returnValue.setValue(qwq);
    }

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void day(MCInt day, ValueWrapper<TypeDataTemplateObject> returnValue) {
        var qwq = newTime();
        qwq.setDelegateVar(qwq.getDelegateVar().assignedBy(day.times(new MCIntConcrete(144000, "temp"))));
        returnValue.setValue(qwq);
    }

    @MNIFunction(normalParams = "int", returnType = "Time")
    public static void gameDay(MCInt gameDay, ValueWrapper<TypeDataTemplateObject> returnValue) {
        var qwq = newTime();
        qwq.setDelegateVar(qwq.getDelegateVar().assignedBy(gameDay.times(new MCIntConcrete(24000, "temp"))));
        returnValue.setValue(qwq);
    }

}
