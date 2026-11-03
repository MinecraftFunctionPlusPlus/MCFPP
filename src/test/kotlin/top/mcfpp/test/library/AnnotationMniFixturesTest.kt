package top.mcfpp.test.library

import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.LibraryModelCodec
import top.mcfpp.library.format.NativeCodec
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.mni.expand.CompilerStateJournal
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.test.MniAnnotationDeclarationStateTest
import top.mcfpp.test.MniAnnotationDeclarationStateTest.Generate
import top.mcfpp.test.MniDeclarationPreparationCompletionTest
import top.mcfpp.test.MniDeclarationPreparationCompletionTest.GenerateField
import top.mcfpp.test.MniScoreMappingTest
import top.mcfpp.test.MniScoreMappingTest.ScoreFieldAnnotation
import top.mcfpp.type.MCFPPType
import java.util.IdentityHashMap
import kotlin.test.*

class AnnotationMniFixturesTest {
    private object GenerateCodec : PortableAnnotationCodec<Generate> {
        override val annotationClass = Generate::class.java
        override val schema = "fixture.mni.generate"
        override val version = 1L
        // Payload has the SDK's finite scalar/CompilerValue/type/annotation/container domain.
        // Container cells preserve aliases with labels/second and finite cycles.
        override fun capture(value: Generate, writer: PortableAnnotationWriter): List<StateField> {
            val seen = IdentityHashMap<Any, Boolean>()
            fun closed(item: Any?) {
                when (item) {
                    null, is String, is Boolean, is Char, is Byte, is Short, is Int, is Long, is Float, is Double,
                    is CompilerValue, is MCFPPType, is Annotation -> Unit
                    else -> {
                        if (item.javaClass !in setOf<Class<*>>(ArrayList::class.java, LinkedHashMap::class.java, LinkedHashSet::class.java))
                            throw PortableAnnotationException("Generate state requires an explicitly supported value or closed container")
                        if (seen.put(item, true) != null) return
                        when (item) {
                            is List<*> -> item.forEach(::closed)
                            is Map<*, *> -> item.forEach { (key, value) -> closed(key); closed(value) }
                            is Set<*> -> item.forEach(::closed)
                        }
                    }
                }
            }
            closed(value.labels); closed(value.second); closed(value.payload)
            return writer.fields("fieldName" to value.fieldName, "applications" to value.applications, "fail" to value.fail,
                "labels" to value.labels, "second" to value.second, "payload" to value.payload)
        }
        override fun allocateShell(reader: PortableAnnotationReader) = Generate.restoreShell()
        @Suppress("UNCHECKED_CAST")
        override fun restore(value: Generate, reader: PortableAnnotationReader) {
            value.fieldName = reader.text("fieldName"); value.applications = reader.integer("applications")
            value.fail = reader.boolean("fail"); value.labels = reader.list("labels") as ArrayList<String>
            value.second = reader.list("second") as List<String>; value.payload = reader.value("payload")
        }
    }
    private object ScoreCodec : PortableAnnotationCodec<ScoreFieldAnnotation> {
        override val annotationClass = ScoreFieldAnnotation::class.java
        override val schema = "fixture.mni.score"
        override val version = 1L
        override val constructorParameters = listOf("java.lang.String")
        override fun capture(value: ScoreFieldAnnotation, writer: PortableAnnotationWriter) = writer.fields("objective" to value.portableObjective())
        override fun allocateShell(reader: PortableAnnotationReader) = ScoreFieldAnnotation(reader.text("objective"))
        override fun restore(value: ScoreFieldAnnotation, reader: PortableAnnotationReader) {
            require(value.portableObjective() == reader.text("objective"))
        }
    }
    private object CompletionCodec : PortableAnnotationCodec<GenerateField> {
        override val annotationClass = GenerateField::class.java
        override val schema = "fixture.mni.completion"
        override val version = 1L
        override fun capture(value: GenerateField, writer: PortableAnnotationWriter) = emptyList<StateField>()
        override fun allocateShell(reader: PortableAnnotationReader) = GenerateField()
        override fun restore(value: GenerateField, reader: PortableAnnotationReader) = Unit
    }

    private class Capture(private val phaseFor: (Annotation) -> AnnotationPhase) : AnnotationCaptureBindings {
        constructor(phase: AnnotationPhase) : this({ phase })
        val types = CompilerTypeTableCapture(object : CompilerTypeBindings {})
        val providers = linkedMapOf<AnnotationProviderBinding, DeclarationId>()
        override fun provider(binding: AnnotationProviderBinding) = DeclarationReference.Local(providers.getOrPut(binding) { DeclarationId(providers.size + 2L) })
        override fun constant(value: CompilerValue) = types.constant(value)
        override fun type(value: MCFPPType) = types.type(value)
        override fun declaration(value: Any): DeclarationReference = error("This fixture codec declares no compiler object payloads")
        override fun phase(value: Annotation) = phaseFor(value)
        fun model(captured: CapturedAnnotations): LibraryModel {
            val environment = EnvironmentId(1); val owner = DeclarationId(1)
            fun common(name: String, kind: Int, parent: DeclarationId?) = DeclCommon(DeclarationKey("fixture.mni", emptyList(), kind, name),
                null, name, parent, environment, DeclarationAccess.PUBLIC, 0)
            val records = providers.map { (binding, _) ->
                val name = binding.ownerClass.substringAfterLast('.').replace('$', '_')
                AnnotationProviderDeclaration(common(name, 13, owner), binding)
            }
            return LibraryModel(LibraryManifest(PackageKey("fixture:mni", "1"), "main", LibraryTarget("26.3"),
                entryEnvironments = listOf(environment), rootDeclarations = listOf(owner)),
                LibraryDeclarations(listOf(NamespaceDeclaration(common("fixture.mni", 1, null), providers.values.toList())) + records,
                    listOf(EnvironmentRecord("fixture.mni", emptyList(), owner))), types.snapshot(),
                LibrarySyntax(emptyList(), emptyList(), emptyList()), captured.annotations, captured.stateCells)
        }
    }
    private class Restore(model: LibraryModel) : AnnotationRestoreBindings {
        private val declarations = model.declarations
        private val types = CompilerTypeTableRestore(model.tables, object : CompilerTypeBindings {})
        override fun provider(reference: DeclarationReference) = (declarations.declaration((reference as DeclarationReference.Local).target) as AnnotationProviderDeclaration).binding
        override fun constant(reference: ConstantRef) = types.constant(reference)
        override fun type(reference: TypeRef) = types.type(reference)
        override fun declaration(reference: DeclarationReference): Any = error("No compiler object payloads")
    }
    internal class AnnotationFixtureBytes internal constructor(val model: LibraryModel, val roots: List<AnnotationId>) {
        fun restore(codecs: PortableAnnotationCodecs): RestoredAnnotations = codecs.restore(model.annotations, model.stateCells, Restore(model))
    }
    companion object {
        internal fun roundTrip(codecs: PortableAnnotationCodecs, roots: List<Annotation>,
                               phase: (Annotation) -> AnnotationPhase): AnnotationFixtureBytes {
            val capture = Capture(phase)
            val captured = codecs.capture(roots, capture)
            return AnnotationFixtureBytes(bytes(capture.model(captured)), captured.roots)
        }
        private val providerPackage = PackageKey("fixture:mni-codecs", "1")
        private val managed = object : ManagedNativeBindings {
            override fun loader(provider: PackageKey?) = AnnotationMniFixturesTest::class.java.classLoader
            override fun provider(owner: Class<*>) = providerPackage
        }
        private fun registry() = PortableAnnotationCodecs.builtins()
            .register(GenerateCodec, providerPackage, managed).register(ScoreCodec, providerPackage, managed)
            .register(CompletionCodec, providerPackage, managed)
        private fun bytes(model: LibraryModel) = LibraryModelCodec(listOf(NativeCodec)).let { it.decode(it.encode(model)) }
    }

    @Test
    fun registeredGenerateKeepsFiniteStateWithoutHostCallbacksAndStillSupportsRealPreparation() =
        MniAnnotationDeclarationStateTest().portableDeclarationState { namespace ->
            val source = Generate().apply { fieldName = "restored"; applications = 4; fail = true }
            val cycle = arrayListOf<Any?>().apply { add(this) }
            source.payload = linkedMapOf("labels" to source.labels, "cycle" to cycle, "fact" to CompilerValue.Text("saved"))
            val constructed = Generate.constructed
            registry().use { codecs ->
                val capture = Capture(AnnotationPhase.PENDING)
                val captured = codecs.capture(listOf(source, source), capture)
                val model = bytes(capture.model(captured))
                source.labels += "writer-only"; source.applications = 99
                val arenas = List(2) { codecs.restore(model.annotations, model.stateCells, Restore(model)) }
                val restored = arenas[0].annotation(captured.roots[0]) as Generate
                val other = arenas[1].annotation(captured.roots[0]) as Generate
                assertEquals(constructed, Generate.constructed); assertEquals(0, Generate.observed); assertEquals(0, Generate.callbacks)
                assertSame(restored, arenas[0].annotation(captured.roots[1]))
                assertEquals(AnnotationPhase.PENDING, arenas[0].phase(restored))
                assertEquals("restored", restored.fieldName); assertEquals(4, restored.applications); assertTrue(restored.fail)
                assertEquals(listOf("before"), restored.labels); assertSame(restored.labels, restored.second)
                val payload = restored.payload as Map<*, *>
                assertSame(restored.labels, payload["labels"])
                val restoredCycle = payload["cycle"] as List<*>
                assertSame(restoredCycle, restoredCycle.single()); assertEquals(CompilerValue.Text("saved"), payload["fact"])
                assertNotSame(restored.labels, other.labels)
                restored.labels += "reader-only"; assertEquals(listOf("before"), other.labels)
                restored.fail = false
                val target = DataTemplate("PortableTarget", namespace.identifier).apply {
                    declarationFile = null; namespace.scope.addTemplate(identifier, this, false)
                    pendingDeclarationAnnotations.add(restored)
                }
                val prepared = CompilerStateJournal.checkpoint().prepare { Project.registerTemplateContract(target) }
                assertEquals(1, Generate.observed); assertEquals(4, restored.applications)
                assertNull(target.scope.getVar("restored"))
                repeat(2) {
                    prepared.apply()
                    assertEquals(5, restored.applications); assertEquals(1, Generate.observed)
                    assertNotNull(target.scope.getVar("restored")); assertNotNull(target.scope.getProperty("restored"))
                    assertTrue(target.hasCompletedDeclaration)
                    prepared.rollback(); assertEquals(4, restored.applications); assertNull(target.scope.getVar("restored"))
                }
                source.payload = Any()
                assertFailsWith<PortableAnnotationException> { codecs.capture(listOf(source), Capture(AnnotationPhase.PENDING)) }
                assertEquals(constructed, Generate.constructed); assertEquals(0, Generate.callbacks)
            }
        }

    @Test
    fun restoredPositionalScoreArgumentReachesTheActualMniDeclarationCatalog() {
        registry().use { codecs ->
            MniScoreMappingTest().portableScoreFacts { original ->
                val snapshot = roundTrip(codecs, listOf(original)) { AnnotationPhase.COMPLETED }
                val arena = snapshot.restore(codecs)
                assertEquals(AnnotationPhase.COMPLETED, arena.phase(snapshot.roots.single()))
                arena.annotation(snapshot.roots.single()) as ScoreFieldAnnotation
            }
        }
    }

    @Test
    fun restoredPendingCompletionProviderUsesTheExistingCanonicalPreparationBarrierOnce() {
        registry().use { codecs ->
            MniDeclarationPreparationCompletionTest().portableCompletion { original ->
                val snapshot = roundTrip(codecs, listOf(original)) { AnnotationPhase.PENDING }
                val arena = snapshot.restore(codecs)
                assertEquals(AnnotationPhase.PENDING, arena.phase(snapshot.roots.single()))
                arena.annotation(snapshot.roots.single()) as GenerateField
            }
        }
    }
}
