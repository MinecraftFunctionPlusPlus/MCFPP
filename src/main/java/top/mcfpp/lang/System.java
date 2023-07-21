package top.mcfpp.lang;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.lang.*;
import top.mcfpp.lib.Function;

public class System {
    public static void print(@NotNull Var[] vars, ClassPointer cls) {
        //只会有一个参数哦
        Var var = vars[0];
        if (var instanceof MCInt) print((MCInt) var);
        else print(var);
    }

    public static void print(@NotNull MCInt var) {
        if (var.isConcrete()) {
            Function.Companion.addCommand("tellraw @a " + var.getValue());
        }else {
            Function.Companion.addCommand("tellraw @a " + var);
        }
    }

    public static void print(@NotNull Var var){
        Function.Companion.addCommand("tellraw @a " + var);
    }

    public static void test(Var[] vars,  ClassPointer cls){
        java.lang.System.out.println("qwq");
    }
}
