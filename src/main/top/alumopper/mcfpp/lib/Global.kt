package top.alumopper.mcfpp.lib

import top.alumopper.mcfpp.Project

/**
 * 全局
 */
class Global : CacheContainer {
    var globalInit: Function? = null

    /**
     * 全局函数
     */
    lateinit var cache: Cache

    /**
     *
     */
    var functionTags: HashMap<String, FunctionTag>? = null

    fun init(): Global {
        cache = Cache(null, this)
        globalInit = Function("_global_init")
        functionTags = HashMap()
        Function.Companion.currFunction = globalInit
        return this
    }

    @get:Override
    override val prefix: String
        get() = Project.name.toString() + "_global_"
}