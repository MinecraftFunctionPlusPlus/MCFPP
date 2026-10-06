package top.mcfpp.mni;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.JavaVar;
import top.mcfpp.core.lang.Var;
import top.mcfpp.util.TempPool;
import top.mcfpp.util.ValueWrapper;

public class MCAnyConcreteData {

    @MNIFunction(caller = "any", returnType = "JavaVar")
    public static void getJavaVar(@NotNull Var<?> value, ValueWrapper<Var<?>> returnValue){
        var re = new JavaVar(value, TempPool.getVarIdentify());
        returnValue.setValue(re);
    }

    @MNIFunction(caller = "any", returnType = "text")
    public static void toText(NativeCallContext context) {
        top.mcfpp.backend.NativeTextOperations.INSTANCE.representation(context);
    }
}
