package top.mcfpp.mni;

import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.JavaVar;
import top.mcfpp.lang.JsonTextConcrete;
import top.mcfpp.lang.MCInt;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.ScoreChatComponent;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

public class MCIntData {

    @MNIRegister(caller = "int", returnType = "text")
    public static void toText(MCInt caller, ValueWrapper<JsonTextConcrete> returnValue) {
        var l = new ListChatComponent();
        l.getComponents().add(new ScoreChatComponent(caller));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }
}
