package top.mcfpp.test.library

import top.mcfpp.library.compiler.*
import top.mcfpp.library.model.AnnotationPhase
import top.mcfpp.library.model.StateField
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.test.MniEmbeddedParentCompletionTest
import top.mcfpp.test.MniEmbeddedParentCompletionTest.CountApplications
import top.mcfpp.test.MniSourceEnvironmentTest
import top.mcfpp.test.MniSourceEnvironmentTest.GenerateField
import kotlin.test.*

class AnnotationEnvironmentFixturesTest {
    private object SourceCodec : PortableAnnotationCodec<GenerateField> {
        override val annotationClass = GenerateField::class.java
        override val schema = "fixture.source-environment.generate"
        override val version = 1L
        // The provider has no instance fields beyond Annotation's shared base state.
        // Its static observation counters belong to the live compiler operation.
        override fun capture(value: GenerateField, writer: PortableAnnotationWriter) = emptyList<StateField>()
        override fun allocateShell(reader: PortableAnnotationReader) = GenerateField()
        override fun restore(value: GenerateField, reader: PortableAnnotationReader) = Unit
    }
    private object ParentCodec : PortableAnnotationCodec<CountApplications> {
        override val annotationClass = CountApplications::class.java
        override val schema = "fixture.embedded-parent.counter"
        override val version = 1L
        override fun capture(value: CountApplications, writer: PortableAnnotationWriter) = writer.fields("applications" to value.applications)
        override fun allocateShell(reader: PortableAnnotationReader) = CountApplications()
        override fun restore(value: CountApplications, reader: PortableAnnotationReader) {
            value.applications = reader.integer("applications")
        }
    }
    companion object {
        private val providerPackage = PackageKey("fixture:environment-codecs", "1")
        private val managed = object : ManagedNativeBindings {
            override fun loader(provider: PackageKey?) = AnnotationEnvironmentFixturesTest::class.java.classLoader
            override fun provider(owner: Class<*>) = providerPackage
        }
        private fun registry() = PortableAnnotationCodecs.builtins()
            .register(SourceCodec, providerPackage, managed).register(ParentCodec, providerPackage, managed)
    }

    @Test
    fun restoredPendingSourceProviderCompletesBeforeTheActualMacroNativeAndOrdinaryCatalogRefresh() {
        registry().use { codecs ->
            MniSourceEnvironmentTest().portableSourceEnvironment { original ->
                original.params.add("source-state")
                val snapshot = AnnotationMniFixturesTest.roundTrip(codecs, listOf(original, original)) { AnnotationPhase.PENDING }
                original.params.add("writer-only")
                val arena = snapshot.restore(codecs)
                val restored = arena.annotation(snapshot.roots[0]) as GenerateField
                assertEquals(AnnotationPhase.PENDING, arena.phase(restored))
                assertNotSame(original, restored); assertSame(restored, arena.annotation(snapshot.roots[1]))
                assertEquals(listOf("source-state"), restored.params)
                val second = snapshot.restore(codecs).annotation(snapshot.roots[0]) as GenerateField
                assertNotSame(restored, second); assertNotSame(restored.params, second.params)
                restored.params.add("reader-only")
                assertEquals(listOf("source-state"), second.params)
                restored
            }
        }
    }

    @Test
    fun restoredPendingEmbeddedCounterKeepsOrderedCanonicalParentsAndCompletedCounterState() {
        registry().use { codecs ->
            MniEmbeddedParentCompletionTest().portableOrderedParents { original ->
                val completed = CountApplications().apply { applications = 7 }
                val snapshot = AnnotationMniFixturesTest.roundTrip(codecs, listOf(original, completed, original)) {
                    if (it === original) AnnotationPhase.PENDING else AnnotationPhase.COMPLETED
                }
                completed.applications = 99
                val arena = snapshot.restore(codecs)
                val restored = arena.annotation(snapshot.roots[0]) as CountApplications
                val completedState = arena.annotation(snapshot.roots[1]) as CountApplications
                assertSame(restored, arena.annotation(snapshot.roots[2])); assertNotSame(original, restored)
                assertEquals(AnnotationPhase.PENDING, arena.phase(restored))
                assertEquals(AnnotationPhase.COMPLETED, arena.phase(completedState))
                assertEquals(0, restored.applications); assertEquals(0, original.applications)
                assertEquals(7, completedState.applications)
                restored
            }
        }
    }
}
