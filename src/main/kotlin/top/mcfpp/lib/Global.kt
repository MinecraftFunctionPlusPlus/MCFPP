package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.lang.SbObject

/**
 * 全局
 */
class Global : CacheContainer {

    /**
     * 全局函数和类
     */
    lateinit var cache: Cache

    /**
     * 函数的标签
     */
    var functionTags: HashMap<String, FunctionTag> = HashMap()

    /**
     * 记分板
     */
    var scoreboards: ArrayList<SbObject> = ArrayList();

    fun init(): Global {
        cache = Cache(null, this)
        functionTags["minecraft:tick"] = FunctionTag.TICK
        functionTags["minecraft:load"] = FunctionTag.LOAD
        scoreboards.add(SbObject.MCS_boolean)
        scoreboards.add(SbObject.MCS_default)
        return this
    }

    @get:Override
    override val prefix: String
        get() = Project.defaultNamespace + "_global_"
}