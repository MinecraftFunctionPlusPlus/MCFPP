package top.alumopper.mcfpp.type

import top.alumopper.mcfpp.lang.MCInt

open class Vector2<T>(var x: T, var y: T) {
    operator fun get(i: MCInt): T? {
        if (i > 1) {
            throw IndexOutOfBoundsException("索引过大: $i。只含有两个元素")
        }
        if (i < 0) {
            throw IndexOutOfBoundsException("索引应当为非负数: $i")
        }
        when (i) {
            0 -> return x
            1 -> return y
        }
        return null
    }

    operator fun set(i: MCInt, value: T) {
        if (i > 1) {
            throw IndexOutOfBoundsException("索引过大: $i。只含有三个元素")
        }
        if (i < 0) {
            throw IndexOutOfBoundsException("索引应当为非负数: $i")
        }
        when (i) {
            0 -> x = value
            1 -> y = value
        }
    }

    @Override
    override fun toString(): String {
        return x.toString() + " " + y
    }
}