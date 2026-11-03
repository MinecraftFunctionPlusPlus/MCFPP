package top.mcfpp.test.library

import top.mcfpp.library.mcfpm.McfpmBridgeException
import top.mcfpp.library.mcfpm.McfpmInstalledProjectBridge
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LibraryMcfpmBridgeTest {
    @Test
    fun actualSdkPreparesTheInstalledCacheThroughJdkMaps() = withInstalledProject { fixture ->
        val prepared = prepare(fixture)
        val request = prepared.request
        assertEquals(1, request["apiVersion"])
        assertEquals(fixture.configuration, request["configuration"])
        assertSame(javaClass.classLoader, request["hostClassLoader"])
        val library = (request["libraries"] as List<*>).single() as Map<*, *>
        assertEquals("test:library", library["coordinate"])
        assertEquals("1.0.0", library["version"])
        assertEquals("mcfpp", library["classifier"])
        assertContentEquals(fixture.digest, library["sha256"] as ByteArray)
        assertContentEquals(byteArrayOf(1, 2, 3), library["binary"] as ByteArray)
        assertContentEquals("{}".toByteArray(), library["moduleJson"] as ByteArray)
        val resources = library["resources"] as Map<*, *>
        assertContentEquals(byteArrayOf(4), resources["datapack/library/data/value.txt"] as ByteArray)
        val locked = (request["lockedPackages"] as List<*>).single() as Map<*, *>
        assertEquals("test:library", locked["coordinate"])
        assertEquals(emptyList<Any>(), locked["dependencies"])
        listOf("plugins", "resourcePacks", "datapacks").forEach { assertEquals(emptyList<Any>(), request[it]) }
        assertTrue(prepared.diagnostics.isEmpty())
        assertJdkValue(request)
        assertContentEquals(fixture.lockBytes, Files.readAllBytes(fixture.lock))
    }

    @Test
    fun missingInstalledCachePreservesSdkDiagnosticsAndLock() = withInstalledProject { fixture ->
        Files.delete(fixture.cached)
        val failure = assertFailsWith<McfpmBridgeException> { prepare(fixture) }
        assertEquals("MCFPM-CACHE-001", failure.diagnostics.single()["code"])
        val context = failure.diagnostics.single()["context"] as Map<*, *>
        assertEquals("test:library", context["coordinate"])
        assertEquals("1.0.0", context["version"])
        assertTrue(failure.message.orEmpty().contains("mcfpm install"))
        assertContentEquals(fixture.lockBytes, Files.readAllBytes(fixture.lock))
        assertFalse(Files.exists(fixture.cached))
    }

    @Test
    fun corruptInstalledCacheFailsWithoutReplacingItOrUpdatingLock() = withInstalledProject { fixture ->
        Files.write(fixture.cached, byteArrayOf(9, 9, 9))
        val failure = assertFailsWith<McfpmBridgeException> { prepare(fixture) }
        assertTrue(failure.message.orEmpty().contains("mcfpm install"))
        assertContentEquals(fixture.lockBytes, Files.readAllBytes(fixture.lock))
        assertFalse(Files.exists(fixture.cached)) // The SDK removes corrupt objects even offline.
    }

    @Test
    fun missingLockHasAnInstallDiagnostic() = withInstalledProject { fixture ->
        Files.delete(fixture.lock)
        val failure = assertFailsWith<McfpmBridgeException> { prepare(fixture) }
        assertTrue(failure.message.orEmpty().contains("Lockfile does not exist"))
        assertTrue(failure.message.orEmpty().contains("mcfpm install"))
        assertFalse(Files.exists(fixture.lock))
    }

    @Test
    fun absentSdkFacadeHasAnActionableAbiDiagnostic() = withInstalledProject { fixture ->
        val bridge = McfpmInstalledProjectBridge(object : ClassLoader(ClassLoader.getPlatformClassLoader()) {})
        val failure = assertFailsWith<McfpmBridgeException> {
            bridge.prepare(fixture.configuration, javaClass.classLoader, fixture.cache, fixture.trust)
        }
        assertEquals("MCFPM-MCFPP-001", failure.diagnostics.single()["code"])
        assertTrue(failure.cause is ClassNotFoundException)
        assertTrue(failure.message.orEmpty().contains("compatible Mcfpm SDK"))
        assertContentEquals(fixture.lockBytes, Files.readAllBytes(fixture.lock))
    }

    private fun prepare(fixture: Fixture) = McfpmInstalledProjectBridge().prepare(
        fixture.configuration, javaClass.classLoader, fixture.cache, fixture.trust,
    )

    private fun assertJdkValue(value: Any?) {
        when (value) {
            is String, is Int, is Long, is Boolean, is ByteArray, is Path, is ClassLoader -> Unit
            is List<*> -> value.forEach(::assertJdkValue)
            is Map<*, *> -> value.forEach { (key, entry) -> assertTrue(key is String); assertJdkValue(entry) }
            else -> error("SDK implementation value crossed the JDK ABI: ${value?.javaClass}")
        }
    }

    private fun withInstalledProject(block: (Fixture) -> Unit) {
        val directory = Files.createTempDirectory("mcfpp-installed-bridge").toAbsolutePath().normalize()
        try {
            val configuration = top.mcfpp.test.util.writeProjectFixture(directory.resolve("mcfpp.toml"), "{}")
            Files.writeString(directory.resolve("mcfpm.toml"), """
                schema = 1
                [package]
                id = "test:consumer"
                version = "1.0.0"
                license = "MIT"
                [dependencies]
                "test:library" = "^1.0.0"
            """.trimIndent())
            // These opaque bytes exercise transport only; compilation belongs to CLI integration.
            val payload = ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    listOf("bin.mclib" to byteArrayOf(1, 2, 3), "module.json" to "{}".toByteArray(),
                        "datapack/library/data/value.txt" to byteArrayOf(4)).forEach { (name, bytes) ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(bytes)
                        zip.closeEntry()
                    }
                }
            }.toByteArray()
            val digest = MessageDigest.getInstance("SHA-256").digest(payload)
            val hash = digest.joinToString("") { "%02x".format(it.toInt() and 255) }
            val lockBytes = """
                lock-version = 1
                resolver-version = "1"
                roots = ["test:library"]
                [[packages]]
                id = "test:library"
                version = "1.0.0"
                repository = "https://unreachable.invalid/maven/"
                descriptor-sha256 = "${"a".repeat(64)}"
                features = []
                artifacts = [{ type = "compiler.mcfpp.library", classifier = "mcfpp", extension = "zip", sha256 = "$hash", size = ${payload.size}, executable = false }]
                [[load-order]]
                package = "test:library"
                type = "compiler.mcfpp.library"
                classifier = "mcfpp"
            """.trimIndent().toByteArray()
            val lock = Files.write(directory.resolve("mcfpm.lock"), lockBytes)
            val cache = directory.resolve("cache")
            val cached = cache.resolve("sha256").resolve(hash.take(2)).resolve(hash)
            Files.createDirectories(cached.parent)
            Files.write(cached, payload)
            block(Fixture(configuration, lock, lockBytes, cache, cached, directory.resolve("trust.toml"), digest))
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    private data class Fixture(val configuration: Path, val lock: Path, val lockBytes: ByteArray,
        val cache: Path, val cached: Path, val trust: Path, val digest: ByteArray)
}
