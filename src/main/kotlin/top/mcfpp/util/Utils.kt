package top.mcfpp.util

import top.mcfpp.nbt.tags.collection.IntArrayTag
import java.io.*
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import kotlin.system.exitProcess

object Utils {

    /**
     * 停止编译
     *
     * @param e 异常信息
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun stopCompile(e : Exception?){
        e?.printStackTrace()
        exitProcess(2)
    }

    val version: Array<String> get() = top.mcfpp.command.TargetCapabilities.supportedVersions.toTypedArray()

    /** Legacy pack format; modern targets use their min/max format capability instead. */
    fun getVersion(version: String): Int = top.mcfpp.command.TargetCapabilities.forVersion(version)?.legacyPackFormat
        ?: throw IllegalArgumentException("Unsupported legacy data pack version: $version")

    fun toNBTArrayUUID(uuid: UUID): IntArrayTag {
        val uuidArray = IntArray(4)
        uuidArray[0] = uuid.leastSignificantBits.toInt()
        uuidArray[1] = (uuid.leastSignificantBits shr 32).toInt()
        uuidArray[2] = uuid.mostSignificantBits.toInt()
        uuidArray[3] = (uuid.mostSignificantBits shr 32).toInt()
        return IntArrayTag(uuidArray)
    }

    fun fromNBTArrayUUID(tag: IntArrayTag): UUID{
        val uuidArray = tag.value
        return UUID(
            (uuidArray[0].toLong() and 0xFFFFFFFFL) or (uuidArray[1].toLong() shl 32),
            (uuidArray[2].toLong() and 0xFFFFFFFFL) or (uuidArray[3].toLong() shl 32)
        )
    }

    fun<T> toByteArrayString(obj: T): String where T : Serializable{
        // 创建一个 ObjectOutputStream，将数据序列化为字节数组
        val byteArrayOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
        objectOutputStream.writeObject(obj)
        objectOutputStream.flush()
        val bytes = byteArrayOutputStream.toByteArray()
        objectOutputStream.close()
        byteArrayOutputStream.close()

        // 将字节数组转换为字符串
        return bytes.joinToString("") { String.format("%02X", it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun<T> fromByteArrayString(str: String): T{
        // 将字符串转换为字节数组
        val bytes = ByteArray(str.length / 2)
        for (i in bytes.indices) {
            bytes[i] = str.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // 创建一个 ObjectInputStream，将字节数组反序列化为对象
        val byteArrayInputStream = ByteArrayInputStream(bytes)
        val objectInputStream = ObjectInputStream(byteArrayInputStream)
        val obj = objectInputStream.readObject() as T
        objectInputStream.close()
        byteArrayInputStream.close()

        return obj
    }

    fun <K, V> LinkedHashMap<K, V>.subMap(fromIndex: Int, toIndex: Int): LinkedHashMap<K, V> {
        require(fromIndex >= 0 && toIndex <= size && fromIndex <= toIndex) {
            "Invalid range: fromIndex=$fromIndex, toIndex=$toIndex, size=$size"
        }
        return LinkedHashMap<K, V>().apply {
            // 通过 entries 按顺序截取并重新插入
            this@subMap.entries.toList()
                .subList(fromIndex, toIndex)
                .forEach { put(it.key, it.value) }
        }
    }

    fun <K, V> LinkedHashMap<K, V>.addFirst(key: K, value: V) {
        val newMap = LinkedHashMap<K, V>()
        newMap[key] = value
        newMap.putAll(this)
        this.clear()
        this.putAll(newMap)
    }

    fun <T> Boolean.v(ifTrue: () -> T?, ifFalse: () -> T?): T? {
        return if (this) ifTrue() else ifFalse()
    }

    fun copyRecursively(source: Path, target: Path, replaceExisting: Boolean = false) {
        val options = if (replaceExisting) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray<CopyOption>()
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val rel = source.relativize(dir)
                val dstDir = target.resolve(rel)
                if (Files.notExists(dstDir)) Files.createDirectories(dstDir)
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val rel = source.relativize(file)
                val dst = target.resolve(rel)
                Files.copy(file, dst, *options)
                return FileVisitResult.CONTINUE
            }
        })
    }


}
