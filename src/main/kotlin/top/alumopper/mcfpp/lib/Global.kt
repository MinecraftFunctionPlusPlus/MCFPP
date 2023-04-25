package top.alumopper.mcfpp.lib

import top.alumopper.mcfpp.Project

/**
 * 全局
 */
class Global : CacheContainer {

    /**
     * 全局函数
     */
    lateinit var cache: Cache

    /**
     *
     */
    var functionTags: HashMap<String, FunctionTag> = HashMap()

    fun init(): Global {
        cache = Cache(null, this)
        return this
    }

    @get:Override
    override val prefix: String
        get() = Project.name + "_global_"
}