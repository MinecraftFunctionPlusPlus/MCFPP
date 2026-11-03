package top.mcfpp.library.format

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Explicit big-endian primitives. These methods never serialize implementation classes. */
class LibraryBinaryWriter {
    private val buffer = ByteArrayOutputStream()
    private val output = DataOutputStream(buffer)

    val size: Int get() = buffer.size()

    fun u8(value: Int) {
        require(value in 0..0xff) { "u8 out of range: $value" }
        output.writeByte(value)
    }

    fun u16(value: Int) {
        require(value in 0..0xffff) { "u16 out of range: $value" }
        output.writeShort(value)
    }

    fun u32(value: Long) {
        require(value in 0..0xffff_ffffL) { "u32 out of range: $value" }
        output.writeInt(value.toInt())
    }

    fun u64(value: ULong) = output.writeLong(value.toLong())
    fun i64(value: Long) = output.writeLong(value)
    fun bool(value: Boolean) = u8(if (value) 1 else 0)

    fun string(value: String) {
        val encoded = try {
            StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value))
        } catch (failure: CharacterCodingException) {
            throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                "String contains an unpaired UTF-16 surrogate", size.toLong(), cause = failure)
        }
        u32(encoded.remaining().toLong())
        val bytes = ByteArray(encoded.remaining())
        encoded.get(bytes)
        rawBytes(bytes)
    }

    fun bytes(value: ByteArray) {
        u32(value.size.toLong())
        rawBytes(value)
    }

    fun rawBytes(value: ByteArray) = output.write(value)

    fun record(write: LibraryBinaryWriter.() -> Unit) {
        val child = LibraryBinaryWriter()
        write(child)
        bytes(child.toByteArray())
    }

    fun tagged(tag: Int, write: LibraryBinaryWriter.() -> Unit) {
        u16(tag)
        record(write)
    }

    fun <T> list(values: List<T>, write: LibraryBinaryWriter.(T) -> Unit) {
        u32(values.size.toLong())
        values.forEach { write(this, it) }
    }

    fun <T : Any> optional(value: T?, write: LibraryBinaryWriter.(T) -> Unit) {
        bool(value != null)
        if (value != null) write(this, value)
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}

/** A bounded view; child records cannot read bytes belonging to their parent or siblings. */
class LibraryBinaryReader private constructor(
    private val data: ByteArray,
    private val start: Int,
    private val end: Int,
    private val baseOffset: Long,
    val sectionId: Int?,
    val recordIndex: Long?,
) {
    constructor(
        bytes: ByteArray,
        baseOffset: Long = 0,
        sectionId: Int? = null,
        recordIndex: Long? = null,
    ) : this(bytes.copyOf(), 0, bytes.size, baseOffset, sectionId, recordIndex)

    init {
        require(baseOffset >= 0 && baseOffset <= Long.MAX_VALUE - (end - start)) {
            "Invalid base byte offset"
        }
    }

    private var cursor = start
    val remaining: Int get() = end - cursor
    val byteOffset: Long get() = baseOffset + (cursor - start)

    private fun requireBytes(length: Int) {
        if (length < 0 || length > remaining)
            fail("Truncated value: requires $length byte(s), only $remaining remain")
    }

    fun fail(detail: String, code: String = LibraryFormatCode.CORRUPT): Nothing =
        throw LibraryFormatException(code, detail, byteOffset, sectionId, recordIndex)

    fun requireEnd() {
        if (remaining != 0) fail("Record has $remaining unconsumed byte(s)")
    }

    fun u8(): Int {
        requireBytes(1)
        return data[cursor++].toInt() and 0xff
    }

    fun u16(): Int {
        requireBytes(2)
        return (u8() shl 8) or u8()
    }

    fun u32(): Long {
        requireBytes(4)
        var value = 0L
        repeat(4) { value = (value shl 8) or u8().toLong() }
        return value
    }

    fun u64(): ULong = i64().toULong()

    fun i64(): Long {
        requireBytes(8)
        var value = 0L
        repeat(8) { value = (value shl 8) or u8().toLong() }
        return value
    }

    fun bool(): Boolean = when (val value = u8()) {
        0 -> false
        1 -> true
        else -> fail("Invalid boolean value: $value")
    }

    private fun length(): Int {
        val value = u32()
        if (value > Int.MAX_VALUE.toLong() || value > remaining.toLong())
            fail("Length $value exceeds the remaining bounded input ($remaining)")
        return value.toInt()
    }

    fun rawBytes(length: Int): ByteArray {
        requireBytes(length)
        val result = data.copyOfRange(cursor, cursor + length)
        cursor += length
        return result
    }

    fun bytes(): ByteArray = rawBytes(length())

    fun string(): String {
        val length = length()
        val offset = byteOffset
        val result = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data, cursor, length)).toString()
        } catch (failure: CharacterCodingException) {
            throw LibraryFormatException(LibraryFormatCode.CORRUPT,
                "Invalid UTF-8 string", offset, sectionId, recordIndex, failure)
        }
        cursor += length
        return result
    }

    fun <T> record(index: Long? = recordIndex, read: LibraryBinaryReader.() -> T): T {
        val length = length()
        val child = LibraryBinaryReader(data, cursor, cursor + length, byteOffset, sectionId, index)
        cursor += length
        val result = read(child)
        child.requireEnd()
        return result
    }

    fun <T> tagged(read: LibraryBinaryReader.(Int) -> T): T {
        val tag = u16()
        return record { read(this, tag) }
    }

    /** Every list element must consume bytes; counts never control a speculative allocation. */
    fun <T> list(read: LibraryBinaryReader.() -> T): List<T> {
        val count = u32()
        if (count > remaining.toLong()) fail("List count $count exceeds the bounded input ($remaining)")
        val values = ArrayList<T>()
        repeat(count.toInt()) {
            val before = remaining
            values.add(read(this))
            if (remaining >= before) fail("List element did not consume input")
        }
        return java.util.Collections.unmodifiableList(values)
    }

    fun <T> optional(read: LibraryBinaryReader.() -> T): T? = if (bool()) read(this) else null
}
