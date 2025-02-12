package top.mcfpp.mni;

import net.querz.nbt.io.SNBTUtil;
import net.querz.nbt.tag.Tag;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.MCFPPValue;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.bool.FunctionBool;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.nbt.NBTBasedData;
import top.mcfpp.core.lang.nbt.NBTList;
import top.mcfpp.core.lang.nbt.NBTListConcrete;
import top.mcfpp.lib.NBTPath;
import top.mcfpp.lib.SbObject;
import top.mcfpp.lib.Storage;
import top.mcfpp.lib.StorageSource;
import top.mcfpp.model.function.Function;
import top.mcfpp.model.function.MCFunction;
import top.mcfpp.util.NBTUtil;
import top.mcfpp.util.ValueWrapper;

import java.io.IOException;

public class NBTListData {

    static NBTBasedData list = new NBTBasedData("list.list");
    static NBTBasedData element = new NBTBasedData("list.element");
    static MCInt index = new MCInt("list.index");
    static FunctionBool contains = new FunctionBool("list.contains", new MCFunction("mcfpp.lang","list","contains"));

    static {
        list.setNbtPath(new NBTPath(new StorageSource(Storage.Companion.getMCFPP_SYSTEM().toString())).memberIndex("list.list"));
        list.setDynamic(true);
        element.setNbtPath(new NBTPath(new StorageSource(Storage.Companion.getMCFPP_SYSTEM().toString())).memberIndex("list.element"));
        element.setDynamic(true);
        index.setObj(SbObject.Companion.getMCFPP_TEMP());
    }


    @MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E")
    public static void add(Var<?> e, NBTList caller) throws IOException {
        if(e instanceof MCFPPValue<?>){
            //e是确定的
            Tag<?> tag = NBTUtil.INSTANCE.varToNBT(e);
            var command = Commands.INSTANCE.method2(caller, new Command("data modify")
                    .build(caller.nbtPath.toCommandPart(), true)
                    .build("append value " + SNBTUtil.toSNBT(tag), true)
            );
            Function.Companion.addCommands(command);
        }else {
            //e不是确定的
            if (e.parentClass() != null) e = e.getTempVar();
            Var<?> finalE = e;
            var command = Commands.INSTANCE.method2(caller, new Command("data modify")
                    .build(caller.nbtPath.toCommandPart(), true)
                    .build("append from", true)
                    .build(finalE.getNbtPath().toCommandPart(), true));
            Function.Companion.addCommands(command);
        }
    }

    @MNIFunction(normalParams = {"list<E> list"}, caller = "list", genericType = "E")
    public static void addAll(@NotNull NBTList list, NBTList caller){
        NBTBasedData l;
        if(list.parentClass() != null) {
            l = list.getTempVar();
        }else if((NBTList)list instanceof NBTListConcrete eC){
            l = list;
            eC.toDynamic(false);
        }else{
            l = list;
        }
        var command = Commands.INSTANCE.method2(caller, new Command("data modify")
                .build(caller.nbtPath.toCommandPart(), true)
                .build("append from", true)
                .build(l.nbtPath.iteratorIndex().toCommandPart(), true));
        Function.Companion.addCommands(command);
    }

    @MNIFunction(normalParams = {"int index", "E e"}, caller = "list", genericType = "E")
    public static void insert(MCInt index, Var<?> e, NBTList caller) throws IOException {
        if(e instanceof MCFPPValue<?> && index instanceof MCIntConcrete indexC){
            //都是确定的
            Tag<?> tag = NBTUtil.INSTANCE.varToNBT(e);
            int i = indexC.getValue();
            var command = Commands.INSTANCE.method2(caller, new Command("data modify")
                    .build(caller.nbtPath.toCommandPart(), true)
                    .build("insert " + i + " value " + SNBTUtil.toSNBT(tag), true));
            Function.Companion.addCommands(command);
        } else if(index instanceof MCIntConcrete indexC){
            //e不是确定的，index是确定的，所以可以直接调用命令而不需要宏
            int i = indexC.getValue();
            if(e.parentClass() != null) e = e.getTempVar();
            Var<?> finalE = e;
            var command = Commands.INSTANCE.method2(caller, new Command("data modify")
                   .build(caller.nbtPath.toCommandPart(), true)
                   .build("insert " + i + " from", true)
                   .build(finalE.getNbtPath().toCommandPart(), true));
            Function.Companion.addCommands(command);
        }else if(e instanceof MCFPPValue<?>){
            //e是确定的，index不是确定的，需要使用宏
            Tag<?> tag = NBTUtil.INSTANCE.varToNBT(e);
            var command = Commands.INSTANCE.method2(caller,  new Command("data modify")
                    .build(caller.nbtPath.toCommandPart(), true)
                    .build("insert", true)
                    .buildMacro(index, true)
                    .build("value " + SNBTUtil.toSNBT(tag), true));
            Function.Companion.addCommands(command);
        } else{
            //e是不确定的，index也不是确定的
            if(e.parentClass() != null) e = e.getTempVar();
            var command = Commands.INSTANCE.method2(caller, new Command("data modify")
                    .build(caller.nbtPath.toCommandPart(), true)
                    .build("insert", true)
                    .buildMacro(index, true)
                    .build("from", true)
                    .build(e.nbtPath.toCommandPart(), true));
            Function.Companion.addCommands(command);
        }
    }

    @MNIFunction(normalParams = {"int index"}, caller = "list", genericType = "E")
    public static void removeAt(MCInt index, NBTList caller){
        var command = Commands.INSTANCE.method2(caller, new Command("data remove")
                .build(caller.nbtPath.intIndex(index).toCommandPart(), true)
        );
        Function.Companion.addCommands(command);
    }

    @MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E", returnType = "int")
    public static void indexOf(@NotNull Var<?> e, NBTList caller, ValueWrapper<MCInt> returnVar){
        var n = e.toNBTVar();
        element.assignedBy(n);
        list.assignedBy(caller);
        Function.Companion.addCommand("scoreboard players set list.index " + SbObject.Companion.getMCFPP_TEMP() + " 0");
        Function.Companion.addCommand("execute store result score list.size mcfpp_temp run data get storage mcfpp:system list.list");
        Function.Companion.addCommand("function mcfpp.lang:list/index_of");
        returnVar.setValue(index);
    }

    @MNIFunction(normalParams = {"E e"}, caller = "list<E>", genericType = "E", returnType = "int")
    public static void lastIndexOf(Var<?> e, NBTList caller, ValueWrapper<MCInt> returnVar){
        var n = e.toNBTVar();
        element.assignedBy(n);
        list.assignedBy(caller);
        Function.Companion.addCommand("scoreboard players set list.index " + SbObject.Companion.getMCFPP_TEMP() + " 0");
        Function.Companion.addCommand("execute store result score list.size mcfpp_temp run data get storage mcfpp:system list.list");
        Function.Companion.addCommand("function mcfpp.lang:list/last_index_of");
        returnVar.setValue(index);
    }

    @MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E", returnType = "bool")
    public static void contains(Var<?> e, NBTList caller, ValueWrapper<BaseBool> returnVar){
        var n = e.toNBTVar();
        element.assignedBy(n);
        list.assignedBy(caller);
        Function.Companion.addCommand("scoreboard players set list.index " + SbObject.Companion.getMCFPP_TEMP() + " 0");
        Function.Companion.addCommand("execute store result score list.size mcfpp_temp run data get storage mcfpp:system list.list");
        Function.Companion.addCommand("function mcfpp.lang:list/contains");
        returnVar.setValue(contains);
    }

    @MNIFunction(caller = "list", genericType = "E")
    public static void clear(NBTList caller){
        caller.replacedBy(caller.assignedBy(NBTListConcrete.Companion.getEmpty()));
    }
}