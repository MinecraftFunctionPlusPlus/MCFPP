package top.mcfpp.mni;

import net.querz.nbt.io.SNBTUtil;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.Tag;
import top.mcfpp.Project;
import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.lang.*;
import top.mcfpp.lang.type.MCFPPNBTType;
import top.mcfpp.lang.value.MCFPPValue;
import top.mcfpp.lib.SbObject;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.NBTUtil;
import top.mcfpp.util.ValueWrapper;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public class NBTListConcreteData {

    @InsertCommand
    @MNIRegister(normalParams = {"E e"}, caller = "list<E>")
    public static void add(Var<?> e, NBTListConcrete caller){
        if(e instanceof MCFPPValue<?>){
            //都是确定的
            //直接添加值
            caller.getValue().add(NBTUtil.INSTANCE.toNBT(e));
        }else {
            //e不是确定的，但是list可能是确定的可能不是确定的
            caller.toDynamic(true);
            Command[] command;
            if(e.parentClass() != null) e = e.getTempVar();
            if(e.isTemp()) e.storeToStack();
            if(caller.getParent() != null){
                command = Commands.INSTANCE.selectRun(caller.getParent(),
                        new Command("data modify " +
                        "entity @s " +
                        "data." + caller.getIdentifier() + " " +
                        "append from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier() + " "));
            }else {
                command = new Command[]{ new Command("data modify " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                        "append from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier() + " ")};
            }
            Function.Companion.addCommands(command);
        }
    }

    @InsertCommand
    @MNIRegister(normalParams = {"list<E> list"}, caller = "list<E>")
    public static void addAll(NBTList list, NBTListConcrete<Tag<?>> caller){
        if(list instanceof MCFPPValue<?> ec){
            //都是确定的
            //直接添加值
            caller.getValue().addAll((Collection) ec.getValue());
        }else {
            caller.toDynamic(true);
            Command[] command;
            NBTBasedData<?> l;
            if(list.parentClass() != null) {
                l = (NBTBasedData<?>) list.getTempVar();
            }else{
                l = list;
            }
            if(caller.getParent() != null){
                command = Commands.INSTANCE.selectRun(caller.getParent(),"data modify " +
                        "entity @s " +
                        "data." + caller.getIdentifier() + " " +
                        "append from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + l.getIdentifier() + "[]");
            }else {
                command = new Command[]{ new Command("data modify " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                        "append from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + l.getIdentifier() + "[]")};
            }
            Function.Companion.addCommands(command);
        }
    }

    @InsertCommand
    @MNIRegister(normalParams = {"int index", "E e"}, caller = "list<E>")
    public static void insert(MCInt index, Var<?> e, NBTListConcrete caller){
        if(e instanceof MCFPPValue<?> && index instanceof MCIntConcrete indexC){
            //都是确定的
            //直接添加值
            caller.getValue().add(Objects.requireNonNull(indexC.getValue()), NBTUtil.INSTANCE.toNBT(e));
        }else if(index instanceof MCIntConcrete indexC){
            //e不是确定的，index是确定的，所以可以直接调用命令而不需要宏
            int i = indexC.getValue();
            caller.toDynamic(true);
            Command[] command;
            if(e.parentClass() != null) e = e.getTempVar();
            if(caller.getParent() != null){
                command = Commands.INSTANCE.selectRun(caller.getParent(), "data modify " +
                        "entity @s " +
                        "data." + caller.getIdentifier() + " " +
                        "insert " + i + " from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier());
            }else {
                command = new Command[] {new Command("data modify " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                        "insert " + i + " from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier())};
            }
            Function.Companion.addCommands(command);
        }else if(e instanceof MCFPPValue<?> eC){
            //e是确定的，index不是确定的，需要使用宏
            caller.toDynamic(true);
            Tag<?> tag = NBTUtil.INSTANCE.toNBT(e);
            try {
                if(caller.getParent() != null){
                    var command = Commands.INSTANCE.selectRun(caller.getParent(),
                            new Command("data modify " +
                                    "entity @s " +
                                    "data." + caller.getIdentifier() + " " +
                                    "insert").build("", "$" + index.getIdentifier(), true).build ("value " + SNBTUtil.toSNBT(tag), true)
                    );
                    Function.Companion.addCommand(command[0]);
                    var f = Commands.INSTANCE.buildMacroCommand(command[1]).build("with storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]", true);
                    Function.Companion.addCommand(f);
                } else {
                    var command = new Command("data modify " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                            "insert").build("", "$" + index.getIdentifier(), true).build("value " + SNBTUtil.toSNBT(tag), true);
                    var f = Commands.INSTANCE.buildMacroCommand(command).build("with storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]", true);
                    Function.Companion.addCommand(f);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else{
            //e是不确定的，index也不是确定的
            caller.toDynamic(true);
            if(e.parentClass() != null) e = e.getTempVar();
            Command[] command;
            if(caller.getParent() != null){
                command = Commands.INSTANCE.selectRun(caller.getParent(), new Command("data modify " +
                        "entity @s " +
                        "data." + caller.getIdentifier() + " " +
                        "insert").build("", "$" + index.getIdentifier(), true).build ("from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier(), true));
            } else {
                command = new Command[] {new Command("data modify " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + caller.getIdentifier() + " " +
                        "insert").build("", "$" + index.getIdentifier(), true).build("from " +
                        "storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]." + e.getIdentifier(), true)};
            }
            if(command.length == 2) Function.Companion.addCommand(command[0]);
            var f = Commands.INSTANCE.buildMacroCommand(command[command.length - 1]).build("with storage mcfpp:system " +
                    Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + caller.getStackIndex() + "]", true);
            Function.Companion.addCommand(f);
        }
    }

    @InsertCommand
    @MNIRegister(normalParams = {"E e"}, caller = "list<E>")
    public static void remove(Var<?> e, NBTListConcrete caller){
        //TODO NBT的api本来就没有remove(E e)这个方法，只有remove(int index)
    }

    @InsertCommand
    @MNIRegister(normalParams = {"int index"}, caller = "list<E>")
    public static void removeAt(MCInt index, NBTListConcrete caller){
        if(index instanceof MCIntConcrete indexC){
            //确定的
            caller.getValue().remove(indexC.getValue());
        }else {
            //不确定的
            NBTListData.removeAt(index, (NBTList) caller.toDynamic(true));
        }
    }

    @InsertCommand
    @MNIRegister(normalParams = {"E e"}, caller = "list<E>", returnType = "int")
    public static void indexOf(Var<?> e, NBTListConcrete caller, ValueWrapper<MCInt> returnVar){
        if(e instanceof MCFPPValue<?>){
            //确定的
            var i = caller.getValue().indexOf(NBTUtil.INSTANCE.toNBT(e));
            returnVar.setValue(new MCIntConcrete(i, UUID.randomUUID().toString()));
        }else {
            NBTListData.indexOf(e, (NBTList) caller.toDynamic(true), returnVar);
        }
    }

    @InsertCommand
    @MNIRegister(normalParams = {"E e"}, caller = "list<E>", returnType = "int")
    public static void lastIndexOf(Var<?> e, NBTListConcrete caller, ValueWrapper<MCInt> returnVar){
        if(e instanceof MCFPPValue<?>){
            //确定的
            for (int i = caller.getValue().size() - 1; i >= 0; i--) {
                if(caller.getValue().get(i).equals(NBTUtil.INSTANCE.toNBT(e))){
                    returnVar.setValue(new MCIntConcrete(i, UUID.randomUUID().toString()));
                    return;
                }
            }
            returnVar.setValue(new MCIntConcrete(-1, UUID.randomUUID().toString()));
        }else {
            NBTListData.lastIndexOf(e, (NBTList) caller.toDynamic(true), returnVar);
        }
    }

    @MNIRegister(normalParams = {"E e"}, caller = "list<E>", returnType = "bool")
    public static void contains(Var<?> e, NBTListConcrete caller, ValueWrapper<MCBool> returnVar){
        var n = e.toNBTVar();
        if(n instanceof NBTBasedDataConcrete<?> nC){
            var contains = caller.getValue().contains(nC.getValue());
            returnVar.setValue(new MCBoolConcrete(contains, UUID.randomUUID().toString()));
        }else {
            caller.toDynamic(false);
            NBTListData.contains(e, caller, returnVar);
        }
    }

    @MNIRegister(caller = "list<E>")
    public static void clear(NBTListConcrete<?> caller){
        caller.getValue().clear();
    }
}
