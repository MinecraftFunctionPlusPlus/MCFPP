package top.mcfpp.util

/**
 * 懒加载包装器
 * @param T 被包装的对象的类型
 */
class LazyWrapper<T> {
    private var value: T? = null
    private var initialized = false
    private val initializer: () -> T?

    /**
     * 构造一个懒加载包装器
     * @param initializer 初始化函数
     */
    constructor(initializer: () -> T?) {
        this.initializer = initializer
    }

    constructor(value: T?) : this({ value })

    fun get(): T? {
        if (!initialized) {
            value = initializer()
            initialized = true
        }
        return value
    }

    /**
     * 判断是否已经初始化
     */
    fun isInitialized(): Boolean {
        return initialized
    }

    fun set(value: T?) {
        this.value = value
        initialized = true
    }

    fun clone(): LazyWrapper<T> {
        val re = LazyWrapper(initializer)
        re.value = value
        re.initialized = initialized
        return re
    }
}