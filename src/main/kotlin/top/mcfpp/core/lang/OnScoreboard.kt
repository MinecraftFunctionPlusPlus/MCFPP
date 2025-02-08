package top.mcfpp.core.lang

import top.mcfpp.lib.SbObject

/**
 * 描述了所有基于单个记分板表达的类。任何依赖记分板的数据类型都应当实现这个类。
 */
interface OnScoreboard {


    var isDataOnly: Boolean

    /**
     * 在Minecraft中的标识符
     */
    var name: String

    fun setObj(sbObject: SbObject): OnScoreboard
}