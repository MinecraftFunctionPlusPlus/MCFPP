package top.mcfpp.lang;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.annotations.MCFPPNative;
import top.mcfpp.lang.*;
import top.mcfpp.lib.Function;

import java.util.Random;

public class System {

    @MCFPPNative
    public static void print(@NotNull Var[] vars, CanSelectMember caller) {
        //只会有一个参数哦
        Var var = vars[0];
        if (var instanceof MCInt) print((MCInt) var);
        else if (var instanceof JsonString) print((JsonString) var);
        else print(var);
    }

    @InsertCommand
    public static void print(@NotNull MCInt var) {
        if (var.isConcrete()) {
            //是确定的，直接输出数值
            Function.Companion.addCommand("tellraw @a " + var.getValue());
        }else {
            Function.Companion.addCommand("tellraw @a " + new JsonTextNumber(var).toJson());
        }
    }

    @InsertCommand
    public static void print(JsonString var){
        Function.Companion.addCommand("tellraw @a " + var.getJsonText().toJson());
    }

    @InsertCommand
    public static void print(@NotNull Var var){
        Function.Companion.addCommand("tellraw @a " + "\"" +var + "\"");
    }

}
