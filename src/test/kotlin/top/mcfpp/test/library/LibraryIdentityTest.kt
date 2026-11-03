package top.mcfpp.test.library

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.core.lang.context.ContextTypes
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.type.*
import kotlin.test.*

class LibraryIdentityTest {
    private val noBindings = object : CompilerTypeBindings { }
    private val noIdentities = object : CanonicalIdentityBindings { }

    private fun roundTrip(tables: LibraryTables): LibraryTables {
        val strings = LibraryStrings.of(TypeTableCodec.collectStrings(tables))
        val restoredStrings = TypeTableCodec.decodeStrings(TypeTableCodec.encodeStrings(strings))
        return TypeTableCodec.decode(TypeTableCodec.encodeTypes(tables.types, strings),
            TypeTableCodec.encodeConstants(tables.constants, strings), restoredStrings)
    }

    @Test fun actualCompilerTypesAndValuesRetainTheirCompleteClosure() {
        val roots = listOf(
            MCFPPListType(MCFPPNBTRefType(MCFPPUnionType(MCFPPBaseType.Int, MCFPPBaseType.String))),
            MCFPPFunctionType(listOf(MCFPPImmutableListType(MCFPPNBTType.Long), ContextTypes.PosContext),
                MCFPPNBTRefType(MCFPPBaseType.Float), MCFPPFunctionType.ExecutionCategory.PAUSED),
            MCFPPDictType(MCFPPMapType(MCFPPBaseType.Bool)), MCFPPVectorType(3),
            requireNotNull(MCFPPType.nativeConstructor("list")), ContextTypes.RelationContext,
            ContextTypes.Position, MCFPPPrivateType.Null, MCFPPConcreteType.Type, MCFPPScoreRefType,
        )
        val payload = CompilerValue.Typed(roots.first().typeId, CompilerValue.Record(mapOf(
            "float" to CompilerValue.FloatBits((-0.0f).toRawBits()),
            "double" to CompilerValue.DoubleBits(0x7ff8000000000001L),
            "nbt" to CompilerValue.Nbt("{\"a\":1,\"b\":2}"),
            "nested" to CompilerValue.Sequence(listOf(CompilerValue.NullValue, CompilerValue.Integral(Long.MIN_VALUE),
                CompilerValue.Bool(true), CompilerValue.Text("库"), CompilerValue.TypeValue(roots[1].typeId))),
        )))
        val capture = CompilerTypeTableCapture(noBindings)
        val rootRefs = roots.map(capture::type)
        val constantRef = capture.constant(payload)
        val canonical = capture.finish(noIdentities)
        val restored = CompilerTypeTableRestore(roundTrip(canonical.tables), noBindings)
        roots.zip(rootRefs).forEach { (type, reference) ->
            assertEquals(type.typeId, restored.type(canonical.typeReferences.getValue(reference)).typeId)
        }
        assertEquals(payload, restored.constant(canonical.constantReferences.getValue(constantRef)))
    }

    @Test fun independentlyImportedLibrariesAndOwnedParametersNeverShareOpaqueIdentities() {
        val first = LibraryIdentity(PackageKey("fixture:first", "1"), "main")
        val second = LibraryIdentity(PackageKey("fixture:second", "1"), "main")
        val key = DeclarationKey("shared.namespace", emptyList(), 3, "Box")
        val declarations = listOf(LibraryDeclarationIdentity(DeclarationId(1), key = key))
        val identities = LibraryIdentities<MCFPPType>()
        val firstShell = identities.register(first, listOf(second), declarations).single()
        val secondShell = identities.register(second, emptyList(), declarations).single()
        assertFalse(firstShell.isBound)
        assertSame(secondShell, identities.resolve(first, DeclarationReference.Dependency(1, key)))
        assertNotEquals(firstShell.sessionIdentity, secondShell.sessionIdentity)
        val local = DeclarationReference.Local(DeclarationId(1))
        val firstParameter = identities.parameter(first, local, ParameterRole.READONLY, 0)
        assertEquals(firstParameter, identities.parameter(first, local, ParameterRole.READONLY, 0))
        assertNotEquals(firstParameter, identities.parameter(second, local, ParameterRole.READONLY, 0))
        assertEquals(identities.parameter(second, local, ParameterRole.READONLY, 0),
            identities.parameter(first, DeclarationReference.Dependency(1, key), ParameterRole.READONLY, 0))
        assertNotEquals(firstParameter, identities.parameter(first, local, ParameterRole.READONLY, 1))
        val sourceParameter = MCFPPGenericParamType("T", arrayListOf())
        val sourceRoot = MCFPPListType(sourceParameter)
        val capture = CompilerTypeTableCapture(object : CompilerTypeBindings {
            override fun parameter(id: TypeId.Opaque) = if (id == sourceParameter.typeId)
                LibraryType.Parameter(local, ParameterRole.READONLY, 0) else null
        })
        val sourceRef = capture.type(sourceRoot)
        val canonical = capture.finish(identities.canonicalBindings(first))
        val parameterType = MCFPPGenericParamType("T", arrayListOf(), firstParameter)
        firstShell.bind(parameterType)
        secondShell.bind(MCFPPBaseType.Bool)
        val restored = CompilerTypeTableRestore(roundTrip(canonical.tables), object : CompilerTypeBindings {
            override fun restoreParameter(value: LibraryType.Parameter): MCFPPType {
                assertEquals(firstParameter, identities.parameter(first, value.owner, value.role, value.index))
                return parameterType
            }
        }).type(canonical.typeReferences.getValue(sourceRef)) as MCFPPListType
        assertSame(parameterType, restored.generic.single())
        assertNotEquals(sourceParameter.typeId, restored.generic.single().typeId)
        assertFailsWith<IllegalStateException> { firstShell.bind(MCFPPBaseType.Int) }
    }

    @Test fun preregisteredShellsSupportForwardAndMutuallyRecursiveDeclarations() {
        val library = LibraryIdentity(PackageKey("fixture:recursive", "1"), "main")
        val keys = listOf("A", "B").map { DeclarationKey("recursive", emptyList(), 3, it) }
        val identities = LibraryIdentities<MutableList<DeclarationShell<*>>>()
        val shells = identities.register(library, emptyList(), keys.mapIndexed { index, key ->
            LibraryDeclarationIdentity(DeclarationId(index + 1L), key = key)
        })
        shells[0].bind(mutableListOf(identities.resolve(library, DeclarationReference.Local(DeclarationId(2)))))
        shells[1].bind(mutableListOf(identities.resolve(library, DeclarationReference.Local(DeclarationId(1)))))
        assertSame(shells[1], shells[0].binding().single())
        assertSame(shells[0], shells[1].binding().single())
        val types = LibraryTables(listOf(LibraryType.Declaration(DeclarationReference.Local(DeclarationId(1))),
            LibraryType.Declaration(DeclarationReference.Local(DeclarationId(2)))), emptyList())
        assertEquals(2, TypeTableCodec.canonicalize(types, identities.canonicalBindings(library)).tables.types.size)
    }

    @Test fun allExplicitTypeConstantAndKeyVariantsRoundTripWithoutInternalObjects() {
        val key = DeclarationKey("fixture", listOf(DeclarationPathPart(3, "Outer")), 3, "Box")
        val dependency = DeclarationReference.Dependency(1, key)
        val types = listOf(
            LibraryType.Builtin("int"), LibraryType.Builtin("list"), LibraryType.Declaration(dependency),
            LibraryType.Parameter(DeclarationReference.Local(DeclarationId(2)), ParameterRole.READONLY, 0),
            LibraryType.Applied(TypeRef(2), listOf(TypeRef(1))),
            LibraryType.Specialized(TypeRef(3), listOf(ConstantRef(1))),
            LibraryType.Union(listOf(TypeRef(5), TypeRef(1), TypeRef(1))),
            LibraryType.Intersection(listOf(TypeRef(3), TypeRef(5)), DeclarationId(3)),
            LibraryType.Function(FunctionExecution.GENERATOR, listOf(TypeRef(4)), TypeRef(1)),
            LibraryType.NbtRef(TypeRef(1)), LibraryType.Dependent(NodeRef(1), EnvironmentId(1), DeclarationId(1)),
        )
        val constants = listOf(
            ConstantValue.Integral(42), ConstantValue.Null, ConstantValue.FloatBits(Int.MIN_VALUE),
            ConstantValue.DoubleBits(Long.MIN_VALUE), ConstantValue.Bool(true), ConstantValue.Text("text"),
            ConstantValue.TypeValue(TypeRef(3)), ConstantValue.Nbt("{a:1}"),
            ConstantValue.Sequence(listOf(ConstantRef(1), ConstantRef(5))),
            ConstantValue.Record(listOf(ConstantField("z", ConstantRef(6)), ConstantField("a", ConstantRef(1)))),
            ConstantValue.Typed(TypeRef(1), ConstantRef(1)),
            ConstantValue.FunctionValue(dependency, listOf(ConstantCapture(DeclarationReference.Local(DeclarationId(2)), ConstantRef(10))), TypeRef(9)),
        )
        val identities = object : CanonicalIdentityBindings {
            override fun localDeclaration(reference: DeclarationId) = when (reference.value) {
                1L -> "owner"; 2L -> "parameter-owner"; else -> "intersection"
            }.toByteArray()
            override fun dependency(index: Long) = "fixture:dependency@1/main".toByteArray()
            override fun node(reference: NodeRef) = "owner/signature/type".toByteArray()
            override fun environment(reference: EnvironmentId) = "fixture/imports".toByteArray()
        }
        val canonical = TypeTableCodec.canonicalize(LibraryTables(types, constants), identities)
        val restored = roundTrip(canonical.tables)
        assertEquals(canonical.tables.types, restored.types)
        assertEquals(canonical.tables.constants, restored.constants)
        val variants = listOf<KeyType>(KeyType.Builtin("int"),
            KeyType.Nominal(PackageKey("fixture:types", "1"), "main", "fixture", listOf("Box")),
            KeyType.Parameter(0, ParameterRole.READONLY, 1),
            KeyType.Applied(KeyType.Builtin("list"), listOf(KeyType.Builtin("int"))),
            KeyType.Specialized(KeyType.Builtin("Box"), listOf(listOf(1, 2).map(Int::toByte))),
            KeyType.Union(listOf(KeyType.Builtin("string"), KeyType.Builtin("int"))),
            KeyType.Function(FunctionExecution.PAUSED, listOf(KeyType.Builtin("int")), KeyType.Builtin("void")),
            KeyType.Dependent(listOf(1, 2, 3).map(Int::toByte)),
            KeyType.Anonymous(PackageKey("fixture:types", "1"), "main", AnonymousOrigin("types.mcfpp", 0, listOf(1, 3))),
        )
        for (variant in variants) {
            val bytes = TypeTableCodec.keyBytes(variant)
            val reader = LibraryBinaryReader(bytes)
            assertContentEquals(bytes, TypeTableCodec.keyBytes(TypeTableCodec.readKeyType(reader)))
            reader.requireEnd()
        }
        val functionKey = key.copy(kind = 2, name = "f", signature = DeclarationSignature(variants.take(3), variants.drop(3)))
        val reference = DeclarationReference.Dependency(1, functionKey)
        val encoded = LibraryBinaryWriter().also { TypeTableCodec.writeDeclarationReference(it, reference) }.toByteArray()
        val reader = LibraryBinaryReader(encoded)
        val decoded = TypeTableCodec.readDeclarationReference(reader)
        reader.requireEnd()
        assertContentEquals(encoded, LibraryBinaryWriter().also { TypeTableCodec.writeDeclarationReference(it, decoded) }.toByteArray())
    }

    @Test fun canonicalTypeOrderDoesNotDependOnCaptureOrderOrTemporaryLocalNumbers() {
        fun build(order: List<MCFPPType>): Pair<ByteArray, ByteArray> {
            val capture = CompilerTypeTableCapture(noBindings)
            order.forEach(capture::type)
            val tables = capture.finish(noIdentities).tables
            val strings = LibraryStrings.of(TypeTableCodec.collectStrings(tables))
            return TypeTableCodec.encodeTypes(tables.types, strings) to TypeTableCodec.encodeStrings(strings)
        }
        val roots = listOf(MCFPPListType(MCFPPBaseType.Int), MCFPPFunctionType(listOf(MCFPPBaseType.String), MCFPPBaseType.Bool))
        val first = build(roots)
        val second = build(roots.reversed())
        assertContentEquals(first.first, second.first)
        assertContentEquals(first.second, second.second)
        val tables = LibraryTables(listOf(LibraryType.Declaration(DeclarationReference.Local(DeclarationId(99))),
            LibraryType.Declaration(DeclarationReference.Local(DeclarationId(1)))), emptyList())
        val canonical = TypeTableCodec.canonicalize(tables, object : CanonicalIdentityBindings {
            override fun localDeclaration(reference: DeclarationId) = if (reference.value == 99L) "A".toByteArray() else "Z".toByteArray()
        })
        assertEquals(TypeRef(1), canonical.typeReferences[TypeRef(1)])
        assertFailsWith<IllegalStateException> { TypeTableCodec.canonicalize(tables, noIdentities) }
    }

    @Test fun dependentTypesRequireTheirStructuredNodeAndOpaqueTypesRequireAnOwner() {
        val unresolved = UnresolvedType("SOURCE_SENTINEL_MUST_NOT_BE_SAVED")
        val dependent = LibraryType.Dependent(NodeRef(7), EnvironmentId(3), DeclarationId(2))
        val capture = CompilerTypeTableCapture(object : CompilerTypeBindings {
            override fun dependent(id: TypeId.Opaque) = if (id == unresolved.typeId) dependent else null
        })
        val reference = capture.type(unresolved)
        val snapshot = roundTrip(capture.snapshot())
        assertEquals(dependent, snapshot.type(reference))
        val expected = MCFPPGenericParamType("Bound", arrayListOf())
        assertSame(expected, CompilerTypeTableRestore(snapshot, object : CompilerTypeBindings {
            override fun restoreDependent(value: LibraryType.Dependent): MCFPPType {
                assertEquals(dependent, value); return expected
            }
        }).type(reference))
        assertFailsWith<IllegalStateException> { CompilerTypeTableCapture(noBindings).type(unresolved) }
        assertFailsWith<IllegalStateException> { CompilerTypeTableCapture(noBindings).type(MCFPPGenericParamType("Orphan", arrayListOf())) }
    }

    @Test fun malformedReferencesCyclesAndUnknownTagsAreRejectedWithTableDiagnostics() {
        val invalid = LibraryTables(listOf(LibraryType.Applied(TypeRef(2), emptyList())), emptyList())
        assertEquals(LibraryFormatCode.CORRUPT, assertFailsWith<LibraryFormatException> { TypeTableCodec.validate(invalid) }.code)
        val cycle = LibraryTables(listOf(LibraryType.Specialized(TypeRef(1), emptyList())), emptyList())
        assertFailsWith<LibraryFormatException> { TypeTableCodec.validate(cycle) }
        val constantCycle = LibraryTables(emptyList(), listOf(ConstantValue.Sequence(listOf(ConstantRef(1)))))
        assertFailsWith<LibraryFormatException> { TypeTableCodec.validate(constantCycle) }
        val unknown = LibraryBinaryWriter().apply { list(listOf(1)) { record { tagged(65535) { } } } }.toByteArray()
        val diagnostic = assertFailsWith<LibraryFormatException> { TypeTableCodec.decodeTypes(unknown, LibraryStrings.of(emptyList())) }
        assertEquals(LibraryFormatCode.UNSUPPORTED, diagnostic.code)
        assertEquals(LibrarySectionId.TYPES, diagnostic.sectionId)
        assertEquals(1L, diagnostic.recordIndex)
        val zeroReference = LibraryBinaryWriter().apply { list(listOf(1)) { record { tagged(7) { u32(0) } } } }.toByteArray()
        val zero = assertFailsWith<LibraryFormatException> { TypeTableCodec.decodeConstants(zeroReference, LibraryStrings.of(emptyList())) }
        assertEquals(LibraryFormatCode.CORRUPT, zero.code)
        assertEquals(LibrarySectionId.CONSTANTS, zero.sectionId)
        assertEquals(1L, zero.recordIndex)
    }

    @Test fun tableSnapshotsOwnConstantCollectionsAndBuiltinMappingsAreExplicit() {
        val fields = arrayListOf(ConstantField("a", ConstantRef(1)))
        val tables = LibraryTables(emptyList(), listOf(ConstantValue.Integral(1), ConstantValue.Record(fields)))
        fields.clear()
        assertEquals(1, (tables.constants[1] as ConstantValue.Record).fields.size)
        for (type in listOf(ContextTypes.ExecutionContext, ContextTypes.PosContext, ContextTypes.EntityContext,
            ContextTypes.RelationContext, ContextTypes.Position, MCFPPNBTType.ByteArray, MCFPPConcreteType.JavaVar)) {
            val key = requireNotNull(LibraryBuiltinTypes.protocolKey(type.typeId as TypeId.Builtin))
            assertEquals(type.typeId, LibraryBuiltinTypes.restore(key).typeId)
        }
        assertFailsWith<IllegalStateException> { LibraryBuiltinTypes.restore("MCFPPBaseType.Int") }
    }
}
