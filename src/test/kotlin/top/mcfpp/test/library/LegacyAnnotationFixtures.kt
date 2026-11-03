package top.mcfpp.test.library

import top.mcfpp.Project
import top.mcfpp.library.api.LibraryExport
import top.mcfpp.library.api.LibraryImportSession
import top.mcfpp.library.compiler.*
import top.mcfpp.library.model.LibraryTarget
import top.mcfpp.library.model.StateField
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.test.DataInheritanceLibraryMetadataTest
import top.mcfpp.test.DataInheritanceLibraryMetadataTest.MetadataMarker
import top.mcfpp.test.DataInheritanceLibraryMetadataTest.GeneratingAnnotation
import top.mcfpp.test.DeclarationInitializationContractTest
import top.mcfpp.test.GenericTemplateAnnotationTest.HeaderProbe
import top.mcfpp.test.GenericTemplateAnnotationTest.ParentProbe
import top.mcfpp.test.GenericTemplateAnnotationTest.BodyProbe
import java.nio.file.Path
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Explicit schemas for these fixtures; container cells retain the annotations' shared list graph. */
internal object LegacyAnnotationFixtures {
    private val provider = PackageKey("fixture:legacy-annotation-providers", "1")
    private val providers = listOf(MetadataMarker::class.java, GeneratingAnnotation::class.java,
        HeaderProbe::class.java, ParentProbe::class.java, BodyProbe::class.java,
        DataInheritanceLibraryMetadataTest::class.java, DeclarationInitializationContractTest::class.java)
        .associate { it.name to provider }
    private val managed = object : ManagedNativeBindings {
        override fun loader(provider: PackageKey?) = LegacyAnnotationFixtures::class.java.classLoader
        override fun provider(owner: Class<*>) = providers[owner.name]
    }
    private class Codec<A : Annotation>(override val annotationClass: Class<A>,
        private val captureState: (A, PortableAnnotationWriter) -> List<StateField>,
        private val shell: () -> A, private val restoreState: (A, PortableAnnotationReader) -> Unit,
        override val constructorParameters: List<String> = emptyList(),
    ) : PortableAnnotationCodec<A> {
        override val schema = "fixture.legacy.${annotationClass.name}"
        override val version = 1L
        override fun capture(value: A, writer: PortableAnnotationWriter) = captureState(value, writer)
        override fun allocateShell(reader: PortableAnnotationReader) = shell()
        override fun restore(value: A, reader: PortableAnnotationReader) = restoreState(value, reader)
    }
    @Suppress("UNCHECKED_CAST")
    private fun registry() = PortableAnnotationCodecs.builtins().apply {
        register(Codec(MetadataMarker::class.java, { value, writer -> writer.fields("values" to value.values) }, ::MetadataMarker,
            { value, reader -> value.restorePortableValues(reader.list("values") as ArrayList<String>) }), provider, managed)
        register(Codec(GeneratingAnnotation::class.java, { value, writer -> writer.fields("applications" to value.applications) }, ::GeneratingAnnotation,
            { value, reader -> value.applications = reader.integer("applications") }), provider, managed)
        register(Codec(HeaderProbe::class.java, { value, writer -> writer.fields("applications" to value.applications,
            "environments" to value.environments, "shared" to value.shared, "nested" to value.nested) }, { HeaderProbe.restoreShell() },
            { value, reader -> value.restorePortableState(reader.integer("applications"), reader.list("environments") as ArrayList<String>,
                reader.list("shared") as ArrayList<String>, reader.list("nested") as ArrayList<ArrayList<String>>) }), provider, managed)
        register(Codec(ParentProbe::class.java, { value, writer -> writer.fields("applications" to value.applications, "shared" to value.shared) }, { ParentProbe.restoreShell() },
            { value, reader -> value.restorePortableState(reader.integer("applications"), reader.list("shared") as ArrayList<String>) },
            constructorParameters = listOf("java.util.ArrayList")), provider, managed)
        register(Codec(BodyProbe::class.java, { value, writer -> writer.fields("applications" to value.applications) }, ::BodyProbe,
            { value, reader -> value.applications = reader.integer("applications") }), provider, managed)
    }

    fun write(output: Path) {
        registry().use { codecs ->
            val selection = LibraryExporter.snapshotCurrentProject(LibraryIdentity(PackageKey("fixture:legacy-annotations", "1"), "mcfpp"),
                LibraryTarget(Project.config.version))
            LibraryExport.write(selection.copy(services = ExportSourceServices.create(selection, managed, codecs,
                providers::get, providers::containsKey)), output.resolve("bin.mclib"))
        }
    }

    /** Called at the existing source harness seam, before its real type/field/body visitors. */
    fun install(outputs: List<Path>) {
        Project.libraryImportSession?.let { previous ->
            assertTrue(previous.exportDependencies().isEmpty(), "Fixture setup must not detach an active dependency directory")
        }
        Project.closeLibraryImports()
        val session = LibraryImportSession()
        Project.libraryImportSession = session
        session.configureNative(managed, registry(), providers)
        outputs.forEach { session.stage(it.resolve("bin.mclib")) }
        session.bindAll(); session.commit()
        assertNotNull(Project.libraryImportSession)
    }

    fun close() = Project.closeLibraryImports()
}
