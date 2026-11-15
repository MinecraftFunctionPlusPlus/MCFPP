package top.mcfpp.core.lang.context

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.type.TypeRelations

/** Lexical dependency lookup is separate from the current Minecraft command source. */
object ContextParameters {
    private val candidates = ThreadLocal<List<List<ExecutionContextVar>>>()
    private val explicit = ThreadLocal<Map<String, Var<*>>>()
    private val executorKnown = ThreadLocal<Boolean>()
    private val frameType = ThreadLocal<MCFPPExecutionContextType>()
    internal val currentExecutorKnown: Boolean get() = executorKnown.get() == true
    internal val currentFrameType: MCFPPExecutionContextType get() = frameType.get() ?: ContextTypes.ExecutionContext

    internal fun syntax(function: Function): P.ContextParametersContext? {
        val source = function.sourceDeclarationContext
        return when (source) {
            is P.FunctionDeclarationPartContext -> source.contextParameters()
                ?: (source.parent as? P.InlineFunctionDeclarationContext)?.contextParameters()
            is P.ExtensionFunctionDeclarationContext -> source.contextParameters()
            else -> null
        }
    }

    internal fun prepare(function: Function) {
        if (function.contextParametersPrepared || function.sourceDeclarationContext == null) return
        function.contextParametersPrepared = true
        val syntax = syntax(function) ?: return
        if (function is top.mcfpp.compiletime.CompileTimeFunction) {
            top.mcfpp.util.LogProcessor.error("Execution context parameters require a synchronous runtime function")
            return
        }
        val readonly = (function as? top.mcfpp.model.Generic<*>)?.readOnlyParams
            ?: (function as? top.mcfpp.model.function.NativeFunction)?.readOnlyParams.orEmpty()
        val names = (function.normalParams + readonly).map { it.identifier }.toMutableSet()
        for (parameter in syntax.contextParameter()) {
            val name = parameter.Identifier().text
            if (!names.add(name)) { top.mcfpp.util.LogProcessor.error("Duplicate parameter '$name'"); continue }
            val type = ContextTypes.named(parameter.type().text) as? MCFPPExecutionContextType
            if (type == null) { top.mcfpp.util.LogProcessor.error("Context parameter '$name' requires a named Context type"); continue }
            function.declaredContextParameters.add(FunctionParam(type, name, function))
            function.scope.putVar(name, type.buildUnConcrete(name), true)
        }
    }

    fun resolve(function: Function): LinkedHashMap<String, Var<*>> {
        val provided = explicit.get().orEmpty()
        val unknown = provided.keys - function.contextParams.map { it.identifier }.toSet()
        if (unknown.isNotEmpty()) throw ContextAccessException("Unknown context parameter(s): ${unknown.joinToString()}")
        return linkedMapOf<String, Var<*>>().apply {
            for (parameter in function.contextParams) {
                val chosen = provided[parameter.identifier]?.let { value ->
                    val payload = ContextBorrowing.payload(value) as? ContextBorrowPayload.Context
                        ?: throw ContextAccessException("'${parameter.identifier}' requires a bound context")
                    if (!TypeRelations.isSubtype(value.type, parameter.type))
                        throw ContextAccessException("Context '${value.type}' cannot satisfy '${parameter.type}'")
                    ContextBorrowing.requireUsable(payload)
                    payload.adapter(parameter.identifier, parameter.type) as ExecutionContextVar
                } ?: find(parameter.type as MCFPPExecutionContextType, parameter.identifier)
                put(parameter.identifier, chosen.implicitCast(parameter.type).clone().also { it.identifier = parameter.identifier })
            }
        }
    }

    private fun find(type: MCFPPExecutionContextType, name: String): ExecutionContextVar {
        for (layer in candidates.get().orEmpty().asReversed()) {
            val matches = layer.filter { TypeRelations.isSubtype(it.type, type) }
                .distinctBy { it.context?.source }
            if (matches.size > 1) throw ContextAccessException("Ambiguous context '$name: $type'; pass it explicitly")
            if (matches.size == 1) return matches.single().also { value ->
                ContextBorrowing.payload(value)?.let(ContextBorrowing::requireUsable)
            }
        }
        throw ContextAccessException("Missing context '$name: $type'")
    }

    fun <T> provide(values: List<ExecutionContextVar>, body: () -> T): T {
        values.forEach { it.requireContext(); ContextBorrowing.payload(it)?.let(ContextBorrowing::requireUsable) }
        return withCandidates(candidates.get().orEmpty() + listOf(values), body)
    }

    internal fun <T> inFunction(function: Function, body: () -> T): T {
        val previous = executorKnown.get()
        val previousType = frameType.get()
        frameType.set(function.callbackFrameType ?: ContextTypes.ExecutionContext)
        executorKnown.set(function.callbackFrameType?.let { ContextTypes.supports(it, ContextTypes.EntityContext) } == true)
        try { return withCandidates(if (function.callbackContextArguments.isNotEmpty())
            candidates.get().orEmpty() + listOf(function.callbackContextArguments)
        else listOf(function.boundContextArguments.values.filterIsInstance<ExecutionContextVar>()), body)
        } finally {
            if (previous == null) executorKnown.remove() else executorKnown.set(previous)
            if (previousType == null) frameType.remove() else frameType.set(previousType)
        }
    }

    private fun <T> withCandidates(next: List<List<ExecutionContextVar>>, body: () -> T): T {
        val previous = candidates.get()
        candidates.set(next)
        try { return body() } finally { if (previous == null) candidates.remove() else candidates.set(previous) }
    }

    fun <T> withExplicit(values: Map<String, Var<*>>, body: () -> T): T {
        val previous = explicit.get()
        explicit.set(values)
        try { return body() } finally { if (previous == null) explicit.remove() else explicit.set(previous) }
    }

    /** A callee cannot consume its caller's named argument map during nested calls. */
    internal fun <T> withoutExplicit(body: () -> T): T = withExplicit(emptyMap(), body)

    internal fun <T> inNative(values: Collection<Var<*>>, body: () -> T): T {
        val previous = executorKnown.get()
        val previousType = frameType.get()
        executorKnown.set(false)
        frameType.set(ContextTypes.ExecutionContext)
        try { return withoutExplicit { withCandidates(listOf(values.filterIsInstance<ExecutionContextVar>()), body) } }
        finally {
            if (previous == null) executorKnown.remove() else executorKnown.set(previous)
            if (previousType == null) frameType.remove() else frameType.set(previousType)
        }
    }
}
