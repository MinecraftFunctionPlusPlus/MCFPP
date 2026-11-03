package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*

/**
 * A complete, session-owned argument binding for one selected macro call.
 *
 * [orderedPrelude] must be imported together with [arguments]: it evaluates every VALUE argument
 * exactly once in original source order (including effects of arguments the host never reads) and
 * captures REF bases/indices at their argument position. VALUE inputs wrap the already projected
 * temporary expression; CONST and REF inputs retain the projector's canonical wrappers and their
 * physical aliases instead of copying an lvalue payload.
 */
internal class BoundMacroCall(
    val macro: MacroFunction,
    val site: SourceSpan,
    val orderedPrelude: Block,
    arguments: List<MacroInput>,
    readonlyArguments: List<TypedConstant>,
    val scope: ScopeId
) {
    val arguments: List<MacroInput> = immutableList(arguments)
    val readonlyArguments: List<TypedConstant> = immutableList(readonlyArguments)

    init {
        require(this.arguments.size == macro.parameters.size) {
            "Macro argument count differs from the prepared signature"
        }
        require(this.readonlyArguments.size == macro.reference.readonlyParameters().size) {
            "Macro readonly argument count differs from the prepared signature"
        }
    }
}

/**
 * Binds one already selected [SourceCall] to its prepared macro declaration. The projector is the
 * only evaluator: this class maps its output to the public [MacroInput] protocol and never invokes a
 * host provider. INLINE arguments stay deferred source descriptors; their bodies are bound by the
 * inline expander only at an actual insertion with that insertion's CONST facts.
 */
internal class MacroArgumentBinder(private val environment: SourceProjectionEnvironment) {
    private val resolver = MacroCallResolver(environment)
    private val projector = SourceCodeProjector(environment)

    fun bind(call: SourceCall): BoundMacroCall = environment.checked(call.source) {
        val macro = resolver.resolve(call)
        val projected = projector.arguments(call)
        val parameters = macro.parameters
        if (projected.arguments.size != parameters.size)
            throw SourceBindingFailure(
                call.source,
                "Source projection produced ${projected.arguments.size} inputs for ${parameters.size} macro parameters"
            )
        val inputs: List<MacroInput> = projected.arguments.mapIndexed { index, argument ->
            val parameter = parameters[index]
            when (val payload = argument.payload) {
                is ArgumentPayload.Value -> {
                    if (parameter.mode() != ParamMode.VALUE)
                        throw SourceBindingFailure(
                            payload.code.source(),
                            "Macro parameter '${parameter.name()}' is ${parameter.mode()} but its projected input is a VALUE"
                        )
                    environment.session.arena.value(payload.expression)
                }
                is ArgumentPayload.Constant -> {
                    if (parameter.mode() != ParamMode.CONST)
                        throw SourceBindingFailure(
                            payload.code.source(),
                            "Macro parameter '${parameter.name()}' is ${parameter.mode()} but its projected input is a CONST"
                        )
                    payload.constant
                }
                is ArgumentPayload.Reference -> {
                    if (parameter.mode() != ParamMode.REF)
                        throw SourceBindingFailure(
                            payload.code.source(),
                            "Macro parameter '${parameter.name()}' is ${parameter.mode()} but its projected input is a REF"
                        )
                    payload.reference
                }
                is ArgumentPayload.Inline -> {
                    if (parameter.mode() != ParamMode.INLINE)
                        throw SourceBindingFailure(
                            payload.code.source(),
                            "Macro parameter '${parameter.name()}' is ${parameter.mode()} but its projected input is an INLINE callback"
                        )
                    payload.callback
                }
            }
        }
        BoundMacroCall(macro, call.source, projected.orderedPrelude, inputs, projected.readonlyArguments, projected.scope)
    }
}
