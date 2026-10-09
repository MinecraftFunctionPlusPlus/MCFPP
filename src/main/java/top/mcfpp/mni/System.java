package top.mcfpp.mni;

import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativePrintOperations;
import top.mcfpp.backend.NativeDiagnosticOperations;
import top.mcfpp.mni.annotation.NoExternalWrites;

@NoExternalWrites
public class System {

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
    @NoExternalWrites
    public static void debug(NativeCallContext context){
        NativeDiagnosticOperations.INSTANCE.debug(context);
    }

    @MNIFunction(normalParams = {"string"})
    @NoExternalWrites
    public static void info(NativeCallContext context){
        NativeDiagnosticOperations.INSTANCE.info(context);
    }

    @MNIFunction(normalParams = {"string"})
    @NoExternalWrites
    public static void warn(NativeCallContext context){
        NativeDiagnosticOperations.INSTANCE.warn(context);
    }

    @MNIFunction(normalParams = {"string"})
    @NoExternalWrites
    public static void error(NativeCallContext context){
        NativeDiagnosticOperations.INSTANCE.error(context);
    }
}
