package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.core.lang.JsonText;
import top.mcfpp.core.lang.JsonTextConcrete;
import top.mcfpp.util.ValueWrapper;

public class JsonTextConcreteData {
    @MNIOperator(paramType = "text", returnType = "text", operator = "+")
    public static void plus(JsonText a, JsonTextConcrete caller, ValueWrapper<JsonText> re){
        re.setValue((JsonText) caller.plus(a));
    }

}
