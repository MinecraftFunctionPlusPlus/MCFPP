package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.util.ValueWrapper;

public class MCBoolConcreteData {

    @MNIOperator(operator = "==", paramType = "bool", returnType = "bool")
    public static void isEqual(BaseBool a, BaseBool caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isEqual(a));
    }

    @MNIOperator(operator = "!=" ,paramType = "bool", returnType = "bool")
    public static void isNotEqual(BaseBool a, BaseBool caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isNotEqual(a));
    }

    @MNIOperator(operator = "||", paramType = "bool", returnType = "bool")
    public static void or(BaseBool a, BaseBool caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.or(a));
    }

    @MNIOperator(operator = "&&", paramType = "bool", returnType = "bool")
    public static void and(BaseBool a, BaseBool caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.and(a));
    }
}
