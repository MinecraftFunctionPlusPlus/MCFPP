package top.mcfpp.mni.api

/** Marks an MCFPP int in generated code. Obtain its type descriptor from [TypeService.intType]. */
class IntT private constructor()
/** Marks an MCFPP bool in generated code. */
class BoolT private constructor()
/** Marks an MCFPP float in generated code. */
class FloatT private constructor()
/** Marks an MCFPP string in generated code. */
class StringT private constructor()
/** Marks an MCFPP void result. */
class VoidT private constructor()
/** Marks an MCFPP entity value in generated code. */
class EntityT private constructor()
/** Marks an MCFPP position value in generated code. */
class PositionT private constructor()
/** Marks an MCFPP block value in generated code. */
class BlockT private constructor()
/** Marks an MCFPP ScoreRef value, which stores a Minecraft scoreboard address. */
class ScoreRefT private constructor()
/** Marks an MCFPP NBTRef value, which stores a Minecraft NBT address for values of type [T]. */
class NBTRefT<T> private constructor()

/**
 * A source range for compiler messages.
 *
 * @property file Logical source file name.
 * @property startOffset Zero-based offset of the first character.
 * @property endOffset Zero-based offset immediately after the range.
 * @property startLine One-based line number at the start.
 * @property startColumn One-based column number at the start.
 * @property endLine One-based line number at the end.
 * @property endColumn One-based column number immediately after the range.
 */
data class SourceSpan(
    val file: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int
) {
    init {
        require(file.isNotBlank()) { "A source range requires a logical file name" }
        require(startOffset >= 0 && endOffset >= startOffset) { "Invalid source offsets" }
        require(startLine > 0 && startColumn > 0 && endLine >= startLine && endColumn > 0) { "Invalid source coordinates" }
        require(endLine != startLine || endColumn >= startColumn) { "Source coordinates run backwards" }
    }

    override fun toString(): String = "$file:$startLine:$startColumn"

    companion object {
        /** Returns an empty range in the logical file `<generated>`. */
        @JvmStatic
        fun unknown(): SourceSpan = SourceSpan("<generated>", 0, 0, 1, 1, 1, 1)
    }
}

/** A compiler-provided MCFPP type. Obtain descriptors through [TypeService] or declaration views. */
sealed interface TypeRef<T> {
    /** Returns the type's display name. */
    fun name(): String
    /** Tests whether [other] identifies the same MCFPP type in the same compilation. */
    fun sameType(other: TypeRef<*>): Boolean
    /** Tests whether a value of [other] can be assigned to this type. */
    fun isAssignableFrom(other: TypeRef<*>): Boolean
    /** Returns the fields visible on this type. */
    fun fields(): List<FieldRef<*>>
    /** Returns the callback signature for a function type, or null for other types. */
    fun inlineSignature(): InlineSignature<*>? = null
}

/** Identifies an MCFPP field for [CodeBuilder.field] or [CodeBuilder.fieldRef]. */
sealed interface FieldRef<T> {
    /** Returns the field name. */
    fun name(): String
    /** Returns the type of the field value. */
    fun type(): TypeRef<T>
    /** Returns true when the field supports assignment. */
    fun writable(): Boolean
    /** Returns the field's source location. */
    fun source(): SourceSpan
}

/** How a generated function or macro receives an argument. */
enum class ParamMode {
    /** Evaluates the argument once and passes its value. */
    VALUE,
    /** Passes a writable location shared with the caller. */
    REF,
    /** Supplies a value known during compilation. */
    CONST,
    /** Supplies a source callback for insertion during expansion. */
    INLINE
}

/** A parameter's name, MCFPP type, and argument mode. */
sealed interface ParameterView {
    /** Returns the name used in the function signature. */
    fun name(): String
    /** Returns the parameter's MCFPP type. */
    fun type(): TypeRef<*>
    /** Returns how callers pass the argument. */
    fun mode(): ParamMode
    /** Returns the callback signature when this parameter describes a callback, or null otherwise. */
    fun inlineSignature(): InlineSignature<*>? = null

    companion object {
        /** Creates a parameter description, deriving a callback signature from [type] when available. */
        @JvmStatic
        fun of(name: String, type: TypeRef<*>, mode: ParamMode): ParameterView = ImmutableParameterView(name, type, mode)
        /** Creates a parameter description with an explicit callback signature. */
        @JvmStatic
        fun of(name: String, type: TypeRef<*>, mode: ParamMode, inlineSignature: InlineSignature<*>): ParameterView = ImmutableParameterView(name, type, mode, inlineSignature)
    }
}

/** The ordered parameters and result type of an MCFPP callback. */
sealed interface InlineSignature<R> {
    /** Returns parameters in call order. */
    fun parameters(): List<ParameterView>
    /** Returns the MCFPP result type. */
    fun returnType(): TypeRef<R>
}

/** A function signature used when constructing generated declarations. */
sealed interface FunctionSignature<R> : InlineSignature<R> {
    companion object {
        /** Creates a signature from a parameter list with unique names and a result type. */
        @JvmStatic
        fun <R> of(parameters: List<ParameterView>, returnType: TypeRef<R>): FunctionSignature<R> = ImmutableFunctionSignature(parameters, returnType)
    }
}

/** Identifies an MCFPP function that generated code can call. */
sealed interface FunctionRef<R> {
    /** Returns the function name. */
    fun name(): String
    /** Returns ordinary parameters in call order. */
    fun parameters(): List<ParameterView>
    /** Returns the MCFPP result type. */
    fun returnType(): TypeRef<R>
    /** Returns parameters from the function's readonly parameter list, in declaration order. */
    fun readonlyParameters(): List<ParameterView> = emptyList()
}

/** An immutable value available during compilation, such as an annotation argument or CONST input. */
sealed class ConstantValue private constructor() {
    /** An integer stored as a signed 64-bit JVM value. */
    data class Integral(val value: Long) : ConstantValue()
    /** A 32-bit floating-point value stored by its IEEE 754 bits. */
    data class FloatBits(val bits: Int) : ConstantValue()
    /** A 64-bit floating-point value stored by its IEEE 754 bits. */
    data class DoubleBits(val bits: Long) : ConstantValue()
    /** A Boolean value. */
    data class Bool(val value: Boolean) : ConstantValue()
    /** A string value. */
    data class Text(val value: String) : ConstantValue()
    /** NBT data represented as SNBT text. */
    data class Nbt(val snbt: String) : ConstantValue()
    /** An MCFPP type used as a constant value. */
    data class TypeValue(val type: TypeRef<*>) : ConstantValue()
    /** The MCFPP null value. */
    object NullValue : ConstantValue()

    /** An ordered immutable copy of [elements]. */
    class Sequence(elements: List<ConstantValue>) : ConstantValue() {
        /** Constant elements in their original order. */
        val elements: List<ConstantValue> = immutableList(elements)
        override fun equals(other: Any?): Boolean = other is Sequence && elements == other.elements
        override fun hashCode(): Int = elements.hashCode()
        override fun toString(): String = "Sequence($elements)"
    }

    /** An immutable copy of named [fields], sorted by field name. */
    class Record(fields: Map<String, ConstantValue>) : ConstantValue() {
        /** Constant values indexed by field name. */
        val fields: Map<String, ConstantValue> = immutableMap(fields.toSortedMap())
        override fun equals(other: Any?): Boolean = other is Record && fields == other.fields
        override fun hashCode(): Int = fields.hashCode()
        override fun toString(): String = "Record($fields)"
    }

    companion object {
        /** Creates an integer constant. */
        @JvmStatic fun integral(value: Long): ConstantValue = Integral(value)
        /** Creates a Boolean constant. */
        @JvmStatic fun bool(value: Boolean): ConstantValue = Bool(value)
        /** Creates a string constant. */
        @JvmStatic fun text(value: String): ConstantValue = Text(value)
        /** Copies [elements] into an immutable sequence constant. */
        @JvmStatic fun sequence(elements: List<ConstantValue>): ConstantValue = Sequence(elements)
        /** Copies [fields] into an immutable record constant. */
        @JvmStatic fun record(fields: Map<String, ConstantValue>): ConstantValue = Record(fields)
    }
}

/**
 * Reports invalid generated code or an invalid use of a code builder.
 * @property source Source location included in the error message.
 * @param message Explanation of the invalid operation.
 */
class CodeModelException(val source: SourceSpan, message: String) : IllegalArgumentException("$message at $source")

internal data class TypeKey(val universe: Long, val id: Long, val revision: Long = 0)
internal data class FieldKey(val owner: TypeKey, val declaration: Long)
internal data class FunctionKey(val universe: Long, val declaration: String)
internal enum class TypeKind { INT, BOOL, FLOAT, STRING, VOID, ENTITY, POSITION, BLOCK, OTHER }
internal enum class FunctionKind { ORDINARY, NATIVE, MACRO, HELPER }

internal data class IndexSchema(val element: TypeKey, val writable: Boolean)
internal enum class ConstantShape { INT32, BOOL, FLOAT32, FLOAT64, STRING, NBT, TYPE, NULL }
internal sealed interface ConstantSchema {
    data class Scalar(val shape: ConstantShape) : ConstantSchema
    data class Sequence(val element: TypeKey) : ConstantSchema
    class Record(fields: Map<String, TypeKey>) : ConstantSchema {
        val fields: Map<String, TypeKey> = immutableMap(fields)
    }
}

internal data class FrozenParameterRecord(
    val name: String,
    val type: TypeKey,
    val mode: ParamMode,
    val inlineSignature: FrozenInlineSignatureRecord? = null
)
internal class FrozenInlineSignatureRecord(parameters: List<FrozenParameterRecord>, val result: TypeKey) {
    val parameters = immutableList(parameters)
    init {
        require(this.parameters.all { it.name.isNotBlank() }) { "A parameter requires a name" }
        require(this.parameters.map { it.name }.distinct().size == this.parameters.size) { "Duplicate parameter names" }
    }
}

/** Only keys connect records, so recursive declarations never require mutable published handles. */
internal class FrozenTypeRecord(
    val key: TypeKey,
    val name: String,
    val stableSourceId: String,
    val kind: TypeKind,
    acceptedSources: Set<TypeKey> = emptySet(),
    fields: List<FieldKey> = emptyList(),
    val index: IndexSchema? = null,
    val constant: ConstantSchema? = null,
    val inlineSignature: FrozenInlineSignatureRecord? = null
) {
    val acceptedSources = immutableSet(acceptedSources + key)
    val fields = immutableList(fields)
    init {
        require(name.isNotBlank() && stableSourceId.isNotBlank()) { "A frozen type requires a name and stable source identity" }
        require(this.fields.distinct().size == this.fields.size) { "Duplicate field identities" }
    }
}
internal data class FrozenFieldRecord(
    val key: FieldKey,
    val name: String,
    val type: TypeKey,
    val writable: Boolean,
    val source: SourceSpan
)
internal data class FrozenConversionRecord(val from: TypeKey, val to: TypeKey, val kind: ConversionKind)

/** Validated as a complete domain before any canonical type/field view is published. */
internal class FrozenTypeGraph private constructor(
    private val records: Map<TypeKey, FrozenTypeRecord>,
    private val fieldRecords: Map<FieldKey, FrozenFieldRecord>,
    private val conversions: Set<FrozenConversionRecord>
) {
    private val handles: Map<TypeKey, ImmutableTypeRef<*>> = immutableMap(
        records.mapValues { (_, record) ->
            ImmutableTypeRef<Any?>(record.key, record.name, kind = record.kind, graph = this)
        }
    )
    private val fieldHandles: Map<FieldKey, FieldRef<*>> = immutableMap(
        fieldRecords.mapValues { (_, record) ->
            ImmutableFieldRef(record.key, record.name, type(record.type), record.writable, record.source)
        }
    )
    private val signatures: Map<TypeKey, InlineSignature<*>> = immutableMap(
        records.mapNotNull { (key, record) ->
            record.inlineSignature?.let { key to FrozenInlineSignatureView(this, it) }
        }.toMap()
    )
    fun type(key: TypeKey): ImmutableTypeRef<*> = handles[key] ?: error("Type is outside this frozen domain: $key")
    fun field(key: FieldKey): FieldRef<*> = fieldHandles[key] ?: error("Field is outside this frozen domain: $key")
    fun fields(key: TypeKey): List<FieldRef<*>> = immutableList(record(key).fields.map(::field))
    fun index(key: TypeKey): IndexSchema? = record(key).index
    fun constantSchema(key: TypeKey): ConstantSchema? = record(key).constant
    fun permits(from: TypeKey, to: TypeKey, kind: ConversionKind): Boolean = from in records && to in records && (from == to || FrozenConversionRecord(from, to, kind) in conversions)
    fun owns(type: TypeRef<*>): Boolean = type is ImmutableTypeRef<*> && handles[type.key] === type
    internal fun record(key: TypeKey): FrozenTypeRecord = records[key] ?: error("Type is outside this frozen domain: $key")
    internal fun signature(key: TypeKey): InlineSignature<*>? = signatures[key]

    companion object {
        fun freeze(
            types: Collection<FrozenTypeRecord>,
            fields: Collection<FrozenFieldRecord> = emptyList(),
            conversions: Collection<FrozenConversionRecord> = emptyList()
        ): FrozenTypeGraph {
            val typeRecords = types.associateBy { it.key }
            val declarations = fields.associateBy { it.key }
            require(typeRecords.size == types.size) { "Duplicate type identity" }
            require(declarations.size == fields.size) { "Duplicate field identity" }
            require(types.map { it.stableSourceId }.distinct().size == types.size) { "Duplicate stable source identity" }
            fun known(key: TypeKey) {
                require(key in typeRecords) { "Type is outside this frozen domain: $key" }
            }
            fun signature(value: FrozenInlineSignatureRecord) {
                known(value.result)
                value.parameters.forEach {
                    known(it.type)
                    it.inlineSignature?.let(::signature)
                }
            }
            typeRecords.values.forEach { record ->
                record.acceptedSources.forEach(::known)
                require(record.fields.all { it in declarations }) { "Unknown field identity in ${record.name}" }
                require(record.fields.map { declarations.getValue(it).name }.distinct().size == record.fields.size) {
                    "Duplicate visible field names in ${record.name}"
                }
                record.index?.let { known(it.element) }
                when (val schema = record.constant) {
                    is ConstantSchema.Sequence -> known(schema.element)
                    is ConstantSchema.Record -> schema.fields.values.forEach(::known)
                    is ConstantSchema.Scalar, null -> Unit
                }
                record.inlineSignature?.let(::signature)
            }
            declarations.values.forEach {
                known(it.key.owner)
                known(it.type)
                require(it.name.isNotBlank()) { "A field requires a name" }
            }
            conversions.forEach {
                known(it.from)
                known(it.to)
            }
            return FrozenTypeGraph(immutableMap(typeRecords), immutableMap(declarations), immutableSet(conversions))
        }
    }
}

private class FrozenInlineSignatureView(val graph: FrozenTypeGraph, record: FrozenInlineSignatureRecord) : InlineSignature<Any?> {
    private val result = graph.type(record.result)
    private val parameters = immutableList(record.parameters.map { FrozenParameterView(graph, it) })
    override fun parameters(): List<ParameterView> = parameters
    @Suppress("UNCHECKED_CAST")
    override fun returnType(): TypeRef<Any?> = result as TypeRef<Any?>
}
private class FrozenParameterView(private val graph: FrozenTypeGraph, private val record: FrozenParameterRecord) : ParameterView {
    override fun name(): String = record.name
    override fun type(): TypeRef<*> = graph.type(record.type)
    override fun mode(): ParamMode = record.mode
    override fun inlineSignature(): InlineSignature<*>? = record.inlineSignature?.let { FrozenInlineSignatureView(graph, it) }
        ?: graph.signature(record.type)
}

internal class ImmutableTypeRef<T>(
    val key: TypeKey,
    private val displayName: String,
    assignableFrom: Collection<TypeKey> = emptyList(),
    fields: List<FieldRef<*>> = emptyList(),
    kind: TypeKind = TypeKind.OTHER,
    inlineSignature: InlineSignature<*>? = null,
    internal val graph: FrozenTypeGraph? = null,
    stableSourceId: String? = null
) : TypeRef<T> {
    private val independentKind = kind
    private val independentSourceId = stableSourceId
    val kind: TypeKind = graph?.record(key)?.kind ?: kind
    val stableSourceId: String? = graph?.record(key)?.stableSourceId ?: stableSourceId
    private val acceptedSources = immutableSet(assignableFrom + key)
    private val declaredFields = immutableList(fields)
    private val callbackSignature = snapshotInlineSignature(inlineSignature)

    init {
        require(displayName.isNotBlank()) { "A type requires a display name" }
        if (graph != null) require(
            assignableFrom.isEmpty() &&
                fields.isEmpty() &&
                inlineSignature == null &&
                displayName == graph.record(key).name &&
                independentKind == graph.record(key).kind &&
                independentSourceId == null
        ) {
            "A graph type cannot override its frozen record"
        }
    }

    override fun name(): String = displayName
    override fun sameType(other: TypeRef<*>): Boolean = this === other ||
        other is ImmutableTypeRef<*> &&
        key == other.key &&
        (if (graph == null) other.graph == null else graph === other.graph && graph.owns(this) && graph.owns(other))
    override fun isAssignableFrom(other: TypeRef<*>): Boolean = other is ImmutableTypeRef<*> &&
        if (graph == null) other.graph == null && other.key in acceptedSources
        else graph.owns(this) && graph.owns(other) && other.key in graph.record(key).acceptedSources
    override fun fields(): List<FieldRef<*>> = graph?.fields(key) ?: declaredFields
    override fun inlineSignature(): InlineSignature<*>? = graph?.signature(key) ?: callbackSignature
    override fun equals(other: Any?): Boolean = other is TypeRef<*> && sameType(other)
    override fun hashCode(): Int = key.hashCode()
    override fun toString(): String = displayName
}

internal class ImmutableFieldRef<T>(
    val key: FieldKey,
    private val identifier: String,
    private val fieldType: TypeRef<T>,
    private val isWritable: Boolean,
    private val span: SourceSpan
) : FieldRef<T> {
    init {
        require(identifier.isNotBlank()) { "A field requires a name" }
    }
    override fun name(): String = identifier
    override fun type(): TypeRef<T> = fieldType
    override fun writable(): Boolean = isWritable
    override fun source(): SourceSpan = span
    override fun equals(other: Any?): Boolean = other is ImmutableFieldRef<*> && key == other.key
    override fun hashCode(): Int = key.hashCode()
}

internal class ImmutableParameterView(
    private val identifier: String,
    private val parameterType: TypeRef<*>,
    private val parameterMode: ParamMode,
    inlineSignature: InlineSignature<*>? = null
) : ParameterView {
    private val callbackSignature = snapshotInlineSignature(inlineSignature ?: parameterType.inlineSignature())
    init {
        require(identifier.isNotBlank()) { "A parameter requires a name" }
    }
    override fun name(): String = identifier
    override fun type(): TypeRef<*> = parameterType
    override fun mode(): ParamMode = parameterMode
    override fun inlineSignature(): InlineSignature<*>? = callbackSignature
}

/** Copy the descriptor graph; no caller-owned parameter list survives publication. */
private fun snapshotInlineSignature(signature: InlineSignature<*>?): InlineSignature<*>? = signature?.let {
    if (it is FrozenInlineSignatureView) it else ImmutableFunctionSignature(
        it.parameters().map { parameter ->
            ImmutableParameterView(parameter.name(), parameter.type(), parameter.mode(), parameter.inlineSignature())
        },
        it.returnType()
    )
}

internal class ImmutableFunctionSignature<R>(
    parameters: List<ParameterView>,
    private val resultType: TypeRef<R>
) : FunctionSignature<R> {
    private val params = immutableList(parameters)
    init {
        require(params.map { it.name() }.distinct().size == params.size) { "Duplicate parameter names" }
    }
    override fun parameters(): List<ParameterView> = params
    override fun returnType(): TypeRef<R> = resultType
}

internal class ImmutableFunctionRef<R>(
    val key: FunctionKey,
    private val identifier: String,
    val signature: FunctionSignature<R>,
    val kind: FunctionKind,
    val sessionId: SessionId? = null,
    readonlyParameters: List<ParameterView> = emptyList()
) : FunctionRef<R> {
    private val readonly = immutableList(
        readonlyParameters.map {
            ImmutableParameterView(it.name(), it.type(), it.mode(), it.inlineSignature())
        }
    )
    init {
        require(identifier.isNotBlank()) { "A function requires a name" }
        require(readonly.map { it.name() }.distinct().size == readonly.size) { "Duplicate readonly parameter names" }
        require(kind !in setOf(FunctionKind.MACRO, FunctionKind.HELPER) || readonly.isEmpty()) {
            "Macro and helper functions have no readonly parameters"
        }
    }
    override fun readonlyParameters(): List<ParameterView> = readonly
    override fun name(): String = identifier
    override fun parameters(): List<ParameterView> = signature.parameters()
    override fun returnType(): TypeRef<R> = signature.returnType()
}
