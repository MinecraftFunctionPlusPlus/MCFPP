package top.mcfpp.model.field

import top.mcfpp.model.Enum

interface IFieldWithEnum {
    /**
     * 向域中添加一个枚举
     *
     * @param identifier 枚举的标识符
     * @param enum 枚举
     * @param force 是否强制添加。如果为true，则即使已经添加过相同标识符的枚举，也会覆盖原来的枚举进行添加。
     * @return 是否添加成功。如果已经存在相同标识符的枚举，且不是强制添加则为false
     */
    fun addEnum(identifier: String, enum: Enum, force: Boolean = false): Boolean

    /**
     * 移除一个枚举
     *
     * @param identifier 这个枚举的标识符
     * @return 是否移除成功。如果不存在此枚举，则返回false
     */
    fun removeEnum(identifier: String):Boolean

    /**
     * 获取一个非泛型枚举。可能不存在
     *
     * @param identifier 枚举的标识符
     * @return 获取到的枚举。如果不存在，则返回null
     */
    fun getEnum(identifier: String): Enum?

    /**
     * 是否存在此枚举
     *
     * @param identifier 枚举的标识符
     * @return
     */
    fun hasEnum(identifier: String):Boolean

    /**
     * 是否存在此枚举
     *
     * @param enum 枚举
     * @return
     */
    fun hasEnum(enum: Enum): Boolean

    fun forEachEnum(operation: (Enum) -> Any?)
}