package top.mcfpp.sys;

import top.mcfpp.lang.*;
import top.mcfpp.lib.Function;

public class System {
    public static void print(Var[] vars,  ClassPointer cls) {
        //只会有一个参数哦
        Var var = vars[0];
        if (var instanceof MCInt) print((MCInt) var);
    }

    public static void print(MCInt var) {
        if (var.isConcrete()) {
            Function.Companion.addCommand("tellraw @a " + var.getValue());
        }
    }

    public static void test(Var[] vars,  ClassPointer cls){
        java.lang.System.out.println("qwq");
    }
}
