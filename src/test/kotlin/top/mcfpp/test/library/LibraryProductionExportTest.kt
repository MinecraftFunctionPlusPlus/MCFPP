package top.mcfpp.test.library

import com.alibaba.fastjson2.JSON
import top.mcfpp.CompileSettings
import top.mcfpp.CompilerOptions
import top.mcfpp.LibraryPublicationConfig
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.api.library.LibraryCompilerApi
import top.mcfpp.io.LibBinWriter
import top.mcfpp.io.LibBinReader
import top.mcfpp.library.compiler.ManagedNativeBindings
import top.mcfpp.library.format.CompoundCodec
import top.mcfpp.library.format.LibraryModelCodec
import top.mcfpp.library.format.LibraryWire
import top.mcfpp.library.format.NativeCodec
import top.mcfpp.library.model.FunctionDeclaration
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.model.function.FunctionTag
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.*

class LibraryProductionExportTest {
    @Test fun repeatedProjectReadRetainsTheNativeOwnerUntilExplicitInitialization() = temporary { root ->
        val previousConfig = Project.config
        val previousClassLoader = Project.classLoader
        val previousIgnore = CompileSettings.ignoreStdLib
        val previousErrors = Project.errorCount
        val previousWarnings = Project.warningCount
        try {
            val producer = producer(root.resolve("producer"))
            assertSuccess(compile(producer))
            val payload = LibBinReader.payload(producer.parent.resolve("out"))
            Project.config = ProjectConfig(version = "26.3", root = root, rootNamespace = "consumer")
            Project.init()
            CompileSettings.ignoreStdLib = true
            Project.readProject(listOf(payload))
            val owner = assertNotNull(Project.libraryImportSession)
            assertTrue(owner.functions().any { it.identifier == "add" })
            val tags = FunctionTag.LOAD.functions.toList()

            // A caller may already have supplied the next host's inputs. A rejected read
            // must preserve both those inputs and the currently active library owner.
            val hostLoader = object : ClassLoader(javaClass.classLoader) {}
            val hostBindings = object : ManagedNativeBindings {
                override fun loader(provider: PackageKey?): ClassLoader = hostLoader
                override fun provider(owner: Class<*>): PackageKey? = null
            }
            val hostProviders = mapOf<String, PackageKey?>("fixture.Host" to null)
            Project.classLoader = hostLoader
            Project.libraryNativeBindings = hostBindings
            Project.librarySourceProviders = hostProviders
            val rejected = assertFailsWith<IllegalStateException> { Project.readProject(listOf(payload)) }
            assertTrue(rejected.message.orEmpty().contains("Project.init()"))
            assertSame(owner, Project.libraryImportSession)
            assertSame(hostLoader, Project.classLoader)
            assertSame(hostBindings, Project.libraryNativeBindings)
            assertSame(hostProviders, Project.librarySourceProviders)
            assertEquals(tags, FunctionTag.LOAD.functions.toList())
            assertTrue(owner.functions().any { it.identifier == "add" })

            Project.init()
            assertFailsWith<IllegalStateException> { owner.functions() }
            Project.readProject(listOf(payload))
            assertNotSame(owner, Project.libraryImportSession)
            assertTrue(assertNotNull(Project.libraryImportSession).functions().any { it.identifier == "add" })
        } finally {
            Project.closeCompilation()
            Project.classLoader = previousClassLoader
            Project.config = previousConfig
            CompileSettings.ignoreStdLib = previousIgnore
            Project.errorCount = previousErrors
            Project.warningCount = previousWarnings
        }
    }

    @Test fun projectConfigurationAcceptsOptionalPublicationAndValidatesItsIdentity() = temporary { root ->
        val previous = Project.errorCount
        try {
            Project.errorCount = 0
            val file = root.resolve("project.toml")
            top.mcfpp.test.util.writeProjectFixture(file, "{}")
            assertNull(Project.readConfig(CompilerOptions().apply { this.file = file.toFile() }).library)
            top.mcfpp.test.util.writeProjectFixture(file, """{"namespace":"language.math","library":{"coordinate":"example:math","version":"1.2.3"}}""")
            val config = Project.readConfig(CompilerOptions().apply { this.file = file.toFile() })
            assertEquals("language.math", config.rootNamespace)
            assertEquals(LibraryPublicationConfig("example:math", "1.2.3"), config.library)
            assertEquals(0, Project.errorCount)
            top.mcfpp.test.util.writeProjectFixture(file, """{"library":{"coordinate":42,"version":"1.2.3"}}""")
            Project.readConfig(CompilerOptions().apply { this.file = file.toFile() })
            assertTrue(Project.errorCount > 0)
            assertFailsWith<IllegalArgumentException> { LibraryPublicationConfig("Wrong", "1.2.3") }
            assertFailsWith<IllegalArgumentException> { LibraryPublicationConfig("example:math", "1.2") }
            assertFailsWith<IllegalArgumentException> { LibraryPublicationConfig("example:math", "1.2.3", "../bad") }
        } finally { Project.errorCount = previous }
    }

    @Test fun realCompilerApiPublishesV1AndAFreshConsumerUsesItsBodyAndStaticResources() = temporary { root ->
        val producer = producer(root.resolve("producer"))
        assertSuccess(compile(producer))
        val output = producer.parent.resolve("out")
        val binary = Files.readAllBytes(output.resolve("bin.mclib"))
        assertEquals(1L, LibraryWire.decode(binary).formatVersion)
        val model = LibraryModelCodec(listOf(CompoundCodec, NativeCodec)).decode(binary)
        assertEquals("example:math", model.manifest.library.coordinate)
        assertEquals("1.2.3", model.manifest.library.version)
        assertEquals("mcfpp", model.manifest.classifier)
        assertTrue(model.declarations.declarations.filterIsInstance<FunctionDeclaration>().any { it.common.name == "add" && it.body != null })
        val resources = resourceBytes(output)
        assertContentEquals("{\"values\":[]}".toByteArray(), resources.getValue("datapack/project/data/published/tags/function/extra.json"))
        val consumerRoot = root.resolve("consumer")
        val consumer = configuration(consumerRoot, """
            namespace consumer
            import published.math:*
            func main(){
                external answer=add(2,3)
                /function #published:extra
            }
        """.trimIndent(), false)
        val library = mapOf<String, Any>(
            "coordinate" to "example:math", "version" to "1.2.3", "classifier" to "mcfpp",
            "sha256" to MessageDigest.getInstance("SHA-256").digest(binary), "binary" to binary,
            "moduleJson" to Files.readAllBytes(output.resolve("module.json")), "resources" to resources,
        )
        assertSuccess(compile(consumer, listOf(library)))
        val generated = Files.walk(consumerRoot.resolve("out")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }
                .map { Files.readString(it) }.toList().joinToString("\n")
        }
        assertTrue(generated.contains("say library-production-body"), generated)
        assertTrue(Files.exists(consumerRoot.resolve("out/project/data/published/tags/function/extra.json")))
    }

    @Test fun repeatedProductionExportsAreDeterministicAndAFailedWritePreservesThePreviousArtifact() = temporary { root ->
        val config = producer(root.resolve("producer"))
        assertSuccess(compile(config))
        val output = config.parent.resolve("out")
        val before = artifactBytes(output)
        val unrelated = output.resolve("datapack/unrelated/data/other/value.txt")
        Files.createDirectories(unrelated.parent)
        Files.writeString(unrelated, "keep")
        assertSuccess(compile(config))
        assertEquals(before.keys, artifactBytes(output).filterKeys { !it.startsWith("datapack/unrelated/") }.keys)
        before.forEach { (path, bytes) -> assertContentEquals(bytes, Files.readAllBytes(output.resolve(path)), path) }
        assertEquals("keep", Files.readString(unrelated))
        val oldConfig = Project.config
        try {
            Project.config = ProjectConfig(name = "project", dataPath = root.resolve("missing-data"))
            assertFailsWith<IllegalArgumentException> { LibBinWriter.write(output.toString()) }
            before.forEach { (path, bytes) -> assertContentEquals(bytes, Files.readAllBytes(output.resolve(path)), path) }
            assertEquals("keep", Files.readString(unrelated))
        } finally { Project.config = oldConfig }
    }

    private fun producer(root: Path): Path {
        val config = configuration(root, """
            namespace published.math
            func add(left:int,right:int):int {
                /say library-production-body
                return left+right
            }
        """.trimIndent(), true)
        val resource = root.resolve("data/published/tags/function/extra.json")
        Files.createDirectories(resource.parent)
        Files.writeString(resource, "{\"values\":[]}")
        return config
    }

    private fun configuration(root: Path, source: String, library: Boolean): Path {
        Files.createDirectories(root.resolve("src"))
        Files.writeString(root.resolve("src/main.mcfpp"), source)
        val values = linkedMapOf<String, Any>(
            "namespace" to "language.default", "sourcePath" to "src", "targetPath" to "out", "version" to "26.3",
            "args" to if (library) listOf("-ignoreStdLib", "-isLib") else listOf("-ignoreStdLib"),
        )
        if (library) {
            values["library"] = mapOf("coordinate" to "example:math", "version" to "1.2.3")
            values["dataPath"] = "data"
        }
        return root.resolve("project.toml").also { top.mcfpp.test.util.writeProjectFixture(it, JSON.toJSONString(values)) }
    }

    private fun compile(config: Path, libraries: List<Map<String, Any>> = emptyList()): Map<String, Any> {
        val request = mapOf<String, Any>(
            "apiVersion" to 1, "configuration" to config, "libraries" to libraries,
            "lockedPackages" to libraries.map { mapOf("coordinate" to it.getValue("coordinate"), "version" to it.getValue("version"), "dependencies" to emptyList<Any>()) },
            "plugins" to emptyList<Any>(), "resourcePacks" to emptyList<Any>(), "datapacks" to emptyList<Any>(),
        )
        return LibraryCompilerApi.openSession(request).use { LibraryCompilerApi.compile(it) }
    }

    private fun assertSuccess(result: Map<String, Any>) = assertEquals(true, result["success"], result.toString())

    private fun resourceBytes(output: Path): Map<String, ByteArray> = artifactBytes(output).filterKeys { it.startsWith("datapack/") }

    private fun artifactBytes(output: Path): Map<String, ByteArray> = Files.walk(output).use { paths ->
        paths.filter { Files.isRegularFile(it) && (it.startsWith(output.resolve("datapack")) || it.fileName.toString() in setOf("bin.mclib", "module.json")) }
            .toList().associate { output.relativize(it).joinToString("/") to Files.readAllBytes(it) }
    }

    private fun temporary(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("mcfpp-production-export-")
        try { action(root) } finally { root.toFile().deleteRecursively() }
    }
}
