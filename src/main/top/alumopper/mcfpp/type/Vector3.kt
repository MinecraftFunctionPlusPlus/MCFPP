package top.alumopper.mcfpp.type

import top.alumopper.mcfpp.lang.MCInt

open class Vector3<T>(var x: T, var y: T, var z: T) {
    operator fun get(i: Int): T? {
        if (i > 2) {
            throw IndexOutOfBoundsException("索引过大: $i。只含有三个元素")
        }
        if (i < 0) {
            throw IndexOutOfBoundsException("索引应当为非负数: $i")
        }
        when (i) {
            0 -> return x
            1 -> return y
            2 -> return z
        }
        return null
    }

    operator fun set(i: Int, value: T) {
        if (i > 2) {
            throw IndexOutOfBoundsException("索引过大: $i。只含有三个元素")
        }
        if (i < 0) {
            throw IndexOutOfBoundsException("索引应当为非负数: $i")
        }
        when (i) {
            0 -> x = value
            1 -> y = value
            2 -> z = value
        }
    }

    @Override
    override fun toString(): String {
        return x.toString() + " " + y + " " + z
    }
}