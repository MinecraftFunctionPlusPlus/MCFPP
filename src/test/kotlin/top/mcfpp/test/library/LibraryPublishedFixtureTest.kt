package top.mcfpp.test.library

import top.mcfpp.library.format.CompoundCodec
import top.mcfpp.library.format.LibraryFormatCode
import top.mcfpp.library.format.LibraryFormatException
import top.mcfpp.library.format.LibraryModelCodec
import top.mcfpp.library.format.LibraryWire
import top.mcfpp.library.format.NativeCodec
import top.mcfpp.library.model.LibraryCompatibility
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Frozen production output: these tests never compile source or regenerate expected bytes. */
class LibraryPublishedFixtureTest {
    private val codec = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))

    @Test
    fun productionV1BytesRequireRebuildingForTheCurrentDataAndFactoryContract() {
        val bytes = fixture("bin.mclib", "f3b28effbe2722d7c0c4dade5280b90fc35fbcc2cf800f7d9e386984ae5d27c9")
        assertEquals(1L, LibraryWire.decode(bytes).formatVersion)
        val failure = assertFailsWith<LibraryFormatException> {
            codec.decode(bytes, LibraryCompatibility(minecraftVersion = "26.3"))
        }
        assertEquals(LibraryFormatCode.UNSUPPORTED, failure.code)
        assertEquals(8L, failure.byteOffset)
        assertTrue(failure.detail.contains("rebuild"), failure.message)
    }

    @Test
    fun futureFormatIsRejectedBeforeReadingTheFrozenPayloadWithUpgradeGuidance() {
        val future = fixture("future-v2.mclib", "610ab256647b055bcc8fb24ecdab97aa0c63077df510dbffc8d3280cee39d9a9")
        // Advance only the header version; retain the immutable historical payload.
        val futureVersion = LibraryWire.CURRENT_VERSION + 1
        top.mcfpp.library.format.LibraryBinaryWriter().apply { u32(futureVersion) }.toByteArray().copyInto(future, 8)
        val failure = assertFailsWith<LibraryFormatException> { codec.decode(future) }
        assertEquals(LibraryFormatCode.NEWER, failure.code)
        assertEquals(8L, failure.byteOffset)
        assertTrue(failure.detail.contains(futureVersion.toString()), failure.message)
        assertTrue(failure.detail.contains("upgrade"), failure.message)
    }

    private fun fixture(name: String, expectedSha256: String): ByteArray {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/library/published/semantic-lib-v1/$name"))
            .use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(expectedSha256, digest, "The committed fixture bytes changed: $name")
        return bytes
    }
}
