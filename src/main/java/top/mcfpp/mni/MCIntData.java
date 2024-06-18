package top.mcfpp.mni;

import kotlin.jvm.functions.Function4;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.CanSelectMember;
import top.mcfpp.lang.JavaVar;
import top.mcfpp.lang.MCInt;
import top.mcfpp.lang.Var;
import top.mcfpp.model.function.Function;
import top.mcfpp.model.function.MNIMethodContainer;
import top.mcfpp.util.ValueWrapper;

import java.util.HashMap;

public class MCIntData {

    @MNIRegister(caller = "int")
    public void test(MCInt caller) {
        Function.Companion.addCommand("say " + caller);
    }

    @MNIRegister(normalParams = {"int i"}, caller = "int", returnType = "JavaVar")
    public void qwq(MCInt i, MCInt caller, ValueWrapper<JavaVar> re){
        re.setValue(new JavaVar(caller, "qwq"));
        Function.Companion.addCommand("say i=" + i);
        Function.Companion.addCommand("say this=" + caller);
    }
}
