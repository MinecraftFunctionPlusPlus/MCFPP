package top.mcfpp.mni;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.JavaVar;
import top.mcfpp.lang.JsonTextConcrete;
import top.mcfpp.lang.Var;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.util.ValueWrapper;

import java.util.UUID;

public class MCAnyConcreteData {

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
