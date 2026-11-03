package top.mcfpp.test.library

import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import kotlin.test.*

class LibraryTypeTableTest {
    private data class Graph(val tables: LibraryTables, val parent: TypeRef, val typedSymbol: ConstantRef)

    private fun graph(): Graph {
        val types = arrayListOf<LibraryType>()
        val constants = arrayListOf<ConstantValue>()
        fun type(value: LibraryType) = TypeRef(types.size + 1L).also { types += value }
        fun constant(value: ConstantValue) = ConstantRef(constants.size + 1L).also { constants += value }
        val int = type(LibraryType.Builtin("int"))
        val float = type(LibraryType.Builtin("float"))
        val double = type(LibraryType.Builtin("double"))
        val meta = type(LibraryType.Builtin("Type"))
        val any = type(LibraryType.Builtin("any"))
        val parent = type(LibraryType.UnresolvedNominal("template", "parent.origin", "Box"))
        val argument = type(LibraryType.UnresolvedNominal("enum", "unowned.argument", "Token"))
        val integer = constant(ConstantValue.Typed(int, constant(ConstantValue.Integral(7))))
        val negativeZero = constant(ConstantValue.Typed(float, constant(ConstantValue.FloatBits(Int.MIN_VALUE))))
        val nan = constant(ConstantValue.Typed(float, constant(ConstantValue.FloatBits(0x7fc0_0011))))
        val doubleNan = constant(ConstantValue.Typed(double, constant(ConstantValue.DoubleBits(0x7ff8_0000_0000_0021L))))
        val typeValue = constant(ConstantValue.Typed(meta, constant(ConstantValue.TypeValue(int))))
        val text = constant(ConstantValue.Text("Box_type-0:<int>,\\\"🚀"))
        val sequence = constant(ConstantValue.Sequence(listOf(integer, nan, negativeZero, text)))
        val record = constant(ConstantValue.Record(listOf(ConstantField("a", sequence), ConstantField("z", doubleNan))))
        val typedSymbol = constant(ConstantValue.Typed(argument, record))
        val wrapped = constant(ConstantValue.Typed(any, typedSymbol))
        val symbolicType = constant(ConstantValue.TypeValue(argument))
        val result = type(LibraryType.Specialized(parent, listOf(integer, negativeZero, nan, doubleNan,
            typeValue, text, sequence, record, wrapped, symbolicType)))
        type(LibraryType.UnresolvedNominal("template", "parent.origin", "Box"))
        return Graph(LibraryTables(types, constants), result, typedSymbol)
    }

    private val identities = object : CanonicalIdentityBindings {}

    private fun canonical(graph: Graph): Graph {
        val value = TypeTableCodec.canonicalize(graph.tables, identities)
        return Graph(value.tables, value.typeReferences.getValue(graph.parent), value.constantReferences.getValue(graph.typedSymbol))
    }

    @Test
    fun tagElevenAndCanonicalIdentityKeepNestedTypedValuesAndRawIeeeBits() {
        val source = graph()
        val canonical = canonical(source)
        val strings = LibraryStrings.of(TypeTableCodec.collectStrings(canonical.tables))
        val typeBytes = TypeTableCodec.encodeTypes(canonical.tables.types, strings)
        val constantBytes = TypeTableCodec.encodeConstants(canonical.tables.constants, strings)
        val decoded = TypeTableCodec.decode(typeBytes, constantBytes, strings)
        assertEquals(canonical.tables.types, decoded.types)
        assertEquals(canonical.tables.constants, decoded.constants)
        assertEquals(2, decoded.types.filterIsInstance<LibraryType.UnresolvedNominal>().size)
        assertTrue(decoded.constants.contains(ConstantValue.FloatBits(Int.MIN_VALUE)))
        assertTrue(decoded.constants.contains(ConstantValue.FloatBits(0x7fc0_0011)))
        assertTrue(decoded.constants.contains(ConstantValue.DoubleBits(0x7ff8_0000_0000_0021L)))
        val symbolic = LibraryType.UnresolvedNominal("template", "parent.origin", "Box")
        val symbolicStrings = LibraryStrings.of(TypeTableCodec.collectStrings(LibraryTables(listOf(symbolic), emptyList())))
        val reader = LibraryBinaryReader(TypeTableCodec.encodeTypes(listOf(symbolic), symbolicStrings))
        assertEquals(listOf(11), reader.list { record { tagged { tag -> repeat(3) { u32() }; tag } } })
        reader.requireEnd()

        fun type(reference: TypeRef) = TypeRef(source.tables.types.size + 1L - reference.value)
        fun constant(reference: ConstantRef) = ConstantRef(source.tables.constants.size + 1L - reference.value)
        val shuffled = LibraryTables(source.tables.types.reversed().map { value -> when (value) {
            is LibraryType.Specialized -> value.copy(constructor = type(value.constructor), arguments = value.arguments.map(::constant))
            else -> value
        } }, source.tables.constants.reversed().map { value -> when (value) {
            is ConstantValue.Typed -> value.copy(type = type(value.type), value = constant(value.value))
            is ConstantValue.TypeValue -> value.copy(value = type(value.value))
            is ConstantValue.Sequence -> value.copy(elements = value.elements.map(::constant))
            is ConstantValue.Record -> value.copy(fields = value.fields.map { it.copy(value = constant(it.value)) })
            else -> value
        } })
        val reordered = TypeTableCodec.canonicalize(shuffled, identities).tables
        assertContentEquals(typeBytes, TypeTableCodec.encodeTypes(reordered.types, strings))
        assertContentEquals(constantBytes, TypeTableCodec.encodeConstants(reordered.constants, strings))
    }

    private fun model(graph: Graph, field: ValueDeclaration? = null, parentExpression: NodeRef? = null,
                      pending: Boolean = true): LibraryModel {
        val owner = DeclarationId(1)
        val members = if (field == null) emptyList() else listOf(DeclarationId(2))
        val compound = CompoundDeclaration(DeclCommon(DeclarationKey("fixture.pending", emptyList(), 3, "Child"), null,
            "Child", null, EnvironmentId(1), DeclarationAccess.PUBLIC, 0), CompoundCategory.DATA, emptyList(),
            if (pending) listOf(ParentRecord(graph.parent, parentExpression)) else emptyList(), null,
            members, members, null, emptyList())
        val syntax = if (parentExpression == null) LibrarySyntax(emptyList(), emptyList(), emptyList())
            else LibrarySyntax(listOf(TypeSyntax.Void(ScopeId(1))), listOf(ScopeRecord(owner, null, emptyList())), emptyList())
        return LibraryModel(LibraryManifest(PackageKey("fixture:pending", "1"), "main", LibraryTarget("26.3"),
            entryEnvironments = listOf(EnvironmentId(1)), rootDeclarations = listOf(owner)),
            LibraryDeclarations(listOf(compound) + listOfNotNull(field), listOf(EnvironmentRecord("fixture.pending", emptyList()))),
            graph.tables, syntax)
    }

    @Test
    fun pendingParentBinaryNeedsNoInventedDeclarationOrPackageForItsSymbolicIdentity() {
        val source = canonical(graph())
        val codec = LibraryModelCodec(listOf(CompoundCodec))
        val decoded = codec.decode(codec.encode(model(source)))
        assertEquals(1, decoded.declarations.declarations.size)
        assertTrue(decoded.manifest.dependencies.isEmpty())
        val parent = assertIs<CompoundDeclaration>(decoded.declarations.declaration(DeclarationId(1))).parents.single()
        assertNull(parent.expression)
        val specialized = assertIs<LibraryType.Specialized>(decoded.tables.type(assertNotNull(parent.type)))
        assertEquals(LibraryType.UnresolvedNominal("template", "parent.origin", "Box"), decoded.tables.type(specialized.constructor))
        assertEquals(source.tables.constants, decoded.tables.constants)
        assertTrue(decoded.localReferencesValidated)
    }

    @Test
    fun symbolicIdentityCannotBecomeAnOrdinaryFieldConstantOrExecutableParentExpression() {
        val source = canonical(graph())
        val codec = LibraryModelCodec(listOf(CompoundCodec))
        fun field(type: TypeRef, frozen: ConstantRef? = null) = ValueDeclaration(DeclCommon(null, null, "value", DeclarationId(1),
            EnvironmentId(1), DeclarationAccess.PUBLIC, if (frozen == null) 0 else DeclarationFlags.CONST), ValueRole.MEMBER,
            if (frozen == null) ValueStorage.VAR else ValueStorage.CONST, type, null, null, frozen)
        assertContains(assertFailsWith<LibraryFormatException> { codec.encode(model(source, field(source.parent))) }.message.orEmpty(), "Unresolved nominal")
        val int = TypeRef(source.tables.types.indexOf(LibraryType.Builtin("int")) + 1L)
        assertContains(assertFailsWith<LibraryFormatException> { codec.encode(model(source, field(int, source.typedSymbol))) }.message.orEmpty(), "Unresolved nominal")
        assertContains(assertFailsWith<LibraryFormatException> { codec.encode(model(source, parentExpression = NodeRef(1))) }.message.orEmpty(), "pending parent")
        assertContains(assertFailsWith<LibraryFormatException> { codec.encode(model(source, pending = false)) }.message.orEmpty(), "pending parent")
    }
}
