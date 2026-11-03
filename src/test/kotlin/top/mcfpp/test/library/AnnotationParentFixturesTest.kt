package top.mcfpp.test.library

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.library.compiler.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.test.DataInheritanceLayoutRestoreTest
import top.mcfpp.test.DataInheritanceLayoutRestoreTest.GeneratingAnnotation
import top.mcfpp.test.DataInheritanceLayoutRestoreTest.Marker
import top.mcfpp.test.DataTemplateParentIdentityMetadataTest
import top.mcfpp.test.DataTemplateParentIdentityMetadataTest.ParentProbe
import top.mcfpp.test.IntersectionTemplateCompletionTest
import top.mcfpp.test.IntersectionTemplateCompletionTest.RestoreProbe
import kotlin.test.*

class AnnotationParentFixturesTest {
    private class Codec<A : Annotation>(
        override val annotationClass: Class<A>,
        private val capture: (A, PortableAnnotationWriter) -> List<StateField>,
        private val allocate: () -> A, private val restore: (A, PortableAnnotationReader) -> Unit
    ) : PortableAnnotationCodec<A> {
        override val schema = "fixture.parent.${annotationClass.name}"
        override val version = 1L
        override fun capture(value: A, writer: PortableAnnotationWriter) = capture.invoke(value, writer)
        override fun allocateShell(reader: PortableAnnotationReader) = allocate()
        override fun restore(value: A, reader: PortableAnnotationReader) = restore.invoke(value, reader)
    }
    companion object {
        internal val providerPackage = PackageKey("fixture:parent-codecs", "1")
        internal val sourceProviders = listOf(Marker::class.java, GeneratingAnnotation::class.java,
            ParentProbe::class.java, RestoreProbe::class.java).associate { it.name to providerPackage }
        internal val managed = object : ManagedNativeBindings {
            override fun loader(provider: PackageKey?) = AnnotationParentFixturesTest::class.java.classLoader
            override fun provider(owner: Class<*>) = sourceProviders[owner.name]
        }
        internal fun registry() = PortableAnnotationCodecs.builtins().apply {
            register(Codec(Marker::class.java, { _, _ -> emptyList() }, ::Marker, { _, _ -> }), providerPackage, managed)
            register(Codec(GeneratingAnnotation::class.java, { value, writer -> writer.fields("applications" to value.applications) },
                ::GeneratingAnnotation, { value, reader -> value.applications = reader.integer("applications") }), providerPackage, managed)
            register(Codec(ParentProbe::class.java, { value, writer -> writer.fields("applications" to value.applications) },
                ::ParentProbe, { value, reader -> value.applications = reader.integer("applications") }), providerPackage, managed)
            register(Codec(RestoreProbe::class.java, { value, writer -> writer.fields("applications" to value.applications) },
                ::RestoreProbe, { value, reader -> value.applications = reader.integer("applications") }), providerPackage, managed)
        }
    }

    @Test
    fun completedLayoutEffectsAndSharedMarkersBindFreshParentsWithoutReplayingGenerators() {
        Project.init()
        val fixture = DataInheritanceLayoutRestoreTest()
        val root = fixture.model("Root").also { fixture.field(it) }
        val middle = fixture.model("Middle", root)
        val generator = GeneratingAnnotation()
        middle.annotations.add(generator); middle.pendingDeclarationAnnotations.add(generator)
        fixture.complete(middle)
        val marker = Marker().apply { params.add("saved"); parents.add(this) }
        middle.scope.getVar("value")!!.annotations.addAll(listOf(marker, marker))
        val saved = middle.completedInheritedFields()
        val pending = GeneratingAnnotation()
        registry().use { codecs ->
            val snapshot = AnnotationMniFixturesTest.roundTrip(codecs, listOf(generator, marker, marker, pending)) {
                if (it === pending) AnnotationPhase.PENDING else AnnotationPhase.COMPLETED
            }
            generator.applications = 99; marker.params.add("writer-only")
            val arena = snapshot.restore(codecs)
            val completed = arena.annotation(snapshot.roots[0]) as GeneratingAnnotation
            val restoredMarker = arena.annotation(snapshot.roots[1]) as Marker
            val pendingGenerator = arena.annotation(snapshot.roots[3]) as GeneratingAnnotation
            assertEquals(1, completed.applications); assertEquals(0, pendingGenerator.applications)
            assertEquals(AnnotationPhase.COMPLETED, arena.phase(completed)); assertEquals(AnnotationPhase.PENDING, arena.phase(pendingGenerator))
            assertSame(restoredMarker, arena.annotation(snapshot.roots[2])); assertEquals(listOf("saved"), restoredMarker.params)
            assertSame(restoredMarker, restoredMarker.parents.single())
            saved.single().field.annotations.clear()
            saved.single().field.annotations.addAll(listOf(restoredMarker, restoredMarker))

            val freshRoot = fixture.model("Root").also { fixture.field(it) }
            val freshMiddle = fixture.model("Middle", freshRoot)
            fixture.field(freshMiddle, name = "generated_1")
            // Install the declaration initializer separately from the annotation fixture;
            // completion must retain it and leave the completed generator unscheduled.
            val initializer = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("17")))).expression()
            freshMiddle.preInit["generated_1"] = initializer
            freshMiddle.annotations.add(completed); freshMiddle.restoreInheritedFields(saved)
            repeat(2) { fixture.complete(freshMiddle) }
            val inherited = freshMiddle.scope.getVar("value") as MCInt
            assertTrue(inherited.isDataOnly)
            assertSame(freshRoot, inherited.declaredParentTemplate)
            assertSame(freshRoot, freshMiddle.scope.fieldOrigins.getValue("value").single().owner)
            assertSame(inherited.annotations.first(), inherited.annotations.last())
            assertSame(inherited.annotations.first(), inherited.annotations.first().parents.single())
            assertEquals(listOf("saved"), inherited.annotations.first().params)
            assertNotSame(restoredMarker, inherited.annotations.first())
            assertEquals(1, completed.applications)
            assertNotNull(freshMiddle.scope.getProperty("generated_1")); assertNull(freshMiddle.scope.getVar("generated_2"))
            assertEquals(setOf("generated_1"), freshMiddle.preInit.keys); assertSame(initializer, freshMiddle.preInit["generated_1"])
            val grand = fixture.model("Grand", freshMiddle).also { fixture.complete(it) }
            assertSame(freshRoot, grand.scope.getVar("value")!!.declaredParentTemplate)
            assertNotSame(inherited.annotations.first(), grand.scope.getVar("value")!!.annotations.first())
            val grandMarker = grand.scope.getVar("value")!!.annotations.first()
            assertSame(grandMarker, grandMarker.parents.single())
            inherited.annotations.first().params.add("middle-only")
            assertEquals(listOf("saved"), grand.scope.getVar("value")!!.annotations.first().params)

            val pendingTarget = fixture.model("Pending", freshRoot)
            pendingTarget.annotations.add(pendingGenerator); pendingTarget.pendingDeclarationAnnotations.add(pendingGenerator)
            repeat(2) { fixture.complete(pendingTarget) }
            assertEquals(1, pendingGenerator.applications)
            assertNotNull(pendingTarget.scope.getProperty("generated_1")); assertNull(pendingTarget.scope.getVar("generated_2"))
            assertTrue(pendingTarget.pendingDeclarationAnnotations.isEmpty())
            assertEquals(99, generator.applications); assertEquals(0, pending.applications)
            assertEquals(0, Project.errorCount)
        }
    }

    @Test
    fun portablePendingParentProbeKeepsTheExistingReadonlyParentBindingOrderAndCounts() {
        registry().use { codecs ->
            DataTemplateParentIdentityMetadataTest().portableReadonlyParents { original ->
                val snapshot = AnnotationMniFixturesTest.roundTrip(codecs, listOf(original)) { AnnotationPhase.PENDING }
                val arena = snapshot.restore(codecs)
                assertEquals(0, original.applications)
                assertEquals(AnnotationPhase.PENDING, arena.phase(snapshot.roots.single()))
                (arena.annotation(snapshot.roots.single()) as ParentProbe).also { assertEquals(0, it.applications) }
            }
        }
    }

    @Test
    fun portableCompletedIntersectionProbeKeepsTheExistingCanonicalParentsAndMemberLayout() {
        registry().use { codecs ->
            IntersectionTemplateCompletionTest().portableIntersection { original ->
                val snapshot = AnnotationMniFixturesTest.roundTrip(codecs, listOf(original)) { AnnotationPhase.COMPLETED }
                val arena = snapshot.restore(codecs)
                assertEquals(AnnotationPhase.COMPLETED, arena.phase(snapshot.roots.single()))
                (arena.annotation(snapshot.roots.single()) as RestoreProbe).also {
                    assertEquals(1, original.applications); assertEquals(1, it.applications); assertNotSame(original, it)
                }
            }
        }
    }
}
