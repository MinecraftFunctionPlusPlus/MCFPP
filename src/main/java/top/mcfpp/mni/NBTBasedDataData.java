package top.mcfpp.mni;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.JsonTextConcrete;
import top.mcfpp.lang.NBTBasedData;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.NBTChatComponent;
import top.mcfpp.util.ValueWrapper;

public class NBTBasedDataData {

    @MNIRegister(caller = "nbt", returnType = "text")
    public static void toText(@NotNull NBTBasedData<?> caller, ValueWrapper<JsonTextConcrete> returnValue){
        var l = new ListChatComponent();
        l.getComponents().add(new NBTChatComponent(caller, false, null));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }

}
