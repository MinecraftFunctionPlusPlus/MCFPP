package top.mcfpp.mni;

import net.querz.nbt.io.SNBTUtil;
import net.querz.nbt.tag.Tag;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.core.lang.nbt.*;
import top.mcfpp.lib.NBTChatComponent;
import top.mcfpp.lib.ScoreChatComponent;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.LogProcessor;
import top.mcfpp.util.NBTUtil;
import top.mcfpp.util.TempPool;
import top.mcfpp.util.ValueWrapper;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public class System {

    @MNIFunction(normalParams = {"any a"}, returnType = "type")
    public static void typeOf(@NotNull Var<?> value, ValueWrapper<MCFPPTypeVar> returnValue){
        var re = new MCFPPTypeVar(value.getType(), TempPool.INSTANCE.getVarIdentify());
        returnValue.setValue(re);
    }

    @InsertCommand
    public static void print(@NotNull JsonText text){
        if(text instanceof JsonTextConcrete textC){
            Function.Companion.addCommand(new Command("tellraw @a").build(textC.getValue().toCommandPart(), true));
        }else {
            Function.Companion.addCommand(new Command("tellraw @a").build(text.toCommandPart(), true));
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"any a"})
    public static void print(@NotNull MCAny value){
        var qwq = value.buildInferredVar();
        if(qwq != null){
            printVar(qwq);
        }else {
            Function.Companion.addCommand("tellraw @a " + "\"" + value + "\"");
        }
    }

    public static void printVar(@NotNull Var<?> var){
        switch (var) {
            case MCInt mcInt -> print(mcInt);
            case NBTList list -> print(list);
            case NBTDictionary dictionary -> print(dictionary);
            case NBTBasedData nbtBasedData -> print(nbtBasedData);
            case BaseBool bool -> print(bool);
            case DataTemplateObject object -> print(object);
            case PropertyVar property -> printVar(property.getter());
            default -> Function.Companion.addCommand("tellraw @a " + "\"" + var + "\"");
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"int i"})
    public static void print(@NotNull MCInt var) {
        if (var instanceof MCIntConcrete varC) {
            //是确定的，直接输出数值
            Function.Companion.addCommand("tellraw @a \"" + varC.getValue() + "\"");
        }else {
            if(var.parentClass() != null){
                Function.Companion.addCommands(Commands.INSTANCE.selectRun(Objects.requireNonNull(var.getParent()), "tellraw @a " + new ScoreChatComponent(var).toCommandPart(), true));
            }else {
                Function.Companion.addCommand("tellraw @a " + new ScoreChatComponent(var).toCommandPart());
            }
        }
    }

    @InsertCommand
    public static void print(@NotNull NBTList var){
        if(var instanceof NBTListConcrete varC){
            if(varC.isAllConcrete()){
                try {
                    Function.Companion.addCommand("tellraw @a \"" + SNBTUtil.toSNBT(NBTUtil.INSTANCE.valueToNBT(varC.getValue())) + "\"");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }else {
                varC.toDynamic(true);
                Function.Companion.addCommands(Commands.INSTANCE.method2(var,new Command("tellraw @a").build(new NBTChatComponent(var, false, null).toCommandPart(), true)));
            }
        }else {
            Function.Companion.addCommands(Commands.INSTANCE.method2(var,new Command("tellraw @a").build(new NBTChatComponent(var, false, null).toCommandPart(), true)));
        }
    }
 
    @InsertCommand
    public static void print(@NotNull NBTDictionary var){
        if(var instanceof NBTDictionaryConcrete varC){
            if(varC.isAllConcrete()){
                try {
                    Function.Companion.addCommand("tellraw @a \"" + SNBTUtil.toSNBT(NBTUtil.INSTANCE.valueToNBT(varC.getValue())) + "\"");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }else {
                varC.toDynamic(true);
                Function.Companion.addCommands(Commands.INSTANCE.method2(var,new Command("tellraw @a").build(new NBTChatComponent(var, false, null).toCommandPart(), true)));
            }
        }else {
            Function.Companion.addCommands(Commands.INSTANCE.method2(var,new Command("tellraw @a").build(new NBTChatComponent(var, false, null).toCommandPart(), true)));
        }
    }

    @InsertCommand
    public static void print(@NotNull NBTBasedData var){
        if(var instanceof MCFPPValue<?> varC){
            try {
                Function.Companion.addCommand("tellraw @a \"" + SNBTUtil.toSNBT(NBTUtil.INSTANCE.valueToNBT(varC.getValue())) + "\"");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else {
            Function.Companion.addCommands(Commands.INSTANCE.method2(var,new Command("tellraw @a").build(new NBTChatComponent(var, false, null).toCommandPart(), true)));
        }
    }

    @InsertCommand
    public static void print(@NotNull DataTemplateObject object) {
        if(object instanceof DataTemplateObjectConcrete objectConcrete){
            try {
                Function.Companion.addCommand("tellraw @a \"" + SNBTUtil.toSNBT(objectConcrete.getValue()) + "\"");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else {
            //TODO
            Function.Companion.addCommand("TODO: tellraw templateData");
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"bool b"})
    public static void print(BaseBool bool){
        ScoreBool b;
        if(bool instanceof ScoreBool){
            b = (ScoreBool) bool;
        }else {
            b = bool.toScoreBool(false);
        }
        if(b instanceof ScoreBoolConcrete bC){
            Function.Companion.addCommand("tellraw @a \"" + (bC.getValue()?1:0) + "\"");
        }else {
            if(b.getParent() != null){
                Function.Companion.addCommands(Commands.INSTANCE.selectRun(b.getParent(), "tellraw @a " + new ScoreChatComponent(b.asIntVar()).toCommandPart(), true));
            }else {
                Function.Companion.addCommand("tellraw @a " + new ScoreChatComponent(b.asIntVar()).toCommandPart());
            }
        }
    }

    @MNIFunction
    public static void debug(){
        //噢，在这里断点，这样就可以断点编译了
        //noinspection unused
        int i = 0;
    }

    @MNIFunction(normalParams = {"string s"})
    public static void info(@NotNull MCString var){
        if(var instanceof MCStringConcrete varC){
            LogProcessor.INSTANCE.info(varC.getValue().getValue());
        }else{
            LogProcessor.INSTANCE.info(var.toString());
        }
    }

    @MNIFunction(normalParams = {"string s"})
    public static void warn(@NotNull MCString var){
        if(var instanceof MCStringConcrete varC){
            LogProcessor.INSTANCE.warn(varC.getValue().getValue());
        }else{
            LogProcessor.INSTANCE.warn(var.toString());
        }
    }

    @MNIFunction(normalParams = {"string s"})
    public static void error(@NotNull MCString var){
        if(var instanceof MCStringConcrete varC){
            LogProcessor.INSTANCE.error(varC.getValue().getValue());
        }else{
            LogProcessor.INSTANCE.error(var.toString());
        }
    }
}
