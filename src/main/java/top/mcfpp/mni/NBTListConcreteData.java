package top.mcfpp.mni;

import net.querz.nbt.io.SNBTUtil;
import net.querz.nbt.tag.Tag;
import top.mcfpp.Project;
import top.mcfpp.command.Command;
import top.mcfpp.lang.*;
import top.mcfpp.lang.value.MCFPPValue;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.MacroHelper;
import top.mcfpp.util.NBTUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

public class NBTListConcreteData extends BaseMNIMethodContainer {

    public static final NBTListConcreteData INSTANCE = new NBTListConcreteData();

    static {
        //list<E>.add(E e)
        methods.put("add", (readOnlyArgs, normalArgs, caller, returnVar) -> {
            //由于类型检查，必然是可以通过的
            var e = normalArgs[0];  //要添加的成员
            var list = (NBTListConcrete)caller;
            if(e instanceof MCFPPValue<?>){
                //都是确定的
                //直接添加值
                list.getValue().add(NBTUtil.INSTANCE.toNBT(e));
            }else {
                //e不是确定的，但是list可能是确定的可能不是确定的
                list.toDynamic(true);
                String command = "";
                if(e.parentClass() != null) e = e.getTempVar();
                if(list.parentClass() != null){
                    command = "data modify " +
                            "entity @s " +
                            "data." + list.getIdentifier() + " " +
                            "append from " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + e.getIdentifier() + " ";
                }else {
                    command = "data modify " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + list.getIdentifier() + " " +
                            "append from " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + e.getIdentifier() + " ";
                }
                Function.Companion.addCommand(command);
            }
            return null;
        });

        //list<E>.addAll(list<E> list)
        methods.put("addAll", (readOnlyArgs, normalArgs, caller, returnVar) -> {
            var e = (NBTList)normalArgs[0];  //要添加的成员
            var list = (NBTListConcrete)caller;
            if(e instanceof MCFPPValue<?> ec){
                //都是确定的
                //直接添加值
                list.getValue().addAll((Collection) ec.getValue());
            }else {
                list.toDynamic(true);
                String command;
                NBTBasedData<?> l;
                if(e.parentClass() != null) {
                    l = (NBTBasedData<?>) e.getTempVar();
                }else{
                    l = e;
                }
                if(list.parentClass() != null){
                    command = "data modify " +
                            "entity @s " +
                            "data." + list.getIdentifier() + " " +
                            "append from " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + l.getIdentifier() + "[]";
                }else {
                    command = "data modify " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + list.getIdentifier() + " " +
                            "append from " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + l.getIdentifier() + "[]";
                }
                Function.Companion.addCommand(command);
            }
            return null;
        });

        //list<E>.insert(int index, E e)
        methods.put("insert", (readOnlyArgs, normalArgs, caller, returnVar) -> {
            //由于类型检查，必然是可以通过的
            var index = (MCInt)normalArgs[0];   //索引
            var e = (Var<?>) normalArgs[1];  //要添加的成员
            var list = (NBTListConcrete)caller;
            if(e instanceof MCFPPValue<?> && index instanceof MCIntConcrete indexC){
                //都是确定的
                //直接添加值
                list.getValue().add(Objects.requireNonNull(indexC.getValue()), NBTUtil.INSTANCE.toNBT(e));
            }else if(index instanceof MCIntConcrete indexC){
                //e不是确定的，index是确定的，所以可以直接调用命令而不需要宏
                int i = indexC.getValue();
                list.toDynamic(true);
                String command = "";
                if(e.parentClass() != null) e = e.getTempVar();
                if(list.parentClass() != null){
                    command = "data modify " +
                            "entity @s " +
                            "data." + list.getIdentifier() + " " +
                            "insert " + i + " from " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + e.getIdentifier();
                }else {
                    command = "data modify " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + list.getIdentifier() + " " +
                            "insert " + i + " from " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + e.getIdentifier();
                }
                Function.Companion.addCommand(command);
            }else if(e instanceof MCFPPValue<?> eC){
                //e是确定的，index不是确定的，需要使用宏
                list.toDynamic(true);
                Tag<?> tag = NBTUtil.INSTANCE.toNBT(e);
                Command command;
                try {
                    if(list.parentClass() != null){
                            command = new Command("data modify " +
                                    "entity @s " +
                                    "data." + list.getIdentifier() + " " +
                                    "insert").build("", index.getIdentifier()).build ("value " + SNBTUtil.toSNBT(tag));
                    } else {
                        command = new Command("data modify " +
                                "storage mcfpp:system " +
                                Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + list.getIdentifier() + " " +
                                "insert").build("", index.getIdentifier()).build("value " + SNBTUtil.toSNBT(tag));
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                var f = MacroHelper.INSTANCE.addMacroCommand(command).build("with storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]");
                Function.Companion.addCommand(f);
            } else{
                //e是不确定的，index也不是确定的
                list.toDynamic(true);
                if(e.parentClass() != null) e = e.getTempVar();
                Command command;
                if(list.parentClass() != null){
                    command = new Command("data modify " +
                            "entity @s " +
                            "data." + list.getIdentifier() + " " +
                            "insert").build("", index.getIdentifier()).build ("from " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + e.getIdentifier());
                } else {
                    command = new Command("data modify " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + list.getIdentifier() + " " +
                            "insert").build("", index.getIdentifier()).build("from " +
                            "storage mcfpp:system " +
                            Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + e.getIdentifier());
                }
                var f = MacroHelper.INSTANCE.addMacroCommand(command).build("with storage mcfpp:system " +
                        Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]");
                Function.Companion.addCommand(f);
            }
            return null;
        });

        methods.put("remove", (readOnlyArgs, normalArgs, caller, returnVar) -> {
            return null;
        });

        methods.put("removeAt", (readOnlyArgs, normalArgs, caller, returnVar) -> {
            return null;
        });

        methods.put("indexOf", (readOnlyArgs, normalArgs, caller, returnVar) -> {
            return null;
        });

        methods.put("lastIndexOf", (readOnlyArgs, normalArgs, caller, returnVar) -> {
            return null;
        });

        methods.put("contains", (readOnlyArgs, normalArgs, caller, returnVar) -> {
            return null;
        });

        methods.put("clear", (readOnlyArgs, normalArgs, caller, returnVar) -> {
            return null;
        });
    }
}
