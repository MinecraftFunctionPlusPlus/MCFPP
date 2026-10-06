package top.mcfpp.mni;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativePrintOperations;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.nbt.*;
import top.mcfpp.util.*;

public class System {

    @MNIFunction(normalParams = {"any"}, returnType = "type")
    public static void typeOf(@NotNull Var<?> value, ValueWrapper<MCFPPTypeVar> returnValue){
        var re = new MCFPPTypeVar(value.getType(), TempPool.getVarIdentify());
        returnValue.setValue(re);
    }

    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = {"text"})
    public static void printText(NativeCallContext context){
        NativePrintOperations.INSTANCE.print(context);
    }

    @MNIFunction(identifier = "print", normalParams = {"string"})
    public static void printString(NativeCallContext context){
        NativePrintOperations.INSTANCE.print(context);
    }

    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = {"any"})
    public static void printAny(NativeCallContext context){
        NativePrintOperations.INSTANCE.print(context);
    }

    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = {"int"})
    public static void printInt(NativeCallContext context){
        NativePrintOperations.INSTANCE.print(context);
    }

    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = {"list"})
    public static void printList(NativeCallContext context){
        NativePrintOperations.INSTANCE.print(context);
    }
 
    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = {"dict"})
    public static void printDict(NativeCallContext context){
        NativePrintOperations.INSTANCE.print(context);
    }

    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = {"nbt"})
    public static void printNbt(NativeCallContext context){
        NativePrintOperations.INSTANCE.print(context);
    }

    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = {"DataObject"})
    public static void printObject(NativeCallContext context){
        NativePrintOperations.INSTANCE.print(context);
    }

    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = {"bool"})
    public static void printBool(NativeCallContext context){
        NativePrintOperations.INSTANCE.print(context);
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
