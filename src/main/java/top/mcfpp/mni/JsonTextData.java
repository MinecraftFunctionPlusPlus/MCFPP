package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.core.lang.JsonText;
import top.mcfpp.core.lang.JsonTextConcrete;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.type.MCFPPBaseType;
import top.mcfpp.util.ValueWrapper;

public class JsonTextData {
    @MNIOperator(paramType = "text", returnType = "text", operator = "+", returnsConstWhenArgsConst = true)
    public static void plus(JsonText a, JsonText caller, ValueWrapper<JsonText> re){
        re.setValue((JsonText) caller.plus(a));
    }

    @MNIOperator(paramType = "string", returnType = "text", operator = "+", returnsConstWhenArgsConst = true)
    public static void plus(MCString a, JsonTextConcrete caller, ValueWrapper<JsonText> re){
        re.setValue((JsonText) caller.plus(a.implicitCast(MCFPPBaseType.JsonText.INSTANCE)));
    }
}
