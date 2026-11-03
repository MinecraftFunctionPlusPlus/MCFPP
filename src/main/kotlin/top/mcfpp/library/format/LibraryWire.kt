package top.mcfpp.library.format

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections

object LibrarySectionId {
    const val MANIFEST = 0x0001
    const val STRINGS = 0x0002
    const val ENVIRONMENTS = 0x0003
    const val TYPES = 0x0004
    const val CONSTANTS = 0x0005
    const val DECLARATIONS = 0x0006
    const val SCOPES = 0x0007
    const val NODES = 0x0008
    const val BODIES = 0x0009
    const val ANNOTATIONS = 0x000a
    const val RUNTIME = 0x000b
    const val RESOURCES = 0x000c
    const val DEBUG = 0x8001

    val requiredIds: Set<Int> = Collections.unmodifiableSet((MANIFEST..RESOURCES).toSet())
}

/** Owns its payload; neither caller mutation nor a returned byte array changes a section. */
class LibrarySection(val id: Int, val required: Boolean, payload: ByteArray) {
    init { require(id in 0..0xffff) { "Section id is not a u16: $id" } }
    internal val content = payload.copyOf()
    val payload: ByteArray get() = content.copyOf()
    val byteLength: Int get() = content.size

    fun reader(payloadOffset: Long = 0): LibraryBinaryReader =
        LibraryBinaryReader(content, payloadOffset, id)

    override fun equals(other: Any?): Boolean = other is LibrarySection &&
        id == other.id && required == other.required && content.contentEquals(other.content)

    override fun hashCode(): Int = 31 * (31 * id + required.hashCode()) + content.contentHashCode()
    override fun toString(): String = "LibrarySection(id=0x${id.toString(16)}, required=$required, bytes=$byteLength)"
}

class LibraryContainer internal constructor(val formatVersion: Long, sections: List<LibrarySection>) {
    val sections: List<LibrarySection> = Collections.unmodifiableList(ArrayList(sections))
    fun section(id: Int): LibrarySection? = sections.firstOrNull { it.id == id }
}

/** Container-only v1 codec. Section payload semantics belong to separate explicit codecs. */
object LibraryWire {
    const val CURRENT_VERSION = 1L
    const val HEADER_BYTES = 24
    const val SECTION_HEADER_BYTES = 12
    private val magic = byteArrayOf(0x4d, 0x43, 0x46, 0x50, 0x4c, 0x49, 0x42, 0)
    private val developmentMagic = byteArrayOf(0x4d, 0x43, 0x46, 0x4c)

    fun encode(sections: Collection<LibrarySection>): ByteArray {
        val ordered = sections.sortedBy { it.id }
        validateSections(ordered)
        val length = HEADER_BYTES.toLong() + ordered.sumOf { SECTION_HEADER_BYTES.toLong() + it.byteLength }
        if (length > Int.MAX_VALUE)
            throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                "Container length $length exceeds the JVM byte array range", 12)
        return LibraryBinaryWriter().apply {
            rawBytes(magic)
            u32(CURRENT_VERSION)
            u64(length.toULong())
            u32(ordered.size.toLong())
            for (section in ordered) {
                u16(section.id)
                u16(if (section.required) 1 else 0)
                u64(section.byteLength.toULong())
                rawBytes(section.content)
            }
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): LibraryContainer = read(ByteArrayInputStream(bytes))

    /** The caller owns [input]; this method reads exactly one complete file including its EOF. */
    fun read(input: InputStream): LibraryContainer {
        val source = ContainerInput(input)
        try {
            val prefix = source.exact(4)
            if (prefix.contentEquals(developmentMagic))
                throw LibraryFormatException(LibraryFormatCode.DEVELOPMENT_CACHE,
                    "Unpublished MCFL development cache; re-export the library with this compiler", 0)
            val actualMagic = prefix + source.exact(4)
            if (!actualMagic.contentEquals(magic))
                throw LibraryFormatException(LibraryFormatCode.UNSUPPORTED, "Unrecognized library magic", 0)
            // Version dispatch precedes lengths, sections, or any semantic payload.
            val version = LibraryBinaryReader(source.exact(4), 8).u32()
            when {
                version > CURRENT_VERSION -> throw LibraryFormatException(LibraryFormatCode.NEWER,
                    "Library format $version is newer than supported format $CURRENT_VERSION; upgrade the compiler", 8)
                version != CURRENT_VERSION -> throw LibraryFormatException(LibraryFormatCode.UNSUPPORTED,
                    "Unsupported published library format $version", 8)
            }
            val fileLength = LibraryBinaryReader(source.exact(8), 12).u64()
            if (fileLength < HEADER_BYTES.toULong() || fileLength > Int.MAX_VALUE.toULong())
                throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                    "Invalid container length $fileLength (JVM byte array range required)", 12)
            val count = LibraryBinaryReader(source.exact(4), 20).u32()
            val bodyLength = fileLength.toInt() - HEADER_BYTES
            if (count > bodyLength.toLong() / SECTION_HEADER_BYTES)
                throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                    "Section count $count cannot fit in $bodyLength byte(s)", 20)
            val body = source.body(bodyLength)
            if (source.next() != -1)
                throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                    "Trailing bytes after the declared container length", fileLength.toLong())
            return decodeV1(body, count.toInt())
        } catch (failure: LibraryFormatException) {
            throw failure
        } catch (failure: IOException) {
            throw LibraryFormatException(LibraryFormatCode.IO,
                failure.message ?: "Cannot read library", source.offset, cause = failure)
        }
    }

    /** The caller owns [output]; encoding finishes before the first output byte is written. */
    fun write(output: OutputStream, sections: Collection<LibrarySection>) {
        val bytes = encode(sections)
        try {
            output.write(bytes)
        } catch (failure: IOException) {
            throw LibraryFormatException(LibraryFormatCode.IO,
                failure.message ?: "Cannot write library", 0, cause = failure)
        }
    }

    private fun decodeV1(body: ByteArray, count: Int): LibraryContainer {
        val reader = LibraryBinaryReader(body, HEADER_BYTES.toLong())
        val sections = ArrayList<LibrarySection>()
        var previousId = -1
        repeat(count) {
            val headerOffset = reader.byteOffset
            val id = reader.u16()
            val flags = reader.u16()
            if (flags and 0xfffe != 0)
                throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                    "Reserved section flags are nonzero: $flags", headerOffset + 2, id)
            if (id <= previousId)
                throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                    "Section ids must be unique and strictly increasing", headerOffset, id)
            val required = flags and 1 != 0
            validateSection(id, required, headerOffset)
            val length = reader.u64()
            if (length > reader.remaining.toULong())
                throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                    "Section length $length exceeds remaining input ${reader.remaining}", headerOffset + 4, id)
            sections.add(LibrarySection(id, required, reader.rawBytes(length.toInt())))
            previousId = id
        }
        reader.requireEnd()
        validateSections(sections)
        return LibraryContainer(CURRENT_VERSION, sections)
    }

    private fun validateSection(id: Int, required: Boolean, offset: Long) {
        if (id in LibrarySectionId.requiredIds && !required)
            throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                "Core section must be marked required", offset, id)
        if (id == LibrarySectionId.DEBUG && required)
            throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                "Debug section must be optional", offset, id)
        if (id !in LibrarySectionId.requiredIds && id != LibrarySectionId.DEBUG && required)
            throw LibraryFormatException(LibraryFormatCode.UNSUPPORTED,
                "Unknown required section; upgrade the compiler", offset, id)
    }

    private fun validateSections(sections: List<LibrarySection>) {
        val seen = HashSet<Int>()
        for (section in sections) {
            if (!seen.add(section.id))
                throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                    "Duplicate section id", 0, section.id)
            validateSection(section.id, section.required, 0)
        }
        val missing = LibrarySectionId.requiredIds - seen
        if (missing.isNotEmpty())
            throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                "Missing required section(s): ${missing.joinToString { "0x${it.toString(16)}" }}", 0)
    }

    private class ContainerInput(private val input: InputStream) {
        var offset = 0L
            private set

        fun next(): Int = input.read().also { if (it >= 0) offset++ }

        fun exact(length: Int): ByteArray {
            val result = ByteArray(length)
            var position = 0
            while (position < length) {
                val count = input.read(result, position, length - position)
                if (count < 0) truncated()
                if (count == 0) {
                    val byte = next()
                    if (byte < 0) truncated()
                    result[position++] = byte.toByte()
                } else {
                    position += count
                    offset += count
                }
            }
            return result
        }

        // A corrupt declared length never causes a giant speculative allocation before reading.
        fun body(length: Int): ByteArray {
            val result = ByteArrayOutputStream(minOf(length, 8192))
            var remaining = length
            while (remaining > 0) {
                val chunk = exact(minOf(remaining, 8192))
                result.write(chunk)
                remaining -= chunk.size
            }
            return result.toByteArray()
        }

        private fun truncated(): Nothing = throw LibraryFormatException(
            LibraryFormatCode.CORRUPT, "Truncated library input", offset)
    }
}
