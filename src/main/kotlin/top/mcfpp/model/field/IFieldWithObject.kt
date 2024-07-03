package top.mcfpp.model.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.model.CompoundData

interface IFieldWithObject {
    /**
     * 向域中添加一个单例
     *
     * @param identifier 单例的标识符
     * @param obj 单例
     * @param force 是否强制添加。如果为true，则即使已经添加过相同标识符的单例，也会覆盖原来的单例进行添加。
     * @return 是否添加成功。如果已经存在相同标识符的单例，且不是强制添加则为false
     */
    fun addObject(identifier: String, obj: CompoundData, force: Boolean = false): Boolean

    /**
     * 移除一个单例
     *
     * @param identifier 这个单例的标识符
     * @return 是否移除成功。如果不存在此单例，则返回false
     */
    fun removeObject(identifier: String):Boolean

    /**
     * 获取一个单例。可能不存在
     *
     * @param identifier 单例的标识符
     * @return 获取到的单例。如果不存在，则返回null
     */
    @Nullable
    fun getObject(identifier: String): CompoundData?

    /**
     * 是否存在此单例
     *
     * @param identifier 单例的标识符
     * @return
     */
    fun hasObject(identifier: String):Boolean

    /**
     * 是否存在此单例
     *
     * @param obj 单例
     * @return
     */
    fun hasObject(obj: CompoundData): Boolean

    fun forEachObject(operation: (CompoundData) -> Unit)
}