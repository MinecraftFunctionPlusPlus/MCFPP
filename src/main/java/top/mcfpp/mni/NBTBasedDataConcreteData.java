package top.mcfpp.mni;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.JsonTextConcrete;
import top.mcfpp.lang.NBTBasedDataConcrete;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.util.ValueWrapper;

public class NBTBasedDataConcreteData {

    @MNIRegister(caller = "nbt", returnType = "text")
    public static void toText(@NotNull NBTBasedDataConcrete<?> caller, ValueWrapper<JsonTextConcrete> returnValue){
        var l = new ListChatComponent();
        l.getComponents().add(new PlainChatComponent(caller.getValue().valueToString()));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }

}
