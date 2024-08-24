package top.mcfpp.util

import net.querz.nbt.tag.IntArrayTag
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.UUID
import kotlin.system.exitProcess

object Utils {

    /**
     * 停止编译
     *
     * @param e 异常信息
     */
    inline fun stopCompile(e : Exception?){
        e?.printStackTrace()
        exitProcess(2)
    }

    /**
     * 获取数据包版本
     * @param version 版本字符串
     * @return 版本编号
     */
    fun getVersion(version: String): Int {
        return when (version) {

            "1.21", "1.21.1" -> {
                48
            }

            "1.20.5", "1.20.6" -> {
                41
            }

            "1.20.3", "1.20.4" -> {
                26
            }

            "1.20.2" -> {
                18
            }

            "1.20", "1.20.1" -> {
                15
            }

            "1.19.4" -> {
                12
            }

            "1.19.3", "1.19.2", "1.19.1", "1.19" -> {
                10
            }

            "1.18.2" -> {
                9
            }

            "1.18.1", "1.18" -> {
                8
            }

            "1.17.1", "1.17" -> {
                7
            }

            "1.16.5", "1.16.4", "1.16.3", "1.16.2" -> {
                6
            }

            "1.16.1", " 1.16", "1.15.2", "1.15.1", "1.15" -> {
                5
            }

            "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14", "1.13.2", "1.13.1", "1.13" -> {
                4
            }

            else -> {
                LogProcessor.warn("Unknown version: \"$version\". Using 1.21.1 (48)")
                13
            }
        }
    }

    fun toNBTArrayUUID(uuid: UUID): IntArrayTag{
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
}

class SerializableFunctionBodyContext(ctx: mcfppParser.FunctionBodyContext) : mcfppParser.FunctionBodyContext(
    ctx.getParent(),
    ctx.invokingState
), Serializable{
    init {
        this.children = ctx.children
        this.start = ctx.start
        this.stop = ctx.stop
    }
}

class SerializableClassBodyContext(ctx: mcfppParser.ClassBodyContext): mcfppParser.ClassBodyContext(
    ctx.getParent(),
    ctx.invokingState
), Serializable{
    init {
        this.children = ctx.children
        this.start = ctx.start
        this.stop = ctx.stop
    }
}