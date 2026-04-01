package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.nbt.MCByte;
import top.mcfpp.util.ValueWrapper;

public class MCByteData {
    @MNIOperator(paramType = "short", returnType = "short", operator = "+", returnsConstWhenArgsConst = true)
    public static void plus(MCByte b, MCByte caller, ValueWrapper<MCByte> re){
        re.setValue((MCByte) caller.plus(b));
    }

    @MNIOperator(paramType = "short", returnType = "short", operator = "-", returnsConstWhenArgsConst = true)
    public static void minus(MCByte b, MCByte caller, ValueWrapper<MCByte> re){
        re.setValue((MCByte) caller.minus(b));
    }

    @MNIOperator(paramType = "short", returnType = "short", operator = "*", returnsConstWhenArgsConst = true)
    public static void times(MCByte b, MCByte caller, ValueWrapper<MCByte> re){
        re.setValue((MCByte) caller.times(b));
    }

    @MNIOperator(paramType = "short", returnType = "short", operator = "/", returnsConstWhenArgsConst = true)
    public static void div(MCByte b, MCByte caller, ValueWrapper<MCByte> re){
        re.setValue((MCByte) caller.div(b));
    }

    @MNIOperator(paramType = "short", returnType = "short", operator = "%", returnsConstWhenArgsConst = true)
    public static void rem(MCByte b, MCByte caller, ValueWrapper<MCByte> re){
        re.setValue((MCByte) caller.rem(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = ">", returnsConstWhenArgsConst = true)
    public static void isBigger(MCByte b, MCByte caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isBigger(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = "<", returnsConstWhenArgsConst = true)
    public static void isSmaller(MCByte b, MCByte caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isSmaller(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = "<=", returnsConstWhenArgsConst = true)
    public static void isSmallerOrEqual(MCByte b, MCByte caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isSmallerOrEqual(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = ">=", returnsConstWhenArgsConst = true)
    public static void isBiggerOrEqual(MCByte b, MCByte caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isBiggerOrEqual(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = "==", returnsConstWhenArgsConst = true)
    public static void isEqual(MCByte b, MCByte caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isEqual(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = "!=", returnsConstWhenArgsConst = true)
    public static void isNotEqual(MCByte b, MCByte caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isNotEqual(b));
    }
}
