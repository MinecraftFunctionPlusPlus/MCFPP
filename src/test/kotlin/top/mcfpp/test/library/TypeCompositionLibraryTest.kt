package top.mcfpp.test.library

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCInt
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.io.KryoManager
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.AbstractFunctionInfo
import top.mcfpp.io.info.TemplateFactoryInfo
import top.mcfpp.library.compiler.*
import top.mcfpp.library.api.LibraryExport
import top.mcfpp.library.api.LibraryImportSession
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.TemplateFactories
import top.mcfpp.model.function.TemplateFactory
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import java.nio.file.Files
import kotlin.test.*

class TypeCompositionLibraryTest {
    private val noBindings = object : CompilerTypeBindings {}

    private fun binary(tables: LibraryTables): LibraryTables {
        val strings = LibraryStrings.of(TypeTableCodec.collectStrings(tables))
        return TypeTableCodec.decode(TypeTableCodec.encodeTypes(tables.types, strings),
            TypeTableCodec.encodeConstants(tables.constants, strings), strings)
    }

    private fun kryo(value: Any): Any {
        val output = Output(4096, -1)
        KryoManager.kryo.writeClassAndObject(output, value)
        return KryoManager.kryo.readClassAndObject(Input(output.toBytes()))
    }

    @Test fun normalizedCompositionsKeepCanonicalIdentityThroughPublishedTypeTables() {
        val one = MCFPPLiteralType(MCFPPBaseType.Int, CompilerValue.Integral(1))
        val shape = MCFPPObjectType(listOf(ObjectFieldContract("x", one),
            ObjectFieldContract("absent", MCFPPNeverType, optional = true)))
        val roots = listOf(shape, one, MCFPPNeverType,
            TypeCompositions.union(one, MCFPPLiteralType(MCFPPBaseType.Int, CompilerValue.Integral(2))),
            TypeCompositions.intersection(shape, MCFPPObjectType(listOf(ObjectFieldContract("enabled", MCFPPBaseType.Bool)))))
        val capture = CompilerTypeTableCapture(noBindings)
        val references = roots.map(capture::type)
        val canonical = capture.finish(object : CanonicalIdentityBindings {})
        val restore = CompilerTypeTableRestore(binary(canonical.tables), noBindings)
        roots.zip(references).forEach { (original, reference) ->
            val current = canonical.typeReferences.getValue(reference)
            assertEquals(original.typeId, restore.identity(current))
            assertEquals(original.typeId, restore.type(current).typeId)
        }
        val result = assertIs<MCFPPObjectType>(restore.type(canonical.typeReferences.getValue(references.first())))
        assertTrue(result.fieldsByName.getValue("absent").optional)
        assertSame(MCFPPNeverType, result.fieldsByName.getValue("absent").type)
        assertTrue(canonical.tables.types.any { it is LibraryType.Object })
        assertTrue(canonical.tables.types.any { it is LibraryType.Literal })
    }

    @Test fun declarationDescriptorsPreserveFieldKindAndProvenance() {
        val origin = TypeId.Declaration("template", "fixture.composition", "Origin")
        val literal = MCFPPLiteralType(MCFPPBaseType.String, CompilerValue.Text("tag"))
        val original = MCFPPObjectType(listOf(ObjectFieldContract("tag", literal,
            kind = ObjectMemberKind.CONST, origins = setOf(origin)),
            ObjectFieldContract("missing", MCFPPNeverType, optional = true)))
        val restored = assertIs<MCFPPObjectType>(kryo(original))
        assertEquals(original.typeId, restored.typeId)
        assertEquals(ObjectMemberKind.CONST, restored.fieldsByName.getValue("tag").kind)
        assertEquals(setOf(origin), restored.fieldsByName.getValue("tag").origins)
        assertEquals(literal.value, assertIs<MCFPPLiteralType>(restored.fieldsByName.getValue("tag").type).value)
        assertSame(MCFPPNeverType, restored.fieldsByName.getValue("missing").type)
    }

    @Test fun equalDataIdentitiesKeepDistinctAccessorCapabilitiesThroughBothFormats() {
        val readOnly = MCFPPObjectType(listOf(ObjectFieldContract("value", MCFPPBaseType.Int, writable = false)))
        val writeOnly = MCFPPObjectType(listOf(ObjectFieldContract("value", MCFPPBaseType.Int, readable = false)))
        val mutable = MCFPPObjectType(listOf(ObjectFieldContract("value", MCFPPBaseType.Int)))
        assertEquals(readOnly.typeId, writeOnly.typeId)
        assertEquals(readOnly.typeId, mutable.typeId)
        val capture = CompilerTypeTableCapture(noBindings)
        val references = listOf(readOnly, writeOnly, mutable).map(capture::type)
        val listReference = capture.type(MCFPPListType(readOnly))
        assertEquals(3, references.distinct().size)
        val canonical = capture.finish(object : CanonicalIdentityBindings {})
        val restore = CompilerTypeTableRestore(binary(canonical.tables), noBindings)
        val restored = references.map { assertIs<MCFPPObjectType>(restore.type(canonical.typeReferences.getValue(it))) }
        assertEquals(listOf(true to false, false to true, true to true), restored.map {
            it.fieldsByName.getValue("value").let { field -> field.readable to field.writable }
        })
        val nested = assertIs<MCFPPListType>(restore.type(canonical.typeReferences.getValue(listReference)))
        assertFalse(assertIs<MCFPPObjectType>(nested.generic.single()).fieldsByName.getValue("value").writable)
        assertFalse(assertIs<MCFPPObjectType>(kryo(readOnly)).fieldsByName.getValue("value").writable)
        assertFalse(assertIs<MCFPPObjectType>(kryo(writeOnly)).fieldsByName.getValue("value").readable)
    }

    @Test fun factoryMetadataRestoresItsOwnerAndDoesNotGenerateASecondDefault() {
        val owner = DataTemplate("FactoryOwner", "fixture.composition")
        TemplateFactories.ensureImplicit(owner)
        val original = owner.factories.single()
        val info = assertIs<TemplateFactoryInfo>(kryo(AbstractFunctionInfo.from(original)))
        val fresh = DataTemplate("FactoryOwnerRestored", "fixture.composition")
        val copied = info.get(fresh)
        fresh.scope.addFunction(copied, true)
        TemplateFactories.ensureImplicit(fresh)
        assertSame(fresh, copied.template)
        assertTrue(copied.isStatic && copied.isImplicit)
        assertEquals(fresh.getType().typeId, copied.returnType.typeId)
        assertEquals(1, fresh.factories.size)

        val type = TypeRef(1)
        val common = DeclCommon(null, null, "new", DeclarationId(1), EnvironmentId(1), DeclarationAccess.PUBLIC,
            DeclarationFlags.STATIC or DeclarationFlags.FACTORY or DeclarationFlags.IMPLICIT_FACTORY)
        val declaration = FunctionDeclaration(common, FunctionExecutionKind.ORDINARY, null, emptyList(), emptyList(), type, BodyId(1), null)
        val declarations = LibraryDeclarations(listOf(declaration), listOf(EnvironmentRecord("fixture.composition", emptyList())))
        val strings = LibraryStrings.of(FunctionCodec.collectStrings(declarations))
        val decoded = assertIs<FunctionDeclaration>(FunctionCodec.decodeDeclarations(FunctionCodec.encodeDeclarations(listOf(declaration), strings), strings).single())
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("{ return {} }"))))
        val body = parser.curlBlock()
        assertEquals(0, parser.numberOfSyntaxErrors)
        val publishedOwner = DataTemplate("PublishedOwner", "fixture.composition")
        val restored = FunctionDeclarations.restoreHead(DeclarationId(2), decoded, { error("No parameters") }, object : FunctionRestoreBindings {
            override fun type(reference: TypeRef) = publishedOwner.getType()
            override fun constant(reference: ConstantRef) = error("No constants")
            override fun environment(reference: EnvironmentId) = MCFPPFile(Namespace("fixture.composition"))
            override fun owner(reference: DeclarationId) = publishedOwner
            override fun nativeMethod(binding: NativeBinding) = error("No native method")
            override fun body(reference: BodyId) = body
        }).function
        val factory = assertIs<TemplateFactory>(restored)
        publishedOwner.scope.addFunction(factory, true)
        TemplateFactories.ensureImplicit(publishedOwner)
        assertSame(publishedOwner, factory.template)
        assertTrue(factory.isStatic && factory.isImplicit)
        assertEquals(1, publishedOwner.factories.size)
    }

    @Test fun stalePublishedDataSemanticsAreRejectedBeforeReadingPayloads() {
        val sections = LibrarySectionId.requiredIds.map { LibrarySection(it, true, byteArrayOf()) }
        val bytes = LibraryWire.encode(sections)
        bytes[8] = 0
        bytes[9] = 0
        bytes[10] = 0
        bytes[11] = 3
        val failure = assertFailsWith<LibraryFormatException> { LibraryModelCodec().decode(bytes) }
        assertEquals(LibraryFormatCode.UNSUPPORTED, failure.code)
    }

    @Test fun objectTypeSyntaxAndObjectShorthandSurvivePortableSyntaxRoundtrip() {
        fun parser(source: String) = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
        val types = CompilerTypeTableCapture(noBindings)
        val capture = ExpressionBridge(object : ExpressionCaptureBindings {
            override fun scope(site: ParserRuleContext) = ScopeId(1)
            override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext) =
                NameUse.Lookup(name, namespace, EnvironmentId(1), emptyList())
            override fun builtinType(key: String, site: ParserRuleContext) = types.type(LibraryBuiltinTypes.restore(key))
            override fun internConstant(value: ConstantValue, site: ParserRuleContext): ConstantRef = error("No literal")
        })
        val sourceType = parser("{ x: int, nested: { y: string } }")
        val objectType = capture.type(sourceType.type())
        assertEquals(0, sourceType.numberOfSyntaxErrors)
        val sourceObject = parser("{ x, other }")
        val objectValue = capture.expression(sourceObject.expression())
        assertEquals(0, sourceObject.numberOfSyntaxErrors)
        val syntax = SyntaxCodec.flatten(listOf(ScopeRecord(DeclarationId(1), null, emptyList())), emptyList(),
            listOf(SyntaxRoot(DeclarationId(1), listOf(0), objectType), SyntaxRoot(DeclarationId(1), listOf(1), objectValue)))
        val strings = LibraryStrings.of(SyntaxCodec.collectStrings(syntax))
        val decoded = SyntaxCodec.decodeUnbound(EncodedSyntax(SyntaxCodec.encodeNodes(syntax, strings),
            SyntaxCodec.encodeScopes(syntax.scopes), SyntaxCodec.encodeBodies(syntax.bodies)), strings)
        val restoredType = assertIs<TypeSyntax.Object>(decoded.nodes.first())
        assertEquals(listOf("x", "nested"), restoredType.fields.map { it.name })
        assertIs<TypeSyntax.Object>(restoredType.fields.last().type)
        val restoredValue = decoded.nodes.filterIsInstance<Expression.Compound>().single()
        assertEquals(listOf("x", "other"), restoredValue.entries.map { it.key })
        assertEquals("x", assertIs<NameUse.Lookup>(assertIs<Expression.Name>(restoredValue.entries.first().value).use).name)
        val projection = ExpressionProjection(object : ProjectionBindings {
            override fun name(use: NameUse, role: NameRole) = (use as NameUse.Lookup).let { ProjectedName(it.name, it.namespace) }
            override fun constant(reference: ConstantRef): ConstantValue = error("No literal")
        })
        val projected = projection.type(restoredType)
        assertNotNull(projected.typeBody().literalObjectType())
        assertEquals("{x:int,nested:{y:string}}", projection.source.text)
    }

    @Test fun sourceFactoryOverloadsSurviveLibraryExportAndFreshImport() {
        val config = Project.config
        val settings = listOf(CompileSettings.ignoreStdLib, CompileSettings.isDebug, CompileSettings.isLib, CompileSettings.printAll)
        val directory = Files.createTempDirectory("mcfpp-factory-library-")
        val path = directory.resolve("bin.mclib")
        try {
            Project.config = ProjectConfig()
            MCFPPStringTest.readFromString("""
                namespace fixture.factorylibrary
                data Empty {}
                abstract data Constants { const ratio: float = 1.25 }
                data Value {
                    var x: int = 2
                    new(x: int) { return { x } }
                }
                data Derived: Value {
                    var extra: int
                    new(x: int, extra: int) {
                        var parent = Value.new(x)
                        return { x: parent.x, extra }
                    }
                    func total(): int { return this.x + this.extra }
                }
                data Generic<T> {
                    var value: T
                    new(value: T) { return { value } }
                }
                data ItValue {
                    var it: int
                    new(it: int = 7) { return { it } }
                }
                func main() {}
            """.trimIndent(), arrayOf("-ignoreStdLib"), version = "26.3")
            assertEquals(0, Project.errorCount)
            val scope = GlobalScope.localNamespaces.getValue("fixture.factorylibrary").scope
            val file = assertNotNull(scope.functions.getValue("main").single().declarationFile)
            val identity = LibraryIdentity(PackageKey("fixture:factory-library", "1"), "main")
            LibraryExport.write(OwnedLibrarySelection(identity, LibraryTarget("26.3"), emptyList(), listOf(file),
                templates = listOf(scope.template.getValue("Empty"), scope.template.getValue("Value"), scope.template.getValue("Derived"), scope.template.getValue("Constants"),
                    assertNotNull(scope.getTemplate("Generic")), scope.template.getValue("ItValue"))), path)
            val model = LibraryModelCodec(listOf(CompoundCodec)).decode(Files.readAllBytes(path))
            val factories = model.declarations.declarations.filterIsInstance<FunctionDeclaration>()
                .filter { it.common.flags and DeclarationFlags.FACTORY != 0L }
            assertEquals(6, factories.size)
            assertEquals(2, factories.count { it.common.flags and DeclarationFlags.IMPLICIT_FACTORY != 0L })
            assertTrue(factories.all { it.common.flags and DeclarationFlags.STATIC != 0L })
            val ratio = model.declarations.declarations.filterIsInstance<ValueDeclaration>().single { it.common.name == "ratio" }
            val literal = assertIs<LibraryType.Literal>(model.tables.type(assertNotNull(ratio.type)))
            assertEquals(LibraryType.Builtin("float"), model.tables.type(literal.primitive))
            Project.init()
            LibraryImportSession().use { session ->
                session.stage(path, identity)
                session.bindAll()
                session.commit()
                val restored = GlobalScope.libNamespaces.getValue("fixture.factorylibrary").scope
                assertEquals(1, restored.template.getValue("Empty").factories.size)
                val overloads = restored.template.getValue("Value").factories
                assertEquals(2, overloads.size)
                assertEquals(1, overloads.count { it.isImplicit })
                assertTrue(overloads.all { it.isStatic && it.returnType.typeId == restored.template.getValue("Value").getType().typeId })
                val derived = restored.template.getValue("Derived")
                val derivedFactory = derived.factories.single()
                assertFalse(derivedFactory.isImplicit)
                assertEquals(2, derivedFactory.normalParams.size)
                assertEquals(derived.getType().typeId, derivedFactory.returnType.typeId)
                assertFalse(derived.scope.functions.getValue("total").single().isStatic)
                val generic = assertIs<GenericDataTemplate>(restored.getTemplate("Generic"))
                val specialized = assertNotNull(generic.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))))
                val factory = specialized.factories.single()
                assertSame(specialized, factory.template)
                assertEquals(specialized.getType().typeId, factory.returnType.typeId)
                assertEquals(MCFPPBaseType.Int.typeId, factory.normalParams.single().type.typeId)
                val defaulted = restored.template.getValue("ItValue").factories.single().normalParams.single()
                assertTrue(defaulted.hasDefault)
                assertEquals(setOf(MCFPPBaseType.Int.typeId), defaulted.defaultTypes.keys)
            }
            LibraryImportSession().use { session ->
                MCFPPStringTest.readFromString("""
                    namespace fixture.factoryconsumer
                    import fixture.factorylibrary:*
                    func main() {
                        var objectValue = ItValue(7)
                        external stored = objectValue.it
                        var genericValue = Generic<int>(9)
                        external genericStored = genericValue.value
                        var derivedValue = Derived.new(11, 4)
                        external derivedX = derivedValue.x
                        external derivedExtra = derivedValue.extra
                        external derivedTotal = derivedValue.total()
                    }
                """.trimIndent(), arrayOf("-ignoreStdLib"), version = "26.3", afterFileCreated = {
                    session.stage(path, identity)
                    session.bindAll()
                    session.commit()
                })
                assertEquals(0, Project.errorCount)
                val main = GlobalScope.localNamespaces.getValue("fixture.factoryconsumer").scope.functions.getValue("main").single()
                val functions = (GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() } + session.functions())
                    .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
                    Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }
                val machine = ScoreCommandExecutor(main.commands.analyzeAll(), functions)
                assertEquals(7, machine.read(assertIs<MCInt>(main.scope.getVar("stored"))))
                assertEquals(9, machine.read(assertIs<MCInt>(main.scope.getVar("genericStored"))))
                assertEquals(11, machine.read(assertIs<MCInt>(main.scope.getVar("derivedX"))))
                assertEquals(4, machine.read(assertIs<MCInt>(main.scope.getVar("derivedExtra"))))
                assertEquals(15, machine.read(assertIs<MCInt>(main.scope.getVar("derivedTotal"))))
            }
        } finally {
            MCFPPFile.currFile = null
            Project.config = config
            CompileSettings.ignoreStdLib = settings[0]; CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]; CompileSettings.printAll = settings[3]
            Files.deleteIfExists(path); Files.deleteIfExists(directory)
        }
    }
}
