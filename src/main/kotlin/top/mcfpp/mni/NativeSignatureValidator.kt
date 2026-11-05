package top.mcfpp.mni

import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.ValueWrapper
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Checks JVM method signatures when registering global functions and data template members through MNIFunction. */
object NativeSignatureValidator {
    /** Returns ordinary declared and inherited JVM methods in reproducible signature order. */
    fun methods(type: Class<*>): List<Method> = (type.methods.asList() + type.declaredMethods.asList())
        .distinct()
        .filterNot { it.isBridge || it.isSynthetic }
        .sortedBy { it.toGenericString() }

    /** Checks public static visibility, JVM void result, and context or wrapper parameters. Reports signature errors and returns false on failure. */
    fun validate(function: NativeFunction): Boolean {
        val method = function.javaMethod
        val location = "Method ${method.name} in class ${method.declaringClass.name}"
        fun reject(reason: String): Boolean {
            LogProcessor.error("$location $reason")
            return false
        }
        if (!Modifier.isPublic(method.modifiers) || !Modifier.isStatic(method.modifiers))
            return reject("must be public static")
        if (method.returnType != Void.TYPE) return reject("must return JVM void")
        if (method.parameterTypes.contentEquals(arrayOf(NativeCallContext::class.java))) return true

        val valueCount = function.readOnlyParams.size + function.normalParams.size +
            if (function.caller != MCFPPPrivateType.Void) 1 else 0
        val hasResult = function.returnType != MCFPPPrivateType.Void
        val expectedCount = valueCount + if (hasResult) 1 else 0
        if (method.parameterCount != expectedCount)
            return reject("has wrong parameter count: expected $expectedCount, found ${method.parameterCount}")
        if (method.parameterTypes.take(valueCount).any { !Var::class.java.isAssignableFrom(it) })
            return reject("must use Var wrappers for read-only parameters, normal parameters and caller")
        if (hasResult && method.parameterTypes.last() != ValueWrapper::class.java)
            return reject("must use ValueWrapper for its result")
        return true
    }

    /** Tracks signatures accepted from one provider while allowing explicit inherited overrides. */
    class Registration {
        private val accepted = ArrayList<NativeFunction>()

        /** Validates [function] and records its signature. Reports a duplicate or invalid signature and returns false. */
        fun accept(function: NativeFunction): Boolean {
            if (!validate(function)) return false
            val previous = accepted.firstOrNull {
                it.identifier == function.identifier &&
                    it.readOnlyParams.map { param -> param.type } == function.readOnlyParams.map { param -> param.type } &&
                    it.normalParams.map { param -> param.type } == function.normalParams.map { param -> param.type }
            }
            if (previous != null) {
                LogProcessor.error(
                    "Duplicate native entry '${function.identifier}' in class ${function.javaMethod.declaringClass.name}: " +
                        "${previous.javaMethod.name} and ${function.javaMethod.name}"
                )
                return false
            }
            accepted.add(function)
            return true
        }
    }
}
