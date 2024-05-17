package top.mcfpp.lang;

import kotlin.jvm.functions.Function4;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.model.function.Function;
import top.mcfpp.model.function.MNIMethodContainer;
import top.mcfpp.util.NBTUtil;
import top.mcfpp.util.ValueWrapper;

import java.util.HashMap;
import java.util.UUID;

public class System extends MNIMethodContainer {

    @NotNull
    @Override
    public Function4<Var<?>[], Var<?>[], CanSelectMember, ValueWrapper<Var<?>>, java.lang.Void> getMNIMethod(@NotNull String name) {
        return methods.get(name);
    }

    static HashMap<String, Function4<Var<?>[], Var<?>[], CanSelectMember, ValueWrapper<Var<?>>, java.lang.Void>> methods;

    static {
        methods = new HashMap<>();
        methods.put("print", (vars, vars2, canSelectMember, varValueWrapper) -> {
            var value = vars2[0];
            //只会有一个参数哦
            if (value instanceof MCInt) print((MCInt) value);
            else if (value instanceof MCString) print((MCString) value);
            //else if (value instanceof JsonString) print((JsonString) value);
            else if (value instanceof NBTBasedData<?>) print((NBTBasedData<?>) value);
            else print(value);
            return null;
        });
        methods.put("typeof", (vars, vars2, canSelectMember, varValueWrapper) -> {
            //获取变量
            var value = vars2[0];
            var re = new MCFPPTypeVar(value.getType(), UUID.randomUUID().toString());
            varValueWrapper.setValue(re);
            return null;
        });
    }

    @InsertCommand
    public static void print(@NotNull MCInt var) {
        if (var instanceof MCIntConcrete varC) {
            //是确定的，直接输出数值
            Function.Companion.addCommand("tellraw @a " + varC.getValue());
        }else {
            Function.Companion.addCommand("tellraw @a " + new JsonTextNumber(var).toJson());
        }
    }

    //@InsertCommand
    //public static void print(JsonString var){
    //    Function.Companion.addCommand("tellraw @a " + var.getJsonText().toJson());
    //}

    @InsertCommand
    public static void print(@NotNull Var<?> var){
        Function.Companion.addCommand("tellraw @a " + "\"" +var + "\"");
    }

    @InsertCommand
    public static void print(@NotNull NBTBasedData<?> var){
        if(var instanceof NBTBasedDataConcrete<?> varC){
            Function.Companion.addCommand("tellraw @a " + NBTUtil.INSTANCE.toJava(varC.getValue()));
        }else {
            //TODO
        }
    }

    @InsertCommand
    public static void print(@NotNull MCString var) {
        if(var instanceof MCStringConcrete varC){
            Function.Companion.addCommand("tellraw @a \"" + varC.getValue().getValue() + "\"");
        }else{
            //TODO
        }
    }
}
