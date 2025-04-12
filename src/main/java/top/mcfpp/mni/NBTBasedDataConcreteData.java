package top.mcfpp.mni;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.JsonTextConcrete;
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.nbt.tags.Tag;
import top.mcfpp.util.ValueWrapper;

public class NBTBasedDataConcreteData {

    @MNIFunction(caller = "nbt", returnType = "text")
    public static void toText(@NotNull NBTBasedDataConcrete caller, ValueWrapper<JsonTextConcrete> returnValue){
        var l = new ListChatComponent();
        l.getComponents().add(new PlainChatComponent(Tag.toSNBT(caller.getValue())));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }

}
