package top.mcfpp.test

import com.esotericsoftware.kryo.io.Input
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinFormat
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
                targetPath = library.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            Input(Files.newInputStream(library.resolve("bin.mclib"))).use {
                assertEquals(LibBinFormat.MAGIC, it.readInt())
                assertEquals(LibBinFormat.VERSION, it.readInt())
            }
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
            MCFPPStringTest.readFromString("func main(){}", targetPath = consumer.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            for ((relative, content) in payloads) {
                val copied = consumer.resolve("Imports/data/fixture.module").resolve(relative)
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
