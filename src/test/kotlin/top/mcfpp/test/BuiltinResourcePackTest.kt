package top.mcfpp.test

import top.mcfpp.io.BuiltinResourcePack
import top.mcfpp.io.LibBinReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import kotlin.test.Test

class BuiltinResourcePackTest {
    @Test fun packedAndUnpackedLibrariesHaveIdenticalSnapshots() {
        val root = Files.createTempDirectory("mcfpp-packed-library-")
        try {
            val prefix = "datapack/targets/1.21.8/"
            val files = linkedMapOf(
                "bin.mclib" to "binary".toByteArray(),
                "module.json" to "{}".toByteArray(),
                "datapack/example/data/example/function/repeated.mcfunction" to "say hi\nsay hi\n".toByteArray(),
                "datapack/example/data/example/function/empty.mcfunction" to byteArrayOf(),
            )
            val chunks = listOf("binary".toByteArray(), "{}".toByteArray(), "say hi\n".toByteArray())
            val ids = listOf(intArrayOf(0), intArrayOf(1), intArrayOf(2, 2), intArrayOf())
            val index = ByteArrayOutputStream().also { buffer ->
                DataOutputStream(buffer).use { out ->
                    out.writeInt(BuiltinResourcePack.MAGIC); out.writeInt(1); out.writeInt(files.size)
                    files.entries.forEachIndexed { i, (name, content) ->
                        out.writeUTF(prefix + name); out.writeInt(content.size); out.writeInt(ids[i].size)
                        ids[i].forEach(out::writeInt)
                    }
                }
            }.toByteArray()
            val archive = root.resolve("packed.jar")
            ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
                fun entry(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                }
                entry(BuiltinResourcePack.INDEX, index)
                chunks.forEachIndexed { id, bytes -> entry("${BuiltinResourcePack.ROOT}/$id", bytes) }
            }
            val directory = root.resolve("plain")
            files.forEach { (name, bytes) ->
                val path = directory.resolve(name)
                Files.createDirectories(path.parent); Files.write(path, bytes)
            }
            val expected = LibBinReader.payload(directory)
            URLClassLoader(arrayOf(archive.toUri().toURL()), null).use { loader ->
                val actual = LibBinReader.builtin(loader, "1.21.8")
                assertContentEquals(expected.binary, actual.binary)
                assertContentEquals(expected.moduleJson, actual.moduleJson)
                assertEquals(expected.resources.keys, actual.resources.keys)
                expected.resources.forEach { (name, bytes) -> assertContentEquals(bytes, actual.resources[name], name) }
            }
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test fun ordinaryResourcesNeedNoPackedIndex() {
        assertNull(BuiltinResourcePack.open(object : ClassLoader(null) {}))
    }

    @Test fun unknownPackedFormatFailsExplicitly() {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { it.writeInt(BuiltinResourcePack.MAGIC); it.writeInt(2) }
        }.toByteArray()
        val loader = object : ClassLoader(null) {
            override fun getResourceAsStream(name: String) = if (name == BuiltinResourcePack.INDEX) bytes.inputStream() else null
        }
        assertFailsWith<IllegalArgumentException> { BuiltinResourcePack.open(loader) }
    }
}
