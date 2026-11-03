package top.mcfpp.library.format

import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import java.util.Collections

/** Explicit v1 T/C and structural ABI-key codecs. The shared S table is supplied by the caller. */
object TypeTableCodec {
    fun collectStrings(tables: LibraryTables): Set<String> = buildSet {
        tables.types.filterIsInstance<LibraryType.Builtin>().forEach { add(it.key) }
        tables.types.filterIsInstance<LibraryType.UnresolvedNominal>().forEach { add(it.kind); add(it.namespace); add(it.name) }
        tables.constants.forEach { when (it) {
            is ConstantValue.Text -> add(it.value)
            is ConstantValue.Nbt -> add(it.snbt)
            is ConstantValue.Record -> it.fields.forEach { field -> add(field.name) }
            else -> Unit
        } }
    }

    fun encodeStrings(strings: LibraryStrings): ByteArray = LibraryBinaryWriter().apply {
        list(strings.values) { record { string(it) } }
    }.toByteArray()

    fun decodeStrings(bytes: ByteArray): LibraryStrings {
        val reader = LibraryBinaryReader(bytes, sectionId = LibrarySectionId.STRINGS)
        return checked(reader) {
            val result = LibraryStrings.ordered(list { record { string() } })
            requireEnd()
            result
        }
    }

    fun encodeTypes(types: List<LibraryType>, strings: LibraryStrings): ByteArray = LibraryBinaryWriter().apply {
        list(types) { record { writeType(this, it, strings) } }
    }.toByteArray()

    fun encodeConstants(constants: List<ConstantValue>, strings: LibraryStrings): ByteArray = LibraryBinaryWriter().apply {
        list(constants) { record { writeConstant(this, it, strings) } }
    }.toByteArray()

    fun decodeTypes(bytes: ByteArray, strings: LibraryStrings): List<LibraryType> {
        val reader = LibraryBinaryReader(bytes, sectionId = LibrarySectionId.TYPES)
        return checked(reader) {
            var index = 0L
            list { record(++index) { checked(this) { readType(this, strings) } } }.also { requireEnd() }
        }
    }

    fun decodeConstants(bytes: ByteArray, strings: LibraryStrings): List<ConstantValue> {
        val reader = LibraryBinaryReader(bytes, sectionId = LibrarySectionId.CONSTANTS)
        return checked(reader) {
            var index = 0L
            list { record(++index) { checked(this) { readConstant(this, strings) } } }.also { requireEnd() }
        }
    }

    /** Cross T/C validation occurs after both tables and all forward references have been read. */
    fun decode(types: ByteArray, constants: ByteArray, strings: LibraryStrings): LibraryTables =
        LibraryTables(decodeTypes(types, strings), decodeConstants(constants, strings)).also(::validate)

    fun writePackage(writer: LibraryBinaryWriter, value: PackageKey) = with(writer) {
        string(value.coordinate); string(value.version)
    }

    fun readPackage(reader: LibraryBinaryReader): PackageKey = with(reader) { PackageKey(string(), string()) }

    fun writeAnonymousOrigin(writer: LibraryBinaryWriter, value: AnonymousOrigin) = with(writer) {
        string(value.logicalUnit); u32(value.declarationOrdinal); list(value.childPath) { u32(it) }
    }

    fun readAnonymousOrigin(reader: LibraryBinaryReader): AnonymousOrigin = checked(reader) {
        AnonymousOrigin(string(), u32(), list { u32() })
    }

    fun writeDeclarationKey(writer: LibraryBinaryWriter, value: DeclarationKey) = with(writer) {
        string(value.namespace)
        list(value.ownerPath) { part ->
            u16(part.kind); string(part.name); optional(part.signature) { writeSignature(this, it) }
        }
        u16(value.kind); string(value.name); optional(value.signature) { writeSignature(this, it) }
    }

    fun readDeclarationKey(reader: LibraryBinaryReader): DeclarationKey = checked(reader) {
        val namespace = string()
        val owners = list { DeclarationPathPart(u16(), string(), optional { readSignature(this) }) }
        DeclarationKey(namespace, owners, u16(), string(), optional { readSignature(this) })
    }

    private fun writeSignature(writer: LibraryBinaryWriter, value: DeclarationSignature) = with(writer) {
        list(value.readonly) { writeKeyType(this, it) }
        list(value.normal) { writeKeyType(this, it) }
        optional(value.receiver) { writeKeyType(this, it) }
    }

    private fun readSignature(reader: LibraryBinaryReader): DeclarationSignature = with(reader) {
        DeclarationSignature(list { readKeyType(this) }, list { readKeyType(this) }, optional { readKeyType(this) })
    }

    fun writeDeclarationReference(writer: LibraryBinaryWriter, value: DeclarationReference) = with(writer) {
        when (value) {
            is DeclarationReference.Local -> tagged(1) { u32(value.target.value) }
            is DeclarationReference.Dependency -> tagged(2) { u32(value.dependencyIndex); writeDeclarationKey(this, value.key) }
        }
    }

    fun readDeclarationReference(reader: LibraryBinaryReader): DeclarationReference = reader.tagged { tag -> checked(this) {
        when (tag) {
            1 -> DeclarationReference.Local(DeclarationId(u32()))
            2 -> DeclarationReference.Dependency(u32(), readDeclarationKey(this))
            else -> fail("Unknown DeclRef tag $tag", LibraryFormatCode.UNSUPPORTED)
        }
    } }

    fun writeKeyType(writer: LibraryBinaryWriter, value: KeyType) {
        with(writer) { when (value) {
            is KeyType.Builtin -> tagged(1) { string(value.key) }
            is KeyType.Nominal -> tagged(2) {
                writePackage(this, value.library); string(value.classifier); string(value.namespace)
                list(value.path) { string(it) }
            }
            is KeyType.Parameter -> tagged(3) { u32(value.ownerDepth); u8(value.role.wireValue); u32(value.index) }
            is KeyType.Applied -> tagged(4) {
                writeKeyType(this, value.base); list(value.arguments) { writeKeyType(this, it) }
            }
            is KeyType.Specialized -> tagged(5) {
                writeKeyType(this, value.base); list(value.arguments) { bytes(it.toByteArray()) }
            }
            is KeyType.Union -> tagged(6) {
                val ordered = value.alternatives.distinctBy { keyBytes(it).toList() }
                    .sortedWith { a, b -> compareBytes(keyBytes(a), keyBytes(b)) }
                list(ordered) { writeKeyType(this, it) }
            }
            is KeyType.Function -> tagged(7) {
                u8(value.execution.wireValue); list(value.parameters) { writeKeyType(this, it) }; writeKeyType(this, value.result)
            }
            is KeyType.Dependent -> tagged(8) { bytes(value.canonicalTree.toByteArray()) }
            is KeyType.Anonymous -> tagged(9) {
                writePackage(this, value.library); string(value.classifier); writeAnonymousOrigin(this, value.origin)
            }
        } }
    }

    fun readKeyType(reader: LibraryBinaryReader): KeyType = reader.tagged { tag -> checked(this) {
        when (tag) {
            1 -> KeyType.Builtin(string())
            2 -> KeyType.Nominal(readPackage(this), string(), string(), list { string() })
            3 -> KeyType.Parameter(u32(), parameterRole(u8()), u32())
            4 -> KeyType.Applied(readKeyType(this), list { readKeyType(this) })
            5 -> KeyType.Specialized(readKeyType(this), list { bytes().toList() })
            6 -> {
                val values = list { readKeyType(this) }
                require(values.isNotEmpty() && values.zipWithNext().all { (a, b) -> compareBytes(keyBytes(a), keyBytes(b)) < 0 }) {
                    "KeyType union must be sorted and unique"
                }
                KeyType.Union(values)
            }
            7 -> KeyType.Function(execution(u8()), list { readKeyType(this) }, readKeyType(this))
            8 -> KeyType.Dependent(bytes().toList())
            9 -> KeyType.Anonymous(readPackage(this), string(), readAnonymousOrigin(this))
            else -> fail("Unknown KeyType tag $tag", LibraryFormatCode.UNSUPPORTED)
        }
    } }

    fun keyBytes(value: KeyType): ByteArray = LibraryBinaryWriter().also { writeKeyType(it, value) }.toByteArray()
    fun declarationKeyBytes(value: DeclarationKey): ByteArray =
        LibraryBinaryWriter().also { writeDeclarationKey(it, value) }.toByteArray()

    private fun writeType(writer: LibraryBinaryWriter, value: LibraryType, strings: LibraryStrings) = with(writer) {
        when (value) {
            is LibraryType.Builtin -> tagged(1) { u32(strings.reference(value.key)) }
            is LibraryType.Declaration -> tagged(2) { writeDeclarationReference(this, value.target) }
            is LibraryType.Parameter -> tagged(3) {
                writeDeclarationReference(this, value.owner); u8(value.role.wireValue); u32(value.index)
            }
            is LibraryType.Applied -> tagged(4) {
                u32(value.constructor.value); list(value.arguments) { u32(it.value) }
            }
            is LibraryType.Specialized -> tagged(5) {
                u32(value.constructor.value); list(value.arguments) { u32(it.value) }
            }
            is LibraryType.Union -> tagged(6) { list(value.alternatives) { u32(it.value) } }
            is LibraryType.Intersection -> tagged(7) {
                list(value.components) { u32(it.value) }; optional(value.declaration) { u32(it.value) }
            }
            is LibraryType.Function -> tagged(8) {
                u8(value.execution.wireValue); list(value.parameters) { u32(it.value) }; u32(value.result.value)
            }
            is LibraryType.NbtRef -> tagged(9) { u32(value.value.value) }
            is LibraryType.Dependent -> tagged(10) {
                u32(value.syntax.value); u32(value.environment.value); u32(value.owner.value)
            }
            is LibraryType.UnresolvedNominal -> tagged(11) {
                u32(strings.reference(value.kind)); u32(strings.reference(value.namespace)); u32(strings.reference(value.name))
            }
        }
    }

    private fun readType(reader: LibraryBinaryReader, strings: LibraryStrings): LibraryType = reader.tagged { tag ->
        when (tag) {
            1 -> LibraryType.Builtin(strings.value(u32()))
            2 -> LibraryType.Declaration(readDeclarationReference(this))
            3 -> LibraryType.Parameter(readDeclarationReference(this), parameterRole(u8()), u32())
            4 -> LibraryType.Applied(TypeRef(u32()), list { TypeRef(u32()) })
            5 -> LibraryType.Specialized(TypeRef(u32()), list { ConstantRef(u32()) })
            6 -> LibraryType.Union(list { TypeRef(u32()) })
            7 -> LibraryType.Intersection(list { TypeRef(u32()) }, optional { DeclarationId(u32()) })
            8 -> LibraryType.Function(execution(u8()), list { TypeRef(u32()) }, TypeRef(u32()))
            9 -> LibraryType.NbtRef(TypeRef(u32()))
            10 -> LibraryType.Dependent(NodeRef(u32()), EnvironmentId(u32()), DeclarationId(u32()))
            11 -> LibraryType.UnresolvedNominal(strings.value(u32()), strings.value(u32()), strings.value(u32()))
            else -> fail("Unknown T tag $tag", LibraryFormatCode.UNSUPPORTED)
        }
    }

    private fun writeConstant(writer: LibraryBinaryWriter, value: ConstantValue, strings: LibraryStrings) = with(writer) {
        when (value) {
            ConstantValue.Null -> tagged(1) { }
            is ConstantValue.Integral -> tagged(2) { i64(value.value) }
            is ConstantValue.FloatBits -> tagged(3) { u32(value.bits.toLong() and 0xffff_ffffL) }
            is ConstantValue.DoubleBits -> tagged(4) { u64(value.bits.toULong()) }
            is ConstantValue.Bool -> tagged(5) { bool(value.value) }
            is ConstantValue.Text -> tagged(6) { u32(strings.reference(value.value)) }
            is ConstantValue.TypeValue -> tagged(7) { u32(value.value.value) }
            is ConstantValue.Nbt -> tagged(8) { u32(strings.reference(value.snbt)) }
            is ConstantValue.Sequence -> tagged(9) { list(value.elements) { u32(it.value) } }
            is ConstantValue.Record -> tagged(10) {
                list(value.fields) { u32(strings.reference(it.name)); u32(it.value.value) }
            }
            is ConstantValue.Typed -> tagged(11) { u32(value.type.value); u32(value.value.value) }
            is ConstantValue.FunctionValue -> tagged(12) {
                writeDeclarationReference(this, value.target)
                list(value.captures) { writeDeclarationReference(this, it.declaration); u32(it.value.value) }
                u32(value.type.value)
            }
        }
    }

    private fun readConstant(reader: LibraryBinaryReader, strings: LibraryStrings): ConstantValue = reader.tagged { tag ->
        when (tag) {
            1 -> ConstantValue.Null
            2 -> ConstantValue.Integral(i64())
            3 -> ConstantValue.FloatBits(u32().toInt())
            4 -> ConstantValue.DoubleBits(u64().toLong())
            5 -> ConstantValue.Bool(bool())
            6 -> ConstantValue.Text(strings.value(u32()))
            7 -> ConstantValue.TypeValue(TypeRef(u32()))
            8 -> ConstantValue.Nbt(strings.value(u32()))
            9 -> ConstantValue.Sequence(list { ConstantRef(u32()) })
            10 -> ConstantValue.Record(list { ConstantField(strings.value(u32()), ConstantRef(u32())) })
            11 -> ConstantValue.Typed(TypeRef(u32()), ConstantRef(u32()))
            12 -> ConstantValue.FunctionValue(readDeclarationReference(this),
                list { ConstantCapture(readDeclarationReference(this), ConstantRef(u32())) }, TypeRef(u32()))
            else -> fail("Unknown C tag $tag", LibraryFormatCode.UNSUPPORTED)
        }
    }

    private fun LibraryBinaryReader.parameterRole(value: Int): ParameterRole = when (value) {
        1 -> ParameterRole.READONLY
        2 -> ParameterRole.LAMBDA_TYPE
        3 -> ParameterRole.NATIVE_TYPE
        else -> fail("Unknown parameter role $value", LibraryFormatCode.UNSUPPORTED)
    }

    private fun LibraryBinaryReader.execution(value: Int): FunctionExecution =
        FunctionExecution.values().firstOrNull { it.wireValue == value }
            ?: fail("Unknown function execution $value", LibraryFormatCode.UNSUPPORTED)

    private fun <T> checked(reader: LibraryBinaryReader, read: LibraryBinaryReader.() -> T): T = try {
        read(reader)
    } catch (failure: IllegalArgumentException) {
        reader.fail(failure.message ?: "Invalid table value")
    }

    /** Checks bounds and closed-value cycles; declaration edges are leaves, enabling recursive shells. */
    fun validate(tables: LibraryTables) {
        val keys = StructuralKeys(tables, object : CanonicalIdentityBindings {
                // Validation needs bounds/cycles, not ordering identities; these markers are never persisted.
                override fun localDeclaration(reference: DeclarationId) = byteArrayOf()
                override fun dependency(index: Long) = byteArrayOf()
                override fun node(reference: NodeRef) = byteArrayOf()
                override fun environment(reference: EnvironmentId) = byteArrayOf()
            })
        fun check(section: Int, index: Int, operation: () -> Unit) {
            try {
                operation()
            } catch (failure: IllegalArgumentException) {
                throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                    failure.message ?: "Invalid T/C graph", 0, section, index + 1L, failure)
            }
        }
        tables.types.indices.forEach { index -> check(LibrarySectionId.TYPES, index) { keys.type(TypeRef(index + 1L)) } }
        tables.constants.indices.forEach { index -> check(LibrarySectionId.CONSTANTS, index) {
            keys.constant(ConstantRef(index + 1L))
            (tables.constants[index] as? ConstantValue.Record)?.let { record ->
                require(record.fields.zipWithNext().all { (a, b) -> compareUtf8(a.name, b.name) < 0 }) {
                    "Constant record fields must be sorted and unique"
                }
            }
        } }
    }

    fun canonicalize(tables: LibraryTables, identities: CanonicalIdentityBindings): CanonicalLibraryTables {
        val keys = StructuralKeys(tables, identities).also { it.all() }
        val typeOrder = tables.types.indices.map { TypeRef(it + 1L) }
            .distinctBy { keys.type(it).toList() }.sortedWith { a, b -> compareBytes(keys.type(a), keys.type(b)) }
        val constantOrder = tables.constants.indices.map { ConstantRef(it + 1L) }
            .distinctBy { keys.constant(it).toList() }.sortedWith { a, b -> compareBytes(keys.constant(a), keys.constant(b)) }
        val typeByKey = typeOrder.mapIndexed { index, ref -> keys.type(ref).toList() to TypeRef(index + 1L) }.toMap()
        val constantByKey = constantOrder.mapIndexed { index, ref -> keys.constant(ref).toList() to ConstantRef(index + 1L) }.toMap()
        val types = tables.types.indices.associate { TypeRef(it + 1L).let { old -> old to typeByKey.getValue(keys.type(old).toList()) } }
        val constants = tables.constants.indices.associate {
            ConstantRef(it + 1L).let { old -> old to constantByKey.getValue(keys.constant(old).toList()) }
        }
        fun t(ref: TypeRef) = types.getValue(ref)
        fun c(ref: ConstantRef) = constants.getValue(ref)
        val result = LibraryTables(typeOrder.map { ref -> when (val value = tables.type(ref)) {
            is LibraryType.Applied -> value.copy(constructor = t(value.constructor), arguments = value.arguments.map(::t))
            is LibraryType.Specialized -> value.copy(constructor = t(value.constructor), arguments = value.arguments.map(::c))
            is LibraryType.Union -> value.copy(alternatives = value.alternatives.distinctBy { keys.type(it).toList() }
                .sortedWith { a, b -> compareBytes(keys.type(a), keys.type(b)) }.map(::t))
            is LibraryType.Intersection -> value.copy(components = value.components.map(::t))
            is LibraryType.Function -> value.copy(parameters = value.parameters.map(::t), result = t(value.result))
            is LibraryType.NbtRef -> value.copy(value = t(value.value))
            else -> value
        } }, constantOrder.map { ref -> when (val value = tables.constant(ref)) {
            is ConstantValue.TypeValue -> value.copy(value = t(value.value))
            is ConstantValue.Sequence -> value.copy(elements = value.elements.map(::c))
            is ConstantValue.Record -> value.copy(fields = value.fields.sortedWith { a, b -> compareUtf8(a.name, b.name) }
                .map { it.copy(value = c(it.value)) })
            is ConstantValue.Typed -> value.copy(type = t(value.type), value = c(value.value))
            is ConstantValue.FunctionValue -> value.copy(type = t(value.type), captures = value.captures.map { it.copy(value = c(it.value)) })
            else -> value
        } })
        validate(result)
        return CanonicalLibraryTables(result, Collections.unmodifiableMap(types), Collections.unmodifiableMap(constants))
    }

    private class StructuralKeys(val tables: LibraryTables, val identities: CanonicalIdentityBindings) {
        private val types = HashMap<TypeRef, ByteArray>()
        private val constants = HashMap<ConstantRef, ByteArray>()
        private val activeTypes = HashSet<TypeRef>()
        private val activeConstants = HashSet<ConstantRef>()

        fun all() {
            tables.types.indices.forEach { type(TypeRef(it + 1L)) }
            tables.constants.indices.forEach { constant(ConstantRef(it + 1L)) }
        }

        private fun LibraryBinaryWriter.declaration(value: DeclarationReference) {
            when (value) {
                is DeclarationReference.Local -> tagged(1) { bytes(identities.localDeclaration(value.target)) }
                is DeclarationReference.Dependency -> tagged(2) {
                    // The resolved package identity must be supplied: dependency array order is not a key.
                    bytes(identities.dependency(value.dependencyIndex)); writeDeclarationKey(this, value.key)
                }
            }
        }

        fun type(ref: TypeRef): ByteArray {
            types[ref]?.let { return it }
            require(activeTypes.add(ref)) { "Cycle in structural T/C graph at T${ref.value}" }
            val result = LibraryBinaryWriter().apply { when (val value = tables.type(ref)) {
                is LibraryType.Builtin -> tagged(1) { string(value.key) }
                is LibraryType.Declaration -> tagged(2) { declaration(value.target) }
                is LibraryType.Parameter -> tagged(3) { declaration(value.owner); u8(value.role.wireValue); u32(value.index) }
                is LibraryType.Applied -> tagged(4) { bytes(type(value.constructor)); list(value.arguments) { bytes(type(it)) } }
                is LibraryType.Specialized -> tagged(5) { bytes(type(value.constructor)); list(value.arguments) { bytes(constant(it)) } }
                is LibraryType.Union -> tagged(6) {
                    val alternatives = value.alternatives.map { type(it).toList() }.distinct()
                        .sortedWith { a, b -> compareBytes(a.toByteArray(), b.toByteArray()) }
                    list(alternatives) { bytes(it.toByteArray()) }
                }
                is LibraryType.Intersection -> tagged(7) {
                    list(value.components) { bytes(type(it)) }; optional(value.declaration) { bytes(identities.localDeclaration(it)) }
                }
                is LibraryType.Function -> tagged(8) {
                    u8(value.execution.wireValue); list(value.parameters) { bytes(type(it)) }; bytes(type(value.result))
                }
                is LibraryType.NbtRef -> tagged(9) { bytes(type(value.value)) }
                is LibraryType.Dependent -> tagged(10) {
                    bytes(identities.node(value.syntax)); bytes(identities.environment(value.environment))
                    bytes(identities.localDeclaration(value.owner))
                }
                is LibraryType.UnresolvedNominal -> tagged(11) { string(value.kind); string(value.namespace); string(value.name) }
            } }.toByteArray()
            activeTypes.remove(ref); types[ref] = result
            return result
        }

        fun constant(ref: ConstantRef): ByteArray {
            constants[ref]?.let { return it }
            require(activeConstants.add(ref)) { "Cycle in structural T/C graph at C${ref.value}" }
            val result = LibraryBinaryWriter().apply { when (val value = tables.constant(ref)) {
                ConstantValue.Null -> tagged(1) { }
                is ConstantValue.Integral -> tagged(2) { i64(value.value) }
                is ConstantValue.FloatBits -> tagged(3) { u32(value.bits.toLong() and 0xffff_ffffL) }
                is ConstantValue.DoubleBits -> tagged(4) { u64(value.bits.toULong()) }
                is ConstantValue.Bool -> tagged(5) { bool(value.value) }
                is ConstantValue.Text -> tagged(6) { string(value.value) }
                is ConstantValue.TypeValue -> tagged(7) { bytes(type(value.value)) }
                is ConstantValue.Nbt -> tagged(8) { string(value.snbt) }
                is ConstantValue.Sequence -> tagged(9) { list(value.elements) { bytes(constant(it)) } }
                is ConstantValue.Record -> tagged(10) {
                    require(value.fields.map { it.name }.distinct().size == value.fields.size) { "Duplicate constant field" }
                    list(value.fields.sortedWith { a, b -> compareUtf8(a.name, b.name) }) { string(it.name); bytes(constant(it.value)) }
                }
                is ConstantValue.Typed -> tagged(11) { bytes(type(value.type)); bytes(constant(value.value)) }
                is ConstantValue.FunctionValue -> tagged(12) {
                    declaration(value.target); list(value.captures) { declaration(it.declaration); bytes(constant(it.value)) }
                    bytes(type(value.type))
                }
            } }.toByteArray()
            activeConstants.remove(ref); constants[ref] = result
            return result
        }
    }
}
