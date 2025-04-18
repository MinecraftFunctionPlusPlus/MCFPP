package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.JsonTextConcrete;
import top.mcfpp.core.lang.nbt.MCStringConcrete;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.util.ValueWrapper;

public class MCStringConcreteData {

    @MNIFunction(caller = "string", returnType = "text", override = true)
    public static void toText(MCStringConcrete caller, ValueWrapper<JsonTextConcrete> returnValue) {
        var l = new ListChatComponent();
        l.append(new PlainChatComponent(caller.getValue().getValue()));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }
}
