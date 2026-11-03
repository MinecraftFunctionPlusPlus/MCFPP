package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*

/**
 * Resolves a selected macro target to exactly one prepared [MacroFunction]. This deliberately
 * performs no second overload ranking: the prepared snapshot must contain the exact canonical
 * reference instance and [FunctionKey], never just a matching name or signature spelling.
 */
internal class MacroCallResolver(private val environment: SourceProjectionEnvironment) {
    private val preparedMacros: List<MacroFunction> =
        environment.macros.namespaces.values.flatMap { it.functions }

    /** Resolve the target already selected by [SourceCodeBinder], including its lexical provenance. */
    fun resolve(call: SourceCall, lexical: BindingEnvironment = environment.binding): MacroFunction {
        environment.requireCurrent()
        if (lexical.types !== environment.types)
            throw SourceBindingFailure(call.source, "Selected macro call belongs to another source catalog")
        val target = environment.target(call, lexical)
        val reference = target.function as? ImmutableFunctionRef<*>
            ?: throw SourceBindingFailure(call.source, "Selected call target is not a canonical function snapshot")
        return canonical(reference, call.source)
    }

    /** Resolve an already projected macro reference, e.g. a nested MacroCall owned by the expander. */
    fun resolve(reference: FunctionRef<*>, site: SourceSpan): MacroFunction {
        environment.requireCurrent()
        val immutable = reference as? ImmutableFunctionRef<*>
            ?: throw SourceBindingFailure(site, "Macro target is not a canonical function snapshot")
        return canonical(immutable, site)
    }

    private fun canonical(reference: ImmutableFunctionRef<*>, site: SourceSpan): MacroFunction {
        if (reference.kind != FunctionKind.MACRO)
            throw SourceBindingFailure(site, "Call target '${reference.name()}' is not a macro")
        val matches = preparedMacros.filter { (it.reference as ImmutableFunctionRef<*>).key == reference.key }
        val macro = matches.singleOrNull() ?: throw SourceBindingFailure(
            site,
            when {
                matches.isEmpty() -> "Selected macro '${reference.name()}' is outside the prepared macro snapshot"
                else -> "Prepared macro snapshot repeats the function key of '${reference.name()}'"
            }
        )
        if (macro.reference !== reference)
            throw SourceBindingFailure(
                site,
                "Selected macro '${reference.name()}' is not the canonical prepared declaration for its function key"
            )
        if (macro.parameters.size != reference.parameters().size)
            throw SourceBindingFailure(site, "Prepared macro signature changed after source selection")
        return macro
    }
}
