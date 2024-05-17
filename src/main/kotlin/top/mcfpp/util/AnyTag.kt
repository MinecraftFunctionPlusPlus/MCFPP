package top.mcfpp.util

import net.querz.nbt.tag.Tag

/**
 * 存放任意变量的NBT标签。不会被使用在游戏中。在被转换为snbt的时候，将会调用其储存值的toString方法作为值。
 *
 * 不适用于MC中，只存在于编译过程中
 */
class AnyTag<T>(value: T) : Tag<T>(value) {
    override fun clone(): Tag<T> {
        return AnyTag(value)
    }

    override fun getID(): Byte = -1

    override fun valueToString(p0: Int): String {
        return this.value.toString()
    }

}