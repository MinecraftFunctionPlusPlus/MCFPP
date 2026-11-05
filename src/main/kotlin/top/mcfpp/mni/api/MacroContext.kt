package top.mcfpp.mni.api

/** Services for building code during one macro expansion. Use them while the provider method runs. */
interface MacroContext {
    /** Returns the code builder entry point for this expansion. */
    fun quote(): Quote
    /** Returns type and function lookup in the macro's source environment. */
    fun types(): TypeService
    /** Returns the service for compiler warnings and errors. */
    fun diagnostics(): DiagnosticService
    /** Returns the service for defining generated helper functions. */
    fun helpers(): HelperService
    /** Returns the service for reserving generated Minecraft resources. */
    fun resources(): ResourceService
    /** Returns the source location of the MCFPP macro call. */
    fun callSite(): SourceSpan
}

/** Finds MCFPP types and functions visible in the current source environment. Lookup runs during expansion. */
interface TypeService {
    /** Returns the MCFPP int type. */
    fun intType(): TypeRef<IntT>
    /** Returns the MCFPP bool type. */
    fun boolType(): TypeRef<BoolT>
    /** Returns the MCFPP float type. */
    fun floatType(): TypeRef<FloatT>
    /** Returns the MCFPP string type. */
    fun stringType(): TypeRef<StringT>
    /** Returns the MCFPP void type for a body that produces statements. */
    fun voidType(): TypeRef<VoidT>

    /** An unknown or inaccessible name produces a diagnostic at the macro call site. */
    fun lookup(name: String): TypeRef<*>

    /** Finds an accessible overload for [argumentTypes]. Reports an error if several overloads match equally. */
    fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*>
}

/** Reports messages at source locations chosen by a macro or declaration processor. */
interface DiagnosticService {
    /** Adds a compiler warning at [site] and allows expansion to continue. */
    fun warning(site: SourceSpan, message: String)

    /** Reports a compiler error at [site]. The compiler discards output from the failed expansion. */
    fun error(site: SourceSpan, message: String)
}

/** Defines generated functions that other generated code can call through [CodeBuilder.call]. */
interface HelperService {
    /**
     * Builds a helper immediately and returns its function reference.
     * [hint] suggests its generated name. [signature] accepts VALUE and REF parameters in declaration order.
     * REF parameters retain the caller's locations, including shared locations.
     * Build the helper body from its own parameters and locally declared values.
     */
    fun <R> define(hint: String, signature: FunctionSignature<R>, body: GeneratedBody<R>): FunctionRef<R>
}

/** A JVM callback that constructs a helper body once, during expansion. */
fun interface GeneratedBody<R> {
    /** Builds with Value or Ref parameters in signature order. Use [CodeBuilder.yield] for a result of type [R]. */
    fun build(builder: CodeBuilder, parameters: List<MacroInput>)
}

/** The Minecraft resource to reserve through [ResourceService]. */
enum class ResourceKind {
    /** A scoreboard objective. */
    OBJECTIVE,
    /** A command storage resource. */
    STORAGE,
    /** A compiler-generated function. Create these through [HelperService.define]. */
    INTERNAL_FUNCTION
}

/** Immutable data describing a resource. [ResourceService.reserve] checks the data against the resource kind. */
class ResourceData private constructor(private val descriptor: ConstantValue) {
    /** Returns the resource description supplied to [of]. */
    fun value(): ConstantValue = descriptor

    companion object {
        /** Wraps [descriptor] for a resource reservation. */
        @JvmStatic fun of(descriptor: ConstantValue): ResourceData = ResourceData(descriptor)
    }
}

/** Identifies a resource reserved during the current expansion. */
interface ResourceRef {
    /** Returns the reserved resource's kind. */
    fun kind(): ResourceKind
    /** Returns the name selected by the compiler. */
    fun name(): String
    /** Returns the source location associated with the reservation. */
    fun source(): SourceSpan
}

/** Reserves resources for publication when the expansion succeeds. */
interface ResourceService {
    /**
     * Reserves a resource of [kind] using [hint] as a suggested name and [immutableData] as its description.
     * The compiler assigns a reproducible name and checks the description before publishing the resource.
     *
     * Supported descriptions are records with these fields:
     * - OBJECTIVE: `criteria` contains the text `dummy`.
     * - STORAGE: `initial` contains numbers, Booleans, text, or nested records and sequences of these values.
     *
     * Use [HelperService.define] to construct an INTERNAL_FUNCTION.
     */
    fun reserve(kind: ResourceKind, hint: String, immutableData: ResourceData): ResourceRef
}
