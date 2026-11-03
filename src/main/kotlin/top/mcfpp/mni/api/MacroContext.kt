package top.mcfpp.mni.api

/** Services belong to one expansion; retaining them cannot extend that expansion's lifetime. */
interface MacroContext {
    fun quote(): Quote
    fun types(): TypeService
    fun diagnostics(): DiagnosticService
    fun helpers(): HelperService
    fun resources(): ResourceService
    fun callSite(): SourceSpan
}

/** Resolves canonical descriptors from the current binding environment without emitting code. */
interface TypeService {
    fun intType(): TypeRef<IntT>
    fun boolType(): TypeRef<BoolT>
    fun floatType(): TypeRef<FloatT>
    fun stringType(): TypeRef<StringT>
    fun voidType(): TypeRef<VoidT>

    /** An unknown or inaccessible name produces a diagnostic at the macro call site. */
    fun lookup(name: String): TypeRef<*>

    /** Resolves an accessible overload by actual argument types; ambiguity is an error. */
    fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*>
}

interface DiagnosticService {
    fun warning(site: SourceSpan, message: String)

    /** Marks the current expansion failed; its staged output cannot be committed. */
    fun error(site: SourceSpan, message: String)
}

interface HelperService {
    /**
     * Immediately builds a helper with newly allocated parameters in signature order.
     * Only VALUE and REF parameters are supported initially. REF arguments preserve their
     * original locations and aliases. The body cannot capture an enclosing local or context self.
     */
    fun <R> define(hint: String, signature: FunctionSignature<R>, body: GeneratedBody<R>): FunctionRef<R>
}

/** A Java SAM, consumed once; the compiler does not retain the host callback in the code tree. */
fun interface GeneratedBody<R> {
    /** Parameters are immutable Value/Ref views; a non-void body produces its result with yield. */
    fun build(builder: CodeBuilder, parameters: List<MacroInput>)
}

enum class ResourceKind { OBJECTIVE, STORAGE, INTERNAL_FUNCTION }

/** Closed immutable resource descriptors; kind-specific schemas are checked before reservation. */
class ResourceData private constructor(private val descriptor: ConstantValue) {
    fun value(): ConstantValue = descriptor

    companion object {
        @JvmStatic fun of(descriptor: ConstantValue): ResourceData = ResourceData(descriptor)
    }
}

/** Read-only compiler-issued handle. Services must reject handles outside their owning expansion. */
interface ResourceRef {
    fun kind(): ResourceKind
    fun name(): String
    fun source(): SourceSpan
}

interface ResourceService {
    /**
     * Stages a supported resource with a deterministic, isolated name. This does not publish it.
     * Unsupported kinds or descriptor schemas are diagnosed before any resource is committed.
     */
    fun reserve(kind: ResourceKind, hint: String, immutableData: ResourceData): ResourceRef
}
