package top.mcfpp.lang;

import kotlin.NotImplementedError;
import kotlin.jvm.functions.Function4;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MCFPPNative;
import top.mcfpp.lib.function.Function;
import top.mcfpp.lib.function.MNIMethodContainer;
import top.mcfpp.util.ValueWrapper;

import java.util.HashMap;

public class MCIntData extends MNIMethodContainer {
    static HashMap<String, Function4<Var<?>[], Var<?>[], CanSelectMember, ValueWrapper<Var<?>>, java.lang.Void>> methods;

    static {
        methods = new HashMap<>();
        methods.put("test", (vars, vars2, canSelectMember, varValueWrapper) -> {
            //不会有参数
            Function.Companion.addCommand("say " + canSelectMember);
            return null;
        });

        methods.put("qwq", (vars, vars2, canSelectMember, varValueWrapper) -> {
            return null;
        });

    }

    @NotNull
    @Override
    public Function4<Var<?>[], Var<?>[], CanSelectMember, ValueWrapper<Var<?>>, java.lang.Void> getMNIMethod(@NotNull String name) {
        return methods.get(name);
    }
}
