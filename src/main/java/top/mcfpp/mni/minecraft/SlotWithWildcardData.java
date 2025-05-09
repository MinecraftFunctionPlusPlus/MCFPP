package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.core.lang.JavaVar;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.obj.EnumVar;
import top.mcfpp.util.ValueWrapper;

public class SlotWithWildcardData {

    @MNIFunction(caller = "SlotWithWildcard", returnType = "JavaVar", override = true)
    public static void toCommandPart(DataTemplateObject caller, ValueWrapper<JavaVar> returnValue){
        var index = caller.getMemberVarWithT("index", MCInt.class);
        var type = caller.getMemberVarWithT("type", EnumVar.class);
        Command command = new Command()
                .build(type.toCommandPart(), false)
                .build(".*",false);
        returnValue.get().setValue(command);
    }
}
