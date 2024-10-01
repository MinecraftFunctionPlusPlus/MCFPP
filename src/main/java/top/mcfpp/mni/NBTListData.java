package top.mcfpp.mni;

import net.querz.nbt.io.SNBTUtil;
import net.querz.nbt.tag.Tag;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.Project;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.MCFPPValue;
import top.mcfpp.core.lang.bool.MCBool;
import top.mcfpp.lib.NBTPath;
import top.mcfpp.lib.SbObject;
import top.mcfpp.lib.Storage;
import top.mcfpp.lib.StorageSource;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.NBTUtil;
import top.mcfpp.util.ValueWrapper;

import java.io.IOException;
import java.util.Objects;

public class NBTListData {

    static NBTBasedData list = new NBTBasedData("list.list");
    static NBTBasedData element = new NBTBasedData("list.element");
    static MCInt index = new MCInt("list.index");
    static MCBool contains = new MCBool("list.contains");

    static {
        list.setNbtPath(new NBTPath(new StorageSource(Storage.Companion.getMCFPP_SYSTEM().toString())).memberIndex("list.list"));
        element.setNbtPath(new NBTPath(new StorageSource(Storage.Companion.getMCFPP_SYSTEM().toString())).memberIndex("list.element"));
        index.setObj(SbObject.Companion.getMCFPP_TEMP());
        contains.setObj(SbObject.Companion.getMCFPP_TEMP());
    }


    @MNIFunction(normalParams = {"E e"}, caller = "list<E>")
    public static void add(Var<?> e, NBTList caller){
        if(e instanceof MCFPPValue<?>){
            //e是确定的
            Tag<?> tag = NBTUtil.INSTANCE.toNBT(e);
            String command;
            try{
                if(caller.parentClass() != null){
                    command = "data modify " +
                            "entity @s " +
                            "data." + caller.getIdentifier() + " " +
                            "append value " + SNBTUtil.toSNBT(tag);
                }else {
                    command = "data modify " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                            "append value " + SNBTUtil.toSNBT(tag);
                }
            }catch (Exception ex){
                throw new RuntimeException(ex);
            }
            Function.Companion.addCommand(command);
        }else {
            //e不是确定的
            String command;
            if (e.parentClass() != null) e = e.getTempVar();
            if (caller.parentClass() != null) {
                command = "data modify " +
                        "entity @s " +
                        "data." + caller.getIdentifier() + " " +
                        "append from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier() + " ";
            } else {
                command = "data modify " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                        "append from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier() + " ";
            }
            Function.Companion.addCommand(command);
        }
    }

    @MNIFunction(normalParams = {"list<E> list"}, caller = "list<E>")
    public static void addAll(@NotNull NBTList list, NBTList caller){
        String command;
        NBTBasedData l;
        if(list.parentClass() != null) {
            l = list.getTempVar();
        }else if((NBTList)list instanceof NBTListConcrete eC){
            l = list;
            eC.toDynamic(false);
        }else{
            l = list;
        }
        if(caller.parentClass() != null){
            command = "data modify " +
                    "entity @s " +
                    "data." + caller.getIdentifier() + " " +
                    "append from " +
                    "storage mcfpp:system " +
                    Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + l.getIdentifier() + "[]";
        }else {
            command = "data modify " +
                    "storage mcfpp:system " +
                    Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                    "append from " +
                    "storage mcfpp:system " +
                    Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + l.getIdentifier() + "[]";
        }
        Function.Companion.addCommand(command);
    }

    @MNIFunction(normalParams = {"int index, E e"}, caller = "list<E>")
    public static void insert(MCInt index, Var<?> e, NBTList caller){
        if(e instanceof MCFPPValue<?> && (MCInt)index instanceof MCIntConcrete indexC){
            //都是确定的
            Tag<?> tag = NBTUtil.INSTANCE.toNBT(e);
            int i = indexC.getValue();
            String command;
            try {
                if (caller.parentClass() != null) {
                    command = "data modify " +
                            "entity @s " +
                            "data." + caller.getIdentifier() + " " +
                            "insert " + i + " value " + SNBTUtil.toSNBT(tag);
                } else {
                    command = "data modify " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                            "insert " + i + " value " + SNBTUtil.toSNBT(tag);
                }
            }catch (IOException ex){
                throw new RuntimeException(ex);
            }
            Function.Companion.addCommand(command);
        }else if((MCInt)index instanceof MCIntConcrete indexC){
            //e不是确定的，index是确定的，所以可以直接调用命令而不需要宏
            int i = indexC.getValue();
            Command[] command;
            if(e.parentClass() != null) e = e.getTempVar();
            if(caller.parentClass() != null){
                command = Commands.INSTANCE.selectRun(Objects.requireNonNull(caller.getParent()),"data modify " +
                        "entity @s " +
                        "data." + caller.getIdentifier() + " " +
                        "insert " + i + " from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier(), true);
            }else {
                command = new Command[]{new Command("data modify " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                        "insert " + i + " from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier())};
            }
            Function.Companion.addCommands(command);
        }else if(e instanceof MCFPPValue<?>){
            //e是确定的，index不是确定的，需要使用宏
            Tag<?> tag = NBTUtil.INSTANCE.toNBT(e);
            try {
                if(caller.getParent() != null){
                    var command = Commands.INSTANCE.selectRun(caller.getParent(), new Command("data modify " +
                            "entity @s " +
                            "data." + caller.getIdentifier() + " " +
                            "insert ").buildMacro(index, true).build ("value " + SNBTUtil.toSNBT(tag), true), true);
                    Function.Companion.addCommand(command[0]);
                    var f = command[1].buildMacroFunction();
                    Function.Companion.addCommands(f);
                } else {
                    var command = new Command("data modify " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                            "insert ").buildMacro(index, true).build("value " + SNBTUtil.toSNBT(tag), true);
                    var f = command.buildMacroFunction();
                    Function.Companion.addCommands(f);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else{
            //e是不确定的，index也不是确定的
            if(e.parentClass() != null) e = e.getTempVar();
            if(caller.getParent() != null){
                var command = Commands.INSTANCE.selectRun(caller.getParent(), new Command("data modify " +
                        "entity @s " +
                        "data." + caller.getIdentifier() + " " +
                        "insert ").buildMacro(index, true)
                        .build (" from storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier(), true), true);
                Function.Companion.addCommand(command[0]);
                var f = command[1].buildMacroFunction();
                Function.Companion.addCommands(f);
            } else {
                var command = new Command("data modify " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                        "insert ").buildMacro(index, true)
                        .build("from storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier(), true);
                var f = command.buildMacroFunction();
                Function.Companion.addCommands(f);
            }
        }
    }

    // TODO
    // @MNIRegister(normalParams = {"E e"}, caller = "list<E>")
    //public static void remove(Var<?> e, NBTList caller){
    //    throw new NotImplementedError();
    //}

    @MNIFunction(normalParams = {"int index"}, caller = "list<E>")
    public static void removeAt(MCInt index, NBTList caller){
        if(index instanceof MCIntConcrete){
            int i = ((MCIntConcrete) index).getValue();
            if(caller.getParent() != null){
                var command = Commands.INSTANCE.selectRun(caller.getParent(), new Command(
                        "data remove entity @s " +
                        "data." + caller.getIdentifier() + "[" + i + "]"), true);
                Function.Companion.addCommands(command);
            }else {
                var command = new Command(
                        "data remove storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + "[" + i + "]");
                Function.Companion.addCommand(command);
            }
        }else {
            if(caller.getParent() != null){
                var command = Commands.INSTANCE.selectRun(caller.getParent(), new Command(
                        "data remove entity @s " +
                        "data." + caller.getIdentifier() + "[").buildMacro(index, true).build("]", true), true);
                Function.Companion.addCommand(command[0]);
                var f = command[1].buildMacroFunction();
                Function.Companion.addCommands(f);
            }else {
                var command = new Command(
                        "data remove storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + "[").buildMacro(index, true).build("]", true);
                var f = command.buildMacroFunction();
                Function.Companion.addCommands(f);
            }
        }
    }

    @MNIFunction(normalParams = {"E e"}, caller = "list<E>", returnType = "int")
    public static void indexOf(@NotNull Var<?> e, NBTList caller, ValueWrapper<MCInt> returnVar){
        var n = e.toNBTVar();
        element.assignedBy(n);
        element.assignedBy(caller);
        Function.Companion.addCommand("scoreboard players set list.index " + SbObject.Companion.getMCFPP_TEMP() + " 0");
        Function.Companion.addCommand("execute store result score list.size mcfpp_temp run data get storage mcfpp:system list.list");
        Function.Companion.addCommand("function mcfpp.lang:list/index_of");
        returnVar.setValue(index);
    }

    @MNIFunction(normalParams = {"E e"}, caller = "list<E>", returnType = "int")
    public static void lastIndexOf(Var<?> e, NBTList caller, ValueWrapper<MCInt> returnVar){
        var n = e.toNBTVar();
        element.assignedBy(n);
        element.assignedBy(caller);
        Function.Companion.addCommand("scoreboard players set list.index " + SbObject.Companion.getMCFPP_TEMP() + " 0");
        Function.Companion.addCommand("execute store result score list.size mcfpp_temp run data get storage mcfpp:system list.list");
        Function.Companion.addCommand("function mcfpp.lang:list/last_index_of");
        returnVar.setValue(index);
    }

    @MNIFunction(normalParams = {"E e"}, caller = "list<E>", returnType = "bool")
    public static void contains(Var<?> e, NBTList caller, ValueWrapper<MCBool> returnVar){
        var n = e.toNBTVar();
        element.assignedBy(n);
        element.assignedBy(caller);
        Function.Companion.addCommand("scoreboard players set list.index " + SbObject.Companion.getMCFPP_TEMP() + " 0");
        Function.Companion.addCommand("execute store result score list.size mcfpp_temp run data get storage mcfpp:system list.list");
        Function.Companion.addCommand("function mcfpp.lang:list/contains");
        returnVar.setValue(contains);
    }

    @MNIFunction(caller = "list<E>")
    public static void clear(NBTList caller){
        caller.assignedBy(NBTListConcrete.Companion.getEmpty());
    }
}