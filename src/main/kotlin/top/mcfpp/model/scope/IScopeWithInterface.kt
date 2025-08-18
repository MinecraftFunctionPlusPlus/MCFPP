package top.mcfpp.model.scope

import top.mcfpp.model.compound.Interface

interface IScopeWithInterface: IScope {

    /**
     * 向域中添加一个接口
     *
     * @param identifier 接口的标识符
     * @param itf 接口
     * @param force 是否强制添加。如果为true，则即使已经添加过相同标识符的接口，也会覆盖原来的接口进行添加。
     * @return 是否添加成功。如果已经存在相同标识符的接口，且不是强制添加则为false
     */
    fun addInterface(identifier: String, itf: Interface, force: Boolean = false): Boolean

    /**
     * 移除一个接口
     *
     * @param identifier 这个接口的标识符
     * @return 被移除的接口，若不存在则返回null
     */
    fun removeInterface(identifier: String): Interface?

    /**
     * 获取一个接口。可能不存在
     *
     * @param identifier 接口的标识符
     * @return 获取到的接口。如果不存在，则返回null
     */
    fun getInterface(identifier: String): Interface?

    /**
     * 是否存在此接口
     *
     * @param identifier 接口的标识符
     * @return
     */
    fun hasInterface(identifier: String):Boolean

    /**
     * 是否存在此接口
     *
     * @param itf 接口
     * @return
     */
    fun hasInterface(itf: Interface): Boolean

    fun forEachInterface(operation: (Interface) -> Any?)
}