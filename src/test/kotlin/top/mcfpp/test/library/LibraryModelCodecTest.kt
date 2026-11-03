package top.mcfpp.test.library

import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import kotlin.test.*

class LibraryModelCodecTest {
    private val environment = EnvironmentId(1)
    private val target = LibraryTarget("26.3")
    private fun common(name: String, owner: DeclarationId? = null, kind: Int? = null) = DeclCommon(
        kind?.let { DeclarationKey("fixture", emptyList(), it, name) }, null, name, owner, environment,
        DeclarationAccess.PUBLIC, 0)

    private fun ordinary(): LibraryModel {
        val scope = ScopeId(1)
        val parameter = DeclarationId(3)
        val root = Statement.Block(scope, listOf(Statement.Return(scope, Expression.Binary(scope, "+",
            Expression.Name(scope, NameUse.Local(parameter)), Expression.Literal(scope, ConstantRef(1))))))
        val syntax = SyntaxCodec.flatten(listOf(ScopeRecord(DeclarationId(2), null, listOf(parameter))), listOf(
            SyntaxBodySource(DeclarationId(2), environment, BodyRole.FUNCTION, root)))
        val declarations = LibraryDeclarations(listOf(
            NamespaceDeclaration(common("fixture", kind = 1), listOf(DeclarationId(2))),
            FunctionDeclaration(common("increment", DeclarationId(1), 2).copy(key =
                DeclarationKey("fixture", emptyList(), 2, "increment", DeclarationSignature(emptyList(), listOf(KeyType.Builtin("int"))))),
                FunctionExecutionKind.ORDINARY, null, emptyList(), listOf(parameter), TypeRef(1), BodyId(1), null),
            ValueDeclaration(common("value", DeclarationId(2)).copy(access = DeclarationAccess.PRIVATE),
                ValueRole.NORMAL_PARAMETER, ValueStorage.PARAMETER, TypeRef(1), null, null, null, parameterIndex = 0),
        ), listOf(EnvironmentRecord("fixture", emptyList())))
        return LibraryModel(LibraryManifest(PackageKey("fixture:ordinary", "1"), "main", target,
            entryEnvironments = listOf(environment), rootDeclarations = listOf(DeclarationId(1))),
            declarations, LibraryTables(listOf(LibraryType.Builtin("int")), listOf(ConstantValue.Integral(1))), syntax)
    }

    @Test fun ordinaryDocumentHasAllRealSectionsAndDecodesWithoutSourceOrParser() {
        val codec = LibraryModelCodec()
        val original = ordinary()
        val encoded = codec.encode(original)
        val wire = LibraryWire.decode(encoded)
        assertEquals(LibrarySectionId.requiredIds, wire.sections.map { it.id }.toSet())
        assertEquals(8, wire.section(LibrarySectionId.ANNOTATIONS)!!.byteLength)
        assertEquals(4, wire.section(LibrarySectionId.RUNTIME)!!.byteLength)
        assertEquals(4, wire.section(LibrarySectionId.RESOURCES)!!.byteLength)
        val decoded = codec.decode(encoded, LibraryCompatibility(minecraftVersion = "26.3"))
        assertTrue(decoded.localReferencesValidated)
        assertTrue(decoded.syntax.externalReferencesValidated)
        assertFalse(decoded.requiresDependencyBinding)
        assertNull(decoded.debug)
        assertEquals(original.manifest, decoded.manifest)
        assertEquals(original.declarations.declarations, decoded.declarations.declarations)
        assertEquals(original.syntax.nodes, decoded.syntax.nodes)
        assertEquals(original.tables.constants, decoded.tables.constants)
        assertContentEquals(encoded, codec.encode(decoded))
    }

    @Test fun localCrossTableDamageAndMissingPayloadHandlersAreRejected() {
        val codec = LibraryModelCodec()
        val original = ordinary()
        fun changed(declarations: LibraryDeclarations = original.declarations, tables: LibraryTables = original.tables,
            manifest: LibraryManifest = original.manifest): LibraryModel =
            LibraryModel(manifest, declarations, tables, original.syntax)
        val declarations = original.declarations.declarations.toMutableList()
        declarations[1] = (declarations[1] as FunctionDeclaration).copy(result = TypeRef(99))
        assertEquals(LibraryFormatCode.CORRUPT, assertFailsWith<LibraryFormatException> {
            codec.encode(changed(LibraryDeclarations(declarations, original.declarations.environments)))
        }.code)
        assertFailsWith<LibraryFormatException> {
            codec.encode(changed(manifest = original.manifest.copy(rootDeclarations = listOf(DeclarationId(99)))))
        }
        assertFailsWith<LibraryFormatException> { codec.encode(changed(tables = LibraryTables(emptyList(), original.tables.constants))) }
        val brokenConstant = LibraryModel(original.manifest, original.declarations,
            LibraryTables(original.tables.types, emptyList()), original.syntax)
        assertFailsWith<LibraryFormatException> { codec.encode(brokenConstant) }
        val wrongBody = LibrarySyntax(original.syntax.nodes, original.syntax.scopes,
            listOf(original.syntax.bodies.single().copy(owner = DeclarationId(1))))
        assertFailsWith<LibraryFormatException> { codec.encode(LibraryModel(original.manifest, original.declarations, original.tables, wrongBody)) }
        val unsupported = LibraryDeclarations(original.declarations.declarations + Provider(common("provider")), original.declarations.environments)
        assertEquals(LibraryFormatCode.UNSUPPORTED, assertFailsWith<LibraryFormatException> {
            codec.encode(changed(unsupported))
        }.code)
        val wire = LibraryWire.decode(codec.encode(original))
        val badManifest = ManifestCodec.encode(original.manifest.copy(rootDeclarations = listOf(DeclarationId(99))))
        val damaged = LibraryWire.encode(wire.sections.map {
            if (it.id == LibrarySectionId.MANIFEST) LibrarySection(it.id, true, badManifest) else it
        })
        assertFailsWith<LibraryFormatException> { codec.decode(damaged) }
    }

    @Test fun semanticsAbiAndTargetHaveCompatibilityDiagnosticsAndDependencyKeysStayUnbound() {
        val original = ordinary()
        val codec = LibraryModelCodec()
        fun withManifest(manifest: LibraryManifest) = LibraryModel(manifest, original.declarations, original.tables, original.syntax)
        assertEquals("LIB_SEMANTICS_UNSUPPORTED", assertFailsWith<LibraryFormatException> {
            codec.encode(withManifest(original.manifest.copy(semantics = "future/2")))
        }.code)
        assertEquals("LIB_RUNTIME_ABI", assertFailsWith<LibraryFormatException> {
            codec.encode(withManifest(original.manifest.copy(runtimeAbi = "future/2")))
        }.code)
        assertEquals("LIB_TARGET", assertFailsWith<LibraryFormatException> {
            codec.decode(codec.encode(original), LibraryCompatibility(minecraftVersion = "other"))
        }.code)
        val dependency = LibraryDependency(PackageKey("fixture:dependency", "1"), "main")
        val unknownKey = DeclarationKey("dependency", emptyList(), 2, "resolved_by_importer")
        val document = LibraryModel(original.manifest.copy(dependencies = listOf(dependency)), original.declarations,
            original.tables, original.syntax, runtime = listOf(RuntimeRecord.TagContribution(TagKey("fixture", "load"),
                listOf(DeclarationReference.Dependency(1, unknownKey)), false)))
        assertTrue(codec.decode(codec.encode(document)).requiresDependencyBinding)
        val badDependency = LibraryModel(document.manifest, original.declarations, original.tables, original.syntax,
            runtime = listOf(RuntimeRecord.TagContribution(TagKey("fixture", "load"),
                listOf(DeclarationReference.Dependency(2, unknownKey)), false)))
        assertEquals(LibraryFormatCode.CORRUPT, assertFailsWith<LibraryFormatException> { codec.encode(badDependency) }.code)
    }

    private data class Provider(override val common: DeclCommon, val providerClass: String = "fixture.Provider") : LibraryDeclaration { override val tag = 13 }
    private val providerHandler = object : DeclarationPayloadHandler {
        override val kinds = setOf(13)
        override fun collectStrings(declaration: LibraryDeclaration) = setOf((declaration as Provider).providerClass, "fixture.state")
        override fun writePayload(writer: LibraryBinaryWriter, declaration: LibraryDeclaration, strings: LibraryStrings) {
            require(declaration is Provider)
            with(writer) {
                bool(false); u32(strings.reference(declaration.providerClass)); u32(0)
                u32(strings.reference("fixture.state")); u32(1)
            }
        }
        override fun readPayload(reader: LibraryBinaryReader, tag: Int, common: DeclCommon, strings: LibraryStrings): LibraryDeclaration = with(reader) {
            optional { TypeTableCodec.readPackage(this) }
            val provider = strings.value(u32()); list { strings.value(u32()) }
            strings.value(u32()); u32()
            Provider(common, provider)
        }
        override fun validate(declaration: LibraryDeclaration, id: DeclarationId, context: LibraryModelValidation) { require(declaration is Provider) }
    }

    @Test fun portableStateAliasesCyclesRuntimeResourcesAndDebugRoundTripExplicitly() {
        val original = ordinary()
        val declarations = LibraryDeclarations(original.declarations.declarations + Provider(common("provider")), original.declarations.environments)
        val path = "datapack/library/data/fixture/function/increment.mcfunction"
        val annotations = listOf(AnnotationRecord(DeclarationReference.Local(DeclarationId(4)), listOf(ConstantRef(1)), emptyList(),
            AnnotationPhase.COMPLETED, PortableState("fixture.state", 1, listOf(
                StateField("first", PortableValue.Cell(StateCellRef(1))), StateField("second", PortableValue.Cell(StateCellRef(1)))))))
        val cells = listOf<StateCell>(StateCell.Sequence(listOf(PortableValue.Cell(StateCellRef(1)), PortableValue.Constant(ConstantRef(1)),
            PortableValue.Declaration(DeclarationReference.Local(DeclarationId(2))), PortableValue.Type(TypeRef(1)), PortableValue.Annotation(AnnotationId(1)))),
            StateCell.Map(listOf(StateMapEntry(ConstantRef(1), PortableValue.Cell(StateCellRef(1))))), StateCell.Set(listOf(ConstantRef(1))))
        val runtime = listOf<RuntimeRecord>(
            RuntimeRecord.FunctionBinding(DeclarationReference.Local(DeclarationId(2)), path, target, ConstantRef(1), RuntimePolicy.REANALYZABLE_FALLBACK),
            RuntimeRecord.TagContribution(TagKey("fixture", "load"), listOf(DeclarationReference.Local(DeclarationId(2))), false),
            RuntimeRecord.Scoreboard("fixture", "dummy", ConstantRef(1)), RuntimeRecord.Initialization(DeclarationReference.Local(DeclarationId(2)), 0))
        val resources = listOf(LibraryResource(path, 3uL, List(32) { 0.toByte() }, DeclarationReference.Local(DeclarationId(2)), "library", null, ResourceOrigin.PRECOMPILED))
        val debug = LibraryDebug(listOf("logical.mcfpp"), listOf(NodeLocation(NodeRef(1), 0, 0, 1, 1, 1)),
            listOf(DeclarationDocument(DeclarationId(2), "increment")), "test")
        val model = LibraryModel(original.manifest, declarations, original.tables, original.syntax, annotations, cells, runtime, resources, debug)
        val codec = LibraryModelCodec(listOf(providerHandler))
        val bytes = codec.encode(model)
        val decoded = codec.decode(bytes)
        assertEquals(model.annotations, decoded.annotations)
        assertEquals(model.stateCells, decoded.stateCells)
        assertEquals(model.runtime, decoded.runtime)
        assertEquals(model.resources, decoded.resources)
        assertEquals(debug, decoded.debug)
        assertContentEquals(bytes, codec.encode(decoded))
        val invalid = LibraryModel(model.manifest, declarations, model.tables, model.syntax, annotations,
            listOf(StateCell.Sequence(listOf(PortableValue.Cell(StateCellRef(99))))), runtime, resources)
        assertFailsWith<LibraryFormatException> { codec.encode(invalid) }
        assertEquals("LIB_RESOURCE_CONFLICT", assertFailsWith<LibraryFormatException> {
            codec.encode(LibraryModel(model.manifest, declarations, model.tables, model.syntax, annotations, cells, runtime, resources + resources))
        }.code)
    }
}
