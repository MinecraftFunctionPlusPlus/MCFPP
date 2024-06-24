package top.mcfpp

object CompileSettings {
    /**
     * 是否是debug模式
     */
    var isDebug : Boolean = false

    /**
     * 是否忽略标准库
     */
    var ignoreStdLib: Boolean = false

    /**
     * 是否是库（不用包含入口函数）
     */
    var isLib: Boolean = false
}