package top.mcfpp.test.library

import top.mcfpp.library.format.LibraryBinaryReader
import top.mcfpp.library.format.LibraryBinaryWriter
import top.mcfpp.library.format.LibraryFormatCode
import top.mcfpp.library.format.LibraryFormatException
import top.mcfpp.library.format.LibrarySection
import top.mcfpp.library.format.LibrarySectionId
import top.mcfpp.library.format.LibraryWire
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryWireTest {
    private fun sections() = LibrarySectionId.requiredIds.map {
        LibrarySection(it, true, byteArrayOf(it.toByte()))
    }

    private fun failure(code: String = LibraryFormatCode.CORRUPT, action: () -> Unit): LibraryFormatException =
        assertFailsWith<LibraryFormatException>(block = action).also { assertEquals(code, it.code) }

    @Test fun fixedWireLayoutAndDeterministicOrdering() {
        // This fixture freezes the wire layout only; empty section payloads are not semantic tables.
        val fixture = requireNotNull(javaClass.getResourceAsStream("/library/wire/v1-empty-sections.hex"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }.filterNot(Char::isWhitespace)
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val emptySections = LibrarySectionId.requiredIds.map { LibrarySection(it, true, byteArrayOf()) }
        val encoded = LibraryWire.encode(emptySections.reversed())
        assertContentEquals(fixture, encoded)
        val restored = LibraryWire.decode(fixture)
        assertEquals(1L, restored.formatVersion)
        assertEquals(emptySections, restored.sections)
        assertContentEquals(fixture, LibraryWire.encode(restored.sections))
    }

    @Test fun streamRoundTripKeepsPayloadsAndDoesNotCloseCallerStreams() {
        val expected = sections() + LibrarySection(LibrarySectionId.DEBUG, false, byteArrayOf(8, 9))
        var outputClosed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() { outputClosed = true; super.close() }
        }
        LibraryWire.write(output, expected)
        var inputClosed = false
        val input = object : ByteArrayInputStream(output.toByteArray()) {
            override fun close() { inputClosed = true; super.close() }
            override fun read(target: ByteArray, offset: Int, length: Int): Int =
                super.read(target, offset, minOf(length, 1))
        }
        assertEquals(expected, LibraryWire.read(input).sections)
        assertFalse(inputClosed)
        assertFalse(outputClosed)
    }

    @Test fun sectionPayloadsAreOwnedAndUnknownOptionalSectionsRoundTrip() {
        val payload = byteArrayOf(42)
        val extension = LibrarySection(0x7000, false, payload)
        payload[0] = 0
        extension.payload[0] = 1
        assertContentEquals(byteArrayOf(42), extension.payload)
        val encoded = LibraryWire.encode(sections() + extension)
        val restored = LibraryWire.decode(encoded)
        assertEquals(extension, restored.section(0x7000))
        assertNull(restored.section(0x7001))
        assertContentEquals(encoded, LibraryWire.encode(restored.sections))
    }

    @Test fun futureUnsignedVersionsAreRejectedBeforeReadingTheRestOfTheHeader() {
        for (version in listOf(2L, 0xffff_ffffL)) {
            val header = LibraryBinaryWriter().apply {
                rawBytes("MCFPLIB\u0000".toByteArray(Charsets.US_ASCII))
                u32(version)
            }.toByteArray()
            val diagnostic = failure(LibraryFormatCode.NEWER) { LibraryWire.decode(header) }
            assertEquals(8L, diagnostic.byteOffset)
            assertTrue(diagnostic.message.orEmpty().contains("upgrade"))
            assertTrue(diagnostic.message.orEmpty().contains(version.toString()))
        }
        failure(LibraryFormatCode.UNSUPPORTED) {
            LibraryWire.decode(LibraryBinaryWriter().apply {
                rawBytes("MCFPLIB\u0000".toByteArray(Charsets.US_ASCII)); u32(0)
            }.toByteArray())
        }
    }

    @Test fun unpublishedDevelopmentCachesHaveTheirOwnDiagnostic() {
        val diagnostic = failure(LibraryFormatCode.DEVELOPMENT_CACHE) {
            LibraryWire.decode("MCFL".toByteArray(Charsets.US_ASCII))
        }
        assertEquals(0L, diagnostic.byteOffset)
        assertTrue(diagnostic.message.orEmpty().contains("re-export"))
        failure(LibraryFormatCode.UNSUPPORTED) { LibraryWire.decode(ByteArray(8)) }
    }

    @Test fun rejectsTruncationTrailingBytesAndImpossibleContainerLengths() {
        val encoded = LibraryWire.encode(sections())
        for (length in listOf(0, 3, 7, 11, 19, 23, encoded.size - 1))
            failure { LibraryWire.decode(encoded.copyOf(length)) }
        val trailing = failure { LibraryWire.decode(encoded + byteArrayOf(0)) }
        assertEquals(encoded.size.toLong(), trailing.byteOffset)
        for (length in listOf(0uL, 23uL, ULong.MAX_VALUE)) {
            val invalid = encoded.copyOf()
            LibraryBinaryWriter().apply { u64(length) }.toByteArray().copyInto(invalid, 12)
            assertEquals(12L, failure { LibraryWire.decode(invalid) }.byteOffset)
        }
        val count = encoded.copyOf()
        byteArrayOf(-1, -1, -1, -1).copyInto(count, 20)
        assertEquals(20L, failure { LibraryWire.decode(count) }.byteOffset)
    }

    @Test fun rejectsMissingDuplicateUnorderedAndUnsupportedRequiredSections() {
        failure { LibraryWire.encode(sections().dropLast(1)) }
        failure { LibraryWire.encode(sections() + sections().first()) }
        failure { LibraryWire.decode(rawContainer(sections().dropLast(1))) }
        failure { LibraryWire.decode(rawContainer(sections().reversed())) }
        failure { LibraryWire.decode(rawContainer(sections() + sections().last())) }
        val unknown = LibrarySection(0x7001, true, byteArrayOf())
        failure(LibraryFormatCode.UNSUPPORTED) { LibraryWire.encode(sections() + unknown) }
        val diagnostic = failure(LibraryFormatCode.UNSUPPORTED) {
            LibraryWire.decode(rawContainer(sections() + unknown))
        }
        assertEquals(0x7001, diagnostic.sectionId)
        failure { LibraryWire.encode(sections().drop(1) + LibrarySection(1, false, byteArrayOf())) }
        failure { LibraryWire.encode(sections() + LibrarySection(LibrarySectionId.DEBUG, true, byteArrayOf())) }
    }

    @Test fun sectionLengthsAndReservedFlagsCannotEscapeTheContainer() {
        val oversized = LibraryWire.encode(sections())
        LibraryBinaryWriter().apply { u64(ULong.MAX_VALUE) }.toByteArray().copyInto(oversized, 28)
        val lengthFailure = failure { LibraryWire.decode(oversized) }
        assertEquals(1, lengthFailure.sectionId)
        assertEquals(28L, lengthFailure.byteOffset)
        val flags = LibraryWire.encode(sections())
        flags[27] = 3
        val flagsFailure = failure { LibraryWire.decode(flags) }
        assertEquals(1, flagsFailure.sectionId)
        assertEquals(26L, flagsFailure.byteOffset)
        val unclaimed = rawContainer(sections(), extra = byteArrayOf(0))
        failure { LibraryWire.decode(unclaimed) }
    }

    @Test fun explicitPrimitivesNestedRecordsAndStringsRoundTrip() {
        val encoded = LibraryBinaryWriter().apply {
            u8(255); u16(65535); u32(0xffff_ffffL); u64(ULong.MAX_VALUE); i64(Long.MIN_VALUE)
            bool(true); bool(false)
            string("库\uD83D\uDE80e\u0301")
            bytes(byteArrayOf(0, -1))
            record {
                string("outer")
                tagged(0x1234) { list(listOf("a", "b")) { string(it) } }
                optional<String>(null) { string(it) }
                optional("present") { string(it) }
            }
        }.toByteArray()
        LibraryBinaryReader(encoded).apply {
            assertEquals(255, u8()); assertEquals(65535, u16()); assertEquals(0xffff_ffffL, u32())
            assertEquals(ULong.MAX_VALUE, u64()); assertEquals(Long.MIN_VALUE, i64())
            assertTrue(bool()); assertFalse(bool())
            assertEquals("库\uD83D\uDE80e\u0301", string())
            assertContentEquals(byteArrayOf(0, -1), bytes())
            record {
                assertEquals("outer", string())
                tagged { tag -> assertEquals(0x1234, tag); assertEquals(listOf("a", "b"), list { string() }) }
                assertNull(optional { string() })
                assertEquals("present", optional { string() })
            }
            requireEnd()
        }
    }

    @Test fun recordReaderCannotConsumeItsSiblingAndMustConsumeItsOwnPayload() {
        val encoded = LibraryBinaryWriter().apply { record { u8(7) }; u8(9) }.toByteArray()
        val reader = LibraryBinaryReader(encoded, baseOffset = 100, sectionId = 4)
        val diagnostic = failure { reader.record(index = 2) { u16() } }
        assertEquals(104L, diagnostic.byteOffset)
        assertEquals(4, diagnostic.sectionId)
        assertEquals(2L, diagnostic.recordIndex)
        assertEquals(9, reader.u8())
        reader.requireEnd()
        failure { LibraryBinaryReader(encoded).record { } }
    }

    @Test fun invalidUtf8BooleanLengthsAndCountsProduceBoundedDiagnostics() {
        failure { LibraryBinaryReader(byteArrayOf(2)).bool() }
        failure { LibraryBinaryReader(byteArrayOf(0, 0, 0, 2, -61, 40)).string() }
        failure { LibraryBinaryWriter().string("\uD800") }
        for (operation in listOf<LibraryBinaryReader.() -> Any>(
            { string() }, { bytes() }, { record { u8() } }, { list { u8() } },
        )) {
            failure { operation(LibraryBinaryReader(byteArrayOf(-1, -1, -1, -1))) }
        }
        failure { LibraryBinaryReader(byteArrayOf(0, 0, 0, 1, 9)).list { Unit } }
        assertFailsWith<IllegalArgumentException> { LibraryBinaryWriter().u8(256) }
        assertFailsWith<IllegalArgumentException> { LibraryBinaryWriter().u16(-1) }
        assertFailsWith<IllegalArgumentException> { LibraryBinaryWriter().u32(0x1_0000_0000L) }
    }

    @Test fun readAndWriteFailuresAreReportedAndNeverCloseCallerStreams() {
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("fixture read failure")
        }
        assertTrue(failure(LibraryFormatCode.IO) { LibraryWire.read(input) }.message.orEmpty().contains("fixture read"))
        val output = object : OutputStream() {
            override fun write(value: Int) { throw IOException("fixture write failure") }
        }
        assertTrue(failure(LibraryFormatCode.IO) { LibraryWire.write(output, sections()) }.message.orEmpty().contains("fixture write"))
    }

    private fun rawContainer(sections: List<LibrarySection>, extra: ByteArray = byteArrayOf()): ByteArray {
        val payload = LibraryBinaryWriter().apply {
            sections.forEach {
                u16(it.id); u16(if (it.required) 1 else 0); u64(it.byteLength.toULong()); rawBytes(it.payload)
            }
            rawBytes(extra)
        }.toByteArray()
        return LibraryBinaryWriter().apply {
            rawBytes("MCFPLIB\u0000".toByteArray(Charsets.US_ASCII))
            u32(1); u64((LibraryWire.HEADER_BYTES + payload.size).toULong()); u32(sections.size.toLong())
            rawBytes(payload)
        }.toByteArray()
    }
}
