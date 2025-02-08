package top.mcfpp

object CompileSettings {
    /**
     * 是否是debug模式
     */
    var isDebug = false

    /**
     * 是否忽略标准库
     */
    var ignoreStdLib = false

    /**
     * 是否是库（不用包含入口函数）
     */
    var isLib = false

    /**
     * 最大循环内联次数
     */
    var maxWhileInline = 32

    /**
     * 是否输出全部编译结果
     */
    var printAll = false
}