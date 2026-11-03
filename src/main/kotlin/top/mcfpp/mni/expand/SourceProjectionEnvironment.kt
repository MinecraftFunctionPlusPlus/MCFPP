package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import top.mcfpp.model.function.Function

/** The projection boundary owns compiler capabilities; its Code and plan outputs do not. */
internal class SourceProjectionEnvironment(val bindings: CompilerSourceBindings) {
    val session = bindings.session
    val binding = bindings.binding
    val types = binding.types
    val roots = bindings.roots
    val declarations = bindings.declarations
    val macros = bindings.macros
    val loweringTarget = bindings.loweringTarget
    val rootScope: ScopeId get() = session.arena.rootScope
    val executionContext: ExecutionContextId get() = requireNotNull(loweringTarget.executionContext)

    init {
        bindings.requireCurrent()
    }

    fun requireCurrent() = bindings.requireCurrent()

    /**
     * Resolve the selected target against the caller's own canonical declaration view.
     *
     * Member targets live in the receiver-typed member table, never in the unqualified
     * global view. Both paths retain the exact SourceFunction instance selected by the
     * binder, so no display-name resolution or second overload ranking occurs here.
     */
    internal fun target(call: SourceCall, lexical: BindingEnvironment): SourceFunction {
        if (lexical.types !== types)
            throw SourceBindingFailure(call.source, "Selected target is outside its original declaration environment")
        val canonical = if (call.receiver == null) {
            lexical.functions.none { it === call.target } ||
                lexical.functions(call.name, call.source).none { it === call.target }
        } else {
            if ((call.target.function as? ImmutableFunctionRef<*>)?.kind == FunctionKind.MACRO)
                throw SourceBindingFailure(call.source, "Member macro calls are not registered by the source declaration view")
            lexical.memberFunctions(call.receiver.type, call.name, call.source).none { it === call.target }
        }
        if (canonical)
            throw SourceBindingFailure(call.source, "Selected target is outside its original declaration environment")
        if (call.receiver != null && declarations[call.target.declarationIdentity] == null)
            throw SourceBindingFailure(call.source, "Selected member has no original declaration environment")
        return call.target
    }

    internal fun declaration(function: SourceFunction): BindingEnvironment = declarations[function.declarationIdentity] ?: if ((function.function as ImmutableFunctionRef<*>).kind == FunctionKind.MACRO)
        binding else throw SourceBindingFailure(
        function.sourceBody?.source ?: session.site.span,
        "Prepared target has no original declaration environment"
    )

    internal fun field(
        field: SourceField,
        receiver: TypeRef<*>,
        lexical: BindingEnvironment,
        source: SourceSpan
    ): FieldRef<*> {
        val view = lexical.field(receiver, field.name)
        if (view == null ||
            view != field ||
            view.canonical !== field.canonical ||
            field.declaration == null ||
            types.field(field.declaration) !== field.canonical
        )
            throw SourceBindingFailure(source, "Field is outside the canonical declaration capability")
        return requireNotNull(field.canonical)
    }

    fun prevalidate(code: Code): PrevalidatedCode = checked(code.source()) {
        CodeValidator().validate(code, session.validationEnvironment(), ValidationPhase.PRE_EXPANSION) as PrevalidatedCode
    }

    fun validate(code: Code): ValidatedCode = checked(code.source()) {
        CodeValidator().validate(code, session.validationEnvironment(), ValidationPhase.FINAL) as ValidatedCode
    }

    /** MacroCall/INLINE must have been expanded before this final validation and detachment. */
    fun lower(code: Code): LoweringPlan = checked(code.source()) {
        val proof = CodeValidator().validate(code, session.validationEnvironment(), ValidationPhase.FINAL) as ValidatedCode
        CodeLowerer().lower(proof, loweringTarget).also { bindings.requireDomain(it.domain) }
    }

    internal fun <T> checked(source: SourceSpan, action: () -> T): T {
        try {
            requireCurrent()
            return action()
        } catch (failure: ExpansionFailure) {
            throw failure
        } catch (failure: IllegalArgumentException) {
            val span = when (failure) {
                is SourceBindingFailure -> failure.source
                is CodeModelException -> failure.source
                else -> source
            }
            session.abort(ExpansionDiagnostic(span, failure.message ?: "Source projection failed", session.budget.frames()))
        } catch (failure: IllegalStateException) {
            session.abort(ExpansionDiagnostic(source, failure.message ?: "Source projection failed", session.budget.frames()))
        }
    }

    companion object {
        fun capture(
            prepared: CompilerSourceEnvironment,
            session: ExpansionSession,
            target: Function = Function.currFunction
        ): SourceProjectionEnvironment = SourceProjectionEnvironment(CompilerSourceBindings.capture(prepared, session, target))
    }
}
