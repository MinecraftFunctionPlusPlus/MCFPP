package top.mcfpp.mni.expand

import top.mcfpp.Project
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.Void
import top.mcfpp.mni.api.CodeModelException
import top.mcfpp.mni.api.FunctionKind
import top.mcfpp.mni.api.FunctionRef
import top.mcfpp.mni.api.ImmutableFunctionRef
import top.mcfpp.mni.api.ImmutableTypeRef
import top.mcfpp.mni.api.TypeKind
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitNamespaceID
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.analysis.StructuredBindings
import top.mcfpp.analysis.StructuredNameBinding
import top.mcfpp.analysis.PreparationDiagnostic
import top.mcfpp.analysis.PreparationFactCode
import top.mcfpp.analysis.PreparationFactFailure
import org.antlr.v4.runtime.tree.ParseTree

/** Bound D11 tokens resolve only inside their original prepared unit directory. */
internal interface FrozenMacroCandidates {
    val directory: PreparedNativeDirectory
    fun bind(catalog: SourceTypeCatalog): List<MacroFunction>
}

/**
 * The single source-call integration point for namespace macros.
 *
 * Both eager compiler entries must consult this object *before* visiting a normal argument:
 * [top.mcfpp.analysis.PrimitiveCompiler] only performs the pure name recognition and falls
 * back to the legacy body compiler, while [top.mcfpp.antlr.MCFPPExprVisitor] runs the complete
 * prepare -> capture -> expand -> validate/lower -> commit sequence. Ordinary calls return
 * `null` from [visitFunctionCall] and continue through their original path unchanged.
 *
 * Nothing in this class publishes compiler state before [ExpansionSession.complete]: argument
 * preludes stay in the session arena, resources/helpers/names stay in the session transaction,
 * and a failure discards the whole savepoint before an error value is returned.
 */
internal object MacroCallPipeline {

    /**
     * Purely detect whether [syntax] can name a declared namespace macro. This reads immutable
     * macro metadata only; it never visits an argument, completes a declaration or invokes a host.
     */
    fun isPotentialMacro(syntax: P.FunctionCallContext, caller: Function = Function.currFunction): Boolean {
        StructuredBindings.name(syntax.namespaceID(), caller)?.let { return it is StructuredNameBinding.Macros }
        val (namespace, identifier) = syntax.namespaceID().text.splitNamespaceID()
        val namespaces = if (namespace != null) listOfNotNull(
            GlobalScope.localNamespaces[namespace],
            GlobalScope.importedLibNamespaces[namespace],
            GlobalScope.libNamespaces[namespace],
            GlobalScope.stdNamespaces[namespace]
        ) else visibleNamespaces(caller)
        return namespaces.any { owner ->
            PreparedNativeDirectory.current()?.hasMacro(owner.identifier, identifier) == true ||
            owner.macroDeclarations().any { descriptor ->
                descriptor.identifier.ifEmpty { descriptor.methodName } == identifier
            }
        }
    }

    /** Declaration inference prepares immutable argument/type facts without invoking or expanding a host. */
    fun declaredResultType(syntax: P.FunctionCallContext, caller: Function): top.mcfpp.type.MCFPPType? {
        val binding = StructuredBindings.name(syntax.namespaceID(), caller)
        val frozen = (binding as? StructuredNameBinding.Macros)?.candidates
        if (binding != null && frozen == null) return null
        val (namespace, identifier) = syntax.namespaceID().text.splitNamespaceID()
        if (frozen == null && namespace == null && caller.scope.getVar(identifier)?.type is top.mcfpp.type.MCFPPFunctionType)
            return null
        if (frozen == null && !isPotentialMacro(syntax, caller)) return null
        val preparation = if (frozen == null) CompilerBindingEnvironment.prepareCall(syntax, caller)
            else prepareFrozen(syntax, caller, null, frozen)
        return when (preparation) {
            is CompilerCallPreparationResult.Rejected -> throw top.mcfpp.analysis.DeclarationBinding.Failure(
                preparation.diagnostics.joinToString("\n") { it.message }.ifBlank { "Cannot prepare macro result type" })
            is CompilerCallPreparationResult.Ready -> {
                if ((preparation.call.target.function as? ImmutableFunctionRef<*>)?.kind != FunctionKind.MACRO) null
                else {
                    preparation.environment.requireCurrent()
                    preparation.environment.binding.types.source(preparation.call.target.function.returnType())
                }
            }
        }
    }

    /**
     * Intercept one namespace call at the eager visitor entry.
     *
     * @return the real macro result (or a `void` marker), an error value for a macro call that
     *         could not be prepared/expanded, or `null` when this call is not a macro and must
     *         keep the legacy eager path.
     */
    fun visitFunctionCall(syntax: P.FunctionCallContext, caller: Function = Function.currFunction,
                          trailing: P.InlineLambdaContext? = null, frozenCandidates: FrozenMacroCandidates? = null): Var<*>? {
        val frozen = try {
            frozenCandidates ?: (StructuredBindings.name(syntax.namespaceID(), caller) as? StructuredNameBinding.Macros)?.candidates
        } catch (failure: Throwable) { return unexpected(syntax, caller, failure) }
        val potential = try {
            frozen != null || isPotentialMacro(syntax, caller)
        } catch (failure: Throwable) {
            return unexpected(syntax, caller, failure)
        }
        if (!potential) return null
        val budget = try {
            // Resolve the compilation-unit owner before the savepoint so an implicit first
            // budget is part of the captured state and failure rollback cannot erase its counts.
            Project.macroExpansionBudget()
        } catch (failure: Throwable) {
            return unexpected(syntax, caller, failure)
        }
        val journal = try {
            // Source preparation completes declarations before expansion starts. The outer
            // savepoint covers that phase together with session completion, so a rejected or
            // failed macro call restores the complete original compiler state.
            CompilerStateJournal.checkpoint()
        } catch (failure: Throwable) {
            return unexpected(syntax, caller, failure)
        }
        var result: Var<*>? = null
        val prepared = try {
            journal.prepare {
                when (val preparation = if (frozen == null) CompilerBindingEnvironment.prepareCall(syntax, caller, trailing)
                    else prepareFrozen(syntax, caller, trailing, frozen)) {
                    is CompilerCallPreparationResult.Rejected -> throw RejectedCall(preparation)
                    is CompilerCallPreparationResult.Ready -> {
                        val target = preparation.call.target.function as? ImmutableFunctionRef<*>
                        if (target?.kind == FunctionKind.MACRO) result = expand(preparation, caller, budget)
                    }
                }
            }
        } catch (failure: RejectedCall) {
            return reject(failure.preparation)
        } catch (failure: Throwable) {
            return unexpected(syntax, caller, failure)
        }
        return try {
            prepared.apply()
            result
        } catch (failure: Throwable) {
            // CompilerStateJournal.apply restores the after snapshot before publishing logs. If
            // publication fails, the already-visible after state must be rolled back to before.
            try {
                prepared.rollback()
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
            unexpected(syntax, caller, failure)
        }
    }

    private class RejectedCall(val preparation: CompilerCallPreparationResult.Rejected) : RuntimeException()

    /** Preserve the original syntax and source identities while replacing only frozen call candidates. */
    private fun prepareFrozen(syntax: P.FunctionCallContext, caller: Function, trailing: P.InlineLambdaContext?,
                              frozen: FrozenMacroCandidates): CompilerCallPreparationResult = try {
        frozen.directory.requireOpen()
        val root = if (trailing != null) syntax.parent as? P.LambdaCallExpressionContext ?: syntax else syntax
        val prepared = CompilerSourceIndex(caller, nativeDirectory = frozen.directory).prepareDeclarationEnvironment(root)
        val base = prepared.binding
        val catalog = base.types
        val calls = arrayListOf<P.FunctionCallContext>()
        fun collect(node: ParseTree) {
            if (node is P.FunctionCallContext) calls += node
            for (index in 0 until node.childCount) collect(node.getChild(index))
        }
        collect(root)
        val admitted = prepared.macros.namespaces.values.flatMap { it.functions }.associateBy { it.reference.key }
        val allFunctions = base.functions.toMutableList()
        val explicit = linkedMapOf<top.mcfpp.mni.api.SourceSpan, List<SourceFunction>>()
        for (call in calls) {
            val bound = if (call === syntax) frozen else
                (StructuredBindings.name(call.namespaceID(), caller) as? StructuredNameBinding.Macros)?.candidates ?: continue
            check(bound.directory === frozen.directory) { "Frozen macro candidates belong to another prepared directory" }
            val source = StructuredSourceFacts.span(call, base.sourceFile)
            val exact = bound.bind(catalog).map { candidate ->
                val macro = admitted[candidate.reference.key]
                    ?: throw SourceBindingFailure(source, "Frozen macro is absent from its prepared author directory")
                if (macro.javaMethod != candidate.javaMethod) throw SourceBindingFailure(source, "Frozen macro binding differs from its prepared declaration")
                allFunctions.firstOrNull { (it.function as ImmutableFunctionRef<*>).key == macro.reference.key }
                    ?: SourceFunction(macro.identifier, macro.reference).also(allFunctions::add)
            }.distinctBy { (it.function as ImmutableFunctionRef<*>).key }
            if (exact.isEmpty()) throw SourceBindingFailure(source, "No author macro candidate is available")
            explicit[source] = exact
        }
        val binder = SnapshotSourceCodeBinder()
        val binding = base.withFrozenCalls(allFunctions.filter { added -> base.functions.none { it === added } }, explicit)
        val environment = prepared.withBinding(binding)
        CompilerCallPreparationResult.Ready(environment, binder.call(syntax, binding, trailing))
    } catch (failure: PreparationFactFailure) {
        CompilerCallPreparationResult.Rejected(listOf(PreparationDiagnostic(failure.code, failure.source, failure.message.orEmpty(), failure)))
    } catch (failure: SourceBindingFailure) {
        CompilerCallPreparationResult.Rejected(listOf(PreparationDiagnostic(PreparationFactCode.UNSUPPORTED_EXPRESSION,
            failure.source, failure.message.orEmpty(), failure)))
    }

    private fun expand(
        prepared: CompilerCallPreparationResult.Ready,
        caller: Function,
        budget: ExpansionBudget
    ): Var<*> {
        val call = prepared.call
        val target = call.target.function as ImmutableFunctionRef<*>
        var session: ExpansionSession? = null
        try {
            val backend = CompilerCommitBackend()
            val active = ExpansionSession(
                ExpansionSite(call.source, canonicalSignature(call)),
                budget,
                CompilerSourceBindings.expansionEnvironment(prepared.environment, namespace = namespace(caller)),
                CompilerTransaction(backend)
            )
            session = active
            val bindings = CompilerSourceBindings.capture(prepared.environment, active, caller)
            backend.attach(bindings)
            val environment = SourceProjectionEnvironment(bindings)
            val expansion = MacroExpander(environment).expandSource(call)
            val validated = expansion.validate()
            active.finalizeHelpers()
            val plan = environment.lower(validated.code)
            if (!isVoid(target) && plan.resultType == null)
                throw CodeModelException(call.source, "Macro '${call.name}' declared a value but produced no lowering result")
            val bridge = CompilerBridge(bindings)
            active.complete(validated, bridge.prepare(plan))
            val result = if (isVoid(target)) Void else bridge.result()
                ?: throw IllegalStateException("Macro '${call.name}' committed without a result adapter")
            // Dump the validated, fully nested tree; the flag is the parsed Project configuration.
            if (Project.config.macroExpansion.dump)
                LogProcessor.info(
                    "Macro expansion dump at ${call.source} for ${canonicalSignature(call)}:\n" +
                        CodePrinter().print(validated.code)
                )
            return result
        } finally {
            session?.close()
        }
    }

    private fun reject(preparation: CompilerCallPreparationResult.Rejected): Var<*> {
        val message = preparation.diagnostics.joinToString("\n") { diagnostic ->
            "${diagnostic.code} at ${diagnostic.source}: ${diagnostic.message}"
        }.ifBlank { "Macro call preparation was rejected" }
        LogProcessor.error(message)
        return UnknownVar("error_macro").apply { isError = true }
    }

    private fun unexpected(syntax: P.FunctionCallContext, caller: Function, failure: Throwable): Var<*> {
        if (failure is VirtualMachineError) throw failure
        LogProcessor.debug("Macro call internal failure:\n${failure.stackTraceToString()}")
        val source = caller.namespace
        return failed(
            "Macro call '${syntax.namespaceID().text}' in '$source' failed: " +
                (failure.message ?: failure.javaClass.simpleName)
        )
    }

    private fun failed(message: String): Var<*> {
        LogProcessor.error(message)
        return UnknownVar("error_macro").apply { isError = true }
    }

    private fun isVoid(function: FunctionRef<*>): Boolean = (function.returnType() as? ImmutableTypeRef<*>)?.kind == TypeKind.VOID

    private fun canonicalSignature(call: SourceCall): String {
        val function = call.target.function
        return buildString {
            append(function.name())
            append('(')
            function.parameters().forEachIndexed { index, parameter ->
                if (index > 0) append(',')
                append(parameter.mode()).append(' ').append(parameter.type().name())
            }
            append(")->").append(function.returnType().name())
        }
    }

    private fun namespace(caller: Function): String = caller.namespace.takeIf { it.matches(Regex("[a-z0-9_.-]+")) } ?: "mcfpp"

    /**
     * Mirror the declaration phase's lexical visibility: the caller namespace, its imports,
     * and the always-visible standard namespaces. The actual selection still happens in
     * [CompilerBindingEnvironment.prepareCall], so this list only avoids intercepting
     * unrelated ordinary calls.
     */
    private fun visibleNamespaces(caller: Function): List<Namespace> {
        val names = linkedSetOf<String>()
        caller.declarationEnvironment?.let { environment ->
            names += environment.namespace
            names += environment.imports.keys
        }
        names += caller.namespace
        caller.declarationFile?.field?.importedNamespaceField?.values?.forEach { names += it.identifier }
        caller.declarationFile?.field?.importField?.values?.forEach { names += it.identifier }
        names += GlobalScope.stdNamespaces.keys
        return names.flatMap { name ->
            listOfNotNull(GlobalScope.localNamespaces[name], GlobalScope.importedLibNamespaces[name], GlobalScope.libNamespaces[name], GlobalScope.stdNamespaces[name])
        }.distinct()
    }
}
