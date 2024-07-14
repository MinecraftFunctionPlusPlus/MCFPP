package top.mcfpp.mni;

import kotlin.jvm.functions.Function4;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.*;
import top.mcfpp.lib.ChatComponent;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.model.function.Function;
import top.mcfpp.model.function.MNIMethodContainer;
import top.mcfpp.util.ValueWrapper;

import java.util.HashMap;
import java.util.UUID;

public class MCAnyData {
    @MNIRegister(normalParams = {"any a"}, returnType = "JavaVar")
    public static void getJavaVar(@NotNull Var<?> value, ValueWrapper<Var<?>> returnValue){
        var re = new JavaVar(value, UUID.randomUUID().toString());
        returnValue.setValue(re);
    }

    @MNIRegister(caller = "any", returnType = "text")
    public static void toText(@NotNull Var<?> caller, ValueWrapper<JsonTextConcrete> returnValue){
        var l = new ListChatComponent();
        l.getComponents().add(new PlainChatComponent(caller.toString()));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }
}
