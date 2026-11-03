package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.io.DatapackCreator
import top.mcfpp.library.api.LibraryExport
import top.mcfpp.library.compiler.LibraryExporter
import top.mcfpp.library.compiler.LibraryIdentity
import top.mcfpp.library.compiler.OwnedLibraryResource
import top.mcfpp.library.format.LibraryWire
import top.mcfpp.library.model.LibraryTarget
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibModuleCopyTest {
    @Test fun directoryIncludeCopiesBaseOnlyModuleResources() = checkCopy("directory")

    @Test fun zipIncludeCopiesBaseOnlyModuleResources() = checkCopy("zip")

    @Test fun jarIncludeCopiesBaseOnlyModuleResources() = checkCopy("jar")

    private fun checkCopy(format: String) {
        val output = Files.createTempDirectory("mcfpp-module-copy-")
        val config = Project.config
        val settings = listOf(CompileSettings.isDebug, CompileSettings.ignoreStdLib, CompileSettings.isLib,
            CompileSettings.printAll, CompileSettings.foldIRConstants)
        val maxInline = CompileSettings.maxWhileInline
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            val library = output.resolve("library")
            Files.createDirectories(library)
            MCFPPStringTest.readFromString("namespace fixture.module; func marker(){}",
                version = "26.3")
            assertEquals(0, Project.errorCount)
            Files.writeString(library.resolve("module.json"),
                """{"fixture":{"base":{"fixture.module":"resources"}}}""")
            val payloads = linkedMapOf(
                "function/marker.mcfunction" to "# copied library marker\nsay fixture-module\n",
                "tags/function/load.json" to "{\"values\":[\"fixture.module:marker\"]}\n"
            )
            for ((relative, content) in payloads) {
                val file = library.resolve("fixture/data/resources").resolve(relative)
                Files.createDirectories(file.parent)
                Files.writeString(file, content)
            }
            // The source loader preserves physical resource paths; module.base applies the
            // resources -> fixture.module alias when the module is linked into the consumer.
            val resources = payloads.map { (relative, content) ->
                OwnedLibraryResource("datapack/fixture/data/resources/$relative", content.toByteArray(Charsets.UTF_8), "fixture")
            }
            LibraryExport.write(LibraryExporter.snapshotCurrentProject(
                LibraryIdentity(PackageKey("fixture:module-copy", "1.0.0"), "mcfpp"), LibraryTarget("26.3"), resources),
                library.resolve("bin.mclib"))
            Files.newInputStream(library.resolve("bin.mclib")).use {
                assertEquals(LibraryWire.CURRENT_VERSION, LibraryWire.read(it).formatVersion)
            }
            val include = if (format == "directory") library else {
                output.resolve("fixture.$format").also { archive ->
                    ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
                        Files.walk(library).use { paths ->
                            paths.filter { Files.isRegularFile(it) }.forEach { file ->
                                val relative = library.relativize(file).joinToString("/") { it.toString() }
                                zip.putNextEntry(ZipEntry("datapack/$relative"))
                                Files.newInputStream(file).use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                }
            }
            Project.config = ProjectConfig().apply {
                includes = arrayListOf(include.toString())
                copyImport = true
            }
            val consumer = output.resolve("consumer")
            MCFPPStringTest.readFromString("func main(){\n/function #fixture.module:load\n}", targetPath = consumer.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            for ((relative, content) in payloads) {
                val copied = consumer.resolve(Project.config.name).resolve("data/fixture.module").resolve(relative)
                assertTrue(Files.isRegularFile(copied), "Missing $format module resource: $relative")
                assertEquals(content, Files.readString(copied))
            }
            Project.config.copyImport = false
            val withoutImports = output.resolve("consumer-without-imports")
            DatapackCreator.createDatapack(withoutImports.toString())
            assertFalse(Files.exists(withoutImports.resolve("Imports")))
            assertTrue(Files.isRegularFile(withoutImports.resolve(Project.config.name).resolve("pack.mcmeta")))
            assertEquals(0, Project.errorCount)
        } finally {
            Project.config = config
            CompileSettings.isDebug = settings[0]
            CompileSettings.ignoreStdLib = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            CompileSettings.maxWhileInline = maxInline
            output.toFile().deleteRecursively()
        }
    }
}
