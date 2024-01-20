package top.mcfpp.util

import net.querz.nbt.tag.*

object NBTUtil {
    fun toNBT(int: Int): Tag<Int>{
        return IntTag(int)
    }

    fun toNBT(double: Double): Tag<Double>{
        return DoubleTag(double)
    }

    fun toNBT(string: String): Tag<String>{
        return StringTag(string)
    }

    fun toNBT(long: Long): Tag<Long>{
        return LongTag(long)
    }

    fun toNBT(byte: Byte): Tag<Byte>{
        return ByteTag(byte)
    }

    fun toNBT(short: Short): Tag<Short>{
        return ShortTag(short)
    }

    fun toNBT(float: Float): Tag<Float>{
        return FloatTag(float)
    }

    fun toNBT(byteArray: ByteArray): Tag<ByteArray>{
        return ByteArrayTag(byteArray)
    }

    fun toNBT(longArray: LongArray): Tag<LongArray>{
        return LongArrayTag(longArray)
    }

    fun toNBT(int: IntArray): Tag<IntArray>{
        return IntArrayTag(int)
    }
}