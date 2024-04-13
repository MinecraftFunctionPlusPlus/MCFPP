package top.mcfpp.lang;

import kotlin.NotImplementedError;
import kotlin.jvm.functions.Function4;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.Tag;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MCFPPNative;
import top.mcfpp.lib.function.MNIMethodContainer;
import top.mcfpp.util.NBTUtil;
import top.mcfpp.util.ValueWrapper;

import java.util.ArrayList;
import java.util.HashMap;

public class NBTListData extends MNIMethodContainer {

    static HashMap<String, Function4<Var<?>[], Var<?>[], CanSelectMember, ValueWrapper<Var<?>>, java.lang.Void>> methods;

    static {
        methods = new HashMap<>();
        methods.put("add", (vars, vars2, canSelectMember, varValueWrapper) -> {
            return null;
        });

        methods.put("addAll", (vars, vars2, canSelectMember, varValueWrapper) -> {
            return null;
        });

        methods.put("insert", (vars, vars2, canSelectMember, varValueWrapper) -> {
            return null;
        });

        methods.put("remove", (vars, vars2, canSelectMember, varValueWrapper) -> {
            return null;
        });

        methods.put("removeAt", (vars, vars2, canSelectMember, varValueWrapper) -> {
            return null;
        });

        methods.put("indexOf", (vars, vars2, canSelectMember, varValueWrapper) -> {
            return null;
        });

        methods.put("lastIndexOf", (vars, vars2, canSelectMember, varValueWrapper) -> {
            return null;
        });

        methods.put("contains", (vars, vars2, canSelectMember, varValueWrapper) -> {
            return null;
        });

        methods.put("clear", (vars, vars2, canSelectMember, varValueWrapper) -> {
            return null;
        });
    }

    @NotNull
    @Override
    public Function4<Var<?>[], Var<?>[], CanSelectMember, ValueWrapper<Var<?>>, java.lang.Void> getMNIMethod(@NotNull String name) {
        return methods.get(name);
    }
}
