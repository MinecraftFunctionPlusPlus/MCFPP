package top.mcfpp.optimization

import org.apache.logging.log4j.Logger
import top.mcfpp.command.Commands
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.util.LogProcessor

/** 为工程识别出的入口函数应用优化器选择的调用约定。 */
internal object EntrypointFrames {
    fun prepare(function: Function, logger: Logger) {
        if (function.optimizedScalar) return

        val staticFrame = OptimizationPipeline.staticFrame(function)
        if (staticFrame != null) {
            function.commands.add(0, Commands.dataSetValue(staticFrame, CompoundTag()))
            return
        }

        if (function.frameExits.isEmpty()) {
            function.commands.add(Commands.stackOut())
        } else {
            function.frameExits.groupBy { it.function }.forEach { (exitFunction, exits) ->
                // 倒序插入使原始退出位置在同一命令列表内保持有效。
                exits.map { if (it.fallthrough) exitFunction.commands.size else it.commandIndex }.sortedDescending().forEach { index ->
                    exitFunction.commands.add(index, Commands.stackOut())
                }
            }
        }
        // 先处理退出位置，再在入口插入入栈命令，避免移动记录的退出索引。
        function.commands.add(0, Commands.stackIn())
        LogProcessor.rawDebug(logger, "Find entrance function: {} {}", function.tags, function.identifier)
    }
}
