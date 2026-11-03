package top.mcfpp.library.format

import java.io.IOException

/** A wire diagnostic, independent of the compiler's global logging/error state. */
class LibraryFormatException(
    val code: String,
    val detail: String,
    val byteOffset: Long,
    val sectionId: Int? = null,
    val recordIndex: Long? = null,
    cause: Throwable? = null,
) : IOException(buildString {
    append(code).append(": ").append(detail)
    append(" (entry=bin.mclib, byte=").append(byteOffset)
    sectionId?.let { append(", section=0x").append(it.toString(16)) }
    recordIndex?.let { append(", record=").append(it) }
    append(')')
}, cause)

object LibraryFormatCode {
    const val NEWER = "LIB_FORMAT_NEWER"
    const val UNSUPPORTED = "LIB_FORMAT_UNSUPPORTED"
    const val DEVELOPMENT_CACHE = "LIB_DEVELOPMENT_CACHE"
    const val CORRUPT = "LIB_CORRUPT"
    const val IO = "LIB_IO"
}
