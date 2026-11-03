package top.mcfpp.mni.expand

import top.mcfpp.mni.api.FunctionSignature
import top.mcfpp.mni.api.ImmutableFunctionRef
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.ParameterView
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.api.TypeRef
import top.mcfpp.mni.api.immutableList
import java.lang.reflect.Method

/** Reflection preparation retains source expressions, never catalog or session handles. */
internal sealed interface PreparedMacroType {
    val source: SourceSpan
    data class Source(val expression: String, override val source: SourceSpan) : PreparedMacroType
    class Callback(val signature: PreparedInlineSignature, override val source: SourceSpan) : PreparedMacroType
}

internal data class PreparedMacroParameter(
    val name: String,
    val mode: ParamMode,
    val type: PreparedMacroType,
    val source: SourceSpan
)

internal class PreparedInlineSignature(
    parameters: List<PreparedMacroParameter>,
    val returnType: PreparedMacroType,
    val source: SourceSpan
) {
    val parameters: List<PreparedMacroParameter> = immutableList(parameters)
}

/** Names and JVM protocol are checked before any source type is resolved. */
internal class PreparedMacroSignature internal constructor(
    val identifier: String,
    val javaMethod: Method,
    parameters: List<PreparedMacroParameter>,
    val returnType: PreparedMacroType,
    val source: SourceSpan
) {
    val parameters: List<PreparedMacroParameter> = immutableList(parameters)
}

/** A validated macro declaration. Host invocation belongs to the expansion session. */
internal class MacroFunction internal constructor(
    val namespace: String,
    val identifier: String,
    val javaMethod: Method,
    val reference: ImmutableFunctionRef<*>,
    val returnsVoid: Boolean,
    val prepared: PreparedMacroSignature
) {
    val qualifiedName: String get() = "$namespace:$identifier"
    val signature: FunctionSignature<*> get() = reference.signature
    val parameters: List<ParameterView> get() = signature.parameters()
    val returnType: TypeRef<*> get() = signature.returnType()
}
