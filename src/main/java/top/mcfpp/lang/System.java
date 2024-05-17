package top.mcfpp.lang;

import kotlin.jvm.functions.Function4;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.lib.function.Function;
import top.mcfpp.lib.function.MNIMethodContainer;
import top.mcfpp.util.ValueWrapper;

import java.util.HashMap;

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
            else if (value instanceof JsonString) print((JsonString) value);
            else if (value instanceof MCFloat) print((MCFloat) value);
            else if (value instanceof MCString) print((MCString) value);
            else print(value);
            return null;
        });
        methods.put("typeof", (vars, vars2, canSelectMember, varValueWrapper) -> {
            //获取变量
            var value = vars2[0];
            var re = new MCFPPTypeVar(value.getType());
            varValueWrapper.setValue(re);
            return null;
        });
    }

    @InsertCommand
    public static void print(@NotNull MCInt var) {
        if (var.isConcrete()) {
            //是确定的，直接输出数值
            Function.Companion.addCommand("tellraw @a \"" + var.getJavaValue() + "\"");
        }else {
            Function.Companion.addCommand("tellraw @a " + new JsonTextNumber(var).toJson());
        }
    }

    @InsertCommand
    public static void print(@NotNull MCFloat var) {
        if (var.isConcrete()) {
            //是确定的，直接输出数值
            Function.Companion.addCommand("tellraw @a " + "\"" + var.getJavaValue() + "\"");
        }else {
            Function.Companion.addCommand("tellraw @a " + "\"" + var + "\"");
        }
    }

    @InsertCommand
    public static void print(JsonString var){
        Function.Companion.addCommand("tellraw @a " + var.getJsonText().toJson());
    }

    @InsertCommand
    public static void print(@NotNull Var<?> var){
        Function.Companion.addCommand("tellraw @a " + "\"" +var + "\"");
    }

    @InsertCommand
    public static void print(@NotNull MCString var){
        if (var.isConcrete()) {
            var javaValue = var.getJavaValue();
            var text = "null";
            if (javaValue != null) {
                text = javaValue.getValue();
            }

            Function.Companion.addCommand("tellraw @a " + "\"" + text + "\"");
        } else {
            Function.Companion.addCommand("tellraw @a " + "\"" + var.toString().replaceAll("\"", "\\\\\"") + "\"");
        }
    }
}
