package top.mcfpp.lib

import top.mcfpp.Project

/**
 * 全局
 */
class Global : CacheContainer {

    /**
     * 全局函数和类
     */
    lateinit var cache: Cache

    /**
     *
     */
    var functionTags: HashMap<String, FunctionTag> = HashMap()

    fun init(): Global {
        cache = Cache(null, this)
        functionTags["minecraft:tick"] = FunctionTag.TICK
        functionTags["minecraft:load"] = FunctionTag.LOAD
        return this
    }

    @get:Override
    override val prefix: String
        get() = Project.defaultNamespace + "_global_"
}