package top.mcfpp.mni;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.MCFPPValue;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.MCIntConcrete;
import top.mcfpp.core.lang.Var;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.bool.FunctionBool;
import top.mcfpp.core.lang.nbt.NBTBasedData;
import top.mcfpp.core.lang.nbt.NBTList;
import top.mcfpp.core.lang.nbt.NBTListConcrete;
import top.mcfpp.lib.NBTPath;
import top.mcfpp.lib.SbObject;
import top.mcfpp.lib.Storage;
import top.mcfpp.lib.StorageSource;
import top.mcfpp.model.function.Function;
import top.mcfpp.model.function.MCFunction;
import top.mcfpp.nbt.tags.Tag;
import top.mcfpp.nbt.tags.collection.ListTag;
import top.mcfpp.type.MCFPPType;
import top.mcfpp.util.NBTUtil;
import top.mcfpp.util.ValueWrapper;

import java.io.IOException;
import java.util.ArrayList;

public class NBTListData {
    static MCInt index = new MCInt("list_index");
    static FunctionBool contains = new FunctionBool("list_contains", new MCFunction("mcfpp.lang","list","contains"));

    static {
        index.setObj(SbObject.Companion.getMCFPP_TEMP());
    }

    private static NBTList getList(MCFPPType genericType){
        var list = new NBTList("list_list", genericType);
        list.setNbtPath(new NBTPath(new StorageSource(Storage.Companion.getMCFPP_SYSTEM().toString())).memberIndex("list.list"));
        list.setDynamic(true);
        return list;
    }

    private static void storeElement(Var<?> element){
        var path = new NBTPath(new StorageSource(Storage.Companion.getMCFPP_SYSTEM().toString())).memberIndex("list.element");
        // This slot carries the source encoding; it is storage transport, not an
        // implicit NBT-to-E conversion or a call to the value-taking build overload.
        var tag = top.mcfpp.analysis.ValueSnapshot.INSTANCE.of(element) != null ? NBTUtil.varToNBT(element) : null;
        if (tag != null) {
            Function.addCommand(Commands.dataSetValue(path, tag));
        } else {
            element.storeToStack();
            Function.addCommand(Commands.dataSetFrom(path, element.getNbtPath()));
        }
    }

    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E")
    public static void add(Var<?> e, NBTList caller) throws IOException {
        if(e instanceof MCFPPValue<?>){
            //e是确定的
            Tag<?> tag = NBTUtil.varToNBT(e);
            assert tag != null;
            var command = new Command("data modify")
                    .build(caller.nbtPath.toCommandPart(), true)
                    .build("append value " + Tag.toSNBT(tag), true);
            Function.addCommand(command);
        }else {
            //e不是确定的
            var command = new Command("data modify")
                    .build(caller.nbtPath.toCommandPart(), true)
                    .build("append from", true)
                    .build(e.getNbtPath().toCommandPart(), true);
            Function.addCommand(command);
        }
    }

    @MNIFunction(normalParams = {"list<E>"}, caller = "list", genericType = "E")
    public static void addAll(@NotNull NBTList list, NBTList caller){
        NBTBasedData l;
        if((NBTList)list instanceof NBTListConcrete eC){
            l = list;
            eC.synchronous();
        }else{
            l = list;
        }
        var command = new Command("data modify")
                .build(caller.nbtPath.toCommandPart(), true)
                .build("append from", true)
                .build(l.nbtPath.iteratorIndex().toCommandPart(), true);
        Function.addCommand(command);
    }

    @MNIFunction(normalParams = {"int", "E"}, caller = "list", genericType = "E")
    public static void insert(MCInt index, Var<?> e, NBTList caller) {
        if(e instanceof MCFPPValue<?> && index instanceof MCIntConcrete indexC){
            //都是确定的
            Tag<?> tag = NBTUtil.varToNBT(e);
            assert tag != null;
            int i = indexC.getValue();
            var command = new Command("data modify")
                    .build(caller.nbtPath.toCommandPart(), true)
                    .build("insert " + i + " value " + Tag.toSNBT(tag), true);
            Function.addCommand(command);
        } else if(index instanceof MCIntConcrete indexC){
            //e不是确定的，index是确定的，所以可以直接调用命令而不需要宏
            int i = indexC.getValue();
            var command = new Command("data modify")
                    .build(caller.nbtPath.toCommandPart(), true)
                    .build("insert " + i + " from", true)
                    .build(e.getNbtPath().toCommandPart(), true);
            Function.addCommand(command);
        }else if(e instanceof MCFPPValue<?>){
            //e是确定的，index不是确定的，需要使用宏
            Tag<?> tag = NBTUtil.varToNBT(e);
            assert tag != null;
            var command = new Command("data modify")
                    .build(caller.nbtPath.toCommandPart(), true)
                    .build("insert", true)
                    .buildMacro(index, true)
                    .build("value " + Tag.toSNBT(tag), true);
            Function.addCommand(command);
        } else{
            //e是不确定的，index也不是确定的
            var command = new Command("data modify")
                    .build(caller.nbtPath.toCommandPart(), true)
                    .build("insert", true)
                    .buildMacro(index, true)
                    .build("from", true)
                    .build(e.nbtPath.toCommandPart(), true);
            Function.addCommand(command);
        }
    }

    @MNIFunction(normalParams = {"int"}, caller = "list", genericType = "E")
    public static void removeAt(MCInt index, NBTList caller){
        if(index instanceof MCIntConcrete){
            var command = new Command("data remove")
                    .build(caller.nbtPath.intIndex(index).toCommandPart(), true);
            Function.addCommand(command);
        }else {
            index.nbtPath = NBTPath.Companion.getNormalStackPath(index);
            index.storeToStack();
            var command = new Command("data remove")
                    .build(caller.nbtPath.intIndex(index).toCommandPart(), true);
            Function.addCommand(command);
        }
    }

    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E")
    public static void remove(@NotNull Var<?> e, NBTList caller){
        ValueWrapper<MCInt> re = new ValueWrapper<>(index);
        indexOf(e, caller, re);
        var qwq = Commands.tempFunction("remove", Function.Companion.getCurrFunction(), (function) -> {
            index.nbtPath = NBTPath.Companion.getNormalStackPath(index);
            index.storeToStack();
            var command = new Command("data remove")
                    .build(caller.nbtPath.intIndex(index).toCommandPart(), true);
            Function.addCommand(command);
            return Unit.INSTANCE;
        });
        Function.addCommand(Commands.unlessScoreMatches(index, -1).build(qwq.getFirst(), true));
    }

    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E", returnType = "int")
    public static void indexOf(@NotNull Var<?> e, NBTList caller, ValueWrapper<MCInt> returnVar){
        storeElement(e);
        getList(caller.getGenericType()).assignedBy(caller);
        Function.addCommand("scoreboard players set list.index " + SbObject.Companion.getMCFPP_TEMP() + " 0");
        Function.addCommand("execute store result score list.size mcfpp_temp run data get storage mcfpp:system list.list");
        Function.addCommand("function mcfpp.lang:list/index_of");
        returnVar.setValue(index);
    }

    @MNIFunction(normalParams = {"E"}, caller = "list<E>", genericType = "E", returnType = "int")
    public static void lastIndexOf(Var<?> e, NBTList caller, ValueWrapper<MCInt> returnVar){
        storeElement(e);
        getList(caller.getGenericType()).assignedBy(caller);
        Function.addCommand("scoreboard players set list.index " + SbObject.Companion.getMCFPP_TEMP() + " 0");
        Function.addCommand("execute store result score list.size mcfpp_temp run data get storage mcfpp:system list.list");
        Function.addCommand("function mcfpp.lang:list/last_index_of");
        returnVar.setValue(index);
    }

    @MNIFunction(normalParams = {"E"}, caller = "list", genericType = "E", returnType = "bool")
    public static void contains(Var<?> e, NBTList caller, ValueWrapper<BaseBool> returnVar){
        storeElement(e);
        getList(caller.getGenericType()).assignedBy(caller);
        Function.addCommand("scoreboard players set list.index " + SbObject.Companion.getMCFPP_TEMP() + " 0");
        Function.addCommand("execute store result score list.size mcfpp_temp run data get storage mcfpp:system list.list");
        Function.addCommand("function mcfpp.lang:list/contains");
        returnVar.setValue(contains);
    }

    @MNIFunction(caller = "list", genericType = "E")
    public static void clear(NBTList caller){
        Function.addCommand(Commands.dataSetValue(caller.getNbtPath(), new ListTag()));
        // An empty value keeps the receiver's invariant element type.
        var empty = new NBTListConcrete(caller, new ArrayList<>());
        empty.setHasAssigned(true);
        empty.setHasStoredInStack(true);
        if (caller.isDynamic()) {
            var runtime = new NBTList(empty);
            runtime.setHasAssigned(true);
            runtime.setDynamic(true);
            caller.replacedBy(runtime);
        } else {
            caller.replacedBy(empty);
        }
    }
}
