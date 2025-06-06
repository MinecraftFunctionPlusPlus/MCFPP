package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.core.lang.JsonText;
import top.mcfpp.util.ValueWrapper;

public class JsonTextData {
    @MNIOperator(paramType = "text", returnType = "text", operator = "+")
    public static void plus(JsonText a, JsonText caller, ValueWrapper<JsonText> re){
        re.setValue((JsonText) caller.plus(a));
    }

}
