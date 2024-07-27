package top.mcfpp.mni;

import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.JsonTextConcrete;
import top.mcfpp.lang.MCIntConcrete;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.util.ValueWrapper;

public class MCIntConcreteData {

    @MNIRegister(caller = "int", returnType = "text", override = true)
    public static void toText(MCIntConcrete caller, ValueWrapper<JsonTextConcrete> returnValue) {
        var l = new ListChatComponent();
        l.getComponents().add(new PlainChatComponent(caller.getValue().toString()));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }
}
