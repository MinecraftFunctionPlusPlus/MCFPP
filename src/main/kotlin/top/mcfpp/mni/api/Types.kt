package top.mcfpp.mni.api

/** Phantom types describe MCFPP values, rather than their JVM representation. */
class IntT private constructor()
class BoolT private constructor()
class FloatT private constructor()
class StringT private constructor()
class VoidT private constructor()
class EntityT private constructor()
class PositionT private constructor()
class BlockT private constructor()
/** Owned public address values; these do not model compiler Ref<IntT>/Ref<T> locations. */
class ScoreRefT private constructor()
class NBTRefT<T> private constructor()

/** A source range uses zero-based, half-open offsets and one-based line/column numbers. */
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
        @JvmStatic
        fun unknown(): SourceSpan = SourceSpan("<generated>", 0, 0, 1, 1, 1, 1)
    }
}

/** Only compiler-created descriptors implement this view. A Java type parameter is not a type proof. */
sealed interface TypeRef<T> {
    fun name(): String
    fun sameType(other: TypeRef<*>): Boolean
    fun isAssignableFrom(other: TypeRef<*>): Boolean
    fun fields(): List<FieldRef<*>>
    fun inlineSignature(): InlineSignature<*>? = null
}

sealed interface FieldRef<T> {
    fun name(): String
    fun type(): TypeRef<T>
    fun writable(): Boolean
    fun source(): SourceSpan
}

enum class ParamMode { VALUE, REF, CONST, INLINE }

sealed interface ParameterView {
    fun name(): String
    fun type(): TypeRef<*>
    fun mode(): ParamMode
    fun inlineSignature(): InlineSignature<*>? = null

    companion object {
        @JvmStatic
        fun of(name: String, type: TypeRef<*>, mode: ParamMode): ParameterView = ImmutableParameterView(name, type, mode)
        @JvmStatic
        fun of(name: String, type: TypeRef<*>, mode: ParamMode, inlineSignature: InlineSignature<*>): ParameterView = ImmutableParameterView(name, type, mode, inlineSignature)
    }
}

sealed interface InlineSignature<R> {
    fun parameters(): List<ParameterView>
    fun returnType(): TypeRef<R>
}

sealed interface FunctionSignature<R> : InlineSignature<R> {
    companion object {
        @JvmStatic
        fun <R> of(parameters: List<ParameterView>, returnType: TypeRef<R>): FunctionSignature<R> = ImmutableFunctionSignature(parameters, returnType)
    }
}

sealed interface FunctionRef<R> {
    fun name(): String
    fun parameters(): List<ParameterView>
    fun returnType(): TypeRef<R>
    fun readonlyParameters(): List<ParameterView> = emptyList()
}

/** Closed immutable payloads; mutable tags and arbitrary host objects cannot become constants. */
sealed class ConstantValue private constructor() {
    data class Integral(val value: Long) : ConstantValue()
    data class FloatBits(val bits: Int) : ConstantValue()
    data class DoubleBits(val bits: Long) : ConstantValue()
    data class Bool(val value: Boolean) : ConstantValue()
    data class Text(val value: String) : ConstantValue()
    data class Nbt(val snbt: String) : ConstantValue()
    data class TypeValue(val type: TypeRef<*>) : ConstantValue()
    object NullValue : ConstantValue()

    class Sequence(elements: List<ConstantValue>) : ConstantValue() {
        val elements: List<ConstantValue> = immutableList(elements)
        override fun equals(other: Any?): Boolean = other is Sequence && elements == other.elements
        override fun hashCode(): Int = elements.hashCode()
        override fun toString(): String = "Sequence($elements)"
    }

    class Record(fields: Map<String, ConstantValue>) : ConstantValue() {
        val fields: Map<String, ConstantValue> = immutableMap(fields.toSortedMap())
        override fun equals(other: Any?): Boolean = other is Record && fields == other.fields
        override fun hashCode(): Int = fields.hashCode()
        override fun toString(): String = "Record($fields)"
    }

    companion object {
        @JvmStatic fun integral(value: Long): ConstantValue = Integral(value)
        @JvmStatic fun bool(value: Boolean): ConstantValue = Bool(value)
        @JvmStatic fun text(value: String): ConstantValue = Text(value)
        @JvmStatic fun sequence(elements: List<ConstantValue>): ConstantValue = Sequence(elements)
        @JvmStatic fun record(fields: Map<String, ConstantValue>): ConstantValue = Record(fields)
    }
}

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
