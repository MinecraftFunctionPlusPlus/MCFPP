package top.mcfpp.mni;

import net.querz.nbt.io.SNBTUtil;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.DataTemplateObject;
import top.mcfpp.lang.DataTemplateObjectConcrete;
import top.mcfpp.lang.JsonTextConcrete;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.NBTChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.util.ValueWrapper;

import java.io.IOException;

public class DataObjectData {

    @MNIRegister(caller = "DataObject", returnType = "text", override = true)
    public static void toText(@NotNull DataTemplateObject caller, ValueWrapper<JsonTextConcrete> returnValue) throws IOException {
        var l = new ListChatComponent();
        if(caller instanceof DataTemplateObjectConcrete callerC){
            l.getComponents().add(new PlainChatComponent(SNBTUtil.toSNBT(callerC.getValue())));
        }else {
            l.getComponents().add(new NBTChatComponent(caller.toNBTVar(), false, null));
        }
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }

}
