package top.mcfpp.mni;

import top.mcfpp.annotations.MNIBinaryOperator;
import top.mcfpp.core.lang.JsonText;
import top.mcfpp.util.ValueWrapper;

public class JsonTextData {
    @MNIBinaryOperator(paramType = "text", returnType = "text", operator = "+")
    public static void plus(JsonText a, JsonText caller, ValueWrapper<JsonText> re){
        re.setValue((JsonText) caller.plus(a));
    }

}
