package top.mcfpp.lang;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.Project;
import top.mcfpp.lib.Function;

public class MCIntFunction {
    public static void test(@NotNull Var[] vars, Var caller) {
        //不会有参数
        Function.Companion.addCommand("say " + caller);
    }
}
