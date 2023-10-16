package top.mcfpp.lib

import org.jetbrains.annotations.Nullable

interface IFieldWithStruct:IField {
    /**
     * 向域中添加一个结构体
     *
     * @param identifier 结构体的标识符
     * @param struct 结构体
     * @param force 是否强制添加。如果为true，则即使已经添加过相同标识符的结构体，也会覆盖原来的结构体进行添加。
     * @return 是否添加成功。如果已经存在相同标识符的结构体，且不是强制添加则为false
     */
    fun addStruct(identifier: String, struct: Struct, force: Boolean = false): Boolean

    /**
     * 移除一个结构体
     *
     * @param identifier 这个结构体的标识符
     * @return 是否移除成功。如果不存在此结构体，则返回false
     */
    fun removeStruct(identifier: String):Boolean

    /**
     * 获取一个结构体。可能不存在
     *
     * @param identifier 结构体的标识符
     * @return 获取到的结构体。如果不存在，则返回null
     */
    @Nullable
    fun getStruct(identifier: String):Struct?

    /**
     * 是否存在此结构体
     *
     * @param identifier 结构体的标识符
     * @return
     */
    fun hasStruct(identifier: String):Boolean

    /**
     * 是否存在此结构体
     *
     * @param struct 结构体
     * @return
     */
    fun hasStruct(struct: Struct): Boolean

    fun forEachStruct(operation: (Struct) -> Any?)
}