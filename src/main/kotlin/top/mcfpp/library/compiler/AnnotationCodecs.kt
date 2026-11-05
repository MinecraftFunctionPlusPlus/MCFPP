package top.mcfpp.library.compiler

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.library.format.LibraryBinaryWriter
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.AnnotationId
import top.mcfpp.library.syntax.ConstantRef
import top.mcfpp.library.syntax.DeclarationReference
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.library.syntax.TypeRef
import top.mcfpp.mni.annotation.*
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import java.util.Collections
import java.util.IdentityHashMap

data class PortableAnnotationCodecKey(val providerPackage: PackageKey?, val ownerClass: String, val schema: String, val version: Long)

/** A provider explicitly declares its portable fields. No annotation fields are inspected by reflection. */
interface PortableAnnotationCodec<A : Annotation> {
    val annotationClass: Class<A>
    val schema: String
    val version: Long
    val constructorParameters: List<String> get() = emptyList()
    fun capture(value: A, writer: PortableAnnotationWriter): List<StateField>
    /** Allocate a fresh instance without running annotation actions or source constructor side effects. */
    fun allocateShell(reader: PortableAnnotationReader): A
    /** All annotations and shared cells are allocated and filled before this callback. */
    fun restore(value: A, reader: PortableAnnotationReader)
}

interface AnnotationCaptureBindings {
    fun provider(binding: AnnotationProviderBinding): DeclarationReference
    fun constant(value: CompilerValue): ConstantRef
    fun type(value: MCFPPType): TypeRef
    fun declaration(value: Any): DeclarationReference
    fun phase(value: Annotation): AnnotationPhase
}

interface AnnotationRestoreBindings {
    fun provider(reference: DeclarationReference): AnnotationProviderBinding
    fun constant(reference: ConstantRef): CompilerValue
    fun type(reference: TypeRef): MCFPPType
    fun declaration(reference: DeclarationReference): Any
}

data class CapturedAnnotations(val annotations: List<AnnotationRecord>, val stateCells: List<StateCell>, val roots: List<AnnotationId>)

class RestoredAnnotations internal constructor(
    private val values: List<Annotation?>, private val phases: List<AnnotationPhase>
) {
    internal var resolveMissing: ((Int) -> Annotation)? = null
    internal var onLoaded: ((AnnotationId, Annotation) -> Unit)? = null
    internal var checkpointState: (() -> (() -> Unit))? = null
    fun annotation(reference: AnnotationId): Annotation {
        val index = arenaIndex(reference.value, values.size, "annotation")
        return values[index] ?: requireNotNull(resolveMissing) { "Annotation is not allocated" }(index)
    }
    fun phase(reference: AnnotationId): AnnotationPhase = phases[arenaIndex(reference.value, phases.size, "annotation")]
    fun phase(value: Annotation): AnnotationPhase = phases[values.indexOfFirst { it === value }.also {
        require(it >= 0) { "Annotation is not owned by this restored arena" }
    }]
    internal fun checkpointForReuse(): () -> Unit = checkpointState?.invoke() ?: {}
    internal fun loadedCount(): Int = values.count { it != null }
}

class PortableAnnotationException(message: String) : IllegalArgumentException("LIB_ANNOTATION_UNSUPPORTED: $message") {
    val code: String get() = "LIB_ANNOTATION_UNSUPPORTED"
}

/** Cell identity is retained for lists/maps/sets, including nested aliases and finite cycles. */
class PortableAnnotationWriter internal constructor(private val arena: AnnotationCaptureArena) {
    fun value(value: Any?): PortableValue = arena.value(value)
    fun constant(value: CompilerValue): PortableValue.Constant = PortableValue.Constant(arena.bindings.constant(value))
    fun type(value: MCFPPType): PortableValue.Type = PortableValue.Type(arena.bindings.type(value))
    fun declaration(value: Any): PortableValue.Declaration = PortableValue.Declaration(arena.bindings.declaration(value))
    fun annotation(value: Annotation): PortableValue.Annotation = PortableValue.Annotation(arena.annotation(value))
    fun sequence(value: List<*>): PortableValue.Cell = PortableValue.Cell(arena.cell(value))
    fun map(value: Map<*, *>): PortableValue.Cell = PortableValue.Cell(arena.cell(value))
    fun set(value: Set<*>): PortableValue.Cell = PortableValue.Cell(arena.cell(value))
    fun field(name: String, value: Any?): StateField = StateField(name, this.value(value))
    fun fields(vararg fields: Pair<String, Any?>): List<StateField> = fields.map { field(it.first, it.second) }
}

class PortableAnnotationReader internal constructor(
    private val arena: AnnotationRestoreArena, private val record: AnnotationRecord
) {
    private val fields = requireNotNull(record.state) { "Portable annotation state is missing" }.fields.associateBy { it.name }
    val identifier: String get() = text(BASE_IDENTIFIER)
    val namespace: String get() = text(BASE_NAMESPACE)
    val phase: AnnotationPhase get() = record.phase
    val arguments: List<CompilerValue> get() = record.arguments.map(arena.bindings::constant)
    fun field(name: String): PortableValue = fields[name]?.value ?: unsupported("Missing portable annotation field '$name'")
    fun hasField(name: String): Boolean = name in fields
    fun value(name: String): Any? = arena.value(field(name))
    fun compilerValue(name: String): CompilerValue = arena.bindings.constant(
        (field(name) as? PortableValue.Constant)?.value ?: unsupported("Field '$name' is not a constant"))
    fun text(name: String): String = value(name) as? String ?: unsupported("Field '$name' is not text")
    fun nullableText(name: String): String? = value(name).let { if (it == null || it is String) it as String? else unsupported("Field '$name' is not nullable text") }
    fun integer(name: String): Int = integral(name).let { require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()); it.toInt() }
    fun long(name: String): Long = integral(name)
    fun boolean(name: String): Boolean = value(name) as? Boolean ?: unsupported("Field '$name' is not boolean")
    private fun integral(name: String): Long = when (val value = value(name)) {
        is Byte -> value.toLong(); is Short -> value.toLong(); is Int -> value.toLong(); is Long -> value
        else -> unsupported("Field '$name' is not integral")
    }
    @Suppress("UNCHECKED_CAST") fun list(name: String): MutableList<Any?> = value(name) as? MutableList<Any?> ?: unsupported("Field '$name' is not a sequence")
    @Suppress("UNCHECKED_CAST") fun map(name: String): MutableMap<Any?, Any?> = value(name) as? MutableMap<Any?, Any?> ?: unsupported("Field '$name' is not a map")
    @Suppress("UNCHECKED_CAST") fun set(name: String): MutableSet<Any?> = value(name) as? MutableSet<Any?> ?: unsupported("Field '$name' is not a set")
}

internal class AnnotationCodecEntry(
    val key: PortableAnnotationCodecKey, val codec: PortableAnnotationCodec<Annotation>, val managed: ManagedNativeBindings,
    val binding: AnnotationProviderBinding
)

/** Versioned provider registry. Registration validates the managed class/API identity and never invokes a constructor. */
class PortableAnnotationCodecs : AutoCloseable {
    private val entries = linkedMapOf<PortableAnnotationCodecKey, AnnotationCodecEntry>()
    private val writers = IdentityHashMap<Class<*>, AnnotationCodecEntry>()
    private var closed = false

    fun <A : Annotation> register(codec: PortableAnnotationCodec<A>, providerPackage: PackageKey?,
                                  managed: ManagedNativeBindings, forCapture: Boolean = true): PortableAnnotationCodecs {
        checkOpen()
        require(codec.schema.isNotEmpty() && codec.version in 0..0xffff_ffffL)
        val key = PortableAnnotationCodecKey(providerPackage, codec.annotationClass.name, codec.schema, codec.version)
        val binding = AnnotationProviderBinding(providerPackage, codec.annotationClass.name, codec.constructorParameters, codec.schema, codec.version)
        require(NativeDeclarations.restoreAnnotationProvider(binding, managed) === codec.annotationClass) { "Codec provider has a different managed class identity" }
        require(key !in entries) { "Duplicate portable annotation codec $key" }
        if (forCapture) require(codec.annotationClass !in writers) { "Select exactly one capture codec for ${codec.annotationClass.name}" }
        @Suppress("UNCHECKED_CAST") val entry = AnnotationCodecEntry(key, codec as PortableAnnotationCodec<Annotation>, managed,
            binding.copy(constructorParameters = immutable(binding.constructorParameters)))
        entries[key] = entry
        if (forCapture) writers[codec.annotationClass] = entry
        return this
    }

    fun bindings(): List<AnnotationProviderBinding> { checkOpen(); return entries.values.map { it.binding } }
    fun captureBinding(type: Class<out Annotation>): AnnotationProviderBinding {
        checkOpen()
        return writers[type]?.binding ?: unsupported("No portable capture codec for provider ${type.name}")
    }
    fun capture(roots: List<Annotation>, bindings: AnnotationCaptureBindings): CapturedAnnotations {
        checkOpen()
        return AnnotationCaptureArena(this, bindings).capture(roots)
    }
    fun restore(annotations: List<AnnotationRecord>, stateCells: List<StateCell>, bindings: AnnotationRestoreBindings): RestoredAnnotations {
        checkOpen()
        return AnnotationRestoreArena(this, annotations, stateCells, bindings).restore()
    }
    /** Closed built-ins can restore a reachable annotation group only when its member is used. */
    internal fun restoreLazily(annotations: List<AnnotationRecord>, stateCells: List<StateCell>,
                               bindings: AnnotationRestoreBindings, hints: Map<Int, AnnotationCellHint>): RestoredAnnotations {
        checkOpen()
        return AnnotationRestoreArena(this, annotations, stateCells, bindings).restoreLazily(hints)
    }
    internal fun writer(value: Annotation): AnnotationCodecEntry {
        checkOpen()
        return writers[value.javaClass] ?: unsupported("No portable codec for provider ${value.javaClass.name}")
    }
    internal fun reader(binding: AnnotationProviderBinding): AnnotationCodecEntry {
        checkOpen()
        val key = PortableAnnotationCodecKey(binding.providerPackage, binding.ownerClass, binding.schema, binding.schemaVersion)
        val entry = entries[key] ?: unsupported("No portable codec registered for $key")
        require(binding.constructorParameters == entry.binding.constructorParameters) { "Annotation provider constructor descriptor changed" }
        require(NativeDeclarations.restoreAnnotationProvider(binding, entry.managed) === entry.codec.annotationClass)
        return entry
    }
    private fun checkOpen() = check(!closed) { "Portable annotation registry is closed" }
    override fun close() { if (!closed) { closed = true; entries.clear(); writers.clear() } }

    companion object {
        /** Class literals are also consumed by the AOT catalog generator, without registering codecs. */
        @JvmStatic
        fun builtinClasses(): List<Class<out Annotation>> = listOf(
            ConcreteOnly::class.java, DataOnly::class.java, Dynamic::class.java, Load::class.java,
            Tick::class.java, MCFPPEntity::class.java, NoInstance::class.java, From::class.java,
            To::class.java, Name::class.java,
        )

        /** Method annotations WritesReceiver/NoExternalWrites are deliberately absent from the instance registry. */
        fun builtins(): PortableAnnotationCodecs {
            val managed = object : ManagedNativeBindings {
                override fun loader(provider: PackageKey?): ClassLoader { require(provider == null); return Annotation::class.java.classLoader }
                override fun provider(owner: Class<*>): PackageKey? = null
            }
            val registry = PortableAnnotationCodecs()
            fun <A : Annotation> stateless(type: Class<A>, factory: () -> A) {
                registry.register(BuiltinAnnotationCodec(type, factory = { factory() }), null, managed)
            }
            stateless(ConcreteOnly::class.java) { ConcreteOnly.restorePortable() }
            stateless(DataOnly::class.java) { DataOnly() }
            stateless(Dynamic::class.java) { Dynamic() }
            stateless(Load::class.java) { Load() }
            stateless(Tick::class.java) { Tick() }
            stateless(MCFPPEntity::class.java) { MCFPPEntity.restorePortable() }
            stateless(NoInstance::class.java) { NoInstance.restorePortable() }
            registry.register(BuiltinAnnotationCodec(From::class.java, listOf("java.lang.String"),
                { value, writer -> writer.fields("className" to value.className) }, { From("") },
                { value, reader -> value.restorePortableState(reader.text("className")) }), null, managed)
            registry.register(BuiltinAnnotationCodec(To::class.java, listOf("java.lang.String"),
                { value, writer -> writer.fields("className" to value.className) }, { To("") },
                { value, reader -> value.restorePortableState(reader.text("className")) }), null, managed)
            registry.register(BuiltinAnnotationCodec(Name::class.java, listOf("java.lang.String", "java.lang.String"),
                { value, writer -> writer.fields("name" to value.name, "version" to value.version) }, { Name("") },
                { value, reader -> value.restorePortableState(reader.text("name"), reader.nullableText("version")) }), null, managed)
            return registry
        }
    }
}

private class BuiltinAnnotationCodec<A : Annotation>(
    override val annotationClass: Class<A>, override val constructorParameters: List<String> = emptyList(),
    private val captureState: (A, PortableAnnotationWriter) -> List<StateField> = { _, _ -> emptyList() },
    private val factory: (PortableAnnotationReader) -> A,
    private val restoreState: (A, PortableAnnotationReader) -> Unit = { _, _ -> }
) : PortableAnnotationCodec<A> {
    override val schema: String = "mcfpp.annotation.${annotationClass.simpleName}"
    override val version: Long = 1
    override fun capture(value: A, writer: PortableAnnotationWriter) = captureState(value, writer)
    override fun allocateShell(reader: PortableAnnotationReader) = factory(reader)
    override fun restore(value: A, reader: PortableAnnotationReader) = restoreState(value, reader)
}

private const val RESERVED = "@mcfpp."
private const val BASE_IDENTIFIER = "${RESERVED}identifier"
private const val BASE_NAMESPACE = "${RESERVED}namespace"
private const val BASE_PARAMS = "${RESERVED}params"
private const val BASE_PARENTS = "${RESERVED}parents"
private const val CELL_HINTS = "${RESERVED}cell-hints"
internal data class AnnotationCellHint(val keys: List<String>, val values: List<String>)

internal class AnnotationCaptureArena(private val registry: PortableAnnotationCodecs, val bindings: AnnotationCaptureBindings) {
    private val annotationIds = IdentityHashMap<Annotation, AnnotationId>()
    private val cellIds = IdentityHashMap<Any, StateCellRef>()
    private val annotations = arrayListOf<AnnotationRecord?>()
    private val cells = arrayListOf<StateCell?>()
    private val hints = linkedMapOf<StateCellRef, AnnotationCellHint>()
    private val writer = PortableAnnotationWriter(this)

    fun capture(roots: List<Annotation>): CapturedAnnotations {
        val references = roots.map(::annotation)
        if (annotations.isEmpty()) return CapturedAnnotations(emptyList(), emptyList(), emptyList())
        val entries = hints.map { (reference, hint) ->
            val keys = append(StateCell.Sequence(hint.keys.map { PortableValue.Constant(bindings.constant(CompilerValue.Text(it))) }))
            val values = append(StateCell.Sequence(hint.values.map { PortableValue.Constant(bindings.constant(CompilerValue.Text(it))) }))
            PortableValue.Cell(append(StateCell.Sequence(listOf(PortableValue.Cell(reference), PortableValue.Cell(keys), PortableValue.Cell(values)))))
        }
        val metadata = append(StateCell.Sequence(entries))
        val records = annotations.map { requireNotNull(it).let { record -> record.copy(state = requireNotNull(record.state).let {
            it.copy(fields = immutable((it.fields + StateField(CELL_HINTS, PortableValue.Cell(metadata))).sortedWith { a, b -> compareUtf8(a.name, b.name) }))
        }) } }
        validateParents(records)
        return CapturedAnnotations(immutable(records), immutable(cells.map { requireNotNull(it) }), immutable(references))
    }

    fun annotation(value: Annotation): AnnotationId {
        annotationIds[value]?.let { return it }
        val entry = registry.writer(value)
        val reference = AnnotationId(annotations.size + 1L)
        annotationIds[value] = reference; annotations += null
        val parents = value.parents.toList().map(::annotation)
        val common = listOf(writer.field(BASE_IDENTIFIER, value.identifier), writer.field(BASE_NAMESPACE, value.namespace),
            StateField(BASE_PARAMS, PortableValue.Cell(cell(value.params))), StateField(BASE_PARENTS, PortableValue.Cell(cell(value.parents))))
        val fields = entry.codec.capture(value, writer)
        require(fields.none { it.name.startsWith(RESERVED) } && fields.map { it.name }.distinct().size == fields.size) { "Codec fields must be unique and cannot use $RESERVED" }
        annotations[(reference.value - 1).toInt()] = AnnotationRecord(bindings.provider(entry.binding), immutable(value.compilerArguments().map(bindings::constant)),
            immutable(parents), bindings.phase(value), PortableState(entry.key.schema, entry.key.version,
                immutable((common + fields).sortedWith { a, b -> compareUtf8(a.name, b.name) })))
        return reference
    }

    fun value(value: Any?): PortableValue = when (value) {
        is PortableValue -> value
        is Annotation -> PortableValue.Annotation(annotation(value))
        is MCFPPType -> PortableValue.Type(bindings.type(value))
        is List<*>, is Map<*, *>, is Set<*> -> PortableValue.Cell(cell(requireNotNull(value)))
        else -> PortableValue.Constant(bindings.constant(scalar(value)))
    }

    fun cell(value: Any): StateCellRef {
        cellIds[value]?.let { return it }
        val reference = StateCellRef(cells.size + 1L)
        cellIds[value] = reference; cells += null
        val cell = when (value) {
            is List<*> -> {
                val source = value.toList()
                hints[reference] = AnnotationCellHint(emptyList(), source.map(::valueKind))
                StateCell.Sequence(immutable(source.map { this.value(it) }))
            }
            is Map<*, *> -> {
                val entries = value.entries.map { entry -> Key(entry.key, primitive(entry.key), entry.value) }.sortedWith { a, b -> compareBytes(a.bytes, b.bytes) }
                uniqueKeys(entries)
                hints[reference] = AnnotationCellHint(entries.map { keyKind(it.host) }, entries.map { valueKind(it.value) })
                StateCell.Map(immutable(entries.map { StateMapEntry(bindings.constant(it.constant), this.value(it.value)) }))
            }
            is Set<*> -> {
                val entries = value.map { Key(it, primitive(it), null) }.sortedWith { a, b -> compareBytes(a.bytes, b.bytes) }
                uniqueKeys(entries)
                hints[reference] = AnnotationCellHint(entries.map { keyKind(it.host) }, emptyList())
                StateCell.Set(immutable(entries.map { bindings.constant(it.constant) }))
            }
            else -> unsupported("Only explicitly declared list/map/set state can allocate a cell")
        }
        cells[(reference.value - 1).toInt()] = cell
        return reference
    }
    private fun append(value: StateCell): StateCellRef = StateCellRef(cells.size + 1L).also { cells += when (value) {
        is StateCell.Sequence -> value.copy(values = immutable(value.values))
        is StateCell.Map -> value.copy(entries = immutable(value.entries))
        is StateCell.Set -> value.copy(values = immutable(value.values))
    } }
    private data class Key(val host: Any?, val constant: CompilerValue, val value: Any?) { val bytes = primitiveBytes(constant) }
    private fun uniqueKeys(values: List<Key>) = require(values.zipWithNext().all { (a, b) -> compareBytes(a.bytes, b.bytes) < 0 }) {
        "Distinct host keys collapse to the same portable primitive; encode a provider-specific key representation"
    }
}

internal class AnnotationRestoreArena(
    private val registry: PortableAnnotationCodecs, private val records: List<AnnotationRecord>, private val states: List<StateCell>,
    val bindings: AnnotationRestoreBindings
) {
    private val annotations = arrayOfNulls<Annotation>(records.size)
    private val cells = arrayOfNulls<Any>(states.size)
    private val hints = arrayOfNulls<AnnotationCellHint>(states.size)
    private val pendingCells = ArrayDeque<Int>()
    private var fillingCells = false
    private var filled = false

    /**
     * Each requested group closes over A/H references before allocating shells. Shared cells
     * stay in one arena, so aliases and finite cycles retain their identities across groups.
     * Hint decoding was performed by the build-time plan, rather than by every browser import.
     */
    internal fun restoreLazily(preparedHints: Map<Int, AnnotationCellHint>): RestoredAnnotations {
        validateParents(records)
        preparedHints.forEach { (index, hint) -> hints[index] = hint }
        val completed = BooleanArray(records.size)
        val allocated = Collections.newSetFromMap(IdentityHashMap<Annotation, Boolean>())
        var restoring = false
        val result = RestoredAnnotations(annotations.asList(), records.map { it.phase })
        result.resolveMissing = { requested ->
            check(!restoring) { "A built-in provider requested an annotation outside its prepared dependency group" }
            restoring = true
            try {
                val selected = sortedSetOf<Int>()
                val pendingAnnotations = ArrayDeque<Int>()
                val pendingValues = ArrayDeque<PortableValue>()
                val visitedCells = hashSetOf<Int>()
                fun include(index: Int) {
                    if (!completed[index] && selected.add(index)) pendingAnnotations.add(index)
                }
                include(requested)
                while (pendingAnnotations.isNotEmpty() || pendingValues.isNotEmpty()) {
                    while (pendingAnnotations.isNotEmpty()) {
                        val record = records[pendingAnnotations.removeFirst()]
                        record.parents.forEach { include(arenaIndex(it.value, records.size, "annotation")) }
                        requireNotNull(record.state).fields.filter { it.name != CELL_HINTS }.forEach { pendingValues.add(it.value) }
                    }
                    while (pendingValues.isNotEmpty()) when (val value = pendingValues.removeFirst()) {
                        is PortableValue.Annotation -> include(arenaIndex(value.value.value, records.size, "annotation"))
                        is PortableValue.Cell -> {
                            val index = arenaIndex(value.value.value, states.size, "cell")
                            if (visitedCells.add(index)) when (val cell = states[index]) {
                                is StateCell.Sequence -> pendingValues.addAll(cell.values)
                                is StateCell.Map -> pendingValues.addAll(cell.entries.map { it.value })
                                is StateCell.Set -> Unit
                            }
                        }
                        else -> Unit
                    }
                }
                filled = false
                val entries = selected.associateWith { index -> registry.reader(bindings.provider(records[index].provider)).also { entry ->
                    val state = requireNotNull(records[index].state)
                    require(state.schema == entry.key.schema && state.version == entry.key.version)
                } }
                val readers = selected.associateWith { PortableAnnotationReader(this, records[it]) }
                selected.forEach { index ->
                    val entry = entries.getValue(index)
                    val reader = readers.getValue(index)
                    val shell = entry.codec.allocateShell(reader)
                    require(shell.javaClass === entry.codec.annotationClass && allocated.add(shell))
                    require(shell.identifier == reader.identifier && shell.namespace == reader.namespace)
                    annotations[index] = shell
                }
                selected.forEach { index ->
                    val reader = readers.getValue(index)
                    @Suppress("UNCHECKED_CAST") val parameters = rawCell(reader.field(BASE_PARAMS)) as ArrayList<String>
                    @Suppress("UNCHECKED_CAST") val parents = rawCell(reader.field(BASE_PARENTS)) as ArrayList<Annotation>
                    annotations[index]!!.restorePortableContainers(parameters, parents)
                    records[index].state!!.fields.forEach { field ->
                        if (field.name != CELL_HINTS && field.value is PortableValue.Cell) rawCell(field.value)
                    }
                }
                fillPendingCells()
                selected.forEach { index ->
                    val value = annotations[index]!!
                    require((value.params as List<*>).all { it is String })
                    require(value.parents.size == records[index].parents.size && value.parents.indices.all { position ->
                        value.parents[position] === annotations[arenaIndex(records[index].parents[position].value, records.size, "annotation")]
                    })
                    value.recordCompilerArguments(records[index].arguments.map(bindings::constant))
                }
                filled = true
                selected.forEach { index ->
                    entries.getValue(index).codec.restore(annotations[index]!!, readers.getValue(index))
                    completed[index] = true
                    result.onLoaded?.invoke(AnnotationId(index + 1L), annotations[index]!!)
                }
                requireNotNull(annotations[requested])
            } finally { restoring = false }
        }
        result.checkpointState = {
            check(!restoring && pendingCells.isEmpty())
            val savedAnnotations = annotations.copyOf()
            val savedCells = cells.copyOf()
            val savedCompleted = completed.copyOf()
            val savedFilled = filled
            val restore: () -> Unit = {
                check(!restoring)
                savedAnnotations.copyInto(annotations); savedCells.copyInto(cells); savedCompleted.copyInto(completed)
                allocated.clear(); savedAnnotations.filterNotNull().forEach(allocated::add)
                pendingCells.clear(); fillingCells = false; filled = savedFilled
            }
            restore
        }
        return result
    }

    fun restore(): RestoredAnnotations {
        validateParents(records)
        val entries = records.map { record -> registry.reader(bindings.provider(record.provider)).also { entry ->
            val state = record.state ?: unsupported("Portable state is missing for ${entry.key}")
            require(state.schema == entry.key.schema && state.version == entry.key.version)
            require(state.fields.zipWithNext().all { (a, b) -> compareUtf8(a.name, b.name) < 0 }) { "Portable fields must be sorted and unique" }
        } }
        val readers = records.map { PortableAnnotationReader(this, it) }
        val distinct = Collections.newSetFromMap(IdentityHashMap<Annotation, Boolean>())
        entries.forEachIndexed { index, entry ->
            val shell = entry.codec.allocateShell(readers[index])
            require(shell.javaClass === entry.codec.annotationClass && distinct.add(shell)) { "Codec must allocate a distinct shell of its registered class" }
            require(shell.identifier == readers[index].identifier && shell.namespace == readers[index].namespace) { "Codec shell has a different language identity" }
            annotations[index] = shell
        }
        readHints()
        records.forEachIndexed { index, record ->
            val reader = readers[index]
            @Suppress("UNCHECKED_CAST") val parameters = rawCell(reader.field(BASE_PARAMS)) as? ArrayList<String>
                ?: unsupported("Base params must be a sequence cell")
            @Suppress("UNCHECKED_CAST") val parents = rawCell(reader.field(BASE_PARENTS)) as? ArrayList<Annotation>
                ?: unsupported("Base parents must be a sequence cell")
            annotations[index]!!.restorePortableContainers(parameters, parents)
        }
        // CELL_HINTS is encoding metadata, already read directly from the protocol table. It can
        // dwarf the actual state (three extra cells per hinted container). Only restore cells
        // reachable from annotation fields; otherwise every import builds thousands of unused
        // lists. Allocate each shell before following its contents so aliases and cycles survive.
        records.forEach { record -> requireNotNull(record.state).fields.forEach { field ->
            if (field.name != CELL_HINTS && field.value is PortableValue.Cell) rawCell(field.value)
        } }
        fillPendingCells()
        records.forEachIndexed { index, record ->
            val annotation = annotations[index]!!
            require((annotation.params as List<*>).all { it is String }) { "Base params contain a non-string value" }
            require(annotation.parents.size == record.parents.size && annotation.parents.indices.all { position ->
                annotation.parents[position] === annotations[arenaIndex(record.parents[position].value, records.size, "annotation")]
            }) { "Base parent cell differs from the A parent graph" }
            annotation.recordCompilerArguments(record.arguments.map(bindings::constant))
        }
        filled = true
        entries.forEachIndexed { index, entry -> entry.codec.restore(annotations[index]!!, readers[index]) }
        return RestoredAnnotations(immutable(annotations.map { requireNotNull(it) }), immutable(records.map { it.phase }))
    }

    /** Drain iteratively: a provider's state may contain shared containers and finite cycles. */
    private fun fillPendingCells() {
        fillingCells = true
        try {
            while (pendingCells.isNotEmpty()) {
                val index = pendingCells.removeFirst()
                val state = states[index]
                val hint = hints[index]
                when (state) {
                    is StateCell.Sequence -> {
                        require(hint == null || hint.keys.isEmpty() && hint.values.size == state.values.size)
                        @Suppress("UNCHECKED_CAST") val target = cells[index] as ArrayList<Any?>
                        state.values.forEachIndexed { position, item -> target.add(decode(item, hint?.values?.get(position) ?: "native")) }
                    }
                    is StateCell.Map -> {
                        require(hint == null || hint.keys.size == state.entries.size && hint.values.size == state.entries.size)
                        @Suppress("UNCHECKED_CAST") val target = cells[index] as MutableMap<Any?, Any?>
                        state.entries.forEachIndexed { position, item ->
                            target[key(bindings.constant(item.key), hint?.keys?.get(position))] = decode(item.value, hint?.values?.get(position) ?: "native")
                        }
                        require(target.size == state.entries.size) { "Portable keys collide in the provider's host key domain" }
                    }
                    is StateCell.Set -> {
                        require(hint == null || hint.keys.size == state.values.size && hint.values.isEmpty())
                        @Suppress("UNCHECKED_CAST") val target = cells[index] as MutableSet<Any?>
                        state.values.forEachIndexed { position, item -> target.add(key(bindings.constant(item), hint?.keys?.get(position))) }
                        require(target.size == state.values.size) { "Portable set values collide in the host key domain" }
                    }
                }
            }
        } finally {
            fillingCells = false
        }
    }

    fun value(value: PortableValue): Any? {
        if (value is PortableValue.Cell) check(filled) { "Shared cells are unavailable during shell allocation" }
        return decode(value, "native")
    }
    private fun decode(value: PortableValue, kind: String): Any? = when (value) {
        is PortableValue.Constant -> bindings.constant(value.value).let { when (kind) {
            "fact" -> it; "char" -> (host(it) as String).single(); "native" -> host(it)
            else -> unsupported("Unknown scalar cell hint '$kind'")
        } }
        is PortableValue.Declaration -> bindings.declaration(value.value)
        is PortableValue.Type -> bindings.type(value.value)
        is PortableValue.Annotation -> annotations[arenaIndex(value.value.value, annotations.size, "annotation")]
            ?: unsupported("Annotation shell is not allocated yet")
        is PortableValue.Cell -> rawCell(value)
    }
    private fun rawCell(value: PortableValue): Any {
        val index = arenaIndex((value as? PortableValue.Cell)?.value?.value
            ?: unsupported("Expected a state cell reference"), cells.size, "cell")
        val cell = cells[index] ?: when (states[index]) {
            is StateCell.Sequence -> arrayListOf<Any?>()
            is StateCell.Map -> linkedMapOf<Any?, Any?>()
            is StateCell.Set -> linkedSetOf<Any?>()
        }.also { cells[index] = it; pendingCells.addLast(index) }
        // Custom codecs may explicitly read reserved metadata too. Materialize it on demand,
        // preserving the reader API without paying that allocation cost for ordinary providers.
        if (filled && !fillingCells) fillPendingCells()
        return cell
    }

    private fun readHints() {
        val read = BooleanArray(states.size)
        fun sequence(value: PortableValue): List<PortableValue> = (states[arenaIndex((value as? PortableValue.Cell)?.value?.value
            ?: unsupported("Malformed cell hint"), states.size, "cell")] as? StateCell.Sequence)?.values ?: unsupported("Hint is not a sequence")
        fun strings(value: PortableValue): List<String> = sequence(value).map {
            val reference = (it as? PortableValue.Constant)?.value ?: unsupported("Hint is not text")
            (bindings.constant(reference) as? CompilerValue.Text)?.value ?: unsupported("Hint is not text")
        }
        records.forEach { record ->
            val metadata = requireNotNull(record.state).fields.singleOrNull { it.name == CELL_HINTS }?.value
                ?: unsupported("Shared cell hints are missing")
            val reference = (metadata as? PortableValue.Cell)?.value ?: unsupported("Malformed shared cell hints")
            val metadataIndex = arenaIndex(reference.value, states.size, "cell")
            if (read[metadataIndex]) return@forEach
            read[metadataIndex] = true
            sequence(metadata).forEach { entry ->
                val values = sequence(entry); require(values.size == 3)
                val target = (values[0] as? PortableValue.Cell)?.value ?: unsupported("Hint target is not a cell")
                val targetIndex = arenaIndex(target.value, states.size, "cell")
                val hint = AnnotationCellHint(strings(values[1]), strings(values[2]))
                require(hint.keys.all { it in setOf("null", "byte", "short", "int", "long", "float", "double", "char", "string", "boolean") } &&
                    hint.values.all { it == "native" || it == "fact" || it == "char" }) { "Unknown shared cell hint" }
                require(hints[targetIndex]?.let { it == hint } != false) { "Conflicting cell hints" }
                hints[targetIndex] = hint
            }
        }
    }
}

private fun scalar(value: Any?): CompilerValue = when (value) {
    null -> CompilerValue.NullValue
    is CompilerValue -> value
    is String -> CompilerValue.Text(value)
    is Char -> CompilerValue.Text(value.toString())
    is Boolean -> CompilerValue.Bool(value)
    is Byte -> CompilerValue.Typed(MCFPPNBTType.Byte.typeId, CompilerValue.Integral(value.toLong()))
    is Short -> CompilerValue.Typed(MCFPPNBTType.Short.typeId, CompilerValue.Integral(value.toLong()))
    is Int -> CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(value.toLong()))
    is Long -> CompilerValue.Typed(MCFPPNBTType.Long.typeId, CompilerValue.Integral(value))
    is Float -> CompilerValue.FloatBits(value.toRawBits())
    is Double -> CompilerValue.DoubleBits(value.toRawBits())
    else -> unsupported("Codec declared an unsupported host value ${value.javaClass.name}")
}
private fun primitive(value: Any?): CompilerValue = when (value) {
    is Byte -> CompilerValue.Integral(value.toLong()); is Short -> CompilerValue.Integral(value.toLong())
    is Int -> CompilerValue.Integral(value.toLong()); is Long -> CompilerValue.Integral(value)
    else -> scalar(value).also { require(it is CompilerValue.Text || it is CompilerValue.Bool || it is CompilerValue.FloatBits ||
        it is CompilerValue.DoubleBits || it === CompilerValue.NullValue) { "Map/set keys must be primitive/text/null" } }
}
private fun valueKind(value: Any?): String = when (value) { is CompilerValue -> "fact"; is Char -> "char"; else -> "native" }
private fun keyKind(value: Any?): String = when (value) {
    null -> "null"; is Byte -> "byte"; is Short -> "short"; is Int -> "int"; is Long -> "long"
    is Float -> "float"; is Double -> "double"; is Char -> "char"; is String -> "string"; is Boolean -> "boolean"
    else -> unsupported("Map/set key has no portable primitive representation")
}
private fun key(value: CompilerValue, kind: String?): Any? {
    val raw = host(value)
    return when (kind) {
        null -> raw
        "null" -> { require(raw == null); null }
        "string" -> raw as String; "char" -> (raw as String).single(); "boolean" -> raw as Boolean
        "float" -> raw as Float; "double" -> raw as Double
        "long" -> (raw as Number).toLong()
        "int" -> (raw as Number).toLong().let { require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()); it.toInt() }
        "short" -> (raw as Number).toLong().let { require(it in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()); it.toShort() }
        "byte" -> (raw as Number).toLong().let { require(it in Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong()); it.toByte() }
        else -> unsupported("Unknown map/set key hint '$kind'")
    }
}
private fun host(value: CompilerValue): Any? = when (value) {
    CompilerValue.NullValue -> null
    is CompilerValue.Text -> value.value
    is CompilerValue.Bool -> value.value
    is CompilerValue.Integral -> value.value
    is CompilerValue.FloatBits -> Float.fromBits(value.bits)
    is CompilerValue.DoubleBits -> Double.fromBits(value.bits)
    is CompilerValue.Typed -> {
        val payload = value.payload
        when ((value.type as? top.mcfpp.type.TypeId.Builtin)?.let(LibraryBuiltinTypes::protocolKey)) {
            "byte" -> (payload as CompilerValue.Integral).value.let { require(it in Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong()); it.toByte() }
            "short" -> (payload as CompilerValue.Integral).value.let { require(it in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()); it.toShort() }
            "int" -> (payload as CompilerValue.Integral).value.let { require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()); it.toInt() }
            "long" -> (payload as CompilerValue.Integral).value
            else -> host(payload)
        }
    }
    is CompilerValue.Sequence -> value.elements.map(::host)
    is CompilerValue.Record -> value.fields.mapValues { host(it.value) }
    is CompilerValue.Nbt -> value.snbt
    is CompilerValue.TypeValue -> unsupported("Use compilerValue() to restore a compiler type-value fact")
}
private fun primitiveBytes(value: CompilerValue): ByteArray = LibraryBinaryWriter().apply { when (value) {
    CompilerValue.NullValue -> u8(1)
    is CompilerValue.Integral -> { u8(2); i64(value.value) }
    is CompilerValue.FloatBits -> { u8(3); u32(value.bits.toLong() and 0xffff_ffffL) }
    is CompilerValue.DoubleBits -> { u8(4); u64(value.bits.toULong()) }
    is CompilerValue.Bool -> { u8(5); bool(value.value) }
    is CompilerValue.Text -> { u8(6); string(value.value) }
    else -> unsupported("Map/set key is not primitive")
} }.toByteArray()
private fun validateParents(records: List<AnnotationRecord>) {
    records.forEach { record -> record.parents.forEach { arenaIndex(it.value, records.size, "annotation") } }
    // Completed parent cycles are state; Annotation.on must never traverse a reachable cycle.
    val state = IntArray(records.size)
    fun visit(position: Int) {
        require(state[position] != 1) { "Pending annotation parent graph contains a cycle" }
        if (state[position] == 2) return
        state[position] = 1
        records[position].parents.forEach { visit(arenaIndex(it.value, records.size, "annotation")) }
        state[position] = 2
    }
    records.indices.filter { records[it].phase == AnnotationPhase.PENDING }.forEach(::visit)
}
private fun arenaIndex(reference: Long, count: Int, kind: String): Int {
    require(reference in 1..count.toLong()) { "$kind reference is outside its arena" }
    return (reference - 1).toInt()
}
private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
private fun unsupported(message: String): Nothing = throw PortableAnnotationException(message)
