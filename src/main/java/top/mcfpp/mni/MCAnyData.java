package top.mcfpp.mni;

import kotlin.jvm.functions.Function4;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.CanSelectMember;
import top.mcfpp.lang.JavaVar;
import top.mcfpp.lang.Var;
import top.mcfpp.model.function.Function;
import top.mcfpp.model.function.MNIMethodContainer;
import top.mcfpp.util.ValueWrapper;

import java.util.HashMap;
import java.util.UUID;

public class MCAnyData {
    @MNIRegister(normalParams = {"any a"}, returnType = "JavaVar")
    public static void getJavaVar(@NotNull Var<?> value, ValueWrapper<Var<?>> returnValue){
        var re = new JavaVar(value, UUID.randomUUID().toString());
        returnValue.setValue(re);
    }
}
