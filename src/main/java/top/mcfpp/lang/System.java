package top.mcfpp.lang;

import net.querz.nbt.tag.StringTag;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.annotations.MCFPPNative;
import top.mcfpp.lib.Function;
import top.mcfpp.util.ValueWrapper;

public class System {
    @MCFPPNative
    @InsertCommand
    public static void insert(@NotNull Var[] vars, CanSelectMember caller, ValueWrapper<Var> output){
        var value = vars[0];
        //只会有一个参数哦
        if(value instanceof MCString mcString)
            Function.Companion.addCommand(((StringTag) mcString.getValue()).getValue());

    }

    @MCFPPNative
    public static void print(@NotNull Var[] vars, CanSelectMember caller, ValueWrapper<Var> output) {
        var value = vars[0];
        //只会有一个参数哦
        if (value instanceof MCInt) print((MCInt) value);
        else if (value instanceof JsonString) print((JsonString) value);
        else print(value);
    }

    @InsertCommand
    public static void print(@NotNull MCInt var) {
        if (var.isConcrete()) {
            //是确定的，直接输出数值
            Function.Companion.addCommand("tellraw @a " + var.getValue());
        }else {
            Function.Companion.addCommand("tellraw @a " + new JsonTextNumber(var).toJson());
        }
    }

    @InsertCommand
    public static void print(JsonString var){
        Function.Companion.addCommand("tellraw @a " + var.getJsonText().toJson());
    }

    @InsertCommand
    public static void print(@NotNull Var var){
        Function.Companion.addCommand("tellraw @a " + "\"" +var + "\"");
    }

}
