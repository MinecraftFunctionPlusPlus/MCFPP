package top.mcfpp.library.api

import top.mcfpp.CompileSettings
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor

/**
 * 嵌入式编译入口借用进程全局设置时的快照。
 * 会话或内核在首次修改设置前创建，并在关闭时恢复；这里不管理编译器的独占权。
 */
internal class CompilerHostSettings {
    private val debug = CompileSettings.isDebug
    private val ignoreStdLib = CompileSettings.ignoreStdLib
    private val library = CompileSettings.isLib
    private val maxWhileInline = CompileSettings.maxWhileInline
    private val printAll = CompileSettings.printAll
    private val level = LogProcessor.level

    fun restore() {
        CompileSettings.isDebug = debug
        CompileSettings.ignoreStdLib = ignoreStdLib
        CompileSettings.isLib = library
        CompileSettings.maxWhileInline = maxWhileInline
        CompileSettings.printAll = printAll
        LogProcessor.level = level
    }

    companion object {
        /** 两种宿主入口共用编译默认值，各自指定诊断输出级别。 */
        fun applyDefaults(level: LogLevel) {
            CompileSettings.isDebug = false
            CompileSettings.ignoreStdLib = false
            CompileSettings.isLib = false
            CompileSettings.maxWhileInline = 32
            CompileSettings.printAll = false
            LogProcessor.level = level
        }
    }
}
