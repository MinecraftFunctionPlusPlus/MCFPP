package top.mcfpp.test

import top.mcfpp.util.Module
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class ModuleResourcesTest {
    @Test fun snapshotMatchesDirectoryExtractionAndEnabledPackages() {
        val root = Files.createTempDirectory("mcfpp-module-resources-")
        try {
            val snapshot = linkedMapOf(
                "datapack/library/data/base/function/shared.mcfunction" to "base".toByteArray(),
                "datapack/library/data/base/function/main.mcfunction" to "main".toByteArray(),
                "datapack/library/data/optional/nested/function/shared.mcfunction" to "override".toByteArray(),
                "datapack/other/data/base/function/ignored.mcfunction" to "other module".toByteArray(),
            )
            snapshot.forEach { (name, bytes) ->
                val file = root.resolve(name)
                Files.createDirectories(file.parent)
                Files.write(file, bytes)
            }
            val module = Module("library").apply {
                resourcePath = root.resolve("datapack")
                base.add(Module.Companion.Package("target").apply { provides = listOf("base") })
                packages[Module.Companion.Package("target").apply { provides = listOf("optional/nested") }] = false
            }
            fun compare(enabled: Boolean) {
                module.resourceSnapshot = null
                val directory = root.resolve("extracted-$enabled")
                module.extract(directory)
                val expected = read(directory)
                module.resourceSnapshot = snapshot
                val actual = assertNotNull(module.resources())
                assertEquals(expected.keys, actual.keys)
                expected.forEach { (name, bytes) -> assertContentEquals(bytes, actual[name], name) }
                val output = root.resolve("snapshot-$enabled")
                module.extract(output)
                read(output).forEach { (name, bytes) -> assertContentEquals(expected[name], bytes, name) }
                assertEquals(if (enabled) "override" else "base", actual.getValue("target/function/shared.mcfunction").decodeToString())
            }
            compare(false)
            module.packages[module.packages.keys.single()] = true
            compare(true)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun replacingSnapshotRemovesSuppressedResources() {
        val path = "datapack/library/data/source/function/saved.mcfunction"
        val module = Module("library").apply {
            base.add(Module.Companion.Package("target").apply { provides = listOf("source") })
            resourceSnapshot = mapOf(path to "saved".toByteArray())
        }
        assertEquals(setOf("target/function/saved.mcfunction"), module.resources()!!.keys)
        module.resourceSnapshot = emptyMap()
        assertTrue(module.resources()!!.isEmpty())
    }

    private fun read(directory: Path): Map<String, ByteArray> = Files.walk(directory).use { paths ->
        paths.filter(Files::isRegularFile).toList().associate {
            directory.relativize(it).joinToString("/") to Files.readAllBytes(it)
        }
    }
}
