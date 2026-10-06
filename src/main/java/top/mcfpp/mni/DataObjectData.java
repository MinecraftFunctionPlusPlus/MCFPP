package top.mcfpp.mni;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeTextOperations;
import top.mcfpp.core.lang.JavaVar;
import top.mcfpp.core.lang.Var;
import top.mcfpp.util.ValueWrapper;

public class DataObjectData {

    @MNIFunction(caller = "DataObject", returnType = "text")
    public static void toText(NativeCallContext context) {
        NativeTextOperations.INSTANCE.template(context);
    }

    @MNIFunction(caller = "DataObject", returnType = "JavaVar")
    public static void toCommandPart(@NotNull Var<?> caller, ValueWrapper<JavaVar> returnValue){
        returnValue.setValue(new JavaVar(caller.toCommandPart(), "command"));
    }
}
