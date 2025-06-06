package top.mcfpp.mni;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.JavaVar;
import top.mcfpp.core.lang.JsonTextConcrete;
import top.mcfpp.core.lang.Null;
import top.mcfpp.core.lang.Var;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.util.TempPool;
import top.mcfpp.util.ValueWrapper;

public class MCAnyData {
    @MNIFunction(caller = "any", returnType = "JavaVar")
    public static void getJavaVar(@NotNull Var<?> caller, ValueWrapper<Var<?>> returnValue){
        var re = new JavaVar(caller, "temp_" + TempPool.getVarIdentify());
        returnValue.setValue(re);
    }

    @MNIFunction(caller = "any", returnType = "text")
    public static void toText(@NotNull Var<?> caller, ValueWrapper<JsonTextConcrete> returnValue){
        var l = new ListChatComponent();
        l.getComponents().add(new PlainChatComponent(caller.toString()));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }

    @MNIFunction(caller = "any", returnType = "nbt")
    public static void getDefault(@NotNull Var<?> caller, ValueWrapper<NBTBasedDataConcrete> returnValue){
        var value = caller.getType().defaultValueVar();
        returnValue.setValue((NBTBasedDataConcrete) value);
    }

    @MNIOperator(operator = "==", paramType = "null", returnType = "bool")
    public static void equalNull(@NotNull Var<?> caller, Null nu, ValueWrapper<Var<?>> returnValue){
        returnValue.setValue(new ScoreBoolConcrete(caller == Null.INSTANCE || !caller.getHasAssigned(), "re"));
    }
}
