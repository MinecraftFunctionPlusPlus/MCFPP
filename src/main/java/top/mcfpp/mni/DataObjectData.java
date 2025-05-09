package top.mcfpp.mni;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.obj.DataTemplateObjectConcrete;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.NBTChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.nbt.tags.Tag;
import top.mcfpp.util.NBTUtil;
import top.mcfpp.util.ValueWrapper;

import java.io.IOException;
import java.util.Objects;

public class DataObjectData {

    @MNIFunction(caller = "DataObject", returnType = "text", override = true)
    public static void toText(DataTemplateObject caller, ValueWrapper<JsonTextConcrete> returnValue) throws IOException {
        var l = new ListChatComponent();
        if(caller instanceof DataTemplateObjectConcrete callerC){
            l.getComponents().add(new PlainChatComponent(Tag.toSNBT(Objects.requireNonNull(NBTUtil.INSTANCE.varToNBT(callerC)))));
        }else {
            l.getComponents().add(new NBTChatComponent(caller.toNBTVar(), false, null));
        }
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }

    @MNIFunction(caller = "DataObject", returnType = "JavaVar")
    public static void toCommandPart(@NotNull Var<?> caller, ValueWrapper<JavaVar> returnValue){
        returnValue.setValue(new JavaVar(caller.toCommandPart(), "command"));
    }
}
