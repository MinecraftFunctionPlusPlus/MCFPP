package top.mcfpp.util

import net.querz.nbt.tag.IntArrayTag
import top.mcfpp.Project
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
            "1.20" -> {
                13
            }

            "1.19.4" -> {
                12
            }

            "23w03a" -> {
                11
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

            "17w43a" -> {
                3
            }

            else -> {
                LogProcessor.warn("Unknown version: \"$version\". Using 1.20 (13)")
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
}