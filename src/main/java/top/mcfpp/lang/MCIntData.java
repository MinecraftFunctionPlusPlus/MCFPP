package top.mcfpp.lang;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MCFPPNative;
import top.mcfpp.lib.Function;

public class MCIntData {

    @MCFPPNative
    public static void test(@NotNull Var[] vars, CanSelectMember caller) {
        //不会有参数
        Function.Companion.addCommand("say " + caller);
    }

    @MCFPPNative
    public static void qwq(){

    }
}
