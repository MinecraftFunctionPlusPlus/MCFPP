package top.mcfpp.mni;

import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.JavaVar;
import top.mcfpp.lang.MCInt;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

public class MCIntData {

    @MNIRegister(caller = "int")
    public static void test(MCInt caller) {
        Function.Companion.addCommand("say " + caller);
    }

    @MNIRegister(normalParams = {"int i"}, caller = "int", returnType = "JavaVar")
    public static void qwq(MCInt i, MCInt caller, ValueWrapper<JavaVar> re){
        re.setValue(new JavaVar(caller, "qwq"));
        Function.Companion.addCommand("say i=" + i);
        Function.Companion.addCommand("say this=" + caller);
    }
}
