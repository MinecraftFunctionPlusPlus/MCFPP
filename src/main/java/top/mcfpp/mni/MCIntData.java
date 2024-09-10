package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.JsonTextConcrete;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.ScoreChatComponent;
import top.mcfpp.util.ValueWrapper;

public class MCIntData {

    @MNIFunction(caller = "int", returnType = "text", override = true)
    public static void toText(MCInt caller, ValueWrapper<JsonTextConcrete> returnValue) {
        var l = new ListChatComponent();
        l.getComponents().add(new ScoreChatComponent(caller));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }
}
