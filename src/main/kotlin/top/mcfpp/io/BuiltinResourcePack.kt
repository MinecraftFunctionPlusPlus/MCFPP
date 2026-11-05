package top.mcfpp.io

import java.io.ByteArrayOutputStream
import java.io.DataInputStream

/** Lossless, shared chunks used only by the compact release distribution. */
internal class BuiltinResourcePack private constructor(
    private val loader: ClassLoader,
    private val entries: Map<String, Entry>,
) {
    private data class Entry(val size: Int, val chunks: IntArray)

    val names: Set<String> get() = entries.keys

    fun read(name: String): ByteArray {
        val entry = requireNotNull(entries[name]) { "Missing packed standard-library resource: $name" }
        val output = ByteArrayOutputStream(entry.size)
        for (chunk in entry.chunks) {
            requireNotNull(loader.getResourceAsStream("$ROOT/$chunk")) {
                "Missing standard-library chunk $chunk for $name"
            }.use { it.copyTo(output) }
        }
        return output.toByteArray().also {
            require(it.size == entry.size) { "Invalid packed standard-library resource length: $name" }
        }
    }

    companion object {
        const val ROOT = "META-INF/mcfpp/stdlib"
        const val INDEX = "$ROOT/index"
        const val MAGIC = 0x4d435250

        fun open(loader: ClassLoader): BuiltinResourcePack? {
            val stream = loader.getResourceAsStream(INDEX) ?: return null
            val entries = linkedMapOf<String, Entry>()
            DataInputStream(stream.buffered()).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == 1) { "Unsupported packed standard library" }
                val count = input.readInt()
                require(count >= 0) { "Invalid packed standard-library index" }
                repeat(count) {
                    val name = input.readUTF()
                    val size = input.readInt()
                    val chunkCount = input.readInt()
                    require(size >= 0 && chunkCount >= 0) { "Invalid packed standard-library resource: $name" }
                    val chunks = IntArray(chunkCount) { input.readInt().also { id -> require(id >= 0) } }
                    require(entries.put(name, Entry(size, chunks)) == null) { "Duplicate packed resource: $name" }
                }
                require(input.read() == -1) { "Trailing data in packed standard-library index" }
            }
            return BuiltinResourcePack(loader, entries)
        }
    }
}
