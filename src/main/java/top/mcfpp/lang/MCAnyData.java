package top.mcfpp.lang;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MCFPPNative;
import top.mcfpp.lib.function.Function;
import top.mcfpp.util.ValueWrapper;

public class MCAnyData {
    @MCFPPNative
    public static void toString(@NotNull Var<?>[] vars, CanSelectMember caller, ValueWrapper<Var> output) {
        //不会有参数
        Function.Companion.addCommand("tellraw @a " + caller);
    }
}
