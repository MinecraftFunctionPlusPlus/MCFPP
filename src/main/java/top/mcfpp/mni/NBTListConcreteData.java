package top.mcfpp.mni;

import net.querz.nbt.io.SNBTUtil;
import net.querz.nbt.tag.Tag;
import top.mcfpp.Project;
import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.bool.ScoreBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.core.lang.nbt.NBTBasedData;
import top.mcfpp.core.lang.nbt.NBTList;
import top.mcfpp.core.lang.nbt.NBTListConcrete;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.NBTUtil;
import top.mcfpp.util.TempPool;
import top.mcfpp.util.ValueWrapper;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

@SuppressWarnings({"unchecked","rawtypes"})
public class NBTListConcreteData {

    @InsertCommand
    @MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E")
    public static void add(Var<?> e, NBTListConcrete caller) throws IOException {
        if(e instanceof MCFPPValue<?>){
            caller.getValue().add(e);
        }else {
            NBTListData.add(e, (NBTList) caller.toDynamic(true));
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"list<E> list"}, caller = "list", genericType = "E")
    public static void addAll(NBTList list, NBTListConcrete caller){
        if(list instanceof MCFPPValue<?> ec){
            caller.getValue().addAll((Collection) ec.getValue());
        }else {
            NBTListData.addAll(list, (NBTList) caller.toDynamic(true));
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"int index", "E e"}, caller = "list", genericType = "E")
    public static void insert(MCInt index, Var<?> e, NBTListConcrete caller) throws IOException {
        if(e instanceof MCFPPValue<?> && index instanceof MCIntConcrete indexC){
            caller.getValue().add(indexC.getValue(), e);
        }else if(index instanceof MCIntConcrete indexC) {
            NBTListData.insert(index, e, (NBTList) caller.toDynamic(true));
        }
    }

    //@InsertCommand
    //@MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E")
    //public static void remove(Var<?> e, NBTListConcrete caller){
    //    //TODO NBT的api本来就没有remove(E e)这个方法，只有remove(int index)
    //}

    @InsertCommand
    @MNIFunction(normalParams = {"int index"}, caller = "list", genericType = "E")
    public static void removeAt(MCInt index, NBTListConcrete caller){
        if(index instanceof MCIntConcrete indexC){
            //确定的
            caller.getValue().remove((int)indexC.getValue());
        }else {
            //不确定的
            NBTListData.removeAt(index, (NBTList) caller.toDynamic(true));
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E", returnType = "int")
    public static void indexOf(Var<?> e, NBTListConcrete caller, ValueWrapper<MCInt> returnVar){
        if(e instanceof MCFPPValue<?>){
            //确定的
            var i = caller.getValue().indexOf(e);
            returnVar.setValue(new MCIntConcrete(i, TempPool.INSTANCE.getVarIdentify()));
        }else {
            NBTListData.indexOf(e, (NBTList) caller.toDynamic(true), returnVar);
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E", returnType = "int")
    public static void lastIndexOf(Var<?> e, NBTListConcrete caller, ValueWrapper<MCInt> returnVar){
        if(e instanceof MCFPPValue<?>){
            //确定的
            for (int i = caller.getValue().size() - 1; i >= 0; i--) {
                if(caller.getValue().get(i).equals(e)){
                    returnVar.setValue(new MCIntConcrete(i, TempPool.INSTANCE.getVarIdentify()));
                    return;
                }
            }
            returnVar.setValue((MCInt) returnVar.getValue().assignedBy(new MCIntConcrete(-1, TempPool.INSTANCE.getVarIdentify())));
        }else {
            NBTListData.lastIndexOf(e, (NBTList) caller.toDynamic(true), returnVar);
        }
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E", returnType = "bool")
    public static void contains(Var<?> e, NBTListConcrete caller, ValueWrapper<BaseBool> returnVar){
        if(e instanceof MCFPPValue eC){
            var contains = caller.getValue().contains(eC.getValue());
            returnVar.setValue(returnVar.getValue().assignedBy(new ScoreBoolConcrete(contains, TempPool.INSTANCE.getVarIdentify())));
        }else {
            caller.toDynamic(false);
            NBTListData.contains(e, caller, returnVar);
        }
    }

    @MNIFunction(caller = "list")
    public static void clear(NBTListConcrete caller){
        caller.getValue().clear();
    }
}
