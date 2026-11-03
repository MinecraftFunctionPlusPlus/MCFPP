package top.mcfpp.mni.api

/** Processes one completed declaration. Generated annotation metadata does not invoke processors. */
fun interface TemplateProcessor {
    fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits
}

/** Compiler-issued facts only: these views never contain a compiler scope, Var, or Function. */
sealed interface TemplateView {
    fun type(): TypeRef<*>
    fun fields(): List<FieldRef<*>>
    fun fieldDeclarations(): List<FieldDeclarationView>
    fun methods(): List<MethodView>
    fun annotations(): List<AnnotationView>
    fun source(): SourceSpan
}

enum class DeclarationAccess { PUBLIC, PROTECTED, PRIVATE, COMPILE_PRIVATE }

sealed interface AnnotationView {
    fun name(): String
    fun arguments(): Map<String, ConstantValue>
    fun source(): SourceSpan
}

sealed interface FieldDeclarationView {
    fun field(): FieldRef<*>
    fun owner(): TypeRef<*>
    fun access(): DeclarationAccess
    fun nullable(): Boolean
    fun annotations(): List<AnnotationView>
    fun source(): SourceSpan
}

sealed interface MethodView {
    fun name(): String
    fun owner(): TypeRef<*>
    fun signature(): FunctionSignature<*>
    fun readonlyParameters(): List<ParameterView>
    fun access(): DeclarationAccess
    fun isStatic(): Boolean
    fun isAbstract(): Boolean
    fun isFinal(): Boolean
    fun annotations(): List<AnnotationView>
    fun source(): SourceSpan
}

interface DeclarationContext {
    fun quote(): Quote
    fun types(): TypeService
    fun diagnostics(): DiagnosticService
    fun helpers(): HelperService
    fun resources(): ResourceService
    fun declarations(): DeclarationBuilder
}

interface DeclarationBuilder {
    /**
     * Adds a PUBLIC instance method on the current canonical owner. Explicit parameters are
     * ordinary runtime VALUE parameters; self is separate. Existing visible signature slots
     * cannot be replaced, including inherited slots. The callback is consumed immediately.
     */
    fun <O, R> method(
        owner: TypeRef<O>,
        name: String,
        signature: FunctionSignature<R>,
        body: GeneratedMethodBody<O, R>
    ): MethodEdit

    /** Adds a compiler-named internal helper using the existing helper construction protocol. */
    fun <R> internalFunction(hint: String, signature: FunctionSignature<R>, body: GeneratedBody<R>): InternalDeclarationEdit<R>

    /** Only compiler-whitelisted annotation names and exact immutable argument shapes are accepted. */
    fun metadata(owner: TypeRef<*>, annotation: String, arguments: Map<String, ConstantValue>): MetadataEdit
    fun edits(edits: List<DeclarationEdit>): DeclarationEdits
}

/** A host callback is construction input; neither the edit nor its Code tree retains it. */
fun interface GeneratedMethodBody<O, R> {
    fun build(context: MethodBodyContext<O>)
}

interface MethodBodyContext<O> {
    fun builder(): CodeBuilder
    fun owner(): TypeRef<O>
    fun self(): Ref<O>
    fun parameters(): List<MacroInput>
}

sealed interface DeclarationEdit {
    fun source(): SourceSpan
}
sealed interface MethodEdit : DeclarationEdit {
    fun owner(): TypeRef<*>
    fun name(): String
    fun signature(): FunctionSignature<*>
    fun body(): Block
}
sealed interface InternalDeclarationEdit<R> : DeclarationEdit {
    fun function(): FunctionRef<R>
}
sealed interface MetadataEdit : DeclarationEdit {
    fun owner(): TypeRef<*>
    fun annotation(): AnnotationView
}
sealed interface DeclarationEdits {
    fun all(): List<DeclarationEdit>
}

internal class ImmutableAnnotationView(
    private val identifier: String,
    arguments: Map<String, ConstantValue>,
    private val span: SourceSpan
) : AnnotationView {
    private val values = immutableMap(arguments)
    init {
        require(identifier.isNotBlank()) { "An annotation requires its canonical name" }
    }
    override fun name(): String = identifier
    override fun arguments(): Map<String, ConstantValue> = values
    override fun source(): SourceSpan = span
}

internal class ImmutableFieldDeclarationView(
    private val reference: FieldRef<*>,
    private val declaringOwner: TypeRef<*>,
    private val visibility: DeclarationAccess,
    private val isNullable: Boolean,
    annotations: List<AnnotationView> = emptyList()
) : FieldDeclarationView {
    private val metadata = immutableList(annotations)
    override fun field(): FieldRef<*> = reference
    override fun owner(): TypeRef<*> = declaringOwner
    override fun access(): DeclarationAccess = visibility
    override fun nullable(): Boolean = isNullable
    override fun annotations(): List<AnnotationView> = metadata
    override fun source(): SourceSpan = reference.source()
}

internal class ImmutableMethodView(
    val identity: FunctionKey,
    private val identifier: String,
    private val declaringOwner: TypeRef<*>,
    private val descriptor: FunctionSignature<*>,
    readonlyParameters: List<ParameterView> = emptyList(),
    private val visibility: DeclarationAccess = DeclarationAccess.PUBLIC,
    private val staticMember: Boolean = false,
    private val abstractMember: Boolean = false,
    private val finalMember: Boolean = false,
    annotations: List<AnnotationView> = emptyList(),
    private val span: SourceSpan
) : MethodView {
    private val readonly = immutableList(readonlyParameters)
    private val metadata = immutableList(annotations)
    init {
        require(identifier.isNotBlank()) { "A method requires a name" }
    }
    override fun name(): String = identifier
    override fun owner(): TypeRef<*> = declaringOwner
    override fun signature(): FunctionSignature<*> = descriptor
    override fun readonlyParameters(): List<ParameterView> = readonly
    override fun access(): DeclarationAccess = visibility
    override fun isStatic(): Boolean = staticMember
    override fun isAbstract(): Boolean = abstractMember
    override fun isFinal(): Boolean = finalMember
    override fun annotations(): List<AnnotationView> = metadata
    override fun source(): SourceSpan = span
}

/** Constructed only after declaration completion, from the complete effective member facts. */
internal class ImmutableTemplateView(
    private val owner: TypeRef<*>,
    fields: List<FieldDeclarationView>,
    methods: List<MethodView>,
    annotations: List<AnnotationView>,
    private val span: SourceSpan
) : TemplateView {
    private val fieldFacts = immutableList(fields)
    private val fieldRefs = immutableList(fields.map { it.field() })
    private val memberFacts = immutableList(methods)
    private val metadata = immutableList(annotations)
    init {
        val graph = (owner as ImmutableTypeRef<*>).graph
            ?: throw CodeModelException(span, "A declaration view requires a canonical frozen type domain")
        require(graph.owns(owner)) { "Template owner is not canonical" }
        fun type(value: TypeRef<*>) {
            require(graph.owns(value)) { "Declaration fact belongs to another canonical domain" }
        }
        val signatures = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<InlineSignature<*>, Boolean>())
        fun signature(value: InlineSignature<*>) {
            if (!signatures.add(value)) return
            type(value.returnType())
            value.parameters().forEach { parameter ->
                type(parameter.type())
                parameter.inlineSignature()?.let(::signature)
            }
        }
        fun annotation(value: AnnotationView) {
            fun constant(value: ConstantValue) {
                when (value) {
                    is ConstantValue.TypeValue -> type(value.type)
                    is ConstantValue.Sequence -> value.elements.forEach(::constant)
                    is ConstantValue.Record -> value.fields.values.forEach(::constant)
                    else -> Unit
                }
            }
            value.arguments().values.forEach(::constant)
        }
        fields.forEach { fact ->
            type(fact.owner())
            type(fact.field().type())
            val field = fact.field() as ImmutableFieldRef<*>
            require(
                field.key.owner == (fact.owner() as ImmutableTypeRef<*>).key &&
                    fact.owner().isAssignableFrom(owner) &&
                    owner.fields().any { it === field }
            ) {
                "Field is not a canonical member of the completed template"
            }
            fact.annotations().forEach(::annotation)
        }
        require(fields.map { (it.field() as ImmutableFieldRef<*>).key }.distinct().size == fields.size) { "Duplicate field facts" }
        require(owner.fields().size == fields.size && owner.fields().all { field -> fieldRefs.any { it === field } }) {
            "A declaration view must include every completed field"
        }
        methods.forEach { method ->
            type(method.owner())
            signature(method.signature())
            require(method.owner().isAssignableFrom(owner)) { "Method owner is outside the template inheritance graph" }
            method.readonlyParameters().forEach { parameter ->
                type(parameter.type())
                parameter.inlineSignature()?.let(::signature)
            }
            method.annotations().forEach(::annotation)
        }
        require(methods.map { (it as ImmutableMethodView).identity }.distinct().size == methods.size) { "Duplicate method facts" }
        annotations.forEach(::annotation)
    }
    override fun type(): TypeRef<*> = owner
    override fun fields(): List<FieldRef<*>> = fieldRefs
    override fun fieldDeclarations(): List<FieldDeclarationView> = fieldFacts
    override fun methods(): List<MethodView> = memberFacts
    override fun annotations(): List<AnnotationView> = metadata
    override fun source(): SourceSpan = span
}

internal sealed interface IssuedDeclarationEdit : DeclarationEdit {
    val authority: Any
}
internal class ImmutableMethodEdit(
    override val authority: Any,
    private val declaringOwner: TypeRef<*>,
    private val identifier: String,
    private val descriptor: FunctionSignature<*>,
    private val code: Block
) : MethodEdit,
    IssuedDeclarationEdit {
    override fun owner(): TypeRef<*> = declaringOwner
    override fun name(): String = identifier
    override fun signature(): FunctionSignature<*> = descriptor
    override fun body(): Block = code
    override fun source(): SourceSpan = code.source()
}
internal class ImmutableInternalDeclarationEdit<R>(
    override val authority: Any,
    private val reference: FunctionRef<R>,
    private val span: SourceSpan
) : InternalDeclarationEdit<R>,
    IssuedDeclarationEdit {
    override fun function(): FunctionRef<R> = reference
    override fun source(): SourceSpan = span
}
internal class ImmutableMetadataEdit(
    override val authority: Any,
    private val declaringOwner: TypeRef<*>,
    private val metadata: AnnotationView
) : MetadataEdit,
    IssuedDeclarationEdit {
    override fun owner(): TypeRef<*> = declaringOwner
    override fun annotation(): AnnotationView = metadata
    override fun source(): SourceSpan = metadata.source()
}
internal class ImmutableDeclarationEdits(val authority: Any, edits: List<DeclarationEdit>) : DeclarationEdits {
    private val values = immutableList(edits)
    override fun all(): List<DeclarationEdit> = values
}
