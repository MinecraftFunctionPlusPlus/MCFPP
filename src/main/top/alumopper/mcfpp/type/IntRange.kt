package top.alumopper.mcfpp.type

import java.util.Objects

/**
 * 整数范围，用于判断计分板或者nbt中的数值是否在范围内
 */
class IntRange {
    private var max: Integer? = null
    private var min: Integer? = null

    /**
     * 根据最大值和最小值创建一个整数范围。注意最大值和最小值都是可选的，如果不需要某个值，可以传入null，但是不能同时为null
     *
     * @param min
     * @param max
     * @exception IllegalArgumentException 若最大值和最小值同时为null或者最大值小于最小值的时候抛出
     */
    constructor(min: Integer?, max: Integer?) {
        this.min = min
        this.max = max
        if (min == null && max == null) {
            throw IllegalArgumentException("最大值和最小值不能同时为空。")
        }
        if (min != null && max != null && min > max) {
            throw IllegalArgumentException("最大值不能小于最小值。")
        }
    }

    constructor(range: String) {
        //2..   ..2     2..3
        val min_max: Array<String> = range.split("[.]", -1)
        max = if (Objects.equals(min_max[2], "")) null else Integer.parseInt(min_max[2]) as Integer
        min = if (Objects.equals(min_max[0], "")) null else Integer.parseInt(min_max[0]) as Integer
        if (max == null && min == null) {
            throw IllegalArgumentException("最大值和最小值不能同时为空。")
        }
    }

    @Override
    override fun toString(): String {
        return if (min == null) {
            "..$max"
        } else if (max == null) {
            min.toString() + ".."
        } else {
            min.toString() + ".." + max
        }
    }
}