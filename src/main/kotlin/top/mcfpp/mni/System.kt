package top.mcfpp.mni

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativePrintOperations
import top.mcfpp.backend.NativeDiagnosticOperations
import top.mcfpp.mni.annotation.NoExternalWrites

/** Implements chat output and compiler messages for mcfpp.lang. */
@NoExternalWrites
object System {

    /** Displays the supplied value to all players through tellraw. */
    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = ["text"])
    @JvmStatic
    fun printText(context: NativeCallContext) {
        NativePrintOperations.print(context)
    }

    /** Displays the supplied value to all players through tellraw. */
    @MNIFunction(identifier = "print", normalParams = ["string"])
    @JvmStatic
    fun printString(context: NativeCallContext) {
        NativePrintOperations.print(context)
    }

    /** Displays the supplied value to all players through tellraw. */
    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = ["any"])
    @JvmStatic
    fun printAny(context: NativeCallContext) {
        NativePrintOperations.print(context)
    }

    /** Displays the supplied value to all players through tellraw. */
    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = ["int"])
    @JvmStatic
    fun printInt(context: NativeCallContext) {
        NativePrintOperations.print(context)
    }

    /** Displays the supplied value to all players through tellraw. */
    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = ["nbt"])
    @JvmStatic
    fun printNbt(context: NativeCallContext) {
        NativePrintOperations.print(context)
    }

    /** Displays the supplied value to all players through tellraw. */
    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = ["DataObject"])
    @JvmStatic
    fun printObject(context: NativeCallContext) {
        NativePrintOperations.print(context)
    }

    /** Displays the Boolean value to all players as 1 or 0. */
    @InsertCommand
    @MNIFunction(identifier = "print", normalParams = ["bool"])
    @JvmStatic
    fun printBool(context: NativeCallContext) {
        NativePrintOperations.print(context)
    }

    /** Provides a JVM breakpoint location during compilation. */
    @MNIFunction
    @NoExternalWrites
    @JvmStatic
    fun debug(context: NativeCallContext) {
        NativeDiagnosticOperations.debug(context)
    }

    /** Reports an information message during compilation. */
    @MNIFunction(normalParams = ["string"])
    @NoExternalWrites
    @JvmStatic
    fun info(context: NativeCallContext) {
        NativeDiagnosticOperations.info(context)
    }

    /** Reports a warning during compilation. */
    @MNIFunction(normalParams = ["string"])
    @NoExternalWrites
    @JvmStatic
    fun warn(context: NativeCallContext) {
        NativeDiagnosticOperations.warn(context)
    }

    /** Reports an error during compilation. */
    @MNIFunction(normalParams = ["string"])
    @NoExternalWrites
    @JvmStatic
    fun error(context: NativeCallContext) {
        NativeDiagnosticOperations.error(context)
    }
}
