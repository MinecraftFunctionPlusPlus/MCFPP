package top.mcfpp.mni.expand

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.mni.api.*
import java.util.Collections
import java.util.IdentityHashMap
import java.util.function.Consumer

/** Compiler configuration, deliberately absent from the processor-facing API. */
internal sealed interface DeclarationMetadataShape {
    data class Scalar(val shape: ConstantShape) : DeclarationMetadataShape
    data class Sequence(val element: DeclarationMetadataShape) : DeclarationMetadataShape
    class Record(fields: Map<String, DeclarationMetadataShape>) : DeclarationMetadataShape {
        val fields = immutableMap(fields)
    }
}

internal class DeclarationMetadataRule(
    arguments: Map<String, DeclarationMetadataShape>,
    val repeatable: Boolean = false
) {
    val arguments = DeclarationMetadataShape.Record(arguments)
}

/** A future compiler mapping consumes these actual parameter locations and the method's own exit. */
internal class PreparedMethodDeclaration(
    val edit: MethodEdit,
    val code: PrevalidatedCode,
    val self: Ref<*>,
    parameters: List<Ref<*>>
) {
    val parameters = immutableList(parameters)
}

/** Detached only. D_INTEGRATE must map declarations and enlist their commit before publishing. */
internal class PreparedDeclarationEdits(
    val edits: DeclarationEdits,
    methods: List<PreparedMethodDeclaration>,
    helpers: List<StagedHelper>,
    internal val session: ExpansionSession,
    /** Exact compiler whitelist this invocation was checked against; publication reuses it. */
    internal val metadataPolicy: Map<String, DeclarationMetadataRule> = emptyMap()
) : AutoCloseable {
    val methods = immutableList(methods)
    /** Expansion must preserve these checked field capabilities when finalizing staged helpers. */
    val helpers = immutableList(helpers)
    override fun close() = session.close()
}

/**
 * A single processor owns a newly created root session and transaction. It never shares the
 * transaction of an earlier processor. Construction/validation cannot publish compiler state.
 * A failed invocation revokes its capabilities and discards its resources, helpers, and edits.
 */
internal class DeclarationEditValidator(
    site: ExpansionSite,
    budget: ExpansionBudget,
    environment: ExpansionEnvironment,
    backend: CompilerTransactionBackend,
    private val template: TemplateView,
    metadataWhitelist: Map<String, DeclarationMetadataRule> = emptyMap()
) : AutoCloseable {
    private enum class State { NEW, BUILDING, PREPARED, FAILED, CLOSED }
    private var state = State.NEW
    private val authority = Any()
    private val whitelist = immutableMap(metadataWhitelist)
    private val access = BuilderAccess()
    private val issued = arrayListOf<DeclarationEdit>()
    private val methods = IdentityHashMap<MethodEdit, PreparedMethodDeclaration>()
    private var inMethod = false
    private val graph = (template.type() as ImmutableTypeRef<*>).graph
        ?: throw CodeModelException(template.source(), "A processor requires a canonical completed template")
    private val session = ExpansionSession(site, budget, environment, CompilerTransaction(backend))
    private val macro = session.context()
    private val fields = immutableSet(
        template.fieldDeclarations().filter { field ->
            field.access() == DeclarationAccess.PUBLIC ||
                field.access() == DeclarationAccess.PROTECTED ||
                field.access() == DeclarationAccess.PRIVATE &&
                field.owner().sameType(template.type())
        }.map { (it.field() as ImmutableFieldRef<*>).key }
    )

    private fun active() {
        if (state != State.BUILDING) throw CodeModelException(template.source(), "Declaration construction context is closed")
        session.context() // Also checks Project epoch and the underlying session's lifetime.
    }
    private fun fail(message: String, source: SourceSpan = template.source()): Nothing = throw CodeModelException(source, message)
    private inline fun <T> construct(block: () -> T): T {
        active()
        try {
            return block()
        } catch (failure: Throwable) {
            reject(failure)
        }
    }
    private fun reject(failure: Throwable): Nothing {
        state = State.FAILED
        val diagnostic = when (failure) {
            is ExpansionFailure -> failure.diagnostic
            is CodeModelException -> ExpansionDiagnostic(failure.source, failure.message ?: "Invalid declaration edit", session.budget.frames())
            else -> ExpansionDiagnostic(template.source(), "Declaration processor failed: ${failure.message ?: failure.javaClass.simpleName}", session.budget.frames())
        }
        try {
            session.abort(diagnostic)
        } catch (error: ExpansionFailure) {
            if (error !== failure) error.initCause(failure)
            throw error
        }
    }
    private fun canonical(type: TypeRef<*>) {
        if (!graph.owns(type)) fail("Declaration type belongs to another canonical domain")
    }
    private fun owner(type: TypeRef<*>) {
        canonical(type)
        if (!template.type().sameType(type)) fail("Generated member owner must be the current canonical template")
    }
    private fun identifier(name: String) {
        val lexer = mcfppLexer(CharStreams.fromString(name))
        var valid = true
        lexer.removeErrorListeners()
        lexer.addErrorListener(object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                error: RecognitionException?
            ) {
                valid = false
            }
        })
        val token = lexer.nextToken()
        // Exact text also rejects skipped whitespace/comments. The lexer owns Unicode categories,
        // keyword priority, VecType, and the Identifier action which emits REF.
        if (token.type != mcfppLexer.Identifier || token.text != name || lexer.nextToken().type != Token.EOF || !valid)
            fail("Invalid generated member name '$name'")
    }
    private fun signature(signature: FunctionSignature<*>, ordinary: Boolean) {
        canonical(signature.returnType())
        val seen = Collections.newSetFromMap(IdentityHashMap<InlineSignature<*>, Boolean>())
        fun inline(value: InlineSignature<*>) {
            if (!seen.add(value)) return
            canonical(value.returnType())
            value.parameters().forEach { parameter ->
                canonical(parameter.type())
                parameter.inlineSignature()?.let(::inline)
            }
        }
        signature.parameters().forEach { parameter ->
            identifier(parameter.name())
            canonical(parameter.type())
            parameter.inlineSignature()?.let(::inline)
            if ((parameter.type() as ImmutableTypeRef<*>).kind == TypeKind.VOID) fail("A parameter cannot have void type")
            if (ordinary && parameter.mode() != ParamMode.VALUE)
                fail("Generated instance methods support only VALUE parameters; ${parameter.mode()} is not a runtime parameter convention")
        }
    }

    /** Exactly one call; no processor or GeneratedMethodBody is retained in the resulting plan. */
    fun prepare(processor: TemplateProcessor): PreparedDeclarationEdits {
        check(state == State.NEW) { "A declaration validator is single-use" }
        state = State.BUILDING
        try {
            val result = processor.expand(context, template)
            val edits = result as ImmutableDeclarationEdits
            if (edits.authority !== authority) fail("Declaration edits belong to another processor invocation")
            val identities = Collections.newSetFromMap(IdentityHashMap<DeclarationEdit, Boolean>())
            edits.all().forEach { edit ->
                if ((edit as IssuedDeclarationEdit).authority !== authority || issued.none { it === edit })
                    fail("Declaration edit was not issued by this processor invocation", edit.source())
                if (!identities.add(edit)) fail("The same declaration edit was returned twice", edit.source())
            }
            if (identities.size != issued.size) fail("Every constructed declaration edit must be included in the returned edits")
            validateNames(edits.all())
            val prepared = edits.all().filterIsInstance<MethodEdit>().map { edit ->
                val original = methods.getValue(edit)
                val previous = original.code.environment
                val known = session.validationEnvironment()
                val env = ValidationEnvironment(
                    session.arena,
                    previous.externalBindings,
                    known.functions,
                    known.inlineTargets,
                    previous.resultRegion,
                    known.types,
                    fields
                )
                val checked = CodeValidator().validate(edit.body(), env, ValidationPhase.PRE_EXPANSION) as PrevalidatedCode
                PreparedMethodDeclaration(edit, checked, original.self, original.parameters)
            }
            val helpers = session.transaction.snapshot().helpers.map { helper ->
                val previous = helper.code.environment
                val known = session.validationEnvironment()
                val env = ValidationEnvironment(
                    session.arena,
                    previous.externalBindings,
                    known.functions,
                    known.inlineTargets,
                    previous.resultRegion,
                    known.types,
                    fields
                )
                helper.copy(code = CodeValidator().validate(helper.code.code, env, ValidationPhase.PRE_EXPANSION) as PrevalidatedCode)
            }
            edits.all().filterIsInstance<InternalDeclarationEdit<*>>().forEach { edit ->
                if (helpers.none { it.function === edit.function() }) fail("Internal declaration is not a helper staged by this invocation", edit.source())
            }
            state = State.PREPARED
            return PreparedDeclarationEdits(edits, prepared, helpers, session, whitelist)
        } catch (failure: Throwable) {
            reject(failure)
        }
    }

    private fun sameSignature(name: String, left: FunctionSignature<*>, method: MethodView): Boolean = name == method.name() &&
        method.readonlyParameters().isEmpty() &&
        sameParameters(left.parameters(), method.signature().parameters())

    // Matches the declaration signature rule: parameter types/count, never return type or mode.
    private fun sameParameters(left: List<ParameterView>, right: List<ParameterView>): Boolean = left.size == right.size && left.zip(right).all { (a, b) -> a.type().sameType(b.type()) }

    private fun validateNames(edits: List<DeclarationEdit>) {
        val generated = arrayListOf<MethodEdit>()
        val metadata = template.annotations().map { it.name() }.toMutableSet()
        edits.forEach { edit ->
            when (edit) {
                is MethodEdit -> {
                    owner(edit.owner())
                    if (generated.any { it.name() == edit.name() && sameParameters(it.signature().parameters(), edit.signature().parameters()) })
                        fail("Duplicate generated method signature '${edit.name()}'", edit.source())
                    template.methods().firstOrNull { existing ->
                        sameSignature(edit.name(), edit.signature(), existing) &&
                            (existing.owner().sameType(template.type()) || existing.access() < DeclarationAccess.PRIVATE)
                    }?.let { existing ->
                        fail("Generated method '${edit.name()}' conflicts with an existing ${if (existing.owner().sameType(template.type())) "local" else "inherited"} signature from ${existing.source()}", edit.source())
                    }
                    generated += edit
                }
                is MetadataEdit -> {
                    owner(edit.owner())
                    val annotation = edit.annotation()
                    val rule = whitelist[annotation.name()] ?: fail("Annotation metadata '${annotation.name()}' is not whitelisted", edit.source())
                    checkMetadata(ConstantValue.Record(annotation.arguments()), rule.arguments)
                    if (!rule.repeatable && !metadata.add(annotation.name())) fail("Duplicate annotation metadata '${annotation.name()}'", edit.source())
                }
                is InternalDeclarationEdit<*> -> Unit // Its actual staged-helper ownership is checked above.
            }
        }
    }

    private fun checkMetadata(value: ConstantValue, shape: DeclarationMetadataShape) {
        val matches = when (shape) {
            is DeclarationMetadataShape.Record ->
                value is ConstantValue.Record &&
                    value.fields.keys == shape.fields.keys &&
                    value.fields.all { (name, child) ->
                        checkMetadata(child, shape.fields.getValue(name))
                        true
                    }
            is DeclarationMetadataShape.Sequence ->
                value is ConstantValue.Sequence &&
                    value.elements.all {
                        checkMetadata(it, shape.element)
                        true
                    }
            is DeclarationMetadataShape.Scalar -> when (shape.shape) {
                ConstantShape.INT32 -> value is ConstantValue.Integral && value.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
                ConstantShape.BOOL -> value is ConstantValue.Bool
                ConstantShape.FLOAT32 -> value is ConstantValue.FloatBits
                ConstantShape.FLOAT64 -> value is ConstantValue.DoubleBits
                ConstantShape.STRING -> value is ConstantValue.Text
                ConstantShape.NBT -> value is ConstantValue.Nbt
                ConstantShape.TYPE -> value is ConstantValue.TypeValue && graph.owns(value.type)
                ConstantShape.NULL -> value === ConstantValue.NullValue
            }
        }
        if (!matches) fail("Annotation metadata arguments do not match the compiler-whitelisted shape")
    }

    private val declarations = object : DeclarationBuilder {
        override fun <O, R> method(owner: TypeRef<O>, name: String, signature: FunctionSignature<R>, body: GeneratedMethodBody<O, R>): MethodEdit = construct {
            if (inMethod) fail("A method body cannot declare another member")
            this@DeclarationEditValidator.owner(owner)
            identifier(name)
            this@DeclarationEditValidator.signature(signature, ordinary = true)
            val arena = session.arena
            val scope = arena.childScope(arena.rootScope)
            val metadata = arena.metadata(scope, session.origin)
            val self = arena.ref(metadata, owner, NodeLocation(arena.freshSymbol(), writable = true))
            val refs = signature.parameters().map { parameter ->
                arena.ref(metadata, parameter.type(), NodeLocation(arena.freshSymbol(), writable = false))
            }
            val parameters = immutableList(refs.map { arena.value(it.load()) as MacroInput })
            val result = if ((signature.returnType() as ImmutableTypeRef<*>).kind == TypeKind.VOID) null
            else YieldTarget(signature.returnType(), arena.freshRegion())
            val builder = DefaultCodeBuilder(
                arena,
                types,
                session.origin,
                scope,
                access,
                result,
                minecraftFactory = session.environment.minecraftFactory
            )
            var bodyOpen = true
            val context = object : MethodBodyContext<O> {
                private fun check() {
                    active()
                    if (!bodyOpen) fail("Method body context is closed")
                }
                override fun builder(): CodeBuilder {
                    check()
                    return builder
                }
                override fun owner(): TypeRef<O> {
                    check()
                    return owner
                }
                override fun self(): Ref<O> {
                    check()
                    return self
                }
                override fun parameters(): List<MacroInput> {
                    check()
                    return parameters
                }
            }
            inMethod = true
            try {
                builder.consume { body.build(context) }
            } finally {
                bodyOpen = false
                inMethod = false
            }
            val block = builder.finish(requireResult = result != null)
            val known = session.validationEnvironment()
            val env = ValidationEnvironment(
                arena,
                listOf(self) + refs,
                known.functions,
                known.inlineTargets,
                result?.let { ValidationRegion(it.region, it.type) },
                known.types,
                fields
            )
            val pre = CodeValidator().validate(block, env, ValidationPhase.PRE_EXPANSION) as PrevalidatedCode
            val edit = ImmutableMethodEdit(authority, owner, name, signature, block)
            methods[edit] = PreparedMethodDeclaration(edit, pre, self, refs)
            issued += edit
            edit
        }

        override fun <R> internalFunction(hint: String, signature: FunctionSignature<R>, body: GeneratedBody<R>): InternalDeclarationEdit<R> = construct {
            if (inMethod) fail("A method body cannot declare another member")
            this@DeclarationEditValidator.signature(signature, ordinary = false)
            val function = helpers.define(hint, signature, body)
            ImmutableInternalDeclarationEdit(authority, function, session.site.span).also { issued += it }
        }
        override fun metadata(owner: TypeRef<*>, annotation: String, arguments: Map<String, ConstantValue>): MetadataEdit = construct {
            this@DeclarationEditValidator.owner(owner)
            val rule = whitelist[annotation] ?: fail("Annotation metadata '$annotation' is not whitelisted")
            val snapshot = ImmutableAnnotationView(annotation, arguments, session.site.span)
            checkMetadata(ConstantValue.Record(snapshot.arguments()), rule.arguments)
            ImmutableMetadataEdit(authority, owner, snapshot).also { issued += it }
        }
        override fun edits(edits: List<DeclarationEdit>): DeclarationEdits = construct {
            ImmutableDeclarationEdits(authority, edits)
        }
    }

    private val types = object : TypeService {
        private fun <T> type(block: () -> TypeRef<T>): TypeRef<T> = construct { block().also(::canonical) }
        override fun intType(): TypeRef<IntT> = type { macro.types().intType() }
        override fun boolType(): TypeRef<BoolT> = type { macro.types().boolType() }
        override fun floatType(): TypeRef<FloatT> = type { macro.types().floatType() }
        override fun stringType(): TypeRef<StringT> = type { macro.types().stringType() }
        override fun voidType(): TypeRef<VoidT> = type { macro.types().voidType() }
        override fun lookup(name: String): TypeRef<*> = type { macro.types().lookup(name) }
        override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = construct {
            argumentTypes.forEach(::canonical)
            macro.types().function(name, immutableList(argumentTypes)).also { function ->
                (function.parameters() + function.readonlyParameters()).forEach { canonical(it.type()) }
                canonical(function.returnType())
            }
        }
    }
    private val quote: Quote = object : Quote {
        private val delegate = DefaultQuote(
            session.arena,
            types,
            session.origin,
            minecraftFactory = session.environment.minecraftFactory,
            access = access
        )
        override fun block(body: Consumer<CodeBuilder>): Block = construct { delegate.block(body) }
        override fun <T> expression(type: TypeRef<T>, body: Consumer<CodeBuilder>): Expr<T> = construct {
            canonical(type)
            delegate.expression(type, body)
        }
    }
    private val helpers = object : HelperService {
        override fun <R> define(hint: String, signature: FunctionSignature<R>, body: GeneratedBody<R>): FunctionRef<R> = construct {
            this@DeclarationEditValidator.signature(signature, ordinary = false)
            val previous = access.active
            access.active = null
            try {
                macro.helpers().define(hint, signature, body)
            } finally {
                access.active = previous
            }
        }
    }
    private val resources = object : ResourceService {
        override fun reserve(kind: ResourceKind, hint: String, immutableData: ResourceData): ResourceRef = construct {
            macro.resources().reserve(kind, hint, immutableData)
        }
    }
    private val diagnostics = object : DiagnosticService {
        override fun warning(site: SourceSpan, message: String) = construct { macro.diagnostics().warning(site, message) }
        override fun error(site: SourceSpan, message: String) {
            construct { macro.diagnostics().error(site, message) }
        }
    }
    private val context = object : DeclarationContext {
        override fun quote(): Quote {
            active()
            return this@DeclarationEditValidator.quote
        }
        override fun types(): TypeService {
            active()
            return types
        }
        override fun diagnostics(): DiagnosticService {
            active()
            return diagnostics
        }
        override fun helpers(): HelperService {
            active()
            return helpers
        }
        override fun resources(): ResourceService {
            active()
            return resources
        }
        override fun declarations(): DeclarationBuilder {
            active()
            return declarations
        }
    }

    override fun close() {
        state = State.CLOSED
        session.close()
    }
}
