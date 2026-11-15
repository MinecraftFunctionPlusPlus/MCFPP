package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMathOperations

/** Scalar mathematics. Commands use the selected target's numeric backend. */
@top.mcfpp.mni.annotation.NoExternalWrites
object MathData {
    @MNIFunction(normalParams = ["float"], returnType = "SinCos")
    @JvmStatic
    fun sinCos(context: NativeCallContext) = NativeMathOperations.apply(context, "sinCos")

    @MNIFunction(identifier = "abs", normalParams = ["int"], returnType = "int")
    @JvmStatic
    fun absInt(context: NativeCallContext) = NativeMathOperations.apply(context, "absInt")

    @MNIFunction(identifier = "abs", normalParams = ["float"], returnType = "float")
    @JvmStatic
    fun absFloat(context: NativeCallContext) = NativeMathOperations.apply(context, "absFloat")

    @MNIFunction(identifier = "min", normalParams = ["int", "int"], returnType = "int")
    @JvmStatic
    fun minInt(context: NativeCallContext) = NativeMathOperations.apply(context, "minInt")

    @MNIFunction(identifier = "max", normalParams = ["int", "int"], returnType = "int")
    @JvmStatic
    fun maxInt(context: NativeCallContext) = NativeMathOperations.apply(context, "maxInt")

    @MNIFunction(identifier = "min", normalParams = ["float", "float"], returnType = "float")
    @JvmStatic
    fun minFloat(context: NativeCallContext) = NativeMathOperations.apply(context, "minFloat")

    @MNIFunction(identifier = "max", normalParams = ["float", "float"], returnType = "float")
    @JvmStatic
    fun maxFloat(context: NativeCallContext) = NativeMathOperations.apply(context, "maxFloat")

    @MNIFunction(identifier = "floor", normalParams = ["float"], returnType = "float")
    @JvmStatic
    fun floor(context: NativeCallContext) = NativeMathOperations.apply(context, "floor")

    @MNIFunction(identifier = "ceil", normalParams = ["float"], returnType = "float")
    @JvmStatic
    fun ceil(context: NativeCallContext) = NativeMathOperations.apply(context, "ceil")

    @MNIFunction(identifier = "trunc", normalParams = ["float"], returnType = "float")
    @JvmStatic
    fun trunc(context: NativeCallContext) = NativeMathOperations.apply(context, "trunc")

    @MNIFunction(identifier = "round", normalParams = ["float"], returnType = "float")
    @JvmStatic
    fun round(context: NativeCallContext) = NativeMathOperations.apply(context, "round")

    @MNIFunction(identifier = "sqrt", normalParams = ["float"], returnType = "float")
    @JvmStatic
    fun sqrt(context: NativeCallContext) = NativeMathOperations.apply(context, "sqrt")

    @MNIFunction(identifier = "sin", normalParams = ["float"], returnType = "float")
    @JvmStatic
    fun sin(context: NativeCallContext) = NativeMathOperations.apply(context, "sin")

    @MNIFunction(identifier = "cos", normalParams = ["float"], returnType = "float")
    @JvmStatic
    fun cos(context: NativeCallContext) = NativeMathOperations.apply(context, "cos")

    @MNIFunction(identifier = "isqrt", normalParams = ["int"], returnType = "int")
    @JvmStatic
    fun isqrt(context: NativeCallContext) = NativeMathOperations.apply(context, "isqrt")

    @MNIFunction(identifier = "pow", normalParams = ["int", "int"], returnType = "int")
    @JvmStatic
    fun powInt(context: NativeCallContext) = NativeMathOperations.apply(context, "powInt")

    @MNIFunction(identifier = "pow", normalParams = ["float", "int"], returnType = "float")
    @JvmStatic
    fun powFloatInt(context: NativeCallContext) = NativeMathOperations.apply(context, "powFloatInt")

    @MNIFunction(identifier = "pow", normalParams = ["float", "float"], returnType = "float")
    @JvmStatic
    fun powFloat(context: NativeCallContext) = NativeMathOperations.apply(context, "powFloat")

    @MNIFunction(identifier = "nroot", normalParams = ["float", "int"], returnType = "float")
    @JvmStatic
    fun nroot(context: NativeCallContext) = NativeMathOperations.apply(context, "nroot")

    @MNIFunction(identifier = "atan2Fixed", normalParams = ["int", "int"], returnType = "float")
    @JvmStatic
    fun atan2Fixed(context: NativeCallContext) = NativeMathOperations.apply(context, "atan2Fixed")

    @MNIFunction(identifier = "hypot", normalParams = ["float", "float"], returnType = "float")
    @JvmStatic
    fun hypot2(context: NativeCallContext) = NativeMathOperations.apply(context, "hypot2")

    @MNIFunction(identifier = "hypot", normalParams = ["float", "float", "float"], returnType = "float")
    @JvmStatic
    fun hypot3(context: NativeCallContext) = NativeMathOperations.apply(context, "hypot3")

    @MNIFunction(identifier = "floorDiv", normalParams = ["int", "int"], returnType = "int")
    @JvmStatic
    fun floorDiv(context: NativeCallContext) = NativeMathOperations.apply(context, "floorDiv")

    @MNIFunction(identifier = "floorMod", normalParams = ["int", "int"], returnType = "int")
    @JvmStatic
    fun floorMod(context: NativeCallContext) = NativeMathOperations.apply(context, "floorMod")
}

