package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import java.lang.reflect.InvocationTargetException

/** The validated staging handle returned to the compiler pipeline after a root expansion. */
internal class MacroExpansionResult(
    val session: ExpansionSession,
    val invocation: MacroInvocation,
    val code: Code,
    val staged: StagedExpansion
) {
    /**
     * Root final validation only. The production pipeline then calls `session.complete(validated,
     * loweringParticipant)` so compiler publication remains inside its own transaction.
     */
    fun validate(): ValidatedCode = CodeValidator().validate(code, session.validationEnvironment(), ValidationPhase.FINAL) as ValidatedCode

    /** Canonical, traversal-stable dump of this fully nested expansion. */
    fun dump(): String = ExpansionDiagnostics.dump(code, invocation.site, invocation.canonicalSignature())
}

/**
 * Recursive macro expansion for the VALUE/CONST/REF protocol.
 *
 * The root invocation runs in the caller-created fresh [ExpansionSession]. Every nested macro call
 * gets a fresh direct child session, shares the compilation [ExpansionBudget], and is brought back
 * through the explicit same-family import. Argument preludes stay in the caller session and are
 * assembled with the imported child result, so exactly one evaluation happens at the original
 * source position; child inputs are explicit aliases to those session-owned references.
 */
internal class MacroExpander(
    val session: ExpansionSession,
    macros: Collection<MacroFunction>,
    private val configurationKey: String = session.site.configurationKey
) {
    constructor(environment: SourceProjectionEnvironment) : this(
        environment.session,
        environment.macros.namespaces.values.flatMap { it.functions },
        environment.session.environment.configurationKey
    ) {
        projection = environment
    }

    private var projection: SourceProjectionEnvironment? = null
    private val prepared: Map<FunctionKey, MacroFunction> =
        immutableMap(macros.associateBy { (it.reference as ImmutableFunctionRef<*>).key })
    private val tree = MacroTreeExpander(this)
    private val inlineExpansion = InlineExpander(this)
    // Child aliases already denote a stable parent address. Freezing an alias again in a symbol
    // CaptureAddress would both duplicate the capture and make a score address look like a copied
    // physical location; score aliases additionally retain the original address for import.
    private val childAliases = LinkedHashSet<SymbolId>()
    private val scoreAliases = LinkedHashMap<SymbolId, ImmutableMinecraftScoreRef>()

    init {
        require(prepared.isNotEmpty()) { "A macro expander requires at least one prepared macro" }
    }

    /**
     * Bind and expand one already selected source call:
     *
     * ```
     * val bound = MacroArgumentBinder(environment).bind(call)
     * val result = MacroExpander(environment).expandRoot(bound)
     * val validated = result.validate()
     * session.finalizeHelpers()
     * session.complete(validated, loweringParticipant)
     * ```
     */
    fun expandSource(call: SourceCall): MacroExpansionResult {
        val environment = projection
            ?: fail(session, call.source, "A source call can only be expanded by an environment-bound MacroExpander")
        return expandRoot(MacroArgumentBinder(environment).bind(call))
    }

    /** Expand the root invocation and stage its fully nested result in [session]. */
    fun expandRoot(bound: BoundMacroCall): MacroExpansionResult {
        try {
            val macro = canonical(bound.macro.reference, bound.site)
            if (macro !== bound.macro) fail(session, bound.site, "Root invocation target is not the prepared canonical macro")
            if ((bound.orderedPrelude as OwnedCode).metadata.sessionId != session.id)
                fail(session, bound.site, "Root invocation prelude belongs to another expansion session")
            val prelude = tree.expand(session, bound.orderedPrelude) as Block
            val invocation = MacroInvocation(
                macro,
                bound.site,
                bound.scope,
                prelude,
                bound.arguments,
                bound.readonlyArguments
            )
            val host = invokeHost(session, invocation.macro, invocation.arguments, invocation.site)
            val expanded = tree.expand(session, host)
            finalizeArenaHelpers(session)
            val code = assemble(session, invocation, expanded)
            val staged = session.stage(code)
            return MacroExpansionResult(session, invocation, code, staged)
        } catch (failure: ExpansionFailure) {
            // The root boundary knows the user's original call. Preserve the precise failing node
            // and budget frame snapshot while making the user call and whole chain explicit.
            throw ExpansionDiagnostics.sourcedFailure(failure, session.site.span, session.site.canonicalMacroSignature)
        } catch (failure: Throwable) {
            fail(session, session.site.span, "Root macro expansion failed: ${reason(failure)}", failure)
        }
    }

    internal fun expandMacroCall(session: ExpansionSession, call: ExprNode.MacroCall, expression: Expr<*>): Code {
        val metadata = (expression as OwnedCode).metadata
        val invocation = bindGenerated(
            session,
            call.function,
            call.arguments,
            metadata.scopeId,
            expression.source(),
            metadata.executionContextId
        )
        return expandNested(session, invocation)
    }

    /** Insert one inline callback through the same recursive tree walk as a macro body. */
    internal fun expandInlineCall(session: ExpansionSession, call: ExprNode.InlineCall, expression: Expr<*>): Code = inlineExpansion.expand(session, call, expression)

    /** The inline expander re-enters the same recursive traversal for a cloned callback body. */
    internal fun expandInlineTree(session: ExpansionSession, code: Code): Code = tree.expand(session, code)

    /** Bind one already selected generated MacroCall without rebuilding a source call or re-ranking. */
    internal fun bindGenerated(
        session: ExpansionSession,
        function: FunctionRef<*>,
        arguments: List<CallArgument>,
        scope: ScopeId,
        site: SourceSpan,
        context: ExecutionContextId? = null
    ): MacroInvocation {
        val macro = canonical(function, site)
        if (macro.reference.readonlyParameters().isNotEmpty())
            fail(session, site, "Generated macro call cannot carry readonly arguments")
        if (arguments.size != macro.parameters.size)
            fail(session, site, "Macro '${macro.qualifiedName}' expects ${macro.parameters.size} arguments, got ${arguments.size}")
        val origin = Origin(session.origin.callSite, site, session.origin.expansionFrames)
        fun metadata(executionContext: ExecutionContextId?) = session.arena.metadata(scope, origin, executionContext)
        val statements = ArrayList<Stmt>()
        val inputs = ArrayList<MacroInput>()
        val sources = ArrayList<Ref<*>?>()
        arguments.forEachIndexed { index, argument ->
            val parameter = macro.parameters[index]
            if (argument.mode() != parameter.mode())
                fail(session, argument.source(), "Argument ${parameter.name()} requires ${parameter.mode()}, got ${argument.mode()}")
            when (val payload = argument.payload) {
                is ArgumentPayload.Value -> {
                    val value = tree.expand(session, payload.expression)
                    val expression = value as? Expr<*>
                        ?: fail(session, payload.expression.source(), "A void macro expansion cannot be a VALUE argument")
                    val converted = if (parameter.type().sameType(expression.type())) expression else {
                        if (!parameter.type().isAssignableFrom(expression.type()))
                            fail(
                                session,
                                expression.source(),
                                "VALUE argument for ${parameter.name()} is ${expression.type().name()}, expected ${parameter.type().name()}"
                            )
                        session.arena.expr(
                            metadata(context),
                            parameter.type(),
                            ExprNode.Convert(expression, parameter.type(), ConversionKind.IMPLICIT)
                        )
                    }
                    val local = session.arena.local(
                        metadata(context),
                        parameter.type(),
                        "macroArgument$index"
                    )
                    statements += session.arena.stmt(
                        metadata(context),
                        StmtNode.LocalDecl(local, converted)
                    )
                    inputs += session.arena.value(local.load())
                    sources += local
                }
                is ArgumentPayload.Constant -> {
                    if (!parameter.type().sameType(payload.constant.type()))
                        fail(
                            session,
                            payload.constant.source(),
                            "CONST argument for ${parameter.name()} is ${payload.constant.type().name()}, expected ${parameter.type().name()}"
                        )
                    inputs += payload.constant
                    sources += null
                }
                is ArgumentPayload.Reference -> {
                    val reference = payload.reference
                    if (!parameter.type().sameType(reference.type()))
                        fail(
                            session,
                            reference.source(),
                            "REF argument for ${parameter.name()} has type ${reference.type().name()}, expected ${parameter.type().name()}"
                        )
                    if (reference is ImmutableMinecraftScoreRef) {
                        // A score is a complete address, not a symbol location. It is aliased in the
                        // child and mapped back to this exact reference during import.
                        requireScoreReference(session, reference, context)
                        inputs += reference
                        sources += reference
                    } else {
                        val location = referenceLocation(session, reference)
                        if (!location.writable)
                            fail(session, reference.source(), "REF argument for ${parameter.name()} is read-only")
                        if (location.executionContextId != null && location.executionContextId != context)
                            fail(session, reference.source(), "REF argument belongs to another execution context")
                        val aliased = reference is ImmutableRef<*> &&
                            reference.location.steps.isEmpty() &&
                            reference.location.capturedBase == null &&
                            reference.location.root in childAliases
                        if (aliased) {
                            // An alias is already a stable parent address. Capturing it again would
                            // duplicate the freeze and, for scores, turn the address into a copied
                            // symbol location on the way back, so it passes through untouched.
                            scoreAliases[location.root]?.let { score -> requireScoreReference(session, score, context) }
                            inputs += reference
                            sources += reference
                        } else {
                            val captured = if (location.capturedBase != null) reference else {
                                val address = session.arena.freshAddress()
                                statements += session.arena.stmt(
                                    metadata(context),
                                    StmtNode.CaptureAddress(address, location, reference.type())
                                )
                                session.arena.ref(
                                    metadata(context),
                                    reference.type(),
                                    NodeLocation(
                                        location.root,
                                        executionContextId = location.executionContextId,
                                        writable = location.writable,
                                        capturedBase = address
                                    )
                                )
                            }
                            inputs += captured
                            sources += captured
                        }
                    }
                }
                is ArgumentPayload.Inline -> {
                    if (parameter.mode() != ParamMode.INLINE)
                        fail(
                            session,
                            argument.source(),
                            "Argument ${parameter.name()} requires ${parameter.mode()}, got ${payload.mode}"
                        )
                    val expected = parameter.inlineSignature()
                        ?: fail(
                            session,
                            argument.source(),
                            "INLINE parameter ${parameter.name()} has no nested signature descriptor"
                        )
                    inputs += requireInline(session, payload.callback, expected, argument.source())
                    sources += null
                }
            }
        }
        val prelude = session.arena.block(metadata(context), statements)
        return MacroInvocation(macro, site, scope, prelude, inputs, emptyList(), sources)
    }

    private fun expandNested(parent: ExpansionSession, invocation: MacroInvocation): Code {
        val source = invocation.site
        val child = parent.child(invocation.expansionSite(configurationKey))
        try {
            val aliases = LinkedHashMap<SymbolId, Ref<*>>()
            fun alias(reference: Ref<*>, writable: Boolean): Ref<*> {
                val metadata = child.arena.metadata(child.arena.rootScope, child.origin)
                val root = child.arena.freshSymbol()
                val aliasRef = child.arena.ref(metadata, reference.type(), NodeLocation(root, writable = writable))
                child.registerBinding(aliasRef)
                aliases[root] = reference
                childAliases += root
                when (reference) {
                    is ImmutableMinecraftScoreRef -> scoreAliases[root] = reference
                    is ImmutableRef<*> -> scoreAliases[reference.location.root]?.let { scoreAliases[root] = it }
                    else -> Unit
                }
                return aliasRef
            }
            // A nested host that requests a compiler context must stay bound to the invocation's
            // context. Only an already context-bound caller can accept that explicit mapping.
            // The two imports have opposite directions: the argument tree lives in the parent arena
            // and is copied into the child, while the result tree lives in the child and is copied
            // back into the parent. Each map is therefore keyed by its source arena's context id and
            // points at a frame owned by the target arena; sharing one direction makes the argument
            // import reject the mapping because the child root id has no parent source frame.
            val parentContext = (invocation.prelude as OwnedCode).metadata.executionContextId
            val argumentContexts: Map<ExecutionContextId, ExecutionContextFrame>
            val resultContexts: Map<ExecutionContextId, ExecutionContextFrame>
            if (parentContext == null) {
                argumentContexts = emptyMap()
                resultContexts = emptyMap()
            } else {
                val childRoot = child.rootExecutionContext()
                argumentContexts = mapOf(parentContext to childRoot)
                resultContexts = mapOf(
                    childRoot.id to
                        parent.arena.contextFrame(parentContext, invocation.scope, source)
                )
            }
            val arguments: List<MacroInput> = invocation.arguments.mapIndexed { index, argument ->
                when (argument) {
                    is ImmutableValue<*> -> {
                        val origin = invocation.source(index)
                            ?: fail(parent, source, "VALUE argument lost its owning temporary")
                        val input = alias(origin, writable = false)
                        child.arena.value(input.load())
                    }
                    is ImmutableConst<*> -> {
                        val metadata = child.arena.metadata(child.arena.rootScope, child.origin)
                        val literal = child.arena.expr(metadata, argument.type(), ExprNode.Literal(argument.value()))
                        child.arena.constant(literal, argument.value())
                    }
                    is ImmutableRef<*> -> {
                        val origin = invocation.source(index) ?: argument
                        alias(origin, writable = true)
                    }
                    is ImmutableLocal<*> -> {
                        val origin = invocation.source(index) ?: argument
                        alias(origin, writable = true)
                    }
                    is ImmutableMinecraftScoreRef -> {
                        val origin = invocation.source(index) ?: argument
                        val score = origin as? ImmutableMinecraftScoreRef
                            ?: fail(parent, source, "Score REF argument lost its physical score address")
                        alias(score, writable = true)
                    }
                    is ImmutableInlineFn<*> -> {
                        if ((argument as OwnedCode).metadata.sessionId != parent.id)
                            fail(parent, source, "INLINE argument belongs to another expansion session")
                        val captures = inlineCaptureReferences(parent, argument, source)
                        val external = LinkedHashMap<SymbolId, Ref<*>>()
                        captures.forEach { (symbol, reference) ->
                            val writable = when (reference) {
                                is ImmutableRef<*> -> reference.location.writable
                                is ImmutableLocal<*> -> reference.location.writable
                                else -> fail(parent, source, "INLINE capture is not an addressable reference")
                            }
                            external[symbol] = alias(reference, writable = writable)
                        }
                        child.arena.importMacroChildTree(
                            parent.arena,
                            argument,
                            child.arena.rootScope,
                            child.origin,
                            external,
                            argumentContexts
                        )
                    }
                }
            }
            val host = invokeHost(child, invocation.macro, arguments, source)
            val expanded = tree.expand(child, host)
            finalizeArenaHelpers(child)
            child.stage(expanded)
            val validated = CodeValidator().validate(expanded, child.validationEnvironment(), ValidationPhase.FINAL)
                as ValidatedCode
            child.acceptChild(validated)
            val imported = parent.importChildResult(child, expanded, invocation.scope, aliases, resultContexts)
            return assemble(parent, invocation, imported)
        } catch (failure: ExpansionFailure) {
            throw failure
        } catch (failure: Throwable) {
            fail(parent, source, "Nested macro '${invocation.qualifiedName}' failed: ${reason(failure)}", failure)
        }
    }

    private fun invokeHost(
        session: ExpansionSession,
        macro: MacroFunction,
        arguments: List<MacroInput>,
        site: SourceSpan
    ): Code {
        val values = arrayOfNulls<Any>(arguments.size + 1)
        values[0] = session.context()
        arguments.forEachIndexed { index, argument -> values[index + 1] = argument }
        val result = try {
            macro.javaMethod.invoke(null, *values)
        } catch (failure: InvocationTargetException) {
            val cause = failure.targetException
            if (cause is ExpansionFailure) throw cause
            if (cause is CodeModelException)
                fail(session, cause.source, cause.message ?: "Macro host rejected generated code", cause)
            fail(session, site, "Macro host '${macro.qualifiedName}' failed: ${reason(cause)}", cause)
        } catch (failure: ExpansionFailure) {
            throw failure
        } catch (failure: Throwable) {
            fail(session, site, "Macro host '${macro.qualifiedName}' could not be invoked: ${reason(failure)}", failure)
        }
        if (macro.returnsVoid) {
            val block = result as? Block ?: fail(
                session,
                site,
                "Void macro '${macro.qualifiedName}' must return exactly a Block, got ${resultName(result)}"
            )
            requireOwned(session, block as Code, site)
            return block
        }
        val expression = result as? Expr<*> ?: fail(
            session,
            site,
            "Macro '${macro.qualifiedName}' must return exactly an Expr, got ${resultName(result)}"
        )
        requireOwned(session, expression, site)
        if (!macro.returnType.sameType(expression.type()))
            fail(
                session,
                expression.source(),
                "Macro '${macro.qualifiedName}' declared ${macro.returnType.name()} but returned ${expression.type().name()}"
            )
        return expression
    }

    private fun requireOwned(session: ExpansionSession, code: Code, site: SourceSpan) {
        val owned = code as? OwnedCode
            ?: fail(session, site, "Macro host returned a code handle from another expansion session")
        if (owned.metadata.sessionId != session.id)
            fail(session, site, "Macro host returned code owned by another expansion session")
    }

    /**
     * Only an immutable callback bound by [InlineBinder] (or directly by the compiler bridge) may be
     * passed through the INLINE protocol. A bare dynamic function value has no closed body and is
     * rejected before it can become a numbered dispatcher or a runtime closure.
     */
    private fun requireInline(
        session: ExpansionSession,
        callback: InlineFn<*>,
        expected: InlineSignature<*>,
        site: SourceSpan
    ): ImmutableInlineFn<*> {
        val descriptor = callback as? ImmutableInlineFn<*>
            ?: fail(session, site, "Dynamic function values cannot satisfy an INLINE parameter")
        val owned = descriptor as OwnedCode
        if (owned.metadata.sessionId != session.id)
            fail(session, site, "INLINE callback belongs to another expansion session")
        if (!sameInlineSignature(expected, descriptor.signature()))
            fail(session, site, "INLINE callback signature differs from the selected parameter")
        return descriptor
    }

    private fun sameInlineSignature(expected: InlineSignature<*>, actual: InlineSignature<*>): Boolean {
        if (!expected.returnType().sameType(actual.returnType())) return false
        val left = expected.parameters()
        val right = actual.parameters()
        if (left.size != right.size) return false
        return left.indices.all { index ->
            val a = left[index]
            val b = right[index]
            a.mode() == b.mode() &&
                a.type().sameType(b.type()) &&
                (
                    a.inlineSignature() == null &&
                        b.inlineSignature() == null ||
                        a.inlineSignature() != null &&
                        b.inlineSignature() != null &&
                        sameInlineSignature(requireNotNull(a.inlineSignature()), requireNotNull(b.inlineSignature()))
                    )
        }
    }

    /**
     * Export the capture references used by an inline descriptor as ordinary parent-session refs.
     * Import supplies fresh child aliases for them, so a write through the callback still returns to
     * the same caller storage; no old lexical environment is treated as a runtime position.
     */
    private fun inlineCaptureReferences(
        session: ExpansionSession,
        callback: ImmutableInlineFn<*>,
        site: SourceSpan
    ): Map<SymbolId, Ref<*>> {
        val body = callback.target as? InlineTarget.Body
            ?: fail(session, site, "INLINE callback has no bound source body")
        if (body.captures.isEmpty()) return emptyMap()
        if (body.captureReferences.keys != body.captures)
            fail(
                session,
                site,
                "INLINE callback capture capability is missing or incomplete; bind it with InlineBinder"
            )
        body.captureReferences.forEach { (root, reference) ->
            val owned = reference as? OwnedCode
                ?: fail(session, site, "INLINE capture reference has no expansion-session owner")
            if (owned.metadata.sessionId != session.id)
                fail(session, site, "INLINE capture reference belongs to another expansion session")
            val location = when (reference) {
                is ImmutableRef<*> -> reference.location
                is ImmutableLocal<*> -> reference.location
                else -> fail(
                    session,
                    site,
                    "An INLINE capture must be an addressable local or reference, not a score value"
                )
            }
            if (location.root != root)
                fail(session, site, "INLINE capture metadata root differs from its declared capture")
        }
        return body.captureReferences
    }

    private fun assemble(session: ExpansionSession, invocation: MacroInvocation, expanded: Code): Code {
        val metadata = (invocation.prelude as OwnedCode).metadata
        if (invocation.macro.returnsVoid) {
            val block = expanded as? Block
                ?: fail(session, invocation.site, "Void macro '${invocation.qualifiedName}' did not produce a block")
            // A directly returned root host block owns a child scope. Keep that scope instead of
            // re-parenting statements or importing (and recapturing) its frozen addresses.
            val body: List<Stmt> = if ((block as OwnedCode).metadata.scopeId == metadata.scopeId) block.statements()
            else listOf(session.arena.stmt(metadata, StmtNode.ScopeBlock(block)))
            return session.arena.block(metadata, invocation.prelude.statements() + body)
        }
        val expression = expanded as? Expr<*>
            ?: fail(session, invocation.site, "Macro '${invocation.qualifiedName}' did not produce an expression")
        if (!invocation.macro.returnType.sameType(expression.type()))
            fail(
                session,
                invocation.site,
                "Macro '${invocation.qualifiedName}' expansion changed its declared result type"
            )
        val region = session.arena.freshRegion()
        val yield = session.arena.stmt(metadata, StmtNode.Yield(region, expression))
        val block = session.arena.block(metadata, invocation.prelude.statements() + yield)
        return session.arena.expr(
            metadata,
            invocation.macro.returnType,
            ExprNode.ExprBlock(block, invocation.macro.returnType, region)
        )
    }

    private fun finalizeArenaHelpers(session: ExpansionSession) {
        session.stagedHelpers().forEach { helper ->
            if (helper.finalCode == null && helper.code.environment.arena === session.arena) {
                val expanded = tree.expand(session, helper.code.code)
                val block = expanded as? Block
                    ?: fail(session, helper.code.code.source(), "A helper body expansion must remain a block")
                session.finalizeExpandedHelper(helper, block)
            }
        }
    }

    private fun canonical(reference: FunctionRef<*>, site: SourceSpan): MacroFunction {
        val immutable = reference as? ImmutableFunctionRef<*>
            ?: fail(session, site, "Macro target is not a canonical function snapshot")
        val macro = prepared[immutable.key]
            ?: fail(session, site, "Macro '${reference.name()}' is outside the prepared macro snapshot")
        if (macro.reference !== reference)
            fail(session, site, "Macro '${reference.name()}' is not its canonical prepared declaration")
        if (macro.parameters.size != reference.parameters().size)
            fail(session, site, "Prepared macro signature changed after source selection")
        return macro
    }

    private fun referenceLocation(session: ExpansionSession, reference: Ref<*>): NodeLocation = when (reference) {
        is ImmutableRef<*> -> reference.location
        is ImmutableLocal<*> -> reference.location
        is ImmutableMinecraftScoreRef ->
            fail(session, reference.source(), "A score reference cannot be captured as a symbol location")
    }

    /**
     * Validate that a generated score REF can be transported without rebinding its address. The
     * child receives a same-typed alias whose root is mapped back to this exact reference during
     * import, so a context-bound (in particular entity-targeted) score must already belong to the
     * invocation's execution context rather than being copied or recaptured later.
     */
    private fun requireScoreReference(
        session: ExpansionSession,
        reference: ImmutableMinecraftScoreRef,
        context: ExecutionContextId?
    ) {
        val scoreContext = reference.metadata.executionContextId
        if (scoreContext != null && scoreContext != context)
            fail(session, reference.source(), "REF argument belongs to another execution context")
        val target = reference.target as? MinecraftScoreTarget.Entity ?: return
        val entity = target.entity as ImmutableEntityHandle
        if (scoreContext == null || entity.metadata.executionContextId != scoreContext)
            fail(session, reference.source(), "Entity score REF lost its bound execution context")
    }

    internal fun fail(
        session: ExpansionSession,
        source: SourceSpan,
        message: String,
        cause: Throwable? = null
    ): Nothing = session.abort(ExpansionDiagnostic(source, message, session.budget.frames()), cause)

    private fun reason(failure: Throwable): String = failure.message?.takeIf { it.isNotBlank() } ?: failure.javaClass.simpleName

    private fun resultName(value: Any?): String = value?.javaClass?.name ?: "null"
}
