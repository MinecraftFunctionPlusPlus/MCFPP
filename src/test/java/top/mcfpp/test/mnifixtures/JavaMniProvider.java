package top.mcfpp.test.mnifixtures;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.Var;
import top.mcfpp.mni.annotation.NoExternalWrites;
import top.mcfpp.util.ValueWrapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@NoExternalWrites
public final class JavaMniProvider {
    public static final List<String> calls = new ArrayList<>();
    public static final Map<String, Integer> values = new LinkedHashMap<>();

    private JavaMniProvider() {}

    @MNIFunction(identifier = "javaVoid")
    public static void voidEntry() { calls.add("javaVoid"); }

    @MNIFunction(identifier = "javaArgs", readOnlyParams = {"int"}, normalParams = {"int"})
    public static void argsEntry(MCInt readOnly, Var<?> normal) {
        calls.add("javaArgs");
        values.put("javaArgs", readOnly.getValue());
        values.put("javaNormal", ((MCInt) normal).getValue());
    }

    @MNIFunction(identifier = "javaCaller", caller = "int")
    public static void callerEntry(MCInt caller) {
        calls.add("javaCaller");
        values.put("javaCaller", caller.getValue());
    }

    @MNIFunction(identifier = "javaResult", returnType = "int")
    public static void resultEntry(ValueWrapper<MCInt> result) {
        calls.add("javaResult");
        result.set(new MCInt(7, "java-result"));
    }

    @MNIFunction(identifier = "javaAll", readOnlyParams = {"int"}, normalParams = {"int"}, caller = "int", returnType = "int")
    public static void allEntry(MCInt readOnly, MCInt normal, MCInt caller, ValueWrapper<MCInt> result) {
        calls.add("javaAll");
        result.set(new MCInt(readOnly.getValue() * 100 + normal.getValue() * 10 + caller.getValue(), "java-all-result"));
    }
}
