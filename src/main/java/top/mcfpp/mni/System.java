package top.mcfpp.mni;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.core.lang.nbt.*;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.obj.DataTemplateObjectConcrete;
import top.mcfpp.lib.NBTChatComponent;
import top.mcfpp.lib.ScoreChatComponent;
import top.mcfpp.model.function.Function;
import top.mcfpp.nbt.tags.Tag;
import top.mcfpp.util.*;

import java.util.Objects;

public class System {

    @MNIFunction(normalParams = {"any"}, returnType = "type")
    public static void typeOf(@NotNull Var<?> value, ValueWrapper<MCFPPTypeVar> returnValue){
        var re = new MCFPPTypeVar(value.getType(), TempPool.getVarIdentify());
        returnValue.setValue(re);
    }

    @InsertCommand
    @MNIFunction(normalParams = {"text"})
    public static void print(@NotNull JsonText text){
        if(text instanceof JsonTextConcrete textC){
            Function.addCommand(new Command("tellraw @a").build(textC.getValue().toCommandPart()));
        }else {
            Function.addCommand(new Command("tellraw @a").build(text.toCommandPart()));
        }
    }

    @MNIFunction(normalParams = {"string"})
    public static void print(@NotNull MCString string){
        if(string instanceof MCStringConcrete stringC){
            Function.addCommand(new Command("tellraw @a \"").build(StringHelper.addEscapes(stringC.getValue().getValue()), false).build("\"", false));
        }else {
            Function.addCommand(new Command("tellraw @a").build(string.toCommandPart()));
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"any"})
    public static void print(@NotNull MCAny value){
        var t = value.getInferredType();
        if(t != null){
            var qwq = value.buildInferredVar(t);
            printVar(qwq);
        }else {
            Function.addCommand("tellraw @a " + "\"" + value + "\"");
        }
    }

    public static void printVar(@NotNull Var<?> var){
        switch (var) {
            case MCInt mcInt -> print(mcInt);
            case NBTList list -> print(list);
            case NBTDictionary dictionary -> print(dictionary);
            case JsonText jsonText -> print(jsonText);
            case MCString mcString -> print(mcString);
            case NBTBasedData nbtBasedData -> print(nbtBasedData);
            case BaseBool bool -> print(bool);
            case DataTemplateObject object -> print(object);
            case PropertyVar property -> printVar(property.get());
            default -> Function.addCommand("tellraw @a " + "\"" + var + "\"");
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"int"})
    public static void print(@NotNull MCInt var) {
        if (var instanceof MCIntConcrete varC) {
            //是确定的，直接输出数值
            Function.addCommand("tellraw @a \"" + varC.getValue() + "\"");
        }else {
            Function.addCommand("tellraw @a " + new ScoreChatComponent(var).toCommandPart());
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"list"})
    public static void print(@NotNull NBTList var){
        if(var instanceof NBTListConcrete varC){
            if(varC.isAllConcrete()){
                Function.addCommand("tellraw @a \"" + Tag.toSNBT(NBTUtil.valueToNBT(varC.getValue())) + "\"");
            }else {
                varC.toDynamic(true);
                Function.addCommand(new Command("tellraw @a").build(new NBTChatComponent(var, false, null).toCommandPart()));
            }
        }else {
            Function.addCommand(new Command("tellraw @a").build(new NBTChatComponent(var, false, null).toCommandPart()));
        }
    }
 
    @InsertCommand
    @MNIFunction(normalParams = {"dict"})
    public static void print(@NotNull NBTDictionary var){
        if(var instanceof NBTDictionaryConcrete varC){
            if(varC.isAllConcrete()){
                Function.addCommand("tellraw @a \"" + Tag.toSNBT(NBTUtil.valueToNBT(varC.getValue())) + "\"");
            }else {
                varC.toDynamic(true);
                Function.addCommand(new Command("tellraw @a").build(new NBTChatComponent(var, false, null).toCommandPart()));
            }
        }else {
            Function.addCommand(new Command("tellraw @a").build(new NBTChatComponent(var, false, null).toCommandPart()));
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"nbt"})
    public static void print(@NotNull NBTBasedData var){
        if(var instanceof MCFPPValue<?> varC){
            Function.addCommand("tellraw @a " + Tag.toSNBT(NBTUtil.valueToNBT(varC.getValue())));
        }else {
            Function.addCommand(new Command("tellraw @a").build(new NBTChatComponent(var, false, null).toCommandPart()));
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"DataObject"})
    public static void print(@NotNull DataTemplateObject object) {
        if(object instanceof DataTemplateObjectConcrete objectConcrete){
            Function.addCommand("tellraw @a \"" + StringHelper.addEscapes(Tag.toSNBT(Objects.requireNonNull(objectConcrete.getTagCache()))) + "\"");
        }else {
            //TODO
            Function.addCommand("TODO: tellraw templateData");
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"bool"})
    public static void print(BaseBool bool){
        ScoreBool b;
        if(bool instanceof ScoreBool){
            b = (ScoreBool) bool;
        }else {
            b = bool.toScoreBool(false);
        }
        if(b instanceof ScoreBoolConcrete bC){
            Function.addCommand("tellraw @a \"" + (bC.getValue()?1:0) + "\"");
        }else {
            Function.addCommand("tellraw @a " + new ScoreChatComponent(b.asIntVar()).toCommandPart());
        }
    }

    @MNIFunction
    public static void debug(){
        //噢，在这里断点，这样就可以断点编译了
        //noinspection unused
        int i = 0;
    }

    @MNIFunction(normalParams = {"string"})
    public static void info(@NotNull MCString var){
        if(var instanceof MCStringConcrete varC){
            LogProcessor.info(varC.getValue().getValue());
        }else{
            LogProcessor.info(var.toString());
        }
    }

    @MNIFunction(normalParams = {"string"})
    public static void warn(@NotNull MCString var){
        if(var instanceof MCStringConcrete varC){
            LogProcessor.warn(varC.getValue().getValue());
        }else{
            LogProcessor.warn(var.toString());
        }
    }

    @MNIFunction(normalParams = {"string"})
    public static void error(@NotNull MCString var){
        if(var instanceof MCStringConcrete varC){
            LogProcessor.error(varC.getValue().getValue());
        }else{
            LogProcessor.error(var.toString());
        }
    }
}
