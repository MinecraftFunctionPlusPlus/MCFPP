package top.mcfpp.mni;

import kotlin.jvm.functions.Function4;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.lang.CanSelectMember;
import top.mcfpp.lang.JavaVar;
import top.mcfpp.lang.Var;
import top.mcfpp.model.function.Function;
import top.mcfpp.model.function.MNIMethodContainer;
import top.mcfpp.util.ValueWrapper;

import java.util.HashMap;
import java.util.UUID;

public class MCAnyData extends MNIMethodContainer {

    static HashMap<String, Function4<Var<?>[], Var<?>[], CanSelectMember, ValueWrapper<Var<?>>, java.lang.Void>> methods;

    static {
        methods = new HashMap<>();
        methods.put("toString", (vars, vars2, canSelectMember, varValueWrapper) -> {
            //不会有参数
            Function.Companion.addCommand("tellraw @a " + canSelectMember);
            return null;
        });
        methods.put("getJavaVar", (r, n, caller, re) -> {
            //不会有参数
            re.setValue(new JavaVar(caller, UUID.randomUUID().toString()));
            return null;
        });
    }

    @NotNull
    @Override
    public Function4<Var<?>[], Var<?>[], CanSelectMember, ValueWrapper<Var<?>>, java.lang.Void> getMNIMethod(@NotNull String name) {
        return methods.get(name);
    }
}
