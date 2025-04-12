package top.mcfpp.mni;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.MCFPPValue;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.MCIntConcrete;
import top.mcfpp.core.lang.Var;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.core.lang.nbt.NBTList;
import top.mcfpp.core.lang.nbt.NBTListConcrete;
import top.mcfpp.util.TempPool;
import top.mcfpp.util.ValueWrapper;

import java.io.IOException;
import java.util.Collection;

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
    @MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E")
    public static void prepend(Var<?> e, NBTListConcrete caller) throws IOException {
        if(e instanceof MCFPPValue<?>){
            caller.getValue().addFirst(e);
        }else {
            NBTListData.add(e, (NBTList) caller.toDynamic(true));
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"list<E> list"}, caller = "list", genericType = "E")
    public static void prependAll(NBTList list, NBTListConcrete caller){
        if(list instanceof NBTListConcrete ec){
            for (var e : ec.getValue()){
                caller.getValue().addFirst(e);
            }
        }else {
            NBTListData.addAll(list, (NBTList) caller.toDynamic(true));
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"int index", "E e"}, caller = "list", genericType = "E")
    public static void insert(MCInt index, Var<?> e, NBTListConcrete caller) throws IOException {
        if(e instanceof MCFPPValue<?> && index instanceof MCIntConcrete indexC){
            caller.getValue().add(indexC.getValue(), e);
        }else if(index instanceof MCIntConcrete) {
            NBTListData.insert(index, e, (NBTList) caller.toDynamic(true));
        }
    }

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

    @MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E")
    public static void remove(@NotNull Var<?> var, NBTListConcrete caller){
        if(var instanceof MCFPPValue<?> vC){
            for (var e : caller.getValue()){
                if(e instanceof MCFPPValue<?> eC && ((Var<?>)eC).getType().equals(vC) && eC.getValue().equals(vC.getValue())){
                    caller.getValue().remove(e);
                    return;
                }
            }
            //没有找到,同时又是部分未知的
            if(!caller.isAllConcrete()){
                caller.toDynamic(true);
                NBTListData.remove(var, caller);
            }
        }else {
            caller.toDynamic(true);
            NBTListData.remove(var, caller);
        }
    }

    @InsertCommand
    @MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E", returnType = "int")
    public static void indexOf(Var<?> e, NBTListConcrete caller, ValueWrapper<MCInt> returnVar){
        if(e instanceof MCFPPValue<?>){
            //确定的
            var i = caller.getValue().indexOf(e);
            returnVar.setValue(new MCIntConcrete(i, TempPool.getVarIdentify()));
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
                    returnVar.setValue(new MCIntConcrete(i, TempPool.getVarIdentify()));
                    return;
                }
            }
            returnVar.setValue((MCInt) returnVar.getValue().assignedBy(new MCIntConcrete(-1, TempPool.getVarIdentify())));
        }else {
            NBTListData.lastIndexOf(e, (NBTList) caller.toDynamic(true), returnVar);
        }
    }

    @MNIFunction(normalParams = {"E e"}, caller = "list", genericType = "E", returnType = "bool")
    public static void contains(Var<?> e, NBTListConcrete caller, ValueWrapper<BaseBool> returnVar){
        if(e instanceof MCFPPValue eC){
            boolean contains = false;
            boolean toDynamic = false;
            for (var v : caller.getValue()){
                if(v instanceof MCFPPValue<?> vC && vC.getValue().equals(eC.getValue())){
                    contains = true;
                    break;
                }
                if(!toDynamic && !(v instanceof MCFPPValue<?>)){
                    toDynamic = true;
                }
            }
            if(!contains && toDynamic){
                caller.toDynamic(false);
                NBTListData.contains(e, caller, returnVar);
            }else {
                returnVar.setValue(returnVar.getValue().assignedBy(new ScoreBoolConcrete(contains, TempPool.getVarIdentify())));
            }
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
