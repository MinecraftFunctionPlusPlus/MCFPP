package top.mcfpp.mni.api

/** Adds generated members or metadata after the compiler completes a source declaration. */
fun interface TemplateProcessor {
    /** Inspects [template] and returns edits built with [context]. Generated metadata remains data during this pass. */
    fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits
}

/** A completed MCFPP template and its effective members, including inherited members. */
sealed interface TemplateView {
    /** Returns the completed template type. */
    fun type(): TypeRef<*>
    /** Returns field references for generated reads and writes. */
    fun fields(): List<FieldRef<*>>
    /** Returns field declarations with visibility, ownership, and annotation information. */
    fun fieldDeclarations(): List<FieldDeclarationView>
    /** Returns the template's effective method declarations. */
    fun methods(): List<MethodView>
    /** Returns annotations attached to the template. */
    fun annotations(): List<AnnotationView>
    /** Returns the template's source location. */
    fun source(): SourceSpan
}

/** Visibility recorded on an MCFPP declaration. */
enum class DeclarationAccess {
    /** Accessible wherever the declaring type is accessible. */
    PUBLIC,
    /** Accessible within the declaring type and derived types. */
    PROTECTED,
    /** Accessible within the declaring type. */
    PRIVATE,
    /** Uses MCFPP's compile-private access rule. */
    COMPILE_PRIVATE
}

/** An annotation name, its constant arguments, and its source location. */
sealed interface AnnotationView {
    /** Returns the annotation's resolved name. */
    fun name(): String
    /** Returns immutable argument values indexed by parameter name. */
    fun arguments(): Map<String, ConstantValue>
    /** Returns the annotation's source location. */
    fun source(): SourceSpan
}

/** Declaration information for a field in a completed template. */
sealed interface FieldDeclarationView {
    /** Returns the field reference for generated code. */
    fun field(): FieldRef<*>
    /** Returns the type that declares this field. */
    fun owner(): TypeRef<*>
    /** Returns the field's declared visibility. */
    fun access(): DeclarationAccess
    /** Returns true when the field declaration permits null values. */
    fun nullable(): Boolean
    /** Returns annotations attached to the field. */
    fun annotations(): List<AnnotationView>
    /** Returns the field's source location. */
    fun source(): SourceSpan
}

/** Declaration information for a method in a completed template. */
sealed interface MethodView {
    /** Returns the method name. */
    fun name(): String
    /** Returns the type that declares this method. */
    fun owner(): TypeRef<*>
    /** Returns ordinary parameters and the result type. */
    fun signature(): FunctionSignature<*>
    /** Returns readonly parameters in declaration order. */
    fun readonlyParameters(): List<ParameterView>
    /** Returns the method's declared visibility. */
    fun access(): DeclarationAccess
    /** Returns true for a static method. */
    fun isStatic(): Boolean
    /** Returns true for an abstract method. */
    fun isAbstract(): Boolean
    /** Returns true for a final method. */
    fun isFinal(): Boolean
    /** Returns annotations attached to the method. */
    fun annotations(): List<AnnotationView>
    /** Returns the method's source location. */
    fun source(): SourceSpan
}

/** Services available while a [TemplateProcessor] builds edits for the current declaration. */
interface DeclarationContext {
    /** Returns the entry point for building generated bodies. */
    fun quote(): Quote
    /** Returns type and function lookup in the declaration's environment. */
    fun types(): TypeService
    /** Returns the compiler message service. */
    fun diagnostics(): DiagnosticService
    /** Returns the generated helper service. */
    fun helpers(): HelperService
    /** Returns the generated resource service. */
    fun resources(): ResourceService
    /** Returns the builder for edits to the current declaration. */
    fun declarations(): DeclarationBuilder
}

/** Builds additions to the declaration currently being processed. */
interface DeclarationBuilder {
    /**
     * Builds a new public instance method on the current template [owner].
     * Choose a signature distinct from existing and inherited methods. Explicit parameters use VALUE mode.
     * [body] runs immediately and receives the instance separately through [MethodBodyContext.self].
     */
    fun <O, R> method(
        owner: TypeRef<O>,
        name: String,
        signature: FunctionSignature<R>,
        body: GeneratedMethodBody<O, R>
    ): MethodEdit

    /** Builds an internal helper with [hint] as its suggested name, using [HelperService.define]'s parameter rules. */
    fun <R> internalFunction(hint: String, signature: FunctionSignature<R>, body: GeneratedBody<R>): InternalDeclarationEdit<R>

    /** Builds metadata for [owner]. The annotation name and argument types must match a compiler-supported annotation. */
    fun metadata(owner: TypeRef<*>, annotation: String, arguments: Map<String, ConstantValue>): MetadataEdit
    /** Groups edits from this builder into the result returned by [TemplateProcessor.expand]. */
    fun edits(edits: List<DeclarationEdit>): DeclarationEdits
}

/** A JVM callback that constructs a generated instance method body once, during declaration processing. */
fun interface GeneratedMethodBody<O, R> {
    /** Builds the method body. Use [CodeBuilder.yield] when the signature requires a result. */
    fun build(context: MethodBodyContext<O>)
}

/** The builder, instance, and parameters available while constructing a generated method. */
interface MethodBodyContext<O> {
    /** Returns the builder for this method body. */
    fun builder(): CodeBuilder
    /** Returns the type that owns the generated method. */
    fun owner(): TypeRef<O>
    /** Returns a writable reference to this method's instance. */
    fun self(): Ref<O>
    /** Returns explicit parameters in signature order. */
    fun parameters(): List<MacroInput>
}

/** One proposed addition, applied after the compiler validates the processor result. */
sealed interface DeclarationEdit {
    /** Returns the source location associated with this addition. */
    fun source(): SourceSpan
}
/** A proposed public instance method and its generated body. */
sealed interface MethodEdit : DeclarationEdit {
    /** Returns the type receiving the method. */
    fun owner(): TypeRef<*>
    /** Returns the proposed method name. */
    fun name(): String
    /** Returns the proposed parameters and result type. */
    fun signature(): FunctionSignature<*>
    /** Returns the generated method body. */
    fun body(): Block
}
/** A proposed internal helper function. */
sealed interface InternalDeclarationEdit<R> : DeclarationEdit {
    /** Returns the reference that generated code can call. */
    fun function(): FunctionRef<R>
}
/** A proposed annotation addition to a template. */
sealed interface MetadataEdit : DeclarationEdit {
    /** Returns the type receiving the annotation. */
    fun owner(): TypeRef<*>
    /** Returns the proposed annotation and constant arguments. */
    fun annotation(): AnnotationView
}
/** The complete group of additions returned by a declaration processor. */
sealed interface DeclarationEdits {
    /** Returns proposed additions in their supplied order. */
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
