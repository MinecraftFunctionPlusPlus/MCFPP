package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.JavaVar;
import top.mcfpp.core.lang.Pos3Var;
import top.mcfpp.util.ValueWrapper;

public class AreaData {

    @MNIFunction(caller = "Area", returnType = "JavaVar", override = true)
    public static void toCommandPart(DataTemplateObject caller, ValueWrapper<JavaVar> returnValue){
        var start = caller.getMemberVarWithT("start", Pos3Var.class);
        var end = caller.getMemberVarWithT("end", Pos3Var.class);
        returnValue.setValue(new JavaVar(Command.Companion.buildAll(start, end), "command"));
    }

}
