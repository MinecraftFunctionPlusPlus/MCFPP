package top.mcfpp.lang;

import kotlin.NotImplementedError;
import kotlin.jvm.functions.Function4;
import net.querz.nbt.io.SNBTUtil;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.Tag;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.Project;
import top.mcfpp.annotations.MCFPPNative;
import top.mcfpp.lib.function.Function;
import top.mcfpp.lib.function.MNIMethodContainer;
import top.mcfpp.util.LogProcessor;
import top.mcfpp.util.NBTUtil;
import top.mcfpp.util.ValueWrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class NBTListData extends MNIMethodContainer {

    static HashMap<String, Function4<Var<?>[], Var<?>[], CanSelectMember, ValueWrapper<Var<?>>, java.lang.Void>> methods;

    static {
        methods = new HashMap<>();
        methods.put("add", (readOnlyArgs, normalArgs, caller, returnVar) -> {
            //由于类型检查，必然是可以通过的
            var e = normalArgs[0];  //要添加的成员
            var list = (NBTList)caller;
            if(list.isConcrete() && e.isConcrete()){
                //都是确定的
                //直接添加值
                list.getJavaValue().add(NBTUtil.INSTANCE.toNBT(e));
            }else if(!e.isConcrete()){
                //e不是确定的，但是list可能是确定的可能不是确定的
                if(list.isConcrete()) list.toDynamic();
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
            }else {
                //e是确定的，而list是不确定的
                Tag<?> tag = NBTUtil.INSTANCE.toNBT(e);
                String command;
                try{
                    if(list.parentClass() != null){
                        command = "data modify " +
                                "entity @s " +
                                "data." + list.getIdentifier() + " " +
                                "append value " + SNBTUtil.toSNBT(tag);
                    }else {
                        command = "data modify " +
                                "storage mcfpp:system " +
                                Project.INSTANCE.getCurrNamespace() + ".stack_frame[" + list.getStackIndex() + "]." + list.getIdentifier() + " " +
                                "append value " + SNBTUtil.toSNBT(tag);
                    }
                }catch (Exception ex){
                    throw new RuntimeException(ex);
                }
                Function.Companion.addCommand(command);
            }
            return null;
        });

        methods.put("addAll", (readOnlyArgs, normalArgs, caller, returnVar) -> {
            var e = normalArgs[0];  //要添加的成员
            var list = (NBTList)caller;
            return null;
        });

        methods.put("insert", (readOnlyArgs, normalArgs, caller, returnVar) -> {
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

    @NotNull
    @Override
    public Function4<Var<?>[], Var<?>[], CanSelectMember, ValueWrapper<Var<?>>, java.lang.Void> getMNIMethod(@NotNull String name) {
        return methods.get(name);
    }
}
